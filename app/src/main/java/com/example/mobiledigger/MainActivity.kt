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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mobiledigger.audio.MusicService
import com.example.mobiledigger.ui.components.MusicPlayerScreen
import com.example.mobiledigger.ui.theme.MobileDiggerTheme
import com.example.mobiledigger.viewmodel.MusicViewModel

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
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request necessary permissions at startup
        requestStoragePermissions()
        
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
    
    private fun requestStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Check for MANAGE_EXTERNAL_STORAGE permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.provider.Settings.System.canWrite(this)) {
                // Request MANAGE_EXTERNAL_STORAGE permission
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
        }
        
        // Storage permissions
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            // Service might not be running, ignore
        }
    }
}
