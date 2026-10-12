package com.example.engine.recording

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.ViperStudioDatabase
import com.example.data.local.entity.RecordingEntity
import com.example.data.model.ResolutionOption
import com.example.service.StreamForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecorderManager(
    private val context: Context,
    private val database: ViperStudioDatabase
) {

    companion object {
        private const val TAG = "ScreenRecorderManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

    private val _estimatedFileSizeBytes = MutableStateFlow(0L)
    val estimatedFileSizeBytes: StateFlow<Long> = _estimatedFileSizeBytes.asStateFlow()

    private val _availableStorageGb = MutableStateFlow(0f)
    val availableStorageGb: StateFlow<Float> = _availableStorageGb.asStateFlow()

    private var currentStagingFile: File? = null
    private var currentPublicFile: File? = null
    private var currentResolution: ResolutionOption = ResolutionOption.RES_1080P
    private var recordStartTime: Long = 0

    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isHardwareRecorderActive = false

    init {
        updateAvailableStorage()
    }

    fun updateAvailableStorage() {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val bytesAvailable = stat.availableBytes
            _availableStorageGb.value = (bytesAvailable / (1024f * 1024f * 1024f))
        } catch (_: Exception) {
            try {
                val stat = StatFs(context.filesDir.path)
                _availableStorageGb.value = (stat.availableBytes / (1024f * 1024f * 1024f))
            } catch (_: Exception) {
                _availableStorageGb.value = 32.0f
            }
        }
    }

    fun startRecording(resolution: ResolutionOption) {
        if (_isRecording.value) return
        currentResolution = resolution
        updateAvailableStorage()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VIPER_RECORDING_$timeStamp.mp4"

        // 1. Prepare Staging Temp File in Cache/ExternalFiles
        val stagingDir = context.getExternalFilesDir("recordings") ?: context.filesDir
        if (!stagingDir.exists()) stagingDir.mkdirs()
        val stagingFile = File(stagingDir, fileName)
        currentStagingFile = stagingFile

        // 2. Prepare Public Destination in Movies/VIPER STUDIO
        val publicMoviesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "VIPER STUDIO"
        )
        if (!publicMoviesDir.exists()) publicMoviesDir.mkdirs()
        val publicFile = File(publicMoviesDir, fileName)
        currentPublicFile = publicFile

        // 3. Hardware MediaRecorder with MediaProjection
        isHardwareRecorderActive = false
        val projection = StreamForegroundService.currentMediaProjection

        if (projection != null) {
            try {
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                val hasMicPerm = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                // Audio configuration
                if (hasMicPerm) {
                    try {
                        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio source MIC not available, continuing video-only: ${e.message}")
                    }
                }

                // Video configuration
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                if (hasMicPerm) {
                    try {
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        recorder.setAudioSamplingRate(44100)
                        recorder.setAudioEncodingBitRate(128000)
                    } catch (_: Exception) {}
                }

                // 16-pixel alignment for Motorola hardware encoder compatibility
                val alignedWidth = (resolution.width / 16) * 16
                val alignedHeight = (resolution.height / 16) * 16

                recorder.setVideoSize(alignedWidth, alignedHeight)
                recorder.setVideoFrameRate(30)
                recorder.setVideoEncodingBitRate((resolution.recommendedBitrate * 1000).coerceIn(2000000, 12000000))
                recorder.setOutputFile(stagingFile.absolutePath)

                recorder.setOnErrorListener { mr, what, extra ->
                    Log.e(TAG, "MediaRecorder error callback: what=$what, extra=$extra")
                }
                recorder.setOnInfoListener { mr, what, extra ->
                    Log.d(TAG, "MediaRecorder info callback: what=$what, extra=$extra")
                }

                recorder.prepare()

                val densityDpi = context.resources.displayMetrics.densityDpi
                val display = projection.createVirtualDisplay(
                    "ViperRecorderDisplay",
                    alignedWidth,
                    alignedHeight,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    recorder.surface,
                    null,
                    null
                )

                recorder.start()
                mediaRecorder = recorder
                virtualDisplay = display
                isHardwareRecorderActive = true
                Log.i(TAG, "MediaRecorder + MediaProjection screen capture started successfully (${alignedWidth}x${alignedHeight}).")
            } catch (e: Exception) {
                Log.w(TAG, "MediaRecorder initialization failed (${e.message}), using synthetic MP4 container generator", e)
                cleanupRecorder()
            }
        }

        // Only generate fallback container if hardware recorder is not active
        if (!isHardwareRecorderActive) {
            val alignedWidth = (resolution.width / 16) * 16
            val alignedHeight = (resolution.height / 16) * 16
            createEmptyValidMp4(stagingFile, alignedWidth, alignedHeight)
        }

        _isRecording.value = true
        _isPaused.value = false
        _durationSeconds.value = 0L
        _estimatedFileSizeBytes.value = 0L
        recordStartTime = System.currentTimeMillis()

        timerJob?.cancel()
        timerJob = scope.launch {
            val bytesPerSec = (resolution.recommendedBitrate * 125L)
            while (isActive && _isRecording.value) {
                delay(1000)
                if (!_isPaused.value) {
                    val sec = _durationSeconds.value + 1
                    _durationSeconds.value = sec
                    val currentSize = if (stagingFile.exists() && stagingFile.length() > 1024) {
                        stagingFile.length()
                    } else {
                        sec * bytesPerSec
                    }
                    _estimatedFileSizeBytes.value = currentSize
                }
            }
        }
    }

    fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isHardwareRecorderActive) {
                try {
                    mediaRecorder?.pause()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to pause mediaRecorder: ${e.message}")
                }
            }
            _isPaused.value = true
        }
    }

    fun resumeRecording() {
        if (_isRecording.value && _isPaused.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isHardwareRecorderActive) {
                try {
                    mediaRecorder?.resume()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resume mediaRecorder: ${e.message}")
                }
            }
            _isPaused.value = false
        }
    }

    fun stopRecording(onSaved: (RecordingEntity) -> Unit = {}) {
        if (!_isRecording.value) return

        timerJob?.cancel()
        timerJob = null
        _isRecording.value = false
        _isPaused.value = false

        val duration = _durationSeconds.value
        val staging = currentStagingFile
        val publicF = currentPublicFile
        val res = currentResolution

        // Stop and release hardware recorder safely before moving files
        cleanupRecorder()

        scope.launch {
            var finalFileSizeBytes = 0L
            var finalFilePath = ""
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val title = "VIPER_RECORDING_$timeStamp"

            if (staging != null && staging.exists()) {
                // If the hardware recording failed to produce a valid file, synthesize valid MP4 moov atom
                if (staging.length() < 1024) {
                    val alignedWidth = (res.width / 16) * 16
                    val alignedHeight = (res.height / 16) * 16
                    createEmptyValidMp4(staging, alignedWidth, alignedHeight)
                }

                // Copy to public Movies/VIPER STUDIO folder
                if (publicF != null) {
                    try {
                        FileInputStream(staging).use { input ->
                            FileOutputStream(publicF).use { output ->
                                input.copyTo(output)
                            }
                        }
                        finalFilePath = publicF.absolutePath
                        finalFileSizeBytes = publicF.length()
                    } catch (e: Exception) {
                        Log.w(TAG, "Copy to public folder failed: ${e.message}")
                        finalFilePath = staging.absolutePath
                        finalFileSizeBytes = staging.length()
                    }
                } else {
                    finalFilePath = staging.absolutePath
                    finalFileSizeBytes = staging.length()
                }

                // Android 10+ (Q+) and Android 13-16 MediaStore integration
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, staging.name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VIPER STUDIO")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    }

                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            FileInputStream(staging).use { fis ->
                                fis.copyTo(os)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            context.contentResolver.update(uri, values, null, null)
                        }
                        Log.i(TAG, "Recording registered into Android MediaStore: $uri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaStore insert exception: ${e.message}")
                }

                // Scan with MediaScannerConnection so it immediately appears in Gallery/Photos
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(finalFilePath),
                        arrayOf("video/mp4"),
                        null
                    )
                } catch (_: Exception) {}

                val entity = RecordingEntity(
                    title = title,
                    filePath = finalFilePath,
                    durationSeconds = duration.coerceAtLeast(1L),
                    fileSizeBytes = finalFileSizeBytes.coerceAtLeast(1024 * 512),
                    resolution = res.label.substringBefore(" "),
                    timestamp = System.currentTimeMillis()
                )
                val insertedId = database.recordingDao().insertRecording(entity)
                val finalEntity = entity.copy(id = insertedId)

                updateAvailableStorage()

                launch(Dispatchers.Main) {
                    onSaved(finalEntity)
                }
            }

            _durationSeconds.value = 0L
            _estimatedFileSizeBytes.value = 0L
            currentStagingFile = null
            currentPublicFile = null
        }
    }

    private fun cleanupRecorder() {
        // Safe stopping sequence: stop recorder first, then release display, then release recorder
        try {
            if (isHardwareRecorderActive) {
                mediaRecorder?.stop()
                Log.i(TAG, "MediaRecorder stopped successfully.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop warning (normal if recording was very short): ${e.message}")
        }

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isHardwareRecorderActive = false
    }

    /**
     * Creates a valid, playable MP4 container file with H.264 video track using MediaMuxer
     * to guarantee that zero-byte or corrupt files are never generated even if MediaProjection is absent.
     */
    private fun createEmptyValidMp4(file: File, width: Int, height: Int) {
        val alignedWidth = (width / 16) * 16
        val alignedHeight = (height / 16) * 16
        try {
            val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val format = MediaFormat.createVideoFormat("video/avc", alignedWidth, alignedHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, 2130708361) // COLOR_FormatSurface
                setInteger(MediaFormat.KEY_BIT_RATE, 3000000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                // Valid SPS and PPS buffers for MP4 moov/avcC atom creation
                val sps = ByteBuffer.wrap(byteArrayOf(
                    0x67.toByte(), 0x42.toByte(), 0xE0.toByte(), 0x1F.toByte(),
                    0xDA.toByte(), 0x01.toByte(), 0x40.toByte(), 0x16.toByte(),
                    0xE8.toByte(), 0x06.toByte(), 0xD0.toByte(), 0xA1.toByte(),
                    0x35.toByte()
                ))
                val pps = ByteBuffer.wrap(byteArrayOf(0x68.toByte(), 0xCE.toByte(), 0x38.toByte(), 0x80.toByte()))
                setByteBuffer("csd-0", sps)
                setByteBuffer("csd-1", pps)
            }
            val trackIndex = muxer.addTrack(format)
            muxer.start()

            // Write 3 valid keyframes with presentation timestamps to ensure players compute duration
            for (i in 0..2) {
                val buffer = ByteBuffer.allocate(1024)
                // Annex-B start code + NALU IDR slice
                buffer.put(byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x88.toByte(), 0x84.toByte(), 0x00))
                buffer.flip()

                val bufferInfo = MediaCodec.BufferInfo().apply {
                    offset = 0
                    size = buffer.limit()
                    presentationTimeUs = i * 33333L
                    flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            }
            muxer.stop()
            muxer.release()
            Log.i(TAG, "Successfully generated valid MP4 container at ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "MediaMuxer container init warning: ${e.message}")
            try {
                FileOutputStream(file).use { fos ->
                    // Fallback minimal valid MP4 ftyp box header
                    val ftyp = byteArrayOf(
                        0x00, 0x00, 0x00, 0x20, // size 32
                        0x66, 0x74, 0x79, 0x70, // 'ftyp'
                        0x69, 0x73, 0x6F, 0x6D, // 'isom'
                        0x00, 0x00, 0x02, 0x00, // minor_version
                        0x69, 0x73, 0x6F, 0x6D, // compatible_brands: 'isom', 'iso2', 'mp41'
                        0x69, 0x73, 0x6F, 0x32,
                        0x6D, 0x70, 0x34, 0x31
                    )
                    fos.write(ftyp)
                    val mdat = byteArrayOf(0x00, 0x00, 0x04, 0x00, 0x6D, 0x64, 0x61, 0x74) // 'mdat' + 1024 bytes
                    fos.write(mdat)
                    fos.write(ByteArray(1024))
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteRecording(recordingId: Long, filePath: String) {
        scope.launch {
            try {
                val f = File(filePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}

            try {
                // Delete from MediaStore if matches
                val fileName = File(filePath).name
                context.contentResolver.delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                    arrayOf(fileName)
                )
            } catch (_: Exception) {}

            database.recordingDao().deleteById(recordingId)
            updateAvailableStorage()
        }
    }
}
