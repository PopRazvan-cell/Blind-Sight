package com.BinarySquad.blindsight.ui.slideshow

import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.BinarySquad.blindsight.R
import com.BinarySquad.blindsight.databinding.FragmentSlideshowBinding

class HelpFragment : Fragment() {

    private var _binding: FragmentSlideshowBinding? = null
    private val binding get() = _binding!!
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)

        // Initialize and play the help audio
        playHelpAudio()

        return binding.root
    }

    private fun playHelpAudio() {
        // Clean up any existing MediaPlayer
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, R.raw.ajutor_2)
        try {
            mediaPlayer?.start()
            Log.d("HelpFragment", "Help audio played")
        } catch (e: Exception) {
            Log.e("HelpFragment", "Error playing help audio: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Release MediaPlayer to prevent memory leaks
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }
}