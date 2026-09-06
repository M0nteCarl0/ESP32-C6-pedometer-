package com.pedometer.companion

data class StepMetrics(
    val steps: Long = 0,
    val cadence: Int = 0,
    val speed: Float = 0f,
    val distanceKm: Float = 0f,
    val caloriesKcal: Float = 0f,
    val activeSeconds: Long = 0,
    val targetGoal: Long = 10000,
    val mode: String = "PAUSED"
)

data class HourlyHistory(
    val hourly: List<Int> = List(24) { 0 },
    val daily: List<Long> = List(7) { 0 },
    val total: Long = 0
)
