plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.mobiledigger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mobiledigger"
        minSdk = 31
        targetSdk = 36
        versionCode = 927
        versionName = "9.27"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    
    // Dynamic delivery disabled for now
    // dynamicFeatures += setOf(":ffmpeg")
    
    lint {
        disable.addAll(listOf(
            "UnsafeOptInUsageError",
            "UnsafeImplicitIntentLaunch",
            "MissingApplicationAttribute"
        ))
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Enhanced Compose features (using BOM versions)
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    
    // File operations and permissions
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    
    // Audio processing - FFmpegMediaPlayer for AIFF support
    implementation("com.github.wseemann:FFmpegMediaPlayer-core:1.0.5")
    implementation("com.github.wseemann:FFmpegMediaPlayer-native:1.0.5")
    
    // Media3 for modern media playback
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-extractor:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media:media:1.7.0")
    
    
    // Image processing for spectrograms
    implementation("androidx.palette:palette-ktx:1.0.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.1")
    
    // Audio waveform processing and display
    implementation("com.github.lincollincol:amplituda:2.2.2")
    implementation("com.github.massoudss:waveformSeekBar:5.0.2")
    
    // Optimized FFT library for better spectrogram performance
    implementation("com.github.wendykierp:JTransforms:3.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}