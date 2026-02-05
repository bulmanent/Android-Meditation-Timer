package com.meditation.timer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PresetManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePreset(preset: Preset) {
        val presets = loadPresets().toMutableList()
        val existingIndex = presets.indexOfFirst { it.name == preset.name }
        if (existingIndex >= 0) {
            presets[existingIndex] = preset
        } else {
            presets.add(preset)
        }
        prefs.edit().putString(KEY_PRESETS, gson.toJson(presets)).apply()
    }

    fun loadPresets(): List<Preset> {
        val json = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        val type = object : TypeToken<List<Preset>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun loadPresetByName(name: String): Preset? {
        return loadPresets().firstOrNull { it.name == name }
    }

    companion object {
        private const val PREFS_NAME = "meditation_presets"
        private const val KEY_PRESETS = "presets_json"
    }
}
