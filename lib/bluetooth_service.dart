import 'package:flutter/services.dart';

class BluetoothService {
  static const MethodChannel _channel =
      MethodChannel('soundstage/bluetooth');

  // Ask Android for Bluetooth and microphone permissions.
  static Future<void> requestPermissions() async {
    await _channel.invokeMethod('requestPermissions');
  }

  // Start searching for nearby BLE devices.
  static Future<void> startScan() async {
    await _channel.invokeMethod('startScan');
  }

  // Stop searching.
  static Future<void> stopScan() async {
    await _channel.invokeMethod('stopScan');
  }

  // Ask Android to pair with a device.
  static Future<void> pairDevice(String address) async {
    await _channel.invokeMethod(
      'pairDevice',
      {'address': address},
    );
  }

  // Connect to a BLE device using GATT.
  static Future<void> connectDevice(String address) async {
    await _channel.invokeMethod(
      'connectDevice',
      {'address': address},
    );
  }

  // Use the hearing aid / headset microphone when one is connected (true),
  // or always use the phone's microphone (false).
  static Future<void> setHeadsetMic(bool enabled) async {
    await _channel.invokeMethod(
      'setHeadsetMic',
      {'enabled': enabled},
    );
  }

  // Listen for messages coming from Kotlin.
  static void setMethodCallHandler(
    Future<dynamic> Function(MethodCall call) handler,
  ) {
    _channel.setMethodCallHandler(handler);
  }
}