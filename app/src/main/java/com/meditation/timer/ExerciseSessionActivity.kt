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
import com.meditation.timer.databinding.ActivityExerciseSessionBinding

class ExerciseSessionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExerciseSessionBinding
    private var service: ExerciseTimerService? = null
    private var isBound = false
    private var isReceiverRegistered = false

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateUi(intent)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as ExerciseTimerService.LocalBinder
            service = localBinder.getService()
            isBound = true
            if (service?.sessionState == ExerciseTimerService.SessionState.IDLE) {
                finish()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            isBound = false
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.pauseResumeButton.setOnClickListener {
            val svc = service ?: return@setOnClickListener
            val action = if (svc.sessionState == ExerciseTimerService.SessionState.RUNNING) {
                ExerciseTimerService.ACTION_PAUSE
            } else {
                ExerciseTimerService.ACTION_RESUME
            }
            startService(Intent(this, ExerciseTimerService::class.java).apply { this.action = action })
        }

        binding.skipButton.setOnClickListener {
            startService(Intent(this, ExerciseTimerService::class.java).apply {
                action = ExerciseTimerService.ACTION_SKIP
            })
        }

        binding.stopButton.setOnClickListener {
            startService(Intent(this, ExerciseTimerService::class.java).apply {
                action = ExerciseTimerService.ACTION_STOP
            })
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        isBound = bindService(
            Intent(this, ExerciseTimerService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        val filter = IntentFilter(ExerciseTimerService.ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(tickReceiver, filter)
        }
        isReceiverRegistered = true
    }

    override fun onStop() {
        super.onStop()
        if (isReceiverRegistered) {
            unregisterReceiver(tickReceiver)
            isReceiverRegistered = false
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun updateUi(intent: Intent) {
        val stateStr = intent.getStringExtra(ExerciseTimerService.EXTRA_SESSION_STATE)
        val state = stateStr?.let {
            try { ExerciseTimerService.SessionState.valueOf(it) } catch (_: Exception) { null }
        }

        if (state == ExerciseTimerService.SessionState.IDLE) {
            finish()
            return
        }

        val isGap = intent.getBooleanExtra(ExerciseTimerService.EXTRA_IS_GAP, false)
        val remaining = intent.getLongExtra(ExerciseTimerService.EXTRA_REMAINING_SECONDS, 0L)
        val intervalIndex = intent.getIntExtra(ExerciseTimerService.EXTRA_INTERVAL_INDEX, 0)
        val totalIntervals = intent.getIntExtra(ExerciseTimerService.EXTRA_TOTAL_INTERVALS, 0)
        val intervalName = intent.getStringExtra(ExerciseTimerService.EXTRA_INTERVAL_NAME) ?: ""
        val nextIntervalName = intent.getStringExtra(ExerciseTimerService.EXTRA_NEXT_INTERVAL_NAME) ?: ""

        val mins = remaining / 60
        val secs = remaining % 60
        binding.countdownDisplay.text = String.format("%02d:%02d", mins, secs)
        binding.progressLabel.text = "${intervalIndex + 1} of $totalIntervals"
        binding.intervalNameLabel.text = intervalName

        if (isGap) {
            binding.gapSection.visibility = View.VISIBLE
            binding.nextIntervalName.text = nextIntervalName
        } else {
            binding.gapSection.visibility = View.GONE
        }

        val isPaused = state == ExerciseTimerService.SessionState.PAUSED
        binding.pauseResumeButton.text = if (isPaused) getString(R.string.resume) else getString(R.string.pause)
        binding.pausedLabel.visibility = if (isPaused) View.VISIBLE else View.GONE
    }
}
