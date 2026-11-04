import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:soundstage_app/bluetooth_service.dart';

import 'test_helpers.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late List<MethodCall> calls;

  setUp(() {
    calls = mockBluetoothChannel();
  });

  tearDown(clearChannelMocks);

  group('BluetoothService sends the right calls to Kotlin', () {
    test('requestPermissions', () async {
      await BluetoothService.requestPermissions();

      expect(calls, hasLength(1));
      expect(calls.single.method, 'requestPermissions');
      expect(calls.single.arguments, isNull);
    });

    test('startScan', () async {
      await BluetoothService.startScan();

      expect(calls.single.method, 'startScan');
    });

    test('stopScan', () async {
      await BluetoothService.stopScan();

      expect(calls.single.method, 'stopScan');
    });

    test('pairDevice passes the device address', () async {
      await BluetoothService.pairDevice('AA:BB:CC:DD:EE:FF');

      expect(calls.single.method, 'pairDevice');
      expect(calls.single.arguments, {'address': 'AA:BB:CC:DD:EE:FF'});
    });

    test('connectDevice passes the device address', () async {
      await BluetoothService.connectDevice('11:22:33:44:55:66');

      expect(calls.single.method, 'connectDevice');
      expect(calls.single.arguments, {'address': '11:22:33:44:55:66'});
    });

    test('setHeadsetMic(true) enables the headset mic', () async {
      await BluetoothService.setHeadsetMic(true);

      expect(calls.single.method, 'setHeadsetMic');
      expect(calls.single.arguments, {'enabled': true});
    });

    test('setHeadsetMic(false) switches back to the phone mic', () async {
      await BluetoothService.setHeadsetMic(false);

      expect(calls.single.arguments, {'enabled': false});
    });
  });

  test('errors from Kotlin are passed back to the caller', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(bluetoothChannel, (call) async {
      throw PlatformException(code: 'NO_ADAPTER', message: 'No Bluetooth');
    });

    expect(
      BluetoothService.startScan(),
      throwsA(isA<PlatformException>()
          .having((e) => e.code, 'code', 'NO_ADAPTER')),
    );
  });

  test('setMethodCallHandler receives events sent from Kotlin', () async {
    final received = <MethodCall>[];
    BluetoothService.setMethodCallHandler((call) async {
      received.add(call);
    });

    await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .handlePlatformMessage(
      bluetoothChannel.name,
      const StandardMethodCodec()
          .encodeMethodCall(const MethodCall('scanStarted')),
      (_) {},
    );

    expect(received.single.method, 'scanStarted');
  });
}
