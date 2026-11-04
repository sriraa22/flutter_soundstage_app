import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:soundstage_app/main.dart';

import 'test_helpers.dart';

void main() {
  late List<MethodCall> bluetoothCalls;

  setUp(() {
    bluetoothCalls = mockBluetoothChannel();
    // Speech is tested in speech_test.dart; unavailable here so the plugin
    // never starts its stop timer when SoundstageScreen is disposed.
    mockSpeechChannel(available: false);
  });

  tearDown(clearChannelMocks);

  Future<void> openApp(WidgetTester tester) async {
    // Tall screen so the device list has room to show several cards.
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const HearingAidApp());
  }

  Future<void> goToPairingPage(WidgetTester tester) async {
    await openApp(tester);
    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();
  }

  Future<void> sendBluetoothEvent(
    WidgetTester tester,
    String method, [
    dynamic arguments,
  ]) {
    return sendPlatformEvent(tester, bluetoothChannel, method, arguments);
  }

  group('Onboarding flow', () {
    testWidgets('starts on the welcome page', (tester) async {
      await openApp(tester);

      expect(find.text('Welcome to Soundstage'), findsOneWidget);
      expect(find.text('Continue'), findsOneWidget);
    });

    testWidgets('Continue moves to the pairing page', (tester) async {
      await goToPairingPage(tester);

      expect(find.text('Connect Your Hearing Aids'), findsOneWidget);
      expect(find.text('Ready to connect'), findsOneWidget);
      expect(find.text('Continue'), findsOneWidget);
    });

    testWidgets('last page shows Get Started', (tester) async {
      await goToPairingPage(tester);
      await tester.tap(find.text('Continue'));
      await tester.pumpAndSettle();

      expect(find.text('You’re All Set!'), findsOneWidget);
      expect(find.text('Get Started'), findsOneWidget);
    });

    testWidgets('Get Started opens the main screen', (tester) async {
      await goToPairingPage(tester);
      await tester.tap(find.text('Continue'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Get Started'));
      await tester.pumpAndSettle();

      expect(find.byType(MainScreen), findsOneWidget);
      expect(find.byType(OnboardingScreen), findsNothing);
    });

    testWidgets('pages cannot be skipped by swiping', (tester) async {
      await openApp(tester);
      await tester.drag(find.byType(PageView), const Offset(-800, 0));
      await tester.pumpAndSettle();

      expect(find.text('Welcome to Soundstage'), findsOneWidget);
    });
  });

  group('Scan button', () {
    testWidgets('asks for permissions and then starts scanning',
        (tester) async {
      await goToPairingPage(tester);
      await tester.tap(find.text('Scan for Devices'));
      await tester.pump();

      expect(
        bluetoothCalls.map((c) => c.method),
        ['requestPermissions', 'startScan'],
      );
    });
  });

  group('Bluetooth status messages', () {
    final cases = <String, (Object?, String)>{
      'scanStarted': (null, 'Scanning for devices...'),
      'scanStopped': (null, 'Scan stopped'),
      'bluetoothDisabled':
          (null, 'Bluetooth is off. Turn it on and scan again.'),
      'pairingStatus': ('Pairing with Hearing Aid L...', 'Pairing with Hearing Aid L...'),
      'connectionStatus': ('Connected to Hearing Aid L', 'Connected to Hearing Aid L'),
      'scanError': ('Scan failed: 2', 'Scan failed: 2'),
      'micChanged': ('Hearing Aid L', 'Using microphone: Hearing Aid L'),
    };

    cases.forEach((method, testCase) {
      final (arguments, expected) = testCase;
      testWidgets('$method shows "$expected"', (tester) async {
        await goToPairingPage(tester);
        await sendBluetoothEvent(tester, method, arguments);

        expect(find.text(expected), findsOneWidget);
        expect(find.text('Ready to connect'), findsNothing);
      });
    });

    testWidgets('micChanged with no device falls back to phone mic',
        (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(tester, 'micChanged', 'Hearing Aid L');
      await sendBluetoothEvent(tester, 'micChanged', null);

      expect(find.text('Using phone microphone'), findsOneWidget);
    });

    testWidgets('unknown events are ignored', (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(tester, 'somethingElse', 'x');

      expect(find.text('Ready to connect'), findsOneWidget);
    });

    testWidgets('events after leaving onboarding do not crash',
        (tester) async {
      await goToPairingPage(tester);
      await tester.tap(find.text('Continue'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Get Started'));
      await tester.pumpAndSettle();

      await sendBluetoothEvent(tester, 'scanStarted');

      expect(tester.takeException(), isNull);
    });
  });

  group('Discovered devices list', () {
    testWidgets('is hidden until a device is found', (tester) async {
      await goToPairingPage(tester);

      expect(find.byType(ListTile), findsNothing);
    });

    testWidgets('shows a found device with its details', (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(tester, 'deviceFound', {
        'name': 'Hearing Aid L',
        'address': 'AA:AA',
        'rssi': -60,
        'kind': 'Audio',
        'paired': false,
      });

      expect(find.text('Hearing Aid L'), findsOneWidget);
      expect(find.text('Audio • Signal: -60 dBm'), findsOneWidget);
      expect(find.byIcon(Icons.headphones), findsOneWidget);
    });

    testWidgets('uses defaults when name, kind and rssi are missing',
        (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(tester, 'deviceFound', {'address': 'BB:BB'});

      expect(find.text('Unknown device'), findsOneWidget);
      expect(find.text('BLE'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(ListTile),
          matching: find.byIcon(Icons.bluetooth),
        ),
        findsOneWidget,
      );
    });

    testWidgets('lists different devices separately', (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(
          tester, 'deviceFound', {'name': 'Left', 'address': 'AA:AA'});
      await sendBluetoothEvent(
          tester, 'deviceFound', {'name': 'Right', 'address': 'BB:BB'});

      expect(find.byType(ListTile), findsNWidgets(2));
    });

    testWidgets('the same device found again is updated, not duplicated',
        (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(tester, 'deviceFound', {
        'name': 'Old name',
        'address': 'AA:AA',
        'rssi': -80,
        'kind': 'Audio',
      });
      await sendBluetoothEvent(tester, 'deviceFound', {
        'name': 'New name',
        'address': 'AA:AA',
        'paired': true,
      });

      expect(find.byType(ListTile), findsOneWidget);
      expect(find.text('New name'), findsOneWidget);
      expect(find.text('Old name'), findsNothing);
      // kind and signal are kept from the first event
      expect(find.text('Audio • Paired • Signal: -80 dBm'), findsOneWidget);
    });

    testWidgets('a newer signal reading replaces the old one',
        (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(
          tester, 'deviceFound', {'address': 'AA:AA', 'rssi': -80});
      await sendBluetoothEvent(
          tester, 'deviceFound', {'address': 'AA:AA', 'rssi': -50});

      expect(find.text('BLE • Signal: -50 dBm'), findsOneWidget);
    });

    group('button label', () {
      Future<void> showDevice(
        WidgetTester tester, {
        required String kind,
        required bool paired,
      }) async {
        await goToPairingPage(tester);
        await sendBluetoothEvent(tester, 'deviceFound', {
          'name': 'Device',
          'address': 'AA:AA',
          'kind': kind,
          'paired': paired,
        });
      }

      Finder deviceButton(String label) => find.descendant(
            of: find.byType(ListTile),
            matching: find.widgetWithText(ElevatedButton, label),
          );

      testWidgets('unpaired audio device shows Pair', (tester) async {
        await showDevice(tester, kind: 'Audio', paired: false);
        expect(deviceButton('Pair'), findsOneWidget);
      });

      testWidgets('paired audio device shows Connect', (tester) async {
        await showDevice(tester, kind: 'Audio', paired: true);
        expect(deviceButton('Connect'), findsOneWidget);
      });

      testWidgets('BLE device always shows Connect', (tester) async {
        await showDevice(tester, kind: 'BLE', paired: false);
        expect(deviceButton('Connect'), findsOneWidget);
      });
    });

    testWidgets('tapping the device button connects to that address',
        (tester) async {
      await goToPairingPage(tester);
      await sendBluetoothEvent(tester, 'deviceFound', {
        'name': 'Hearing Aid L',
        'address': 'AA:BB:CC:DD:EE:FF',
        'kind': 'Audio',
      });

      await tester.tap(find.text('Pair'));
      await tester.pump();

      expect(bluetoothCalls.last.method, 'connectDevice');
      expect(bluetoothCalls.last.arguments, {'address': 'AA:BB:CC:DD:EE:FF'});
    });

    testWidgets('a long list scrolls instead of overflowing',
        (tester) async {
      await goToPairingPage(tester);
      for (var i = 0; i < 20; i++) {
        await sendBluetoothEvent(
            tester, 'deviceFound', {'name': 'Device $i', 'address': '$i'});
      }

      expect(tester.takeException(), isNull);
      expect(find.byType(Scrollable), findsWidgets);
    });
  });
}
