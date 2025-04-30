package com.binary_squad.blind_sight
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.binary_squad.blind_sight.databinding.ActivityMainBinding
import com.binary_squad.blind_sight.databinding.ContentMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var contentBinding: ContentMainBinding
    private lateinit var mainBinding: ActivityMainBinding
    private var isShowingMainLayout = false
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        try {
            // Initialize both bindings
            contentBinding = ContentMainBinding.inflate(layoutInflater)
            mainBinding = ActivityMainBinding.inflate(layoutInflater)

            // Start with content layout
            setContentView(contentBinding.root)

            // Set up menu button
            contentBinding.menuButton.setOnClickListener {
                if (!isShowingMainLayout) {
                    // Switch to main layout
                    setContentView(mainBinding.root)
                    isShowingMainLayout = true
                    
                    // Set up the toolbar
                    setSupportActionBar(mainBinding.root.findViewById(R.id.toolbar))
                    
                    // Set up the FAB
                    mainBinding.root.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab).setOnClickListener { view ->
                        // Add your FAB action here
                    }
                    
                    // Set up navigation
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
                    navController = navHostFragment.navController
                    
                    appBarConfiguration = AppBarConfiguration(
                        setOf(
                            R.id.nav_home,
                            R.id.nav_gallery,
                            R.id.nav_slideshow
                        ),
                        mainBinding.drawerLayout
                    )
                    
                    setupActionBarWithNavController(navController, appBarConfiguration)
                    mainBinding.navView.setupWithNavController(navController)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
