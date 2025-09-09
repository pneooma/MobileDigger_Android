package com.example.mobiledigger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobiledigger.audio.MusicService
import com.example.mobiledigger.ui.components.MusicPlayerScreen
import com.example.mobiledigger.ui.theme.MobileDiggerTheme
import com.example.mobiledigger.viewmodel.MusicViewModel
import android.content.SharedPreferences
import androidx.core.content.edit

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
        
        setContent {
            val viewModel: MusicViewModel = viewModel()
            MobileDiggerTheme(
                darkTheme = viewModel.themeManager.isDarkMode.value,
                dynamicColor = viewModel.themeManager.useDynamicColor.value
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
        // Optionally stop the service when app goes to background
        // This ensures the app doesn't keep running in background
        try {
            val serviceIntent = android.content.Intent(this, MusicService::class.java)
            stopService(serviceIntent)
        } catch (_: Exception) {
            // Service might not be running, ignore
        }
    }
}
