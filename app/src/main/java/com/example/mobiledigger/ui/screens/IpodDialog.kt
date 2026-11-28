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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.mobiledigger.ipod.IpodManager
import com.example.mobiledigger.ipod.SendQuality
import com.example.mobiledigger.ipod.TouchLibrary
import com.example.mobiledigger.ipod.TouchPlaylist
import com.example.mobiledigger.ipod.TouchTrack

private fun mb(b: Long) = if (b >= 1L shl 30) "%.1f GB".format(b / 1073741824.0) else "${b shr 20} MB"

/**
 * The iPod page: what's connected, sending songs from this phone (with a per-song transfer queue),
 * playlists on the iPod and the songs on it.
 */

private typealias MF = com.example.mobiledigger.model.MusicFile

/** A thin scrollbar on the right of a LazyColumn (Compose has none built in). */
fun Modifier.lazyScrollbar(state: androidx.compose.foundation.lazy.LazyListState, color: androidx.compose.ui.graphics.Color) =
    this.drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.isEmpty() || visible.size >= total) return@drawWithContent
        val avg = visible.sumOf { it.size }.toFloat() / visible.size
        val contentH = avg * total
        val viewH = size.height
        val scrolled = state.firstVisibleItemIndex * avg + state.firstVisibleItemScrollOffset
        val thumbH = (viewH * viewH / contentH).coerceIn(24.dp.toPx(), viewH)
        val y = ((scrolled / (contentH - viewH).coerceAtLeast(1f)) * (viewH - thumbH)).coerceIn(0f, viewH - thumbH)
        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width - 5.dp.toPx(), y),
            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), thumbH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
    }

/**
 * The iPod page: what's connected, sending songs/folders from this phone (converted in the
 * background), the transfer queue, playlists on the iPod and the songs on it.
 */
@Composable
fun IpodPage(viewModel: com.example.mobiledigger.viewmodel.MusicViewModel, bars: PaddingValues = PaddingValues(),
             onLoadFolder: () -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ui by IpodManager.ui.collectAsState()
    val lib = ui.library
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TouchPlaylist?>(null) }
    var creating by remember { mutableStateOf(false) }
    val busy = ui.status == IpodManager.Status.BUSY || ui.status == IpodManager.Status.CONNECTING
    LaunchedEffect(Unit) { if (!IpodManager.isConnected && !busy) IpodManager.connect(context) }

    Dialog(onDismissRequest = { if (editing != null || creating) { editing = null; creating = false } else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // The window starts below the status bar but keeps the full screen height, so it reaches past the
            // bottom edge by the status-bar height: pad the bottom by that plus the navigation bar.
            Column(Modifier.fillMaxSize().padding(bottom = bars.calculateTopPadding() + bars.calculateBottomPadding()).imePadding()) {
                if (lib != null && (editing != null || creating)) {
                    PlaylistEditor(lib, editing, busy,
                        onBack = { editing = null; creating = false },
                        onSave = { name, members -> IpodManager.savePlaylist(context, editing?.id, name, members); editing = null; creating = false; tab = 2 },
                        onDelete = { pl -> IpodManager.deletePlaylist(context, pl.id, pl.name); editing = null })
                    return@Column
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("iPod", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { IpodManager.connect(context) }, enabled = !busy) { Icon(Icons.Default.Refresh, "Reconnect") }
                }
                DeviceCard(ui, lib, busy) { IpodManager.connect(context) }
                StatusCard(ui, busy)
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Send") }, icon = { Icon(Icons.Default.Upload, null) })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Queue" + if (ui.pending > 0) " (${ui.pending})" else "") },
                        icon = { Icon(Icons.Default.Sync, null) })
                    Tab(tab == 2, { tab = 2 }, text = { Text("Playlists" + (lib?.let { " (${it.playlists.size})" } ?: "")) },
                        icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) })
                    Tab(tab == 3, { tab = 3 }, text = { Text("On iPod" + (lib?.let { " (${it.tracks.size})" } ?: "")) },
                        icon = { Icon(Icons.Default.PhoneIphone, null) })
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> SendTab(viewModel, ui, onLoadFolder) { tab = 1 }
                        1 -> QueueTab(ui)
                        2 -> if (lib != null) PlaylistsTab(lib, busy, onOpen = { editing = it }, onNew = { creating = true }) else NotConnected()
                        else -> if (lib != null) SongsTab(lib, busy) else NotConnected()
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

// ---------------------------------------------------------------- shared pieces

/** AAC 128/192/256/320 or Apple Lossless, and whether MP3/AAC/ALAC files go untouched. */
@Composable
fun QualityPicker(q: SendQuality, onChange: (SendQuality) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Quality on the iPod", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(128, 192, 256, 320).forEach { k ->
                FilterChip(!q.alac && q.aacKbps == k, { onChange(q.copy(alac = false, aacKbps = k)) }, label = { Text("AAC $k") })
            }
            FilterChip(q.alac, { onChange(q.copy(alac = true)) }, label = { Text("Lossless (ALAC)") })
        }
        Row(Modifier.fillMaxWidth().clickable { onChange(q.copy(keepCompatible = !q.keepCompatible)) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(q.keepCompatible, { onChange(q.copy(keepCompatible = it)) })
            Text("Keep MP3 / AAC / ALAC files as they are", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (q.alac) "WAV, FLAC and AIFF become Apple Lossless (no quality lost, larger files). OGG/Opus become AAC 320."
            else "WAV, FLAC, AIFF, OGG" + (if (q.keepCompatible) "" else ", MP3 and AAC") + " are converted to AAC ${q.aacKbps} kbps.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "Also add to an iPod playlist" with a name field and the iPod's playlists as suggestions. */
@Composable
fun PlaylistField(enabled: Boolean, name: String, playlists: List<String>, onEnabled: (Boolean) -> Unit, onName: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { onEnabled(!enabled) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(enabled, onEnabled)
            Text("Add to an iPod playlist", fontWeight = FontWeight.SemiBold)
        }
        if (enabled) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, onName, Modifier.weight(1f), singleLine = true, label = { Text("Playlist (new or existing)") })
            if (playlists.isNotEmpty()) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.ArrowDropDown, "iPod playlists") }
                DropdownMenu(menu, { menu = false }) {
                    playlists.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { onName(p); menu = false }) }
                }
            }
        }
    }
}

private fun lastPart(path: String) = path.trimEnd('/').substringAfterLast('/')

/** The folder all [files] share (deepest common folder), or null. */
fun commonFolder(files: List<MF>): String? {
    val paths = files.map { it.subfolder?.trim('/') ?: "" }
    if (paths.isEmpty() || paths.any { it.isEmpty() }) return null
    var common = paths.first().split('/')
    for (p in paths.drop(1)) {
        val parts = p.split('/')
        common = common.zip(parts).takeWhile { (a, b) -> a == b }.map { it.first }
    }
    return common.lastOrNull()?.takeIf { it.isNotEmpty() }
}

private fun inBranch(f: MF, branch: String) = com.example.mobiledigger.viewmodel.folderInBranch(f.subfolder, branch)

// ---------------------------------------------------------------- Send

/** Folder tree of one list; folders can be ticked as a whole (their whole branch). */
@Composable
private fun SendTab(viewModel: com.example.mobiledigger.viewmodel.MusicViewModel, ui: IpodManager.Ui, onLoadFolder: () -> Unit, onQueued: () -> Unit) {
    val context = LocalContext.current
    val todo by viewModel.musicFiles.collectAsState()
    val liked by viewModel.likedFiles.collectAsState()
    val rejected by viewModel.rejectedFiles.collectAsState()
    var source by rememberSaveable { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateMapOf<String, MF>() }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    var quality by remember { mutableStateOf(SendQuality.load(context)) }
    var usePlaylist by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf("") }
    var playlistTouched by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val lists = listOf("To Do" to todo, "Liked" to liked, "Rejected" to rejected)
    val all = lists[source].second
    val shown = remember(source, query, all) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { it.name.lowercase().contains(q) || (it.subfolder ?: "").lowercase().contains(q) }
    }
    // Tree rows: folder headers (with depth) and files under their folder
    data class Row(val folder: String?, val depth: Int, val file: MF?)
    val rows = remember(shown, collapsed.toMap()) {
        val norm = { f: MF -> f.subfolder?.trim('/')?.takeIf { it.isNotEmpty() } }
        val grouped = shown.groupBy(norm)
        val paths = grouped.keys.filterNotNull().flatMap { p -> p.split('/').runningReduce { a, b -> "$a/$b" } }.toSet()
        val children = paths.groupBy { it.substringBeforeLast('/', "") }.mapValues { it.value.sortedBy { p -> p.lowercase() } }
        val out = ArrayList<Row>()
        fun add(path: String, depth: Int) {
            grouped[path.ifEmpty { null }]?.forEach { out += Row(null, depth, it) }
            children[path]?.forEach { c ->
                out += Row(c, depth, null)
                if (collapsed[c] != true) add(c, depth + 1)
            }
        }
        add("", 0)
        out
    }
    val pickedSize = picked.values.sumOf { it.size }

    fun toggleFolder(path: String) {
        val branch = shown.filter { inBranch(it, path) }
        val allOn = branch.isNotEmpty() && branch.all { it.uri.toString() in picked }
        if (allOn) branch.forEach { picked.remove(it.uri.toString()) }
        else {
            branch.forEach { picked[it.uri.toString()] = it }
            // Ask-each-time: propose the folder's name as the iPod playlist
            if (!playlistTouched) { playlist = lastPart(path); usePlaylist = true }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lists.forEachIndexed { i, (name, l) -> FilterChip(source == i, { source = i }, label = { Text("$name ${l.size}") }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Search ${lists[source].first}") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            val allOn = shown.isNotEmpty() && shown.all { it.uri.toString() in picked }
            TextButton(onClick = {
                if (allOn) { shown.forEach { picked.remove(it.uri.toString()) }; if (!playlistTouched) { usePlaylist = false; playlist = "" } }
                else shown.forEach { picked[it.uri.toString()] = it }
            }, enabled = shown.isNotEmpty()) { Text(if (allOn) "None" else "All") }
        }
        // Choose what's in To Do: load another phone folder without leaving this page
        TextButton(onClick = onLoadFolder, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text("Load a folder into To Do")
        }
        val ls1 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().lazyScrollbar(ls1, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)), state = ls1) {
            if (rows.isEmpty()) item {
                Text(if (all.isEmpty()) "Nothing in ${lists[source].first}. Load your music first." else "No match.",
                    Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(rows, key = { _, r -> r.folder?.let { "d:$it" } ?: "f:${r.file!!.uri}" }) { _, r ->
                if (r.folder != null) {
                    val branch = shown.filter { inBranch(it, r.folder) }
                    val on = branch.count { it.uri.toString() in picked }
                    val isCollapsed = collapsed[r.folder] == true
                    Row(Modifier.fillMaxWidth().clickable { collapsed[r.folder] = !isCollapsed }
                        .padding(start = (4 + 16 * r.depth).dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        TriStateCheckbox(
                            state = when { on == 0 -> androidx.compose.ui.state.ToggleableState.Off
                                on == branch.size -> androidx.compose.ui.state.ToggleableState.On
                                else -> androidx.compose.ui.state.ToggleableState.Indeterminate },
                            onClick = { toggleFolder(r.folder) })
                        Icon(Icons.Default.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(lastPart(r.folder), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${branch.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val f = r.file!!
                    val key = f.uri.toString()
                    val sel = key in picked
                    val ext = f.name.substringAfterLast('.', "").lowercase()
                    Row(Modifier.fillMaxWidth().clickable { if (sel) picked.remove(key) else picked[key] = f }
                        .padding(start = (28 + 16 * r.depth).dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(sel, { if (sel) picked.remove(key) else picked[key] = f })
                        Column(Modifier.weight(1f)) {
                            Text(f.name.substringBeforeLast('.'), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text(listOfNotNull(ext.uppercase(), if (f.size > 0) mb(f.size) else null).joinToString(" · "),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { showOptions = !showOptions }, verticalAlignment = Alignment.CenterVertically) {
                Text(quality.label + (if (quality.keepCompatible) " · keep MP3/AAC" else "") +
                    (if (usePlaylist && playlist.isNotBlank()) " · playlist “${playlist.trim()}”" else ""),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (showOptions) "Hide options" else "Options", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            if (showOptions) {
                QualityPicker(quality) { quality = it; it.save(context) }
                PlaylistField(usePlaylist, playlist, ui.library?.playlists?.filter { it.editable }?.map { it.name } ?: emptyList(),
                    onEnabled = { usePlaylist = it; playlistTouched = true }, onName = { playlist = it; playlistTouched = true })
            }
            Button(
                onClick = {
                    focus.clearFocus()
                    IpodManager.enqueue(context, picked.values.map { IpodManager.Source(it.uri, it.name, it.size) },
                        if (usePlaylist) playlist.trim().ifEmpty { null } else null, quality)
                    picked.clear(); playlistTouched = false; usePlaylist = false; playlist = ""
                    onQueued()
                },
                enabled = picked.isNotEmpty() && (!usePlaylist || playlist.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Upload, null); Spacer(Modifier.width(8.dp))
                Text(if (picked.isEmpty()) "Tick songs or folders to send"
                    else "Send ${picked.size} song${if (picked.size == 1) "" else "s"} · ${mb(pickedSize)}")
            }
            if (!IpodManager.isConnected && picked.isNotEmpty())
                Text("They'll be converted now and sent as soon as the iPod is connected.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Queue

@Composable
private fun QueueTab(ui: IpodManager.Ui) {
    val context = LocalContext.current
    val q = ui.queue
    val done = q.count { it.state == IpodManager.TState.DONE }
    val failed = q.count { it.state == IpodManager.TState.FAILED }
    val converting = q.count { it.state == IpodManager.TState.CONVERTING || it.state == IpodManager.TState.PREPARING }
    val ready = q.count { it.state == IpodManager.TState.READY }
    val waiting = q.count { it.state == IpodManager.TState.WAITING }
    var confirmCancel by remember { mutableStateOf(false) }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("Cancel the songs not sent yet?") },
        text = { Text("${waiting + converting + ready} songs waiting, converting or ready will be removed from the queue. Songs already on the iPod stay.") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; IpodManager.cancelPending() }) { Text("Cancel them") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep") } })
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (q.isEmpty()) "Nothing queued" else "$done of ${q.size} on the iPod", fontWeight = FontWeight.SemiBold)
                if (q.isNotEmpty()) Text(listOfNotNull(
                    if (converting > 0) "$converting converting" else null, if (waiting > 0) "$waiting waiting" else null,
                    if (ready > 0) "$ready ready" else null,
                    if (failed > 0) "$failed failed" else null).joinToString(" · ").ifEmpty { "All done" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (failed > 0) TextButton(onClick = { IpodManager.retryFailed(context) }) { Text("Retry failed") }
            if (done + failed > 0) TextButton(onClick = { IpodManager.clearFinished() }) { Text("Clear done") }
            if (waiting + converting + ready > 0) TextButton(onClick = { confirmCancel = true }) { Text("Cancel") }
        }
        if (waiting + converting + ready > 0)
            Text("Keeps going in the background — you can leave the app or lock the phone.", Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ready > 0 && !IpodManager.isConnected)
            Text("Connect the iPod to send the songs that are ready.", Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        val ls2 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().lazyScrollbar(ls2, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)), state = ls2) {
            itemsIndexed(q.reversed(), key = { _, it -> it.id }) { _, it ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (it.state) {
                        IpodManager.TState.DONE -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                        IpodManager.TState.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                        IpodManager.TState.SENDING -> Icons.Default.Upload to MaterialTheme.colorScheme.primary
                        IpodManager.TState.READY -> Icons.Default.Inventory2 to MaterialTheme.colorScheme.tertiary
                        IpodManager.TState.CONVERTING, IpodManager.TState.PREPARING -> Icons.Default.Sync to MaterialTheme.colorScheme.tertiary
                        IpodManager.TState.WAITING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(icon, null, Modifier.size(20.dp), tint = tint)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it.name.substringBeforeLast('.'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(listOfNotNull(it.detail.ifEmpty { "Waiting" }, it.playlist?.let { p -> "→ “$p”" }).joinToString("  "),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (it.state == IpodManager.TState.CONVERTING)
                            LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp))
                    }
                }
            }
            if (ui.history.isNotEmpty()) {
                item { Text("Recent", Modifier.padding(start = 14.dp, top = 12.dp), style = MaterialTheme.typography.labelLarge) }
                itemsIndexed(ui.history.take(10)) { _, h ->
                    Text((if (h.ok) "✓ " else "✗ ") + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(h.time)) + "  " + h.text,
                        Modifier.padding(horizontal = 14.dp, vertical = 2.dp), fontSize = 12.sp,
                        color = if (h.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- On iPod

@Composable
private fun SongsTab(lib: TouchLibrary, busy: Boolean) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }
    var editTrack by remember { mutableStateOf<TouchTrack?>(null) }
    val shown = remember(lib, query) {
        val q = query.trim().lowercase()
        lib.tracks.filter { q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
            .sortedBy { it.title.lowercase() }
    }
    val selTracks = lib.tracks.filter { it.id in selected && it.syncId != 0L }
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
                TextButton(onClick = { IpodManager.removeSongs(context, lib.unfinished.map { it.syncId }) }, enabled = !busy) { Text("Remove") }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), singleLine = true,
            placeholder = { Text("Search the iPod") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        val selectable = shown.filter { it.syncId != 0L }.map { it.id }
        val allSel = selectable.isNotEmpty() && selected.containsAll(selectable)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(allSel, { selected = if (allSel) selected - selectable.toSet() else selected + selectable })
            Text(if (selected.isEmpty()) "Select all (${selectable.size})" else "${selected.size} selected", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (selected.isNotEmpty()) {
            FilledTonalButton(onClick = { addTo = true }, enabled = !busy) { Text("+ Playlist") }
            if (selTracks.size == 1) FilledTonalButton(onClick = { editTrack = selTracks.first() }, enabled = !busy) { Text("Edit") }
            FilledTonalButton(onClick = { confirm = true }, enabled = !busy) { Text("Delete") }
            }
        }
        val ls3 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxWidth().lazyScrollbar(ls3, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)), state = ls3) {
            itemsIndexed(shown, key = { _, t -> t.id }) { _, t ->
                val sel = t.id in selected
                ListItem(
                    headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(listOf(t.artist, t.album).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Checkbox(sel, { selected = if (sel) selected - t.id else selected + t.id }, enabled = t.syncId != 0L) },
                    trailingContent = { if (t.syncId != 0L) IconButton(onClick = { editTrack = t }, enabled = !busy) { Icon(Icons.Default.Edit, "Edit ${t.title}") } },
                    modifier = Modifier.clickable(enabled = t.syncId != 0L) { selected = if (sel) selected - t.id else selected + t.id }
                )
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${selTracks.size} songs from the iPod?") },
        text = { Text("They're deleted from the iPod and from its playlists. The songs on this phone aren't touched.") },
        confirmButton = { TextButton(onClick = { confirm = false; IpodManager.removeSongs(context, selTracks.map { it.syncId }); selected = emptySet() }) {
            Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
    if (addTo) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTo = false },
            title = { Text("Add ${selTracks.size} songs to a playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lib.playlists.filter { it.editable }.forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable { name = p.name }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(name == p.name, { name = p.name }); Text("${p.name} (${p.trackIds.size})")
                        }
                    }
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Or a new playlist") })
                }
            },
            confirmButton = { TextButton(onClick = { addTo = false; IpodManager.addToPlaylist(context, selTracks.map { it.syncId }, name.trim()); selected = emptySet() },
                enabled = name.isNotBlank()) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addTo = false }) { Text("Cancel") } }
        )
    }
    editTrack?.let { t ->
        var title by remember(t.id) { mutableStateOf(t.title) }
        var artist by remember(t.id) { mutableStateOf(t.artist) }
        var album by remember(t.id) { mutableStateOf(t.album) }
        var albumArtist by remember(t.id) { mutableStateOf(t.albumArtist) }
        var genre by remember(t.id) { mutableStateOf(t.genre) }
        AlertDialog(
            onDismissRequest = { editTrack = null },
            title = { Text("Edit song") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Title") })
                    OutlinedTextField(artist, { artist = it }, singleLine = true, label = { Text("Artist") })
                    OutlinedTextField(album, { album = it }, singleLine = true, label = { Text("Album") })
                    OutlinedTextField(albumArtist, { albumArtist = it }, singleLine = true, label = { Text("Album artist") })
                    OutlinedTextField(genre, { genre = it }, singleLine = true, label = { Text("Genre") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    IpodManager.editSong(context, com.example.mobiledigger.ipod.TrackEdit(t.syncId, title.trim(), artist.trim(), album.trim(), albumArtist.trim(), genre.trim()))
                    editTrack = null; selected = emptySet()
                }, enabled = title.isNotBlank()) { Text("Save to the iPod") }
            },
            dismissButton = { TextButton(onClick = { editTrack = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- from the main list

/**
 * "Send to iPod" for songs selected in MobileDigger: quality + optional playlist (pre-filled with
 * their folder's name), then they're queued — converted now, sent when the iPod is connected.
 */
@Composable
fun SendToIpodDialog(files: List<MF>, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val context = LocalContext.current
    val ui by IpodManager.ui.collectAsState()
    var quality by remember { mutableStateOf(SendQuality.load(context)) }
    val folder = remember(files) { commonFolder(files) }
    var usePlaylist by remember { mutableStateOf(folder != null) }
    var playlist by remember { mutableStateOf(folder?.let { lastPart(it) } ?: "") }
    LaunchedEffect(Unit) { if (!IpodManager.isConnected) IpodManager.connect(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PhoneIphone, null) },
        title = { Text("Send ${files.size} song${if (files.size == 1) "" else "s"} to the iPod") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (IpodManager.isConnected) "${ui.deviceName ?: "iPod"} connected." else "Not connected: they'll be converted now and sent when the iPod is plugged in.",
                    style = MaterialTheme.typography.bodySmall)
                QualityPicker(quality) { quality = it; it.save(context) }
                PlaylistField(usePlaylist, playlist, ui.library?.playlists?.filter { it.editable }?.map { it.name } ?: emptyList(),
                    onEnabled = { usePlaylist = it }, onName = { playlist = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                IpodManager.enqueue(context, files.map { IpodManager.Source(it.uri, it.name, it.size) },
                    if (usePlaylist) playlist.trim().ifEmpty { null } else null, quality)
                onQueued()
            }, enabled = files.isNotEmpty() && (!usePlaylist || playlist.isNotBlank())) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
    val ls4 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxWidth().lazyScrollbar(ls4, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)), state = ls4, contentPadding = PaddingValues(vertical = 6.dp)) {
        item {
            Button(onClick = onNew, enabled = !busy && lib.tracks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(8.dp)); Text("New playlist")
            }
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
        val ls5 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).lazyScrollbar(ls5, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)), state = ls5) {
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
        val ls6 = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.fillMaxWidth().lazyScrollbar(ls6, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)), state = ls6) {
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
