package com.BinarySquad.blindsight

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.BinarySquad.blindsight.databinding.FragmentTutorialBottomBinding

class TutorialBottomFragment : Fragment() {

    private var _binding: FragmentTutorialBottomBinding? = null
    private val binding get() = _binding!!

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentText = ""
    private val fullText = """
        👋 Bine ai venit la Blindsight!

        🔍 Cum funcționează aplicația?

        ✅ Apasă „Deschide Camera” din meniul principal.
        ✅ Ține telefonul spre obiectul dorit.
        ✅ Vei primi o descriere audio a ceea ce se vede.

        🎤 Asigură-te că:
        - Volumul este activ.
        - Ai dat permisiune la cameră.
        - Ții camera nemișcată pentru rezultate clare.

        📩 Întrebări? Scrie-ne la: support@blindsight.com
    """.trimIndent()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTutorialBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tutorialText.text = ""
        currentText = ""
        isPlaying = false

        // Single tap → play/pause
        binding.tutorialPanel.setOnClickListener {
            if (isPlaying) pauseTutorial() else startTutorial()
        }

        // Double tap → restart
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                restartTutorial()
                return true
            }
        })

        binding.tutorialPanel.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun startTutorial() {
        try {
            if (mediaPlayer == null) {
                //mediaPlayer = MediaPlayer.create(context, R.raw.tutorial_audio)
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            // Ignore audio errors
        }

        isPlaying = true
        typeText(fullText)
    }

    private fun pauseTutorial() {
        mediaPlayer?.pause()
        isPlaying = false
    }

    private fun restartTutorial() {
        pauseTutorial()
        mediaPlayer?.seekTo(0)
        currentText = ""
        binding.tutorialText.text = ""
        startTutorial()
    }

    private fun typeText(text: String, index: Int = 0) {
        if (!isPlaying || index >= text.length) return

        currentText += text[index]
        binding.tutorialText.text = currentText

        Handler(Looper.getMainLooper()).postDelayed({
            typeText(text, index + 1)
        }, 20)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }
}
