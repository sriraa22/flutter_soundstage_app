import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:soundstage_app/main.dart';

import 'test_helpers.dart';

void main() {
  setUp(() {
    mockBluetoothChannel();
    // Speech is tested in speech_test.dart; unavailable here so the plugin
    // never starts its stop timer when SoundstageScreen is disposed.
    mockSpeechChannel(available: false);
  });

  tearDown(clearChannelMocks);

  Future<void> openMainScreen(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaterialApp(home: MainScreen()));
    await tester.pump();
  }

  Future<void> openPeopleTab(WidgetTester tester) async {
    await tester.tap(find.text('People').last);
    await tester.pumpAndSettle();
  }

  Finder personSwitch(String name) => find.descendant(
        of: find.widgetWithText(ListTile, name),
        matching: find.byType(Switch),
      );

  group('Person model', () {
    test('stores the values it was created with', () {
      final person = Person(name: 'Maria', isIncluded: true, x: 0.3, y: 0.4);

      expect(person.name, 'Maria');
      expect(person.isIncluded, isTrue);
      expect(person.x, 0.3);
      expect(person.y, 0.4);
    });

    test('included state can be changed', () {
      final person = Person(name: 'Maria', isIncluded: true, x: 0, y: 0);
      person.isIncluded = false;

      expect(person.isIncluded, isFalse);
    });
  });

  group('Soundstage tab', () {
    testWidgets('is shown first with all four people', (tester) async {
      await openMainScreen(tester);

      expect(find.byType(SoundstageScreen), findsOneWidget);
      for (final name in ['Maria', 'Leah', 'James', 'Sofia', 'You']) {
        expect(find.text(name), findsOneWidget);
      }
    });

    testWidgets('shows starting conversation counts', (tester) async {
      await openMainScreen(tester);

      expect(find.text('2 people in conversation • 2 muted'), findsOneWidget);
      expect(find.text('muted'), findsNWidgets(2));
      expect(find.byIcon(Icons.star), findsNWidgets(2));
    });

    testWidgets('tapping an included person mutes them', (tester) async {
      await openMainScreen(tester);
      await tester.tap(find.text('Maria'));
      await tester.pump();

      expect(find.text('1 people in conversation • 3 muted'), findsOneWidget);
      expect(find.text('muted'), findsNWidgets(3));
    });

    testWidgets('tapping a muted person includes them', (tester) async {
      await openMainScreen(tester);
      await tester.tap(find.text('James'));
      await tester.pump();

      expect(find.text('3 people in conversation • 1 muted'), findsOneWidget);
    });

    testWidgets('tapping a person twice returns them to the start',
        (tester) async {
      await openMainScreen(tester);
      await tester.tap(find.text('Leah'));
      await tester.pump();
      await tester.tap(find.text('Leah'));
      await tester.pump();

      expect(find.text('2 people in conversation • 2 muted'), findsOneWidget);
    });
  });

  group('People tab', () {
    testWidgets('lists every person with their status', (tester) async {
      await openMainScreen(tester);
      await openPeopleTab(tester);

      expect(find.byType(PeopleScreen), findsOneWidget);
      expect(find.byType(SoundstageScreen), findsNothing);
      expect(find.text('In your conversation'), findsNWidgets(2));
      expect(find.text('Muted'), findsNWidgets(2));
      expect(tester.widget<Switch>(personSwitch('Maria')).value, isTrue);
      expect(tester.widget<Switch>(personSwitch('James')).value, isFalse);
    });

    testWidgets('shows the first letter of each name in the avatar',
        (tester) async {
      await openMainScreen(tester);
      await openPeopleTab(tester);

      for (final letter in ['M', 'L', 'J', 'S']) {
        expect(
          find.descendant(
            of: find.byType(CircleAvatar),
            matching: find.text(letter),
          ),
          findsOneWidget,
        );
      }
    });

    testWidgets('switching a person off mutes them', (tester) async {
      await openMainScreen(tester);
      await openPeopleTab(tester);
      await tester.tap(personSwitch('Maria'));
      await tester.pumpAndSettle();

      expect(tester.widget<Switch>(personSwitch('Maria')).value, isFalse);
      expect(find.text('In your conversation'), findsOneWidget);
      expect(find.text('Muted'), findsNWidgets(3));
    });
  });

  group('Tabs share the same people', () {
    testWidgets('a change on Soundstage shows on People', (tester) async {
      await openMainScreen(tester);
      await tester.tap(find.text('Sofia'));
      await tester.pump();
      await openPeopleTab(tester);

      expect(tester.widget<Switch>(personSwitch('Sofia')).value, isTrue);
    });

    testWidgets('a change on People shows on Soundstage', (tester) async {
      await openMainScreen(tester);
      await openPeopleTab(tester);
      await tester.tap(personSwitch('Maria'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Soundstage').last);
      await tester.pumpAndSettle();

      expect(find.text('1 people in conversation • 3 muted'), findsOneWidget);
    });
  });
}
