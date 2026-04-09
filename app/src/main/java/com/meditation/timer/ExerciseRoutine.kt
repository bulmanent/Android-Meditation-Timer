package com.meditation.timer

data class ExerciseRoutine(
    val id: String,
    val name: String,
    val intervals: List<ExerciseInterval>,
    val chimeUri: String?,
    val musicUri: String?
)
