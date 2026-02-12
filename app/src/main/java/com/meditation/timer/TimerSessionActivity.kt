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
import androidx.appcompat.app.AppCompatActivity
import com.meditation.timer.databinding.ActivityTimerSessionBinding

class TimerSessionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTimerSessionBinding
    private var service: MeditationTimerService? = null
    private var isBound = false

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
            updateButtons()
            finishIfStopped()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
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
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MeditationTimerService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        isBound = true

        val filter = IntentFilter(MeditationTimerService.ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(timerReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(timerReceiver)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
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
        binding.remainingTime.text = String.format("%02d:%02d", minutes, seconds)

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
    }

    private fun finishIfStopped() {
        if (service?.currentState == MeditationTimerService.TimerState.IDLE) {
            finish()
        }
    }
}
