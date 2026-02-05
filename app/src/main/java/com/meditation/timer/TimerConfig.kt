package com.meditation.timer

data class TimerConfig(
    val durationMinutes: Int,
    val intervalMinutes: Int,
    val musicUri: String?,
    val startChimeUri: String?,
    val intervalChimeUri: String?,
    val endChimeUri: String?
)

data class Preset(
    val name: String,
    val durationMinutes: Int,
    val intervalMinutes: Int,
    val musicUri: String?,
    val startChimeUri: String?,
    val intervalChimeUri: String?,
    val endChimeUri: String?
)
