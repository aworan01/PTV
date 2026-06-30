package com.personal.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.personal.tv.data.model.Channel
import com.personal.tv.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Two-column channel browser overlay shown when the user presses LEFT while playing.
 * Left column: categories (groups) with channel counts.
 * Right column: channels within the selected category, with channel number, logo, and name.
 *
 * Navigation:
 *  - Up/Down moves the highlighted row in the focused column
 *  - Right moves focus from categories -> channels
 *  - Left moves focus from channels -> categories, or closes the browser if already on categories
 *  - OK plays the highlighted channel immediately and closes the browser
 *  - Back closes the browser without changing channel
 */
@Composable
fun ChannelBrowserOverlay(
    groups: List<String>,
    channelsByGroup: Map<String, List<Channel>>,
    currentGroup: String,
    currentChannelId: String,
    onGroupSelected: (String) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onDismiss: () -> Unit,
    onHandlersReady: (BrowserKeyHandlers) -> Unit
) {
    var focusColumn by remember { mutableStateOf(BrowserColumn.CATEGORIES) }
    var selectedGroupIndex by remember(groups) {
        mutableStateOf(groups.indexOf(currentGroup).coerceAtLeast(0))
    }
    val selectedGroup = groups.getOrNull(selectedGroupIndex) ?: currentGroup
    val channels = channelsByGroup[selectedGroup] ?: emptyList()

    var selectedChannelIndex by remember(selectedGroup) {
        mutableStateOf(channels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0))
    }

    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(focusColumn, selectedGroupIndex, selectedChannelIndex, channels) {
        onHandlersReady(
            BrowserKeyHandlers(
                onUp = {
                    when (focusColumn) {
                        BrowserColumn.CATEGORIES -> if (selectedGroupIndex > 0) {
                            selectedGroupIndex--
                            scope.launch { categoryListState.animateScrollToItem(maxOf(0, selectedGroupIndex - 2)) }
                        }
                        BrowserColumn.CHANNELS -> if (selectedChannelIndex > 0) {
                            selectedChannelIndex--
                            scope.launch { channelListState.animateScrollToItem(maxOf(0, selectedChannelIndex - 2)) }
                        }
                    }
                },
                onDown = {
                    when (focusColumn) {
                        BrowserColumn.CATEGORIES -> if (selectedGroupIndex < groups.size - 1) {
                            selectedGroupIndex++
                            scope.launch { categoryListState.animateScrollToItem(maxOf(0, selectedGroupIndex - 2)) }
                        }
                        BrowserColumn.CHANNELS -> if (selectedChannelIndex < channels.size - 1) {
                            selectedChannelIndex++
                            scope.launch { channelListState.animateScrollToItem(maxOf(0, selectedChannelIndex - 2)) }
                        }
                    }
                },
                onLeft = {
                    if (focusColumn == BrowserColumn.CHANNELS) {
                        focusColumn = BrowserColumn.CATEGORIES
                    } else {
                        onDismiss()
                    }
                },
                onRight = {
                    if (focusColumn == BrowserColumn.CATEGORIES) {
                        focusColumn = BrowserColumn.CHANNELS
                        onGroupSelected(selectedGroup)
                    }
                },
                onOk = {
                    if (focusColumn == BrowserColumn.CATEGORIES) {
                        focusColumn = BrowserColumn.CHANNELS
                        onGroupSelected(selectedGroup)
                    } else {
                        channels.getOrNull(selectedChannelIndex)?.let { onPlayChannel(it) }
                    }
                },
                onBack = onDismiss
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.92f)
                .align(Alignment.TopStart)
                .background(NavyDeep.copy(alpha = 0.97f))
        ) {
            // ── Categories column ─────────────────────────────────────
            Column(
                modifier = Modifier.width(320.dp).fillMaxHeight().background(NavyMid.copy(alpha = 0.9f))
            ) {
                Text("Categories", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    modifier = Modifier.padding(20.dp))
                LazyColumn(
                    state = categoryListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(groups) { index, group ->
                        val count = channelsByGroup[group]?.size ?: 0
                        CategoryRow(
                            name = group, count = count,
                            isSelected = index == selectedGroupIndex,
                            isFocused = focusColumn == BrowserColumn.CATEGORIES && index == selectedGroupIndex,
                            onClick = {
                                selectedGroupIndex = index
                                focusColumn = BrowserColumn.CHANNELS
                                onGroupSelected(group)
                            }
                        )
                    }
                }
            }

            // ── Channels column ───────────────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedGroup, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(BlueAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("${channels.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BlueAccent) }
                }
                LazyColumn(
                    state = channelListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(channels) { index, channel ->
                        ChannelRow(
                            channel = channel,
                            isSelected = index == selectedChannelIndex,
                            isFocused = focusColumn == BrowserColumn.CHANNELS && index == selectedChannelIndex,
                            isPlaying = channel.id == currentChannelId,
                            onClick = {
                                selectedChannelIndex = index
                                onPlayChannel(channel)
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.92f)
                .background(NavyDeep.copy(alpha = 0.97f))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HintChip("◄", "menu")
            HintChip("OK", "select")
            HintChip("►", "channels")
        }
    }
}

enum class BrowserColumn { CATEGORIES, CHANNELS }

data class BrowserKeyHandlers(
    val onUp: () -> Unit,
    val onDown: () -> Unit,
    val onLeft: () -> Unit,
    val onRight: () -> Unit,
    val onOk: () -> Unit,
    val onBack: () -> Unit
)

@Composable
fun CategoryRow(name: String, count: Int, isSelected: Boolean, isFocused: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) NavySelected else Color.Transparent)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) BlueAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name, fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) BlueAccent else TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("$count channels", fontSize = 11.sp, color = TextSecondary)
        }
        if (isSelected) Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BlueAccent))
    }
}

@Composable
fun ChannelRow(channel: Channel, isSelected: Boolean, isFocused: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NavySelected else NavySurface.copy(alpha = 0.5f))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) BlueAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isPlaying) BlueAccent.copy(alpha = 0.25f) else NavyBorder)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                channel.tvgChno.ifEmpty { "—" }, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (isPlaying) BlueAccent else TextSecondary
            )
        }

        Spacer(Modifier.width(10.dp))

        if (channel.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = channel.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        if (isPlaying) Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
    }
}

@Composable
fun HintChip(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(NavySurface)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) { Text(key, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
        Text(label, fontSize = 13.sp, color = TextSecondary)
    }
}
