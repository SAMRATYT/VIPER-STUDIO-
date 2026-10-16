package com.example.engine.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Manages importing, persistent caching, and decoding of custom gallery images,
 * logos, watermarks, and stream frame overlays for VIPER STUDIO Scene Editor.
 */
class SceneImageManager(private val context: Context) {

    companion object {
        private const val TAG = "SceneImageManager"
        private const val ASSETS_DIR = "scene_assets"
        private const val MAX_IMAGE_DIMENSION = 2048

        @Volatile
        private var instance: SceneImageManager? = null

        fun getInstance(context: Context): SceneImageManager {
            return instance ?: synchronized(this) {
                instance ?: SceneImageManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val storageDir: File by lazy {
        File(context.filesDir, ASSETS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Imports an image from a content URI (e.g. from PhotoPicker), persists it locally
     * so permissions never expire, and returns the absolute file URI string.
     */
    fun importImageFromUri(sourceUri: Uri, prefix: String = "overlay"): String? {
        return try {
            val extension = when (context.contentResolver.getType(sourceUri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "png" // default to png to preserve transparency
            }
            val fileName = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
            val targetFile = File(storageDir, fileName)

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream == null) {
                    Log.e(TAG, "Cannot open stream for URI: $sourceUri")
                    return null
                }
                outputStream = FileOutputStream(targetFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                Log.i(TAG, "Successfully imported image to: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                "file://${targetFile.absolutePath}"
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import image from URI: $sourceUri", e)
            null
        }
    }

    /**
     * Safely decodes a bitmap from a file URI or path, respecting max dimensions to prevent OOM.
     */
    fun loadBitmap(uriString: String, maxDimension: Int = MAX_IMAGE_DIMENSION): Bitmap? {
        if (uriString.isBlank()) return null
        return try {
            val file = if (uriString.startsWith("file://")) {
                File(uriString.removePrefix("file://"))
            } else {
                File(uriString)
            }

            if (!file.exists()) {
                Log.w(TAG, "Image file does not exist: ${file.absolutePath}")
                return null
            }

            // Decode dimensions first
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var inSampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from $uriString", e)
            null
        }
    }

    /**
     * Deletes unused custom image files when a layer is permanently removed.
     */
    fun deleteImageFile(uriString: String) {
        try {
            val file = if (uriString.startsWith("file://")) {
                File(uriString.removePrefix("file://"))
            } else {
                File(uriString)
            }
            if (file.exists() && file.parentFile == storageDir) {
                file.delete()
                Log.d(TAG, "Deleted local image file: ${file.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete image: $uriString", e)
        }
    }
}
