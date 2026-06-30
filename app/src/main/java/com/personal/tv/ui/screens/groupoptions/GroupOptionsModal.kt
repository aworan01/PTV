package com.personal.tv.ui.screens.groupoptions

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.tv.ui.theme.*

@Composable
fun GroupOptionsModal(
    groupName: String,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onRename: (String) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(groupName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyMid.copy(alpha = 0.92f))
                .clickable(indication = null, interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                }) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .verticalScroll(rememberScrollState())
                    .clickable(indication = null, interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    }) { /* consume clicks */ }
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        text = "Group options",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = groupName,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }

                // Name section
                SectionCard(title = "Name") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current name", fontSize = 12.sp, color = TextSecondary)
                            Text(groupName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedActionButton(
                                text = "Rename",
                                isPrimary = true,
                                onClick = { showRenameDialog = true }
                            )
                            OutlinedActionButton(
                                text = "Smart rename",
                                isPrimary = false,
                                onClick = { onRename(groupName.replace(Regex("[^A-Za-z0-9 ]"), "").trim()) }
                            )
                            OutlinedActionButton(
                                text = "Restore",
                                isPrimary = false,
                                onClick = { onRename(groupName) }
                            )
                        }
                    }
                }

                // Group section
                SectionCard(title = "Group") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionTile(
                            title = "Lock",
                            subtitle = "Control access to this group",
                            modifier = Modifier.weight(1f),
                            onClick = { }
                        )
                        OptionTile(
                            title = "Move",
                            subtitle = "Grab and slide it in the row",
                            modifier = Modifier.weight(1f),
                            onClick = { }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OptionTile(
                        title = "Hide group",
                        subtitle = "Remove it from the guide row",
                        titleColor = LiveRed,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        onClick = { onHide() }
                    )
                }

                // Channels section
                SectionCard(title = "Channels") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionTile(
                            title = "Choose visible",
                            subtitle = "Toggle individual channels",
                            modifier = Modifier.weight(1f),
                            onClick = { }
                        )
                        OptionTile(
                            title = "Show hidden",
                            subtitle = "Restore hidden channels here",
                            modifier = Modifier.weight(1f),
                            onClick = { }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionTile(
                            title = "Auto-rename",
                            subtitle = "Clean provider prefixes and tags",
                            modifier = Modifier.weight(1f),
                            onClick = { }
                        )
                        OptionTile(
                            title = "Restore names",
                            subtitle = "Use original provider names",
                            modifier = Modifier.weight(1f),
                            onClick = { }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = NavySurface,
            title = { Text("Rename Group", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BlueAccent,
                        unfocusedBorderColor = NavyBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = BlueAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(renameText)
                    showRenameDialog = false
                }) { Text("Rename", color = BlueAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NavySurface)
            .border(0.5.dp, NavyBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        content()
    }
}

@Composable
fun OutlinedActionButton(text: String, isPrimary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.5.dp,
                color = if (isPrimary) BlueAccent else NavyBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isPrimary) NavySelected else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}

@Composable
fun OptionTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NavyMid)
            .border(0.5.dp, NavyBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
    }
}
