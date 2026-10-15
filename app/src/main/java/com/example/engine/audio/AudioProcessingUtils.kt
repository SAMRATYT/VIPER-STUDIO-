package com.example.engine.audio

import com.example.data.model.AudioCaptureMode
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * High-performance audio DSP and mathematics utilities.
 * Handles channel alignment, resampling, audio mixing, soft-clipping limiter,
 * and VU meter level calculations.
 */
object AudioProcessingUtils {

    const val SAMPLE_RATE_HZ = 48000
    const val CHANNEL_COUNT_STEREO = 2
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM

    // 1024 stereo frames = 2048 shorts = 4096 bytes (21.33ms at 48kHz)
    const val FRAME_SIZE_STEREO_SAMPLES = 1024
    const val FRAME_SIZE_SHORTS = FRAME_SIZE_STEREO_SAMPLES * CHANNEL_COUNT_STEREO
    const val FRAME_SIZE_BYTES = FRAME_SIZE_SHORTS * BYTES_PER_SAMPLE

    private const val THRESHOLD_LINEAR = 24000f
    private const val MAX_HEADROOM = 8767f // 32767 - 24000

    /**
     * Smooth mathematical soft-clipping saturation limiter.
     * Prevents harsh digital clipping, pops, and crackles when game sound and shouting mic peak simultaneously.
     * Strictly limits output to [-32767, 32767] with zero harmonic wrap-around.
     */
    fun softClip(sample: Float): Short {
        return when {
            sample > THRESHOLD_LINEAR -> {
                val excess = sample - THRESHOLD_LINEAR
                val compressed = THRESHOLD_LINEAR + MAX_HEADROOM * (excess / (excess + MAX_HEADROOM))
                compressed.coerceAtMost(32767f).toInt().toShort()
            }
            sample < -THRESHOLD_LINEAR -> {
                val excess = -sample - THRESHOLD_LINEAR
                val compressed = -(THRESHOLD_LINEAR + MAX_HEADROOM * (excess / (excess + MAX_HEADROOM)))
                compressed.coerceAtLeast(-32767f).toInt().toShort()
            }
            else -> sample.toInt().toShort()
        }
    }

    /**
     * Mixes interleaved stereo PCM buffers from Microphone and Game playback capture
     * with independent volume gain, calibrated preamp gain, mute handling, and selectable AudioCaptureMode.
     */
    fun mixStereoPcm(
        micPcm: ShortArray,
        micVolume: Float,
        micGain: Float = 1.0f,
        isMicMuted: Boolean,
        isMicEnabled: Boolean = true,
        gamePcm: ShortArray,
        gameVolume: Float,
        isInternalAudioEnabled: Boolean = true,
        outputPcm: ShortArray,
        mode: AudioCaptureMode
    ) {
        val count = outputPcm.size.coerceAtMost(micPcm.size).coerceAtMost(gamePcm.size)

        when (mode) {
            AudioCaptureMode.INTERNAL_ONLY -> {
                // Game only; mic completely muted and excluded
                if (!isInternalAudioEnabled || gameVolume <= 0f) {
                    outputPcm.fill(0, 0, count)
                } else {
                    for (i in 0 until count) {
                        val gameSample = gamePcm[i] * gameVolume
                        outputPcm[i] = softClip(gameSample)
                    }
                }
            }
            AudioCaptureMode.MIC_ONLY -> {
                // Mic only; game completely excluded
                val effectiveMicVol = if (isMicMuted || !isMicEnabled) 0f else (micVolume * micGain)
                if (effectiveMicVol <= 0f) {
                    outputPcm.fill(0, 0, count)
                } else {
                    for (i in 0 until count) {
                        val micSample = micPcm[i] * effectiveMicVol
                        outputPcm[i] = softClip(micSample)
                    }
                }
            }
            AudioCaptureMode.INTERNAL_AND_MIC -> {
                // Independent toggles: when microphone is OFF, NEVER mix mic samples.
                // When microphone is ON, mix both safely with calibrated headroom and soft-clipping limiter.
                val effectiveMicVol = if (isMicMuted || !isMicEnabled) 0f else (micVolume * micGain)
                val effectiveGameVol = if (!isInternalAudioEnabled) 0f else gameVolume

                if (effectiveMicVol <= 0f && effectiveGameVol <= 0f) {
                    outputPcm.fill(0, 0, count)
                } else if (effectiveMicVol <= 0f) {
                    for (i in 0 until count) {
                        outputPcm[i] = softClip(gamePcm[i] * effectiveGameVol)
                    }
                } else if (effectiveGameVol <= 0f) {
                    for (i in 0 until count) {
                        outputPcm[i] = softClip(micPcm[i] * effectiveMicVol)
                    }
                } else {
                    for (i in 0 until count) {
                        val micSample = micPcm[i] * effectiveMicVol
                        val gameSample = gamePcm[i] * effectiveGameVol
                        outputPcm[i] = softClip(micSample + gameSample)
                    }
                }
            }
        }
    }

    /**
     * Overload for backwards compatibility with tests and callers omitting micGain.
     */
    fun mixStereoPcm(
        micPcm: ShortArray,
        micVolume: Float,
        isMicMuted: Boolean,
        isMicEnabled: Boolean = true,
        gamePcm: ShortArray,
        gameVolume: Float,
        isInternalAudioEnabled: Boolean = true,
        outputPcm: ShortArray,
        mode: AudioCaptureMode
    ) = mixStereoPcm(
        micPcm = micPcm,
        micVolume = micVolume,
        micGain = 1.0f,
        isMicMuted = isMicMuted,
        isMicEnabled = isMicEnabled,
        gamePcm = gamePcm,
        gameVolume = gameVolume,
        isInternalAudioEnabled = isInternalAudioEnabled,
        outputPcm = outputPcm,
        mode = mode
    )

    /**
     * Dedicated 2nd-order Butterworth IIR High-Pass Filter with a 100 Hz cutoff frequency at 48,000 Hz.
     * Eliminates sub-bass acoustic rumble, electrical 50/60 Hz hum, proximity effect 'mud',
     * and smartphone chassis vibration (especially on Motorola and modern multi-mic devices).
     *
     * Transfer function H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
     * Normalized coefficients for fc = 100 Hz, fs = 48000 Hz, Q = 0.7071:
     *   b0 =  0.9907868f
     *   b1 = -1.9815736f
     *   b2 =  0.9907868f
     *   a1 = -1.9814885f
     *   a2 =  0.9816578f
     */
    class HighPassFilter100Hz {
        private var x1L = 0f
        private var x2L = 0f
        private var y1L = 0f
        private var y2L = 0f

        private var x1R = 0f
        private var x2R = 0f
        private var y1R = 0f
        private var y2R = 0f

        fun reset() {
            x1L = 0f; x2L = 0f; y1L = 0f; y2L = 0f
            x1R = 0f; x2R = 0f; y1R = 0f; y2R = 0f
        }

        fun processStereo(buffer: ShortArray, length: Int) {
            val b0 = 0.9907868f
            val b1 = -1.9815736f
            val b2 = 0.9907868f
            val a1 = -1.9814885f
            val a2 = 0.9816578f

            val safeLimit = length.coerceAtMost(buffer.size)
            for (i in 0 until safeLimit step 2) {
                val x0L = buffer[i].toFloat()
                val y0L = b0 * x0L + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
                x2L = x1L
                x1L = x0L
                y2L = y1L
                y1L = y0L
                buffer[i] = y0L.coerceIn(-32767f, 32767f).toInt().toShort()

                val rIdx = i + 1
                if (rIdx < safeLimit) {
                    val x0R = buffer[rIdx].toFloat()
                    val y0R = b0 * x0R + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
                    x2R = x1R
                    x1R = x0R
                    y2R = y1R
                    y1R = y0R
                    buffer[rIdx] = y0R.coerceIn(-32767f, 32767f).toInt().toShort()
                }
            }
        }

        fun processMono(buffer: ShortArray, length: Int) {
            val b0 = 0.9907868f
            val b1 = -1.9815736f
            val b2 = 0.9907868f
            val a1 = -1.9814885f
            val a2 = 0.9816578f

            val safeLimit = length.coerceAtMost(buffer.size)
            for (i in 0 until safeLimit) {
                val x0 = buffer[i].toFloat()
                val y0 = b0 * x0 + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
                x2L = x1L
                x1L = x0
                y2L = y1L
                y1L = y0
                buffer[i] = y0.coerceIn(-32767f, 32767f).toInt().toShort()
            }
        }
    }

    /**
     * Expands mono 16-bit PCM to interleaved stereo 16-bit PCM (duplicates L into R).
     */
    fun upmixMonoToStereo(monoPcm: ShortArray, monoLength: Int, stereoOut: ShortArray): Int {
        val frames = monoLength.coerceAtMost(stereoOut.size / 2)
        for (i in 0 until frames) {
            val sample = monoPcm[i]
            stereoOut[i * 2] = sample
            stereoOut[i * 2 + 1] = sample
        }
        return frames * 2
    }

    /**
     * Fast linear interpolation resampler from srcRate to dstRate for 16-bit PCM shorts.
     */
    fun resampleLinearStereo(
        src: ShortArray,
        srcFrames: Int,
        srcRate: Int,
        dst: ShortArray,
        dstFrames: Int,
        dstRate: Int
    ): Int {
        if (srcRate == dstRate) {
            val copyCount = (srcFrames * 2).coerceAtMost(dst.size)
            System.arraycopy(src, 0, dst, 0, copyCount)
            return copyCount
        }

        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outFrames = (srcFrames / ratio).toInt().coerceAtMost(dstFrames)

        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()

            val srcL0 = if (srcIdx * 2 < src.size) src[srcIdx * 2] else 0
            val srcL1 = if ((srcIdx + 1) * 2 < src.size) src[(srcIdx + 1) * 2] else srcL0
            val outL = srcL0 + frac * (srcL1 - srcL0)

            val srcR0 = if (srcIdx * 2 + 1 < src.size) src[srcIdx * 2 + 1] else 0
            val srcR1 = if ((srcIdx + 1) * 2 + 1 < src.size) src[(srcIdx + 1) * 2 + 1] else srcR0
            val outR = srcR0 + frac * (srcR1 - srcR0)

            dst[i * 2] = outL.toInt().toShort()
            dst[i * 2 + 1] = outR.toInt().toShort()
        }

        return outFrames * 2
    }

    /**
     * Computes normalized VU level [0.0f, 1.0f] from 16-bit PCM buffer.
     */
    fun calculateNormalizedLevel(buffer: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toDouble()
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / length)
        if (rms <= 1.0) return 0f
        val db = 20 * log10(rms / 32767.0)
        return ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
    }

    // High-pass DC-block filter state for microphone channel
    private var hpPrevXLeft = 0f
    private var hpPrevYLeft = 0f
    private var hpPrevXRight = 0f
    private var hpPrevYRight = 0f

    // Smoothed noise floor envelope
    private var smoothedNoiseFloor = 350f
    private var gainEnvelope = 1.0f

    /**
     * Software Digital Signal Processing (DSP) Noise Suppressor for Microphone Voice.
     * Removes 50/60Hz ground hum, low rumble (<80Hz), and attenuates ambient background hiss
     * using an adaptive downward expander.
     * Operates exclusively on microphone PCM without affecting internal game audio.
     */
    @Synchronized
    fun suppressMicNoise(buffer: ShortArray, length: Int) {
        if (length <= 0) return

        // 1. Calculate frame RMS energy
        var sumSq = 0.0
        for (i in 0 until length) {
            val s = buffer[i].toDouble()
            sumSq += s * s
        }
        val currentRms = sqrt(sumSq / length).toFloat()

        // Adaptive noise floor tracking (slowly adapts to ambient background)
        if (currentRms < smoothedNoiseFloor * 2.0f) {
            smoothedNoiseFloor = (smoothedNoiseFloor * 0.95f) + (currentRms * 0.05f)
        } else {
            smoothedNoiseFloor = (smoothedNoiseFloor * 0.999f) + (currentRms * 0.001f)
        }
        val noiseThreshold = smoothedNoiseFloor.coerceIn(120f, 1200f)

        // Target gain: if signal is close to or below noise floor, smoothly attenuate
        val targetGain = when {
            currentRms > noiseThreshold * 2.2f -> 1.0f
            currentRms > noiseThreshold -> {
                val ratio = (currentRms - noiseThreshold) / (noiseThreshold * 1.2f)
                0.20f + 0.80f * ratio
            }
            else -> 0.12f // ~18dB background hiss attenuation
        }

        // 2. High-pass filter coefficient (R ≈ 0.985 corresponds to ~80Hz cutoff at 48kHz)
        val R = 0.985f

        for (i in 0 until length step 2) {
            // Smooth gain envelope transition (fast attack ~3ms, smooth release ~120ms)
            if (gainEnvelope < targetGain) {
                gainEnvelope += (targetGain - gainEnvelope) * 0.04f
            } else {
                gainEnvelope += (targetGain - gainEnvelope) * 0.008f
            }

            // Left channel high-pass
            val xL = buffer[i].toFloat()
            val yL = xL - hpPrevXLeft + R * hpPrevYLeft
            hpPrevXLeft = xL
            hpPrevYLeft = yL

            // Right channel high-pass
            val rightIdx = (i + 1).coerceAtMost(length - 1)
            val xR = buffer[rightIdx].toFloat()
            val yR = xR - hpPrevXRight + R * hpPrevYRight
            hpPrevXRight = xR
            hpPrevYRight = yR

            // Apply noise-reduction gain & soft limiter
            buffer[i] = softClip(yL * gainEnvelope)
            if (rightIdx < length) {
                buffer[rightIdx] = softClip(yR * gainEnvelope)
            }
        }
    }
}
