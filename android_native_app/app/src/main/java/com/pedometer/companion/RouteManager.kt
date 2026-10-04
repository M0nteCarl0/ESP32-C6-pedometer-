package com.pedometer.companion

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.*

data class RoutePoint(val lat: Double, val lng: Double)

data class SavedRoute(
    val id: String,
    val name: String,
    val points: List<RoutePoint>,
    val distanceMeters: Double
)

class RouteManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mercury_saved_routes", Context.MODE_PRIVATE)

    companion object {
        const val KEY_ROUTES = "saved_routes_json"

        fun calculateDistance(p1: RoutePoint, p2: RoutePoint): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(p2.lat - p1.lat)
            val dLng = Math.toRadians(p2.lng - p1.lng)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(p1.lat)) * cos(Math.toRadians(p2.lat)) *
                    sin(dLng / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        fun calculateTotalDistance(points: List<RoutePoint>): Double {
            if (points.size < 2) return 0.0
            var total = 0.0
            for (i in 0 until points.size - 1) {
                total += calculateDistance(points[i], points[i + 1])
            }
            return total
        }

        fun calculateBearing(p1: RoutePoint, p2: RoutePoint): Float {
            val lat1 = Math.toRadians(p1.lat)
            val lat2 = Math.toRadians(p2.lat)
            val dLng = Math.toRadians(p2.lng - p1.lng)
            val y = sin(dLng) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
            var b = Math.toDegrees(atan2(y, x)).toFloat()
            if (b < 0) b += 360f
            return b
        }

        /**
         * Returns interpolated point and bearing along route at specified distance from start.
         * Loops seamlessly if distance exceeds total distance!
         */
        fun getPointAtDistance(points: List<RoutePoint>, distanceTraveledMeters: Double): Pair<RoutePoint, Float> {
            if (points.isEmpty()) return Pair(RoutePoint(55.7558, 37.6173), 0f)
            if (points.size == 1) return Pair(points[0], 0f)

            val totalDist = calculateTotalDistance(points)
            if (totalDist <= 0.0) return Pair(points[0], 0f)

            // Loop distance around the circuit
            val currentDist = distanceTraveledMeters % totalDist

            var accumulated = 0.0
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val segDist = calculateDistance(p1, p2)
                if (accumulated + segDist >= currentDist) {
                    val remaining = currentDist - accumulated
                    val ratio = if (segDist > 0) (remaining / segDist) else 0.0
                    val lat = p1.lat + (p2.lat - p1.lat) * ratio
                    val lng = p1.lng + (p2.lng - p1.lng) * ratio
                    val bearing = calculateBearing(p1, p2)
                    return Pair(RoutePoint(lat, lng), bearing)
                }
                accumulated += segDist
            }
            val lastP1 = points[points.size - 2]
            val lastP2 = points[points.size - 1]
            return Pair(lastP2, calculateBearing(lastP1, lastP2))
        }

        fun getPresetRoutes(): List<SavedRoute> {
            return listOf(
                SavedRoute(
                    id = "preset_gorky_park",
                    name = "Парк Горького (Круг 2.8 км)",
                    points = listOf(
                        RoutePoint(55.731456, 37.603416),
                        RoutePoint(55.728912, 37.597923),
                        RoutePoint(55.723145, 37.591245),
                        RoutePoint(55.719823, 37.584912),
                        RoutePoint(55.717120, 37.593210),
                        RoutePoint(55.722415, 37.601420),
                        RoutePoint(55.728120, 37.606820),
                        RoutePoint(55.731456, 37.603416)
                    ),
                    distanceMeters = 2820.0
                ),
                SavedRoute(
                    id = "preset_luzhniki_stadium",
                    name = "Стадион Лужники (Круг 1.4 км)",
                    points = listOf(
                        RoutePoint(55.716120, 37.553920),
                        RoutePoint(55.717450, 37.551210),
                        RoutePoint(55.719210, 37.552840),
                        RoutePoint(55.719110, 37.556210),
                        RoutePoint(55.717230, 37.557420),
                        RoutePoint(55.716120, 37.553920)
                    ),
                    distanceMeters = 1410.0
                ),
                SavedRoute(
                    id = "preset_kremlin_embankment",
                    name = "Кремлевская Набережная (3.6 км)",
                    points = listOf(
                        RoutePoint(55.748920, 37.608410),
                        RoutePoint(55.751210, 37.616230),
                        RoutePoint(55.750120, 37.625840),
                        RoutePoint(55.748120, 37.636120),
                        RoutePoint(55.744920, 37.644120),
                        RoutePoint(55.742120, 37.640120),
                        RoutePoint(55.745120, 37.632120),
                        RoutePoint(55.747120, 37.621210),
                        RoutePoint(55.748920, 37.608410)
                    ),
                    distanceMeters = 3600.0
                )
            )
        }
    }

    fun saveRoute(name: String, points: List<RoutePoint>): SavedRoute {
        val dist = calculateTotalDistance(points)
        val id = UUID.randomUUID().toString()
        val route = SavedRoute(id, name, points, dist)
        val existing = loadSavedRoutes().toMutableList()
        existing.add(0, route)
        persistRoutes(existing)
        return route
    }

    fun deleteRoute(id: String) {
        val existing = loadSavedRoutes().toMutableList()
        existing.removeAll { it.id == id }
        persistRoutes(existing)
    }

    fun loadSavedRoutes(): List<SavedRoute> {
        val json = prefs.getString(KEY_ROUTES, null) ?: return emptyList()
        val list = mutableListOf<SavedRoute>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val dist = obj.getDouble("distance")
                val ptsArr = obj.getJSONArray("points")
                val pts = mutableListOf<RoutePoint>()
                for (j in 0 until ptsArr.length()) {
                    val pObj = ptsArr.getJSONObject(j)
                    pts.add(RoutePoint(pObj.getDouble("lat"), pObj.getDouble("lng")))
                }
                list.add(SavedRoute(id, name, pts, dist))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun persistRoutes(routes: List<SavedRoute>) {
        val arr = JSONArray()
        for (r in routes) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("name", r.name)
            obj.put("distance", r.distanceMeters)
            val ptsArr = JSONArray()
            for (p in r.points) {
                val pObj = JSONObject()
                pObj.put("lat", p.lat)
                pObj.put("lng", p.lng)
                ptsArr.put(pObj)
            }
            obj.put("points", ptsArr)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ROUTES, arr.toString()).apply()
    }
}
