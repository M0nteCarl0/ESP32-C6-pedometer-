package com.pedometer.companion

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.concurrent.TimeUnit

class GoogleFitSyncManager(private val context: Context) {

    companion object {
        const val GOOGLE_FIT_REQUEST_CODE = 1002

        val HEALTH_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
        )

        val GOOGLE_FIT_OPTIONS: FitnessOptions by lazy {
            FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_WRITE)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_WRITE)
                .build()
        }
    }

    private val healthConnectClient: HealthConnectClient? by lazy {
        if (isHealthConnectAvailable()) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    fun isHealthConnectAvailable(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasHealthConnectPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(HEALTH_PERMISSIONS)
    }

    suspend fun uploadToHealthConnect(
        history: HourlyHistory,
        metrics: StepMetrics
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val client = healthConnectClient
                ?: return@withContext Result.failure(IllegalStateException("Health Connect is not available on this device"))

            if (!hasHealthConnectPermissions()) {
                return@withContext Result.failure(SecurityException("Health Connect permissions not granted"))
            }

            val records = mutableListOf<Record>()
            val today = LocalDate.now()
            val zoneId = ZoneId.systemDefault()
            val zoneOffset = zoneId.rules.getOffset(Instant.now())
            var totalExported = 0L

            val hasHourlyData = history.hourly.any { it > 0 }

            if (hasHourlyData) {
                history.hourly.forEachIndexed { hour, steps ->
                    if (steps > 0) {
                        val startZdt = today.atTime(hour, 0, 0).atZone(zoneId)
                        val endZdt = if (hour == ZonedDateTime.now().hour) {
                            ZonedDateTime.now().minusSeconds(1).let {
                                if (it.isBefore(startZdt)) startZdt.plusSeconds(30) else it
                            }
                        } else {
                            today.atTime(hour, 59, 59).atZone(zoneId)
                        }

                        val startInstant = startZdt.toInstant()
                        val endInstant = endZdt.toInstant()

                        if (endInstant.isAfter(startInstant)) {
                            // 1. Steps Record
                            records.add(
                                StepsRecord(
                                    count = steps.toLong(),
                                    startTime = startInstant,
                                    startZoneOffset = zoneOffset,
                                    endTime = endInstant,
                                    endZoneOffset = zoneOffset
                                )
                            )

                            // 2. Distance Record (meters)
                            val distMeters = steps * 0.75
                            records.add(
                                DistanceRecord(
                                    distance = Length.meters(distMeters),
                                    startTime = startInstant,
                                    startZoneOffset = zoneOffset,
                                    endTime = endInstant,
                                    endZoneOffset = zoneOffset
                                )
                            )

                            // 3. Calories Record (kcal)
                            val kcal = steps * 0.045
                            records.add(
                                TotalCaloriesBurnedRecord(
                                    energy = Energy.kilocalories(kcal),
                                    startTime = startInstant,
                                    startZoneOffset = zoneOffset,
                                    endTime = endInstant,
                                    endZoneOffset = zoneOffset
                                )
                            )
                            totalExported += steps
                        }
                    }
                }
            } else if (metrics.steps > 0) {
                // Fallback: Use live metrics steps over today
                val now = Instant.now()
                val activeSec = if (metrics.activeSeconds > 0) metrics.activeSeconds else 3600
                val start = now.minusSeconds(activeSec)

                records.add(
                    StepsRecord(
                        count = metrics.steps,
                        startTime = start,
                        startZoneOffset = zoneOffset,
                        endTime = now,
                        endZoneOffset = zoneOffset
                    )
                )

                if (metrics.distanceKm > 0) {
                    records.add(
                        DistanceRecord(
                            distance = Length.meters((metrics.distanceKm * 1000).toDouble()),
                            startTime = start,
                            startZoneOffset = zoneOffset,
                            endTime = now,
                            endZoneOffset = zoneOffset
                        )
                    )
                }

                if (metrics.caloriesKcal > 0) {
                    records.add(
                        TotalCaloriesBurnedRecord(
                            energy = Energy.kilocalories(metrics.caloriesKcal.toDouble()),
                            startTime = start,
                            startZoneOffset = zoneOffset,
                            endTime = now,
                            endZoneOffset = zoneOffset
                        )
                    )
                }
                totalExported = metrics.steps
            }

            if (records.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No step data to export (0 steps)"))
            }

            client.insertRecords(records)
            Result.success(records.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasGoogleFitApiPermission(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, GOOGLE_FIT_OPTIONS)
    }

    fun requestGoogleFitPermission(activity: Activity) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        GoogleSignIn.requestPermissions(
            activity,
            GOOGLE_FIT_REQUEST_CODE,
            account,
            GOOGLE_FIT_OPTIONS
        )
    }

    fun uploadToGoogleFitApi(
        history: HourlyHistory,
        metrics: StepMetrics,
        onResult: (Boolean, String) -> Unit
    ) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null || !GoogleSignIn.hasPermissions(account, GOOGLE_FIT_OPTIONS)) {
            onResult(false, "Google Fit account not connected or permissions not granted")
            return
        }

        try {
            val dataSource = DataSource.Builder()
                .setAppPackageName(context.packageName)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName("ESP32-C6-Pedometer")
                .setType(DataSource.TYPE_RAW)
                .build()

            val dataSet = DataSet.builder(dataSource)
            val cal = Calendar.getInstance()
            val nowMs = System.currentTimeMillis()
            var addedPoints = 0

            val hasHourly = history.hourly.any { it > 0 }

            if (hasHourly) {
                history.hourly.forEachIndexed { hour, steps ->
                    if (steps > 0) {
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        val startMs = cal.timeInMillis

                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        var endMs = cal.timeInMillis
                        if (endMs > nowMs) endMs = nowMs

                        if (endMs > startMs) {
                            val dataPoint = DataPoint.builder(dataSource)
                                .setTimeInterval(startMs, endMs, TimeUnit.MILLISECONDS)
                                .setField(Field.FIELD_STEPS, steps)
                                .build()
                            dataSet.add(dataPoint)
                            addedPoints++
                        }
                    }
                }
            } else if (metrics.steps > 0) {
                val activeSec = if (metrics.activeSeconds > 0) metrics.activeSeconds else 3600
                val startMs = nowMs - (activeSec * 1000)
                val dataPoint = DataPoint.builder(dataSource)
                    .setTimeInterval(startMs, nowMs, TimeUnit.MILLISECONDS)
                    .setField(Field.FIELD_STEPS, metrics.steps.toInt())
                    .build()
                dataSet.add(dataPoint)
                addedPoints++
            }

            if (addedPoints == 0) {
                onResult(false, "No non-zero steps to insert")
                return
            }

            Fitness.getHistoryClient(context, account)
                .insertData(dataSet.build())
                .addOnSuccessListener {
                    onResult(true, "Successfully uploaded $addedPoints data points to Google Fit API!")
                }
                .addOnFailureListener { e ->
                    onResult(false, "Google Fit upload failed: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            onResult(false, "Error building Google Fit data: ${e.message}")
        }
    }

    fun generateGoogleFitJson(history: HourlyHistory, metrics: StepMetrics): String {
        val root = JSONObject()
        root.put("device", "Waveshare ESP32-C6-LCD-1.47")
        root.put("exportDate", Instant.now().toString())

        val summary = JSONObject()
        summary.put("totalSteps", if (history.total > 0) history.total else metrics.steps)
        summary.put("totalDistanceKm", metrics.distanceKm)
        summary.put("totalCaloriesKcal", metrics.caloriesKcal)
        summary.put("activeSeconds", metrics.activeSeconds)
        summary.put("targetGoal", metrics.targetGoal)
        root.put("summary", summary)

        val buckets = JSONArray()
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        history.hourly.forEachIndexed { hour, steps ->
            val bucket = JSONObject()
            bucket.put("hour", hour)
            val hStr = String.format("%02d", hour)
            bucket.put("startTime", "${todayStr}T${hStr}:00:00Z")
            bucket.put("endTime", "${todayStr}T${hStr}:59:59Z")
            bucket.put("stepCount", steps)
            bucket.put("distanceMeters", (steps * 0.75).toInt())
            bucket.put("caloriesBurned", (steps * 0.045).toInt())
            buckets.put(bucket)
        }
        root.put("hourlyBuckets", buckets)

        return root.toString(2)
    }

    fun shareGoogleFitExport(activity: Activity, history: HourlyHistory, metrics: StepMetrics) {
        val jsonStr = generateGoogleFitJson(history, metrics)
        val file = File(activity.cacheDir, "google_fit_steps_${LocalDate.now()}.json")
        file.writeText(jsonStr)

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Google Fit Steps Export (${LocalDate.now()})")
            putExtra(Intent.EXTRA_TEXT, "Exported ${metrics.steps} steps from ESP32-C6 Pedometer Simulator")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        activity.startActivity(Intent.createChooser(shareIntent, "Share Google Fit Data"))
    }
}
