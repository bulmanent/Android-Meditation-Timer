package com.meditation.timer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.meditation.timer.databinding.FragmentMetronomeBinding
import kotlin.math.roundToInt

class MetronomeFragment : Fragment() {
    private var _binding: FragmentMetronomeBinding? = null
    private val binding get() = _binding!!
    private var service: MeditationTimerService? = null
    private var isBound = false
    private var suppressUpdates = false
    private var toneIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMetronomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bpmSlider.value = 90f
        binding.beatsPerBarSlider.value = 4f
        binding.bpmInput.setText("90")
        binding.beatsPerBarInput.setText("4")
        binding.metronomeVolumeSlider.value = 100f
        binding.metronomeVolumeValue.text = "100%"
        updateStatus(false)

        val toneLabels = MetronomeToneOptions.options.map { it.label }
        val toneAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, toneLabels)
        binding.metronomeToneDropdown.setAdapter(toneAdapter)
        toneIndex = AppSettings.getMetronomeToneIndex(requireContext())
        binding.metronomeToneDropdown.setText(toneLabels.getOrNull(toneIndex) ?: toneLabels.first())
        binding.metronomeToneDropdown.setOnItemClickListener { _, _, position, _ ->
            toneIndex = position
            AppSettings.setMetronomeToneIndex(requireContext(), position)
            service?.setMetronomeToneIndex(position)
        }

        binding.bpmSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !suppressUpdates) {
                binding.bpmInput.setText(value.roundToInt().toString())
                updateServiceConfigIfRunning()
            }
        }
        binding.beatsPerBarSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !suppressUpdates) {
                binding.beatsPerBarInput.setText(value.roundToInt().toString())
                updateServiceConfigIfRunning()
            }
        }
        binding.metronomeVolumeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !suppressUpdates) {
                val percent = value.roundToInt().coerceIn(0, 100)
                binding.metronomeVolumeValue.text = "$percent%"
                service?.setMetronomeVolume(percent / 100f)
            }
        }

        binding.bpmInput.doAfterTextChanged { text ->
            if (suppressUpdates) return@doAfterTextChanged
            val bpm = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
            if (bpm in 30..200) {
                suppressUpdates = true
                binding.bpmSlider.value = bpm.toFloat()
                suppressUpdates = false
                updateServiceConfigIfRunning()
            }
        }

        binding.beatsPerBarInput.doAfterTextChanged { text ->
            if (suppressUpdates) return@doAfterTextChanged
            val beats = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
            if (beats in 1..12) {
                suppressUpdates = true
                binding.beatsPerBarSlider.value = beats.toFloat()
                suppressUpdates = false
                updateServiceConfigIfRunning()
            }
        }

        binding.metronomeStartButton.setOnClickListener {
            val bpm = binding.bpmInput.text?.toString()?.toIntOrNull() ?: 90
            val beats = binding.beatsPerBarInput.text?.toString()?.toIntOrNull() ?: 4
            val volumePercent = binding.metronomeVolumeSlider.value.roundToInt().coerceIn(0, 100)
            val volume = volumePercent / 100f
            if (bpm < 30 || bpm > 200) {
                Toast.makeText(requireContext(), "Tempo must be between 30 and 200 BPM.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (beats < 1 || beats > 12) {
                Toast.makeText(requireContext(), "Beats per bar must be between 1 and 12.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), MeditationTimerService::class.java).apply {
                action = MeditationTimerService.ACTION_METRONOME_START
                putExtra(MeditationTimerService.EXTRA_METRONOME_BPM, bpm)
                putExtra(MeditationTimerService.EXTRA_METRONOME_BEATS, beats)
                putExtra(MeditationTimerService.EXTRA_METRONOME_VOLUME, volume)
                putExtra(MeditationTimerService.EXTRA_METRONOME_TONE_INDEX, toneIndex)
            }
            ContextCompat.startForegroundService(requireContext(), intent)
            updateStatus(true)
        }

        binding.metronomeStopButton.setOnClickListener {
            val intent = Intent(requireContext(), MeditationTimerService::class.java).apply {
                action = MeditationTimerService.ACTION_METRONOME_STOP
            }
            requireContext().startService(intent)
            updateStatus(false)
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), MeditationTimerService::class.java)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateStatus(running: Boolean) {
        binding.metronomeStatus.text = if (running) {
            getString(R.string.metronome_status_running)
        } else {
            getString(R.string.metronome_status_stopped)
        }
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName, binder: android.os.IBinder) {
            val localBinder = binder as MeditationTimerService.LocalBinder
            service = localBinder.getService()
            isBound = true
            syncFromService()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName) {
            service = null
            isBound = false
        }
    }

    private fun syncFromService() {
        val boundService = service ?: return
        suppressUpdates = true
        binding.bpmInput.setText(boundService.getMetronomeBpm().toString())
        binding.beatsPerBarInput.setText(boundService.getMetronomeBeats().toString())
        binding.bpmSlider.value = boundService.getMetronomeBpm().toFloat()
        binding.beatsPerBarSlider.value = boundService.getMetronomeBeats().toFloat()
        val percent = (boundService.getMetronomeVolume() * 100).roundToInt().coerceIn(0, 100)
        binding.metronomeVolumeSlider.value = percent.toFloat()
        binding.metronomeVolumeValue.text = "$percent%"
        toneIndex = boundService.getMetronomeToneIndex()
        val toneLabels = MetronomeToneOptions.options.map { it.label }
        binding.metronomeToneDropdown.setText(
            toneLabels.getOrNull(toneIndex) ?: toneLabels.first()
        )
        suppressUpdates = false
        updateStatus(boundService.isMetronomeRunning())
    }

    private fun updateServiceConfigIfRunning() {
        val boundService = service ?: return
        if (!boundService.isMetronomeRunning()) return
        val bpm = binding.bpmInput.text?.toString()?.toIntOrNull() ?: return
        val beats = binding.beatsPerBarInput.text?.toString()?.toIntOrNull() ?: return
        if (bpm !in 30..200 || beats !in 1..12) return
        boundService.updateMetronome(bpm, beats)
    }
}
