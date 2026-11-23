package com.example.mobiledigger.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
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
import com.example.mobiledigger.file.drives.NetworkDrive
import com.example.mobiledigger.file.transfer.TransferJob
import com.example.mobiledigger.file.transfer.TransferManager
import com.example.mobiledigger.file.transfer.TransferStatus
import com.example.mobiledigger.ui.components.folderLabel
import com.example.mobiledigger.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Copy or move [folder] of [drive] to a folder on the phone. */
@Composable
fun TransferSetupDialog(drive: NetworkDrive, folder: String, viewModel: MusicViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var plan by remember { mutableStateOf<TransferManager.Plan?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var writable by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    // Default destination: the phone folder already used as the music source
    var dest by remember { mutableStateOf(viewModel.preferences.getSourceRootUri()?.let { Uri.parse(it) }) }
    val busy = TransferManager.state.collectAsState().value.status == TransferStatus.RUNNING
    val folderName = folder.trim('/').substringAfterLast('/')

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
    val free = remember(dest) { dest?.let { TransferManager.freeBytes(context, it) } }
    val spaceProblem = remember(dest, plan) { dest?.let { d -> plan?.let { TransferManager.spaceProblem(context, d, it.totalBytes) } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Download, null) },
        title = { Text("Copy “$folderName” to the phone", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    scanError != null -> Text(scanError!!, color = MaterialTheme.colorScheme.error)
                    plan == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp)); Text("Counting files…")
                    }
                    else -> Text("${plan!!.files.size} files · ${TransferManager.fmt(plan!!.totalBytes)} (subfolders included)",
                        fontWeight = FontWeight.SemiBold)
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !move, onClick = { move = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Copy") }
                    SegmentedButton(selected = move, onClick = { move = true }, enabled = writable, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Move") }
                }
                Text(
                    when {
                        !writable -> "Move isn't available: ${drive.name} is read-only. Copy leaves the files on the drive."
                        move -> "Each file is deleted from ${drive.name} once it's safely on the phone."
                        else -> "The files stay on ${drive.name} too."
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedCard(onClick = { picker.launch(dest) }, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Save into", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dest?.let { folderLabel(it.toString()) } ?: "Pick a phone folder", fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (dest != null) Text("A folder “$folderName” is made inside it", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Change", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
                if (free != null) Text("Free on the phone: ${TransferManager.fmt(free)}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (spaceProblem != null) Text(spaceProblem, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Text("It runs in the background. You can keep sorting meanwhile.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = plan ?: return@Button
                    val d = dest ?: return@Button
                    TransferManager.start(
                        context, TransferJob(
                            driveId = drive.id, driveName = drive.name, sourceFolder = folder,
                            destTreeUri = d.toString(), destLabel = folderLabel(d.toString()) ?: "phone",
                            move = move && writable, files = p.files
                        )
                    )
                    onDismiss()
                },
                enabled = plan != null && plan!!.files.isNotEmpty() && dest != null && spaceProblem == null && !busy
            ) { Text(if (move && writable) "Move" else "Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
                    (if (job.move) "Files not moved yet stay on the network drive. " else "") +
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
                    Text(
                        when (st.status) {
                            TransferStatus.RUNNING -> "⬇ $verb “${job.folderName}” to the phone"
                            TransferStatus.PAUSED -> "⏸ $verb “${job.folderName}” paused"
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
