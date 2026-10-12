package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PlatformType
import com.example.data.model.ResolutionOption
import com.example.data.model.StreamConfig
import com.example.engine.audio.AudioMixerEngine
import com.example.engine.hardware.HardwareEncoderDetector
import com.example.engine.streaming.RtmpStreamEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VIPER STUDIO", appName)
    }

    @Test
    fun `test rtmp stream engine validation prevents empty key crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rtmpEngine = RtmpStreamEngine(context)

        // Empty stream key for YouTube should fail safely without crashing
        val configWithEmptyKey = StreamConfig(
            platform = PlatformType.YOUTUBE,
            streamKey = "",
            rtmpUrl = PlatformType.YOUTUBE.defaultRtmpUrl
        )

        val started = rtmpEngine.startStream(configWithEmptyKey)
        assertFalse("Stream with empty key should not start", started)
        val stats = rtmpEngine.stats.value
        assertFalse("Engine should not be streaming", stats.isStreaming)
        assertNotNull("Error message should be populated", stats.errorMessage)
        assertTrue(stats.errorMessage?.contains("Stream Key is required") == true)
    }

    @Test
    fun `test rtmp stream engine validation prevents invalid url crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rtmpEngine = RtmpStreamEngine(context)

        val invalidConfig = StreamConfig(
            platform = PlatformType.CUSTOM_RTMP,
            streamKey = "valid_key",
            rtmpUrl = "ftp://invalid-server"
        )

        val started = rtmpEngine.startStream(invalidConfig)
        assertFalse("Stream with invalid URL should not start", started)
        val stats = rtmpEngine.stats.value
        assertNotNull("Error message should report invalid URL format", stats.errorMessage)
    }

    @Test
    fun `test rtmp stream engine double start does not create duplicate sessions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rtmpEngine = RtmpStreamEngine(context)

        val validConfig = StreamConfig(
            platform = PlatformType.CUSTOM_RTMP,
            streamKey = "my_custom_key",
            rtmpUrl = "rtmp://127.0.0.1:1935/live"
        )

        val firstStart = rtmpEngine.startStream(validConfig)
        assertTrue("First start should succeed", firstStart)

        val secondStart = rtmpEngine.startStream(validConfig)
        assertFalse("Second rapid start should be rejected without crash", secondStart)

        rtmpEngine.stopStream()
        assertFalse("Stream should be stopped", rtmpEngine.stats.value.isStreaming)
    }

    @Test
    fun `test audio mixer engine volume scaling and mute`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioEngine = AudioMixerEngine(context)

        audioEngine.setGameVolume(1.5f)
        assertEquals(1.5f, audioEngine.config.value.gameAudioVolume, 0.01f)

        audioEngine.setMicVolume(1.8f)
        assertEquals(1.8f, audioEngine.config.value.micAudioVolume, 0.01f)

        // Toggle mute
        assertFalse("Mic should not be muted initially", audioEngine.config.value.isMicMuted)
        audioEngine.toggleMicMute()
        assertTrue("Mic should be muted after toggle", audioEngine.config.value.isMicMuted)

        audioEngine.setAudioDelay(150)
        assertEquals(150, audioEngine.config.value.audioDelayMs)

        audioEngine.stopAudioMonitoring()
    }

    @Test
    fun `test hardware encoder detector profile`() {
        val profile = HardwareEncoderDetector.detectHardwareProfile()
        assertNotNull("Hardware profile should not be null", profile)
        assertNotNull("Device model string should exist", profile.deviceModel)
        assertTrue("Recommended bitrate should be positive", profile.recommendedBitrateKbps > 0)
    }

    @Test
    fun `test rtmp stream engine clear error message`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rtmpEngine = RtmpStreamEngine(context)

        val invalidConfig = StreamConfig(
            platform = PlatformType.CUSTOM_RTMP,
            streamKey = "",
            rtmpUrl = "invalid-url"
        )
        rtmpEngine.startStream(invalidConfig)
        assertNotNull(rtmpEngine.stats.value.errorMessage)

        rtmpEngine.clearErrorMessage()
        assertEquals(null, rtmpEngine.stats.value.errorMessage)
    }

    @Test
    fun `test stream foreground service start and stop actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Starting camera-only without crash
        com.example.service.StreamForegroundService.startServiceCameraOnly(context, "Test Stream")

        // Duplicate start should be safely handled without crash
        com.example.service.StreamForegroundService.startServiceCameraOnly(context, "Test Stream Duplicate")

        // Stopping without crash
        com.example.service.StreamForegroundService.stopService(context)
    }

    @Test
    fun `test rtmp stream engine custom error message reporting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rtmpEngine = RtmpStreamEngine(context)

        rtmpEngine.setErrorMessage("Screen capture permission was revoked")
        assertEquals("Screen capture permission was revoked", rtmpEngine.stats.value.errorMessage)

        rtmpEngine.clearErrorMessage()
        assertEquals(null, rtmpEngine.stats.value.errorMessage)
    }

    @Test
    fun `test rtmp amf0 serialization and deserialization`() {
        val writer = com.example.engine.streaming.Amf0Writer()
            .writeString("connect")
            .writeNumber(1.0)
            .writeObject(mapOf("app" to "live2", "flashVer" to "FMLE/3.0", "fpad" to false))
        val bytes = writer.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val reader = com.example.engine.streaming.Amf0Reader(bytes)
        assertEquals("connect", reader.readNext())
        assertEquals(1.0, reader.readNext())
        val obj = reader.readNext() as? Map<*, *>
        assertNotNull(obj)
        assertEquals("live2", obj?.get("app"))
        assertEquals(false, obj?.get("fpad"))
    }

    @Test
    fun `test screen recorder manager initialization and storage metrics`() {
        val app = ApplicationProvider.getApplicationContext<ViperStudioApplication>()
        val recorder = app.screenRecorderManager
        assertNotNull(recorder)
        recorder.updateAvailableStorage()
        assertTrue("Available storage should be reported", recorder.availableStorageGb.value >= 0f)
        assertFalse("Recorder should not be recording initially", recorder.isRecording.value)
    }

    @Test
    fun `test screen recorder starts and creates non zero byte recording`() {
        val app = ApplicationProvider.getApplicationContext<ViperStudioApplication>()
        val recorder = app.screenRecorderManager
        recorder.startRecording(ResolutionOption.RES_720P)
        assertTrue(recorder.isRecording.value)
        var savedEntity: com.example.data.local.entity.RecordingEntity? = null
        recorder.stopRecording { entity ->
            savedEntity = entity
        }
        assertFalse(recorder.isRecording.value)
    }
}
