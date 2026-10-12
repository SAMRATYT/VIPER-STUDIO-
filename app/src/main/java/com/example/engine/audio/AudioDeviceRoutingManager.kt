package com.example.engine.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

interface AudioRouteChangeListener {
    fun onAudioRouteChanged(description: String)
}

/**
 * Monitors wired headset, Bluetooth SCO/A2DP, and USB audio routing changes.
 * Prevents crashes, buffer starvation, or dead AudioRecord handles during hardware switches.
 */
class AudioDeviceRoutingManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioRoutingManager"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isRegistered = false
    private var listener: AudioRouteChangeListener? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val deviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val names = addedDevices?.mapNotNull { it.productName?.toString() }?.joinToString(", ") ?: "New Audio Device"
                Log.i(TAG, "Audio device connected: $names")
                notifyChange("Connected: $names")
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val names = removedDevices?.mapNotNull { it.productName?.toString() }?.joinToString(", ") ?: "Removed Audio Device"
                Log.i(TAG, "Audio device disconnected: $names")
                notifyChange("Disconnected: $names")
            }
        }
    } else null

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    val name = intent.getStringExtra("name") ?: "Headset"
                    val desc = if (state == 1) "Wired Headset Plugged: $name" else "Wired Headset Unplugged"
                    Log.i(TAG, "Broadcast received: $desc")
                    notifyChange(desc)
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.i(TAG, "Audio becoming noisy (headphones pulled)")
                    notifyChange("Audio becoming noisy (re-routing to speakers/mic)")
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val scoState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    val desc = if (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED) "Bluetooth SCO Connected" else "Bluetooth SCO Disconnected"
                    Log.i(TAG, "SCO state updated: $desc")
                    notifyChange(desc)
                }
            }
        }
    }

    fun startListening(listener: AudioRouteChangeListener) {
        if (isRegistered) return
        this.listener = listener

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
                audioManager?.registerAudioDeviceCallback(deviceCallback, mainHandler)
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            }
            context.registerReceiver(headsetReceiver, filter)
            isRegistered = true
            Log.i(TAG, "AudioDeviceRoutingManager registered successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Error registering audio routing receiver: ${e.message}")
        }
    }

    fun stopListening() {
        if (!isRegistered) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
                audioManager?.unregisterAudioDeviceCallback(deviceCallback)
            }
            context.unregisterReceiver(headsetReceiver)
            isRegistered = false
            listener = null
            Log.i(TAG, "AudioDeviceRoutingManager unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering audio routing: ${e.message}")
        }
    }

    private fun notifyChange(desc: String) {
        listener?.onAudioRouteChanged(desc)
    }
}
