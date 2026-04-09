package com.meditation.timer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.meditation.timer.databinding.FragmentExerciseTimerBinding

class ExerciseTimerFragment : Fragment() {
    private var _binding: FragmentExerciseTimerBinding? = null
    private val binding get() = _binding!!
    private lateinit var routineManager: ExerciseRoutineManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routineManager = ExerciseRoutineManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExerciseTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addRoutineButton.setOnClickListener {
            startActivity(Intent(requireContext(), ExerciseRoutineEditActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRoutineList()
    }

    private fun refreshRoutineList() {
        val container = binding.routineListContainer
        container.removeAllViews()
        val routines = routineManager.loadRoutines()
        if (routines.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            return
        }
        binding.emptyText.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (routine in routines) {
            val card = inflater.inflate(R.layout.item_exercise_routine, container, false)
            card.findViewById<TextView>(R.id.routineCardName).text = routine.name
            val count = routine.intervals.size
            card.findViewById<TextView>(R.id.routineCardIntervalCount).text =
                if (count == 1) "1 interval" else "$count intervals"
            card.findViewById<MaterialButton>(R.id.startRoutineButton).setOnClickListener {
                startRoutineSession(routine)
            }
            card.findViewById<MaterialButton>(R.id.editRoutineButton).setOnClickListener {
                val intent = Intent(requireContext(), ExerciseRoutineEditActivity::class.java)
                intent.putExtra(ExerciseRoutineEditActivity.EXTRA_ROUTINE_ID, routine.id)
                startActivity(intent)
            }
            card.findViewById<MaterialButton>(R.id.deleteRoutineButton).setOnClickListener {
                confirmDeleteRoutine(routine)
            }
            container.addView(card)
        }
    }

    private fun startRoutineSession(routine: ExerciseRoutine) {
        if (routine.intervals.isEmpty()) {
            Toast.makeText(requireContext(), "This routine has no intervals.", Toast.LENGTH_SHORT).show()
            return
        }
        val serviceIntent = Intent(requireContext(), ExerciseTimerService::class.java).apply {
            action = ExerciseTimerService.ACTION_START
            putExtra(ExerciseTimerService.EXTRA_ROUTINE_ID, routine.id)
        }
        ContextCompat.startForegroundService(requireContext(), serviceIntent)
        startActivity(Intent(requireContext(), ExerciseSessionActivity::class.java))
    }

    private fun confirmDeleteRoutine(routine: ExerciseRoutine) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete routine")
            .setMessage("Delete \"${routine.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                routineManager.deleteRoutineById(routine.id)
                refreshRoutineList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
