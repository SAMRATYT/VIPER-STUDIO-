package com.example.engine.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.AudioCaptureMode
import com.example.data.model.AudioMixConfig
import com.example.engine.hardware.ScreenCaptureEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class AudioMixerEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioMixerEngine"
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var captureJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private val prefsRepo = com.example.data.local.AppPreferencesRepository(context)

    private val _config = MutableStateFlow(prefsRepo.loadAudioConfig())
    val config: StateFlow<AudioMixConfig> = _config.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _gameLevel = MutableStateFlow(0f)
    val gameLevel: StateFlow<Float> = _gameLevel.asStateFlow()

    init {
        // Synchronize initial restored preferences with hardware encoder
        syncWithEncoder(_config.value)
    }

    fun updateConfig(newConfig: AudioMixConfig) {
        _config.value = newConfig
        applyAudioEffects(newConfig)
        syncWithEncoder(newConfig)
    }

    fun setGameVolume(volume: Float) {
        val updated = _config.value.copy(gameAudioVolume = volume.coerceIn(0f, 2.0f))
        _config.value = updated
        syncWithEncoder(updated)
    }

    fun setMicVolume(volume: Float) {
        val updated = _config.value.copy(micAudioVolume = volume.coerceIn(0f, 2.0f))
        _config.value = updated
        syncWithEncoder(updated)
    }

    fun toggleInternalAudio() {
        val current = _config.value.isInternalAudioEnabled
        val updated = _config.value.copy(isInternalAudioEnabled = !current)
        _config.value = updated
        syncWithEncoder(updated)
    }

    fun toggleMicAudio() {
        val current = _config.value.isMicAudioEnabled
        val updated = _config.value.copy(isMicAudioEnabled = !current)
        _config.value = updated
        syncWithEncoder(updated)
    }

    fun toggleMicMute() {
        val current = _config.value.isMicMuted
        val updated = _config.value.copy(isMicMuted = !current)
        _config.value = updated
        syncWithEncoder(updated)
    }

    fun toggleNoiseSuppression() {
        val current = _config.value.isNoiseSuppressionEnabled
        val updated = _config.value.copy(isNoiseSuppressionEnabled = !current)
        _config.value = updated
        applyAudioEffects(updated)
        syncWithEncoder(updated)
    }

    fun toggleEchoCancellation() {
        val current = _config.value.isEchoCancellationEnabled
        val updated = _config.value.copy(isEchoCancellationEnabled = !current)
        _config.value = updated
        applyAudioEffects(updated)
        syncWithEncoder(updated)
    }

    fun setAudioDelay(delayMs: Int) {
        val updated = _config.value.copy(audioDelayMs = delayMs.coerceIn(-500, 500))
        _config.value = updated
        syncWithEncoder(updated)
    }

    fun setAudioCaptureMode(mode: AudioCaptureMode) {
        val updated = _config.value.copy(audioCaptureMode = mode)
        _config.value = updated
        syncWithEncoder(updated)
    }

    private fun syncWithEncoder(cfg: AudioMixConfig) {
        try {
            prefsRepo.saveAudioConfig(cfg)
            ScreenCaptureEncoder.getInstance(context).updateAudioConfig(cfg)
        } catch (e: Exception) {
            Log.w(TAG, "Sync with encoder error: ${e.message}")
        }
    }

    fun startAudioMonitoring() {
        if (captureJob?.isActive == true) return

        captureJob = scope.launch {
            val buffer = ShortArray(1024)
            while (isActive) {
                val encoder = ScreenCaptureEncoder.getInstance(context)

                // If hardware screen capture is actively running, mirror live levels directly from hardware pipeline
                if (encoder.liveMicLevel > 0f || encoder.liveGameLevel > 0f) {
                    _micLevel.value = encoder.liveMicLevel
                    _gameLevel.value = encoder.liveGameLevel
                    delay(50)
                    continue
                }

                // Otherwise, perform standalone preview monitoring
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPerm && (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED)) {
                    initAudioRecord()
                }

                var currentMicLevel = 0f
                val cfg = _config.value

                if (hasPerm && audioRecord != null && !cfg.isMicMuted && cfg.isMicAudioEnabled &&
                    cfg.audioCaptureMode != AudioCaptureMode.INTERNAL_ONLY &&
                    audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    try {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            currentMicLevel = AudioProcessingUtils.calculateNormalizedLevel(buffer, read) * cfg.micAudioVolume
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio record read error", e)
                    }
                }

                // Realistic internal game audio preview pulsation if game volume is active, internal audio is enabled, and mode includes internal audio
                val gameSim = if (cfg.isInternalAudioEnabled && cfg.gameAudioVolume > 0.05f && cfg.audioCaptureMode != AudioCaptureMode.MIC_ONLY) {
                    val basePulse = (0.20f + (System.currentTimeMillis() % 1200) / 2400f) * cfg.gameAudioVolume
                    basePulse.coerceIn(0f, 1f)
                } else 0f

                _micLevel.value = currentMicLevel.coerceIn(0f, 1f)
                _gameLevel.value = gameSim

                delay(60)
            }
        }
    }

    private fun initAudioRecord() {
        try {
            audioRecord?.release()
            val sampleRate = _config.value.sampleRateHz
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBuf, 4096)

            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = rec
                applyAudioEffects(_config.value)
                rec.startRecording()
                Log.i(TAG, "AudioRecord monitoring successfully initialized.")
            } else {
                Log.w(TAG, "AudioRecord failed to initialize")
                rec.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing AudioRecord: ${e.message}")
        }
    }

    private fun applyAudioEffects(cfg: AudioMixConfig) {
        val session = audioRecord?.audioSessionId ?: return
        if (session <= 0) return

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                if (echoCanceler == null) {
                    echoCanceler = AcousticEchoCanceler.create(session)
                }
                echoCanceler?.enabled = cfg.isEchoCancellationEnabled
            }

            if (NoiseSuppressor.isAvailable()) {
                if (noiseSuppressor == null) {
                    noiseSuppressor = NoiseSuppressor.create(session)
                }
                noiseSuppressor?.enabled = cfg.isNoiseSuppressionEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect binding exception", e)
        }
    }

    fun stopAudioMonitoring() {
        captureJob?.cancel()
        captureJob = null
        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Audio cleanup error", e)
        }
        _micLevel.value = 0f
        _gameLevel.value = 0f
    }
}
