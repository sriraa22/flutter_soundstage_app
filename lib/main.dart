import 'package:flutter/material.dart';

void main() {
  runApp(const HearingAidApp());
}

class HearingAidApp extends StatelessWidget {
  const HearingAidApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Soundstage',
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF121212),
        primarySwatch: Colors.blue,
        fontFamily: 'Arial',
      ),
      home: const OnboardingScreen(),
    );
  }
}

// =====================================================
// MODEL FOR PEOPLE
// =====================================================

class Person {
  String name;
  bool isIncluded;
  double x;
  double y;

  Person({
    required this.name,
    required this.isIncluded,
    required this.x,
    required this.y,
  });
}

// =====================================================
// ONBOARDING SCREEN
// =====================================================

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  int currentPage = 0;

  final PageController pageController = PageController();

  void nextPage() {
    if (currentPage < 2) {
      setState(() {
        currentPage++;
      });

      pageController.animateToPage(
        currentPage,
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    } else {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (context) => const MainScreen(),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PageView(
                controller: pageController,
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  // PAGE 1 - WELCOME
                  buildWelcomePage(),

                  // PAGE 2 - PAIRING
                  buildPairingPage(),

                  // PAGE 3 - READY
                  buildReadyPage(),
                ],
              ),
            ),

            // PAGE INDICATORS
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                3,
                (index) => Container(
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  width: currentPage == index ? 24 : 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: currentPage == index
                        ? Colors.blueAccent
                        : Colors.grey,
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
              ),
            ),

            const SizedBox(height: 30),

            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: nextPage,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blueAccent,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: Text(
                    currentPage == 2 ? 'Get Started' : 'Continue',
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            ),

            const SizedBox(height: 30),
          ],
        ),
      ),
    );
  }

  Widget buildWelcomePage() {
    return Padding(
      padding: const EdgeInsets.all(30),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.hearing,
            size: 100,
            color: Colors.blueAccent,
          ),

          const SizedBox(height: 35),

          const Text(
            'Welcome to Soundstage',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 30,
              fontWeight: FontWeight.bold,
            ),
          ),

          const SizedBox(height: 20),

          const Text(
            'A smarter way to hear what matters most.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 17,
              color: Colors.grey,
            ),
          ),

          const SizedBox(height: 20),

          const Text(
            'Soundstage helps you focus on the people you are talking to, even in noisy environments.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 15,
              color: Colors.grey,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget buildPairingPage() {
    return Padding(
      padding: const EdgeInsets.all(30),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            Icons.bluetooth,
            size: 100,
            color: Colors.blueAccent,
          ),

          const SizedBox(height: 35),

          const Text(
            'Connect Your Hearing Aids',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.bold,
            ),
          ),

          const SizedBox(height: 20),

          const Text(
            'Pair your smart hearing devices to get started.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 16,
              color: Colors.grey,
            ),
          ),

          const SizedBox(height: 40),

          // Fake pairing progress bar
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: LinearProgressIndicator(
              value: 0.7,
              minHeight: 10,
              backgroundColor: Colors.grey[800],
              valueColor: const AlwaysStoppedAnimation<Color>(
                Colors.blueAccent,
              ),
            ),
          ),

          const SizedBox(height: 15),

          const Text(
            'Ready to connect',
            style: TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }

  Widget buildReadyPage() {
    return Padding(
      padding: const EdgeInsets.all(30),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(
            Icons.check_circle_outline,
            size: 100,
            color: Colors.greenAccent,
          ),

          const SizedBox(height: 35),

          const Text(
            'You’re All Set!',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 30,
              fontWeight: FontWeight.bold,
            ),
          ),

          const SizedBox(height: 20),

          const Text(
            'Explore your surroundings and focus on the conversations that matter to you.',
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 16,
              color: Colors.grey,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }
}

// =====================================================
// MAIN SCREEN WITH BOTTOM NAVIGATION
// =====================================================

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int selectedIndex = 0;

  final List<Person> people = [
    Person(name: 'Maria', isIncluded: true, x: 0.32, y: 0.30),
    Person(name: 'Leah', isIncluded: true, x: 0.68, y: 0.32),
    Person(name: 'James', isIncluded: false, x: 0.18, y: 0.10),
    Person(name: 'Sofia', isIncluded: false, x: 0.82, y: 0.12),
  ];

  void togglePerson(int index) {
    setState(() {
      people[index].isIncluded = !people[index].isIncluded;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: selectedIndex == 0
          ? SoundstageScreen(
              people: people,
              onTogglePerson: togglePerson,
            )
          : PeopleScreen(
              people: people,
              onTogglePerson: togglePerson,
            ),

      bottomNavigationBar: NavigationBar(
        backgroundColor: const Color(0xFF1C1C1C),
        selectedIndex: selectedIndex,
        onDestinationSelected: (index) {
          setState(() {
            selectedIndex = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.spatial_audio_off),
            selectedIcon: Icon(Icons.spatial_audio),
            label: 'Soundstage',
          ),
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            selectedIcon: Icon(Icons.people),
            label: 'People',
          ),
        ],
      ),
    );
  }
}

// =====================================================
// SOUNDSTAGE SCREEN
// =====================================================

class SoundstageScreen extends StatelessWidget {
  final List<Person> people;
  final Function(int) onTogglePerson;

  const SoundstageScreen({
    super.key,
    required this.people,
    required this.onTogglePerson,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          // HEADER
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: 20,
              vertical: 20,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Soundstage',
                  style: TextStyle(
                    fontSize: 27,
                    fontWeight: FontWeight.bold,
                  ),
                ),

                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 18,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.blue.withOpacity(0.25),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Text(
                    'Ultra',
                    style: TextStyle(
                      color: Colors.blueAccent,
                      fontSize: 16,
                    ),
                  ),
                ),
              ],
            ),
          ),

          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text(
                '${people.where((p) => p.isIncluded).length} people in conversation • '
                '${people.where((p) => !p.isIncluded).length} muted',
                style: const TextStyle(
                  color: Colors.grey,
                  fontSize: 15,
                ),
              ),
            ),
          ),

          const SizedBox(height: 20),

          // SOUNDSTAGE MAP
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 15),
              child: LayoutBuilder(
                builder: (context, constraints) {
                  return Stack(
                    children: [
                      // Green Conversation Area
                      Center(
                        child: Container(
                          width: constraints.maxWidth * 0.85,
                          height: constraints.maxHeight * 0.65,
                          decoration: BoxDecoration(
                            color: const Color(0xFF075A4D),
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: Colors.white54,
                              width: 2,
                            ),
                          ),
                        ),
                      ),

                      // Dotted-like label
                      const Positioned(
                        top: 15,
                        left: 0,
                        right: 0,
                        child: Center(
                          child: Text(
                            'island',
                            style: TextStyle(
                              color: Colors.grey,
                              fontSize: 16,
                            ),
                          ),
                        ),
                      ),

                      // PEOPLE DOTS
                      ...people.asMap().entries.map((entry) {
                        int index = entry.key;
                        Person person = entry.value;

                        return Positioned(
                          left: constraints.maxWidth * person.x - 50,
                          top: constraints.maxHeight * person.y,
                          child: GestureDetector(
                            onTap: () => onTogglePerson(index),
                            child: Column(
                              children: [
                                Container(
                                  width: 105,
                                  height: 105,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: person.isIncluded
                                        ? const Color(0xFF08695A)
                                        : Colors.grey[800],
                                    border: Border.all(
                                      color: person.isIncluded
                                          ? Colors.tealAccent
                                          : Colors.grey,
                                      width: 2,
                                    ),
                                  ),
                                  child: Center(
                                    child: Text(
                                      person.name,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                ),

                                if (!person.isIncluded)
                                  const Text(
                                    'muted',
                                    style: TextStyle(
                                      color: Colors.grey,
                                      fontSize: 13,
                                    ),
                                  ),

                                if (person.isIncluded)
                                  const Icon(
                                    Icons.star,
                                    color: Colors.blueAccent,
                                    size: 20,
                                  ),
                              ],
                            ),
                          ),
                        );
                      }),

                      // YOU - CENTER
                      Positioned(
                        bottom: constraints.maxHeight * 0.05,
                        left: constraints.maxWidth / 2 - 55,
                        child: Container(
                          width: 110,
                          height: 110,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: Colors.deepPurple[600],
                            border: Border.all(
                              color: Colors.deepPurpleAccent,
                              width: 2,
                            ),
                          ),
                          child: const Center(
                            child: Text(
                              'You',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  );
                },
              ),
            ),
          ),

          // CAPTION BOX
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey[800]!),
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.chat_bubble_outline,
                    color: Colors.blueAccent,
                  ),
                  SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Maria\n"...grab the table by the window?"',
                      style: TextStyle(
                        fontSize: 16,
                        height: 1.5,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 12),

          const Text(
            'Tap a person to keep or mute',
            style: TextStyle(
              color: Colors.grey,
              fontSize: 14,
            ),
          ),

          const SizedBox(height: 15),
        ],
      ),
    );
  }
}

// =====================================================
// PEOPLE LIST SCREEN
// =====================================================

class PeopleScreen extends StatelessWidget {
  final List<Person> people;
  final Function(int) onTogglePerson;

  const PeopleScreen({
    super.key,
    required this.people,
    required this.onTogglePerson,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          const Padding(
            padding: EdgeInsets.all(20),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'People',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),

          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 20),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'Manage who is included in your conversation.',
                style: TextStyle(
                  color: Colors.grey,
                  fontSize: 15,
                ),
              ),
            ),
          ),

          const SizedBox(height: 20),

          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 15),
              itemCount: people.length,
              itemBuilder: (context, index) {
                final person = people[index];

                return Card(
                  color: const Color(0xFF1E1E1E),
                  margin: const EdgeInsets.symmetric(vertical: 6),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(15),
                  ),
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 20,
                      vertical: 8,
                    ),

                    leading: CircleAvatar(
                      radius: 25,
                      backgroundColor: person.isIncluded
                          ? const Color(0xFF075A4D)
                          : Colors.grey[800],
                      child: Text(
                        person.name[0],
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 18,
                        ),
                      ),
                    ),

                    title: Text(
                      person.name,
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                      ),
                    ),

                    subtitle: Text(
                      person.isIncluded
                          ? 'In your conversation'
                          : 'Muted',
                      style: TextStyle(
                        color: person.isIncluded
                            ? Colors.greenAccent
                            : Colors.grey,
                      ),
                    ),

                    trailing: Switch(
                      value: person.isIncluded,
                      onChanged: (_) => onTogglePerson(index),
                      activeColor: Colors.blueAccent,
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}