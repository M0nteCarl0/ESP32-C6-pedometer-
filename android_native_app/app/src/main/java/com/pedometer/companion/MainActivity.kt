package com.pedometer.companion

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), BleManager.BleEventListener, GpsEmulatorManager.GpsEmulatorListener {

    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var bleManager: BleManager
    private lateinit var googleFitSyncManager: GoogleFitSyncManager
    private lateinit var routeManager: RouteManager
    private lateinit var gpsEmulatorManager: GpsEmulatorManager

    // UI Elements - Metrics & Connection
    private lateinit var tvSteps: TextView
    private lateinit var tvDist: TextView
    private lateinit var tvKcal: TextView
    private lateinit var tvCadence: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvHistoryStatus: TextView
    private lateinit var tvConsoleLog: TextView

    private lateinit var btnConnect: Button
    private lateinit var btnUploadGoogleFit: Button
    private lateinit var btnSyncHistory: Button
    private lateinit var btnExportJson: Button

    // UI Elements - Targets (Steps & Duration)
    private lateinit var etTargetSteps: EditText
    private lateinit var etTargetDurationMin: EditText
    private lateinit var btnSetTargets: Button
    private lateinit var btnClearTargets: Button
    private lateinit var tvTargetStatus: TextView
    private lateinit var pbStepsTarget: ProgressBar

    // UI Elements - Google Maps & GPS Emulation
    private lateinit var tvRouteInfo: TextView
    private lateinit var webViewMap: WebView
    private lateinit var btnPresetRoutes: Button
    private lateinit var btnSaveRoute: Button
    private lateinit var btnLoadRoute: Button
    private lateinit var btnToggleGps: Button
    private lateinit var tvGpsStatus: TextView
    private lateinit var btnOpenDevSettings: Button

    // State
    private var lastMetrics = StepMetrics()
    private var lastHistory = HourlyHistory()
    private var currentRoutePoints = mutableListOf<RoutePoint>()
    private var currentRouteName = "Пользовательский маршрут"
    private var currentRouteDistanceMeters = 0.0

    // Permissions launcher
    private val appPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = permissions.entries.filter { it.key.contains("BLUETOOTH") }.all { it.value }
        if (btGranted) {
            logToConsole("Bluetooth permissions granted! Scanning...")
            bleManager.startScan()
        } else {
            logToConsole("Bluetooth permissions not fully granted.")
        }
    }

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(GoogleFitSyncManager.HEALTH_PERMISSIONS)) {
            logToConsole("Health Connect permissions granted! Uploading data...")
            performHealthConnectUpload()
        } else {
            logToConsole("Health Connect permissions denied or partial: $grantedPermissions")
            Toast.makeText(this, "Health Connect permissions required for Google Fit", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bleManager = BleManager(this)
        bleManager.listener = this
        googleFitSyncManager = GoogleFitSyncManager(this)
        routeManager = RouteManager(this)
        gpsEmulatorManager = GpsEmulatorManager(this, bleManager)
        gpsEmulatorManager.listener = this

        initViews()
        setupListeners()
        setupWebViewMap()

        logToConsole("Mercury App started. Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val hcStatus = if (googleFitSyncManager.isHealthConnectAvailable()) "Available (Ready)" else "Not available on this device"
        logToConsole("Health Connect: $hcStatus")
    }

    private fun initViews() {
        // Core metrics
        tvSteps = findViewById(R.id.tvSteps)
        tvDist = findViewById(R.id.tvDist)
        tvKcal = findViewById(R.id.tvKcal)
        tvCadence = findViewById(R.id.tvCadence)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvMode = findViewById(R.id.tvMode)
        tvStatus = findViewById(R.id.tvStatus)
        tvHistoryStatus = findViewById(R.id.tvHistoryStatus)
        tvConsoleLog = findViewById(R.id.tvConsoleLog)
        tvConsoleLog.movementMethod = ScrollingMovementMethod()

        btnConnect = findViewById(R.id.btnConnect)
        btnUploadGoogleFit = findViewById(R.id.btnUploadGoogleFit)
        btnSyncHistory = findViewById(R.id.btnSyncHistory)
        btnExportJson = findViewById(R.id.btnExportJson)

        // Target Settings
        etTargetSteps = findViewById(R.id.etTargetSteps)
        etTargetDurationMin = findViewById(R.id.etTargetDurationMin)
        btnSetTargets = findViewById(R.id.btnSetTargets)
        btnClearTargets = findViewById(R.id.btnClearTargets)
        tvTargetStatus = findViewById(R.id.tvTargetStatus)
        pbStepsTarget = findViewById(R.id.pbStepsTarget)

        // Google Maps & GPS
        tvRouteInfo = findViewById(R.id.tvRouteInfo)
        webViewMap = findViewById(R.id.webViewMap)
        btnPresetRoutes = findViewById(R.id.btnPresetRoutes)
        btnSaveRoute = findViewById(R.id.btnSaveRoute)
        btnLoadRoute = findViewById(R.id.btnLoadRoute)
        btnToggleGps = findViewById(R.id.btnToggleGps)
        tvGpsStatus = findViewById(R.id.tvGpsStatus)
        btnOpenDevSettings = findViewById(R.id.btnOpenDevSettings)
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            if (bleManager.isConnected()) {
                bleManager.disconnect()
            } else {
                checkPermissionsAndScan()
            }
        }

        btnUploadGoogleFit.setOnClickListener { handleGoogleFitUpload() }
        btnSyncHistory.setOnClickListener {
            if (bleManager.isConnected()) {
                logToConsole("Requesting history from ESP32...")
                bleManager.requestHistory()
            } else {
                Toast.makeText(this, "Connect to ESP32 first", Toast.LENGTH_SHORT).show()
            }
        }
        btnExportJson.setOnClickListener {
            googleFitSyncManager.shareGoogleFitExport(this, lastHistory, lastMetrics)
        }

        // Hardware mode triggers
        findViewById<Button>(R.id.btnWalk).setOnClickListener { bleManager.setMode("WALK") }
        findViewById<Button>(R.id.btnJog).setOnClickListener { bleManager.setMode("JOG") }
        findViewById<Button>(R.id.btnPause).setOnClickListener { bleManager.setMode("PAUSE") }
        findViewById<Button>(R.id.btnAdd1000).setOnClickListener { bleManager.addSteps(1000) }

        // Target Quick Buttons
        findViewById<Button>(R.id.btnQuick5k).setOnClickListener { etTargetSteps.setText("5000") }
        findViewById<Button>(R.id.btnQuick10k).setOnClickListener { etTargetSteps.setText("10000") }
        findViewById<Button>(R.id.btnQuick15k).setOnClickListener { etTargetSteps.setText("15000") }

        findViewById<Button>(R.id.btnQuick15m).setOnClickListener { etTargetDurationMin.setText("15") }
        findViewById<Button>(R.id.btnQuick30m).setOnClickListener { etTargetDurationMin.setText("30") }
        findViewById<Button>(R.id.btnQuick60m).setOnClickListener { etTargetDurationMin.setText("60") }

        btnSetTargets.setOnClickListener { applyTargets() }
        btnClearTargets.setOnClickListener { clearTargets() }

        // Route & GPS
        btnPresetRoutes.setOnClickListener { showPresetRoutesDialog() }
        btnSaveRoute.setOnClickListener { showSaveRouteDialog() }
        btnLoadRoute.setOnClickListener { showLoadSavedRoutesDialog() }

        btnToggleGps.setOnClickListener {
            if (gpsEmulatorManager.isRunning()) {
                gpsEmulatorManager.stopEmulation()
            } else {
                if (currentRoutePoints.isEmpty()) {
                    Toast.makeText(this, "Сначала выберите или нарисуйте маршрут на карте", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                gpsEmulatorManager.startEmulation(currentRoutePoints, currentRouteName)
            }
        }

        btnOpenDevSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Откройте: Настройки -> Для разработчиков -> Выбрать фиктивные местоположения", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewMap() {
        webViewMap.settings.javaScriptEnabled = true
        webViewMap.settings.domStorageEnabled = true
        webViewMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                logToConsole("Карта Google Maps / Leaflet загружена")
                // Load default preset if empty
                val presets = RouteManager.getPresetRoutes()
                if (presets.isNotEmpty() && currentRoutePoints.isEmpty()) {
                    loadPresetRoute(presets[0])
                }
            }
        }

        // Bridge to receive route waypoints from JavaScript map
        webViewMap.addJavascriptInterface(object {
            @JavascriptInterface
            fun onRouteChanged(jsonStr: String, distMeters: Double) {
                runOnUiThread {
                    currentRouteDistanceMeters = distMeters
                    currentRoutePoints.clear()
                    try {
                        val arr = JSONArray(jsonStr)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            currentRoutePoints.add(RoutePoint(obj.getDouble("lat"), obj.getDouble("lng")))
                        }
                        val distKm = String.format(Locale.US, "%.2f", distMeters / 1000.0)
                        tvRouteInfo.text = "Маршрут: $currentRouteName | Точек: ${currentRoutePoints.size} | $distKm км"
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing route from map", e)
                    }
                }
            }

            @JavascriptInterface
            fun onApplyClicked() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Маршрут применен (${currentRoutePoints.size} точек)", Toast.LENGTH_SHORT).show()
                    logToConsole("Маршрут готов к GPS эмуляции: ${currentRoutePoints.size} точек")
                }
            }
        }, "AndroidBridge")

        webViewMap.loadUrl("file:///android_asset/map.html")
    }

    private fun applyTargets() {
        val stepsStr = etTargetSteps.text.toString().trim()
        val minStr = etTargetDurationMin.text.toString().trim()

        val steps = stepsStr.toLongOrNull() ?: 10000L
        val mins = minStr.toLongOrNull() ?: 30L
        val seconds = mins * 60L

        bleManager.setTargetGoal(steps)
        bleManager.setTargetDuration(seconds)

        val msg = "Цели установлены: $steps шагов | $mins мин ($seconds сек)"
        logToConsole(msg)
        tvTargetStatus.text = "Цели: $steps шагов | $mins мин движения"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun clearTargets() {
        bleManager.clearTargets()
        tvTargetStatus.text = "Цели сброшены (без ограничений)"
        logToConsole("Цели тренировки сброшены")
        Toast.makeText(this, "Цели сброшены", Toast.LENGTH_SHORT).show()
    }

    private fun showPresetRoutesDialog() {
        val presets = RouteManager.getPresetRoutes()
        val names = presets.map { "${it.name} (${String.format(Locale.US, "%.1f", it.distanceMeters / 1000.0)} км)" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите готовый маршрут")
            .setItems(names) { _, which ->
                loadPresetRoute(presets[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadPresetRoute(route: SavedRoute) {
        currentRouteName = route.name
        currentRoutePoints = route.points.toMutableList()
        currentRouteDistanceMeters = route.distanceMeters

        val arr = JSONArray()
        for (p in route.points) {
            val obj = org.json.JSONObject()
            obj.put("lat", p.lat)
            obj.put("lng", p.lng)
            arr.put(obj)
        }
        val jsonStr = arr.toString()
        webViewMap.evaluateJavascript("setRoutePoints('$jsonStr')", null)

        val distKm = String.format(Locale.US, "%.2f", route.distanceMeters / 1000.0)
        tvRouteInfo.text = "Маршрут: ${route.name} | Точек: ${route.points.size} | $distKm км"
        logToConsole("Загружен маршрут: ${route.name}")
    }

    private fun showSaveRouteDialog() {
        if (currentRoutePoints.isEmpty()) {
            Toast.makeText(this, "Маршрут пуст. Добавьте точки на карте.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Название маршрута"
            setText("Маршрут ${SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())}")
        }

        AlertDialog.Builder(this)
            .setTitle("Сохранить маршрут")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Маршрут" }
                val saved = routeManager.saveRoute(name, currentRoutePoints)
                currentRouteName = saved.name
                logToConsole("Маршрут '${saved.name}' сохранен")
                Toast.makeText(this, "Маршрут '${saved.name}' сохранен!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showLoadSavedRoutesDialog() {
        val savedList = routeManager.loadSavedRoutes()
        if (savedList.isEmpty()) {
            Toast.makeText(this, "Нет сохраненных маршрутов. Сохраните маршрут с карты.", Toast.LENGTH_SHORT).show()
            return
        }

        val items = savedList.map { "${it.name} (${String.format(Locale.US, "%.2f", it.distanceMeters / 1000.0)} км)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Сохраненные маршруты")
            .setItems(items) { _, which ->
                loadPresetRoute(savedList[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // GpsEmulatorManager Callbacks
    override fun onLocationUpdated(lat: Double, lng: Double, bearing: Float, speedKmh: Float, distanceTraveledMeters: Double) {
        val distKm = String.format(Locale.US, "%.2f", distanceTraveledMeters / 1000.0)
        val text = String.format(Locale.US, "GPS: %.5f, %.5f | %.1f км/ч | Пройдено: %s км", lat, lng, speedKmh, distKm)
        tvGpsStatus.text = text
        tvGpsStatus.setTextColor(0xFF00FFA3.toInt())

        // Move runner icon on map
        val js = String.format(Locale.US, "updateRunnerPosition(%.6f, %.6f, %.1f);", lat, lng, bearing)
        webViewMap.evaluateJavascript(js, null)
    }

    override fun onEmulationStateChanged(isRunning: Boolean) {
        btnToggleGps.text = if (isRunning) "⏹ Остановить GPS Эмуляцию" else "▶ Запустить GPS Эмуляцию"
        btnToggleGps.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (isRunning) 0xFFDC2626.toInt() else 0xFF22C55E.toInt()
        )
        if (!isRunning) {
            tvGpsStatus.text = "GPS: Остановлен"
            tvGpsStatus.setTextColor(0xFF94A3B8.toInt())
            webViewMap.evaluateJavascript("removeRunnerPosition();", null)
        }
    }

    override fun onMockLocationPermissionNeeded() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Требуется разрешение на фиктивное местоположение")
            .setMessage("Для эмуляции GPS на Android перейдите в:\n\n1. Настройки телефона -> 'Для разработчиков'\n2. Найдите пункт 'Выбрать приложение для фиктивных местоположений'\n3. Выберите 'ESP32 Pedometer Sync'.")
            .setPositiveButton("Открыть настройки") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Не удалось открыть настройки автоматически", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Позже", null)
            .create()
        dialog.show()
    }

    override fun onStatusMessage(msg: String) {
        logToConsole(msg)
    }

    // BLE Callbacks
    override fun onConnectionStateChange(connected: Boolean) {
        tvStatus.text = if (connected) "Connected (ESP32-C6)" else "Disconnected"
        tvStatus.setTextColor(if (connected) 0xFF00FFA3.toInt() else 0xFFEF4444.toInt())
        btnConnect.text = if (connected) "Disconnect" else "Scan & Connect"
        btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (connected) 0xFF374151.toInt() else 0xFF00FFA3.toInt()
        )
    }

    override fun onLiveMetricsReceived(metrics: StepMetrics) {
        lastMetrics = metrics
        tvSteps.text = metrics.steps.toString()
        tvDist.text = String.format(Locale.US, "%.2f km", metrics.distanceKm)
        tvKcal.text = String.format(Locale.US, "%.0f kcal", metrics.caloriesKcal)
        tvCadence.text = "${metrics.cadence} spm"
        tvSpeed.text = String.format(Locale.US, "%.1f km/h", metrics.speed)
        tvMode.text = "MODE: ${metrics.mode}"

        // Update GPS speed from ESP32
        gpsEmulatorManager.setSpeedFromEsp32(metrics.speed, metrics.mode)

        // Update Target Progress
        val targetGoal = if (metrics.targetGoal > 0) metrics.targetGoal else 10000L
        val pct = ((metrics.steps.toFloat() / targetGoal.toFloat()) * 100).toInt().coerceIn(0, 100)
        pbStepsTarget.progress = pct

        if (metrics.targetDurationSec > 0) {
            val remSec = (metrics.targetDurationSec - metrics.sessionActiveSec).coerceAtLeast(0)
            val remMin = remSec / 60
            val remS = remSec % 60
            tvTargetStatus.text = String.format(Locale.US, "Цели: %d шагов (%d%%) | Осталось: %02d:%02d", targetGoal, pct, remMin, remS)
        } else {
            tvTargetStatus.text = String.format(Locale.US, "Цель: %d шагов (%d%%)", targetGoal, pct)
        }
    }

    override fun onHistoryReceived(history: HourlyHistory) {
        lastHistory = history
        val nonZeroHours = history.hourly.count { it > 0 }
        val msg = "History synced: ${history.total} steps across $nonZeroHours active hours"
        logToConsole(msg)
        tvHistoryStatus.text = "$msg (Ready to upload)"
        tvHistoryStatus.setTextColor(0xFF38BDF8.toInt())
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onLogMessage(message: String) {
        logToConsole(message)
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            appPermissionLauncher.launch(needed.toTypedArray())
        } else {
            bleManager.startScan()
        }
    }

    private fun handleGoogleFitUpload() {
        val totalSteps = if (lastHistory.total > 0) lastHistory.total else lastMetrics.steps
        if (totalSteps == 0L) {
            Toast.makeText(this, "0 steps recorded - nothing to upload", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (googleFitSyncManager.isHealthConnectAvailable()) {
                if (googleFitSyncManager.hasHealthConnectPermissions()) {
                    performHealthConnectUpload()
                } else {
                    logToConsole("Requesting Health Connect permissions...")
                    healthPermissionLauncher.launch(GoogleFitSyncManager.HEALTH_PERMISSIONS)
                }
            } else {
                logToConsole("Health Connect not available. Trying Google Fit Play Services API...")
                if (googleFitSyncManager.hasGoogleFitApiPermission()) {
                    googleFitSyncManager.uploadToGoogleFitApi(lastHistory, lastMetrics) { _, msg ->
                        runOnUiThread {
                            logToConsole("Google Fit API: $msg")
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    googleFitSyncManager.requestGoogleFitPermission(this@MainActivity)
                }
            }
        }
    }

    private fun performHealthConnectUpload() {
        lifecycleScope.launch {
            logToConsole("Uploading to Google Fit (Health Connect)...")
            btnUploadGoogleFit.isEnabled = false
            val result = googleFitSyncManager.uploadToHealthConnect(lastHistory, lastMetrics)
            btnUploadGoogleFit.isEnabled = true

            result.onSuccess { recordCount ->
                val totalSteps = if (lastHistory.total > 0) lastHistory.total else lastMetrics.steps
                val msg = "Успех! $recordCount записей ($totalSteps шагов) экспортировано в Google Fit!"
                logToConsole("OK: $msg")
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                tvHistoryStatus.text = "Выгружено: $totalSteps шагов в Google Fit"
                tvHistoryStatus.setTextColor(0xFF00FFA3.toInt())
            }.onFailure { err ->
                val msg = "Ошибка экспорта Health Connect: ${err.message}"
                logToConsole("ERROR: $msg")
                Log.e(TAG, "Health Connect error", err)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun logToConsole(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$time] $message\n"
        tvConsoleLog.append(entry)
        val scrollAmount = tvConsoleLog.layout?.getLineTop(tvConsoleLog.lineCount)?.minus(tvConsoleLog.height) ?: 0
        if (scrollAmount > 0) {
            tvConsoleLog.scrollTo(0, scrollAmount)
        }
        Log.d(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        gpsEmulatorManager.stopEmulation()
        bleManager.disconnect()
    }
}
