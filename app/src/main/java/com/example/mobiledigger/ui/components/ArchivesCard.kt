@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.mobiledigger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobiledigger.file.archives.ArchiveItem
import java.util.Locale

private val Amber = Color(0xFFF59E0B)

private fun sizeLabel(b: Long): String = when {
    b <= 0 -> ""
    b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", b / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.0f KB", b / 1024.0)
}

/**
 * One archive (.zip / .rar / .7z) shown inside its folder in the To Do list.
 * Unpack = extract next to it, then delete it. Delete = remove it without extracting.
 */
@Composable
fun ArchiveRow(
    archive: ArchiveItem,
    readOnly: Boolean,
    /** null = idle; otherwise "Queued" or "Unpacking 42%" for this archive. */
    state: String?,
    /** Opens the contents popup (preview, pick files, extract). */
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    /** Multi-select: show a checkbox; tapping the row toggles it. */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var ask by remember { mutableStateOf<String?>(null) } // "delete"
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) Amber.copy(alpha = 0.22f) else Amber.copy(alpha = 0.08f), label = "archSel"
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                onLongClick = onToggleSelect
            )
            .padding(start = if (selectionMode) 2.dp else 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.animation.AnimatedVisibility(selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        }
        Text(
            archive.type, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Amber,
            modifier = Modifier.border(1.dp, Amber, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(archive.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(state, sizeLabel(archive.size).ifBlank { null }, if (readOnly) "read-only drive" else null).joinToString(" · ")
                    .ifBlank { "archive" },
                fontSize = 11.sp,
                color = if (state != null) Amber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val busy = state != null
        if (!selectionMode) {
        TextButton(onClick = onOpen, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Default.Unarchive, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Unpack", fontSize = 13.sp)
        }
        IconButton(onClick = { ask = "delete" }, enabled = !readOnly && !busy) {
            Icon(Icons.Default.DeleteOutline, "Delete ${archive.name}", tint = if (!readOnly && !busy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        } else Spacer(Modifier.width(12.dp))
    }

    when (ask) {
        "delete" -> AlertDialog(
            onDismissRequest = { ask = null },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${archive.name}?") },
            text = { Text("The archive is deleted without unpacking it. This can't be undone.") },
            confirmButton = {
                Button(onClick = { ask = null; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Cancel") } }
        )
    }
}

/** Slim progress strip shown at the top of the list while an archive is being unpacked. */
@Composable
fun UnpackProgressStrip(status: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Amber.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)), color = Amber)
        Spacer(Modifier.height(5.dp))
        Text("Unpacking $status", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Popup listing everything inside an archive. The user picks what to extract (everything is
 * ticked to start with) and whether the archive is deleted afterwards.
 */
@Composable
fun ArchiveContentsDialog(
    archive: ArchiveItem,
    readOnly: Boolean,
    loader: suspend () -> Result<List<com.example.mobiledigger.file.archives.ArchiveEntry>>,
    onExtract: (include: Set<String>?, deleteArchive: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<com.example.mobiledigger.file.archives.ArchiveEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteAfter by remember { mutableStateOf(true) }
    LaunchedEffect(archive.uri) {
        loader().onSuccess { list -> entries = list; selected = list.map { it.path }.toSet() }
            .onFailure { error = it.message ?: "Couldn't read this archive" }
    }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val list = entries.orEmpty()
    val allSelected = list.isNotEmpty() && selected.size == list.size

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().heightIn(max = (config.screenHeightDp - 110).dp).padding(12.dp),
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Header
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp), verticalAlignment = Alignment.Top) {
                    Text(archive.type, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Amber,
                        modifier = Modifier.padding(top = 4.dp).border(1.dp, Amber, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(archive.name, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val audio = list.count { it.isAudio }
                        val total = list.sumOf { it.size }
                        Text(
                            if (entries == null && error == null) "Reading the contents…"
                            else listOfNotNull(
                                "${list.size} file${if (list.size == 1) "" else "s"}",
                                "$audio audio".takeIf { list.isNotEmpty() },
                                sizeLabel(total).ifBlank { null }
                            ).joinToString(" · "),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(androidx.compose.material.icons.Icons.Default.Close, "Close") }
                }

                // Select all / audio only
                if (list.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TriStateCheckbox(
                            state = when {
                                allSelected -> androidx.compose.ui.state.ToggleableState.On
                                selected.isEmpty() -> androidx.compose.ui.state.ToggleableState.Off
                                else -> androidx.compose.ui.state.ToggleableState.Indeterminate
                            },
                            onClick = { selected = if (allSelected) emptySet() else list.map { it.path }.toSet() }
                        )
                        Text("Select all", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        if (list.any { !it.isAudio } && list.any { it.isAudio }) {
                            AssistChip(onClick = { selected = list.filter { it.isAudio }.map { it.path }.toSet() },
                                label = { Text("Audio only", fontSize = 12.sp) })
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${selected.size} of ${list.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp))
                    }
                    HorizontalDivider()
                }

                // Contents
                Box(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 120.dp)) {
                    when {
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                        entries == null -> Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        list.isEmpty() -> Text("This archive is empty.", modifier = Modifier.padding(20.dp))
                        else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                            val byFolder = list.groupBy { it.folder }.toSortedMap()
                            byFolder.forEach { (folder, files) ->
                                if (folder.isNotEmpty()) item(key = "f_$folder") {
                                    Text("📁 $folder", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp))
                                }
                                files.sortedBy { it.name.lowercase() }.forEach { e ->
                                    item(key = e.path) {
                                        val on = e.path in selected
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clickable { selected = if (on) selected - e.path else selected + e.path }
                                                .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = on, onCheckedChange = { selected = if (on) selected - e.path else selected + e.path })
                                            Icon(
                                                if (e.isAudio) androidx.compose.material.icons.Icons.Default.MusicNote
                                                else androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile,
                                                null, Modifier.size(18.dp),
                                                tint = if (e.isAudio) Amber else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(e.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Spacer(Modifier.width(8.dp))
                                            Text(sizeLabel(e.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (readOnly) {
                        Text("This drive is read-only, so nothing can be extracted here. Turn on Write access for it under Network drives.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Row(Modifier.fillMaxWidth().clickable { deleteAfter = !deleteAfter }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Delete the archive afterwards", style = MaterialTheme.typography.bodyMedium)
                                Text(if (deleteAfter) "Only once the files are out safely" else "The archive stays where it is",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = deleteAfter, onCheckedChange = { deleteAfter = it })
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Cancel") }
                        Button(
                            onClick = { onExtract(if (allSelected) null else selected, deleteAfter) },
                            enabled = !readOnly && selected.isNotEmpty(),
                            modifier = Modifier.weight(1.6f).height(48.dp), shape = RoundedCornerShape(14.dp)
                        ) { Text(if (allSelected) "Extract all" else "Extract ${selected.size}") }
                    }
                }
            }
        }
    }
}
