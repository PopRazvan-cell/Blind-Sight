package com.BinarySquad.blindsight

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.activity.OnBackPressedCallback
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

        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                restartTutorial()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val deltaY = e2.y - (e1?.y ?: 0f)
                if (deltaY > 150 && velocityY > 200) {
                    parentFragmentManager.popBackStack() // Close on swipe down
                    return true
                }
                return false
            }


            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isPlaying) pauseTutorial() else startTutorial()
                return true
            }
        })

        binding.tutorialPanel.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // Close on back press
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.popBackStack()
                }
            })
    }

    private fun startTutorial() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(context, R.raw.ajutor_2)
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            // fail silently
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
