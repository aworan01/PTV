package com.personal.tv.ui.screens.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.tv.data.db.entity.PlaylistEntity
import com.personal.tv.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PlaylistsScreen(viewModel: PlaylistsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFromFile(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyMid)
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Playlists", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("${state.playlists.size} playlist${if (state.playlists.size != 1) "s" else ""}",
                    fontSize = 12.sp, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*", "audio/x-mpegurl")) },
                    border = BorderStroke(1.dp, NavyBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Local File", fontSize = 13.sp)
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add URL", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Loading / error
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BlueAccent)
                    Spacer(Modifier.height(12.dp))
                    Text(state.loadingMessage, color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else if (state.playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlaylistAdd, null, tint = TextMuted, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No playlists yet", color = TextSecondary, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Add an M3U URL or open a local file", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        isActive = playlist.isActive,
                        onActivate = { viewModel.setActive(playlist.id) },
                        onRefresh = { viewModel.refresh(playlist.id) },
                        onDelete = { viewModel.delete(playlist.id) },
                        onEditEpg = { newEpgUrl -> viewModel.updateEpgUrl(playlist.id, newEpgUrl) }
                    )
                }
            }
        }

        // Error snackbar area
        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LiveRed.copy(alpha = 0.15f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = LiveRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(err, color = LiveRed, fontSize = 13.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = LiveRed)
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlaylistDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, epgUrl ->
                viewModel.addFromUrl(url, name, epgUrl)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun PlaylistCard(
    playlist: PlaylistEntity,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onEditEpg: (String) -> Unit
) {
    var showEpgEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) NavySelected else NavySurface)
            .border(
                width = if (isActive) 1.5.dp else 0.5.dp,
                color = if (isActive) BlueAccent else NavyBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { if (!isActive) onActivate() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active indicator
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (isActive) BlueAccent else TextMuted)
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = playlist.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) TextPrimary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BlueAccent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Source
            val source = playlist.url ?: playlist.localUri ?: "Unknown source"
            Text(
                text = source,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (playlist.epgUrl.isNotEmpty()) {
                Text(
                    text = "EPG: ${playlist.epgUrl}",
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (playlist.lastUpdated > 0) {
                Text(
                    text = "Updated: ${fmt.format(Date(playlist.lastUpdated))}",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { showEpgEdit = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.CalendarToday, "Set EPG", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            if (!isActive) {
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = LiveRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    // EPG URL edit dialog
    if (showEpgEdit) {
        var epgText by remember { mutableStateOf(playlist.epgUrl) }
        AlertDialog(
            onDismissRequest = { showEpgEdit = false },
            containerColor = NavySurface,
            title = { Text("EPG URL", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = epgText,
                    onValueChange = { epgText = it },
                    label = { Text("XMLTV URL", color = TextSecondary) },
                    placeholder = { Text("http://...", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueAccent, unfocusedBorderColor = NavyBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = BlueAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { onEditEpg(epgText); showEpgEdit = false }) {
                    Text("Save", color = BlueAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEpgEdit = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = NavySurface,
            title = { Text("Delete Playlist?", color = TextPrimary) },
            text = { Text("This will remove \"${playlist.name}\" and all its channels.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = LiveRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, epgUrl: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        title = { Text("Add Playlist", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name", color = TextSecondary) },
                    placeholder = { Text("My IPTV", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueAccent, unfocusedBorderColor = NavyBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = BlueAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("M3U URL *", color = TextSecondary) },
                    placeholder = { Text("http://provider.com/playlist.m3u", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueAccent, unfocusedBorderColor = NavyBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = BlueAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("EPG URL (optional)", color = TextSecondary) },
                    placeholder = { Text("http://provider.com/epg.xml", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueAccent, unfocusedBorderColor = NavyBorder,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = BlueAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotEmpty()) onAdd(name.ifEmpty { "My Playlist" }, url, epgUrl)
            }) { Text("Add", color = BlueAccent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
