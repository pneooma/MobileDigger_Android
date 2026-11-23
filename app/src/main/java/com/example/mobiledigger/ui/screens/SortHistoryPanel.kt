package com.example.mobiledigger.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mobiledigger.util.SortHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → History: every track ever liked or rejected, newest first, with when, where it came
 * from (full path) and where it went. Search by any part of the name or path.
 */
@Composable
fun SortHistoryPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by SortHistory.version.collectAsState()
    val all = remember(version) { SortHistory.all() }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Char?>(null) } // null = all, 'L', 'R'
    val shown = remember(all, query, filter) {
        val q = query.trim().lowercase()
        all.filter { e ->
            (filter == null || e.verdict == filter) &&
                (q.isEmpty() || e.name.lowercase().contains(q) ||
                    e.origin?.lowercase()?.contains(q) == true || e.destination?.lowercase()?.contains(q) == true)
        }
    }
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    Column(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search name, folder or path") }
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All ${all.size}") })
            FilterChip(selected = filter == 'L', onClick = { filter = 'L' }, label = { Text("Liked ${all.count { it.verdict == 'L' }}") })
            FilterChip(selected = filter == 'R', onClick = { filter = 'R' }, label = { Text("Rejected ${all.count { it.verdict == 'R' }}") })
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (all.isEmpty()) "Nothing sorted yet" else "No match",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp)) {
            items(shown, key = { it.name + "|" + it.size }) { e -> HistoryRow(e, dateFmt, context) }
        }
    }
}

@Composable
private fun HistoryRow(e: SortHistory.Entry, dateFmt: SimpleDateFormat, context: Context) {
    val liked = e.verdict == 'L'
    val accent = if (liked) Color(0xFF22C55E) else Color(0xFFEF4444)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (liked) "♥ Liked" else "👎 Rejected") +
                        (e.time?.let { " · " + dateFmt.format(Date(it)) } ?: "") +
                        " · " + fmtSize(e.size),
                    style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.Bold
                )
                Text("From: " + (e.origin ?: "not recorded (sorted before this was kept)"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.destination?.let {
                    Text("To: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (e.origin != null) IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Path", e.origin))
                Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy path", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.getDefault(), "%.1f GB", b / (1024.0 * 1024 * 1024))
    else -> String.format(Locale.getDefault(), "%.1f MB", b / (1024.0 * 1024))
}
