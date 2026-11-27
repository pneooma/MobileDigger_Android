package com.example.mobiledigger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mobiledigger.ipod.IpodManager
import com.example.mobiledigger.ipod.TouchLibrary
import com.example.mobiledigger.ipod.TouchPlaylist
import com.example.mobiledigger.ipod.TouchTrack

private fun mb(b: Long) = if (b >= 1L shl 30) "%.1f GB".format(b / 1073741824.0) else "${b shr 20} MB"

/** iPod touch manager: what's on it, playlists (create / edit / delete), remove songs. */
@Composable
fun IpodDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ui by IpodManager.ui.collectAsState()
    val lib = ui.library
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TouchPlaylist?>(null) }
    var creating by remember { mutableStateOf(false) }
    val busy = ui.status == IpodManager.Status.BUSY || ui.status == IpodManager.Status.CONNECTING
    LaunchedEffect(Unit) { if (!IpodManager.isConnected && !busy) IpodManager.connect(context) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp - 90).dp).padding(10.dp),
            shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface
        ) {
            if (lib != null && (editing != null || creating)) {
                PlaylistEditor(lib, editing, busy,
                    onBack = { editing = null; creating = false },
                    onSave = { name, members ->
                        IpodManager.savePlaylist(context, editing?.id, name, members); editing = null; creating = false
                    },
                    onDelete = { pl -> IpodManager.deletePlaylist(context, pl.id, pl.name); editing = null })
                return@Surface
            }
            Column(Modifier.fillMaxWidth()) {
                // Header
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneIphone, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ui.deviceName ?: "iPod touch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                if (lib != null) append("${lib.tracks.size} songs · ${lib.playlists.size} playlists")
                                ui.free?.let { (free, total) -> append(" · ${mb(free)} free of ${mb(total)}") }
                            }.ifEmpty { "Not connected" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { IpodManager.connect(context) }, enabled = !busy) { Icon(Icons.Default.Refresh, "Reconnect") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                // Status
                StatusCard(ui, busy)
                if (lib != null) {
                    TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 6.dp)) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("Playlists") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("Songs") })
                    }
                    Box(Modifier.weight(1f, fill = false)) {
                        if (tab == 0) PlaylistsTab(lib, busy, onOpen = { editing = it }, onNew = { creating = true })
                        else SongsTab(lib, busy) { ids -> IpodManager.removeSongs(context, ids) }
                    }
                } else Spacer(Modifier.height(12.dp))
                Text(
                    "To send songs: select them in your playlist (Select), then tap “iPod”.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusCard(ui: IpodManager.Ui, busy: Boolean) {
    val show = busy || ui.prompt != null || ui.status == IpodManager.Status.ERROR || ui.status == IpodManager.Status.NO_DEVICE ||
        (ui.status == IpodManager.Status.READY && ui.message.isNotEmpty())
    if (!show) return
    val err = ui.status == IpodManager.Status.ERROR || ui.status == IpodManager.Status.NO_DEVICE
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (err) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ui.prompt?.let { Text(it, fontWeight = FontWeight.Bold) }
            Text(ui.message, style = MaterialTheme.typography.bodyMedium)
            if (busy) {
                val p = ui.progress
                if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Keep the iPod unlocked and plugged in.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PlaylistsTab(lib: TouchLibrary, busy: Boolean, onOpen: (TouchPlaylist) -> Unit, onNew: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        item {
            ListItem(
                headlineContent = { Text("New playlist", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(enabled = !busy && lib.tracks.isNotEmpty()) { onNew() }
            )
        }
        if (lib.playlists.isEmpty()) item {
            Text("No playlists on the iPod yet.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(lib.playlists, key = { _, p -> p.id }) { _, p ->
            ListItem(
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${p.trackIds.size} songs" + if (!p.editable) " · made on the iPod (view only)" else "") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                trailingContent = { if (p.editable) Icon(Icons.Default.Edit, "Edit") },
                modifier = Modifier.clickable(enabled = !busy) { onOpen(p) }
            )
        }
    }
}

@Composable
private fun SongsTab(lib: TouchLibrary, busy: Boolean, onRemove: (List<Long>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf(false) }
    val shown = remember(lib, query) {
        val q = query.trim().lowercase()
        lib.tracks.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
            .sortedBy { it.title.lowercase() }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Search the iPod") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            if (selected.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { confirm = true }, enabled = !busy) { Text("Remove ${selected.size}") }
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(shown, key = { _, t -> t.id }) { _, t ->
                val sel = t.id in selected
                ListItem(
                    headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(listOf(t.artist, t.album).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Checkbox(sel, { selected = if (sel) selected - t.id else selected + t.id }, enabled = t.syncId != 0L)
                    },
                    modifier = Modifier.clickable(enabled = t.syncId != 0L) { selected = if (sel) selected - t.id else selected + t.id }
                )
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove ${selected.size} songs from the iPod?") },
        text = { Text("They're deleted from the iPod and from its playlists. The songs on this phone aren't touched.") },
        confirmButton = {
            TextButton(onClick = {
                val ids = lib.tracks.filter { it.id in selected }.map { it.syncId }.filter { it != 0L }
                confirm = false; selected = emptySet(); onRemove(ids)
            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
}

/** Create (playlist == null) or edit a playlist: name, order, members. */
@Composable
private fun PlaylistEditor(
    lib: TouchLibrary, playlist: TouchPlaylist?, busy: Boolean,
    onBack: () -> Unit, onSave: (String, List<Long>) -> Unit, onDelete: (TouchPlaylist) -> Unit
) {
    val byId = remember(lib) { lib.tracks.associateBy { it.id } }
    val bySync = remember(lib) { lib.tracks.filter { it.syncId != 0L }.associateBy { it.syncId } }
    var name by remember { mutableStateOf(playlist?.name ?: "") }
    val members = remember { mutableStateListOf<Long>().apply {
        playlist?.trackIds?.mapNotNull { byId[it]?.syncId?.takeIf { s -> s != 0L } }?.let { addAll(it) }
    } }
    var picking by remember { mutableStateOf(playlist == null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editable = playlist?.editable != false

    if (picking) {
        SongPicker(lib, members.toSet(), onDone = { picked -> members.addAll(picked.filter { it !in members }); picking = false }, onBack = { picking = false })
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(if (playlist == null) "New playlist" else if (editable) "Edit playlist" else playlist.name,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (playlist != null && editable) IconButton(onClick = { confirmDelete = true }, enabled = !busy) {
                Icon(Icons.Default.DeleteOutline, "Delete playlist", tint = MaterialTheme.colorScheme.error)
            }
        }
        if (editable) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text("Name") }, singleLine = true)
        else Text("Made on the iPod, so MobileDigger can't change it.", Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${members.size} songs", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (editable) TextButton(onClick = { picking = true }) { Icon(Icons.Default.Add, null); Text("Add songs") }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            itemsIndexed(members.toList(), key = { i, id -> "$i-$id" }) { i, sid ->
                val t = bySync[sid]
                ListItem(
                    headlineContent = { Text(t?.title ?: "Unknown song", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(t?.artist.orEmpty(), maxLines = 1) },
                    leadingContent = { Text("${i + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        if (editable) Row {
                            IconButton(onClick = { if (i > 0) { members.removeAt(i); members.add(i - 1, sid) } }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { if (i < members.size - 1) { members.removeAt(i); members.add(i + 1, sid) } }, enabled = i < members.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { members.removeAt(i) }) { Icon(Icons.Default.Close, "Remove from playlist") }
                        }
                    }
                )
            }
        }
        if (editable) Button(
            onClick = { onSave(name.trim(), members.toList()) },
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp), shape = RoundedCornerShape(16.dp)
        ) { Text(if (playlist == null) "Create on the iPod" else "Save to the iPod") }
    }
    if (confirmDelete && playlist != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${playlist.name}”?") },
        text = { Text("The playlist is removed from the iPod. Its songs stay on the iPod.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(playlist) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SongPicker(lib: TouchLibrary, already: Set<Long>, onDone: (List<Long>) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>() }
    val shown = remember(lib, query) {
        val q = query.trim().lowercase()
        lib.tracks.filter { it.syncId != 0L && (q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)) }
            .sortedBy { it.title.lowercase() }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Add songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { onDone(picked.toList()) }, enabled = picked.isNotEmpty()) { Text("Add ${picked.size}") }
            Spacer(Modifier.width(8.dp))
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp), singleLine = true,
            placeholder = { Text("Search the iPod") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        LazyColumn(Modifier.fillMaxWidth()) {
            itemsIndexed(shown, key = { _, t -> t.id }) { _, t: TouchTrack ->
                val inList = t.syncId in already
                val sel = t.syncId in picked
                ListItem(
                    headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(if (inList) "Already in the playlist" else t.artist, maxLines = 1) },
                    leadingContent = { Checkbox(sel || inList, { if (!inList) { if (sel) picked.remove(t.syncId) else picked.add(t.syncId) } }, enabled = !inList) },
                    modifier = Modifier.clickable(enabled = !inList) { if (sel) picked.remove(t.syncId) else picked.add(t.syncId) }
                )
            }
        }
    }
}

/** "Send to iPod" for songs selected in MobileDigger: pick a playlist (optional), then send. */
@Composable
fun SendToIpodDialog(count: Int, onSend: (IpodManager.Target) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ui by IpodManager.ui.collectAsState()
    LaunchedEffect(Unit) { if (!IpodManager.isConnected) IpodManager.connect(context) }
    val lib = ui.library
    var choice by remember { mutableIntStateOf(0) } // 0 none, 1 new, 2 existing
    var newName by remember { mutableStateOf("") }
    var existing by remember { mutableStateOf<TouchPlaylist?>(null) }
    val ready = IpodManager.isConnected && lib != null && ui.status == IpodManager.Status.READY
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PhoneIphone, null) },
        title = { Text("Send $count song${if (count == 1) "" else "s"} to the iPod") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ready) {
                    Text(ui.prompt ?: ui.message.ifEmpty { "Connecting to the iPod…" })
                    if (ui.status == IpodManager.Status.CONNECTING || ui.status == IpodManager.Status.BUSY) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (ui.status == IpodManager.Status.ERROR || ui.status == IpodManager.Status.NO_DEVICE)
                        TextButton(onClick = { IpodManager.connect(context) }) { Text("Try again") }
                } else {
                    Text("${ui.deviceName}: ${lib!!.tracks.size} songs" + (ui.free?.let { " · ${mb(it.first)} free" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                    Text("Also put them in a playlist?", fontWeight = FontWeight.SemiBold)
                    @Composable fun opt(i: Int, label: String) = Row(Modifier.fillMaxWidth().clickable { choice = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(choice == i, { choice = i }); Text(label)
                    }
                    opt(0, "No, just add the songs")
                    opt(1, "A new playlist")
                    if (choice == 1) OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Playlist name") })
                    val editable = lib.playlists.filter { it.editable }
                    if (editable.isNotEmpty()) {
                        opt(2, "An existing playlist")
                        if (choice == 2) editable.forEach { p ->
                            Row(Modifier.fillMaxWidth().clickable { existing = p }.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(existing?.id == p.id, { existing = p }); Text("${p.name} (${p.trackIds.size})")
                            }
                        }
                    }
                    Text("FLAC, WAV, AIFF and OGG are converted to AAC 256 kbps; MP3 and AAC/ALAC are sent as they are.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSend(when (choice) {
                        1 -> IpodManager.Target.New(newName.trim())
                        2 -> existing?.let { IpodManager.Target.Existing(it.id) } ?: IpodManager.Target.None
                        else -> IpodManager.Target.None
                    })
                },
                enabled = ready && (choice != 1 || newName.isNotBlank()) && (choice != 2 || existing != null)
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
