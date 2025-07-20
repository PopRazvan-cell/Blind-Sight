package com.BinarySquad.blindsight

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.bluetooth.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibrationEffect
import java.io.IOException
import java.io.InputStream
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.BinarySquad.blindsight.ui.bluetooth.AdapterBluetooth
import com.BinarySquad.blindsight.ui.bluetooth.BluetoothActivity
import com.BinarySquad.blindsight.ui.bluetooth.BluetoothItem
import com.BinarySquad.blindsight.ui.home.HomeFragment
import com.google.android.material.navigation.NavigationView
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var settingsDrawer: NavigationView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var btnConnect: Button
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var vibrator: Vibrator
    private var bluetoothSocket: BluetoothSocket? = null
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper())

    // UUID for SPP (Serial Port Profile) - commonly used for Arduino/sensor communication
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val TAG: String = MainActivity::class.java.simpleName

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val REQUEST_ENABLE_BT: Int = 1
    //private val devices: List<BluetoothItem>? = null
    val devices = mutableListOf<BluetoothItem>()
    private val REQUEST_BLUETOOTH_PERMISSION = 1


    lateinit var tts: TextToSpeech

    private var selectedMenuItemId: Int? = null
    private var lastTapTime = 0L
    private val DOUBLE_TAP_TIMEOUT = 500

    //MediaPlayer pentru audio detectie (exemplu)
    private var detectionMediaPlayer: MediaPlayer? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        settingsDrawer = findViewById(R.id.settings_main_drawer)
        toolbar = findViewById(R.id.toolbar)
        btnConnect = findViewById(R.id.btn_connect)

        //bluetooth
        val customView = layoutInflater.inflate(R.layout.blue_layout, navView, false)
        navView.addView(customView,1)



        setSupportActionBar(toolbar)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.getDefault()
            }
        }

        val screenWidth = resources.displayMetrics.widthPixels
        settingsDrawer.layoutParams.width = screenWidth
        navView.layoutParams.width = screenWidth
        drawerLayout.setScrimColor(Color.TRANSPARENT)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_about),
            drawerLayout
        )
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        btnConnect.setOnClickListener {

            drawerLayout.openDrawer(GravityCompat.START)

        }

        setupGestures()
        setupMenuConfirmation(navView)
        setupMenuConfirmation(settingsDrawer)

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) = updateDrawerLockMode()
            override fun onDrawerClosed(drawerView: View) = updateDrawerLockMode()
            override fun onDrawerStateChanged(newState: Int) {}
        })

        updateDrawerLockMode()
        setupBluetooth()

        //bluetooth
        val bluetoothManager = getSystemService(
            BluetoothManager::class.java
        )
        checkNotNull(bluetoothManager)
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device doesn't support bluetooth!")
        }

        val adapter = bluetoothAdapter
        if (adapter != null && !adapter.isEnabled) {
            val btIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            //startActivityForResult(btIntent, MainActivity.REQUEST_ENABLE_BT)
        } else {
            initDevices()
        }

        if (devices != null) {
            Toast.makeText(
                this,
                String.format("Found %d devices", devices.size),
                Toast.LENGTH_LONG
            ).show()
        }

        val rcv = findViewById<RecyclerView>(R.id.rcv_devices)

        val adapterBluetooth = AdapterBluetooth(devices, object : AdapterBluetooth.OnDeviceClickListener {
            override fun onDeviceClick(device: BluetoothItem) {
                connectToBluetoothDevice(device)
            }
        })
        rcv.adapter = adapterBluetooth
        rcv.layoutManager = LinearLayoutManager(this)
    }
    private fun connectToBluetoothDevice(deviceItem: BluetoothItem) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Find the actual BluetoothDevice by address
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(deviceItem.getAddress()) // Use getter method
        if (bluetoothDevice == null) {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Disconnect if already connected
        if (isConnected) {
            disconnectBluetoothDevice()
        }

        Thread {
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()

                runOnUiThread {
                    Toast.makeText(this, "Connected to ${deviceItem.getName()}", Toast.LENGTH_SHORT).show() // Use getter method
                    isConnected = true
                    startListeningForUltrasonicData()

                    // Close the drawer after successful connection
                    drawerLayout.closeDrawer(GravityCompat.START)
                }

            } catch (e: IOException) {
                Log.e(TAG, "Could not connect to device", e)
                runOnUiThread {
                    Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                try {
                    bluetoothSocket?.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "Could not close socket", closeException)
                }
            }
        }.start()
    }
    private fun startListeningForUltrasonicData() {
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int
            val inputStream: InputStream = bluetoothSocket?.inputStream ?: return@Thread

            while (isConnected) {
                try {
                    bytes = inputStream.read(buffer)
                    val receivedData = String(buffer, 0, bytes).trim()

                    Log.d(TAG, "Received raw ultrasonic distance: $receivedData")
                    try {
                        val distance = receivedData.substringAfter(':').toFloat()

                        Log.d(TAG, "Received ultrasonic distance: $distance cm")

                        // Check if distance is below threshold
                        if (distance < 50.0f) {
                            runOnUiThread {
                                triggerProximityAlert(distance)
                            }
                        }

                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Could not parse distance data: $receivedData")
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "Error reading from Bluetooth socket", e)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connection lost", Toast.LENGTH_SHORT).show()
                    }
                    break
                }
            }
        }.start()
    }
    // NEW METHOD: Trigger device vibration



    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun triggerProximityAlert(distance: Float) {
        // Trigger vibration
        triggerVibration()

        // Show toast notification
        Toast.makeText(
            this,
            "distance ${String.format("%.1f", distance)} cm!",
            Toast.LENGTH_SHORT
        ).show()

        // Optional: Use TTS to announce the alert
        if (::tts.isInitialized) {
            tts.speak(
                "distance ${String.format("%.0f", distance)} centimeters",
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
        }

        // Optional: Play alert sound using your existing detection audio system
        //startDetectionAudio(R.raw.acasa) // Replace with appropriate alert sound
    }

    // NEW METHOD: Trigger device vibration
    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun triggerVibration() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For API 26 and above
                val vibrationEffect = VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                // For older versions
                @Suppress("DEPRECATION")
                vibrator.vibrate(5000)
            }
            Log.d(TAG, "Vibration triggered for 5 seconds")
        }
    }

    // NEW METHOD: Disconnect Bluetooth device
    private fun disconnectBluetoothDevice() {
        isConnected = false
        try {
            bluetoothSocket?.close()
            Toast.makeText(this, "Bluetooth disconnected", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close Bluetooth socket", e)
        }
    }
    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        checkNotNull(bluetoothManager)
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device doesn't support bluetooth!")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter != null && !adapter.isEnabled) {
            val btIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        } else {
            initDevices()
        }

        if (devices != null) {
            Toast.makeText(
                this,
                String.format("Found %d devices", devices.size),
                Toast.LENGTH_LONG
            ).show()
        }

        val rcv = findViewById<RecyclerView>(R.id.rcv_devices)
        // UPDATED: Add click listener to adapter
        val adapterBluetooth = AdapterBluetooth(devices) { device ->
            connectToBluetoothDevice(device)
        }
        rcv.adapter = adapterBluetooth
        rcv.layoutManager = LinearLayoutManager(this)
    }

    private fun updateDrawerLockMode() {
        when {
            drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
            }

            drawerLayout.isDrawerOpen(GravityCompat.END) -> {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
            }

            else -> {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
            }
        }
    }

    private fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun openSettingsDrawer() {
        drawerLayout.openDrawer(GravityCompat.END)
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
                val deltaX = e2.x - (e1?.x ?: 0f)
                val deltaY = e2.y - (e1?.y ?: 0f)

                return when {
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) &&
                            deltaX > SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD -> {
                        if (!drawerLayout.isDrawerOpen(GravityCompat.END)) openDrawer()
                        true
                    }

                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) &&
                            deltaX < -SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD -> {
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
    private var menuMediaPlayer: MediaPlayer? = null

    private fun setupMenuConfirmation(navView: NavigationView) {
        navView.setNavigationItemSelectedListener { menuItem ->
            val now = System.currentTimeMillis()
            val navController = findNavController(R.id.nav_host_fragment_content_main)

            if (menuItem.itemId == selectedMenuItemId && (now - lastTapTime < DOUBLE_TAP_TIMEOUT)) {
                menuItem.isChecked = true
                drawerLayout.closeDrawers()

                when (menuItem.itemId) {
                    R.id.nav_home -> navController.navigate(R.id.nav_home)
                    R.id.nav_about -> navController.navigate(R.id.nav_about)
                }

                true
            } else {
                selectedMenuItemId = menuItem.itemId
                lastTapTime = now

                // Release previous media player if playing
                menuMediaPlayer?.release()
                menuMediaPlayer = null

                val soundResId = when (menuItem.itemId) {
                    R.id.nav_home -> R.raw.acasa
                    R.id.nav_about -> R.raw.despre_noi
                    else -> null
                }

                soundResId?.let {
                    menuMediaPlayer = MediaPlayer.create(this, it)
                    menuMediaPlayer?.start()
                }

                false
            }
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        stopDetectionAudio()  // asigură oprirea sunetelor de detecție la închidere
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

    // --- START ADĂUGARE METODE PENTRU PAUZARE/RELUARE DETECȚIE ---

    private val homeFragment: HomeFragment?
        get() = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
            ?.childFragmentManager
            ?.fragments
            ?.firstOrNull { it is HomeFragment } as? HomeFragment

    fun pauseDetection() {
        homeFragment?.pauseDetection()
        Log.d("MainActivity", "Detection paused")
    }

    fun resumeDetection() {
        homeFragment?.resumeDetection()
        Log.d("MainActivity", "Detection resumed")
    }

    // --- ADĂUGARE OPRIRE AUDIO DETECTIE ---
    fun stopDetectionAudio() {
        if (detectionMediaPlayer?.isPlaying == true) {
            detectionMediaPlayer?.stop()
            detectionMediaPlayer?.release()
            detectionMediaPlayer = null
            Log.d("MainActivity", "Detection audio stopped")
        }
    }

    // --- OPȚIONAL: Funcție de start audio detectie ---
    fun startDetectionAudio(audioResId: Int) {
        stopDetectionAudio() // oprește ce era înainte
        detectionMediaPlayer = MediaPlayer.create(this, audioResId)
        detectionMediaPlayer?.setOnCompletionListener {
            stopDetectionAudio()
        }
        detectionMediaPlayer?.start()
        Log.d("MainActivity", "Detection audio started")
    }


    //bluetooth
    protected fun initDevices() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_PERMISSION
            )
            return
        }
        val pairedDevices = bluetoothAdapter!!.bondedDevices

        Log.d(TAG, "Paired device found: ${pairedDevices.size} ")
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                val deviceName = device.name
                val deviceHardwareAddress = device.address
                Log.d(TAG, "Paired device found: ${device.name} @ ${device.address}")
                Log.i(
                    TAG, String.format(
                        "Device name: %s; Device Address: %s",
                        deviceName, deviceHardwareAddress
                    )
                )

                devices.add(BluetoothItem(deviceName, deviceHardwareAddress))
                Log.d(TAG, "Paired device found: ${devices.size} ")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        try {
            super.onActivityResult(requestCode, resultCode, data)

            if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
                initDevices()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(
                this, ex.toString(),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
