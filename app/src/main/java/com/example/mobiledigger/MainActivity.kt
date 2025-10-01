package com.example.mobiledigger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobiledigger.audio.MusicService
import com.example.mobiledigger.ui.components.MusicPlayerScreen
import com.example.mobiledigger.ui.theme.MobileDiggerTheme
import com.example.mobiledigger.viewmodel.MusicViewModel
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    
    // Permission request launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Show a message that some features might not work
            // This could be handled through the ViewModel if needed
            // For now, we'll just log this and continue
            android.util.Log.w("MainActivity", "Some permissions were not granted")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if this is the first start and show permissions popup
        checkFirstStartAndRequestPermissions()
        
        // Handle incoming intent for audio files
        handleIncomingIntent(intent)
        
        setContent {
            val viewModel: MusicViewModel = viewModel()
            var isDarkMode by remember { mutableStateOf(viewModel.themeManager.isDarkMode.value) }
            var useDynamicColor by remember { mutableStateOf(viewModel.themeManager.useDynamicColor.value) }
            var selectedTheme by remember { mutableStateOf(viewModel.themeManager.selectedTheme.value) }
            
            // Update theme when it changes - OPTIMIZED to prevent ANR
            LaunchedEffect(Unit) {
                // Check theme changes less frequently to reduce memory pressure
                while (currentCoroutineContext().isActive) {
                    try {
                        val currentDarkMode = viewModel.themeManager.isDarkMode.value
                        val currentDynamicColor = viewModel.themeManager.useDynamicColor.value
                        val currentTheme = viewModel.themeManager.selectedTheme.value
                        
                        if (currentDarkMode != isDarkMode || currentDynamicColor != useDynamicColor || currentTheme != selectedTheme) {
                            isDarkMode = currentDarkMode
                            useDynamicColor = currentDynamicColor
                            selectedTheme = currentTheme
                        }
                        delay(3000) // Increased to 3 seconds to prevent ANR
                    } catch (e: Exception) {
                        CrashLogger.log("MainActivity", "Error in theme monitoring", e)
                        delay(5000) // Longer delay on error
                    }
                }
            }
            
            // Monitor for pending external audio files - OPTIMIZED to prevent ANR
            LaunchedEffect(Unit) {
                while (currentCoroutineContext().isActive) {
                    try {
                        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        val hasPendingAudio = prefs.getBoolean("has_pending_audio", false)
                        if (hasPendingAudio) {
                            // Force the ViewModel to check for pending audio
                            viewModel.forceCheckPendingExternalAudio()
                        }
                        delay(2000) // Reduced frequency to 2 seconds to prevent ANR
                    } catch (e: Exception) {
                        CrashLogger.log("MainActivity", "Error in pending audio monitoring", e)
                        delay(5000) // Longer delay on error
                    }
                }
            }
            
            MobileDiggerTheme(
                darkTheme = isDarkMode,
                dynamicColor = useDynamicColor,
                selectedTheme = selectedTheme
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MusicPlayerScreen(
                        viewModel = viewModel,
                        contentPadding = innerPadding
                    )
                }
            }
        }
    }
    
    private fun checkFirstStartAndRequestPermissions() {
        val prefs: SharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstStart = prefs.getBoolean("is_first_start", true)
        val permissionsShown = prefs.getBoolean("permissions_shown", false)
        
        if (isFirstStart && !permissionsShown) {
            // Show permissions popup dialog only if we haven't shown it before
            showPermissionsDialog()
            
            // Mark that we've shown the permissions dialog
            prefs.edit {
                putBoolean("is_first_start", false)
                putBoolean("permissions_shown", true)
            }
        } else {
            // Not first start or permissions already shown, just request permissions silently
            requestStoragePermissions()
        }
        
        // Request battery optimization exemption for background playback
        requestBatteryOptimizationExemption()
    }
    
    // Request battery optimization exemption for background playback
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Could not request battery optimization exemption", e)
                }
            }
        }
    }
    
    private fun showPermissionsDialog() {
        try {
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("MobileDigger needs access to your music files to play and analyze them. Please grant the following permissions:")
                .setPositiveButton("Grant Permissions") { dialog, _ ->
                    requestStoragePermissions()
                    dialog.dismiss()
                }
                .setNegativeButton("Skip") { dialog, _ ->
                    // User can still use the app, but some features might not work
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()
            
            dialog.show()
        } catch (_: Exception) {
            // If dialog fails to show, just request permissions silently
            requestStoragePermissions()
        }
    }
    
    private fun requestStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Only request essential permissions, skip MANAGE_EXTERNAL_STORAGE to avoid settings popup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Request permissions if any are needed
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop the music service when the app is destroyed
        try {
            val serviceIntent = android.content.Intent(this, MusicService::class.java)
            stopService(serviceIntent)
        } catch (_: Exception) {
            // Service might not be running, ignore
        }
    }
    
    override fun onPause() {
        super.onPause()
        // The MusicService is a foreground service and will manage its own lifecycle.
        // It should continue running even when the app is paused if music is playing.
    }
    
    // Handle incoming intents (including when app is already running)
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }
    
    // Process incoming audio file intent
    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val audioUri = intent.data
                if (audioUri != null) {
                    android.util.Log.d("MainActivity", "Received audio file intent: $audioUri")
                    CrashLogger.log("MainActivity", "Received audio file intent: $audioUri")
                    
                    // Store the URI to be processed by the ViewModel
                    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    prefs.edit {
                        putString("pending_audio_uri", audioUri.toString())
                        putBoolean("has_pending_audio", true)
                        putBoolean("show_analyze_prompt", true) // Show prompt asking user what they want to do
                    }
                    
                    // Force the ViewModel to check for pending audio immediately
                    // This ensures the file is processed even if the app is already running
                    android.util.Log.d("MainActivity", "Stored pending audio URI, ViewModel will process it")
                    CrashLogger.log("MainActivity", "Stored pending audio URI, ViewModel will process it")
                }
            }
        }
    }
}
