package com.example.soundstage_app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import java.util.UUID

// How the app talks to a device:
// CLASSIC - serial (SPP/RFCOMM) link, e.g. HC-05/HC-06 modules (Bluetooth 2.x/3.0 and later)
// BLE     - Bluetooth Low Energy (GATT) link
// AUDIO   - headphones, earbuds, AirPods, headsets: they have no serial link; the phone connects
//           their audio itself and the app uses their microphone for the transcript
enum class DeviceKind(val label: String) {
    CLASSIC("Classic"),
    BLE("BLE"),
    AUDIO("Audio")
}

data class FoundDevice(
    val address: String,
    val name: String?,
    val kind: DeviceKind = DeviceKind.CLASSIC
)

// Services that mark a device as headphones/headset
private val AUDIO_SERVICE_UUIDS = setOf(
    "0000110B-0000-1000-8000-00805F9B34FB", // A2DP audio sink (music)
    "0000111E-0000-1000-8000-00805F9B34FB", // Hands-free (HFP, carries the microphone)
    "00001108-0000-1000-8000-00805F9B34FB", // Headset (HSP)
    "0000184E-0000-1000-8000-00805F9B34FB", // LE Audio stream control
    "00001850-0000-1000-8000-00805F9B34FB", // LE Audio published capabilities
    "0000FDF0-0000-1000-8000-00805F9B34FB"  // ASHA hearing aids
).map(UUID::fromString).toSet()

fun UUID.isAudioService() = this in AUDIO_SERVICE_UUIDS

@SuppressLint("MissingPermission")
fun BluetoothDevice.deviceKind(btClass: BluetoothClass? = bluetoothClass): DeviceKind {
    val services = uuids?.map { it.uuid }.orEmpty()
    return when {
        btClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceKind.AUDIO
        services.any { it.isAudioService() } -> DeviceKind.AUDIO
        type == BluetoothDevice.DEVICE_TYPE_LE -> DeviceKind.BLE
        else -> DeviceKind.CLASSIC
    }
}

// Turns Android's low-level socket errors into something the user can act on
fun friendlySocketError(raw: String?): String = when {
    raw == null -> "unknown error"
    raw.contains("timed out", ignoreCase = true) ->
        "the device did not answer in time. Make sure it is switched on, nearby and not connected to another phone."
    raw.contains("read failed", ignoreCase = true) ||
        raw.contains("socket might closed", ignoreCase = true) ->
        "the device refused a serial (SPP) connection. It may not support serial data, " +
            "or it is already connected to another phone."
    raw.contains("Service discovery failed", ignoreCase = true) ->
        "the device does not offer a serial (SPP) service."
    else -> raw
}
