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
         versionCode = 13104
         versionName = "13.104"

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
        buildConfig = true
    }
    
    
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
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.runtime:runtime")
    
    // Google Fonts for Compose
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.8")

    // File operations and permissions
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    
    // libVLC for robust audio playback (including AIFF). 3.7.7 ships 16 KB-aligned libs.
    implementation("org.videolan.android:libvlc-all:3.7.7")

    // Force the 16 KB-aligned native lib (older transitive versions were 4 KB-aligned)
    implementation("androidx.graphics:graphics-path:1.0.1")
    
    // Media3 for modern media playback
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-extractor:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media:media:1.7.0")
    
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.1")
    
    // Audio waveform processing and display
    implementation("com.github.massoudss:waveformSeekBar:5.0.2")

    // Network drives: WebDAV (OkHttp: needs PROPFIND) and SMB (smbj + BouncyCastle lightweight API,
    // because Android's stripped "BC" JCA provider lacks MD4 for NTLM).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Archives in music folders: ZIP + 7z (commons-compress, xz for LZMA2) and RAR 4 (junrar)
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.junrar:junrar:7.5.5")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}