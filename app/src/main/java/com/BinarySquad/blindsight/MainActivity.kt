package com.BinarySquad.blindsight

import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        navView.layoutParams = navView.layoutParams.apply {
            width = resources.displayMetrics.widthPixels
        }

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        setupGestures()
    }

    private fun setupGestures() {
        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 150
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null || e2 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                return when {
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && deltaX > SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD -> {
                        openDrawer()
                        true
                    }

                    kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) && deltaY < -SWIPE_THRESHOLD && kotlin.math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD -> {
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

    private fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun openTutorialPanel() {
        val existingFragment = supportFragmentManager.findFragmentByTag("TutorialFragment")
        if (existingFragment == null) {
            val tutorialFragment = TutorialBottomFragment()
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, tutorialFragment, "TutorialFragment")
                .addToBackStack(null)
                .commit()
        }
    }

    private fun returnToCamera() {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.popBackStack(R.id.nav_home, false)
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
