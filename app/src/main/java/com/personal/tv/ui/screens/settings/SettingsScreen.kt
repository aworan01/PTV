package com.personal.tv.ui.screens.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.tv.ui.theme.*

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyMid)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        // Playlist section
        SettingsSection(title = "Playlist") {
            SettingsInputRow(
                label = "M3U Playlist URL",
                value = state.playlistUrl,
                onValueChange = viewModel::setPlaylistUrl,
                placeholder = "http://your-provider.com/playlist.m3u"
            )
            SettingsDivider()
            SettingsInputRow(
                label = "EPG URL",
                value = state.epgUrl,
                onValueChange = viewModel::setEpgUrl,
                placeholder = "http://your-provider.com/epg.xml"
            )
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { viewModel.refreshPlaylist() },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reload Playlist")
                }
            }
        }

        // Player section
        SettingsSection(title = "Player") {
            SettingsToggleRow(
                label = "Hardware Acceleration",
                subtitle = "Use device GPU for decoding",
                icon = Icons.Default.Memory,
                checked = state.hwAccelEnabled,
                onCheckedChange = viewModel::setHwAccel
            )
            SettingsDivider()
            SettingsDropdownRow(
                label = "Default Aspect Ratio",
                icon = Icons.Default.AspectRatio,
                options = listOf("Fit", "Fill", "Stretch", "4:3", "16:9"),
                selected = state.aspectRatio,
                onSelect = viewModel::setAspectRatio
            )
            SettingsDivider()
            SettingsDropdownRow(
                label = "Network Buffer",
                icon = Icons.Default.NetworkCheck,
                options = listOf("Auto", "Low", "Medium", "High"),
                selected = state.bufferMode,
                onSelect = viewModel::setBufferMode
            )
            SettingsDivider()
            SettingsDropdownRow(
                label = "Deinterlacing Mode",
                icon = Icons.Default.Tune,
                options = listOf("Auto", "Off", "On"),
                selected = state.deinterlaceMode,
                onSelect = viewModel::setDeinterlaceMode
            )
        }

        // Subtitles section
        SettingsSection(title = "Subtitles") {
            SettingsToggleRow(
                label = "Show Subtitles by Default",
                subtitle = "Auto-enable when tracks are available",
                icon = Icons.Default.Subtitles,
                checked = state.autoSubtitles,
                onCheckedChange = viewModel::setAutoSubtitles
            )
            SettingsDivider()
            SettingsDropdownRow(
                label = "Subtitle Text Size",
                icon = Icons.Default.TextFields,
                options = listOf("Small", "Medium", "Large", "Extra Large"),
                selected = state.subtitleSize,
                onSelect = viewModel::setSubtitleSize
            )
        }

        // EPG section
        SettingsSection(title = "EPG / TV Guide") {
            SettingsToggleRow(
                label = "Auto-refresh EPG",
                subtitle = "Update guide data every 12 hours",
                icon = Icons.Default.Update,
                checked = state.autoRefreshEpg,
                onCheckedChange = viewModel::setAutoRefreshEpg
            )
            SettingsDivider()
            SettingsDropdownRow(
                label = "EPG Time Window",
                icon = Icons.Default.Schedule,
                options = listOf("2 hours", "4 hours", "6 hours", "12 hours"),
                selected = state.epgWindow,
                onSelect = viewModel::setEpgWindow
            )
        }

        // About section
        SettingsSection(title = "About") {
            SettingsInfoRow(label = "Version", value = "1.0.0")
            SettingsDivider()
            SettingsInfoRow(label = "Package", value = "com.personal.tv")
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(0.5.dp, NavyBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BlueAccent,
            modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
fun SettingsDivider() = Divider(color = NavyBorder.copy(alpha = 0.5f), thickness = 0.5.dp,
    modifier = Modifier.padding(vertical = 4.dp))

@Composable
fun SettingsInputRow(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextMuted, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BlueAccent,
                unfocusedBorderColor = NavyBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = BlueAccent
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = BlueAccent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = NavyBorder
            )
        )
    }
}

@Composable
fun SettingsDropdownRow(
    label: String,
    icon: ImageVector,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(selected, fontSize = 13.sp, color = BlueAccent)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(NavySurface)
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option, color = if (option == selected) BlueAccent else TextPrimary) },
                onClick = { onSelect(option); expanded = false },
                leadingIcon = if (option == selected) {
                    { Icon(Icons.Default.Check, null, tint = BlueAccent, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextPrimary)
        Text(value, fontSize = 13.sp, color = TextSecondary)
    }
}
