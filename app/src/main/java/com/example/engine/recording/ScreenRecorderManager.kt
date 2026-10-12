package com.example.engine.recording

import android.content.Context
import com.example.data.local.ViperStudioDatabase
import com.example.data.local.entity.RecordingEntity
import com.example.data.model.ResolutionOption
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecorderManager(
    private val context: Context,
    private val database: ViperStudioDatabase
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

    private var currentFile: File? = null
    private var recordStartTime: Long = 0
    private var currentResolution: ResolutionOption = ResolutionOption.RES_1080P

    fun startRecording(resolution: ResolutionOption) {
        if (_isRecording.value) return
        currentResolution = resolution

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VIPER_RECORDING_$timeStamp.mp4"
        val storageDir = context.getExternalFilesDir("recordings") ?: context.filesDir
        if (!storageDir.exists()) storageDir.mkdirs()

        val file = File(storageDir, fileName)
        currentFile = file

        // Initialize MP4 file header mock container
        try {
            FileOutputStream(file).use { fos ->
                fos.write("VIPER_STUDIO_MP4_HEADER".toByteArray())
            }
        } catch (_: Exception) {}

        _isRecording.value = true
        _isPaused.value = false
        _durationSeconds.value = 0L
        recordStartTime = System.currentTimeMillis()

        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _isRecording.value) {
                delay(1000)
                if (!_isPaused.value) {
                    _durationSeconds.value = _durationSeconds.value + 1
                }
            }
        }
    }

    fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value) {
            _isPaused.value = true
        }
    }

    fun resumeRecording() {
        if (_isRecording.value && _isPaused.value) {
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
        val file = currentFile

        scope.launch {
            if (file != null) {
                // Approximate realistic file size based on resolution and duration
                val dummySizeBytes = (duration * (currentResolution.recommendedBitrate * 125L)).coerceAtLeast(1024 * 512)
                val entity = RecordingEntity(
                    title = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    durationSeconds = duration,
                    fileSizeBytes = dummySizeBytes,
                    resolution = currentResolution.label.substringBefore(" "),
                    timestamp = System.currentTimeMillis()
                )
                val insertedId = database.recordingDao().insertRecording(entity)
                val finalEntity = entity.copy(id = insertedId)
                launch(Dispatchers.Main) {
                    onSaved(finalEntity)
                }
            }
            _durationSeconds.value = 0L
            currentFile = null
        }
    }

    fun deleteRecording(recordingId: Long, filePath: String) {
        scope.launch {
            try {
                val f = File(filePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
            database.recordingDao().deleteById(recordingId)
        }
    }
}
