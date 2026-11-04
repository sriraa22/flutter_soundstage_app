package com.example.soundstage_app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// Someone around the user. x / y place them on the soundstage map, as fractions of its size.
class Person(
    val name: String,
    isIncluded: Boolean,
    val x: Float,
    val y: Float
) {
    var isIncluded by mutableStateOf(isIncluded)
}

// Starting and stopping live captions (CaptionService), as seen from the screen
interface CaptionControls {
    // Asks for the microphone if needed; onResult(false) when it wasn't allowed
    fun start(onResult: (Boolean) -> Unit)
    fun stop()
}

// Everything the screens show. Platform work (Bluetooth, captions) is done by MainActivity,
// which reports back through onBluetoothEvent and the caption callbacks.
class SoundstageViewModel : ViewModel() {

    companion object {
        const val ONBOARDING_PAGES = 3
        // Finished sentences kept above the one being spoken
        const val MAX_CAPTION_LINES = 3
        const val TAB_SOUNDSTAGE = 0
        const val TAB_PEOPLE = 1
    }

    // ---------------- Onboarding ----------------

    var onboardingPage by mutableIntStateOf(0)
        private set
    var onboardingDone by mutableStateOf(false)
        private set

    val discoveredDevices = mutableStateListOf<DiscoveredDevice>()
    var bluetoothStatus by mutableStateOf("Ready to connect")
        private set

    fun nextOnboardingPage() {
        if (onboardingPage < ONBOARDING_PAGES - 1) onboardingPage++ else onboardingDone = true
    }

    fun onBluetoothEvent(event: BluetoothEvent) {
        when (event) {
            is BluetoothEvent.MicChanged -> bluetoothStatus =
                if (event.micName == null) "Using phone microphone" else "Using microphone: ${event.micName}"

            is BluetoothEvent.DeviceFound -> addOrUpdateDevice(event.device)

            BluetoothEvent.BluetoothDisabled -> bluetoothStatus = "Bluetooth is off. Turn it on and scan again."
            BluetoothEvent.ScanStarted -> bluetoothStatus = "Scanning for devices..."
            BluetoothEvent.ScanStopped -> bluetoothStatus = "Scan stopped"
            is BluetoothEvent.Status -> bluetoothStatus = event.message
            is BluetoothEvent.ServicesDiscovered -> Unit
        }
    }

    private fun addOrUpdateDevice(device: DiscoveredDevice) {
        val index = discoveredDevices.indexOfFirst { it.address == device.address }
        if (index == -1) {
            discoveredDevices.add(device)
        } else {
            // Name, signal or paired state can change while scanning/pairing
            discoveredDevices[index] = device.copy(rssi = device.rssi ?: discoveredDevices[index].rssi)
        }
    }

    // ---------------- People ----------------

    var selectedTab by mutableIntStateOf(TAB_SOUNDSTAGE)

    val people = listOf(
        Person(name = "Maria", isIncluded = true, x = 0.32f, y = 0.30f),
        Person(name = "Leah", isIncluded = true, x = 0.68f, y = 0.32f),
        Person(name = "James", isIncluded = false, x = 0.18f, y = 0.10f),
        Person(name = "Sofia", isIncluded = false, x = 0.82f, y = 0.12f),
    )

    fun togglePerson(index: Int) {
        people[index].isIncluded = !people[index].isIncluded
    }

    // ---------------- Live captions ----------------

    // True while the user has captions switched on (they keep running in the background)
    var isListening by mutableStateOf(false)
        private set
    var transcript by mutableStateOf("Tap the mic and start speaking")
        private set
    private val lines = mutableListOf<String>()

    fun toggleListening(captions: CaptionControls) {
        if (isListening) {
            isListening = false
            captions.stop()
            return
        }

        isListening = true
        lines.clear()
        transcript = "Getting ready..."

        captions.start { started ->
            if (!started) {
                isListening = false
                transcript = "Allow microphone access to see live captions"
            }
        }
    }

    fun onCaptionsListening() {
        if (lines.isEmpty()) transcript = "Listening..."
    }

    // Gets the sentence being spoken as it goes (isFinal false), then the finished sentence (isFinal true)
    fun onCaption(text: String, isFinal: Boolean) {
        if (isFinal) {
            lines.add(text)
            if (lines.size > MAX_CAPTION_LINES) lines.removeAt(0)
        }
        transcript = (if (isFinal) lines else lines + text).joinToString("\n")
    }

    fun onCaptionsError(message: String) {
        isListening = false
        transcript = message
    }

    // The user tapped Stop in the captions notification
    fun onCaptionsStopped() {
        isListening = false
    }
}
