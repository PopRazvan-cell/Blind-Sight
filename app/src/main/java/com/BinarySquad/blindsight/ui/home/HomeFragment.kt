package com.BinarySquad.blindsight.ui.home
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.BinarySquad.blindsight.R
import com.BinarySquad.blindsight.databinding.FragmentHomeBinding
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.annotation.RequiresPermission
import org.tensorflow.lite.Interpreter
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.content.res.AssetFileDescriptor
import android.os.SystemClock
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.Arrays

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private var tflite: Interpreter? = null
    private val inputImageSize = 48
    private var labels: List<String> = emptyList()
    private var lastProcessedTime = 0L
    private val processingIntervalMs = 2500L
    private val binding get() = _binding!!
    private val confidenceThreshold = 0.9f
    private var detectionMediaPlayer: MediaPlayer? = null
    private var startupMediaPlayer: MediaPlayer? = null
    private var isFirstDetection = true // Moved to class scope to reset on resume

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
            playStartupSound()
        } else {
            activity?.runOnUiThread {
                Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        detectionMediaPlayer = MediaPlayer()
        Log.d("HomeFragment", "onViewCreated")

    }

    override fun onResume() {
        Log.d("HomeFragment", "onResume")
        super.onResume()

        if (tutorialVisible) {
            Log.d("ObjectDetection", "Tutorial is visible. Skipping model start.")
            return
        }

        try {
            Log.d("ObjectDetection", "onResume called")
            // Reset inference state
            isFirstDetection = true
            lastProcessedTime = 0L // Force immediate processing

            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("ObjectDetection", "Camera permission granted, starting camera")
                startCamera()
                playStartupSound()
            } else {
                Log.d("ObjectDetection", "Requesting camera permission")
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            Log.e(
                "ObjectDetection",
                "Error in onResume: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
            activity?.runOnUiThread {
                Toast.makeText(context, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun playStartupSound() {
        try {
            startupMediaPlayer?.release()
            startupMediaPlayer = MediaPlayer.create(context, R.raw.starting_1)
            startupMediaPlayer?.start()
            Log.d("ObjectDetection", "Startup sound played")
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error playing startup sound: ${e.message}", e)
        }
    }

    private fun startCamera() {
        try {
            // Verify context and binding
            if (_binding == null) {
                Log.e("ObjectDetection", "Binding is null in startCamera")
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "UI binding error: Fragment view not initialized",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            if (!isAdded || context == null) {
                Log.e("ObjectDetection", "Fragment not attached to activity or context is null")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Fragment not attached to activity", Toast.LENGTH_LONG)
                        .show()
                }
                return
            }

            // Clean up existing resources
            Log.d("ObjectDetection", "Cleaning up existing resources")
            try {
                cameraDevice?.close()
                cameraDevice = null

                tflite?.close()
                tflite = null
                labels = emptyList()
            } catch (e: Exception) {
                Log.e("ObjectDetection", "Error cleaning up resources: ${e.message}", e)
            }

            // Initialize handler thread
            Log.d("ObjectDetection", "Initializing HandlerThread")
            handlerThread = HandlerThread("CameraThread")
            try {
                handlerThread.start()
            } catch (e: IllegalThreadStateException) {
                Log.e("ObjectDetection", "Failed to start HandlerThread: ${e.message}", e)
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Thread initialization error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
            handler = Handler(handlerThread.looper)

            // Initialize camera manager
            Log.d("ObjectDetection", "Initializing CameraManager")
            try {
                cameraManager =
                    requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager
                if (cameraManager.cameraIdList.isEmpty()) {
                    Log.e("ObjectDetection", "No cameras available on device")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "No cameras available on device", Toast.LENGTH_LONG)
                            .show()
                    }
                    return
                }
                Log.d(
                    "ObjectDetection",
                    "Available cameras: ${cameraManager.cameraIdList.joinToString()}"
                )
            } catch (e: Exception) {
                Log.e("ObjectDetection", "Error initializing CameraManager: ${e.message}", e)
                throw e
            }

            // Load model and labels
            Log.d("ObjectDetection", "Loading model and labels")
            if (!loadModelAndLabels()) {
                Log.e("ObjectDetection", "Failed to load model or labels")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to load model or labels", Toast.LENGTH_LONG)
                        .show()
                }
                return
            }

            // Set up TextureView
            Log.d("ObjectDetection", "TextureView available: ${binding.textureView.isAvailable}")
            if (binding.textureView.isAvailable) {
                openCamera()

            }
            binding.textureView.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    @RequiresPermission(Manifest.permission.CAMERA)
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        Log.d("ObjectDetection", "SurfaceTexture available, opening camera")
                        openCamera()
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        try {
                            if (_binding == null) {
                                Log.e(
                                    "ObjectDetection",
                                    "Binding is null in onSurfaceTextureUpdated"
                                )
                                return
                            }
                            if (isFirstDetection) {
                                Log.d("ObjectDetection", "Scheduling first detection")
                                handler.postDelayed({
                                    if (_binding == null) {
                                        Log.e(
                                            "ObjectDetection",
                                            "Binding is null during delayed detection"
                                        )
                                        return@postDelayed
                                    }
                                    isFirstDetection = false
                                    val bitmap = binding.textureView.bitmap
                                    if (bitmap == null) {
                                        Log.e(
                                            "ObjectDetection",
                                            "Bitmap is null for first detection"
                                        )
                                        return@postDelayed
                                    }
                                    Log.d("ObjectDetection", "Processing first detection")
                                    processImageForObjectDetection(bitmap)
                                    lastProcessedTime = System.currentTimeMillis()
                                }, 1000L)
                            } else {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastProcessedTime >= processingIntervalMs) {
                                    val bitmap = binding.textureView.bitmap
                                    if (bitmap == null) {
                                        Log.e(
                                            "ObjectDetection",
                                            "Bitmap is null for periodic detection"
                                        )
                                        return
                                    }
                                    Log.d("ObjectDetection", "Processing periodic detection")
                                    processImageForObjectDetection(bitmap)
                                    lastProcessedTime = currentTime
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(
                                "ObjectDetection",
                                "Error in onSurfaceTextureUpdated: ${e.message}",
                                e
                            )
                        }
                    }
                }

        } catch (e: SecurityException) {
            Log.e("ObjectDetection", "Camera permission error: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Camera permission error: ${e.message}", Toast.LENGTH_LONG)
                    .show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: android.hardware.camera2.CameraAccessException) {
            Log.e("ObjectDetection", "Camera access error: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Camera access error: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        } catch (e: IOException) {
            Log.e("ObjectDetection", "IO error in startCamera: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "IO error starting camera: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        } catch (e: NullPointerException) {
            Log.e("ObjectDetection", "Null pointer error in startCamera: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(
                    context,
                    "Null pointer error starting camera: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(
                "ObjectDetection",
                "Unexpected error in startCamera: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
            activity?.runOnUiThread {
                Toast.makeText(
                    context,
                    "Unexpected camera startup error: ${e.javaClass.simpleName} - ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            if (cameraManager.cameraIdList.isEmpty()) {
                Log.e("ObjectDetection", "No cameras available")
                activity?.runOnUiThread {
                    Toast.makeText(context, "No cameras available", Toast.LENGTH_LONG).show()
                }
                return
            }
            cameraManager.openCamera(
                cameraManager.cameraIdList[0],
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        val surfaceTexture = binding.textureView.surfaceTexture
                        if (surfaceTexture == null) {
                            Log.e("ObjectDetection", "SurfaceTexture is null")
                            camera.close()
                            cameraDevice = null
                            return
                        }
                        val surface = Surface(surfaceTexture)

                        val captureRequest =
                            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        captureRequest.addTarget(surface)

                        cameraDevice!!.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        session.setRepeatingRequest(
                                            captureRequest.build(),
                                            null,
                                            handler
                                        )
                                        Log.d(
                                            "ObjectDetection",
                                            "Camera capture session configured"
                                        )
                                    } catch (e: Exception) {
                                        Log.e(
                                            "ObjectDetection",
                                            "Error setting repeating request: ${e.message}",
                                            e
                                        )
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    activity?.runOnUiThread {
                                        Toast.makeText(
                                            context,
                                            "Camera configuration failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            handler
                        )
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        Log.d("ObjectDetection", "Camera disconnected")
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Camera error: $error", Toast.LENGTH_SHORT)
                                .show()
                        }
                        Log.e("ObjectDetection", "Camera error: $error")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error opening camera: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun loadModelAndLabels(): Boolean {
        try {
            val assets = requireContext().assets
            val modelFile = "model.tflite"
            val labelsFile = "labels.txt"
            val files = assets.list("") ?: run {
                Log.e("ObjectDetection", "Failed to access assets directory")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to access assets directory", Toast.LENGTH_LONG)
                        .show()
                }
                return false
            }
            if (!files.contains(modelFile)) {
                Log.e("ObjectDetection", "Model file $modelFile not found in assets")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Model file $modelFile not found", Toast.LENGTH_LONG)
                        .show()
                }
                return false
            }
            if (!files.contains(labelsFile)) {
                Log.e("ObjectDetection", "Labels file $labelsFile not found in assets")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Labels file $labelsFile not found", Toast.LENGTH_LONG)
                        .show()
                }
                return false
            }

            val options = Interpreter.Options().apply { setUseNNAPI(true) }
            tflite = Interpreter(loadModelFile(), options)
            Log.d("ObjectDetection", "Model loaded successfully")

            labels = loadLabels()
            if (labels.isEmpty()) {
                Log.e("ObjectDetection", "Labels file is empty or invalid")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Labels file is empty or invalid", Toast.LENGTH_LONG)
                        .show()
                }
                tflite?.close()
                tflite = null
                return false
            }
            Log.d("ObjectDetection", "Labels loaded: ${labels.joinToString()}")
            return true
        } catch (e: IOException) {
            Log.e("ObjectDetection", "IO error loading model or labels: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "IO error loading model: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
            return false
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error loading model or labels: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Error loading model: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
            return false
        }
    }

    override fun onStop() {
        super.onStop()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = requireContext().assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        return try {
            requireContext().assets.open("labels.txt").bufferedReader().readLines()
                .filter { it.isNotBlank() }
        } catch (e: IOException) {
            Log.e("ObjectDetection", "Error reading labels.txt: ${e.message}", e)
            emptyList()
        }
    }

    private fun processImageForObjectDetection(bitmap: Bitmap) {
        if (tflite == null || labels.isEmpty()) {
            Log.e("ObjectDetection", "Model or labels not initialized")
            activity?.runOnUiThread {
                Toast.makeText(context, "Model or labels not initialized", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }

        try {
            val grayscaleBitmap =
                Bitmap.createBitmap(inputImageSize, inputImageSize, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(grayscaleBitmap)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, inputImageSize.toFloat(), inputImageSize.toFloat()),
                paint
            )

            val inputBuffer = convertBitmapToByteBuffer(grayscaleBitmap)
            Log.d("ObjectDetection", "Input buffer size: ${inputBuffer.capacity()}")

            // Initialize outputs based on expected model structure
            val classOutput = Array(1) { FloatArray(6) } // 6 classes
            val bboxOutput = Array(1) { FloatArray(4) }  // x, y, width, height

            // Run inference with two outputs
            tflite!!.runForMultipleInputsOutputs(
                arrayOf(inputBuffer),
                mapOf(0 to classOutput, 1 to bboxOutput)
            )
            Log.d(
                "ObjectDetection",
                "Model inference completed. Class output shape: [1, ${classOutput[0].size}], Class output: ${classOutput[0].joinToString()}"
            )
            Log.d(
                "ObjectDetection",
                "Bbox output shape: [1, ${bboxOutput[0].size}], Bbox output: ${bboxOutput[0].joinToString()}"
            )

            val result = processOutput(classOutput[0], bboxOutput[0])
            Log.d(
                "ObjectDetection",
                "Processed detection: ${result.label}, confidence: ${result.confidence}, box: ${result.boundingBox}"
            )
            if (result.confidence >= confidenceThreshold) {
                playSoundOnDetection(result.label)
                drawBoundingBox(result)
            } else {
                Log.d(
                    "ObjectDetection",
                    "Detection filtered out due to low confidence: ${result.confidence}"
                )
            }
        } catch (e: IllegalArgumentException) {
            Log.e(
                "ObjectDetection",
                "Shape mismatch error: ${e.message}. Check model output configuration.",
                e
            )
            activity?.runOnUiThread {
                Toast.makeText(
                    context,
                    "Model output mismatch: Check model configuration.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error processing image: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Image processing error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }


    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputImageSize * inputImageSize * 1)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(intValues, 0, inputImageSize, 0, 0, inputImageSize, inputImageSize)
        var pixel = 0
        for (i in 0 until inputImageSize) {
            for (j in 0 until inputImageSize) {
                val value = intValues[pixel++]
                val normalizedValue = ((value shr 16) and 0xFF) / 255.0f
                byteBuffer.putFloat(normalizedValue)
            }
        }
        return byteBuffer
    }

    data class DetectionResult(
        val label: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    private fun processOutput(classOutput: FloatArray, bboxOutput: FloatArray): DetectionResult {
        Log.d(
            "HomeFragment",
            "classOutput=${classOutput.contentToString()} bbox=${bboxOutput.contentToString()}"
        )
        val maxIndex = classOutput.indices.maxByOrNull { classOutput[it] } ?: 0
        val confidence = classOutput[maxIndex]
        val label = labels.getOrNull(maxIndex) ?: "Unknown"

        val centerX = bboxOutput[0] * inputImageSize
        val centerY = bboxOutput[1] * inputImageSize
        val width = bboxOutput[2] * inputImageSize
        val height = bboxOutput[3] * inputImageSize
        val left = centerX - width / 2
        val top = centerY - height / 2
        val right = centerX + width / 2
        val bottom = centerY + height / 2
        val boundingBox = RectF(left, top, right, bottom)

        return DetectionResult(label, confidence, boundingBox)
    }

    private fun drawBoundingBox(result: DetectionResult) {
        try {
            binding.overlayView?.post {
                if (_binding == null) {
                    Log.e("ObjectDetection", "Binding is null in drawBoundingBox")
                    return@post
                }
                binding.overlayView.setResults(listOf(result))
                binding.overlayView.invalidate()
            }
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error drawing bounding box: ${e.message}", e)
        }
    }

    private var lastSoundTime = 0L
    private var lastSoundLabel = ""

    private fun playSoundOnDetection(label: String) {
        try {
            if (lastSoundLabel == label && SystemClock.elapsedRealtime() - lastSoundTime < 20000) {
                return
            }

            lastSoundLabel = label
            lastSoundTime = SystemClock.elapsedRealtime()
            detectionMediaPlayer?.reset()

            val soundResourceOptions = when (label) {
                "Farmacie" -> listOf(R.raw.farmacie1, R.raw.farmacie2, R.raw.farmacie3)
                "Semn pentru statie de autobuz" -> listOf(R.raw.semn_statie_bus1, R.raw.semn_statie_bus2)
                "Semafor rosu" -> listOf(R.raw.semafor_rosu1, R.raw.semafor_rosu2, R.raw.semafor_rosu3)
                "Semafor verde" -> listOf(R.raw.semafor_verde1, R.raw.semafor_verde2, R.raw.semafor_verde3)
                "Semn pentru trecere de pietoni" -> listOf(R.raw.semn_trecere_de_pietoni1, R.raw.semn_trecere_de_pietoni2, R.raw.semn_trecere_de_pietoni3)
                "Trecere de pietoni" -> listOf(R.raw.trecere1, R.raw.trecere2, R.raw.trecere3)
                else -> null
            }

            val selectedSoundRes = soundResourceOptions?.random()

            selectedSoundRes?.let {
                detectionMediaPlayer?.setDataSource(
                    requireContext(),
                    android.net.Uri.parse("android.resource://${context?.packageName}/$it")
                )
                detectionMediaPlayer?.prepare()
                detectionMediaPlayer?.start()
                Log.d("ObjectDetection", "Playing random sound for $label")
            }

        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error playing sound for $label: ${e.message}", e)
        }
    }

    override fun onPause() {
        Log.d("HomeFragment", "onPause")
        super.onPause()
        try {
            cameraDevice?.close()
            cameraDevice = null
            startupMediaPlayer?.stop()
            detectionMediaPlayer?.stop()
            handlerThread.quitSafely()

            tflite?.close()
            tflite = null
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error in onPause: ${e.message}", e)

        }
    }

    override fun onDestroyView() {
        Log.d("HomeFragment", "onDestroyView")
        super.onDestroyView()
        try {
            handlerThread.quitSafely()
            tflite?.close()
            tflite = null
            labels = emptyList()
            startupMediaPlayer?.release()
            startupMediaPlayer = null
            detectionMediaPlayer?.release()
            detectionMediaPlayer = null
            binding.textureView.surfaceTextureListener = null
            _binding = null
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error in onDestroyView: ${e.message}", e)
        }
    }

    private var tutorialVisible = false
    private fun stopDetectionModel() {
        try {
            cameraDevice?.close()
            cameraDevice = null
            handlerThread.quitSafely()
            tflite?.close()
            tflite = null
            Log.d("TutorialState", "Detection model stopped.")
        } catch (e: Exception) {
            Log.e("TutorialState", "Error stopping model: ${e.message}", e)
        }
    }

    private fun resumeDetectionModel() {
        try {
            if (!loadModelAndLabels()) {
                Log.e("TutorialState", "Failed to reload model and labels")
                return
            }
            startCamera()
            Log.d("TutorialState", "Detection model resumed.")
        } catch (e: Exception) {
            Log.e("TutorialState", "Error resuming model: ${e.message}", e)
        }
    }

    fun pauseDetection() {
        stopDetectionModel()
        android.util.Log.d("HomeFragment", "Detection paused")
    }

    fun resumeDetection() {
        resumeDetectionModel()
        android.util.Log.d("HomeFragment", "Detection resumed")
    }

}





