package com.pedometer.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), BleManager.BleEventListener {

    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var bleManager: BleManager
    private lateinit var googleFitSyncManager: GoogleFitSyncManager

    // UI Elements
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

    // Cached Metrics
    private var lastMetrics = StepMetrics()
    private var lastHistory = HourlyHistory()

    // Activity Result Launcher for Bluetooth Permissions
    private val btPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            logToConsole("Bluetooth permissions granted! Starting scan...")
            bleManager.startScan()
        } else {
            logToConsole("Error: Bluetooth permissions denied by user.")
            Toast.makeText(this, "Bluetooth permissions required to connect to ESP32", Toast.LENGTH_LONG).show()
        }
    }

    // Activity Result Launcher for Health Connect Permissions
    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(GoogleFitSyncManager.HEALTH_PERMISSIONS)) {
            logToConsole("Health Connect permissions granted! Uploading data...")
            performHealthConnectUpload()
        } else {
            logToConsole("Health Connect permissions partially or not granted: $grantedPermissions")
            Toast.makeText(this, "Health Connect permissions required to upload to Google Fit", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bleManager = BleManager(this)
        bleManager.listener = this
        googleFitSyncManager = GoogleFitSyncManager(this)

        initViews()
        setupListeners()

        logToConsole("Companion App started. Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val hcStatus = if (googleFitSyncManager.isHealthConnectAvailable()) "Available (Ready)" else "Not available on this device"
        logToConsole("Health Connect: $hcStatus")
    }

    private fun initViews() {
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
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            if (bleManager.isConnected()) {
                bleManager.disconnect()
            } else {
                checkPermissionsAndScan()
            }
        }

        btnUploadGoogleFit.setOnClickListener {
            handleGoogleFitUpload()
        }

        btnSyncHistory.setOnClickListener {
            if (bleManager.isConnected()) {
                logToConsole("Requesting history dump from ESP32...")
                bleManager.requestHistory()
            } else {
                Toast.makeText(this, "Connect to ESP32 first", Toast.LENGTH_SHORT).show()
            }
        }

        btnExportJson.setOnClickListener {
            googleFitSyncManager.shareGoogleFitExport(this, lastHistory, lastMetrics)
        }

        findViewById<Button>(R.id.btnWalk).setOnClickListener { bleManager.setMode("WALK") }
        findViewById<Button>(R.id.btnJog).setOnClickListener { bleManager.setMode("JOG") }
        findViewById<Button>(R.id.btnPause).setOnClickListener { bleManager.setMode("PAUSE") }
        findViewById<Button>(R.id.btnAdd1000).setOnClickListener { bleManager.addSteps(1000) }
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            btPermissionLauncher.launch(needed.toTypedArray())
        } else {
            bleManager.startScan()
        }
    }

    private fun handleGoogleFitUpload() {
        val totalSteps = if (lastHistory.total > 0) lastHistory.total else lastMetrics.steps
        if (totalSteps == 0L) {
            Toast.makeText(this, "No steps to upload (0 steps recorded)", Toast.LENGTH_SHORT).show()
            logToConsole("Upload aborted: 0 steps.")
            return
        }

        lifecycleScope.launch {
            if (googleFitSyncManager.isHealthConnectAvailable()) {
                if (googleFitSyncManager.hasHealthConnectPermissions()) {
                    performHealthConnectUpload()
                } else {
                    logToConsole("Requesting Health Connect permissions for Google Fit...")
                    healthPermissionLauncher.launch(GoogleFitSyncManager.HEALTH_PERMISSIONS)
                }
            } else {
                // Fallback to Google Fit Play Services API or share JSON
                logToConsole("Health Connect not available. Attempting Google Fit API...")
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
            logToConsole("Uploading to Health Connect (Google Fit)...")
            btnUploadGoogleFit.isEnabled = false
            val result = googleFitSyncManager.uploadToHealthConnect(lastHistory, lastMetrics)
            btnUploadGoogleFit.isEnabled = true

            result.onSuccess { recordCount ->
                val totalSteps = if (lastHistory.total > 0) lastHistory.total else lastMetrics.steps
                val msg = "Success! $recordCount records ($totalSteps steps) exported to Google Fit / Health Connect!"
                logToConsole("OK: $msg")
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                tvHistoryStatus.text = "Last exported: $totalSteps steps to Google Fit"
                tvHistoryStatus.setTextColor(0xFF00FFA3.toInt())
            }.onFailure { err ->
                val msg = "Health Connect upload error: ${err.message}"
                logToConsole("ERROR: $msg")
                Log.e(TAG, "Health Connect error", err)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
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
        bleManager.disconnect()
    }
}
