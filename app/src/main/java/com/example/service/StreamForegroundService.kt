package com.example.service

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.ViperStudioApplication
import com.example.engine.recording.ScreenRecorderManager
import com.example.data.model.NetworkHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StreamForegroundService : Service() {

    companion object {
        private const val TAG = "StreamForegroundService"
        const val CHANNEL_ID = "viper_studio_stream_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_STREAM = "ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "ACTION_STOP_STREAM"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val ACTION_UPDATE_STATUS = "ACTION_UPDATE_STATUS"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_STREAM_TITLE = "EXTRA_STREAM_TITLE"

        @Volatile
        var currentMediaProjection: MediaProjection? = null
            private set

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun stopRecording(context: Context) {
            val intent = Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send stop recording to StreamForegroundService", e)
            }
        }

        fun startServiceWithProjection(
            context: Context,
            resultCode: Int,
            resultData: Intent?,
            streamTitle: String = "BGMI Live Stream"
        ) {
            val intent = Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_START_STREAM
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_STREAM_TITLE, streamTitle)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start StreamForegroundService with projection", e)
            }
        }

        fun startServiceCameraOnly(context: Context, streamTitle: String = "Studio Camera Stream") {
            val intent = Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_START_STREAM
                putExtra(EXTRA_STREAM_TITLE, streamTitle)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start StreamForegroundService (Camera Only)", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, StreamForegroundService::class.java).apply {
                action = ACTION_STOP_STREAM
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send stop to StreamForegroundService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statsObserverJob: Job? = null
    private var currentStreamTitle: String = "Live Broadcast"
    private var isProjectionConsentActive: Boolean = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection.Callback.onStop() invoked by Android OS or user.")
            currentMediaProjection = null
            isProjectionConsentActive = false
            val app = applicationContext as? ViperStudioApplication
            app?.rtmpStreamEngine?.stopStream()
            app?.screenRecorderManager?.stopRecording()
            stopServiceInternal()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Android restarted service with null intent; cannot recreate projection without token
            if (!isServiceRunning || currentMediaProjection == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_STOP_RECORDING -> {
                Log.i(TAG, "ACTION_STOP_RECORDING received in StreamForegroundService.")
                val app = applicationContext as? ViperStudioApplication
                app?.screenRecorderManager?.stopRecording()
                return START_NOT_STICKY
            }
            ACTION_STOP_STREAM -> {
                Log.i(TAG, "ACTION_STOP_STREAM received. Cleaning up.")
                stopServiceInternal()
                return START_NOT_STICKY
            }
            ACTION_START_STREAM -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unparcel resultData Intent", e)
                    null
                }

                currentStreamTitle = intent.getStringExtra(EXTRA_STREAM_TITLE) ?: "Live Broadcast"

                val hasNewProjectionConsent = resultCode == Activity.RESULT_OK && resultData != null
                if (hasNewProjectionConsent) {
                    isProjectionConsentActive = true
                }

                // Android 8.0+ contract: ALWAYS call startForeground() on onStartCommand when started via startForegroundService
                val hasActiveProjection = isProjectionConsentActive || (currentMediaProjection != null)
                startForegroundSafely(hasActiveProjection, currentStreamTitle)

                // Initialize new MediaProjection if new consent was provided
                if (hasNewProjectionConsent && resultData != null) {
                    initializeMediaProjection(resultCode, resultData)
                }

                isServiceRunning = true
                observeStreamStats()
            }
        }
        return START_NOT_STICKY
    }

    private fun initializeMediaProjection(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager != null) {
                releaseMediaProjection()
                val projection = projectionManager.getMediaProjection(resultCode, data)
                if (projection != null) {
                    // Register required callback on main looper
                    projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
                    currentMediaProjection = projection
                    Log.i(TAG, "MediaProjection successfully acquired and callback registered.")
                    val app = applicationContext as? ViperStudioApplication
                    app?.screenRecorderManager?.onMediaProjectionReady(projection)
                } else {
                    Log.w(TAG, "MediaProjectionManager.getMediaProjection returned null.")
                    notifyStreamError("Screen capture permission was not recognized by Android.")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException acquiring MediaProjection: ${e.message}", e)
            notifyStreamError("Screen capture security permission error: ${e.localizedMessage}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException acquiring MediaProjection: ${e.message}", e)
            notifyStreamError("Screen capture state error: ${e.localizedMessage}")
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining or registering MediaProjection", e)
            notifyStreamError("Screen capture error: ${e.localizedMessage}")
        }
    }

    private fun releaseMediaProjection() {
        val proj = currentMediaProjection
        currentMediaProjection = null
        if (proj != null) {
            try {
                proj.unregisterCallback(projectionCallback)
                proj.stop()
                Log.i(TAG, "MediaProjection successfully unregistered and stopped.")
            } catch (e: Exception) {
                Log.w(TAG, "Exception during MediaProjection release", e)
            }
        }
    }

    private fun startForegroundSafely(hasMediaProjection: Boolean, streamTitle: String) {
        val notification = buildInitialNotification(hasMediaProjection, streamTitle, "Broadcasting mobile gameplay & audio...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasMicPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMediaProjection) {
                // Must specify FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION on Android 14+ (API 34+)
                val combinedType = if (hasMicPerm) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }

                try {
                    startForeground(NOTIFICATION_ID, notification, combinedType)
                    Log.i(TAG, "startForeground initialized with combined bitmask: $combinedType")
                    return
                } catch (e: SecurityException) {
                    Log.w(TAG, "startForeground with combinedType $combinedType threw SecurityException, falling back to MEDIA_PROJECTION alone", e)
                } catch (e: Exception) {
                    Log.w(TAG, "startForeground with combinedType failed: ${e.message}", e)
                }

                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    Log.i(TAG, "startForeground initialized with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION failed", e)
                }
            } else {
                // Non-mediaProjection mode (mic audio or camera)
                if (hasMicPerm) {
                    try {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                        Log.i(TAG, "startForeground initialized with FOREGROUND_SERVICE_TYPE_MICROPHONE")
                        return
                    } catch (e: Exception) {
                        Log.w(TAG, "startForeground with FOREGROUND_SERVICE_TYPE_MICROPHONE failed", e)
                    }
                }
            }
        }

        // Fallback for pre-Android 10 or basic notification
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.i(TAG, "startForeground initialized with basic notification")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback startForeground failed", e)
        }
    }

    private fun buildInitialNotification(
        hasMediaProjection: Boolean,
        streamTitle: String,
        statusText: String
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val app = applicationContext as? ViperStudioApplication
        val isRecordingActive = app?.screenRecorderManager?.isRecording?.value == true ||
            app?.screenRecorderManager?.recordingState?.value == com.example.engine.recording.ScreenRecorderManager.RecordingState.RECORDING ||
            app?.screenRecorderManager?.recordingState?.value == com.example.engine.recording.ScreenRecorderManager.RecordingState.STARTING

        val isStreamingActive = app?.rtmpStreamEngine?.stats?.value?.isStreaming == true ||
            app?.rtmpStreamEngine?.stats?.value?.isConnecting == true

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isRecordingActive && !isStreamingActive) {
            builder.setContentTitle("VIPER STUDIO • Screen Recording")
            builder.setContentText(statusText.ifBlank { "Recording MP4 to Movies/VIPER STUDIO..." })

            val stopRecIntent = Intent(this, StreamForegroundService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            val stopRecPending = PendingIntent.getService(
                this,
                2,
                stopRecIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_media_pause, "STOP RECORDING", stopRecPending)
        } else {
            builder.setContentTitle("VIPER STUDIO • $streamTitle")
            builder.setContentText(statusText)

            val stopIntent = Intent(this, StreamForegroundService::class.java).apply {
                action = ACTION_STOP_STREAM
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "END BROADCAST", stopPendingIntent)

            if (isRecordingActive) {
                val stopRecIntent = Intent(this, StreamForegroundService::class.java).apply {
                    action = ACTION_STOP_RECORDING
                }
                val stopRecPending = PendingIntent.getService(
                    this,
                    2,
                    stopRecIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_menu_save, "STOP RECORDING", stopRecPending)
            }
        }

        return builder.build()
    }

    private fun observeStreamStats() {
        statsObserverJob?.cancel()
        statsObserverJob = serviceScope.launch {
            val app = applicationContext as? ViperStudioApplication ?: return@launch
            var wasActiveSession = false

            app.rtmpStreamEngine.stats.collect { stats ->
                if (!isServiceRunning) return@collect

                val isCurrentlyActive = stats.isStreaming || stats.isConnecting || (app.screenRecorderManager.isRecording.value == true)
                if (isCurrentlyActive) {
                    wasActiveSession = true
                }

                val statusText = when {
                    stats.isConnecting -> "Connecting to RTMP ingest..."
                    stats.isStreaming -> {
                        val hours = stats.uptimeSeconds / 3600
                        val mins = (stats.uptimeSeconds % 3600) / 60
                        val secs = stats.uptimeSeconds % 60
                        val timeStr = if (hours > 0) "%02d:%02d:%02d".format(hours, mins, secs) else "%02d:%02d".format(mins, secs)
                        "LIVE $timeStr • ${stats.currentBitrateKbps} Kbps • ${stats.currentFps} FPS"
                    }
                    stats.networkHealth == NetworkHealth.RECONNECTING -> "Reconnecting to RTMP..."
                    stats.errorMessage != null -> "Stream Warning: ${stats.errorMessage}"
                    else -> "VIPER STUDIO Ready"
                }

                updateNotificationContent(
                    buildInitialNotification(isProjectionConsentActive, currentStreamTitle, statusText)
                )

                val isRecActive = (app.screenRecorderManager.isRecording.value) ||
                    (app.screenRecorderManager.recordingState.value != ScreenRecorderManager.RecordingState.IDLE &&
                     app.screenRecorderManager.recordingState.value != ScreenRecorderManager.RecordingState.SAVED &&
                     app.screenRecorderManager.recordingState.value != ScreenRecorderManager.RecordingState.ERROR)

                // Only auto-stop if a session WAS ACTUALLY ACTIVE and has now stopped completely
                if (wasActiveSession && !stats.isStreaming && !stats.isConnecting && !isRecActive) {
                    Log.i(TAG, "Active broadcast session ended. Shutting down foreground service after grace period.")
                    delay(1500)
                    // Re-check after grace period before stopping
                    val stillRecActive = (app.screenRecorderManager.isRecording.value) ||
                        (app.screenRecorderManager.recordingState.value != ScreenRecorderManager.RecordingState.IDLE &&
                         app.screenRecorderManager.recordingState.value != ScreenRecorderManager.RecordingState.SAVED &&
                         app.screenRecorderManager.recordingState.value != ScreenRecorderManager.RecordingState.ERROR)

                    if (!app.rtmpStreamEngine.stats.value.isStreaming &&
                        !app.rtmpStreamEngine.stats.value.isConnecting &&
                        !stillRecActive) {
                        stopServiceInternal()
                    }
                }
            }
        }
    }

    private fun updateNotificationContent(notification: Notification) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun notifyStreamError(message: String) {
        try {
            val app = applicationContext as? ViperStudioApplication
            app?.rtmpStreamEngine?.setErrorMessage(message)
        } catch (_: Exception) {}
    }

    private fun stopServiceInternal() {
        isServiceRunning = false
        isProjectionConsentActive = false
        statsObserverJob?.cancel()
        statsObserverJob = null

        try {
            com.example.engine.scene.StreamOverlayManager.getInstance(applicationContext).hideOverlay()
        } catch (_: Exception) {}

        releaseMediaProjection()

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VIPER STUDIO Broadcast Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps VIPER STUDIO live stream and screen capture active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopServiceInternal()
    }
}
