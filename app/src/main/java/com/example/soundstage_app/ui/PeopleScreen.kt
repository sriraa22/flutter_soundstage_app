package com.example.soundstage_app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soundstage_app.SoundstageViewModel

// Manage who is included in the conversation
@Composable
fun PeopleScreen(viewModel: SoundstageViewModel) {
    Column(
        Modifier
            .fillMaxSize()
    ) {
        Text(
            "People",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(20.dp)
        )

        Text(
            "Manage who is included in your conversation.",
            color = AppColors.Grey,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(20.dp))

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 15.dp)
        ) {
            itemsIndexed(viewModel.people, key = { _, person -> person.name }) { index, person ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Card),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .background(
                                        if (person.isIncluded) AppColors.ConversationArea else AppColors.Grey800,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    person.name.take(1),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        },
                        headlineContent = {
                            Text(person.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        },
                        supportingContent = {
                            Text(
                                if (person.isIncluded) "In your conversation" else "Muted",
                                color = if (person.isIncluded) AppColors.GreenAccent else AppColors.Grey
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = person.isIncluded,
                                onCheckedChange = { viewModel.togglePerson(index) },
                                colors = SwitchDefaults.colors(checkedTrackColor = AppColors.BlueAccent)
                            )
                        }
                    )
                }
            }
        }
    }
}
