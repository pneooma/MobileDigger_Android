package com.example.mobiledigger.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobiledigger.file.drives.DriveType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.mobiledigger.file.drives.AttachedVolume
import com.example.mobiledigger.file.drives.UsbDrives
import com.example.mobiledigger.file.transfer.TransferManager
import com.example.mobiledigger.file.drives.NetworkDrive
import com.example.mobiledigger.viewmodel.MusicViewModel
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF10B981)

fun driveIcon(type: DriveType): ImageVector = when (type) {
    DriveType.SMB -> Icons.Default.Lan
    DriveType.WEBDAV -> Icons.Default.Cloud
    DriveType.MOBILEDIGGER -> Icons.Default.Dns
    DriveType.USB -> Icons.Default.Usb
}

private sealed interface DrivesPage {
    data object List : DrivesPage
    data class Edit(val drive: NetworkDrive?) : DrivesPage
    data class Browse(val drive: NetworkDrive) : DrivesPage
}

/** Full-screen manager for drives (USB + network): list → add/edit → pick a folder → load / copy. */
@Composable
fun NetworkDrivesDialog(viewModel: MusicViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refreshDrives() }
    val drives by viewModel.drives.collectAsState()
    val activeId by viewModel.activeDriveId.collectAsState()
    var page by remember { mutableStateOf<DrivesPage>(DrivesPage.List) }
    val config = LocalConfiguration.current

    Dialog(
        onDismissRequest = {
            if (page is DrivesPage.List) onDismiss() else page = DrivesPage.List
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // Dialog windows don't get system-bar insets here, so leave room for the status
                // and navigation bars ourselves; every page scrolls inside this height.
                .heightIn(max = (config.screenHeightDp - 110).dp)
                .imePadding()
                .padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "drives") { p ->
                when (p) {
                    DrivesPage.List -> DriveListPage(
                        drives = drives,
                        activeId = activeId,
                        viewModel = viewModel,
                        onClose = onDismiss,
                        onAdd = { page = DrivesPage.Edit(null) },
                        onEdit = { page = DrivesPage.Edit(it) },
                        onOpen = { d -> viewModel.browseDrive(d, d.folder); page = DrivesPage.Browse(d) },
                        onDelete = { viewModel.deleteDrive(it.id) },
                        onDisconnect = { viewModel.disconnectDrive() }
                    )
                    is DrivesPage.Edit -> DriveEditPage(
                        initial = p.drive,
                        viewModel = viewModel,
                        onBack = { page = DrivesPage.List },
                        onSaved = { d, open ->
                            viewModel.saveDrive(d)
                            if (open) { viewModel.browseDrive(d, d.folder); page = DrivesPage.Browse(d) } else page = DrivesPage.List
                        }
                    )
                    is DrivesPage.Browse -> DriveBrowsePage(
                        drive = p.drive,
                        viewModel = viewModel,
                        onBack = { page = DrivesPage.List },
                        onLoad = { folder -> viewModel.loadDrive(p.drive, folder); onDismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String?, onBack: (() -> Unit)?, onClose: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp)) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
    }
}

// ---------------------------------------------------------------- list

/** Removable volumes plugged in right now; updates live as drives are plugged in or pulled out. */
@Composable
private fun rememberAttachedVolumes(): List<AttachedVolume> {
    val context = LocalContext.current
    var vols by remember { mutableStateOf(UsbDrives.attached(context)) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
        val cb = object : android.os.storage.StorageManager.StorageVolumeCallback() {
            override fun onStateChanged(volume: android.os.storage.StorageVolume) { vols = UsbDrives.attached(context) }
        }
        sm?.registerStorageVolumeCallback(context.mainExecutor, cb)
        onDispose { runCatching { sm?.unregisterStorageVolumeCallback(cb) } }
    }
    return vols
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun DriveListPage(
    drives: List<NetworkDrive>,
    activeId: String?,
    viewModel: MusicViewModel,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (NetworkDrive) -> Unit,
    onOpen: (NetworkDrive) -> Unit,
    onDelete: (NetworkDrive) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<NetworkDrive?>(null) }
    val attached = rememberAttachedVolumes()
    val usbSaved = drives.filter { it.type == DriveType.USB }
    val network = drives.filter { it.type.isNetwork }
    // Grants can be revoked from system settings; re-check whenever the list changes.
    val access = remember(drives, attached) { usbSaved.associate { it.id to UsbDrives.hasAccess(context, it.address) } }

    var pendingVolume by remember { mutableStateOf<AttachedVolume?>(null) }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val vol = pendingVolume
        pendingVolume = null
        val tree = res.data?.data
        if (res.resultCode == android.app.Activity.RESULT_OK && tree != null && vol != null) {
            viewModel.connectUsbDrive(tree, vol)?.let(onOpen)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        PageHeader("Drives", "USB drives plugged into the phone, and drives on your Wi-Fi.", null, onClose)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionLabel("Plugged into the phone") }
            if (attached.isEmpty()) item {
                HintCard(
                    Icons.Default.Usb,
                    "No USB drive plugged in",
                    "Connect a hard drive, SSD or stick (USB-C, or with an OTG adapter). It appears here by itself."
                )
            }
            items(attached, key = { "vol-" + it.id }) { v ->
                val saved = usbSaved.firstOrNull { it.share == v.id }
                if (saved != null && access[saved.id] == true) {
                    UsbCard(
                        name = saved.name, vol = v, active = saved.id == activeId, connected = true,
                        onOpen = { onOpen(saved) }, onForget = { confirmDelete = saved }
                    )
                } else {
                    UsbCard(
                        name = v.label, vol = v, active = false, connected = true, needsAccess = true,
                        onOpen = { pendingVolume = v; grant.launch(UsbDrives.accessIntent(v)) }, onForget = null
                    )
                }
            }
            val away = usbSaved.filter { d -> attached.none { it.id == d.share } }
            items(away, key = { it.id }) { d ->
                UsbCard(name = d.name, vol = null, active = d.id == activeId, connected = false,
                    onOpen = {}, onForget = { confirmDelete = d })
            }

            item { SectionLabel("Network") }
            if (network.isEmpty()) item {
                HintCard(Icons.Default.Router, "No network drives yet", "Shared folders (SMB), WebDAV and the MobileDigger server.")
            }
            items(network, key = { it.id }) { d ->
                DriveCard(d, d.id == activeId, onOpen = { onOpen(d) }, onEdit = { onEdit(d) }, onDelete = { confirmDelete = d })
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a network drive")
            }
            if (activeId != null) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.LinkOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Stop playing from a drive")
                }
            }
        }
    }
    confirmDelete?.let { d ->
        val usb = d.type == DriveType.USB
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (usb) "Forget ${d.name}?" else "Remove ${d.name}?") },
            text = {
                Text(
                    if (usb) "The app stops having access to this drive. Nothing on it is touched; you can allow access again any time."
                    else "This only removes the saved connection. Nothing on the drive is touched."
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(d); confirmDelete = null }) { Text(if (usb) "Forget" else "Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HintCard(icon: ImageVector, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsbCard(
    name: String, vol: AttachedVolume?, active: Boolean, connected: Boolean, needsAccess: Boolean = false,
    onOpen: () -> Unit, onForget: (() -> Unit)?
) {
    val border = when {
        active -> BorderStroke(1.5.dp, OkGreen)
        needsAccess -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    OutlinedCard(
        onClick = onOpen, enabled = connected,
        shape = RoundedCornerShape(20.dp), border = border,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(
                        when {
                            active -> OkGreen.copy(alpha = 0.18f)
                            connected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Usb, null, tint = when {
                        active -> OkGreen
                        connected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (active) {
                            Spacer(Modifier.width(8.dp))
                            Text("IN USE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OkGreen, letterSpacing = 1.sp,
                                modifier = Modifier.border(1.dp, OkGreen, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
                        }
                    }
                    Text(
                        when {
                            !connected -> "Not plugged in"
                            needsAccess -> "Plugged in · tap to allow access"
                            else -> "Plugged in · tap to browse"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onForget != null) IconButton(onClick = onForget) {
                    Icon(Icons.Default.DeleteOutline, "Forget $name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val free = vol?.freeBytes
            val total = vol?.totalBytes
            if (free != null && total != null && total > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(Modifier.height(4.dp))
                Text("${TransferManager.fmt(free)} free of ${TransferManager.fmt(total)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (needsAccess) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android asks once: on the next screen tap “Use this folder”, then “Allow”.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DriveCard(d: NetworkDrive, active: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val border = if (active) BorderStroke(1.5.dp, OkGreen) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    OutlinedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        border = border,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (active) OkGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(driveIcon(d.type), null, tint = if (active) OkGreen else MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name.ifBlank { "Drive" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Text("IN USE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OkGreen, letterSpacing = 1.sp,
                            modifier = Modifier.border(1.dp, OkGreen, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
                    }
                }
                Text(d.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(d.displayAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (d.folder.isNotBlank()) {
                    Text("Folder: /${d.folder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit ${d.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Remove ${d.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ---------------------------------------------------------------- add / edit

@Composable
private fun DriveEditPage(
    initial: NetworkDrive?,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onSaved: (NetworkDrive, open: Boolean) -> Unit
) {
    var type by remember { mutableStateOf(initial?.type ?: DriveType.SMB) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var share by remember { mutableStateOf(initial?.share ?: "") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var pass by remember { mutableStateOf(initial?.password ?: "") }
    var domain by remember { mutableStateOf(initial?.domain ?: "") }
    var writeOn by remember { mutableStateOf(!initial?.writeShare.isNullOrBlank()) }
    var writeShare by remember { mutableStateOf(initial?.writeShare?.ifBlank { null } ?: "music") }
    var writeUser by remember { mutableStateOf(initial?.writeUser ?: "") }
    var writePass by remember { mutableStateOf(initial?.writePassword ?: "") }
    var showWritePass by remember { mutableStateOf(false) }
    var showPass by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(domain.isNotBlank()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun build() = NetworkDrive(
        id = initial?.id ?: java.util.UUID.randomUUID().toString().take(8),
        name = name.trim().ifBlank {
            when (type) {
                DriveType.SMB -> share.trim().ifBlank { address.trim() }
                else -> address.trim().removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
            }
        },
        type = type,
        address = address.trim(),
        share = if (type == DriveType.SMB) share.trim() else "",
        username = if (type == DriveType.MOBILEDIGGER) "" else user.trim(),
        password = pass,
        domain = if (type == DriveType.SMB) domain.trim() else "",
        folder = initial?.folder ?: "",
        writeShare = if (type == DriveType.MOBILEDIGGER && writeOn) writeShare.trim() else "",
        writeUser = if (type == DriveType.MOBILEDIGGER && writeOn) writeUser.trim() else "",
        writePassword = if (type == DriveType.MOBILEDIGGER && writeOn) writePass else ""
    )
    val canSave = address.isNotBlank() && (type != DriveType.SMB || share.isNotBlank())

    Column(Modifier.fillMaxWidth()) {
        PageHeader(if (initial == null) "Add a drive" else "Edit ${initial.name}", null, onBack, null)
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("What kind of drive?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            DriveType.entries.filter { it.isNetwork }.forEach { t ->
                val selected = t == type
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { type = t; testResult = null }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(driveIcon(t), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = selected, onClick = { type = t; testResult = null })
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, placeholder = { Text("Living room NAS") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = address, onValueChange = { address = it; testResult = null },
                label = {
                    Text(when (type) {
                        DriveType.SMB -> "Address"
                        DriveType.WEBDAV -> "WebDAV address"
                        DriveType.MOBILEDIGGER, DriveType.USB -> "Server address"
                    })
                },
                placeholder = {
                    Text(when (type) {
                        DriveType.SMB -> "192.168.1.20 or nas.local"
                        DriveType.WEBDAV -> "http://nas.local:5005/music"
                        DriveType.MOBILEDIGGER, DriveType.USB -> "192.168.1.129:8080"
                    })
                },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
            )
            if (type == DriveType.SMB) {
                OutlinedTextField(
                    value = share, onValueChange = { share = it; testResult = null },
                    label = { Text("Share name") }, placeholder = { Text("Music") },
                    supportingText = { Text("The name of the shared folder, as it appears on your network.") },
                    leadingIcon = { Icon(Icons.Default.FolderShared, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
            }
            if (type != DriveType.MOBILEDIGGER) {
                OutlinedTextField(
                    value = user, onValueChange = { user = it; testResult = null },
                    label = { Text("Username") },
                    supportingText = if (type == DriveType.SMB) ({ Text("Leave empty to connect as guest.") }) else null,
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
            }
            OutlinedTextField(
                value = pass, onValueChange = { pass = it; testResult = null },
                label = { Text(if (type == DriveType.MOBILEDIGGER) "Password (if the server has one)" else "Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPass) "Hide password" else "Show password")
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
            if (type == DriveType.SMB) {
                TextButton(onClick = { showMore = !showMore }) {
                    Text(if (showMore) "Fewer options" else "More options")
                }
                if (showMore) {
                    OutlinedTextField(
                        value = domain, onValueChange = { domain = it },
                        label = { Text("Domain / workgroup (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            if (type == DriveType.MOBILEDIGGER) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable { writeOn = !writeOn; testResult = null }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Write access", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Lets the app delete rejected tracks and unpack archives, through the shared folder (SMB) on the same machine.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = writeOn, onCheckedChange = { writeOn = it; testResult = null })
                }
                if (writeOn) {
                    OutlinedTextField(
                        value = writeShare, onValueChange = { writeShare = it; testResult = null },
                        label = { Text("Share name") }, placeholder = { Text("music") },
                        supportingText = { Text("The shared folder that holds the same music library.") },
                        leadingIcon = { Icon(Icons.Default.FolderShared, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = writeUser, onValueChange = { writeUser = it; testResult = null },
                        label = { Text("Share username") },
                        supportingText = { Text("Leave empty if the share allows guests.") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = writePass, onValueChange = { writePass = it; testResult = null },
                        label = { Text("Share password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { showWritePass = !showWritePass }) {
                                Icon(if (showWritePass) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showWritePass) "Hide password" else "Show password")
                            }
                        },
                        visualTransformation = if (showWritePass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Test result
            testResult?.let { (ok, msg) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (ok) OkGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null,
                        tint = if (ok) OkGreen else MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium,
                        color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Text(
                "Passwords are kept only on this phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    testing = true; testResult = null
                    scope.launch {
                        val err = viewModel.testDrive(build())
                        testResult = if (err == null) true to (if (type == DriveType.MOBILEDIGGER && writeOn) "Connected, with write access" else "Connected") else false to err
                        testing = false
                    }
                },
                enabled = canSave && !testing,
                modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Test")
            }
            Button(
                onClick = { onSaved(build(), true) },
                enabled = canSave,
                modifier = Modifier.weight(1.6f).height(50.dp), shape = RoundedCornerShape(16.dp)
            ) { Text("Save & pick folder") }
        }
        Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- folder browser

@Composable
private fun DriveBrowsePage(drive: NetworkDrive, viewModel: MusicViewModel, onBack: () -> Unit, onLoad: (String) -> Unit) {
    val st by viewModel.driveBrowse.collectAsState()
    val path = st.path
    var showTransfer by remember { mutableStateOf(false) }
    val crumbs = if (path.isBlank()) emptyList() else path.split('/')

    Column(Modifier.fillMaxWidth()) {
        PageHeader(drive.name, "Open a folder, then play it or copy it to the phone. Subfolders are included.", onBack, null)
        // Breadcrumbs
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { viewModel.browseDrive(drive, "") },
                label = { Text(when (drive.type) { DriveType.SMB -> drive.share.ifBlank { "Top" }; DriveType.USB -> drive.name; else -> "Top" }, maxLines = 1) },
                leadingIcon = { Icon(driveIcon(drive.type), null, Modifier.size(16.dp)) }
            )
            crumbs.forEachIndexed { i, c ->
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val target = crumbs.take(i + 1).joinToString("/")
                AssistChip(onClick = { viewModel.browseDrive(drive, target) }, label = { Text(c, maxLines = 1) })
            }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 160.dp)) {
            when {
                st.loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                st.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Text(st.error ?: "", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { viewModel.browseDrive(drive, path) }) { Text("Try again") }
                }
                else -> LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    if (path.isNotBlank()) item {
                        ListItem(
                            headlineContent = { Text("Up one level") },
                            leadingContent = { Icon(Icons.Default.ArrowUpward, null) },
                            modifier = Modifier.clickable { viewModel.browseDrive(drive, path.substringBeforeLast('/', "")) }
                        )
                    }
                    items(st.dirs, key = { it.path }) { d ->
                        ListItem(
                            headlineContent = { Text(d.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                            modifier = Modifier.clickable { viewModel.browseDrive(drive, d.path) }
                        )
                    }
                    if (st.dirs.isEmpty() && path.isBlank()) item {
                        Text("No folders here.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (!st.loading && st.error == null) {
                Text(
                    when (st.audioCount) {
                        0 -> "No tracks directly in this folder" + if (st.dirs.isNotEmpty()) ", but subfolders will be searched." else "."
                        1 -> "1 track here" + if (st.dirs.isNotEmpty()) ", plus whatever is in the subfolders." else "."
                        else -> "${st.audioCount} tracks here" + if (st.dirs.isNotEmpty()) ", plus whatever is in the subfolders." else "."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { onLoad(path) },
                enabled = !st.loading && st.error == null,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                Text(if (path.isBlank()) "Load everything" else "Load “${path.substringAfterLast('/')}”", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (path.isNotBlank() || drive.type == DriveType.USB) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showTransfer = true },
                    enabled = !st.loading && st.error == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp))
                    Text(if (path.isBlank()) "Copy or move the whole drive…" else "Copy or move “${path.substringAfterLast('/')}” to phone…",
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (showTransfer) {
        TransferSetupDialog(drive = drive, folder = path, viewModel = viewModel, onDismiss = { showTransfer = false })
    }
}

