import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:soundstage_app/main.dart';

import 'test_helpers.dart';

// speech_to_text keeps a single shared instance, and once initialize()
// succeeds it never asks the platform again. So "speech unavailable" is
// tested first, before any test lets initialize succeed.
void main() {
  late List<MethodCall> speechCalls;

  tearDown(clearChannelMocks);

  Future<void> openSoundstage(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: SoundstageScreen(people: const [], onTogglePerson: (_) {}),
      ),
    ));
    await tester.pump();
  }

  Future<void> tapMic(WidgetTester tester) async {
    await tester.tap(find.byType(IconButton));
    await tester.pump();
  }

  testWidgets('mic does nothing when speech recognition is unavailable',
      (tester) async {
    speechCalls = mockSpeechChannel(available: false);
    await openSoundstage(tester);
    await tapMic(tester);

    expect(find.text('Tap the mic and start speaking'), findsOneWidget);
    expect(find.byIcon(Icons.mic_none), findsOneWidget);
    expect(speechCalls.map((c) => c.method), isNot(contains('listen')));
  });

  group('when speech recognition is available', () {
    setUp(() {
      speechCalls = mockSpeechChannel(available: true);
    });

    // stop() starts a 2 second timer in the plugin that delivers the final
    // result. Stop the mic like a user would, let that timer finish, then
    // unmount (dispose calls stop() again) and let that one finish too.
    Future<void> closeSoundstage(WidgetTester tester) async {
      if (find.byIcon(Icons.mic).evaluate().isNotEmpty) await tapMic(tester);
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 3));
    }

    // Known bug: the onResult callback in _toggleListening has no mounted
    // check, so a result arriving after dispose calls setState on a dead
    // State. Fails until that callback checks `mounted`.
    testWidgets('leaving the screen while listening does not crash',
        (tester) async {
      await openSoundstage(tester);
      await tapMic(tester);
      await sendSpeechResult(tester, 'half a sentence');

      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 3));

      expect(tester.takeException(), isNull);
    });

    testWidgets('starts idle', (tester) async {
      await openSoundstage(tester);

      expect(find.text('Tap the mic and start speaking'), findsOneWidget);
      expect(find.text('Tap the mic to transcribe speech'), findsOneWidget);
      expect(find.byIcon(Icons.mic_none), findsOneWidget);
      await closeSoundstage(tester);
    });

    testWidgets('tapping the mic starts listening', (tester) async {
      await openSoundstage(tester);
      await tapMic(tester);

      expect(find.text('Listening...'), findsOneWidget);
      expect(find.text('Listening... tap the mic to stop'), findsOneWidget);
      expect(find.byIcon(Icons.mic), findsOneWidget);
      expect(speechCalls.map((c) => c.method), contains('listen'));
      await closeSoundstage(tester);
    });

    testWidgets('recognised words appear in the caption box',
        (tester) async {
      await openSoundstage(tester);
      await tapMic(tester);

      await sendSpeechResult(tester, 'hello');
      expect(find.text('hello'), findsOneWidget);

      await sendSpeechResult(tester, 'hello there');
      expect(find.text('hello there'), findsOneWidget);
      await closeSoundstage(tester);
    });

    testWidgets('tapping the mic again stops listening', (tester) async {
      await openSoundstage(tester);
      await tapMic(tester);
      await sendSpeechResult(tester, 'keep this text');
      await tapMic(tester);

      expect(speechCalls.map((c) => c.method), contains('stop'));
      expect(find.byIcon(Icons.mic_none), findsOneWidget);
      expect(find.text('Tap the mic to transcribe speech'), findsOneWidget);
      // the last transcript stays on screen after stopping
      expect(find.text('keep this text'), findsOneWidget);
      await closeSoundstage(tester);
    });
  });
}
