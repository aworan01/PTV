package com.personal.tv.ui.screens.epg

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.personal.tv.data.model.Channel
import com.personal.tv.data.model.Programme
import com.personal.tv.ui.screens.groupoptions.GroupOptionsModal
import com.personal.tv.ui.screens.player.PlayerActivity
import com.personal.tv.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

private val CHANNEL_COL_WIDTH = 180.dp
private val TIME_SLOT_WIDTH    = 200.dp
private val ROW_HEIGHT         = 56.dp
private val TIME_HEADER_HEIGHT = 36.dp

@Composable
fun EpgScreen(viewModel: EpgViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // File picker launcher for local M3U files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadPlaylistFromUri(it) }
    }

    Box(modifier = Modifier.fillMaxSize().background(NavyMid)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Hero banner
            HeroBanner(
                channel = state.featuredChannel,
                programme = state.featuredProgramme,
                onChannelClick = { channel ->
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_CHANNEL, channel)
                    }
                    context.startActivity(intent)
                }
            )

            // Group tabs
            GroupTabRow(
                groups = state.groups,
                selectedGroup = state.selectedGroup,
                onGroupSelected = viewModel::selectGroup,
                onGroupLongPress = viewModel::showGroupOptions
            )

            // EPG grid
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BlueAccent)
                            if (state.loadingMessage.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(state.loadingMessage, color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
                state.error != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, tint = LiveRed, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(state.error ?: "Unknown error", color = LiveRed, fontSize = 13.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.dismissError() },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                            ) { Text("Dismiss") }
                        }
                    }
                }
                state.channels.isEmpty() -> {
                    EmptyState(
                        onLoadUrl = { url -> viewModel.loadPlaylistFromUrl(url) },
                        onLoadFile = { filePicker.launch(arrayOf("*/*", "audio/x-mpegurl", "application/vnd.apple.mpegurl")) }
                    )
                }
                else -> {
                    EpgGrid(
                        channels = state.channels,
                        programmes = state.programmes,
                        onChannelClick = { channel ->
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_CHANNEL, channel)
                                putParcelableArrayListExtra(PlayerActivity.EXTRA_CHANNEL_LIST, ArrayList(state.channels))
                            }
                            context.startActivity(intent)
                        },
                        onProgrammeClick = { channel, _ ->
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_CHANNEL, channel)
                                putParcelableArrayListExtra(PlayerActivity.EXTRA_CHANNEL_LIST, ArrayList(state.channels))
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // Group options modal
        if (state.showGroupOptions) {
            GroupOptionsModal(
                groupName = state.groupOptionsTarget,
                onDismiss = viewModel::dismissGroupOptions,
                onHide = { viewModel.hideGroup(state.groupOptionsTarget) },
                onRename = { newName -> viewModel.renameGroup(state.groupOptionsTarget, newName) }
            )
        }
    }
}

@Composable
fun HeroBanner(
    channel: Channel?,
    programme: Programme?,
    onChannelClick: (Channel) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(NavySurface)
    ) {
        // Background image (blurred backdrop)
        if (channel?.logoUrl?.isNotEmpty() == true) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.15f
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to NavySurface.copy(alpha = 0.95f),
                        0.6f to NavySurface.copy(alpha = 0.7f),
                        1f to Color.Transparent
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            // Left: programme info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Channel logo
                if (channel?.logoUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.height(36.dp).widthIn(max = 100.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Programme title
                Text(
                    text = programme?.title ?: channel?.name ?: "No programme info",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Live badge + time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (programme?.isLive == true) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(LiveRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "LIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    programme?.let {
                        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Text(
                            text = "${fmt.format(it.startTime)} – ${fmt.format(it.endTime)}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }

                programme?.description?.let { desc ->
                    if (desc.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Progress bar
                programme?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { it.progressFraction },
                        modifier = Modifier.width(200.dp).height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = BlueAccent,
                        trackColor = NavyBorder
                    )
                }
            }

            // Right: live thumbnail
            channel?.let {
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyBorder)
                        .clickable { onChannelClick(it) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GroupTabRow(
    groups: List<String>,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    onGroupLongPress: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyMid)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val isSelected = group == selectedGroup
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) NavySelected else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) BlueAccent else NavyBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onGroupSelected(group) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) TextPrimary else TextSecondary
                )
            }
        }
    }
}

@Composable
fun EpgGrid(
    channels: List<Channel>,
    programmes: Map<String, List<Programme>>,
    onChannelClick: (Channel) -> Unit,
    onProgrammeClick: (Channel, Programme) -> Unit
) {
    val now = System.currentTimeMillis()
    val windowStart = now - 30 * 60 * 1000L
    val windowEnd = now + 4 * 60 * 60 * 1000L
    val timeSlots = generateTimeSlots(windowStart, windowEnd)

    val horizontalScrollState = rememberScrollState()

    // Time header + grid rows share the same horizontal scroll
    Column(modifier = Modifier.fillMaxSize()) {
        // Time header row
        Row(modifier = Modifier.fillMaxWidth()) {
            // Fixed empty corner
            Spacer(modifier = Modifier.width(CHANNEL_COL_WIDTH))
            // Time labels scrollable
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
            ) {
                timeSlots.forEach { slotMs ->
                    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Box(
                        modifier = Modifier
                            .width(TIME_SLOT_WIDTH)
                            .height(TIME_HEADER_HEIGHT)
                            .background(NavyDeep)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = fmt.format(Date(slotMs)),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Channel rows
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(channels) { index, channel ->
                val channelProgrammes = programmes[channel.id] ?: emptyList()
                EpgRow(
                    channel = channel,
                    programmes = channelProgrammes,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    progressColor = ProgressColors[index % ProgressColors.size],
                    horizontalScrollState = horizontalScrollState,
                    onChannelClick = { onChannelClick(channel) },
                    onProgrammeClick = { prog -> onProgrammeClick(channel, prog) }
                )
                Divider(color = NavyBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun EpgRow(
    channel: Channel,
    programmes: List<Programme>,
    windowStart: Long,
    windowEnd: Long,
    progressColor: Color,
    horizontalScrollState: ScrollState,
    onChannelClick: () -> Unit,
    onProgrammeClick: (Programme) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fixed channel column
        Row(
            modifier = Modifier
                .width(CHANNEL_COL_WIDTH)
                .fillMaxHeight()
                .background(NavyDeep)
                .clickable { onChannelClick() }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = channel.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Scrollable programme blocks
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(horizontalScrollState)
        ) {
            val windowDuration = windowEnd - windowStart
            val totalWidth = TIME_SLOT_WIDTH * (windowDuration / (30 * 60 * 1000f))

            Box(modifier = Modifier.width(totalWidth).fillMaxHeight()) {
                if (programmes.isEmpty()) {
                    // No EPG — show single placeholder block
                    ProgrammeBlock(
                        title = "No programme info",
                        widthFraction = 1f,
                        offsetFraction = 0f,
                        totalWidth = totalWidth,
                        progressFraction = 0f,
                        progressColor = progressColor,
                        isLive = false,
                        onClick = {}
                    )
                } else {
                    programmes.forEach { prog ->
                        val clampedStart = max(prog.startTime, windowStart)
                        val clampedEnd = minOf(prog.endTime, windowEnd)
                        if (clampedEnd <= clampedStart) return@forEach

                        val offsetFrac = (clampedStart - windowStart).toFloat() / windowDuration
                        val widthFrac = (clampedEnd - clampedStart).toFloat() / windowDuration

                        ProgrammeBlock(
                            title = prog.title,
                            widthFraction = widthFrac,
                            offsetFraction = offsetFrac,
                            totalWidth = totalWidth,
                            progressFraction = if (prog.isLive) prog.progressFraction else 0f,
                            progressColor = progressColor,
                            isLive = prog.isLive,
                            onClick = { onProgrammeClick(prog) }
                        )
                    }
                }

                // "Now" line
                val nowOffset = (System.currentTimeMillis() - windowStart).toFloat() / windowDuration
                if (nowOffset in 0f..1f) {
                    Box(
                        modifier = Modifier
                            .offset(x = totalWidth * nowOffset)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(BlueAccent)
                    )
                }
            }
        }
    }
}

@Composable
fun ProgrammeBlock(
    title: String,
    widthFraction: Float,
    offsetFraction: Float,
    totalWidth: androidx.compose.ui.unit.Dp,
    progressFraction: Float,
    progressColor: Color,
    isLive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .absoluteOffset(x = totalWidth * offsetFraction)
            .width(totalWidth * widthFraction - 2.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(NavySurface)
            .border(0.5.dp, NavyBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = if (isLive) TextPrimary else TextSecondary,
                fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            // Progress bar at bottom of block
            if (progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = progressColor,
                    trackColor = NavyBorder
                )
            }
        }
    }
}

@Composable
fun EmptyState(onLoadUrl: (String) -> Unit, onLoadFile: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var playlistName by remember { mutableStateOf("My Playlist") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Tv, null, tint = TextMuted, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("No playlist loaded", color = TextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text("Load an M3U playlist to get started", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Load from URL")
                }
                OutlinedButton(
                    onClick = onLoadFile,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Local File")
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = NavySurface,
            title = { Text("Load M3U Playlist", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist name", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueAccent, unfocusedBorderColor = NavyBorder,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = BlueAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("M3U URL", color = TextSecondary) },
                        placeholder = { Text("http://...", color = TextMuted) },
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
                    if (url.isNotEmpty()) { onLoadUrl(url); showDialog = false }
                }) { Text("Load", color = BlueAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

private fun generateTimeSlots(from: Long, to: Long): List<Long> {
    val slots = mutableListOf<Long>()
    val interval = 30 * 60 * 1000L // 30 min
    // Round to nearest 30min
    var t = (from / interval) * interval
    while (t <= to) {
        if (t >= from) slots.add(t)
        t += interval
    }
    return slots
}
