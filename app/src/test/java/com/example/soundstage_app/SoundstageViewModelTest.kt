package com.example.soundstage_app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundstageViewModelTest {

    private class FakeCaptions(private val allowed: Boolean = true) : CaptionControls {
        var starts = 0
        var stops = 0
        override fun start(onResult: (Boolean) -> Unit) {
            starts++
            onResult(allowed)
        }
        override fun stop() {
            stops++
        }
    }

    private fun device(address: String, rssi: Int? = -50, paired: Boolean = false) =
        DiscoveredDevice("Buds", address, rssi, DeviceKind.AUDIO, paired)

    // ---------------- Onboarding ----------------

    @Test
    fun `onboarding goes through three pages then finishes`() {
        val vm = SoundstageViewModel()
        assertEquals(0, vm.onboardingPage)
        vm.nextOnboardingPage()
        vm.nextOnboardingPage()
        assertEquals(2, vm.onboardingPage)
        assertFalse(vm.onboardingDone)
        vm.nextOnboardingPage()
        assertTrue(vm.onboardingDone)
    }

    @Test
    fun `found devices are added once and updated in place keeping the last signal`() {
        val vm = SoundstageViewModel()
        vm.onBluetoothEvent(BluetoothEvent.DeviceFound(device("A")))
        vm.onBluetoothEvent(BluetoothEvent.DeviceFound(device("B")))
        vm.onBluetoothEvent(BluetoothEvent.DeviceFound(device("A", rssi = null, paired = true)))

        assertEquals(listOf("A", "B"), vm.discoveredDevices.map { it.address })
        assertTrue(vm.discoveredDevices[0].paired)
        assertEquals(-50, vm.discoveredDevices[0].rssi)
    }

    @Test
    fun `bluetooth events update the status line`() {
        val vm = SoundstageViewModel()
        assertEquals("Ready to connect", vm.bluetoothStatus)

        vm.onBluetoothEvent(BluetoothEvent.ScanStarted)
        assertEquals("Scanning for devices...", vm.bluetoothStatus)
        vm.onBluetoothEvent(BluetoothEvent.ScanStopped)
        assertEquals("Scan stopped", vm.bluetoothStatus)
        vm.onBluetoothEvent(BluetoothEvent.BluetoothDisabled)
        assertEquals("Bluetooth is off. Turn it on and scan again.", vm.bluetoothStatus)
        vm.onBluetoothEvent(BluetoothEvent.Status("Pairing with Buds..."))
        assertEquals("Pairing with Buds...", vm.bluetoothStatus)
        vm.onBluetoothEvent(BluetoothEvent.MicChanged("Buds"))
        assertEquals("Using microphone: Buds", vm.bluetoothStatus)
        vm.onBluetoothEvent(BluetoothEvent.MicChanged(null))
        assertEquals("Using phone microphone", vm.bluetoothStatus)
    }

    // ---------------- People ----------------

    @Test
    fun `starts with two people included and two muted`() {
        val vm = SoundstageViewModel()
        assertEquals(listOf("Maria", "Leah", "James", "Sofia"), vm.people.map { it.name })
        assertEquals(listOf(true, true, false, false), vm.people.map { it.isIncluded })
        assertEquals(SoundstageViewModel.TAB_SOUNDSTAGE, vm.selectedTab)
    }

    @Test
    fun `toggling a person flips them between included and muted`() {
        val vm = SoundstageViewModel()
        vm.togglePerson(0)
        assertFalse(vm.people[0].isIncluded)
        vm.togglePerson(2)
        assertTrue(vm.people[2].isIncluded)
        vm.togglePerson(0)
        assertTrue(vm.people[0].isIncluded)
    }

    // ---------------- Live captions ----------------

    @Test
    fun `tapping the mic starts then stops captions`() {
        val vm = SoundstageViewModel()
        val captions = FakeCaptions()
        assertEquals("Tap the mic and start speaking", vm.transcript)

        vm.toggleListening(captions)
        assertTrue(vm.isListening)
        assertEquals("Getting ready...", vm.transcript)
        assertEquals(1, captions.starts)

        vm.onCaptionsListening()
        assertEquals("Listening...", vm.transcript)

        vm.toggleListening(captions)
        assertFalse(vm.isListening)
        assertEquals(1, captions.stops)
    }

    @Test
    fun `denied microphone shows a message`() {
        val vm = SoundstageViewModel()
        vm.toggleListening(FakeCaptions(allowed = false))
        assertFalse(vm.isListening)
        assertEquals("Allow microphone access to see live captions", vm.transcript)
    }

    @Test
    fun `keeps the last three sentences above the one being spoken`() {
        val vm = SoundstageViewModel()
        vm.toggleListening(FakeCaptions())

        vm.onCaption("one", isFinal = true)
        vm.onCaption("two", isFinal = true)
        vm.onCaption("three", isFinal = true)
        vm.onCaption("fo", isFinal = false)
        assertEquals("one\ntwo\nthree\nfo", vm.transcript)

        vm.onCaption("four", isFinal = true)
        assertEquals("two\nthree\nfour", vm.transcript)

        // Lines already shown keep "Listening..." from replacing them
        vm.onCaptionsListening()
        assertEquals("two\nthree\nfour", vm.transcript)
    }

    @Test
    fun `errors and the notification stop button end listening`() {
        val vm = SoundstageViewModel()
        vm.toggleListening(FakeCaptions())
        vm.onCaptionsError("No speech recognizer")
        assertFalse(vm.isListening)
        assertEquals("No speech recognizer", vm.transcript)

        vm.toggleListening(FakeCaptions())
        vm.onCaptionsStopped()
        assertFalse(vm.isListening)
    }
}
