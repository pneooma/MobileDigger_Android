package com.example.mobiledigger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.mobiledigger.audio.AudioManager
import com.example.mobiledigger.audio.WaveformGenerator
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.ui.screens.SpectrogramPopupDialog
import com.example.mobiledigger.ui.theme.MobileDiggerTheme
import com.example.mobiledigger.ui.theme.ThemeManager
import com.example.mobiledigger.ui.theme.VisualSettingsManager
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Spectrogram (MobileDigger)" in Android's Open with / Share menus: shows only the spectrum
 * analysis of the chosen audio file. It does not load it into the player or touch the playlists
 * (the regular "MobileDigger" entry still opens a file to listen to it). Closing it finishes.
 */
class SpectrogramActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = audioUri(intent)
        if (uri == null) { finish(); return }
        CrashLogger.log("SpectrogramActivity", "Spectrogram requested for $uri")

        // Own instance: decoding for the spectrogram never interferes with what the player is doing
        val audioManager = AudioManager(applicationContext)
        val themeManager = ThemeManager(applicationContext)
        val visual = VisualSettingsManager(applicationContext)

        setContent {
            val settings by visual.settings
            MobileDiggerTheme(
                darkTheme = themeManager.isDarkMode.value,
                dynamicColor = themeManager.useDynamicColor.value,
                selectedTheme = themeManager.selectedTheme.value,
                fontFamilyName = settings.fontFamily
            ) {
                var file by remember { mutableStateOf<MusicFile?>(null) }
                var waveform by remember { mutableStateOf<IntArray?>(null) }
                var failed by remember { mutableStateOf(false) }
                LaunchedEffect(uri) {
                    val f = withContext(Dispatchers.IO) { describe(uri) }
                    if (f == null) { failed = true; return@LaunchedEffect }
                    file = f
                    waveform = withContext(Dispatchers.IO) {
                        runCatching { WaveformGenerator(applicationContext).generateWaveform(uri, 512, f.name) }.getOrNull()
                    }
                }
                when {
                    failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Couldn't open this file", style = MaterialTheme.typography.bodyLarge)
                    }
                    file != null -> SpectrogramPopupDialog(
                        musicFile = file,
                        audioManager = audioManager,
                        onDismiss = { finish() },
                        waveformData = waveform
                    )
                }
            }
        }
    }

    private fun audioUri(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }

    /** Name, size and duration of the shared/opened file (no copy is made). */
    private fun describe(uri: Uri): MusicFile? = try {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        var size = 0L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val duration = runCatching {
            val r = android.media.MediaMetadataRetriever()
            try { r.setDataSource(this, uri); r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
            finally { r.release() }
        }.getOrDefault(0L)
        MusicFile(uri = uri, name = name, duration = duration, size = size)
    } catch (e: Exception) {
        CrashLogger.log("SpectrogramActivity", "Can't read $uri", e)
        null
    }
}
