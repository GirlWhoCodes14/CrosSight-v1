package com.crossight.fragments

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.crossight.ObjectDetectorHelper
import com.crossight.R
import com.crossight.databinding.FragmentCameraBinding
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.task.vision.detector.Detection
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var ttsHandler = Handler(Looper.getMainLooper())
    private var detectionResults: MutableList<Detection>? = null
    private val speechQueue: Queue<String> = LinkedList()
    private var textToSpeechInit: Boolean = false
    private var currentLanguage: String = ""

    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService
    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedLanguagePreferences: SharedPreferences
    private lateinit var nocrossingdetected: String


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedLanguagePreferences = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        currentLanguage = sharedLanguagePreferences.getString("currentLanguage", Locale.getDefault().language) ?: "en"
        nocrossingdetected = requireContext().getString(R.string.no_crossing)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("OpenCV", "Could not load OpenCV library: ${e.message}")
            // Handle the error by notifying the user or attempting a fallback
        }
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
        initializeTextToSpeech()
        textToSpeechInit = true
        cameraExecutor = Executors.newSingleThreadExecutor()
        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),  // The context from the fragment
            objectDetectorListener = this  // The listener for detection results
        )
        setUpCamera()
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        if(_binding != null){
            val rotation = binding.viewFinder.display.rotation

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        processImage(image)
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
        }


    }



    private fun initializeTextToSpeech() {
        // Check if already initialized
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop() // Stop any ongoing speech
            textToSpeech.shutdown() // Shutdown existing instance
        }

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Adjust based on current language
                val locale = if (currentLanguage == "zh-rTW") Locale.TRADITIONAL_CHINESE else Locale.US
                val result = textToSpeech.setLanguage(locale)

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                } else {
                    textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { Log.d("TTS", "Started speaking $utteranceId") }
                        override fun onDone(utteranceId: String?) {
                            if (!speechQueue.isEmpty()) {
                                dequeueAndSpeak()
                            }
                            Log.d("TTS", "Completed speaking $utteranceId")
                        }
                        @Deprecated("Deprecated in Java", ReplaceWith(
                            "Log.e(\"TTS\", \"Error in processing Text to Speech!\")",
                            "android.util.Log"
                        )
                        )
                        override fun onError(utteranceId: String?) { Log.e("TTS", "Error in processing Text to Speech!") }
                    })
                }
            } else {
                Log.e("TTS", "Initialization of Text To Speech failed")
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun processImage(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.capacity())
        buffer.get(data)
        buffer.rewind()

        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data))

        val rotationDegrees = image.imageInfo.rotationDegrees // Get the rotation degrees from ImageProxy
        val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees.toFloat())

        // Adjust rotation degrees for compatibility with object detector (if needed)
        val detectorRotation = adjustRotationForDetector(rotationDegrees)

        objectDetectorHelper.detect(rotatedBitmap, detectorRotation)

        val mat = Mat()
        Utils.bitmapToMat(rotatedBitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)
        detectAndProcessLines(mat)

        image.close()
    }

    private fun adjustRotationForDetector(rotationDegrees: Int): Int {
        // Adjust rotation for compatibility with the object detector
        // This might involve converting rotation to a format expected by the detector
        // or handling specific rotation angles that the detector supports

        // Example: Convert rotation degrees to angles accepted by the detector
        return when (rotationDegrees) {
            0 -> 90 // Example adjustment
            90 -> 0 // Example adjustment
            180 -> 270 // Example adjustment
            270 -> 180 // Example adjustment
            else -> rotationDegrees // No adjustment needed
        }
    }

    private fun processDetectionResults(results: MutableList<Detection>?,screenCenterX:Double, minLeft:Double, maxRight:Double) {
        if(_binding != null){
            val viewFinderCenterX = binding.viewFinder.width / 2f  // Calculate the center of the viewfinder
            val context = requireContext()

            results?.forEach { detection ->
                val boundingBox = detection.boundingBox
                val boxCenterX = (boundingBox.left + boundingBox.right) / 2f * 2  // Calculate the center of the bounding box
                when {
                    ((boxCenterX < viewFinderCenterX) && (screenCenterX > maxRight)) -> updateMessage(context.getString(R.string.alert_left))
                    ((boxCenterX > viewFinderCenterX) && (screenCenterX < minLeft)) -> updateMessage(context.getString(R.string.alert_right))
                    else -> updateMessage(context.getString(R.string.alert_straight))
                }
            }
        }
    }

    private fun detectAndProcessLines(mat: Mat) {
        val hsvMat = Mat()
        Imgproc.cvtColor(mat, hsvMat, Imgproc.COLOR_BGR2HSV)
        Imgproc.GaussianBlur(hsvMat, hsvMat, Size(15.0, 15.0), 6.0)

        val lowerWhite = Scalar(0.0, 0.0, 180.0)  // Lower bound for white color
        val upperWhite = Scalar(255.0, 80.0, 255.0)  // Upper bound for white color
        val whiteMask = Mat()
        Core.inRange(hsvMat, lowerWhite, upperWhite, whiteMask)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.threshold(grayMat, grayMat, 180.0, 255.0, Imgproc.THRESH_BINARY)

        val maskedResult = Mat()
        Core.bitwise_and(whiteMask, grayMat, maskedResult)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(maskedResult, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)

        val screenHeight = mat.rows()
        val screenWidth = mat.cols()
        val screenCenterX = screenWidth / 2.0

        var minLeft = Double.MAX_VALUE
        var maxRight = Double.MIN_VALUE

        for (contour in contours) {
            val rect = Imgproc.boundingRect(contour)
            // Calculate the angle of the contour
            val angle = atan2(rect.height.toDouble(), rect.width.toDouble()) * 180 / Math.PI

            if (rect.y + rect.height / 2 >= screenHeight / 2 && Imgproc.contourArea(contour) >= 2000 && abs(angle) < 15) {
                minLeft = min(minLeft, rect.x.toDouble())
                maxRight = max(maxRight, (rect.x + rect.width).toDouble())
                Imgproc.rectangle(mat, rect, Scalar(0.0, 255.0, 0.0), 2)
            }
        }

        updateOverlay(mat)

        if (!detectionResults.isNullOrEmpty())
            processDetectionResults(detectionResults, screenCenterX, minLeft, maxRight)
        else
            if(sharedPreferences.getBoolean("visualCue",true)){
                updateMessage(nocrossingdetected)
            }

    }

    private fun updateMessage(message: String) {
        activity?.runOnUiThread {
            if(_binding != null) {
                if(sharedPreferences.getBoolean("visualCue",true)){
                    binding.overlay.updateMessage(message)
                } else{
                    binding.overlay.updateMessage("")
                }
                if(sharedPreferences.getBoolean("voiceCue",true) && textToSpeechInit) {
                    speakMessage(message)
                }

            }
        } ?: Log.e("UpdateMessage", "Activity reference is null")
    }

    private var lastSpokenMessage: String? = null

    private fun speakMessage(message: String) {
        if (message != lastSpokenMessage) {
            if (!textToSpeech.isSpeaking) {
                textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
                lastSpokenMessage = message  // Update the last message spoken
            }

        } else {
            Log.d("TTS", "Skipping speech: Message is the same as the last one")
        }
    }

    private fun dequeueAndSpeak() {
        if(sharedPreferences.getBoolean("voiceCue",true)){
            speechQueue.poll()?.let {
                if (!textToSpeech.isSpeaking) {
                    textToSpeech.speak(it, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }

    private fun updateOverlay(mat: Mat) {
        val updatedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, updatedBitmap)
        activity?.runOnUiThread {
            if(_binding != null){
                binding.imageView.setImageBitmap(updatedBitmap)
                binding.overlay.invalidate()
            }
        }
    }

    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        activity?.runOnUiThread {
            // This method assumes that your OverlayView's setResults method requires the image width and height
            // Let's ensure we are passing all required parameters including the width and height of the image
            if(_binding != null){
                binding.overlay.setResults(
                    results ?: LinkedList<Detection>(), // Safe call and Elvis operator to ensure a non-null list
                    imageHeight,
                    imageWidth
                )
                detectionResults = results

                // Check if the results contain the "go" signal
                results?.let {
                    for (result in it) {
                        if (result.categories[0].label.contains("go")) { // Simplified to 'contains' check
                            // Flash the background green
                            val originalColor = binding.root.background
                            binding.root.setBackgroundColor(Color.GREEN)

                            // Use a Handler to change the color back after 500 milliseconds
                            Handler(Looper.getMainLooper()).postDelayed({
                                binding.root.background = originalColor
                            }, 500)
                            break
                        }
                    }
                }


                // Force a redraw of the overlay
                binding.overlay.invalidate()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val rotation = binding.viewFinder.display.rotation
        preview?.targetRotation = rotation
        imageAnalyzer?.targetRotation = rotation
    }




    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        ttsHandler.removeCallbacksAndMessages(null)  // Clear all callbacks
        if (::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        if (::textToSpeech.isInitialized) {
            textToSpeech.shutdown()
        }
        cameraExecutor.shutdown()
        _binding = null
    }
}