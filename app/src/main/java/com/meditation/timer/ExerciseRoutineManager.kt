package com.meditation.timer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ExerciseRoutineManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRoutine(routine: ExerciseRoutine) {
        val routines = loadRoutines().toMutableList()
        val idx = routines.indexOfFirst { it.id == routine.id }
        if (idx >= 0) routines[idx] = routine else routines.add(routine)
        prefs.edit().putString(KEY_ROUTINES, gson.toJson(routines)).apply()
    }

    fun loadRoutines(): List<ExerciseRoutine> {
        val json = prefs.getString(KEY_ROUTINES, null) ?: return emptyList()
        val type = object : TypeToken<List<ExerciseRoutine>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun loadRoutineById(id: String): ExerciseRoutine? =
        loadRoutines().firstOrNull { it.id == id }

    fun deleteRoutineById(id: String) {
        val updated = loadRoutines().filterNot { it.id == id }
        prefs.edit().putString(KEY_ROUTINES, gson.toJson(updated)).apply()
    }

    companion object {
        private const val PREFS_NAME = "exercise_routines"
        private const val KEY_ROUTINES = "routines_json"
    }
}
