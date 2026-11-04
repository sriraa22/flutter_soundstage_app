# Soundstage

Native Android app (Kotlin + Jetpack Compose) that helps people with hearing aids follow conversations:

- **Onboarding** – scan for, pair and connect Bluetooth hearing aids, headphones and BLE devices.
- **Soundstage** – a map of the people around you; tap someone to include or mute them.
- **Live captions** – speech turned into text by the phone's speech recognizer, using the connected
  headset's microphone when there is one. Captions keep running in the background with a notification.
- **People** – switch each person in or out of the conversation.

## Project layout

```
app/src/main/java/com/example/soundstage_app/
  MainActivity.kt          Hosts the UI, permissions, starts/stops captions
  SoundstageViewModel.kt   Screen state (people, devices, transcript)
  BluetoothController.kt   Scanning, pairing, connecting
  BluetoothAudioRouter.kt  Uses the headset microphone while one is connected
  BluetoothDevices.kt      Device kinds (BLE / Classic / Audio)
  CaptionService.kt        Foreground service running speech recognition
  ui/                      Compose screens (onboarding, soundstage, people)
app/src/test/              Unit tests
```

## Build

Open the folder in Android Studio, or from a terminal:

```
./gradlew installDebug        # build and install on a connected phone
./gradlew testDebugUnitTest   # run unit tests
```

Requires JDK 17+ (Android Studio's bundled JDK works) and the Android SDK (compileSdk 36).
