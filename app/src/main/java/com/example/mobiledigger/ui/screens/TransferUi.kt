package com.example.mobiledigger.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobiledigger.file.drives.DriveClients
import com.example.mobiledigger.file.drives.DriveType
import com.example.mobiledigger.file.drives.NetworkDrive
import com.example.mobiledigger.file.transfer.TransferJob
import com.example.mobiledigger.file.transfer.TransferManager
import com.example.mobiledigger.file.transfer.TransferStatus
import com.example.mobiledigger.ui.components.folderLabel
import com.example.mobiledigger.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copy or move [folder] of [drive] (USB or network) to a folder on the phone. */
@Composable
fun TransferSetupDialog(drive: NetworkDrive, folder: String, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var plan by remember { mutableStateOf<TransferManager.Plan?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var writable by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    var musicOnly by remember { mutableStateOf(false) }
    // Default destination: the phone folder already used as the music source
    var dest by remember { mutableStateOf(viewModel.preferences.getSourceRootUri()?.let { Uri.parse(it) }) }
    val busy = TransferManager.state.collectAsState().value.status == TransferStatus.RUNNING
    val folderName = folder.trim('/').substringAfterLast('/').ifBlank { drive.name }
    val isUsb = drive.type == DriveType.USB

    LaunchedEffect(drive.id, folder) {
        withContext(Dispatchers.IO) {
            runCatching {
                writable = TransferManager.canWrite(drive)
                TransferManager.scan(drive, folder)
            }.onSuccess { plan = it }.onFailure { scanError = it.message ?: "Couldn't read the folder" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            dest = uri
        }
    }
    val files = remember(plan, musicOnly) {
        plan?.files?.let { all -> if (musicOnly) all.filter { DriveClients.isAudio(it.rel) } else all }
    }
    val bytes = files?.sumOf { it.size } ?: 0L
    val leftOut = (plan?.files?.size ?: 0) - (files?.size ?: 0)
    val free = remember(dest) { dest?.let { TransferManager.freeBytes(context, it) } }
    val spaceProblem = remember(dest, bytes) { dest?.let { d -> files?.let { TransferManager.spaceProblem(context, d, bytes) } } }
    val canMove = writable
    val doMove = move && canMove

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (doMove) "Move to the phone" else "Copy to the phone") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // From → To
                RouteRow(
                    icon = if (isUsb) Icons.Default.Usb else Icons.Default.Lan,
                    caption = "From ${drive.name}", title = folderName,
                    detail = when {
                        scanError != null -> null
                        files == null -> "Counting files…"
                        else -> "${files.size} ${if (musicOnly) "music files" else "files"} · ${TransferManager.fmt(bytes)}" +
                            (if (leftOut > 0) " · $leftOut other files left out" else "")
                    },
                    loading = plan == null && scanError == null
                )
                Icon(Icons.Default.ArrowDownward, null, Modifier.padding(start = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedCard(onClick = { picker.launch(dest) }, shape = RoundedCornerShape(16.dp)) {
                    RouteRow(
                        icon = Icons.Default.PhoneAndroid, caption = "Into this phone folder",
                        title = dest?.let { folderLabel(it.toString()) } ?: "Pick a phone folder",
                        detail = if (dest != null) "A folder “$folderName” is made inside it" else null,
                        trailing = "Change", modifier = Modifier.padding(12.dp)
                    )
                }
                scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                // Space on the phone
                if (free != null && files != null) {
                    val after = (free - bytes).coerceAtLeast(0)
                    Column {
                        LinearProgressIndicator(
                            progress = { if (free > 0) (bytes.toFloat() / free).coerceIn(0f, 1f) else 1f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (spaceProblem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Needs ${TransferManager.fmt(bytes)} of ${TransferManager.fmt(free)} free · ${TransferManager.fmt(after)} left after",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (spaceProblem != null) Text(spaceProblem, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)

                // Copy / Move
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !doMove, onClick = { move = false }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { SegmentedButtonDefaults.Icon(!doMove) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) } }) { Text("Copy") }
                    SegmentedButton(selected = doMove, onClick = { move = true }, enabled = canMove, shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { SegmentedButtonDefaults.Icon(doMove) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, Modifier.size(16.dp)) } }) { Text("Move") }
                }
                Text(
                    when {
                        !canMove && isUsb -> "Move needs write access to ${drive.name}. Copy leaves the files on the drive."
                        !canMove -> "Move isn't available: ${drive.name} is read-only. Copy leaves the files on the drive."
                        doMove -> "Each file is deleted from ${drive.name} only after it's safely on the phone. Emptied folders are removed."
                        else -> "The files stay on ${drive.name} too."
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { musicOnly = !musicOnly }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Only music files", fontWeight = FontWeight.SemiBold)
                        Text("Leave out covers, playlists, text files…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = musicOnly, onCheckedChange = { musicOnly = it })
                }
                Text(
                    "Files already on the phone (same name and size) are skipped. A different file with the same name is never " +
                        "overwritten: the new one is saved as “name (1)”. It runs in the background — keep sorting meanwhile.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val f = files ?: return@Button
                    val d = dest ?: return@Button
                    TransferManager.start(
                        context, TransferJob(
                            driveId = drive.id, driveName = drive.name, sourceFolder = folder,
                            destTreeUri = d.toString(), destLabel = folderLabel(d.toString()) ?: "phone",
                            move = doMove, files = f, usb = isUsb
                        )
                    )
                    onDismiss()
                },
                enabled = !files.isNullOrEmpty() && dest != null && spaceProblem == null && !busy
            ) {
                Icon(if (doMove) Icons.AutoMirrored.Filled.DriveFileMove else Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (busy) "Another transfer is running" else if (doMove) "Move" else "Copy")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RouteRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector, caption: String, title: String, detail: String?,
    modifier: Modifier = Modifier, loading: Boolean = false, trailing: String? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) { CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp); Spacer(Modifier.width(6.dp)) }
                Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
    }
}

/** Slim progress banner on the main screen: Pause / Continue / Abort. */
@Composable
fun TransferBanner(onRescan: (() -> Unit)?) {
    val context = LocalContext.current
    val st by TransferManager.state.collectAsState()
    val job = st.job
    var confirmCancel by remember { mutableStateOf(false) }
    if (confirmCancel && job != null) {
        val verb = if (job.move) "move" else "copy"
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel the $verb?") },
            text = {
                Text("${job.doneFiles} of ${job.files.size} files are already on the phone and stay there. " +
                    "The file being copied right now is removed. " +
                    (if (job.move) "Files not moved yet stay on ${job.driveName}. " else "") +
                    "This can't be continued later.")
            },
            confirmButton = { TextButton(onClick = { confirmCancel = false; TransferManager.abort() }) {
                Text("Cancel $verb", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep going") } }
        )
    }
    AnimatedVisibility(visible = st.status != TransferStatus.IDLE && job != null, enter = expandVertically(), exit = shrinkVertically()) {
        if (job == null) return@AnimatedVisibility
        val blue = Color(0xFF3B82F6)
        Card(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 0.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (st.status) {
                    TransferStatus.PAUSED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    TransferStatus.DONE -> Color(0xFF2E7D32).copy(alpha = 0.25f)
                    else -> blue.copy(alpha = 0.20f)
                }
            )
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                val verb = if (job.move) "Moving" else "Copying"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (job.usb) Icons.Default.Usb else Icons.Default.Lan, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (st.status) {
                            TransferStatus.RUNNING -> "$verb “${job.folderName}” from ${job.driveName}"
                            TransferStatus.PAUSED -> "$verb “${job.folderName}” paused"
                            else -> st.message ?: "Done"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    if (st.status != TransferStatus.DONE) {
                        // Download speed + time left while running
                        if (st.status == TransferStatus.RUNNING && st.speedBps > 0) {
                            Text(
                                TransferManager.fmtSpeed(st.speedBps) + (st.etaSeconds?.let { " · " + TransferManager.fmtEta(it) } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("${(st.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (st.status != TransferStatus.DONE) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { st.fraction }, modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = if (st.status == TransferStatus.PAUSED) MaterialTheme.colorScheme.error else blue,
                        trackColor = blue.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${job.doneFiles}/${job.files.size} files · ${TransferManager.fmt(st.doneBytes)} of ${TransferManager.fmt(job.totalBytes)}" +
                            when {
                                st.status == TransferStatus.PAUSED && st.message != null -> " · ${st.message}"
                                st.status == TransferStatus.RUNNING && st.currentName.isNotEmpty() -> " · ${st.currentName}"
                                else -> ""
                            },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when (st.status) {
                        TransferStatus.RUNNING -> {
                            // Cancel stops for good (confirm first); Pause can be continued later
                            TextButton(onClick = { confirmCancel = true }) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { TransferManager.pause() }) { Text("Pause") }
                        }
                        TransferStatus.PAUSED -> {
                            TextButton(onClick = { confirmCancel = true }) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                            Button(onClick = { TransferManager.resume(context) }) { Text("Continue") }
                        }
                        else -> {
                            if (onRescan != null) TextButton(onClick = { TransferManager.dismiss(); onRescan() }) { Text("Rescan phone folder") }
                            TextButton(onClick = { TransferManager.dismiss() }) { Text("OK") }
                        }
                    }
                }
            }
        }
    }
}
