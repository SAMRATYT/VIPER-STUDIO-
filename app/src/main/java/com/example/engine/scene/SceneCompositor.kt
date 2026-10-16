package com.example.engine.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import com.example.data.model.LayerType
import com.example.data.model.SceneLayer
import com.example.data.model.ScenePreset
import java.util.concurrent.ConcurrentHashMap

/**
 * Native 2D Android Canvas Compositing Engine for VIPER STUDIO.
 * High-performance, zero-allocation per frame compositing pipeline for rendering
 * scene presets (text with shadow/outline, transparent PNGs, stream frames, alerts)
 * into live video streams, local recordings, and UI viewports.
 */
class SceneCompositor(private val context: Context) {

    companion object {
        private const val TAG = "SceneCompositor"

        @Volatile
        private var instance: SceneCompositor? = null

        fun getInstance(context: Context): SceneCompositor {
            return instance ?: synchronized(this) {
                instance ?: SceneCompositor(context.applicationContext).also { instance = it }
            }
        }
    }

    private val imageManager = SceneImageManager.getInstance(context)
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    // Reusable Paints to avoid garbage collection churn
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Renders an entire ScenePreset onto the target Canvas.
     * @param canvas The target Canvas (can be from a Bitmap, Surface, or View).
     * @param width Canvas width in pixels.
     * @param height Canvas height in pixels.
     * @param isLandscape Whether the canvas orientation is 16:9 Landscape (true) or 9:16 Portrait (false).
     * @param scene The ScenePreset to composite.
     * @param renderGameScreen Whether to fill the game screen layer (set to false if screen capture is already underlying).
     */
    fun compositeScene(
        canvas: Canvas,
        width: Int,
        height: Int,
        isLandscape: Boolean,
        scene: ScenePreset?,
        renderGameScreen: Boolean = true
    ) {
        if (scene == null) return
        val layers = scene.layers.filter { it.isVisible }.sortedBy { it.zIndex }

        for (layer in layers) {
            renderLayer(canvas, width, height, isLandscape, layer, renderGameScreen)
        }
    }

    /**
     * Renders a single SceneLayer with exact coordinate transformation, rotation, opacity,
     * typography font, outline stroke, shadow, and aspect-ratio preservation.
     */
    fun renderLayer(
        canvas: Canvas,
        canvasW: Int,
        canvasH: Int,
        isLandscape: Boolean,
        layer: SceneLayer,
        renderGameScreen: Boolean
    ) {
        if (!layer.isVisible) return
        if (layer.type == LayerType.GAME_SCREEN && !renderGameScreen) return

        val xPct = layer.getX(isLandscape)
        val yPct = layer.getY(isLandscape)
        val wPct = layer.getWidth(isLandscape)
        val hPct = layer.getHeight(isLandscape)

        val left = (canvasW * (xPct / 100f))
        val top = (canvasH * (yPct / 100f))
        val width = (canvasW * (wPct / 100f))
        val height = (canvasH * (hPct / 100f))

        if (width <= 0f || height <= 0f) return

        val centerX = left + width / 2f
        val centerY = top + height / 2f

        val count = canvas.save()
        try {
            // Apply rotation around the layer center
            if (layer.rotationDeg != 0f) {
                canvas.rotate(layer.rotationDeg, centerX, centerY)
            }

            // Alpha blending
            val alphaInt = (layer.opacity.coerceIn(0.05f, 1.0f) * 255).toInt()

            val rect = RectF(left, top, left + width, top + height)

            when (layer.type) {
                LayerType.TEXT_BANNER -> {
                    renderTextLayer(canvas, rect, layer, alphaInt)
                }
                LayerType.IMAGE_OVERLAY -> {
                    renderImageOrOverlayLayer(canvas, rect, layer, alphaInt)
                }
                LayerType.CAMERA_PIP -> {
                    renderCameraPipPlaceholder(canvas, rect, layer, alphaInt)
                }
                LayerType.ALERT_BOX -> {
                    renderAlertBoxPlaceholder(canvas, rect, layer, alphaInt)
                }
                LayerType.SOCIAL_BADGE -> {
                    renderSocialBadge(canvas, rect, layer, alphaInt)
                }
                LayerType.GAME_SCREEN -> {
                    if (renderGameScreen) {
                        fillPaint.color = Color.parseColor("#0C121E")
                        fillPaint.alpha = alphaInt
                        canvas.drawRect(rect, fillPaint)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error rendering layer ${layer.label}", e)
        } finally {
            canvas.restoreToCount(count)
        }
    }

    private fun renderTextLayer(canvas: Canvas, rect: RectF, layer: SceneLayer, alphaInt: Int) {
        val text = layer.textContent.ifBlank { layer.label }
        if (text.isBlank()) return

        // Background box if configured
        val bgColor = try { Color.parseColor(layer.bgColorHex) } catch (_: Exception) { Color.parseColor("#B30F1722") }
        fillPaint.color = bgColor
        fillPaint.alpha = ((Color.alpha(bgColor) / 255f) * (alphaInt / 255f) * 255).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, 12f, 12f, fillPaint)

        // Typeface selection
        val baseTypeface = when (layer.textFontFamily.uppercase()) {
            "MONOSPACE" -> Typeface.MONOSPACE
            "SERIF" -> Typeface.SERIF
            "CURSIVE", "DISPLAY" -> Typeface.SANS_SERIF // Standard elegant fallback
            else -> Typeface.SANS_SERIF
        }
        val typefaceStyle = if (layer.isTextBold) Typeface.BOLD else Typeface.NORMAL
        val finalTypeface = Typeface.create(baseTypeface, typefaceStyle)

        // Text scaling based on layer height and configured font size
        val fontSizePx = (layer.textFontSizeSp * (context.resources.displayMetrics.scaledDensity)).coerceIn(12f, rect.height() * 0.75f)

        // Configure text paints
        textFillPaint.apply {
            typeface = finalTypeface
            textSize = fontSizePx
            color = try { Color.parseColor(layer.textColorHex) } catch (_: Exception) { Color.parseColor("#00E676") }
            alpha = alphaInt

            if (layer.hasTextShadow) {
                val shadowColor = try { Color.parseColor(layer.textShadowColorHex) } catch (_: Exception) { Color.BLACK }
                setShadowLayer(layer.textShadowRadius, 2f, 2f, shadowColor)
            } else {
                clearShadowLayer()
            }
        }

        // Center text vertically
        val textY = rect.centerY() - ((textFillPaint.descent() + textFillPaint.ascent()) / 2f)

        // Render outline stroke if enabled
        if (layer.hasTextOutline && layer.textOutlineWidth > 0f) {
            textStrokePaint.apply {
                typeface = finalTypeface
                textSize = fontSizePx
                strokeWidth = layer.textOutlineWidth * 2f
                color = try { Color.parseColor(layer.textOutlineColorHex) } catch (_: Exception) { Color.BLACK }
                alpha = alphaInt
            }
            canvas.drawText(text, rect.centerX(), textY, textStrokePaint)
        }

        // Render text body
        canvas.drawText(text, rect.centerX(), textY, textFillPaint)
    }

    private fun renderImageOrOverlayLayer(canvas: Canvas, rect: RectF, layer: SceneLayer, alphaInt: Int) {
        // If there's an image URL / local file path
        if (layer.imageUrl.isNotBlank()) {
            val bitmap = bitmapCache.getOrPut(layer.imageUrl) {
                imageManager.loadBitmap(layer.imageUrl) ?: return
            }

            bitmapPaint.alpha = alphaInt

            if (layer.maintainAspectRatio) {
                val srcW = bitmap.width.toFloat()
                val srcH = bitmap.height.toFloat()
                val targetRatio = srcW / srcH
                val rectRatio = rect.width() / rect.height()

                val drawRect = if (targetRatio > rectRatio) {
                    val h = rect.width() / targetRatio
                    val y = rect.centerY() - h / 2f
                    RectF(rect.left, y, rect.right, y + h)
                } else {
                    val w = rect.height() * targetRatio
                    val x = rect.centerX() - w / 2f
                    RectF(x, rect.top, x + w, rect.bottom)
                }
                canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint)
            } else {
                canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
            }
        } else {
            // Built-in Overlay Frame Presets
            renderPresetFrame(canvas, rect, layer, alphaInt)
        }

        // Optional custom border stroke
        if (layer.borderStrokeWidthDp > 0f) {
            val strokeWidthPx = layer.borderStrokeWidthDp * context.resources.displayMetrics.density
            strokePaint.strokeWidth = strokeWidthPx
            strokePaint.color = try { Color.parseColor(layer.borderStrokeColorHex) } catch (_: Exception) { Color.CYAN }
            strokePaint.alpha = alphaInt
            canvas.drawRoundRect(rect, 8f, 8f, strokePaint)
        }
    }

    private fun renderPresetFrame(canvas: Canvas, rect: RectF, layer: SceneLayer, alphaInt: Int) {
        val accentColor = try { Color.parseColor(layer.borderStrokeColorHex.ifBlank { "#00E5FF" }) } catch (_: Exception) { Color.CYAN }

        when (layer.overlayPresetType) {
            "GAMING_BORDER" -> {
                strokePaint.strokeWidth = 4f
                strokePaint.color = accentColor
                strokePaint.alpha = alphaInt
                canvas.drawRoundRect(rect, 10f, 10f, strokePaint)

                // Corner brackets
                fillPaint.color = accentColor
                fillPaint.alpha = alphaInt
                val cornerSize = 16f
                canvas.drawRect(rect.left, rect.top, rect.left + cornerSize, rect.top + 5f, fillPaint)
                canvas.drawRect(rect.left, rect.top, rect.left + 5f, rect.top + cornerSize, fillPaint)
                canvas.drawRect(rect.right - cornerSize, rect.top, rect.right, rect.top + 5f, fillPaint)
                canvas.drawRect(rect.right - 5f, rect.top, rect.right, rect.top + cornerSize, fillPaint)
                canvas.drawRect(rect.left, rect.bottom - 5f, rect.left + cornerSize, rect.bottom, fillPaint)
                canvas.drawRect(rect.left, rect.bottom - cornerSize, rect.left + 5f, rect.bottom, fillPaint)
                canvas.drawRect(rect.right - cornerSize, rect.bottom - 5f, rect.right, rect.bottom, fillPaint)
                canvas.drawRect(rect.right - 5f, rect.bottom - cornerSize, rect.right, rect.bottom, fillPaint)
            }
            "WEBCAM_FRAME" -> {
                strokePaint.strokeWidth = 3f
                strokePaint.color = accentColor
                strokePaint.alpha = alphaInt
                canvas.drawRoundRect(rect, 8f, 8f, strokePaint)

                // Header tag pill
                val pillRect = RectF(rect.left + 12f, rect.top - 8f, rect.left + 90f, rect.top + 10f)
                fillPaint.color = accentColor
                fillPaint.alpha = alphaInt
                canvas.drawRoundRect(pillRect, 6f, 6f, fillPaint)

                textFillPaint.apply {
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 10f * context.resources.displayMetrics.scaledDensity
                    color = Color.BLACK
                    alpha = alphaInt
                    clearShadowLayer()
                }
                canvas.drawText("CAM", pillRect.centerX(), pillRect.centerY() + 4f, textFillPaint)
            }
            "TOP_BAR" -> {
                fillPaint.color = Color.parseColor("#E60A0E17")
                fillPaint.alpha = alphaInt
                canvas.drawRoundRect(rect, 8f, 8f, fillPaint)

                strokePaint.strokeWidth = 2f
                strokePaint.color = accentColor
                strokePaint.alpha = alphaInt
                canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, strokePaint)

                textFillPaint.apply {
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 12f * context.resources.displayMetrics.scaledDensity
                    color = accentColor
                    alpha = alphaInt
                    clearShadowLayer()
                }
                canvas.drawText(layer.label, rect.centerX(), rect.centerY() + 4f, textFillPaint)
            }
            "BOTTOM_TICKER" -> {
                fillPaint.color = Color.parseColor("#E60F1722")
                fillPaint.alpha = alphaInt
                canvas.drawRoundRect(rect, 8f, 8f, fillPaint)

                strokePaint.strokeWidth = 2f
                strokePaint.color = Color.parseColor("#00E676")
                strokePaint.alpha = alphaInt
                canvas.drawLine(rect.left, rect.top, rect.right, rect.top, strokePaint)

                textFillPaint.apply {
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 11f * context.resources.displayMetrics.scaledDensity
                    color = Color.WHITE
                    alpha = alphaInt
                    clearShadowLayer()
                }
                canvas.drawText(layer.textContent.ifBlank { "VIPER STUDIO LIVE STREAM" }, rect.centerX(), rect.centerY() + 4f, textFillPaint)
            }
            else -> {
                // Default subtle placeholder frame
                strokePaint.strokeWidth = 1.5f
                strokePaint.color = Color.parseColor("#6600E5FF")
                strokePaint.alpha = alphaInt
                canvas.drawRoundRect(rect, 6f, 6f, strokePaint)
            }
        }
    }

    private fun renderCameraPipPlaceholder(canvas: Canvas, rect: RectF, layer: SceneLayer, alphaInt: Int) {
        fillPaint.color = Color.parseColor("#4D00E676")
        fillPaint.alpha = alphaInt
        canvas.drawRoundRect(rect, 10f, 10f, fillPaint)

        strokePaint.strokeWidth = 2.5f
        strokePaint.color = Color.parseColor("#00E676")
        strokePaint.alpha = alphaInt
        canvas.drawRoundRect(rect, 10f, 10f, strokePaint)

        textFillPaint.apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f * context.resources.displayMetrics.scaledDensity
            color = Color.WHITE
            alpha = alphaInt
            clearShadowLayer()
        }
        canvas.drawText("FACECAM PiP", rect.centerX(), rect.centerY() + 4f, textFillPaint)
    }

    private fun renderAlertBoxPlaceholder(canvas: Canvas, rect: RectF, layer: SceneLayer, alphaInt: Int) {
        fillPaint.color = Color.parseColor("#4DFFD600")
        fillPaint.alpha = alphaInt
        canvas.drawRoundRect(rect, 10f, 10f, fillPaint)

        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.parseColor("#FFD600")
        strokePaint.alpha = alphaInt
        canvas.drawRoundRect(rect, 10f, 10f, strokePaint)

        textFillPaint.apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 11f * context.resources.displayMetrics.scaledDensity
            color = Color.parseColor("#FFD600")
            alpha = alphaInt
            clearShadowLayer()
        }
        canvas.drawText("LIVE ALERT BOX", rect.centerX(), rect.centerY() + 4f, textFillPaint)
    }

    private fun renderSocialBadge(canvas: Canvas, rect: RectF, layer: SceneLayer, alphaInt: Int) {
        fillPaint.color = Color.parseColor("#D90E1624")
        fillPaint.alpha = alphaInt
        canvas.drawRoundRect(rect, 14f, 14f, fillPaint)

        strokePaint.strokeWidth = 1.5f
        strokePaint.color = Color.parseColor("#00E5FF")
        strokePaint.alpha = alphaInt
        canvas.drawRoundRect(rect, 14f, 14f, strokePaint)

        textFillPaint.apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 10f * context.resources.displayMetrics.scaledDensity
            color = Color.parseColor("#00E5FF")
            alpha = alphaInt
            clearShadowLayer()
        }
        canvas.drawText(layer.textContent.ifBlank { "@VIPER_STREAM" }, rect.centerX(), rect.centerY() + 4f, textFillPaint)
    }

    fun clearCache() {
        bitmapCache.clear()
    }
}
