package com.meditation.timer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.meditation.timer.databinding.ActivityExerciseRoutineEditBinding
import java.util.UUID

class ExerciseRoutineEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExerciseRoutineEditBinding
    private lateinit var routineManager: ExerciseRoutineManager

    private var routineId: String? = null
    private var currentChimeUri: Uri? = null
    private var currentMusicUri: Uri? = null
    private val intervals = mutableListOf<ExerciseInterval>()

    private var pendingPickerTarget: PickerTarget? = null

    private val chimePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, "Unable to persist bell sound permission.") { picked ->
            currentChimeUri = picked
            binding.chimeFilename.text = picked.lastPathSegment ?: picked.toString()
        }
    }

    private val musicPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, "Unable to persist music permission.") { picked ->
            currentMusicUri = picked
            binding.musicFilename.text = picked.lastPathSegment ?: picked.toString()
        }
    }

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchPendingPicker()
        else Toast.makeText(this, "Media access is required to pick audio files.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseRoutineEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.editToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        routineManager = ExerciseRoutineManager(this)

        routineId = intent.getStringExtra(EXTRA_ROUTINE_ID)
        if (routineId != null) {
            supportActionBar?.title = getString(R.string.edit_routine_title)
            loadExistingRoutine(routineId!!)
        } else {
            supportActionBar?.title = getString(R.string.new_routine_title)
        }

        binding.selectChimeButton.setOnClickListener {
            pendingPickerTarget = PickerTarget.CHIME
            ensureAudioPermissionAndPick()
        }
        binding.clearChimeButton.setOnClickListener {
            currentChimeUri = null
            binding.chimeFilename.text = getString(R.string.chime_none)
        }
        binding.selectMusicButton.setOnClickListener {
            pendingPickerTarget = PickerTarget.MUSIC
            ensureAudioPermissionAndPick()
        }
        binding.clearMusicButton.setOnClickListener {
            currentMusicUri = null
            binding.musicFilename.text = getString(R.string.music_none)
        }
        binding.addIntervalButton.setOnClickListener {
            showIntervalEditDialog(null, -1)
        }
        binding.saveRoutineButton.setOnClickListener {
            saveRoutine()
        }
    }

    private fun loadExistingRoutine(id: String) {
        val routine = routineManager.loadRoutineById(id) ?: return
        binding.routineNameInput.setText(routine.name)
        currentChimeUri = routine.chimeUri?.let { Uri.parse(it) }
        binding.chimeFilename.text = currentChimeUri?.lastPathSegment ?: getString(R.string.chime_none)
        currentMusicUri = routine.musicUri?.let { Uri.parse(it) }
        binding.musicFilename.text = currentMusicUri?.lastPathSegment ?: getString(R.string.music_none)
        intervals.clear()
        intervals.addAll(routine.intervals)
        refreshIntervalList()
    }

    private fun refreshIntervalList() {
        val container = binding.intervalListContainer
        container.removeAllViews()
        if (intervals.isEmpty()) {
            binding.noIntervalsText.visibility = android.view.View.VISIBLE
            return
        }
        binding.noIntervalsText.visibility = android.view.View.GONE
        for ((index, interval) in intervals.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_exercise_interval, container, false)
            row.findViewById<TextView>(R.id.intervalItemName).text = interval.name
            val mins = interval.durationSeconds / 60
            val secs = interval.durationSeconds % 60
            row.findViewById<TextView>(R.id.intervalItemDuration).text =
                String.format("%d:%02d", mins, secs)
            row.findViewById<MaterialButton>(R.id.intervalMoveUpButton).apply {
                isEnabled = index > 0
                setOnClickListener { moveInterval(index, index - 1) }
            }
            row.findViewById<MaterialButton>(R.id.intervalMoveDownButton).apply {
                isEnabled = index < intervals.size - 1
                setOnClickListener { moveInterval(index, index + 1) }
            }
            row.findViewById<MaterialButton>(R.id.intervalEditButton).setOnClickListener {
                showIntervalEditDialog(interval, index)
            }
            row.findViewById<MaterialButton>(R.id.intervalDeleteButton).setOnClickListener {
                intervals.removeAt(index)
                refreshIntervalList()
            }
            container.addView(row)
        }
    }

    private fun moveInterval(fromIndex: Int, toIndex: Int) {
        val item = intervals.removeAt(fromIndex)
        intervals.add(toIndex, item)
        refreshIntervalList()
    }

    private fun showIntervalEditDialog(interval: ExerciseInterval?, index: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_interval_edit, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.dialogIntervalName)
        val minsInput = dialogView.findViewById<EditText>(R.id.dialogIntervalMinutes)
        val secsInput = dialogView.findViewById<EditText>(R.id.dialogIntervalSeconds)

        if (interval != null) {
            nameInput.setText(interval.name)
            minsInput.setText((interval.durationSeconds / 60).toString())
            secsInput.setText((interval.durationSeconds % 60).toString())
        }

        val title = if (interval == null) getString(R.string.add_interval_title)
        else getString(R.string.edit_interval_title)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text.toString().ifBlank { "Interval" }
                val mins = minsInput.text.toString().toIntOrNull() ?: 0
                val secs = secsInput.text.toString().toIntOrNull() ?: 0
                val totalSecs = mins * 60 + secs
                if (totalSecs < 1) {
                    Toast.makeText(this, "Duration must be at least 1 second.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updated = ExerciseInterval(
                    id = interval?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    durationSeconds = totalSecs
                )
                if (index >= 0) intervals[index] = updated else intervals.add(updated)
                refreshIntervalList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveRoutine() {
        val name = binding.routineNameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Routine name is required.", Toast.LENGTH_SHORT).show()
            return
        }
        if (intervals.isEmpty()) {
            Toast.makeText(this, "Add at least one interval.", Toast.LENGTH_SHORT).show()
            return
        }
        val routine = ExerciseRoutine(
            id = routineId ?: UUID.randomUUID().toString(),
            name = name,
            intervals = intervals.toList(),
            chimeUri = currentChimeUri?.toString(),
            musicUri = currentMusicUri?.toString()
        )
        routineManager.saveRoutine(routine)
        Toast.makeText(this, "Routine saved.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun ensureAudioPermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchPendingPicker()
            } else {
                requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            launchPendingPicker()
        }
    }

    private fun launchPendingPicker() {
        when (pendingPickerTarget) {
            PickerTarget.CHIME -> chimePicker.launch(arrayOf("audio/*"))
            PickerTarget.MUSIC -> musicPicker.launch(arrayOf("audio/*"))
            null -> Unit
        }
        pendingPickerTarget = null
    }

    private fun handlePickedUri(uri: Uri?, errorMessage: String, onSuccess: (Uri) -> Unit) {
        if (uri == null) return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            return
        }
        onSuccess(uri)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    enum class PickerTarget { CHIME, MUSIC }

    companion object {
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
    }
}
