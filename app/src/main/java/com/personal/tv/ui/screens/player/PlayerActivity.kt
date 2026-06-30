package com.personal.tv.ui.screens.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.personal.tv.data.model.Channel
import com.personal.tv.data.model.Programme
import com.personal.tv.data.model.StreamInfo
import com.personal.tv.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    companion object {
        const val EXTRA_CHANNEL      = "extra_channel"
        const val EXTRA_CHANNEL_LIST = "extra_channel_list"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val channel = intent.getParcelableExtra<Channel>(EXTRA_CHANNEL)
        @Suppress("UNCHECKED_CAST")
        val channelList = intent.getParcelableArrayListExtra<Channel>(EXTRA_CHANNEL_LIST)
            ?: (channel?.let { arrayListOf(it) } ?: arrayListOf())

        setContent {
            PersonalTVTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onBrowserHandlersReady = { handlers -> browserHandlers = handlers }
                )
            }
        }

        channel?.let { viewModel.initWithChannel(it, channelList) }
    }

    // ── D-pad / remote key handling ───────────────────────────────────
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.uiState.value

        // When the channel browser is open, route all D-pad input to it
        if (state.showChannelBrowser) {
            val handlers = browserHandlers
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP    -> { handlers?.onUp?.invoke(); true }
                KeyEvent.KEYCODE_DPAD_DOWN  -> { handlers?.onDown?.invoke(); true }
                KeyEvent.KEYCODE_DPAD_LEFT  -> { handlers?.onLeft?.invoke(); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { handlers?.onRight?.invoke(); true }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER      -> { handlers?.onOk?.invoke(); true }
                KeyEvent.KEYCODE_BACK       -> { handlers?.onBack?.invoke() ?: viewModel.hideChannelBrowser(); true }
                else -> super.onKeyDown(keyCode, event)
            }
        }

        // Normal playback key handling
        return when (keyCode) {
            // LEFT opens the channel browser (replaces old previous-channel behaviour)
            KeyEvent.KEYCODE_DPAD_LEFT -> { viewModel.showChannelBrowser(); true }

            // RIGHT is reserved (was next-channel; no longer auto-switches)
            KeyEvent.KEYCODE_DPAD_RIGHT -> { viewModel.showOverlay(); true }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER      -> { viewModel.togglePlayPause(); true }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN  -> { viewModel.showOverlay(); true }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { viewModel.togglePlayPause(); true }

            KeyEvent.KEYCODE_BACK -> {
                if (state.showOverlay || state.showTrackMenu) {
                    viewModel.hideOverlay(); true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private var browserHandlers: BrowserKeyHandlers? = null

    override fun onPause()  { super.onPause();  viewModel.player.pause() }
    override fun onResume() { super.onResume(); viewModel.player.play()  }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onBrowserHandlersReady: (BrowserKeyHandlers) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Video surface ────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = false
                    resizeMode = when (state.aspectRatio) {
                        AspectRatioMode.FILL    -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        else                    -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Buffering spinner ────────────────────────────────────────
        if (state.isBuffering && state.error == null) {
            CircularProgressIndicator(
                color = BlueAccent,
                modifier = Modifier.align(Alignment.Center).size(52.dp)
            )
        }

        // ── Error ────────────────────────────────────────────────────
        state.error?.let { err ->
            Box(
                modifier = Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayerOverlay)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = LiveRed, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.widthIn(max = 320.dp))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)) {
                        Text("Retry")
                    }
                }
            }
        }

        // ── Channel info overlay (bottom bar) ────────────────────────
        AnimatedVisibility(
            visible = state.showOverlay && state.error == null && !state.showChannelBrowser,
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerOverlayBar(
                state = state,
                onBack = onBack,
                onTogglePlayPause = viewModel::togglePlayPause,
                onOpenTrackMenu = viewModel::toggleTrackMenu
            )
        }

        // ── Track/Settings side panel ────────────────────────────────
        AnimatedVisibility(
            visible = state.showTrackMenu && !state.showChannelBrowser,
            enter = fadeIn() + slideInHorizontally { it },
            exit  = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 160.dp, end = 24.dp)
        ) {
            TrackMenuPanel(state = state, viewModel = viewModel)
        }

        // ── Channel browser (Left key) ────────────────────────────────
        if (state.showChannelBrowser) {
            val currentGroup = state.channel?.groupTitle?.ifEmpty { "Uncategorised" } ?: ""
            ChannelBrowserOverlay(
                groups = state.allGroups,
                channelsByGroup = state.channelsByGroup,
                currentGroup = currentGroup,
                currentChannelId = state.channel?.id ?: "",
                onGroupSelected = { /* selection handled internally; no extra action needed */ },
                onPlayChannel = { channel -> viewModel.playChannelFromBrowser(channel) },
                onDismiss = { viewModel.hideChannelBrowser() },
                onHandlersReady = onBrowserHandlersReady
            )
        }
    }
}

@Composable
fun PlayerOverlayBar(
    state: PlayerUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenTrackMenu: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.2f to PlayerOverlay
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Row 1: channel logo | channel number | name ── stream pills | LIVE ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel logo
                val logo = state.channel?.logoUrl ?: ""
                if (logo.isNotEmpty()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NavySurface)
                    )
                    Spacer(Modifier.width(10.dp))
                }

                // Channel number pill
                val chno = state.channel?.tvgChno ?: ""
                if (chno.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BlueAccent)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(chno, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(Modifier.width(10.dp))
                }

                // Channel name
                Text(
                    text = state.channel?.name ?: "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.width(16.dp))

                // Stream info pills
                val info = state.streamInfo
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (info.resolutionLabel.isNotEmpty()) InfoPill(info.resolutionLabel)
                    if (info.fpsLabel.isNotEmpty()) InfoPill(info.fpsLabel)
                    if (info.videoDecoderLabel.isNotEmpty()) InfoPill(info.videoDecoderLabel)
                    if (info.audioDecoderLabel.isNotEmpty()) InfoPill(info.audioDecoderLabel)
                    if (info.audioChannels.isNotEmpty()) InfoPill(info.audioChannels)

                    // LIVE pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(LiveRed)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TextPrimary))
                            Spacer(Modifier.width(4.dp))
                            Text("Live", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            // Group / category
            state.channel?.groupTitle?.let { group ->
                if (group.isNotEmpty()) {
                    Text(group, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Row 2: programme info ────────────────────────────────
            state.currentProgramme?.let { prog ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(prog.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        // Progress bar with time labels
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(timeFmt.format(Date(prog.startTime)), fontSize = 11.sp, color = TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                LinearProgressIndicator(
                                    progress = { prog.progressFraction },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                    color = BlueAccent,
                                    trackColor = NavyBorder
                                )
                                // "X min left" label centred
                                Text(
                                    text = "${prog.minutesLeft} min left",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.align(Alignment.Center).padding(top = 6.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(timeFmt.format(Date(prog.endTime)), fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    // Next programme card
                    state.nextProgramme?.let { next ->
                        Spacer(Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NavySurface)
                                .padding(10.dp)
                        ) {
                            Text("Next", fontSize = 10.sp, color = TextSecondary)
                            Text(next.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(timeFmt.format(Date(next.startTime)), fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Row 3: control hints ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ControlHint("◄", "Channels")
                    ControlHint("OK", if (state.isPlaying) "Pause" else "Play")
                }
                // Track/Settings button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavySurface)
                        .clickable { onOpenTrackMenu() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tracks & Settings", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackMenuPanel(state: PlayerUiState, viewModel: PlayerViewModel) {
    Box(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerOverlay)
    ) {
        when (state.trackMenuPage) {
            TrackMenuPage.MAIN -> Column(Modifier.padding(4.dp)) {
                TrackMenuRow(Icons.Default.VolumeUp,    "Audio",       Icons.Default.ChevronRight) { viewModel.navigateTrackMenu(TrackMenuPage.AUDIO) }
                TrackMenuDivider()
                TrackMenuRow(Icons.Default.Subtitles,   "Subtitles",   Icons.Default.ChevronRight) { viewModel.navigateTrackMenu(TrackMenuPage.SUBTITLES) }
                TrackMenuDivider()
                TrackMenuRow(Icons.Default.Settings,    "Settings",    Icons.Default.ChevronRight) { viewModel.navigateTrackMenu(TrackMenuPage.SETTINGS) }
            }
            TrackMenuPage.AUDIO -> Column(Modifier.padding(4.dp)) {
                TrackMenuBack("Audio") { viewModel.navigateTrackMenu(TrackMenuPage.MAIN) }
                if (state.audioTracks.isEmpty()) {
                    Text("No audio tracks", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    state.audioTracks.forEach { track ->
                        val sel = state.selectedAudioTrack == track.index
                        TrackMenuRow(
                            if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            track.label, null, if (sel) BlueAccent else TextPrimary
                        ) { viewModel.selectAudioTrack(track.index) }
                    }
                }
            }
            TrackMenuPage.SUBTITLES -> Column(Modifier.padding(4.dp)) {
                TrackMenuBack("Subtitles") { viewModel.navigateTrackMenu(TrackMenuPage.MAIN) }
                TrackMenuRow(
                    if (state.selectedSubtitleTrack == -1) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    "Off", null, if (state.selectedSubtitleTrack == -1) BlueAccent else TextPrimary
                ) { viewModel.selectSubtitleTrack(-1) }
                state.subtitleTracks.forEach { track ->
                    val sel = state.selectedSubtitleTrack == track.index
                    TrackMenuRow(
                        if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        track.label, null, if (sel) BlueAccent else TextPrimary
                    ) { viewModel.selectSubtitleTrack(track.index) }
                }
            }
            TrackMenuPage.SETTINGS -> Column(Modifier.padding(4.dp)) {
                TrackMenuBack("Settings") { viewModel.navigateTrackMenu(TrackMenuPage.MAIN) }
                TrackMenuRow(Icons.Default.AspectRatio,  "Aspect Ratio",         Icons.Default.ChevronRight) { viewModel.navigateTrackMenu(TrackMenuPage.ASPECT) }
                TrackMenuDivider()
                TrackMenuRow(Icons.Default.NetworkCheck, "Network Buffer",        Icons.Default.ChevronRight) { viewModel.navigateTrackMenu(TrackMenuPage.BUFFER) }
                TrackMenuDivider()
                TrackMenuRow(Icons.Default.Tune,         "Deinterlacing",        Icons.Default.ChevronRight) { viewModel.navigateTrackMenu(TrackMenuPage.DEINTERLACE) }
                TrackMenuDivider()
                TrackMenuRow(Icons.Default.Memory,       "Hardware Acceleration", Icons.Default.ChevronRight) { viewModel.navigateTrackMenu(TrackMenuPage.HW) }
            }
            TrackMenuPage.ASPECT -> Column(Modifier.padding(4.dp)) {
                TrackMenuBack("Aspect Ratio") { viewModel.navigateTrackMenu(TrackMenuPage.SETTINGS) }
                AspectRatioMode.values().forEach { mode ->
                    val sel = state.aspectRatio == mode
                    TrackMenuRow(
                        if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        mode.name.replace("_", ":"), null, if (sel) BlueAccent else TextPrimary
                    ) { viewModel.setAspectRatio(mode) }
                }
            }
            TrackMenuPage.BUFFER -> Column(Modifier.padding(4.dp)) {
                TrackMenuBack("Network Buffer") { viewModel.navigateTrackMenu(TrackMenuPage.SETTINGS) }
                BufferMode.values().forEach { mode ->
                    val sel = state.bufferMode == mode
                    TrackMenuRow(
                        if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        mode.name, null, if (sel) BlueAccent else TextPrimary
                    ) { viewModel.setBufferMode(mode) }
                }
            }
            TrackMenuPage.DEINTERLACE -> Column(Modifier.padding(4.dp)) {
                TrackMenuBack("Deinterlacing") { viewModel.navigateTrackMenu(TrackMenuPage.SETTINGS) }
                DeinterlaceMode.values().forEach { mode ->
                    val sel = state.deinterlaceMode == mode
                    TrackMenuRow(
                        if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        mode.name, null, if (sel) BlueAccent else TextPrimary
                    ) { viewModel.setDeinterlaceMode(mode) }
                }
            }
            TrackMenuPage.HW -> Column(Modifier.padding(4.dp)) {
                TrackMenuBack("HW Acceleration") { viewModel.navigateTrackMenu(TrackMenuPage.SETTINGS) }
                listOf(true to "Enabled", false to "Disabled").forEach { (v, label) ->
                    val sel = state.hwAccelEnabled == v
                    TrackMenuRow(
                        if (sel) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        label, null, if (sel) BlueAccent else TextPrimary
                    ) { viewModel.setHwAccel(v) }
                }
            }
        }
    }
}

// ── Small reusable components ─────────────────────────────────────────

@Composable
fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PillBackground)
            .border(0.5.dp, PillBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary) }
}

@Composable
fun ControlHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(NavySurface)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) { Text(key, fontSize = 10.sp, color = TextSecondary) }
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
fun TrackMenuRow(leadIcon: ImageVector, text: String, trailIcon: ImageVector?,
                 tint: Color = TextPrimary, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(leadIcon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = tint, modifier = Modifier.weight(1f))
        trailIcon?.let { Icon(it, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun TrackMenuBack(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onBack() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ChevronLeft, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
    Divider(color = NavyBorder)
}

@Composable
fun TrackMenuDivider() = Divider(color = NavyBorder.copy(alpha = 0.4f), thickness = 0.5.dp)
