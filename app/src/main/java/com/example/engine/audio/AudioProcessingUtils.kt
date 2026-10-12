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
     * with independent volume gain, mute handling, and selectable AudioCaptureMode.
     */
    fun mixStereoPcm(
        micPcm: ShortArray,
        micVolume: Float,
        isMicMuted: Boolean,
        gamePcm: ShortArray,
        gameVolume: Float,
        outputPcm: ShortArray,
        mode: AudioCaptureMode
    ) {
        val count = outputPcm.size.coerceAtMost(micPcm.size).coerceAtMost(gamePcm.size)

        when (mode) {
            AudioCaptureMode.INTERNAL_ONLY -> {
                // Game only; mic completely muted
                for (i in 0 until count) {
                    val gameSample = gamePcm[i] * gameVolume
                    outputPcm[i] = softClip(gameSample)
                }
            }
            AudioCaptureMode.MIC_ONLY -> {
                // Mic only; game muted
                if (isMicMuted) {
                    outputPcm.fill(0, 0, count)
                } else {
                    for (i in 0 until count) {
                        val micSample = micPcm[i] * micVolume
                        outputPcm[i] = softClip(micSample)
                    }
                }
            }
            AudioCaptureMode.INTERNAL_AND_MIC -> {
                // Mix both internal game and microphone
                val effectiveMicVol = if (isMicMuted) 0f else micVolume
                for (i in 0 until count) {
                    val micSample = if (effectiveMicVol > 0f) micPcm[i] * effectiveMicVol else 0f
                    val gameSample = if (gameVolume > 0f) gamePcm[i] * gameVolume else 0f
                    val mixed = micSample + gameSample
                    outputPcm[i] = softClip(mixed)
                }
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
}
