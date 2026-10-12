package com.example.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

object AlertSoundSynthesizer {

    private const val TAG = "AlertSoundSynthesizer"
    private val scope = CoroutineScope(Dispatchers.Default)
    private const val SAMPLE_RATE = 44100

    fun playFanfare() {
        scope.launch {
            val notes = listOf(523.25 to 120, 659.25 to 120, 784.00 to 120, 1046.50 to 380)
            generateAndPlayPcm(notes)
        }
    }

    fun playSuperChatChime() {
        scope.launch {
            val notes = listOf(880.0 to 90, 1174.66 to 90, 1760.0 to 120, 2093.0 to 300)
            generateAndPlayPcm(notes)
        }
    }

    fun playDonationPing() {
        scope.launch {
            val notes = listOf(659.25 to 80, 987.77 to 100, 1318.51 to 350)
            generateAndPlayPcm(notes)
        }
    }

    fun playFollowerBell() {
        scope.launch {
            val notes = listOf(784.0 to 150, 1046.5 to 250)
            generateAndPlayPcm(notes)
        }
    }

    private suspend fun generateAndPlayPcm(notes: List<Pair<Double, Int>>) {
        try {
            val totalDurationMs = notes.sumOf { it.second }
            val totalSamples = (SAMPLE_RATE * (totalDurationMs / 1000.0)).toInt()
            val buffer = ShortArray(totalSamples)

            var sampleIndex = 0
            for ((freq, durationMs) in notes) {
                val noteSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                for (i in 0 until noteSamples) {
                    if (sampleIndex >= totalSamples) break
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = 1.0 - (i.toDouble() / noteSamples).coerceIn(0.0, 1.0) * 0.7
                    val sampleVal = (sin(2.0 * Math.PI * freq * t) * 0.7 +
                            sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.2 +
                            sin(2.0 * Math.PI * (freq * 3.0) * t) * 0.1) * envelope
                    buffer[sampleIndex] = (sampleVal * 26000).toInt().coerceIn(-32768, 32767).toShort()
                    sampleIndex++
                }
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Non-blocking coroutine delay instead of Thread.sleep
            delay(totalDurationMs.toLong() + 60)

            try {
                if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop()
                }
                audioTrack.release()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Procedural audio chime playback error: ${e.message}")
        }
    }
}
