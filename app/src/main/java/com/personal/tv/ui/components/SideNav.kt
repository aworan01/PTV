package com.personal.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.tv.ui.Screen
import com.personal.tv.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

data class NavEntry(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val navEntries = listOf(
    NavEntry(Screen.TV,        "TV",        Icons.Default.Tv),
    NavEntry(Screen.Movies,    "Movies",    Icons.Default.Movie),
    NavEntry(Screen.Shows,     "Shows",     Icons.Default.VideoLibrary),
    NavEntry(Screen.Playlists, "Playlists", Icons.Default.PlaylistPlay),
    NavEntry(Screen.Settings,  "Settings",  Icons.Default.Settings),
)

@Composable
fun SideNav(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(timeFormat.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormat.format(Date())
            kotlinx.coroutines.delay(30_000)
        }
    }

    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(NavyDeep)
            .padding(vertical = 16.dp, horizontal = 12.dp)
    ) {
        // Logo + time
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Personal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("TV", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BlueAccent)
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(ProYellow)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) { Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NavyDeep) }
            }
            Text(currentTime, fontSize = 13.sp, color = TextSecondary)
        }

        Text(
            "IPTV tuned for TV", fontSize = 10.sp, color = TextMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Spacer(Modifier.height(20.dp))

        NavRow(label = "Search", icon = Icons.Default.Search, isSelected = false, onClick = { })
        Spacer(Modifier.height(4.dp))

        navEntries.forEach { entry ->
            NavRow(
                label = entry.label,
                icon = entry.icon,
                isSelected = currentRoute == entry.screen.route,
                onClick = { onNavigate(entry.screen) }
            )
            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.weight(1f))

        // Exit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onExit() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ExitToApp, "Exit", tint = LiveRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Exit", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LiveRed)
        }
    }
}

@Composable
fun NavRow(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NavySelected else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(2.dp, 20.dp)
                .background(if (isSelected) BlueAccent else Color.Transparent)
                .clip(RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(if (isSelected) 10.dp else 12.dp))
        Icon(icon, label, tint = if (isSelected) BlueAccent else TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label, fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) TextPrimary else TextSecondary
        )
    }
}
