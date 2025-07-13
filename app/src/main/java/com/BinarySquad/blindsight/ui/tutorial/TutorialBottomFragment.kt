package com.BinarySquad.blindsight

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.view.*
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.BinarySquad.blindsight.databinding.FragmentTutorialBottomBinding

class TutorialBottomFragment : Fragment() {

    private var _binding: FragmentTutorialBottomBinding? = null
    private val binding get() = _binding!!

    private var isPlaying = false
    private var isPaused = false
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

    private var sentenceIndex = 0
    private var wordIndexInSentence = 0
    private val sentenceTextViews = mutableListOf<TextView>()
    private var tts: TextToSpeech? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTutorialBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tts = (activity as? MainActivity)?.tts
        setupTtsListener()
        resetTutorial()

        var downX = 0f
        var downY = 0f

        binding.tutorialPanel.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY

                    if (deltaY > 150 && kotlin.math.abs(deltaX) < 100) {
                        parentFragmentManager.popBackStack()
                        return@setOnTouchListener true
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < doubleTapTimeout) {
                        restartTutorial()
                        lastTapTime = 0
                    } else {
                        if (isPlaying) pauseTutorial() else startTutorial()
                        lastTapTime = now
                    }
                    true
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

    private fun resetTutorial() {
        isPlaying = false
        isPaused = false
        sentenceIndex = 0
        wordIndexInSentence = 0
        sentenceTextViews.clear()
        binding.tutorialTextContainer.removeAllViews()

        for (line in sentences) {
            val textView = TextView(requireContext()).apply {
                text = line
                setTextColor(Color.parseColor("#E0FFE0"))
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8)
                alpha = 1f
            }
            sentenceTextViews.add(textView)
            binding.tutorialTextContainer.addView(textView)
        }

        handler.post {
            binding.scrollView.fullScroll(View.FOCUS_UP)
        }
    }

    private fun startTutorial() {
        if (sentences.isEmpty()) return
        isPlaying = true
        isPaused = false
        speakNextWord()
    }

    private fun pauseTutorial() {
        isPlaying = false
        isPaused = true
        tts?.stop()
    }

    private fun restartTutorial() {
        tts?.stop()
        resetTutorial()
        startTutorial()
    }

    private fun highlightCurrentWord() {
        if (sentenceIndex >= sentences.size) return

        val sentence = sentences[sentenceIndex]
        val words = sentence.split(" ")

        if (wordIndexInSentence >= words.size) return

        val highlightedSentence = words.mapIndexed { i, word ->
            if (i == wordIndexInSentence) "<b><u>$word</u></b>" else word
        }.joinToString(" ")

        handler.post {
            sentenceTextViews[sentenceIndex].text = Html.fromHtml(highlightedSentence, Html.FROM_HTML_MODE_LEGACY)

            // Fade previous lines gradually
            for (i in 0 until sentenceIndex) {
                val alphaValue = 1f - 0.2f * (sentenceIndex - i) // decrease alpha for older lines
                sentenceTextViews[i].alpha = alphaValue.coerceAtLeast(0.2f)
            }

            // Ensure current line full opacity
            sentenceTextViews[sentenceIndex].alpha = 1f

            // Scroll so current sentence is visible
            binding.scrollView.smoothScrollTo(0, sentenceTextViews[sentenceIndex].top)
        }
    }

    private fun speakNextWord() {
        if (!isPlaying || isPaused) return
        if (sentenceIndex >= sentences.size) {
            // Finished all sentences
            isPlaying = false
            return
        }

        val words = sentences[sentenceIndex].split(" ")
        if (wordIndexInSentence >= words.size) {
            // Move to next sentence
            sentenceIndex++
            wordIndexInSentence = 0
            speakNextWord()
            return
        }

        highlightCurrentWord()

        val word = words[wordIndexInSentence]
        val params = Bundle()
        val utteranceId = "word_${sentenceIndex}_$wordIndexInSentence"
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}

            override fun onDone(utteranceId: String) {
                if (!isPlaying || isPaused) return

                // Increase word index after each spoken word
                wordIndexInSentence++
                handler.post { speakNextWord() }
            }

            override fun onError(utteranceId: String) {}
        })
    }

    override fun onDestroyView() {
        tts?.stop()
        _binding = null
        super.onDestroyView()
    }
}
