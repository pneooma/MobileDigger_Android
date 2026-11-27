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

/**
 * The iPod page: what's connected, sending songs from this phone (with a per-song transfer queue),
 * playlists on the iPod and the songs on it.
 */
@Composable
fun IpodPage(viewModel: com.example.mobiledigger.viewmodel.MusicViewModel, bars: PaddingValues = PaddingValues(), onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ui by IpodManager.ui.collectAsState()
    val lib = ui.library
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TouchPlaylist?>(null) }
    var creating by remember { mutableStateOf(false) }
    val busy = ui.status == IpodManager.Status.BUSY || ui.status == IpodManager.Status.CONNECTING
    LaunchedEffect(Unit) { if (!IpodManager.isConnected && !busy) IpodManager.connect(context) }

    Dialog(onDismissRequest = { if (editing != null || creating) { editing = null; creating = false } else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Edge to edge; the dialog window gets no system-bar insets, so the caller's ([bars]) keep the
        // top bar and the Send button clear of the status and navigation bars.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // The window starts below the status bar but keeps the full screen height, so it reaches past the
            // bottom edge by the status-bar height: pad the bottom by that plus the navigation bar.
            Column(Modifier.fillMaxSize().padding(bottom = bars.calculateTopPadding() + bars.calculateBottomPadding()).imePadding()) {
                if (lib != null && (editing != null || creating)) {
                    PlaylistEditor(lib, editing, busy,
                        onBack = { editing = null; creating = false },
                        onSave = { name, members -> IpodManager.savePlaylist(context, editing?.id, name, members); editing = null; creating = false; tab = 1 },
                        onDelete = { pl -> IpodManager.deletePlaylist(context, pl.id, pl.name); editing = null })
                    return@Column
                }
                // Top bar
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("iPod", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { IpodManager.connect(context) }, enabled = !busy) { Icon(Icons.Default.Refresh, "Reconnect") }
                }
                DeviceCard(ui, lib, busy) { IpodManager.connect(context) }
                StatusCard(ui, busy)
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Send") }, icon = { Icon(Icons.Default.Upload, null) })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Playlists" + (lib?.let { " (${it.playlists.size})" } ?: "")) },
                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) })
                    Tab(tab == 2, { tab = 2 }, text = { Text("On iPod" + (lib?.let { " (${it.tracks.size})" } ?: "")) },
                        icon = { Icon(Icons.Default.PhoneIphone, null) })
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> SendTab(viewModel, ui, busy)
                        1 -> if (lib != null) PlaylistsTab(lib, busy, onOpen = { editing = it }, onNew = { creating = true }) else NotConnected()
                        else -> if (lib != null) SongsTab(lib, busy) { ids -> IpodManager.removeSongs(context, ids) } else NotConnected()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotConnected() {
    Text("Connect the iPod to see what's on it.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DeviceCard(ui: IpodManager.Ui, lib: TouchLibrary?, busy: Boolean, onConnect: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneIphone, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ui.deviceName ?: "iPod touch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            lib != null -> "${lib.tracks.size} songs · ${lib.playlists.size} playlists"
                            ui.status == IpodManager.Status.CONNECTING -> "Connecting…"
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lib == null && !busy) Button(onClick = onConnect) { Text("Connect") }
            }
            ui.free?.let { (free, total) ->
                if (total > 0) {
                    LinearProgressIndicator(progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    Text("${mb(free)} free of ${mb(total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val convertExt = setOf("flac", "wav", "aif", "aiff", "ogg", "opus")

/** Pick songs from this phone's lists and send them; shows the transfer queue and recent transfers. */
@Composable
private fun SendTab(viewModel: com.example.mobiledigger.viewmodel.MusicViewModel, ui: IpodManager.Ui, busy: Boolean) {
    val context = LocalContext.current
    val todo by viewModel.musicFiles.collectAsState()
    val liked by viewModel.likedFiles.collectAsState()
    val rejected by viewModel.rejectedFiles.collectAsState()
    var source by remember { mutableIntStateOf(1) } // Liked first: the songs worth taking along
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateMapOf<String, com.example.mobiledigger.model.MusicFile>() }
    var target by remember { mutableStateOf<IpodManager.Target>(IpodManager.Target.None) }
    var newName by remember { mutableStateOf("") }
    var targetMenu by remember { mutableStateOf(false) }
    val lists = listOf("To Do" to todo, "Liked" to liked, "Rejected" to rejected)
    val shown = remember(source, query, todo, liked, rejected) {
        val q = query.trim().lowercase()
        lists[source].second.filter { q.isEmpty() || it.name.lowercase().contains(q) || (it.subfolder ?: "").lowercase().contains(q) }
    }
    val pickedSize = picked.values.sumOf { it.size }
    val lib = ui.library
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        // Transfer queue (current / last job)
        if (ui.queue.isNotEmpty()) TransferQueue(ui, busy)
        // Source + search
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lists.forEachIndexed { i, (name, l) ->
                FilterChip(selected = source == i, onClick = { source = i }, label = { Text("$name ${l.size}") })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Search ${lists[source].first}") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.width(6.dp))
            val allPicked = shown.isNotEmpty() && shown.all { it.uri.toString() in picked }
            TextButton(onClick = {
                if (allPicked) shown.forEach { picked.remove(it.uri.toString()) } else shown.forEach { picked[it.uri.toString()] = it }
            }, enabled = shown.isNotEmpty()) { Text(if (allPicked) "None" else "All") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) item {
                Text(if (lists[source].second.isEmpty()) "Nothing in ${lists[source].first}. Load your music first." else "No match.",
                    Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(shown, key = { _, f -> f.uri.toString() }) { _, f ->
                val key = f.uri.toString()
                val sel = key in picked
                val ext = f.name.substringAfterLast('.', "").lowercase()
                ListItem(
                    headlineContent = { Text(f.name.substringBeforeLast('.'), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
                    supportingContent = {
                        Text(listOfNotNull(f.subfolder, ext.uppercase(), if (f.size > 0) mb(f.size) else null,
                            if (ext in convertExt) "→ AAC" else null).joinToString(" · "), fontSize = 12.sp)
                    },
                    leadingContent = { Checkbox(sel, { if (sel) picked.remove(key) else picked[key] = f }) },
                    modifier = Modifier.clickable { if (sel) picked.remove(key) else picked[key] = f }
                )
            }
        }
        // Send bar
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = lib != null) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(8.dp))
                    Text(when (val t = target) {
                        IpodManager.Target.None -> "No playlist (just add the songs)"
                        is IpodManager.Target.New -> "New playlist: ${t.name.ifBlank { "…" }}"
                        is IpodManager.Target.Existing -> "Add to: " + (lib?.playlists?.firstOrNull { it.id == t.pid }?.name ?: "?")
                    }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(targetMenu, { targetMenu = false }) {
                    DropdownMenuItem(text = { Text("No playlist") }, onClick = { target = IpodManager.Target.None; targetMenu = false })
                    DropdownMenuItem(text = { Text("New playlist…") }, onClick = { target = IpodManager.Target.New(newName); targetMenu = false })
                    lib?.playlists?.filter { it.editable }?.forEach { p ->
                        DropdownMenuItem(text = { Text("${p.name} (${p.trackIds.size})") }, onClick = { target = IpodManager.Target.Existing(p.id); targetMenu = false })
                    }
                }
            }
            if (target is IpodManager.Target.New) OutlinedTextField(newName, { newName = it; target = IpodManager.Target.New(it) },
                Modifier.fillMaxWidth(), singleLine = true, label = { Text("Playlist name") })
            val free = ui.free?.first
            val tooBig = free != null && pickedSize > free - (50L shl 20)
            Button(
                onClick = {
                    focus.clearFocus()
                    IpodManager.send(context, picked.values.map { IpodManager.Source(it.uri, it.name) },
                        (target as? IpodManager.Target.New)?.let { IpodManager.Target.New(newName.trim()) } ?: target)
                    picked.clear()
                },
                enabled = picked.isNotEmpty() && lib != null && !busy && !tooBig &&
                    (target !is IpodManager.Target.New || newName.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Upload, null); Spacer(Modifier.width(8.dp))
                Text(when {
                    busy -> "Transfer in progress…"
                    picked.isEmpty() -> "Pick songs to send"
                    tooBig -> "Not enough space on the iPod"
                    else -> "Send ${picked.size} song${if (picked.size == 1) "" else "s"} · ${mb(pickedSize)}"
                })
            }
        }
    }
}

@Composable
private fun TransferQueue(ui: IpodManager.Ui, busy: Boolean) {
    val done = ui.queue.count { it.state == IpodManager.TState.DONE }
    val failed = ui.queue.count { it.state == IpodManager.TState.FAILED }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (busy) "Transferring · $done of ${ui.queue.size} done" else "Last transfer · $done sent" + (if (failed > 0) ", $failed failed" else ""),
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!busy) TextButton(onClick = { IpodManager.clearQueue() }) { Text("Clear") }
            }
            LazyColumn(Modifier.heightIn(max = 170.dp)) {
                itemsIndexed(ui.queue) { _, it ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when (it.state) {
                            IpodManager.TState.DONE -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                            IpodManager.TState.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                            IpodManager.TState.SENDING -> Icons.Default.Upload to MaterialTheme.colorScheme.primary
                            IpodManager.TState.CONVERTING, IpodManager.TState.PREPARING -> Icons.Default.Sync to MaterialTheme.colorScheme.tertiary
                            IpodManager.TState.WAITING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(icon, null, Modifier.size(18.dp), tint = tint)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.name.substringBeforeLast('.'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            val label = when (it.state) {
                                IpodManager.TState.WAITING -> it.detail.ifEmpty { "Waiting" }
                                IpodManager.TState.PREPARING -> "Preparing"
                                IpodManager.TState.CONVERTING -> "Converting to AAC"
                                IpodManager.TState.SENDING -> "Sending"
                                IpodManager.TState.DONE -> "On the iPod"
                                IpodManager.TState.FAILED -> it.detail.ifEmpty { "Failed" }
                            }
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (ui.history.isNotEmpty() && !busy) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ui.history.take(3).forEach { h ->
                    Text((if (h.ok) "✓ " else "✗ ") + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(h.time)) + "  " + h.text,
                        fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (h.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(ui: IpodManager.Ui, busy: Boolean) {
    val show = busy || ui.prompt != null || ui.status == IpodManager.Status.ERROR || ui.status == IpodManager.Status.NO_DEVICE ||
        (ui.status == IpodManager.Status.READY && ui.message.isNotEmpty() && ui.message != "Connected")
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
        if (lib.unfinished.isNotEmpty()) Card(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${lib.unfinished.size} song${if (lib.unfinished.size == 1) "" else "s"} didn't finish transferring", fontWeight = FontWeight.SemiBold)
                    Text(lib.unfinished.joinToString { it.title }.take(120), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { onRemove(lib.unfinished.map { it.syncId }) }, enabled = !busy) { Text("Remove") }
            }
        }
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
