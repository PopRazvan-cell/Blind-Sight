package com.BinarySquad.blindsight

import android.graphics.Color
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.*
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.BinarySquad.blindsight.databinding.FragmentTutorialBottomBinding

class TutorialBottomFragment : Fragment() {

    private var _binding: FragmentTutorialBottomBinding? = null
    private val binding get() = _binding!!

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isPaused = false

    private var lastTapTime = 0L
    private val doubleTapTimeout = 300L

    private val sentences = listOf(
        "👋 Bun venit la Blindsight, aplicația care te ajută să identifici obiectele din jur prin descrieri audio.",
        "🔍 Pentru a folosi aplicația, ține camera telefonului îndreptată spre obiectul pe care vrei să-l detectezi.",
        "Aplicația va analiza imaginea și îți va oferi o descriere audio a obiectului detectat.?",
        "✅ Pentru a deschide tutorialul audio în orice moment, fă un swipe în sus pe ecran.",
        "Pentru a închide tutorialul, fă un swipe în jos.",
        "✅ Apasă o dată pe ecran pentru a pune pauză sau pentru a relua tutorialul audio.",
        "Dacă faci dublu tap, tutorialul va reporni de la început.",
        "✅ Dacă faci swipe de la stânga la dreapta, se va deschide meniul principal.",
        "Swipe-ul de la dreapta la stânga va închide meniul.",
        "🎤Pentru setări, fă swipe în direcția opusă: swipe de la dreapta la stânga pentru a deschide setările, ",
        "iar swipe de la stânga la dreapta pentru a le închide.",
        "Pentru a auzi o descriere audio a unui buton din aplicație, atinge-l o singură dată.",
        "Pentru a activa funcția butonului, dă dublu tap pe el.",
        "Asigură-te că volumul este activ și că ai dat permisiunile necesare pentru cameră. ",
        "De asemenea, ține telefonul nemișcat pentru o detectare mai precisă.",
        "📩 Dacă ai întrebări sau ai nevoie de asistență, ne poți contacta la adresa de email blindsight2025@gmail.com",
        "Mulțumim că folosești Blindsight!"
    )

    private var initialY = 0f
    private var deltaY = 0f

    // Pentru text cuvânt cu cuvânt
    private var currentSentenceIndex = 0
    private var currentWordIndex = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTutorialBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // OPRESTE detectia si orice mp3 de detectie
        (activity as? MainActivity)?.pauseDetection()
        (activity as? MainActivity)?.stopDetectionAudio()  // pune asta in MainActivity, trebuie să oprești mp3 detectie

        binding.tutorialTextContainer.removeAllViews()
        binding.tutorialTextContainer.alpha = 1f

        // Start media player cu LoudnessEnhancer
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.intro2)
        mediaPlayer?.setVolume(1f, 1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val enhancer = LoudnessEnhancer(mediaPlayer!!.audioSessionId)
                enhancer.setTargetGain(1500)
                enhancer.enabled = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer?.start()
        isPlaying = true
        isPaused = false

        currentSentenceIndex = 0
        currentWordIndex = 0
        binding.tutorialTextContainer.removeAllViews()

        // Start animatie cuvânt cu cuvânt
        showNextWord()

        // Gesturi swipe/dublu tap / tap simplu pentru pauza/play
        binding.tutorialPanel.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> initialY = event.rawY
                MotionEvent.ACTION_MOVE -> {
                    deltaY = event.rawY - initialY
                    if (deltaY > 200 || deltaY < -200) {
                        closeTutorial()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < doubleTapTimeout) {
                        // dublu tap = restart audio și animație
                        mediaPlayer?.seekTo(0)
                        mediaPlayer?.start()
                        isPlaying = true
                        isPaused = false
                        currentSentenceIndex = 0
                        currentWordIndex = 0
                        binding.tutorialTextContainer.removeAllViews()
                        showNextWord()
                    } else {
                        // tap simplu = pauză / redare
                        if (isPlaying) {
                            pauseAudioAndAnimation()
                        } else {
                            resumeAudioAndAnimation()
                        }
                    }
                    lastTapTime = currentTime
                }
            }
            true
        }
    }

    private fun pauseAudioAndAnimation() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
            isPaused = true
            handler.removeCallbacksAndMessages(null)  // oprește animatia
        }
    }

    private fun resumeAudioAndAnimation() {
        if (isPaused) {
            mediaPlayer?.start()
            isPlaying = true
            isPaused = false
            showNextWord() // reia animatia de unde a ramas
        }
    }

    // Animatie text cuvânt cu cuvânt, cu fade in pentru cuvinte noi,
    // liniile vechi fac fade out si dispar.
    private fun showNextWord() {
        if (!isPlaying) return

        if (currentSentenceIndex >= sentences.size) {
            // Am terminat toate propozițiile
            return
        }

        val words = sentences[currentSentenceIndex].split(" ")
        if (currentWordIndex == 0) {
            // La început de propoziție: adaug un TextView nou pentru linia curentă
            val tv = TextView(requireContext()).apply {
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                alpha = 0f
                setPadding(16, 8, 16, 8)
            }
            binding.tutorialTextContainer.addView(tv)
        }

        val currentTextView = binding.tutorialTextContainer.getChildAt(binding.tutorialTextContainer.childCount - 1) as TextView

        // Adaugă cuvântul curent în TextView curent (spațiu între cuvinte)
        val newText = if (currentWordIndex == 0) words[currentWordIndex] else currentTextView.text.toString() + " " + words[currentWordIndex]
        currentTextView.text = newText

        // Animare fade in progresiv (opțional)
        currentTextView.animate().alpha(1f).setDuration(300).start()

        currentWordIndex++

        if (currentWordIndex < words.size) {
            // Mai avem cuvinte în propoziție
            handler.postDelayed({ showNextWord() }, 500)
        } else {
            // Propoziția s-a terminat, facem fade out linia anterioară (dacă există)
            if (binding.tutorialTextContainer.childCount > 1) {
                val oldLine = binding.tutorialTextContainer.getChildAt(binding.tutorialTextContainer.childCount - 2)
                oldLine.animate().alpha(0f).setDuration(1000).withEndAction {
                    binding.tutorialTextContainer.removeView(oldLine)
                }.start()
            }
            currentSentenceIndex++
            currentWordIndex = 0
            handler.postDelayed({ showNextWord() }, 700)
        }
    }

    private fun closeTutorial() {
        try {
            handler.removeCallbacksAndMessages(null)
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        (activity as? MainActivity)?.resumeDetection()
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        closeTutorial()
        _binding = null
    }
}
