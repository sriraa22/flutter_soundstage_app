package com.example.soundstage_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.soundstage_app.ui.SoundstageApp

class MainActivity : ComponentActivity(), CaptionControls {

    companion object {
        private const val CAPTION_PERMISSION_REQUEST = 101
    }

    private val viewModel: SoundstageViewModel by viewModels()
    private lateinit var bluetooth: BluetoothController

    // Waiting for the microphone permission before starting captions
    private var pendingCaptionStart: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bluetooth = BluetoothController(this, viewModel::onBluetoothEvent)
        bluetooth.start()

        CaptionService.listener = object : CaptionService.Listener {
            override fun onListening() = viewModel.onCaptionsListening()
            override fun onCaption(text: String, isFinal: Boolean) = viewModel.onCaption(text, isFinal)
            override fun onError(message: String) = viewModel.onCaptionsError(message)
            override fun onStopRequested() = viewModel.onCaptionsStopped()
        }

        setContent {
            SoundstageApp(
                viewModel = viewModel,
                onScanForDevices = {
                    bluetooth.requestPermissions()
                    bluetooth.startScan()
                },
                onConnectDevice = bluetooth::connectDevice,
                captions = this
            )
        }
    }

    // Asks for the microphone first if needed. Also asks (Android 13+) to show the captions notification,
    // which holds the Stop button; captions run in the background without it too.
    override fun start(onResult: (Boolean) -> Unit) {
        val missing = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (missing.isEmpty() || bluetooth.permissionRequestInFlight) {
            onResult(CaptionService.start(this))
            return
        }
        pendingCaptionStart = onResult
        bluetooth.permissionRequestInFlight = true
        requestPermissions(missing.toTypedArray(), CAPTION_PERMISSION_REQUEST)
    }

    override fun stop() {
        CaptionService.stop(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAPTION_PERMISSION_REQUEST -> {
                bluetooth.permissionRequestInFlight = false
                pendingCaptionStart?.invoke(CaptionService.start(this))
                pendingCaptionStart = null
            }

            BluetoothController.PERMISSION_REQUEST -> bluetooth.onPermissionsResult()
        }
    }

    override fun onDestroy() {
        // Captions belong to this screen, so they end with it
        CaptionService.listener = null
        CaptionService.stop(this)
        bluetooth.release()
        super.onDestroy()
    }
}
