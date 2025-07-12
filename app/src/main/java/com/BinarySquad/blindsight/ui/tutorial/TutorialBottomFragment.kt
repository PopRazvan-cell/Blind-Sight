package com.BinarySquad.blindsight

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.BinarySquad.blindsight.databinding.FragmentTutorialBottomBinding

class TutorialBottomFragment : Fragment() {

    private var _binding: FragmentTutorialBottomBinding? = null
    private val binding get() = _binding!!

    private var isPlaying = false
    private var lastTapTime = 0L
    private val doubleTapTimeout = 300L
    private val handler = Handler(Looper.getMainLooper())

    private val sentences = listOf(
        "👋 Bine ai venit la Blindsight!",
        "🔍 Cum funcționează aplicația?",
        "✅ Apasă „Deschide Camera” din meniul principal.",
        "✅ Ține telefonul spre obiectul dorit.",
        "✅ Vei primi o descriere audio a ceea ce se vede.",
        "🎤 Asigură-te că:",
        "- Volumul este activ.",
        "- Ai dat permisiune la cameră.",
        "- Ții camera nemișcată pentru rezultate clare.",
        "📩 Întrebări? Scrie-ne la: support@blindsight.com"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTutorialBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        resetTutorial()

        // Setup gesture for dismiss
        var downY = 0f
        var downX = 0f

        binding.tutorialPanel.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.y
                    downX = event.x
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val deltaY = event.y - downY
                    val deltaX = event.x - downX

                    if (deltaY > 150 && kotlin.math.abs(deltaX) < 100) {
                        parentFragmentManager.popBackStack()
                        return@setOnTouchListener true
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastTapTime <= doubleTapTimeout) {
                        restartTutorial()
                        lastTapTime = 0
                    } else {
                        if (isPlaying) pauseTutorial() else startTutorial()
                        lastTapTime = now
                    }
                    return@setOnTouchListener true
                }

                else -> false
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.popBackStack()
                }
            }
        )
    }

    private fun startTutorial() {
        isPlaying = true
        showAllTutorialTextInstantly()
    }

    private fun pauseTutorial() {
        isPlaying = false
    }

    private fun restartTutorial() {
        resetTutorial()
        startTutorial()
    }

    private fun resetTutorial() {
        isPlaying = false
        binding.tutorialTextContainer.removeAllViews()
    }

    private fun showAllTutorialTextInstantly() {
        binding.tutorialTextContainer.removeAllViews()

        val tts = (activity as? MainActivity)?.tts
        val fullText = sentences.joinToString("\n")

        binding.tutorialContainer.setOnClickListener {
            tts?.speak(fullText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
        }

        for (line in sentences) {
            val textView = TextView(requireContext()).apply {
                text = line
                setTextColor(Color.parseColor("#E0FFE0"))
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8)
            }
            binding.tutorialTextContainer.addView(textView)
        }

        handler.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
