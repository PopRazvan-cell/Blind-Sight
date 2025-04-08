package com.binary_squad.blind_sight.ui.bikeride

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.binary_squad.blind_sight.R
import com.binary_squad.blind_sight.databinding.FragmentBikeRideBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

class BikeRideFragment : Fragment() {

    private var _binding: FragmentBikeRideBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBikeRideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.checkWeatherButton.setOnClickListener {
            checkWeather()
        }
    }

    private fun checkWeather() {
        binding.progressBar.visibility = View.VISIBLE
        binding.resultTextView.visibility = View.GONE

        // Simulate weather check with a delay
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                // Simulate network delay
                Thread.sleep(2000)
            }

            val random = Random()
            val weatherCondition = random.nextInt(3)

            binding.progressBar.visibility = View.GONE
            binding.resultTextView.visibility = View.VISIBLE

            when (weatherCondition) {
                0 -> {
                    binding.resultTextView.text = getString(R.string.perfect_day)
                    binding.resultTextView.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                }
                1 -> {
                    binding.resultTextView.text = getString(R.string.good_day)
                    binding.resultTextView.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
                }
                else -> {
                    binding.resultTextView.text = getString(R.string.not_recommended)
                    binding.resultTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 