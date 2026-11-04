package com.example.soundstage_app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundstage_app.DeviceKind
import com.example.soundstage_app.DiscoveredDevice
import com.example.soundstage_app.SoundstageViewModel

@Composable
fun OnboardingScreen(
    viewModel: SoundstageViewModel,
    onScanForDevices: () -> Unit,
    onConnectDevice: (String) -> Unit,
) {
    val currentPage = viewModel.onboardingPage
    val pagerState = rememberPagerState { SoundstageViewModel.ONBOARDING_PAGES }

    LaunchedEffect(currentPage) {
        pagerState.animateScrollToPage(
            currentPage,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .safeDrawingPadding()
    ) {
        // Pages only change with the button, not by swiping
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> PairingPage(viewModel, onScanForDevices, onConnectDevice)
                else -> ReadyPage()
            }
        }

        // Page indicators
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(SoundstageViewModel.ONBOARDING_PAGES) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (currentPage == index) 24.dp else 8.dp, height = 8.dp)
                        .background(
                            if (currentPage == index) AppColors.BlueAccent else AppColors.Grey,
                            RoundedCornerShape(10.dp)
                        )
                )
            }
        }

        Spacer(Modifier.height(30.dp))

        Button(
            onClick = viewModel::nextOnboardingPage,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.BlueAccent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                if (currentPage == SoundstageViewModel.ONBOARDING_PAGES - 1) "Get Started" else "Continue",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

// Icon, title and description shared by the three pages
@Composable
private fun OnboardingPage(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    titleSize: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(35.dp))
        Text(
            title,
            textAlign = TextAlign.Center,
            fontSize = titleSize.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun GreyText(text: String, size: Int, lineHeight: Float = 1f) {
    Text(
        text,
        textAlign = TextAlign.Center,
        color = AppColors.Grey,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp
    )
}

@Composable
private fun WelcomePage() {
    OnboardingPage(Icons.Filled.Hearing, AppColors.BlueAccent, "Welcome to Soundstage", 30) {
        GreyText("A smarter way to hear what matters most.", 17)
        Spacer(Modifier.height(20.dp))
        GreyText(
            "Soundstage helps you focus on the people you are talking to, even in noisy environments.",
            15,
            lineHeight = 1.5f
        )
    }
}

@Composable
private fun PairingPage(
    viewModel: SoundstageViewModel,
    onScanForDevices: () -> Unit,
    onConnectDevice: (String) -> Unit,
) {
    OnboardingPage(Icons.Filled.Bluetooth, AppColors.BlueAccent, "Connect Your Hearing Aids", 28) {
        GreyText("Pair your smart hearing devices to get started.", 16)
        Spacer(Modifier.height(40.dp))
        GreyText(viewModel.bluetoothStatus, 16)
        Spacer(Modifier.height(20.dp))

        Button(onClick = onScanForDevices) {
            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan for Devices")
        }

        Spacer(Modifier.height(20.dp))

        // A long device list scrolls instead of overflowing
        if (viewModel.discoveredDevices.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f)) {
                items(viewModel.discoveredDevices, key = { it.address }) { device ->
                    DeviceCard(device, onConnect = { onConnectDevice(device.address) })
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DiscoveredDevice, onConnect: () -> Unit) {
    val subtitle = buildList {
        add(device.kind.label)
        if (device.paired) add("Paired")
        device.rssi?.let { add("Signal: $it dBm") }
    }.joinToString(" • ")

    Card(Modifier.padding(vertical = 4.dp)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    if (device.kind == DeviceKind.AUDIO) Icons.Filled.Headphones else Icons.Filled.Bluetooth,
                    contentDescription = null
                )
            },
            headlineContent = { Text(device.name) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                Button(onClick = onConnect) {
                    Text(if (device.paired || device.kind == DeviceKind.BLE) "Connect" else "Pair")
                }
            }
        )
    }
}

@Composable
private fun ReadyPage() {
    OnboardingPage(Icons.Outlined.CheckCircle, AppColors.GreenAccent, "You’re All Set!", 30) {
        GreyText(
            "Explore your surroundings and focus on the conversations that matter to you.",
            16,
            lineHeight = 1.5f
        )
    }
}
