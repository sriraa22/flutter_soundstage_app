import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

const bluetoothChannel = MethodChannel('soundstage/bluetooth');
const speechChannel = MethodChannel('plugin.csdcorp.com/speech_to_text');

// Records every call Dart makes to the Bluetooth channel so tests can check
// what was sent to Kotlin.
List<MethodCall> mockBluetoothChannel() {
  final calls = <MethodCall>[];
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(bluetoothChannel, (call) async {
    calls.add(call);
    return null;
  });
  return calls;
}

// Fakes the speech_to_text plugin. [available] controls whether
// initialize() reports that speech recognition can be used.
List<MethodCall> mockSpeechChannel({bool available = true}) {
  final calls = <MethodCall>[];
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(speechChannel, (call) async {
    calls.add(call);
    switch (call.method) {
      case 'has_permission':
      case 'initialize':
        return available;
      case 'listen':
        return true;
      default:
        return null;
    }
  });
  return calls;
}

void clearChannelMocks() {
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(bluetoothChannel, null);
  messenger.setMockMethodCallHandler(speechChannel, null);
}

// Simulates Kotlin calling into Dart over [channel].
Future<void> sendPlatformEvent(
  WidgetTester tester,
  MethodChannel channel,
  String method, [
  dynamic arguments,
]) async {
  await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
    channel.name,
    const StandardMethodCodec().encodeMethodCall(MethodCall(method, arguments)),
    (_) {},
  );
  await tester.pump();
}

// Simulates the speech plugin delivering recognised words.
Future<void> sendSpeechResult(WidgetTester tester, String words) {
  return sendPlatformEvent(
    tester,
    speechChannel,
    'textRecognition',
    jsonEncode({
      'alternates': [
        {'recognizedWords': words, 'confidence': 0.9},
      ],
      'resultType': 0,
    }),
  );
}
