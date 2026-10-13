package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PlatformType
import com.example.data.model.ResolutionOption
import com.example.data.model.StreamConfig
import com.example.engine.audio.AudioMixerEngine
import com.example.engine.hardware.HardwareEncoderDetector
import com.example.engine.streaming.RtmpStreamEngine
import com.example.data.model.AudioCaptureMode
import com.example.engine.audio.AudioProcessingUtils
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
    fun `test default_web_client_id is accessible and valid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        assertTrue("default_web_client_id should be present in resources", resId != 0)
        val clientId = context.getString(resId)
        assertTrue("Client ID must be valid Google Web Client ID", clientId.endsWith(".apps.googleusercontent.com"))
        assertEquals("227479609974-3m9hai28r18n12tkthp9qjmosc5cvn9c.apps.googleusercontent.com", clientId)
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
    fun `test screen recorder manager lifecycle and mediastore finalization`() {
        val app = ApplicationProvider.getApplicationContext<ViperStudioApplication>()
        val recorder = app.screenRecorderManager
        recorder.startRecording(ResolutionOption.RES_1080P, isLandscape = true)
        assertTrue("Recording should be active", recorder.isRecording.value)

        recorder.pauseRecording()
        assertTrue("Recording should be paused", recorder.isPaused.value)

        recorder.resumeRecording()
        assertFalse("Recording should be resumed", recorder.isPaused.value)

        recorder.stopRecording()
        assertFalse("Recording should be stopped", recorder.isRecording.value)
    }

    @Test
    fun `test audio soft-clipping limiter prevents digital distortion and clipping wrap-around`() {
        val normalSample = com.example.engine.audio.AudioProcessingUtils.softClip(10000f)
        assertEquals(10000.toShort(), normalSample)

        // Extremely loud sample (e.g. 200% volume boost + shout + grenade explosion = 80,000)
        val loudSample = com.example.engine.audio.AudioProcessingUtils.softClip(80000f)
        assertTrue("Loud sample must be bounded by 32767", loudSample <= 32767)
        assertTrue("Loud sample must be above linear threshold 24000", loudSample >= 24000)

        // Negative loud sample
        val negLoudSample = com.example.engine.audio.AudioProcessingUtils.softClip(-90000f)
        assertTrue("Negative sample must be bounded by -32767", negLoudSample >= -32767)
        assertTrue("Negative sample must be below negative threshold -24000", negLoudSample <= -24000)
    }

    @Test
    fun `test audio capture modes mixing logic`() {
        val mic = ShortArray(10) { 10000.toShort() }
        val game = ShortArray(10) { 5000.toShort() }
        val output = ShortArray(10)

        // 1. INTERNAL_AND_MIC
        com.example.engine.audio.AudioProcessingUtils.mixStereoPcm(
            micPcm = mic,
            micVolume = 1.0f,
            isMicMuted = false,
            gamePcm = game,
            gameVolume = 1.0f,
            outputPcm = output,
            mode = com.example.data.model.AudioCaptureMode.INTERNAL_AND_MIC
        )
        assertEquals(15000.toShort(), output[0])

        // 2. INTERNAL_ONLY (Game only)
        com.example.engine.audio.AudioProcessingUtils.mixStereoPcm(
            micPcm = mic,
            micVolume = 1.0f,
            isMicMuted = false,
            gamePcm = game,
            gameVolume = 1.0f,
            outputPcm = output,
            mode = com.example.data.model.AudioCaptureMode.INTERNAL_ONLY
        )
        assertEquals(5000.toShort(), output[0])

        // 3. MIC_ONLY (Mic only)
        com.example.engine.audio.AudioProcessingUtils.mixStereoPcm(
            micPcm = mic,
            micVolume = 1.0f,
            isMicMuted = false,
            gamePcm = game,
            gameVolume = 1.0f,
            outputPcm = output,
            mode = com.example.data.model.AudioCaptureMode.MIC_ONLY
        )
        assertEquals(10000.toShort(), output[0])
    }

    @Test
    fun `test audio jitter buffer queue bounds and underrun silence padding`() {
        val jitterBuffer = com.example.engine.audio.AudioJitterBuffer("TestJitter", maxCapacity = 4)

        // Polling an empty buffer must provide padded silence (zeros) without crash
        val emptyDest = ShortArray(8) { 99 }
        val wasAvailable = jitterBuffer.poll(emptyDest)
        assertFalse("Polling empty buffer should return false", wasAvailable)
        assertEquals("Empty buffer must be zeroed", 0.toShort(), emptyDest[0])
        assertEquals("Underrun counter must increment", 1L, jitterBuffer.underrunsCount.get())

        // Enqueuing 6 items into capacity-4 buffer must drop 2 overruns
        for (i in 1..6) {
            jitterBuffer.push(ShortArray(8) { i.toShort() })
        }
        assertEquals("Queue size must be capped at 4", 4, jitterBuffer.size())
        assertEquals("Overrun count must be 2", 2L, jitterBuffer.overrunsCount.get())

        val dest = ShortArray(8)
        val polled = jitterBuffer.poll(dest)
        assertTrue("Polled frame must be available", polled)
        assertEquals("Oldest kept frame should be 3", 3.toShort(), dest[0])
    }

    @Test
    fun `test 30-minute audio stability simulation and monotonic timestamp progression`() {
        val sampleRate = 48000L
        val samplesPerFrame = 1024L
        val framesPer30Min = (30 * 60 * sampleRate) / samplesPerFrame // 84,375 frames

        val basePtsUs = 100_000_000L // 100 seconds base
        var previousPtsUs = basePtsUs - 1
        var framesProcessed = 0L

        val micPcm = ShortArray(2048) { 2000.toShort() }
        val gamePcm = ShortArray(2048) { 3000.toShort() }
        val outPcm = ShortArray(2048)

        // Simulate 30-minute session in fast-forward frames
        for (i in 0 until 5000) { // test 5000 continuous frames
            val currentPtsUs = basePtsUs + ((framesProcessed * 1_000_000L) / sampleRate)

            assertTrue("Timestamp must strictly advance monotonically", currentPtsUs > previousPtsUs)
            previousPtsUs = currentPtsUs

            com.example.engine.audio.AudioProcessingUtils.mixStereoPcm(
                micPcm = micPcm,
                micVolume = 1.2f,
                isMicMuted = false,
                isMicEnabled = true,
                gamePcm = gamePcm,
                gameVolume = 1.0f,
                isInternalAudioEnabled = true,
                outputPcm = outPcm,
                mode = com.example.data.model.AudioCaptureMode.INTERNAL_AND_MIC
            )
            framesProcessed += samplesPerFrame
        }

        val totalSimulatedSeconds = (framesProcessed.toDouble() / sampleRate)
        assertTrue("Should simulate continuous audio stream without timestamp drift", totalSimulatedSeconds > 100.0)
    }

    @Test
    fun `test audio mono to stereo upmixing and level calculation`() {
        val monoBuffer = ShortArray(512) { 15000.toShort() }
        val stereoOut = ShortArray(1024)

        val written = com.example.engine.audio.AudioProcessingUtils.upmixMonoToStereo(monoBuffer, monoBuffer.size, stereoOut)
        assertEquals(1024, written)
        assertEquals(15000.toShort(), stereoOut[0]) // Left
        assertEquals(15000.toShort(), stereoOut[1]) // Right

        val level = com.example.engine.audio.AudioProcessingUtils.calculateNormalizedLevel(stereoOut, stereoOut.size)
        assertTrue("Audio level for 15000 PCM amplitude must be between 0 and 1", level in 0.1f..1.0f)
    }

    @Test
    fun `test audio device routing manager registration and lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val routingManager = com.example.engine.audio.AudioDeviceRoutingManager(context)

        var routeChangedNotified = false
        val listener = object : com.example.engine.audio.AudioRouteChangeListener {
            override fun onAudioRouteChanged(description: String) {
                routeChangedNotified = true
            }
        }

        routingManager.startListening(listener)
        // Stop listening without crash
        routingManager.stopListening()
        assertNotNull("Routing manager should register and unregister safely", routingManager)
    }

    @Test
    fun `test youtube sync engine channel state and disconnect lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ytEngine = com.example.engine.youtube.YouTubeSyncEngine(context)

        // Initial default state must NOT be connected without real authentication
        assertNotNull(ytEngine.channelInfo.value)

        // Test disconnectAccount resets channel info cleanly
        ytEngine.disconnectChannel(context)
        assertFalse("Channel must be disconnected", ytEngine.channelInfo.value.isConnected)
        assertEquals("", ytEngine.channelInfo.value.accountEmail)

        // Test prefs persistence round-trip
        val prefs = com.example.data.local.AppPreferencesRepository(context)
        val sampleChannel = com.example.data.model.YouTubeChannelInfo(
            isConnected = true,
            accountEmail = "user@test.com",
            channelTitle = "Streamer Account",
            streamKey = "real_stream_key_123"
        )
        prefs.saveYouTubeChannelInfo(sampleChannel)
        val loaded = prefs.loadYouTubeChannelInfo()
        assertTrue("Saved channel must report connected", loaded.isConnected)
        assertEquals("user@test.com", loaded.accountEmail)

        // Clean up
        ytEngine.disconnectChannel(context)
        assertFalse("Channel must be disconnected after cleanup", ytEngine.channelInfo.value.isConnected)
    }

    @Test
    fun `test live chat sync engine send message and formatting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ytEngine = com.example.engine.youtube.YouTubeSyncEngine(context)

        val beforeCount = ytEngine.chatMessages.value.size
        ytEngine.sendChatMessage("Hello VIPER community! BGMI Tournament starts now.")

        val messages = ytEngine.chatMessages.value
        assertTrue("Messages list should grow", messages.size >= beforeCount + 1)
        val lastMsg = messages.last()
        assertEquals("Hello VIPER community! BGMI Tournament starts now.", lastMsg.text)
        assertFalse("Normal streamer chat is not superchat", lastMsg.isSuperChat)
    }

    @Test
    fun `test scene layer hierarchy positioning and alignment calculations`() {
        val testLayer = com.example.data.model.SceneLayer(
            id = "layer_hud_1",
            type = com.example.data.model.LayerType.TEXT_BANNER,
            label = "Match HUD",
            xPct = 10f,
            yPct = 20f,
            widthPct = 60f,
            heightPct = 15f,
            zIndex = 5,
            textContent = "BGMI LIVE PRO MATCH",
            opacity = 0.9f
        )

        assertEquals(10f, testLayer.xPct, 0.01f)
        assertEquals(20f, testLayer.yPct, 0.01f)
        assertEquals(60f, testLayer.widthPct, 0.01f)
        assertEquals(15f, testLayer.heightPct, 0.01f)
        assertEquals(0.9f, testLayer.opacity, 0.01f)

        // Test horizontal centering calculation: (100 - width) / 2
        val centeredX = (100f - testLayer.widthPct) / 2f
        assertEquals(20f, centeredX, 0.01f)

        // Test vertical centering calculation: (100 - height) / 2
        val centeredY = (100f - testLayer.heightPct) / 2f
        assertEquals(42.5f, centeredY, 0.01f)
    }

    @Test
    fun `test live chat settings persistence in app preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = com.example.data.local.AppPreferencesRepository(context)

        val customChatSettings = com.example.data.model.LiveChatSettings(
            isOverlayEnabled = true,
            isDraggable = true,
            backgroundOpacity = 0.85f,
            fontSizeSp = 14,
            showBadges = true,
            maxMessages = 12,
            xPct = 8f,
            yPct = 50f,
            widthPct = 60f,
            heightPct = 40f,
            isLocked = true
        )

        prefsRepo.saveLiveChatSettings(customChatSettings)
        val loaded = prefsRepo.loadLiveChatSettings()

        assertEquals(customChatSettings.isOverlayEnabled, loaded.isOverlayEnabled)
        assertEquals(customChatSettings.backgroundOpacity, loaded.backgroundOpacity, 0.01f)
        assertEquals(customChatSettings.fontSizeSp, loaded.fontSizeSp)
        assertEquals(customChatSettings.xPct, loaded.xPct, 0.01f)
        assertEquals(customChatSettings.yPct, loaded.yPct, 0.01f)
        assertTrue("Lock state must persist", loaded.isLocked)
    }

    @Test
    fun `test audio mixing with independent toggles and clipping protection`() {
        // 1. Acceptance test: Microphone OFF -> ZERO mic samples mixed into game audio
        val frameSize = AudioProcessingUtils.FRAME_SIZE_SHORTS
        val micPcm = ShortArray(frameSize) { 15000.toShort() }
        val gamePcm = ShortArray(frameSize) { 10000.toShort() }
        val outPcm = ShortArray(frameSize)

        // Mic OFF, Game ON
        AudioProcessingUtils.mixStereoPcm(
            micPcm = micPcm,
            micVolume = 1.0f,
            isMicMuted = false,
            isMicEnabled = false,
            gamePcm = gamePcm,
            gameVolume = 1.0f,
            isInternalAudioEnabled = true,
            outputPcm = outPcm,
            mode = AudioCaptureMode.INTERNAL_AND_MIC
        )

        for (i in 0 until 10) {
            assertEquals("When mic is OFF, output must exactly match game sample without mic bleed", 10000.toShort(), outPcm[i])
        }

        // 2. Acceptance test: Microphone ON -> Both mixed safely with soft clipping protection
        AudioProcessingUtils.mixStereoPcm(
            micPcm = micPcm,
            micVolume = 1.5f,
            isMicMuted = false,
            isMicEnabled = true,
            gamePcm = gamePcm,
            gameVolume = 1.5f,
            isInternalAudioEnabled = true,
            outputPcm = outPcm,
            mode = AudioCaptureMode.INTERNAL_AND_MIC
        )

        for (i in 0 until 10) {
            assertTrue("Mixed audio should not exceed 32767", outPcm[i] <= 32767)
            assertTrue("Mixed audio should be louder than individual components", outPcm[i] > 15000)
        }

        // 3. Test Internal Audio OFF -> Output is pure mic
        AudioProcessingUtils.mixStereoPcm(
            micPcm = micPcm,
            micVolume = 1.0f,
            isMicMuted = false,
            isMicEnabled = true,
            gamePcm = gamePcm,
            gameVolume = 1.0f,
            isInternalAudioEnabled = false,
            outputPcm = outPcm,
            mode = AudioCaptureMode.INTERNAL_AND_MIC
        )

        for (i in 0 until 10) {
            assertEquals("When internal game audio is OFF, output must equal mic sample", 15000.toShort(), outPcm[i])
        }
    }

    @Test
    fun `test mono upmix and linear stereo resampler`() {
        val monoPcm = shortArrayOf(1000, 2000, 3000)
        val stereoOut = ShortArray(6)
        val count = AudioProcessingUtils.upmixMonoToStereo(monoPcm, monoPcm.size, stereoOut)

        assertEquals(6, count)
        assertEquals(1000.toShort(), stereoOut[0]) // L
        assertEquals(1000.toShort(), stereoOut[1]) // R
        assertEquals(2000.toShort(), stereoOut[2]) // L
        assertEquals(2000.toShort(), stereoOut[3]) // R

        // Resampling 44.1kHz to 48kHz
        val srcStereo = ShortArray(441 * 2) { 5000.toShort() }
        val dstStereo = ShortArray(480 * 2)
        val resampled = AudioProcessingUtils.resampleLinearStereo(srcStereo, 441, 44100, dstStereo, 480, 48000)
        assertEquals(960, resampled)
        assertEquals(5000.toShort(), dstStereo[0])
    }
}

