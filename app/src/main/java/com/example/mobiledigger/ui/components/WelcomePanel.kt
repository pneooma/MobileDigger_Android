package com.example.mobiledigger.ui.components

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.rememberScrollState

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobiledigger.R

// Follows the selected theme (was a fixed green on near-black)
private val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary

/** Readable tail of a SAF tree URI: "primary:Music/To triage" → "Music/To triage". */
fun folderLabel(uriString: String?): String? {
    if (uriString.isNullOrBlank()) return null
    val path = runCatching { android.net.Uri.parse(uriString).path }.getOrNull() ?: uriString
    val afterColon = path.substringAfterLast(':', path)
    return afterColon.trim('/').split('/').takeLast(2).joinToString("/").ifBlank { "Phone storage" }
}

@Composable
fun WelcomePanel(
    lastSource: String?,
    destinationName: String?,
    activeDriveName: String?,
    savedDriveCount: Int,
    recentSources: List<String>,
    recentDestinations: List<String>,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    onResume: () -> Unit,
    onOpenDrives: () -> Unit,
    onOpenIpod: () -> Unit = {},
    onLoadRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onSetDestination: (String) -> Unit,
    onRemoveDestination: (String) -> Unit,
    sourceMode: String = "BOTH",
    onSourceMode: (String) -> Unit = {},
    /** Opens the results folder in the phone's file manager. */
    onOpenSorted: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hero()

        // Quick resume
        val lastLabel = folderLabel(lastSource)
        if (lastLabel != null || activeDriveName != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)))
                    .clickable(onClick = onResume)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pick up where you left off", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        listOfNotNull(
                            lastLabel?.takeIf { sourceMode != "NETWORK" || activeDriveName == null },
                            activeDriveName?.let { "$it (network)" }?.takeIf { sourceMode != "PHONE" || lastLabel == null }
                        ).joinToString(" + "),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        // Step 1
        StepLabel(1, "Where's your music?")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceTile(
                icon = Icons.Default.PhoneAndroid, title = "On this phone", subtitle = "Pick a folder",
                selected = false, modifier = Modifier.weight(1f), onClick = onPickSource
            )
            SourceTile(
                icon = Icons.Default.Router, title = "Drives",
                subtitle = when {
                    activeDriveName != null -> activeDriveName
                    savedDriveCount > 0 -> "$savedDriveCount saved"
                    else -> "USB, NAS, PC or Pi"
                },
                selected = activeDriveName != null, modifier = Modifier.weight(1f), onClick = onOpenDrives
            )
        }
        // iPod touch: send music (converted in the background) and manage its playlists
        val ipod by com.example.mobiledigger.ipod.IpodManager.ui.collectAsState()
        OutlinedCard(onClick = onOpenIpod, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneIphone, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("iPod", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            ipod.pending > 0 -> "${ipod.pending} songs on their way to the iPod"
                            ipod.library != null -> "${ipod.deviceName ?: "iPod"} · ${ipod.library!!.tracks.size} songs · ${ipod.library!!.playlists.size} playlists"
                            else -> "Send music and manage playlists on an iPod touch"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Both a phone folder and a network drive are set up: let the user pick what to load
        if (lastLabel != null && activeDriveName != null) {
            LoadFromChooser(sourceMode, onSourceMode)
        }

        // Step 2
        StepLabel(2, "Where do sorted tracks go?")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    if (destinationName != null) Accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(20.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onPickDestination)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBubble(if (destinationName != null) Icons.Default.CheckCircle else Icons.Default.CreateNewFolder, destinationName != null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(destinationName ?: "Choose a folder", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (destinationName != null) "Liked and Rejected folders live here" else "The app makes Liked and Rejected folders inside it",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // "Open" = show the results folder (Liked / Rejected files) in the phone's file manager
            if (destinationName != null && onOpenSorted != null) {
                FilledTonalButton(
                    onClick = onOpenSorted,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("Open", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                Spacer(Modifier.width(8.dp))
            }
            Text(if (destinationName != null) "Change" else "Pick", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        // Recent folders
        if (recentSources.isNotEmpty() || recentDestinations.isNotEmpty()) {
            val total = recentSources.size.coerceAtMost(20) + recentDestinations.size.coerceAtMost(5)
            Text(if (total > 4) "Recent · $total  (scroll for more)" else "Recent",
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            // Scrolls on its own once there are more than ~4 entries
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .heightIn(max = 290.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                recentSources.take(20).forEach { uri ->
                    RecentRow(Icons.Default.LibraryMusic, folderLabel(uri) ?: uri, "Music folder", "Load",
                        onAction = { onLoadRecent(uri) }, onRemove = { onRemoveRecent(uri) })
                }
                recentDestinations.take(5).forEach { uri ->
                    RecentRow(Icons.Default.Inventory2, folderLabel(uri) ?: uri, "Sorted tracks", "Use",
                        onAction = { onSetDestination(uri) }, onRemove = { onRemoveDestination(uri) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Hero() {
    val t = rememberInfiniteTransition(label = "hero")
    val glow by t.animateFloat(0.25f, 0.55f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val bob by t.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(Brush.radialGradient(listOf(Accent.copy(alpha = glow), Color.Transparent), radius = 700f))
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).scale(bob).clip(RoundedCornerShape(20.dp))) {
                Image(painterResource(R.drawable.icon_crate_bg), null, Modifier.fillMaxSize().scale(1.35f))
                Image(painterResource(R.drawable.icon_crate_fg), null, Modifier.fillMaxSize().scale(1.35f))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("MOBILEDIGGER", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Let's dig.", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
                Text("Listen. Keep the gems. Bin the rest.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun StepLabel(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconBubble(icon: ImageVector, active: Boolean) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) Accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (active) Accent else MaterialTheme.colorScheme.onSecondaryContainer) }
}

@Composable
private fun SourceTile(icon: ImageVector, title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, selected)
            Spacer(Modifier.weight(1f))
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentRow(icon: ImageVector, label: String, kind: String, action: String, onAction: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onAction).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(action) }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, "Remove from recent", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadFromChooser(mode: String, onMode: (String) -> Unit) {
    val options = listOf("PHONE" to "Phone", "NETWORK" to "Network", "BOTH" to "Both")
    Column(Modifier.fillMaxWidth()) {
        Text("Load from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (key, label) ->
                SegmentedButton(
                    selected = mode == key,
                    onClick = { onMode(key) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size)
                ) { Text(label) }
            }
        }
    }
}


/**
 * Opens [folderUri] (a SAF tree or document URI) in the phone's file manager. Tries the folder view
 * of the Files app first; returns false if nothing on the phone can show a folder.
 */
fun openFolderInFiles(context: android.content.Context, folderUri: android.net.Uri): Boolean {
    val docUri = try {
        if (android.provider.DocumentsContract.isTreeUri(folderUri) && !android.provider.DocumentsContract.isDocumentUri(context, folderUri))
            android.provider.DocumentsContract.buildDocumentUriUsingTree(folderUri, android.provider.DocumentsContract.getTreeDocumentId(folderUri))
        else folderUri
    } catch (_: Exception) { folderUri }
    val attempts = listOf(
        android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(docUri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR),
        android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(docUri, "resource/folder"),
        android.content.Intent(android.content.Intent.ACTION_VIEW, docUri)
    )
    for (i in attempts) {
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(i); return true } catch (_: Exception) { }
    }
    return false
}
