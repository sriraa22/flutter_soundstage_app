package com.example.soundstage_app

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
import androidx.core.content.ContextCompat

// Sends the microphone of a connected Bluetooth headset (AirPods, earbuds, headsets, car kits) to the
// app, so the speech recognizer hears the headset instead of the phone's own microphone.
// A headset's microphone travels over the hands-free link (HFP/SCO, or LE Audio on newer phones),
// which Android only switches on while the phone is in "communication" mode.
// Whichever headset is connected is used; when it disconnects, the phone's microphone takes over,
// and when it comes back it is picked up again. Must be used from the main thread.
class BluetoothAudioRouter(
    private val context: Context,
    private val onMicChanged: (AudioDeviceInfo?) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    // The headset microphone in use, or null when the phone's microphone is used
    var activeMic: AudioDeviceInfo? = null
        private set

    // Turned off by the user to use the phone's microphone even while earphones are connected
    var enabled = true
        set(value) {
            if (field == value) return
            field = value
            if (value) refresh() else release(notify = true)
        }

    private var savedMode: Int? = null
    private var started = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    // Before Android 12 the headset link is started with startBluetoothSco(), which reports back by broadcast
    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED && activeMic != null) {
                onMicChanged(activeMic)
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ContextCompat.registerReceiver(
                context,
                scoReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
        refresh()
    }

    // Gives the microphone and the phone's audio mode back to normal (e.g. when the app goes to the background)
    fun stop() {
        if (!started) return
        started = false
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            context.unregisterReceiver(scoReceiver)
        }
        release(notify = false)
    }

    // True when the device with this Bluetooth address is connected as a headset with a microphone
    fun isHeadsetConnected(address: String) =
        headsetMics().any { it.address.equals(address, ignoreCase = true) }

    // True when any earphones/headset with a microphone are connected
    fun hasHeadset() = headsetMics().isNotEmpty()

    private fun headsetMics(): List<AudioDeviceInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        }

    private fun refresh() {
        if (!started) return
        val mic = if (enabled) headsetMics().firstOrNull() else null
        val current = activeMic
        when {
            mic == null && current != null -> release(notify = true)
            mic != null && (current == null || !current.address.equals(mic.address, ignoreCase = true)) -> use(mic)
        }
    }

    private fun use(mic: AudioDeviceInfo) {
        if (savedMode == null) savedMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!audioManager.setCommunicationDevice(mic)) {
                release(notify = activeMic != null)
                return
            }
            activeMic = mic
            onMicChanged(mic)
        } else {
            if (!audioManager.isBluetoothScoAvailableOffCall) {
                release(notify = false)
                return
            }
            activeMic = mic
            @Suppress("DEPRECATION")
            audioManager.startBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = true
            // onMicChanged is called by scoReceiver once the headset link is up
        }
    }

    private fun release(notify: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else if (activeMic != null) {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
        }
        savedMode?.let { audioManager.mode = it }
        savedMode = null

        val hadMic = activeMic != null
        activeMic = null
        if (notify && hadMic) onMicChanged(null)
    }
}
