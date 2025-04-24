package com.binary_squad.blind_sight
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.binary_squad.blind_sight.databinding.ActivityMainBinding
import com.binary_squad.blind_sight.databinding.ContentMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var contentBinding: ContentMainBinding
    private lateinit var mainBinding: ActivityMainBinding
    private lateinit var surfaceView: SurfaceView
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var cameraId: String? = null
    private var isShowingMainLayout = false
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var isCameraInitialized = false

    companion object {
        private const val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            camera.close()
            cameraDevice = null
        }
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "Surface created")
            if (!isCameraInitialized && hasCameraPermission()) {
                setupCamera()
            }
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG, "Surface changed: $width x $height")
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "Surface destroyed")
            isCameraInitialized = false
        }
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

            // Initialize camera manager
            cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

            // Set up SurfaceView
            surfaceView = contentBinding.surfaceView
            surfaceView.holder.addCallback(surfaceCallback)

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

            // Start background thread
            backgroundThread = HandlerThread("CameraBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }

            // Check and request camera permission
            if (!hasCameraPermission()) {
                requestCameraPermission()
            } else {
                // If we already have permission, set up the camera
                setupCamera()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupCamera() {
        try {
            if (!hasCameraPermission()) {
                Log.e(TAG, "Camera permission not granted")
                return
            }

            Log.d(TAG, "Setting up camera")
            
            // Get the first back-facing camera
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList[0]
            
            Log.d(TAG, "Using camera ID: $cameraId")
            
            // Get the optimal preview size
            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSizes = map?.getOutputSizes(SurfaceView::class.java)
            
            if (previewSizes.isNullOrEmpty()) {
                Log.e(TAG, "No preview sizes available")
                return
            }
            
            val largestPreviewSize = Collections.max(
                Arrays.asList(*previewSizes),
                CompareSizesByArea()
            )
            
            Log.d(TAG, "Selected preview size: ${largestPreviewSize.width}x${largestPreviewSize.height}")

            // Set up the SurfaceView
            surfaceView.holder.setFixedSize(largestPreviewSize.width, largestPreviewSize.height)

            // Set up ImageReader
            imageReader = ImageReader.newInstance(
                largestPreviewSize.width,
                largestPreviewSize.height,
                ImageFormat.YUV_420_888,
                2
            )

            // Open the camera
            cameraManager.openCamera(cameraId!!, cameraStateCallback, backgroundHandler)
            isCameraInitialized = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while setting up camera: ${e.message}")
            e.printStackTrace()
            isCameraInitialized = false
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up camera: ${e.message}")
            e.printStackTrace()
            isCameraInitialized = false
        }
    }

    private fun createCameraPreviewSession() {
        try {
            if (!hasCameraPermission()) {
                Log.e(TAG, "Camera permission not granted")
                return
            }

            Log.d(TAG, "Creating camera preview session")
            val surface = surfaceView.holder.surface
            val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Camera preview session configured")
                        captureSession = session
                        try {
                            previewRequestBuilder?.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            captureSession?.setRepeatingRequest(
                                previewRequestBuilder?.build() ?: return,
                                null,
                                backgroundHandler
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting camera preview: ${e.message}")
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera preview session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while creating camera preview: ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating camera preview session: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted")
                setupCamera()
            } else {
                Log.e(TAG, "Camera permission denied")
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        try {
            captureSession?.stopRepeating()
            cameraDevice?.close()
            cameraDevice = null
            isCameraInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPause: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        if (hasCameraPermission()) {
            setupCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        try {
            backgroundThread?.quitSafely()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
            e.printStackTrace()
        }
    }

    // Helper class to compare sizes
    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
            )
        }
    }
}
