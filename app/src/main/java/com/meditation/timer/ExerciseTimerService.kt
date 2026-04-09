package com.meditation.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ExerciseTimerService : Service() {

    enum class SessionState { IDLE, RUNNING, PAUSED }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private var routine: ExerciseRoutine? = null
    private var currentIntervalIndex = 0
    private var remainingSeconds = 0L
    var sessionState = SessionState.IDLE
        private set
    private var isInGap = false

    private var musicPlayer: MediaPlayer? = null
    private val activeChimePlayers = mutableSetOf<MediaPlayer>()

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (sessionState != SessionState.RUNNING) return
            if (remainingSeconds <= 0) {
                advancePhase()
                return
            }
            remainingSeconds--
            broadcastTick()
            updateNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_STOP -> stopSession()
            ACTION_SKIP -> skipCurrent()
        }
        return START_STICKY
    }

    private fun startSession(intent: Intent) {
        val routineId = intent.getStringExtra(EXTRA_ROUTINE_ID) ?: return
        val loaded = ExerciseRoutineManager(this).loadRoutineById(routineId) ?: return
        if (loaded.intervals.isEmpty()) return

        routine = loaded
        currentIntervalIndex = 0
        isInGap = false
        sessionState = SessionState.RUNNING

        acquireWakeLock()
        startBackgroundMusic(loaded.musicUri)
        ensureForeground()
        startInterval()
    }

    private fun startInterval() {
        val r = routine ?: return
        val interval = r.intervals[currentIntervalIndex]
        remainingSeconds = interval.durationSeconds.toLong()
        isInGap = false
        broadcastTick()
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, 1000L)
    }

    private fun advancePhase() {
        val r = routine ?: return
        if (isInGap) {
            // Gap ended: start next interval
            currentIntervalIndex++
            if (currentIntervalIndex >= r.intervals.size) {
                stopSessionInternal()
                return
            }
            startInterval()
        } else {
            // Interval ended: play chime
            if (currentIntervalIndex + 1 >= r.intervals.size) {
                // Last interval: chime then stop
                playChime(r.chimeUri) { stopSessionInternal() }
                broadcastTick()
            } else {
                // More intervals: chime, then 5-second gap (chime plays concurrently)
                playChime(r.chimeUri)
                isInGap = true
                remainingSeconds = GAP_SECONDS
                broadcastTick()
                handler.removeCallbacks(tickRunnable)
                handler.postDelayed(tickRunnable, 1000L)
            }
        }
    }

    private fun pauseSession() {
        if (sessionState != SessionState.RUNNING) return
        sessionState = SessionState.PAUSED
        handler.removeCallbacks(tickRunnable)
        musicPlayer?.pause()
        updateNotification()
        broadcastTick()
    }

    private fun resumeSession() {
        if (sessionState != SessionState.PAUSED) return
        sessionState = SessionState.RUNNING
        musicPlayer?.start()
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, 1000L)
        updateNotification()
        broadcastTick()
    }

    private fun stopSession() {
        stopSessionInternal()
    }

    private fun stopSessionInternal() {
        sessionState = SessionState.IDLE
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        stopBackgroundMusic()
        synchronized(activeChimePlayers) {
            activeChimePlayers.forEach { it.release() }
            activeChimePlayers.clear()
        }
        routine = null
        broadcastTick()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun skipCurrent() {
        if (sessionState != SessionState.RUNNING) return
        handler.removeCallbacks(tickRunnable)
        remainingSeconds = 0
        advancePhase()
    }

    private fun startBackgroundMusic(uriString: String?) {
        stopBackgroundMusic()
        if (uriString.isNullOrBlank()) return
        val uri = Uri.parse(uriString)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        try {
            musicPlayer = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@ExerciseTimerService, uri)
                isLooping = true
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                setOnErrorListener { mp, _, _ -> mp.release(); musicPlayer = null; true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            stopBackgroundMusic()
        }
    }

    private fun stopBackgroundMusic() {
        musicPlayer?.release()
        musicPlayer = null
    }

    private fun playChime(uriString: String?, onComplete: (() -> Unit)? = null) {
        if (uriString.isNullOrBlank()) {
            onComplete?.invoke()
            return
        }
        val uri = Uri.parse(uriString)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val player = MediaPlayer()
        synchronized(activeChimePlayers) { activeChimePlayers.add(player) }
        try {
            player.setAudioAttributes(attrs)
            player.setDataSource(this, uri)
            player.setOnCompletionListener {
                releaseChimePlayer(it)
                onComplete?.invoke()
            }
            player.setOnErrorListener { mp, _, _ ->
                releaseChimePlayer(mp)
                onComplete?.invoke()
                true
            }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        } catch (e: Exception) {
            releaseChimePlayer(player)
            onComplete?.invoke()
        }
    }

    private fun releaseChimePlayer(player: MediaPlayer) {
        synchronized(activeChimePlayers) { activeChimePlayers.remove(player) }
        player.release()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ExerciseTimer::WakeLock")
        wakeLock?.acquire(4 * 60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun broadcastTick() {
        val r = routine
        val intent = Intent(ACTION_TICK).apply {
            putExtra(EXTRA_SESSION_STATE, sessionState.name)
            putExtra(EXTRA_IS_GAP, isInGap)
            putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
            putExtra(EXTRA_INTERVAL_INDEX, currentIntervalIndex)
            putExtra(EXTRA_TOTAL_INTERVALS, r?.intervals?.size ?: 0)
            putExtra(EXTRA_INTERVAL_NAME, r?.intervals?.getOrNull(currentIntervalIndex)?.name ?: "")
            putExtra(EXTRA_NEXT_INTERVAL_NAME, r?.intervals?.getOrNull(currentIntervalIndex + 1)?.name ?: "")
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, ExerciseSessionActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val r = routine
        val contentText = when {
            sessionState == SessionState.RUNNING && r != null -> {
                val name = r.intervals.getOrNull(currentIntervalIndex)?.name ?: "Exercise"
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                "$name \u2013 %02d:%02d".format(mins, secs)
            }
            sessionState == SessionState.PAUSED -> "Exercise timer paused"
            else -> "Exercise timer"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exercise Timer")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Exercise Timer", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun ensureForeground() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        stopBackgroundMusic()
        synchronized(activeChimePlayers) {
            activeChimePlayers.forEach { it.release() }
            activeChimePlayers.clear()
        }
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        fun getService(): ExerciseTimerService = this@ExerciseTimerService
    }

    fun getCurrentIntervalName(): String {
        val r = routine ?: return ""
        return r.intervals.getOrNull(currentIntervalIndex)?.name ?: ""
    }

    fun getNextIntervalName(): String {
        val r = routine ?: return ""
        return r.intervals.getOrNull(currentIntervalIndex + 1)?.name ?: ""
    }

    fun getRemainingSeconds(): Long = remainingSeconds

    fun getCurrentIntervalIndex(): Int = currentIntervalIndex

    fun getTotalIntervals(): Int = routine?.intervals?.size ?: 0

    fun isInGapPhase(): Boolean = isInGap

    companion object {
        const val ACTION_START = "com.meditation.timer.exercise.START"
        const val ACTION_PAUSE = "com.meditation.timer.exercise.PAUSE"
        const val ACTION_RESUME = "com.meditation.timer.exercise.RESUME"
        const val ACTION_STOP = "com.meditation.timer.exercise.STOP"
        const val ACTION_SKIP = "com.meditation.timer.exercise.SKIP"
        const val ACTION_TICK = "com.meditation.timer.exercise.TICK"

        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val EXTRA_SESSION_STATE = "extra_session_state"
        const val EXTRA_IS_GAP = "extra_is_gap"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"
        const val EXTRA_INTERVAL_INDEX = "extra_interval_index"
        const val EXTRA_TOTAL_INTERVALS = "extra_total_intervals"
        const val EXTRA_INTERVAL_NAME = "extra_interval_name"
        const val EXTRA_NEXT_INTERVAL_NAME = "extra_next_interval_name"

        private const val CHANNEL_ID = "exercise_timer"
        private const val NOTIFICATION_ID = 1002
        private const val GAP_SECONDS = 5L
        private const val MUSIC_VOLUME = 0.3f
    }
}
