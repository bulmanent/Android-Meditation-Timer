package com.meditation.timer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.meditation.timer.databinding.ActivityTimerSessionBinding
import kotlin.math.roundToInt

class TimerSessionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTimerSessionBinding
    private var service: MeditationTimerService? = null
    private var isBound = false
    private var isReceiverRegistered = false
    private var isGifLoaded = false

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val remaining = intent.getLongExtra(MeditationTimerService.EXTRA_REMAINING_SECONDS, 0L)
            val total = intent.getLongExtra(MeditationTimerService.EXTRA_TOTAL_SECONDS, 0L)
            updateTimerUi(remaining, total)
            updateButtons()
            finishIfStopped()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as MeditationTimerService.LocalBinder
            service = localBinder.getService()
            updateTimerUi(service?.getRemainingSeconds() ?: 0L, service?.getTotalSeconds() ?: 0L)
            initializeVolumeControls()
            updateButtons()
            finishIfStopped()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            isBound = false
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pauseResumeButton.setOnClickListener { pauseOrResumeTimer() }
        binding.stopButton.setOnClickListener {
            stopTimer()
            finish()
        }
        binding.musicVolumeSlider.addOnChangeListener { _, value, fromUser ->
            binding.musicVolumeValue.text = formatVolumePercent(value)
            if (fromUser) {
                service?.setMusicVolume(value)
            }
        }
        binding.entrainmentVolumeSlider.addOnChangeListener { _, value, fromUser ->
            binding.entrainmentVolumeValue.text = formatVolumePercent(value)
            if (fromUser) {
                service?.setEntrainmentVolume(value)
            }
        }

        val useGif = AppSettings.isSquareBreathingGifEnabled(this)
        binding.visualModeSwitch.isChecked = useGif
        applyVisualMode(useGif)
        binding.visualModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setSquareBreathingGifEnabled(this, isChecked)
            applyVisualMode(isChecked)
        }

        binding.musicVolumeValue.text = formatVolumePercent(binding.musicVolumeSlider.value)
        binding.entrainmentVolumeValue.text = formatVolumePercent(binding.entrainmentVolumeSlider.value)
    }

    override fun onStart() {
        super.onStart()
        isBound = bindService(
            Intent(this, MeditationTimerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        val filter = IntentFilter(MeditationTimerService.ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(timerReceiver, filter)
        }
        isReceiverRegistered = true
    }

    override fun onStop() {
        super.onStop()
        if (isReceiverRegistered) {
            unregisterReceiver(timerReceiver)
            isReceiverRegistered = false
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Glide.with(this).clear(binding.breathingGif)
    }

    private fun pauseOrResumeTimer() {
        val currentState = service?.currentState ?: MeditationTimerService.TimerState.IDLE
        val action = if (currentState == MeditationTimerService.TimerState.RUNNING) {
            MeditationTimerService.ACTION_PAUSE
        } else {
            MeditationTimerService.ACTION_RESUME
        }
        startService(
            Intent(this, MeditationTimerService::class.java).apply {
                this.action = action
            }
        )
    }

    private fun stopTimer() {
        startService(
            Intent(this, MeditationTimerService::class.java).apply {
                action = MeditationTimerService.ACTION_STOP
            }
        )
    }

    private fun updateTimerUi(remainingSeconds: Long, totalSeconds: Long) {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeText = String.format("%02d:%02d", minutes, seconds)
        binding.remainingTime.text = timeText
        binding.remainingTimeBelow.text = timeText

        val fractionRemaining = if (totalSeconds > 0L) {
            remainingSeconds.toFloat() / totalSeconds.toFloat()
        } else {
            0f
        }
        binding.countdownRing.setRemainingFraction(fractionRemaining)
    }

    private fun updateButtons() {
        val state = service?.currentState ?: MeditationTimerService.TimerState.IDLE
        val isRunning = state == MeditationTimerService.TimerState.RUNNING
        val isPaused = state == MeditationTimerService.TimerState.PAUSED
        binding.pauseResumeButton.isEnabled = isRunning || isPaused
        binding.pauseResumeButton.text = if (isPaused) getString(R.string.resume) else getString(R.string.pause)
        binding.stopButton.isEnabled = isRunning || isPaused
        updateKeepScreenAwake(state)
    }

    private fun updateKeepScreenAwake(state: MeditationTimerService.TimerState) {
        val enabled = AppSettings.isKeepScreenAwakeEnabled(this)
        if (state == MeditationTimerService.TimerState.RUNNING && enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun finishIfStopped() {
        if (service?.currentState == MeditationTimerService.TimerState.IDLE) {
            finish()
        }
    }

    private fun initializeVolumeControls() {
        val serviceRef = service ?: return
        val music = serviceRef.getMusicVolume()
        val entrainment = serviceRef.getEntrainmentVolume()
        binding.musicVolumeSlider.value = music
        binding.entrainmentVolumeSlider.value = entrainment
        binding.musicVolumeValue.text = formatVolumePercent(music)
        binding.entrainmentVolumeValue.text = formatVolumePercent(entrainment)
    }

    private fun formatVolumePercent(value: Float): String {
        return "${(value * 100).roundToInt().coerceIn(0, 100)}%"
    }

    private fun applyVisualMode(useGif: Boolean) {
        binding.ringContainer.visibility = if (useGif) View.GONE else View.VISIBLE
        binding.gifContainer.visibility = if (useGif) View.VISIBLE else View.GONE
        if (useGif && !isGifLoaded) {
            Glide.with(this)
                .asGif()
                .load(R.drawable.square_breathing)
                .into(binding.breathingGif)
            isGifLoaded = true
        }
    }
}
