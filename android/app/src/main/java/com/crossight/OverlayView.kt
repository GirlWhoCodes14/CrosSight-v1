package com.crossight

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibratorManager
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.LinkedList
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private val sharedPreferences: SharedPreferences = context!!.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var results: List<Detection> = LinkedList<Detection>()
    private var boxPaint = Paint()
    private var scaleFactor: Float = 1f
    private var bounds = Rect()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    // Variables: Crosswalk Navigation - Guidance Text
    private var guidanceText: String = ""
    private val guidancePaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    // Variables: Sound Cue
    private var mediaPlayer: MediaPlayer? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                it.seekTo(0)  // Optionally reset the player to start of the track
            }
        }
    }

    // Variables: Vibration Cue
    private var isFastVibrate = false
    private var isVibrateCancelled: Boolean = false


    init {
        initPaints()
        initMediaPlayer()
    }


    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer.create(context, R.raw.ticker)
    }


    private fun initPaints() {
        textBackgroundPaint.color = Color.WHITE
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.BLACK
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    fun updateMessage(message: String) {
        guidanceText = message
        invalidate()  // Request a redraw so the new message will be painted
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        var crs = false
        var go = false
        var stop = false

        if (results.isEmpty()) { // No objects detected
            println("No results")
            isVibrateCancelled = true
            context.vibrator(longArrayOf(0), true)
        }

        for (result in results) {
            val boundingBox = result.boundingBox

            val top = boundingBox.top * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor
            val left = boundingBox.left * scaleFactor
            val right = boundingBox.right * scaleFactor

            // Draw bounding box around detected objects
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boxPaint)

            // Holds the classes for the detected objects
            val label = result.categories[0].label

            // Checks if there is a crosswalk, go light, or stop light
            if (label.contains("crossing")){ crs = true }
            go = (label.contains("go") || label.contains("count-blank"))
            stop = label.contains("stop")

            // Sets the text for the label
            val drawableText: String =
                if (go) { context.getString(R.string.go)
                } else if (stop) { context.getString(R.string.stop)
                } else if (crs) { context.getString(R.string.crosswalk)
                } else { ""
                }

            // Sets the color of the label
            textPaint.color =
                if (go) { Color.GREEN
                } else if (stop) { Color.RED
                } else { Color.BLACK
                }

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint
            )

            // Draw text for detected object
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)

            if (guidanceText.isNotEmpty()) {
                val x = width / 2f
                val y = height * 0.9f  // Position the text near the bottom of the view
                canvas.drawText(guidanceText, x, y, guidancePaint)
            }

            // Toggle: Visual Cue (red or green color overlay)
            if (sharedPreferences.getBoolean("visualCue", true)) {
                if (go) {
                    //visualSignal(Color.GREEN, canvas)
                    visualSignal(Paint().apply { setARGB(128, 0, 255, 0) },canvas) // Flash the screen green
                } else if (stop) {
                    //visualSignal(Color.RED, canvas)
                    visualSignal(Paint().apply { setARGB(128, 255, 0, 0) },canvas) // Flash the screen red
                }
            }

            // Toggle: Sound Cue (fast or low beeping Sound)
            if (sharedPreferences.getBoolean("soundCue", true)) {
                if(crs && go && !mediaPlayer!!.isPlaying){
                    adjustPlaybackSpeed(false)
                    stopHandler.removeCallbacks(stopRunnable)
                    stopHandler.postDelayed(stopRunnable, 1000)  // Delay is in milliseconds

                    if (!mediaPlayer!!.isPlaying) {
                        mediaPlayer?.start()
                    }
                } else if(crs && stop){
                    adjustPlaybackSpeed(true) // Half speed
                    stopHandler.removeCallbacks(stopRunnable)
                    stopHandler.postDelayed(stopRunnable, 1000)
                    if (!mediaPlayer!!.isPlaying) {
                        mediaPlayer?.start()
                    }
                }
            }

            // Toggle: Vibration Cue (fast or low vibration)
            if (sharedPreferences.getBoolean("vibrationCue", true)) {
                if ((crs && go) && (!isFastVibrate || isVibrateCancelled)) {
                    isFastVibrate = true
                    isVibrateCancelled = false
                    context.vibrator(longArrayOf(0, 150, 150, 150, 150))
                } else if ((crs && stop) && (isFastVibrate || isVibrateCancelled)) {
                    isFastVibrate = false
                    isVibrateCancelled = false
                    context.vibrator(longArrayOf(0, 1200, 300, 1200, 300))
                } // conditional statement for vibration
            } else{
                isVibrateCancelled = true
                context.vibrator(longArrayOf(0), true)
            }

        }

        if (!go && !stop && mediaPlayer?.isPlaying == true) {
            stopHandler.postDelayed(stopRunnable, 3000) // Stop playing if neither "go" nor "stop" are detected for 8 seconds
        }

    }

    private fun visualSignal(myColor: Paint, canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), myColor)

        // Use a Handler to clear the flash after 1000 milliseconds
        Handler(Looper.getMainLooper()).postDelayed({
            val clearPaint = Paint().apply { color = Color.TRANSPARENT }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), clearPaint)
            invalidate() // Force a redraw
        }, 3000) // EDIT: changed from 500

    }

    private fun adjustPlaybackSpeed(isSlow: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let {
                val speed = if (isSlow) 0.5f else 1.0f // Half speed for "stop", normal speed otherwise
                val params = it.playbackParams
                params.speed = speed
                it.playbackParams = params
                if (!it.isPlaying) {
                    it.start()
                }
            }
        } else {
            // Handle the case for older versions where playback speed cannot be changed
            Log.w("OverlayView", "Playback speed adjustment is not supported on this device.")
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }


    // -------------------- ADDED: To create vibration --------------------------
    @SuppressLint("ServiceCast", "WrongConstant")
    @Suppress("DEPRECATION")
    fun Context.vibrator(mVibratePattern: LongArray, isCancelled: Boolean = false) { //
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> { // For Android 12 (S) and above
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator

                if (isCancelled) { vibrator.cancel()
                } else { vibrator.vibrate(mVibratePattern, 0) // 0 : repeats constantly
                }
            } // Build.VERSION_CODES.S
        } // when
    } // fun context.vibrator()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseMediaPlayer()  // Ensure you release the MediaPlayer when the view is destroyed
    }

    fun setResults(
        detectionResults: MutableList<Detection>,
        imageHeight: Int,
        imageWidth: Int
    ) {
        results = detectionResults
        scaleFactor = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
