package com.example.soundstage_app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Locale

// Live captions: turns what the microphone (or the connected headset's, see BluetoothAudioRouter)
// hears into text with the phone's speech recognizer (Google).
//
// On Android 13+ with Google's on-device speech pack, it listens in one long "segmented" session
// that reports each sentence as it's finished, so there are no gaps or beeps between sentences.
// Otherwise it falls back to the classic recognizer, which stops after every pause and is
// started again straight away (Android plays a beep each time; that can't be turned off).
//
// It runs as a foreground service so captions keep going while the app is in the background or
// the screen is off; Android shows its "Live captions on" notification (with the latest caption
// and a Stop button) meanwhile. It must be started while the app is on screen (Android 14+ refuses otherwise).
class CaptionService : Service(), RecognitionListener {

    // What the service reports back to the app (on the main thread)
    interface Listener {
        fun onListening()
        fun onCaption(text: String, isFinal: Boolean)
        fun onError(message: String)
        // The user tapped Stop in the notification
        fun onStopRequested()
    }

    companion object {
        private const val CHANNEL_ID = "captions"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.soundstage_app.STOP_CAPTIONS"
        // Android drops notification updates that come faster than a few per second
        private const val UPDATE_INTERVAL_MS = 1_000L
        // How long one continuous session asks to last; a new one is started when it ends
        private const val SESSION_LENGTH_MS = 60 * 60 * 1000L
        private const val RESTART_DELAY_MS = 300L
        private const val MAX_RESTART_DELAY_MS = 5_000L
        // Errors in a row (without any caption) before giving up on continuous mode
        private const val MAX_CONTINUOUS_FAILURES = 3

        var listener: Listener? = null

        // False when the microphone permission is missing; the service can't be started without it
        fun start(context: Context): Boolean {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) return false
            ContextCompat.startForegroundService(context, Intent(context, CaptionService::class.java))
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaptionService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var recognizer: SpeechRecognizer? = null
    // True while using the continuous (segmented) session
    private var continuous = false
    private var started = false
    private var destroyed = false
    private var reportedListening = false
    private var failures = 0
    private var lastPartial = ""
    private val restart = Runnable { listen() }

    private var caption = ""
    private var lastUpdate = 0L
    private val updateNotification = Runnable {
        lastUpdate = SystemClock.elapsedRealtime()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            @Suppress("MissingPermission") // checked by areNotificationsEnabled()
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            listener?.onStopRequested()
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            // Refused by Android, e.g. started while the app wasn't on screen
            fail("Couldn't start captions. Open the app and try again.")
            return START_NOT_STICKY
        }
        started = true

        // Keeps the CPU awake with the screen off, so speech keeps being turned into text
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "soundstage:captions")
            .apply { acquire() }

        continuous = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (!continuous && !SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("This phone has no speech recognizer. Install or update the Google app.")
            return START_NOT_STICKY
        }
        createRecognizer()
        listen()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(restart)
        recognizer?.destroy()
        recognizer = null

        handler.removeCallbacks(updateNotification)
        wakeLock?.release()
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = if (continuous && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }.apply { setRecognitionListener(this@CaptionService) }
    }

    private fun recognizerIntent() =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (continuous && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Keep listening for SESSION_LENGTH_MS, reporting each sentence through onSegmentResults
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS
                )
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, SESSION_LENGTH_MS)
            }
        }

    private fun listen() {
        if (destroyed) return
        lastPartial = ""
        recognizer?.startListening(recognizerIntent())
    }

    // Waits longer after each error in a row, so a recognizer that keeps failing
    // (e.g. no internet in classic mode) isn't hammered
    private fun scheduleRestart() {
        handler.removeCallbacks(restart)
        val delay = (RESTART_DELAY_MS shl failures.coerceAtMost(4)).coerceAtMost(MAX_RESTART_DELAY_MS)
        handler.postDelayed(restart, delay)
    }

    private fun useClassicMode() {
        continuous = false
        failures = 0
        createRecognizer()
        scheduleRestart()
    }

    private fun fail(message: String) {
        listener?.onError(message)
        stopSelf()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        if (reportedListening) return
        reportedListening = true
        listener?.onListening()
    }

    override fun onPartialResults(partialResults: Bundle) {
        val text = partialResults.bestResult() ?: return
        if (text == lastPartial) return
        lastPartial = text
        report(text, isFinal = false)
    }

    // A finished sentence in continuous mode
    override fun onSegmentResults(segmentResults: Bundle) {
        lastPartial = ""
        segmentResults.bestResult()?.let { report(it, isFinal = true) }
    }

    override fun onEndOfSegmentedSession() {
        scheduleRestart()
    }

    // The end of a listening session in classic mode (and on some phones, of a continuous one)
    override fun onResults(results: Bundle) {
        results.bestResult()?.let { report(it, isFinal = true) }
        scheduleRestart()
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                fail("Allow microphone access to see live captions")

            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                if (continuous && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // The on-device speech pack for this language isn't on the phone yet:
                    // ask Android to download it, and use the classic recognizer meanwhile
                    recognizer?.triggerModelDownload(recognizerIntent())
                    useClassicMode()
                } else {
                    fail("Live captions don't support this phone's language yet")
                }

            else -> {
                failures++
                if (continuous && failures >= MAX_CONTINUOUS_FAILURES) {
                    useClassicMode()
                    return
                }
                // Busy or disconnected recognizers are replaced; anything else
                // (nothing heard, timeouts, no internet...) just listens again
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                    error == SpeechRecognizer.ERROR_CLIENT
                ) {
                    createRecognizer()
                }
                scheduleRestart()
            }
        }
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun report(text: String, isFinal: Boolean) {
        failures = 0
        listener?.onCaption(text, isFinal)
        showCaption(text)
    }

    private fun showCaption(text: String) {
        caption = text
        handler.removeCallbacks(updateNotification)
        val wait = lastUpdate + UPDATE_INTERVAL_MS - SystemClock.elapsedRealtime()
        handler.postDelayed(updateNotification, wait.coerceAtLeast(0))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live captions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while captions are running in the background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Live captions on")
            .setContentText(caption.ifEmpty { "Listening..." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(caption.ifEmpty { "Listening..." }))
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop", stopIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun openAppIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    private fun stopIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            0,
            Intent(this, CaptionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
}

private fun Bundle.bestResult(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
