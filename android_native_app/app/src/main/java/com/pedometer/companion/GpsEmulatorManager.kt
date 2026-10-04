package com.pedometer.companion

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale

class GpsEmulatorManager(
    private val context: Context,
    private val bleManager: BleManager
) {

    companion object {
        const val TAG = "GpsEmulatorManager"
    }

    interface GpsEmulatorListener {
        fun onLocationUpdated(lat: Double, lng: Double, bearing: Float, speedKmh: Float, distanceTraveledMeters: Double)
        fun onEmulationStateChanged(isRunning: Boolean)
        fun onMockLocationPermissionNeeded()
        fun onStatusMessage(msg: String)
    }

    var listener: GpsEmulatorListener? = null

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())

    private var isEmulating = false
    private var activeRoutePoints: List<RoutePoint> = emptyList()
    private var distanceTraveledMeters = 0.0
    private var currentSpeedKmh = 4.5f // Default walking speed 4.5 km/h
    private var isTestProviderAdded = false

    private val emulationRunnable = object : Runnable {
        override fun run() {
            if (!isEmulating) return

            // If we have route points, step forward along the polyline
            if (activeRoutePoints.isNotEmpty()) {
                val speedMps = (currentSpeedKmh / 3.6f)
                distanceTraveledMeters += speedMps

                val (pt, bearing) = RouteManager.getPointAtDistance(activeRoutePoints, distanceTraveledMeters)
                injectMockLocation(pt.lat, pt.lng, bearing, speedMps)

                // Notify BLE device so ESP32 screen shows current GPS coords
                val gpsCmd = String.format(Locale.US, "GPS:%.6f,%.6f", pt.lat, pt.lng)
                bleManager.sendCommand(gpsCmd)

                listener?.onLocationUpdated(pt.lat, pt.lng, bearing, currentSpeedKmh, distanceTraveledMeters)
            }

            handler.postDelayed(this, 1000) // 1 Hz tick
        }
    }

    fun isRunning(): Boolean = isEmulating

    fun setSpeedFromEsp32(speedKmh: Float, mode: String) {
        if (mode.equals("PAUSED", ignoreCase = true)) {
            currentSpeedKmh = 0f
        } else if (speedKmh > 0.5f) {
            currentSpeedKmh = speedKmh
        } else {
            currentSpeedKmh = when (mode.uppercase()) {
                "JOG" -> 7.0f
                "RUN" -> 10.5f
                else -> 4.5f
            }
        }
    }

    fun startEmulation(points: List<RoutePoint>, routeName: String = "Active Route"): Boolean {
        if (points.isEmpty()) {
            listener?.onStatusMessage("Ошибка: маршрут пуст. Добавьте точки на карте.")
            return false
        }

        activeRoutePoints = points
        if (!setupMockProvider()) {
            listener?.onMockLocationPermissionNeeded()
            return false
        }

        isEmulating = true
        distanceTraveledMeters = 0.0
        handler.removeCallbacks(emulationRunnable)
        handler.post(emulationRunnable)

        // Send Route Name to ESP32
        bleManager.sendCommand("ROUTE:$routeName")

        listener?.onEmulationStateChanged(true)
        listener?.onStatusMessage("GPS Эмуляция запущена: $routeName (${points.size} точек)")
        return true
    }

    fun stopEmulation() {
        if (!isEmulating) return
        isEmulating = false
        handler.removeCallbacks(emulationRunnable)
        cleanupMockProvider()
        listener?.onEmulationStateChanged(false)
        listener?.onStatusMessage("GPS Эмуляция остановлена")
    }

    private fun setupMockProvider(): Boolean {
        try {
            if (!isTestProviderAdded) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(
                        LocationManager.GPS_PROVIDER,
                        false, false, false, false, true, true, true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        LocationManager.GPS_PROVIDER,
                        false, false, false, false, true, true, true,
                        1, 1
                    )
                }
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
                isTestProviderAdded = true
            }
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Mock location not permitted", e)
            isTestProviderAdded = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error adding test provider", e)
            // If provider already exists, try enabling it
            try {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
                isTestProviderAdded = true
                return true
            } catch (e2: Exception) {
                return false
            }
        }
    }

    private fun cleanupMockProvider() {
        if (isTestProviderAdded) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing test provider", e)
            }
            isTestProviderAdded = false
        }
    }

    private fun injectMockLocation(lat: Double, lng: Double, bearing: Float, speedMps: Float) {
        try {
            val loc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = lat
                longitude = lng
                altitude = 150.0 + (Math.random() * 2.0 - 1.0) // Small natural variation
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                accuracy = 3.0f + (Math.random().toFloat() * 1.5f)
                speed = speedMps
                this.bearing = bearing
            }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject mock location", e)
        }
    }
}
