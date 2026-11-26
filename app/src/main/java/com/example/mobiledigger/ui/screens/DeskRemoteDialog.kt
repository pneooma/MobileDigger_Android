package com.example.mobiledigger.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mobiledigger.remote.DeskRemote

/** Switch the Bluetooth desk remote on/off and show the PIN the computer's page asks for. */
@Composable
fun DeskRemoteDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val st by DeskRemote.state.collectAsState()
    var denied by remember { mutableStateOf(false) }
    val hasPerms = {
        DeskRemote.PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) { denied = false; DeskRemote.start(context) } else denied = true
    }
    val turnOn = { if (hasPerms()) DeskRemote.start(context) else askPerms.launch(DeskRemote.PERMISSIONS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Bluetooth, null) },
        title = { Text("Desk remote") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Control playback from a computer next to you over Bluetooth: next, previous, play/pause, like, dislike. No internet involved.",
                    style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (st.on) "On" else "Off", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(checked = st.on, onCheckedChange = { if (it) turnOn() else DeskRemote.stop() })
                }
                if (st.on) {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PIN for the computer", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(st.pin.chunked(3).joinToString(" "), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                when (st.unlocked) {
                                    0 -> "Waiting for the computer…"
                                    1 -> "Computer connected" + (st.lastCommand?.let { " · last: $it" } ?: "")
                                    else -> "${st.unlocked} computers connected"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text("A new PIN is made every time you switch this on.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                st.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (denied) Text("Bluetooth permission is needed. Allow “Nearby devices” for MobileDigger in Settings.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("On the computer: open the MobileDigger Remote page in Chrome or Edge, click Connect, pick this phone, type the PIN. " +
                    "Keys: → next · ← previous · Space play/pause · L like · D dislike.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
