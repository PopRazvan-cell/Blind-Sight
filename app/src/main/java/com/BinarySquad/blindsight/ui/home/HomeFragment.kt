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
import com.BinarySquad.blindsight.R // Import your R file
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
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.util.Log
import java.io.IOException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private var tflite: Interpreter? = null
    private val inputImageSize = 48 // Model expects 48x48 grayscale images
    private var labels: List<String> = emptyList()
    private var lastProcessedTime = 0L
    private val processingIntervalMs = 6000L // Process every 6 seconds
    private val binding get() = _binding!!
    private val confidenceThreshold = 0.5f // Filter detections below this confidence
    private var mediaPlayer: MediaPlayer? = null

    // ActivityResultLauncher for permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
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
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Camera initialization moved to onResume
        mediaPlayer = MediaPlayer.create(context, R.raw.obiect_neclar_1) // Replace with your sound file
    }

    override fun onResume() {
        super.onResume()
        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        handlerThread = HandlerThread("CameraThread").apply { start() }
        handler = Handler(handlerThread.looper)
        cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Load TFLite model and labels
        if (!loadModelAndLabels()) {
            activity?.runOnUiThread {
                Toast.makeText(context, "Failed to load model or labels", Toast.LENGTH_LONG).show()
            }
            return
        }

        // Check if TextureView is available
        Log.d("ObjectDetection", "TextureView available: ${binding.textureView.isAvailable}")
        if (binding.textureView.isAvailable) {
            openCamera()
        } else {
            binding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                @RequiresPermission(Manifest.permission.CAMERA)
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    Log.d("ObjectDetection", "SurfaceTexture available, opening camera")
                    openCamera()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProcessedTime >= processingIntervalMs) {
                        val bitmap = binding.textureView.bitmap ?: return
                        processImageForObjectDetection(bitmap)
                        lastProcessedTime = currentTime
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surfaceTexture = binding.textureView.surfaceTexture
                    if (surfaceTexture == null) {
                        Log.e("ObjectDetection", "SurfaceTexture is null")
                        return
                    }
                    val surface = Surface(surfaceTexture)

                    val captureRequest = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)

                    cameraDevice!!.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.setRepeatingRequest(captureRequest.build(), null, handler)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                activity?.runOnUiThread {
                                    Toast.makeText(context, "Camera configuration failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        handler
                    )
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Camera error: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }, handler)
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error opening camera", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadModelAndLabels(): Boolean {
        try {
            // Verify assets exist
            val assets = requireContext().assets
            val modelFile = "model.tflite"
            val labelsFile = "labels.txt"
            assets.list("")?.let { files ->
                if (!files.contains(modelFile)) {
                    Log.e("ObjectDetection", "Model file $modelFile not found in assets")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Model file $modelFile not found", Toast.LENGTH_LONG).show()
                    }
                    return false
                }
                if (!files.contains(labelsFile)) {
                    Log.e("ObjectDetection", "Labels file $labelsFile not found in assets")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Labels file $labelsFile not found", Toast.LENGTH_LONG).show()
                    }
                    return false
                }
            } ?: run {
                Log.e("ObjectDetection", "Failed to access assets directory")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to access assets directory", Toast.LENGTH_LONG).show()
                }
                return false
            }

            // Load model
            val options = Interpreter.Options().apply {
                setUseNNAPI(true) // Enable NNAPI if supported
            }
            tflite = Interpreter(loadModelFile(), options)
            Log.d("ObjectDetection", "Model loaded successfully")

            // Load labels
            labels = loadLabels()
            if (labels.isEmpty()) {
                Log.e("ObjectDetection", "Labels file is empty or invalid")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Labels file is empty or invalid", Toast.LENGTH_LONG).show()
                }
                return false
            }
            Log.d("ObjectDetection", "Labels loaded: ${labels.joinToString()}")
            return true
        } catch (e: IOException) {
            Log.e("ObjectDetection", "IO error loading model or labels: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "IO error loading model: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return false
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error loading model or labels: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Error loading model: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return false
        }
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
            requireContext().assets.open("labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (e: IOException) {
            Log.e("ObjectDetection", "Error reading labels.txt: ${e.message}", e)
            emptyList()
        }
    }

    private fun processImageForObjectDetection(bitmap: Bitmap) {
        if (tflite == null || labels.isEmpty()) {
            Log.e("ObjectDetection", "Model or labels not initialized")
            return
        }

        // Convert to grayscale and resize to 48x48
        val grayscaleBitmap = Bitmap.createBitmap(inputImageSize, inputImageSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(grayscaleBitmap)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f) // Convert to grayscale
            })
        }
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, inputImageSize.toFloat(), inputImageSize.toFloat()), paint)

        // Preprocess the bitmap
        val inputBuffer = convertBitmapToByteBuffer(grayscaleBitmap)
        Log.d("ObjectDetection", "Input buffer size: ${inputBuffer.capacity()}")

        // Prepare output buffers
        val classOutput = Array(1) { FloatArray(labels.size) } // Class probabilities
        val bboxOutput = Array(1) { FloatArray(4) } // [center_x, center_y, width, height]

        // Run inference
        try {
            tflite!!.runForMultipleInputsOutputs(
                arrayOf(inputBuffer),
                mapOf(
                    0 to classOutput,
                    1 to bboxOutput
                )
            )
            Log.d("ObjectDetection", "Model inference completed. Class output: ${classOutput[0].joinToString()}, Bbox output: ${bboxOutput[0].joinToString()}")
        } catch (e: Exception) {
            Log.e("ObjectDetection", "Error running inference: ${e.message}", e)
            activity?.runOnUiThread {
                Toast.makeText(context, "Inference error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Process the output
        val result = processOutput(classOutput[0], bboxOutput[0])
        Log.d("ObjectDetection", "Processed detection: ${result.label}, confidence: ${result.confidence}, box: ${result.boundingBox}")
        if (result.confidence >= confidenceThreshold) {
            playSoundOnDetection(result.label) // Play sound when an object is detected
            drawBoundingBox(result)
        } else {
            Log.d("ObjectDetection", "Detection filtered out due to low confidence: ${result.confidence}")
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputImageSize * inputImageSize * 1) // 1 channel (grayscale)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputImageSize * inputImageSize)
        bitmap.getPixels(intValues, 0, inputImageSize, 0, 0, inputImageSize, inputImageSize)
        var pixel = 0
        for (i in 0 until inputImageSize) {
            for (j in 0 until inputImageSize) {
                val value = intValues[pixel++]
                // Extract grayscale value (R=G=B in grayscale) and normalize to [0, 1]
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
        // Get class with highest probability
        val maxIndex = classOutput.indices.maxByOrNull { classOutput[it] } ?: 0
        val confidence = classOutput[maxIndex]
        val label = labels.getOrNull(maxIndex) ?: "Unknown"

        // Convert YOLO format (center_x, center_y, width, height) to RectF (left, top, right, bottom)
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
        binding.overlayView?.post {
            binding.overlayView.setResults(listOf(result))
            binding.overlayView.invalidate()
        }
    }

    private fun playSoundOnDetection(label: String) {
        mediaPlayer?.reset() // Reset the MediaPlayer to load a new file

        val soundResourceId = when (label) {
            "Farmacie" -> R.raw.farmacie_detectata_1
            "1" -> R.raw.obiect_neclar_1
            "3" -> R.raw.obiect_neclar_1
            "Semafor" -> R.raw.posibil_semafor_1
            "Semn Trecere de Pietoni" -> R.raw.semn_trecere_1
            "Trecere de pietoni" -> R.raw.trecere_detectata_2
            else -> null // Or a default sound if the label doesn't match
        }

        soundResourceId?.let {
            try {
                mediaPlayer?.setDataSource(requireContext(), android.net.Uri.parse("android.resource://${context?.packageName}/$it"))
                mediaPlayer?.prepare()
                mediaPlayer?.start()
            } catch (e: IOException) {
                Log.e("ObjectDetection", "Error playing sound for $label: ${e.message}")
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        // ... (rest of your onDestroyView code) ...
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }
}