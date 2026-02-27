package com.meditation.timer

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "meditation_timer_settings"
    const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
    const val KEY_METRONOME_TONE_INDEX = "metronome_tone_index"
    const val KEY_USE_SQUARE_BREATHING_GIF = "use_square_breathing_gif"

    fun isKeepScreenAwakeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SCREEN_AWAKE, true)
    }

    fun setKeepScreenAwakeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled)
            .apply()
    }

    fun getMetronomeToneIndex(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_METRONOME_TONE_INDEX, 0)
    }

    fun setMetronomeToneIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_METRONOME_TONE_INDEX, index)
            .apply()
    }

    fun isSquareBreathingGifEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_SQUARE_BREATHING_GIF, false)
    }

    fun setSquareBreathingGifEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_SQUARE_BREATHING_GIF, enabled)
            .apply()
    }

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
