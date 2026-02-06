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
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MeditationTimerService : Service() {
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var metronomeManager: MetronomeManager? = null
    private var metronomeRunning = false
    private var metronomeBpm = 90
    private var metronomeBeats = 4
    private var metronomeVolume = 1.0f

    var currentState: TimerState = TimerState.IDLE
        private set

    private var totalSeconds: Long = 0L
    private var remainingSeconds: Long = 0L
    private var intervalSeconds: Long = 0L
    private var entrainmentUri: String? = null
    private var entrainmentVolume: Float = 1.0f
    private var musicUri: String? = null
    private var musicVolume: Float = 1.0f
    private var startChimeUri: String? = null
    private var intervalChimeUri: String? = null
    private var endChimeUri: String? = null

    private var entrainmentPlayer: MediaPlayer? = null
    private var backgroundPlayer: MediaPlayer? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (currentState != TimerState.RUNNING) {
                return
            }
            if (remainingSeconds <= 0) {
                completeSession()
                return
            }
            remainingSeconds -= 1
            val elapsedSeconds = totalSeconds - remainingSeconds
            if (intervalSeconds > 0 && elapsedSeconds > 0 && elapsedSeconds % intervalSeconds == 0L) {
                if (remainingSeconds > 0) {
                    playChime(intervalChimeUri)
                }
            }
            updateNotification()
            broadcastTick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        metronomeManager = MetronomeManager(audioManager) { onMetronomeStopped() }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_STOP -> stopSession()
            ACTION_METRONOME_START -> startMetronome(intent)
            ACTION_METRONOME_STOP -> stopMetronome()
        }
        return START_STICKY
    }

    private fun startSession(intent: Intent) {
        val durationMinutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 0)
        val intervalMinutes = intent.getIntExtra(EXTRA_INTERVAL_MINUTES, 0)
        if (durationMinutes <= 0) {
            return
        }

        totalSeconds = durationMinutes * 60L
        remainingSeconds = totalSeconds
        intervalSeconds = intervalMinutes * 60L
        entrainmentUri = intent.getStringExtra(EXTRA_ENTRAINMENT_URI)
        entrainmentVolume = intent.getFloatExtra(EXTRA_ENTRAINMENT_VOLUME, 1.0f)
        musicUri = intent.getStringExtra(EXTRA_MUSIC_URI)
        musicVolume = intent.getFloatExtra(EXTRA_MUSIC_VOLUME, 1.0f)
        startChimeUri = intent.getStringExtra(EXTRA_START_CHIME)
        intervalChimeUri = intent.getStringExtra(EXTRA_INTERVAL_CHIME)
        endChimeUri = intent.getStringExtra(EXTRA_END_CHIME)

        currentState = TimerState.RUNNING
        acquireWakeLock()
        ensureForeground()
        playChime(startChimeUri)
        startEntrainmentAudio(entrainmentUri)
        startBackgroundMusic(musicUri)
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, 1000L)
        broadcastTick()
    }

    private fun pauseSession() {
        if (currentState != TimerState.RUNNING) {
            return
        }
        currentState = TimerState.PAUSED
        handler.removeCallbacks(tickRunnable)
        entrainmentPlayer?.pause()
        backgroundPlayer?.pause()
        updateNotification()
        broadcastTick()
    }

    private fun resumeSession() {
        if (currentState != TimerState.PAUSED) {
            return
        }
        currentState = TimerState.RUNNING
        entrainmentPlayer?.start()
        backgroundPlayer?.start()
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, 1000L)
        updateNotification()
        broadcastTick()
    }

    private fun stopSession() {
        currentState = TimerState.IDLE
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        stopBackgroundMusic()
        stopEntrainmentAudio()
        if (metronomeRunning) {
            updateNotification()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        broadcastTick()
    }

    private fun completeSession() {
        playChime(endChimeUri)
        stopSession()
    }

    private fun startBackgroundMusic(uriString: String?) {
        stopBackgroundMusic()
        if (uriString.isNullOrBlank()) {
            return
        }
        val uri = Uri.parse(uriString)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        try {
            backgroundPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(this@MeditationTimerService, uri)
                isLooping = true
                setVolume(musicVolume, musicVolume)
                prepare()
                start()
            }
        } catch (exception: Exception) {
            stopBackgroundMusic()
        }
    }

    private fun stopBackgroundMusic() {
        backgroundPlayer?.stop()
        backgroundPlayer?.release()
        backgroundPlayer = null
    }

    private fun startEntrainmentAudio(uriString: String?) {
        stopEntrainmentAudio()
        if (uriString.isNullOrBlank()) {
            return
        }
        val uri = Uri.parse(uriString)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        try {
            entrainmentPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(this@MeditationTimerService, uri)
                isLooping = true
                setVolume(entrainmentVolume, entrainmentVolume)
                prepare()
                start()
            }
        } catch (exception: Exception) {
            stopEntrainmentAudio()
        }
    }

    private fun stopEntrainmentAudio() {
        entrainmentPlayer?.stop()
        entrainmentPlayer?.release()
        entrainmentPlayer = null
    }

    private fun playChime(uriString: String?) {
        if (uriString.isNullOrBlank()) {
            return
        }
        val uri = Uri.parse(uriString)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(audioAttributes)
            player.setDataSource(this, uri)
            player.setOnCompletionListener { it.release() }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                true
            }
            player.prepare()
            player.start()
        } catch (exception: Exception) {
            player.release()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeditationTimer::WakeLock")
        wakeLock?.acquire(60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun broadcastTick() {
        val intent = Intent(ACTION_TICK).apply {
            putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
            putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = when {
            currentState == TimerState.RUNNING && metronomeRunning ->
                "Timer: ${formatTime(remainingSeconds)} \u2022 Metronome"
            currentState == TimerState.RUNNING ->
                "Timer: ${formatTime(remainingSeconds)}"
            currentState == TimerState.PAUSED && metronomeRunning ->
                "Timer paused \u2022 Metronome"
            currentState == TimerState.PAUSED ->
                "Timer paused"
            metronomeRunning ->
                "Metronome running"
            else ->
                "Meditation timer idle"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meditation Timer")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
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
                CHANNEL_ID,
                "Meditation Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val remaining = seconds % 60
        return String.format("%02d:%02d", minutes, remaining)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        releaseWakeLock()
        stopBackgroundMusic()
        stopEntrainmentAudio()
        stopMetronomeInternal()
        metronomeManager?.release()
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        fun getService(): MeditationTimerService = this@MeditationTimerService
    }

    fun setEntrainmentVolume(volume: Float) {
        entrainmentVolume = volume
        entrainmentPlayer?.setVolume(volume, volume)
    }

    fun setMusicVolume(volume: Float) {
        musicVolume = volume
        backgroundPlayer?.setVolume(volume, volume)
    }

    fun stopEntrainmentPlayback() {
        stopEntrainmentAudio()
    }

    fun stopMusicPlayback() {
        stopBackgroundMusic()
    }

    fun isMetronomeRunning(): Boolean = metronomeRunning

    fun getMetronomeBpm(): Int = metronomeBpm

    fun getMetronomeBeats(): Int = metronomeBeats

    fun getMetronomeVolume(): Float = metronomeVolume

    fun setMetronomeVolume(volume: Float) {
        metronomeVolume = volume.coerceIn(0f, 1f)
        metronomeManager?.setVolume(metronomeVolume)
    }

    fun updateMetronome(bpm: Int, beats: Int) {
        metronomeBpm = bpm
        metronomeBeats = beats
        metronomeManager?.configure(metronomeBpm, metronomeBeats)
        if (metronomeRunning) {
            updateNotification()
        }
    }

    private fun startMetronome(intent: Intent) {
        metronomeBpm = intent.getIntExtra(EXTRA_METRONOME_BPM, metronomeBpm)
        metronomeBeats = intent.getIntExtra(EXTRA_METRONOME_BEATS, metronomeBeats)
        metronomeVolume = intent.getFloatExtra(EXTRA_METRONOME_VOLUME, metronomeVolume)
        metronomeManager?.configure(metronomeBpm, metronomeBeats)
        metronomeManager?.setVolume(metronomeVolume)
        metronomeRunning = true
        metronomeManager?.start()
        ensureForeground()
        updateNotification()
    }

    private fun stopMetronome() {
        stopMetronomeInternal()
        updateNotification()
        if (currentState == TimerState.IDLE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopMetronomeInternal() {
        if (metronomeRunning) {
            metronomeRunning = false
            metronomeManager?.stop()
        }
    }

    private fun onMetronomeStopped() {
        metronomeRunning = false
        updateNotification()
        if (currentState == TimerState.IDLE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    enum class TimerState {
        IDLE,
        RUNNING,
        PAUSED
    }

    companion object {
        const val ACTION_START = "com.meditation.timer.START"
        const val ACTION_PAUSE = "com.meditation.timer.PAUSE"
        const val ACTION_RESUME = "com.meditation.timer.RESUME"
        const val ACTION_STOP = "com.meditation.timer.STOP"
        const val ACTION_TICK = "com.meditation.timer.TICK"
        const val ACTION_METRONOME_START = "com.meditation.timer.METRONOME_START"
        const val ACTION_METRONOME_STOP = "com.meditation.timer.METRONOME_STOP"

        const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
        const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
        const val EXTRA_ENTRAINMENT_URI = "extra_entrainment_uri"
        const val EXTRA_ENTRAINMENT_VOLUME = "extra_entrainment_volume"
        const val EXTRA_MUSIC_URI = "extra_music_uri"
        const val EXTRA_MUSIC_VOLUME = "extra_music_volume"
        const val EXTRA_START_CHIME = "extra_start_chime"
        const val EXTRA_INTERVAL_CHIME = "extra_interval_chime"
        const val EXTRA_END_CHIME = "extra_end_chime"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"
        const val EXTRA_TOTAL_SECONDS = "extra_total_seconds"
        const val EXTRA_METRONOME_BPM = "extra_metronome_bpm"
        const val EXTRA_METRONOME_BEATS = "extra_metronome_beats"
        const val EXTRA_METRONOME_VOLUME = "extra_metronome_volume"

        private const val CHANNEL_ID = "meditation_timer"
        private const val NOTIFICATION_ID = 1001
    }

    private fun ensureForeground() {
        startForeground(NOTIFICATION_ID, buildNotification())
    }
}
