package com.BinarySquad.blindsight

import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.BinarySquad.blindsight.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var settingsDrawer: NavigationView
    lateinit var tts: TextToSpeech

    private var selectedMenuItemId: Int? = null
    private var lastTapTime = 0L
    private val DOUBLE_TAP_TIMEOUT = 500 // milliseconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        // Setup TTS
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.getDefault()
            }
        }

        settingsDrawer = binding.settingsDrawer

        // Make settings drawer full screen width
        val screenWidth = resources.displayMetrics.widthPixels
        val params = settingsDrawer.layoutParams
        params.width = screenWidth
        settingsDrawer.layoutParams = params

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        navView.layoutParams = navView.layoutParams.apply {
            width = screenWidth
        }

        drawerLayout.setScrimColor(Color.TRANSPARENT)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        setupGestures()
        setupMenuConfirmation(navView)
        setupMenuConfirmation(settingsDrawer)

        // Add DrawerListener to lock/unlock drawers accordingly
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerOpened(drawerView: View) {
                updateDrawerLockMode()
            }

            override fun onDrawerClosed(drawerView: View) {
                updateDrawerLockMode()
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })

        // Initialize drawer lock mode state
        updateDrawerLockMode()
    }

    private fun updateDrawerLockMode() {
        val drawerLayout = binding.drawerLayout

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            // Lock the settings drawer (right) when main drawer (left) is open
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
        } else if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            // Lock the main drawer (left) when settings drawer (right) is open
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        } else {
            // Both drawers unlocked when none open
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        }
    }

    private fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun openSettingsDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.END)
    }

    private fun openTutorialPanel() {
        val existingFragment = supportFragmentManager.findFragmentByTag("TutorialFragment")
        if (existingFragment == null) {
            val tutorialFragment = TutorialBottomFragment()
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, tutorialFragment, "TutorialFragment")
                .addToBackStack("TutorialFragment")
                .commit()
        }
    }

    private fun returnToCamera() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.popBackStack(R.id.nav_home, false)
    }

    private fun setupGestures() {
        val gestureListener = object : SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 150
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val deltaX = (e2.x - (e1?.x ?: 0f))
                val deltaY = (e2.y - (e1?.y ?: 0f))
                val drawerLayout = binding.drawerLayout

                return when {
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) &&
                            deltaX > SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD -> {
                        // Only open left drawer if right drawer is closed
                        if (!drawerLayout.isDrawerOpen(GravityCompat.END)) openDrawer()
                        true
                    }

                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) &&
                            deltaX < -SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD -> {
                        // Only open right drawer if left drawer is closed
                        if (!drawerLayout.isDrawerOpen(GravityCompat.START)) openSettingsDrawer()
                        true
                    }

                    kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) &&
                            deltaY < -SWIPE_THRESHOLD && kotlin.math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD -> {
                        openTutorialPanel()
                        true
                    }

                    else -> false
                }
            }
        }

        gestureDetector = GestureDetector(this, gestureListener)

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (detector.scaleFactor > 1.1f) {
                    returnToCamera()
                }
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        scaleGestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupMenuConfirmation(navView: NavigationView) {
        navView.setNavigationItemSelectedListener { menuItem ->
            val now = System.currentTimeMillis()
            if (menuItem.itemId == selectedMenuItemId && (now - lastTapTime < DOUBLE_TAP_TIMEOUT)) {
                // Confirm selection
                menuItem.isChecked = true
                binding.drawerLayout.closeDrawers()
                true
            } else {
                // First tap: speak title
                selectedMenuItemId = menuItem.itemId
                lastTapTime = now
                val title = menuItem.title.toString()
                tts.speak(title, TextToSpeech.QUEUE_FLUSH, null, null)
                false
            }
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
