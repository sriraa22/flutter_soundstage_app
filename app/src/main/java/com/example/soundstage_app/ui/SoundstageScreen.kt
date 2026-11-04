package com.example.soundstage_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.soundstage_app.CaptionControls
import com.example.soundstage_app.Person
import com.example.soundstage_app.SoundstageViewModel

@Composable
fun SoundstageScreen(viewModel: SoundstageViewModel, captions: CaptionControls) {
    val people = viewModel.people
    val isListening = viewModel.isListening

    Column(
        Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Soundstage", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Ultra",
                color = AppColors.BlueAccent,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(AppColors.Blue.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }

        Text(
            "${people.count { it.isIncluded }} people in conversation • ${people.count { !it.isIncluded }} muted",
            color = AppColors.Grey,
            fontSize = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(20.dp))

        SoundstageMap(
            people = people,
            onTogglePerson = viewModel::togglePerson,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 15.dp)
        )

        // Caption box
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .border(1.dp, AppColors.Grey800, RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            val accent = if (isListening) AppColors.RedAccent else AppColors.BlueAccent
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = accent)
            Spacer(Modifier.width(12.dp))
            Text(
                viewModel.transcript,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.toggleListening(captions) }) {
                Icon(
                    if (isListening) Icons.Filled.Mic else Icons.Filled.MicNone,
                    contentDescription = if (isListening) "Stop captions" else "Start captions",
                    tint = accent
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            if (isListening) "Listening... tap the mic to stop" else "Tap the mic to transcribe speech",
            color = AppColors.Grey,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(15.dp))
    }
}

// The conversation area with everyone around the user; tapping someone includes or mutes them
@Composable
private fun SoundstageMap(
    people: List<Person>,
    onTogglePerson: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth
        val height = maxHeight

        // Green conversation area (a circle that fits in 85% x 65% of the map)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(min(width * 0.85f, height * 0.65f))
                .background(AppColors.ConversationArea, CircleShape)
                .border(2.dp, AppColors.White54, CircleShape)
        )

        Text(
            "island",
            color = AppColors.Grey,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 15.dp)
        )

        // People dots
        people.forEachIndexed { index, person ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(x = width * person.x - 50.dp, y = height * person.y)
                    .clickable { onTogglePerson(index) }
            ) {
                Box(
                    Modifier
                        .size(105.dp)
                        .background(
                            if (person.isIncluded) AppColors.IncludedPerson else AppColors.Grey800,
                            CircleShape
                        )
                        .border(
                            2.dp,
                            if (person.isIncluded) AppColors.TealAccent else AppColors.Grey,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(person.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (person.isIncluded) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = AppColors.BlueAccent,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("muted", color = AppColors.Grey, fontSize = 13.sp)
                }
            }
        }

        // You, at the bottom centre
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = height * 0.05f)
                .size(110.dp)
                .background(AppColors.DeepPurple600, CircleShape)
                .border(2.dp, AppColors.DeepPurpleAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("You", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
