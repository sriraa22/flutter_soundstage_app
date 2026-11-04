import 'package:flutter/services.dart';

// Live captions, turned from speech into text by the phone's speech recognizer (Google).
// They keep running in the background, with a "Live captions on" notification,
// until stop() is called or the user taps Stop in that notification.
class LiveCaptions {
  static const MethodChannel _channel =
      MethodChannel('soundstage/captions');

  // Call while the app is on screen. Asks for the microphone if needed,
  // and returns false if it wasn't allowed.
  static Future<bool> start() async {
    return await _channel.invokeMethod<bool>('start') ?? false;
  }

  static Future<void> stop() async {
    await _channel.invokeMethod('stop');
  }

  // onCaption gets the sentence being spoken as it goes (isFinal false),
  // then the finished sentence (isFinal true).
  static void setHandlers({
    required void Function() onListening,
    required void Function(String text, bool isFinal) onCaption,
    required void Function(String message) onError,
    required void Function() onStopped,
  }) {
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'listening':
          onListening();
        case 'caption':
          onCaption(
            call.arguments['text'] as String,
            call.arguments['final'] as bool,
          );
        case 'error':
          onError(call.arguments as String);
        case 'stopRequested':
          onStopped();
      }
    });
  }
}
