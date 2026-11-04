package com.example.soundstage_app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.soundstage_app.CaptionControls
import com.example.soundstage_app.SoundstageViewModel

// Colors carried over from the Flutter app (Material color palette)
object AppColors {
    val Background = Color(0xFF121212)
    val NavBar = Color(0xFF1C1C1C)
    val Card = Color(0xFF1E1E1E)
    val BlueAccent = Color(0xFF448AFF)
    val Blue = Color(0xFF2196F3)
    val Grey = Color(0xFF9E9E9E)
    val Grey800 = Color(0xFF424242)
    val GreenAccent = Color(0xFF69F0AE)
    val TealAccent = Color(0xFF64FFDA)
    val RedAccent = Color(0xFFFF5252)
    val DeepPurple600 = Color(0xFF5E35B1)
    val DeepPurpleAccent = Color(0xFF7C4DFF)
    val ConversationArea = Color(0xFF075A4D)
    val IncludedPerson = Color(0xFF08695A)
    val White54 = Color(0x8AFFFFFF)
}

@Composable
fun SoundstageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.BlueAccent,
            background = AppColors.Background,
            surface = AppColors.Background,
        ),
        content = content
    )
}

@Composable
fun SoundstageApp(
    viewModel: SoundstageViewModel,
    onScanForDevices: () -> Unit,
    onConnectDevice: (String) -> Unit,
    captions: CaptionControls,
) {
    SoundstageTheme {
        if (!viewModel.onboardingDone) {
            OnboardingScreen(viewModel, onScanForDevices, onConnectDevice)
        } else {
            MainScreen(viewModel, captions)
        }
    }
}

// Bottom navigation between the Soundstage map and the People list
@Composable
fun MainScreen(viewModel: SoundstageViewModel, captions: CaptionControls) {
    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = {
            NavigationBar(containerColor = AppColors.NavBar) {
                val onSoundstage = viewModel.selectedTab == SoundstageViewModel.TAB_SOUNDSTAGE
                NavigationBarItem(
                    selected = onSoundstage,
                    onClick = { viewModel.selectedTab = SoundstageViewModel.TAB_SOUNDSTAGE },
                    icon = {
                        Icon(
                            if (onSoundstage) Icons.Filled.SpatialAudio else Icons.Filled.SpatialAudioOff,
                            contentDescription = null
                        )
                    },
                    label = { Text("Soundstage") }
                )
                NavigationBarItem(
                    selected = !onSoundstage,
                    onClick = { viewModel.selectedTab = SoundstageViewModel.TAB_PEOPLE },
                    icon = {
                        Icon(
                            if (!onSoundstage) Icons.Filled.People else Icons.Outlined.People,
                            contentDescription = null
                        )
                    },
                    label = { Text("People") }
                )
            }
        }
    ) { innerPadding ->
        // Caption state lives in the view model, so it keeps running while the People tab is shown
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewModel.selectedTab) {
                SoundstageViewModel.TAB_SOUNDSTAGE -> SoundstageScreen(viewModel, captions)
                else -> PeopleScreen(viewModel)
            }
        }
    }
}
