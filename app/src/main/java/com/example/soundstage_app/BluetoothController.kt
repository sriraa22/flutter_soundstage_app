package com.example.soundstage_app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

// A device found while scanning, as shown in the onboarding list
data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int?,
    val kind: DeviceKind,
    val paired: Boolean
)

// What the Bluetooth side reports to the screen (always on the main thread)
sealed interface BluetoothEvent {
    // The headset microphone now in use, or null for the phone's microphone
    data class MicChanged(val micName: String?) : BluetoothEvent
    data class DeviceFound(val device: DiscoveredDevice) : BluetoothEvent
    data object BluetoothDisabled : BluetoothEvent
    data object ScanStarted : BluetoothEvent
    data object ScanStopped : BluetoothEvent
    // Pairing, connection and scan progress / errors, shown to the user as-is
    data class Status(val message: String) : BluetoothEvent
    data class ServicesDiscovered(val serviceUuids: List<String>) : BluetoothEvent
}

// Scanning, pairing and connecting Bluetooth devices (BLE, classic and headphones / hearing aids),
// and using a connected headset's microphone. Owned by MainActivity, which forwards permission results.
@SuppressLint("MissingPermission") // every Bluetooth call is guarded by hasBluetoothPermissions()
class BluetoothController(
    private val activity: Activity,
    private val onEvent: (BluetoothEvent) -> Unit
) {
    companion object {
        const val PERMISSION_REQUEST = 100
        private const val SCAN_DURATION_MS = 15_000L
        private const val AUDIO_CONNECT_TIMEOUT_MS = 30_000L
    }

    private val bluetoothAdapter: BluetoothAdapter =
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Devices seen during the scan, so pairing/connecting uses the same object
    // (BLE devices often use random addresses that getRemoteDevice() can't reach)
    private val knownDevices = mutableMapOf<String, BluetoothDevice>()
    private val knownKinds = mutableMapOf<String, DeviceKind>()

    private var scanning = false
    private var scanAfterPermission = false
    private val stopScanRunnable = Runnable { stopScan() }

    // Set by MainActivity so Bluetooth and caption permission dialogs don't overlap
    var permissionRequestInFlight = false

    // Headphones being connected, and how many of their profiles (headset, music) are still trying
    private var pendingAudioDevice: BluetoothDevice? = null
    private var pendingAudioAttempts = 0
    private val audioConnectTimeout = Runnable {
        pendingAudioDevice?.let { audioConnectFailed(it) }
    }

    // Uses the hearing aid / headset microphone while one is connected
    private val audioRouter = BluetoothAudioRouter(activity, ::onMicChanged)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advertisesAudio = result.scanRecord?.serviceUuids
                ?.any { it.uuid.isAudioService() } == true

            reportDevice(
                result.device,
                result.scanRecord?.deviceName,
                result.rssi,
                if (advertisesAudio) DeviceKind.AUDIO else null
            )
        }

        override fun onScanFailed(errorCode: Int) {
            send(BluetoothEvent.Status("Bluetooth scan failed: $errorCode"))
        }
    }

    // Classic discovery (headphones, earbuds, HC-05...) and pairing progress
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.bluetoothDevice() ?: return

            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    reportDevice(
                        device,
                        intent.getStringExtra(BluetoothDevice.EXTRA_NAME),
                        if (rssi == Short.MIN_VALUE) null else rssi.toInt(),
                        try {
                            device.deviceKind(intent.bluetoothClass())
                        } catch (e: SecurityException) {
                            null
                        }
                    )
                }

                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    if (scanning) {
                        reportDevice(device, intent.getStringExtra(BluetoothDevice.EXTRA_NAME), null)
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> onBondStateChanged(device, intent)

                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> onAudioConnectionChanged(device, intent)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                send(
                    BluetoothEvent.Status(
                        "Connection failed (error $status). Make sure the device is on, nearby and not connected to another phone."
                    )
                )
                gatt.close()
                if (bluetoothGatt == gatt) bluetoothGatt = null
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                send(BluetoothEvent.Status("Connected"))
                if (hasBluetoothPermissions()) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                send(BluetoothEvent.Status("Disconnected"))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                send(BluetoothEvent.ServicesDiscovered(gatt.services.map { it.uuid.toString() }))
            }
        }
    }

    fun start() {
        ContextCompat.registerReceiver(
            activity,
            bluetoothReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        audioRouter.start()
    }

    fun release() {
        stopScan()
        mainHandler.removeCallbacks(audioConnectTimeout)

        bluetoothGatt?.close()
        bluetoothGatt = null

        audioRouter.stop()
        activity.unregisterReceiver(bluetoothReceiver)
    }

    // Use the hearing aid / headset microphone when one is connected (true),
    // or always use the phone's microphone (false)
    fun setHeadsetMic(enabled: Boolean) {
        audioRouter.enabled = enabled
    }

    // GATT callbacks run on a binder thread; the screen must only be updated from the main thread
    private fun send(event: BluetoothEvent) {
        mainHandler.post { onEvent(event) }
    }

    private fun onMicChanged(mic: AudioDeviceInfo?) {
        send(BluetoothEvent.MicChanged(mic?.productName?.toString()))
    }

    // Ask Android for Bluetooth and microphone permissions
    fun requestPermissions() {
        if (hasBluetoothPermissions() || permissionRequestInFlight) return
        permissionRequestInFlight = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.RECORD_AUDIO
                ),
                PERMISSION_REQUEST
            )
        } else {
            // Before Android 12, scanning returns nothing without location permission
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.RECORD_AUDIO
                ),
                PERMISSION_REQUEST
            )
        }
    }

    // Called by MainActivity when the user answered the permission dialog
    fun onPermissionsResult() {
        permissionRequestInFlight = false

        if (scanAfterPermission) {
            scanAfterPermission = false

            if (hasBluetoothPermissions()) {
                startScan()
            } else {
                send(
                    BluetoothEvent.Status(
                        "Bluetooth permission was denied. Allow \"Nearby devices\" in the app settings to find devices."
                    )
                )
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        fun granted(permission: String) =
            activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Search for nearby devices (classic and BLE) for SCAN_DURATION_MS
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            // Scan as soon as the user answers the permission dialog
            scanAfterPermission = true
            requestPermissions()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            send(BluetoothEvent.BluetoothDisabled)
            return
        }

        if (scanning) return
        scanning = true

        // Paired devices are listed even when they aren't advertising right now
        bluetoothAdapter.bondedDevices.forEach { reportDevice(it, null, null) }

        // Classic discovery finds headphones/earbuds in pairing mode; the BLE scan finds LE devices
        bluetoothAdapter.startDiscovery()

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        bluetoothLeScanner?.startScan(
            null,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback
        )

        mainHandler.postDelayed(stopScanRunnable, SCAN_DURATION_MS)

        send(BluetoothEvent.ScanStarted)
    }

    fun stopScan() {
        mainHandler.removeCallbacks(stopScanRunnable)

        if (!scanning || !hasBluetoothPermissions()) return
        scanning = false

        bluetoothAdapter.cancelDiscovery()
        bluetoothLeScanner?.stopScan(scanCallback)

        send(BluetoothEvent.ScanStopped)
    }

    private fun reportDevice(
        device: BluetoothDevice,
        advertisedName: String?,
        rssi: Int?,
        kind: DeviceKind? = null
    ) {
        val name = try {
            advertisedName ?: device.name
        } catch (e: SecurityException) {
            advertisedName
        }

        // Nameless broadcasts (other phones, TVs, trackers) can't be recognised by the user.
        // If the name arrives later (scan response / name change) the device is reported then.
        if (name.isNullOrBlank()) return

        val address = device.address
        val deviceKind = kind ?: knownKinds[address] ?: try {
            device.deviceKind()
        } catch (e: SecurityException) {
            DeviceKind.BLE
        }

        knownDevices[address] = device
        knownKinds[address] = deviceKind

        send(
            BluetoothEvent.DeviceFound(
                DiscoveredDevice(
                    name = name,
                    address = address,
                    rssi = rssi,
                    kind = deviceKind,
                    paired = device.bondState == BluetoothDevice.BOND_BONDED
                )
            )
        )
    }

    private fun deviceFor(address: String): BluetoothDevice =
        knownDevices[address] ?: bluetoothAdapter.getRemoteDevice(address)

    private fun nameOf(device: BluetoothDevice): String =
        device.name ?: device.address

    // Ask Android to pair with a device
    fun pairDevice(address: String) {
        if (!hasBluetoothPermissions()) {
            requestPermissions()
            return
        }

        try {
            // Discovery running in the background makes pairing slow or fail
            stopScan()

            val device = deviceFor(address)

            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                send(BluetoothEvent.Status("Already paired with ${nameOf(device)}"))
            } else if (device.createBond()) {
                send(BluetoothEvent.Status("Pairing with ${nameOf(device)}..."))
            } else {
                send(
                    BluetoothEvent.Status(
                        "Could not start pairing with ${nameOf(device)}. Make sure it is in pairing mode."
                    )
                )
            }
        } catch (e: Exception) {
            send(BluetoothEvent.Status("Pairing failed: ${e.message}"))
        }
    }

    private fun onBondStateChanged(device: BluetoothDevice, intent: Intent) {
        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
        val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
        val name = nameOf(device)

        when {
            state == BluetoothDevice.BOND_BONDED -> {
                // Android connects the audio of newly paired headphones by itself;
                // MicChanged reports when their microphone is in use
                send(
                    BluetoothEvent.Status(
                        if (knownKinds[device.address] == DeviceKind.AUDIO) "Paired with $name. Connecting audio..."
                        else "Paired with $name"
                    )
                )
                reportDevice(device, null, null)
            }

            state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING ->
                send(BluetoothEvent.Status("Pairing with $name failed or was cancelled"))
        }
    }

    // One "Connect" action for every kind of device
    fun connectDevice(address: String) {
        if (!hasBluetoothPermissions()) {
            requestPermissions()
            return
        }

        try {
            stopScan()

            val device = deviceFor(address)
            val kind = knownKinds[address] ?: device.deviceKind()

            when {
                device.bondState != BluetoothDevice.BOND_BONDED && kind != DeviceKind.BLE ->
                    pairDevice(address)

                kind == DeviceKind.AUDIO -> connectAudio(device)

                kind == DeviceKind.BLE -> connectGatt(device)

                else -> send(BluetoothEvent.Status("Paired with ${nameOf(device)}"))
            }
        } catch (e: Exception) {
            send(BluetoothEvent.Status("Connection failed: ${e.message}"))
        }
    }

    private fun connectGatt(device: BluetoothDevice) {
        send(BluetoothEvent.Status("Connecting to ${nameOf(device)}..."))

        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(activity, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    // Headphones/earbuds connect through the system's audio profiles, not GATT: the headset profile
    // (carries the microphone) and the music profile, like Bluetooth settings does.
    // If Android won't let the app start either one, the user is sent to Bluetooth settings.
    // The result arrives later in onAudioConnectionChanged.
    private fun connectAudio(device: BluetoothDevice) {
        if (audioRouter.isHeadsetConnected(device.address)) {
            send(BluetoothEvent.Status("Connected to ${nameOf(device)}"))
            return
        }

        send(BluetoothEvent.Status("Connecting to ${nameOf(device)}..."))

        mainHandler.removeCallbacks(audioConnectTimeout)
        pendingAudioDevice = device
        pendingAudioAttempts = 0

        val profiles = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)
        var proxiesLeft = profiles.size

        // Called once per profile, after its connect attempt was made (or couldn't be)
        fun profileDone() {
            proxiesLeft--
            if (proxiesLeft > 0 || pendingAudioDevice != device) return

            if (pendingAudioAttempts == 0) {
                pendingAudioDevice = null
                openBluetoothSettings(device)
            } else {
                mainHandler.postDelayed(audioConnectTimeout, AUDIO_CONNECT_TIMEOUT_MS)
            }
        }

        profiles.forEach { profileId ->
            val requested = bluetoothAdapter.getProfileProxy(
                activity,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        if (pendingAudioDevice == device) {
                            if (proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                                audioConnected(device)
                            } else if (connectProfile(proxy, device)) {
                                pendingAudioAttempts++
                            }
                        }
                        bluetoothAdapter.closeProfileProxy(profile, proxy)
                        profileDone()
                    }

                    override fun onServiceDisconnected(profile: Int) {}
                },
                profileId
            )

            if (!requested) profileDone()
        }
    }

    // connect() is hidden from apps, so it is called by name; false when Android refuses it
    private fun connectProfile(proxy: BluetoothProfile, device: BluetoothDevice): Boolean =
        try {
            proxy.javaClass
                .getMethod("connect", BluetoothDevice::class.java)
                .invoke(proxy, device) as Boolean
        } catch (e: Exception) {
            false
        }

    private fun onAudioConnectionChanged(device: BluetoothDevice, intent: Intent) {
        val pending = pendingAudioDevice ?: return
        if (!pending.address.equals(device.address, ignoreCase = true)) return

        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
        val previous = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_DISCONNECTED)

        when {
            state == BluetoothProfile.STATE_CONNECTED -> audioConnected(pending)

            // This profile gave up; the attempt failed once every profile has
            state == BluetoothProfile.STATE_DISCONNECTED && previous == BluetoothProfile.STATE_CONNECTING -> {
                pendingAudioAttempts--
                if (pendingAudioAttempts <= 0) audioConnectFailed(pending)
            }
        }
    }

    private fun audioConnected(device: BluetoothDevice) {
        mainHandler.removeCallbacks(audioConnectTimeout)
        pendingAudioDevice = null
        // MicChanged follows once the headset microphone is in use
        send(BluetoothEvent.Status("Connected to ${nameOf(device)}"))
    }

    private fun audioConnectFailed(device: BluetoothDevice) {
        mainHandler.removeCallbacks(audioConnectTimeout)
        pendingAudioDevice = null
        send(
            BluetoothEvent.Status(
                "${nameOf(device)} didn't answer. Make sure it is switched on, out of its case, nearby and " +
                    "not connected to another phone or laptop, then tap Connect again."
            )
        )
    }

    private fun openBluetoothSettings(device: BluetoothDevice) {
        mainHandler.post {
            send(BluetoothEvent.Status("Tap \"${nameOf(device)}\" in Bluetooth settings to connect it, then come back."))
            activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.bluetoothDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

@Suppress("DEPRECATION")
private fun Intent.bluetoothClass(): BluetoothClass? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
    }
