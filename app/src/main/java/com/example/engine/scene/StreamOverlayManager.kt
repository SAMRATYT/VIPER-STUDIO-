package com.example.engine.scene

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.data.model.ScenePreset

/**
 * System Overlay Manager for VIPER STUDIO.
 * Renders the active Scene's custom graphics, logos, borders, and text overlays
 * directly onto the screen display using Android WindowManager (SYSTEM_ALERT_WINDOW).
 *
 * This guarantees that Android's VirtualDisplay / MediaProjection hardware pipeline
 * mirrors and encodes these overlays directly into the H.264 video frames for
 * YouTube Live RTMP streaming and local MP4 recording.
 */
class StreamOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "StreamOverlayManager"

        @Volatile
        private var instance: StreamOverlayManager? = null

        fun getInstance(context: Context): StreamOverlayManager {
            return instance ?: synchronized(this) {
                instance ?: StreamOverlayManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val compositor = SceneCompositor.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: OverlayCanvasView? = null
    private var currentScene: ScenePreset? = null
    private var isLandscape: Boolean = true
    private var isShowing: Boolean = false

    /**
     * Checks if the app has permission to draw system overlays over other apps.
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Updates the active scene and triggers an immediate redraw.
     */
    fun updateActiveScene(scene: ScenePreset?, landscape: Boolean) {
        currentScene = scene
        isLandscape = landscape
        mainHandler.post {
            overlayView?.invalidate()
        }
    }

    /**
     * Shows the floating overlay canvas on top of games/screen capture.
     */
    fun showOverlay(scene: ScenePreset?, landscape: Boolean) {
        if (!canDrawOverlays()) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        currentScene = scene
        isLandscape = landscape

        mainHandler.post {
            try {
                if (overlayView == null) {
                    val view = OverlayCanvasView(context)
                    val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }

                    // FLAG_NOT_FOCUSABLE and FLAG_NOT_TOUCHABLE ensure gamers can touch BGMI/games underneath without interference
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }

                    windowManager.addView(view, params)
                    overlayView = view
                    isShowing = true
                    Log.i(TAG, "Stream overlay view added to WindowManager for frame compositing")
                } else {
                    overlayView?.invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying stream overlay window", e)
            }
        }
    }

    /**
     * Hides the floating overlay window.
     */
    fun hideOverlay() {
        mainHandler.post {
            try {
                overlayView?.let {
                    windowManager.removeViewImmediate(it)
                    overlayView = null
                    isShowing = false
                    Log.i(TAG, "Stream overlay view removed from WindowManager")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing stream overlay view", e)
            }
        }
    }

    private inner class OverlayCanvasView(context: Context) : View(context) {
        init {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Render visible overlay layers into the canvas (ignoring the GAME_SCREEN layer since the game is already running)
            compositor.compositeScene(
                canvas = canvas,
                width = width,
                height = height,
                isLandscape = isLandscape,
                scene = currentScene,
                renderGameScreen = false
            )
        }
    }
}
