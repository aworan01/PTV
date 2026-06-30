package com.personal.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.tv.ui.theme.*

@Composable
fun MoviesScreen() = StubScreen("Movies", Icons.Default.Movie, "Movies section coming soon")

@Composable
fun ShowsScreen() = StubScreen("Shows", Icons.Default.VideoLibrary, "Shows section coming soon")

@Composable
private fun StubScreen(title: String, icon: ImageVector, message: String) {
    Box(modifier = Modifier.fillMaxSize().background(NavyMid), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Icon(icon, null, tint = TextMuted, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 24.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 14.sp, color = TextSecondary)
        }
    }
}
