# Privacy Policy for MobileDigger

**Last Updated:** November 4, 2024  
**Effective Date:** November 4, 2024

---

## Overview

MobileDigger is a local music organization app developed by Alin Florescu. This privacy policy explains how the app handles user data and protects user privacy.

## Our Commitment

**We DO NOT collect, store, transmit, or share any personal data.**

MobileDigger is designed with privacy as a core principle. All audio processing, file organization, and data storage occur entirely on your device.

---

## Data Collection

**WE COLLECT NO DATA.** The app:

✅ **Does NOT** collect personal information  
✅ **Does NOT** upload files to any server  
✅ **Does NOT** track user behavior  
✅ **Does NOT** use analytics services  
✅ **Does NOT** use crash reporting services  
✅ **Does NOT** require user accounts or registration  
✅ **Does NOT** share data with third parties  

All your music files and preferences stay on your device.

---

## Permissions Explained

MobileDigger requests the following Android permissions to function properly:

### 📂 **File Access Permissions**
- **READ_EXTERNAL_STORAGE** (Android 12 and below)
- **WRITE_EXTERNAL_STORAGE** (Android 12 and below)  
- **MANAGE_EXTERNAL_STORAGE** (All Files Access)
- **READ_MEDIA_AUDIO** (Android 13+)

**Why:** To scan, read, and organize your music files locally. Files are moved between folders based on your like/dislike decisions. No files are uploaded or transmitted.

### 🎵 **Media Playback Permissions**
- **FOREGROUND_SERVICE_MEDIA_PLAYBACK**
- **WAKE_LOCK**

**Why:** To play music continuously in the background and prevent device sleep during playback.

### 🔔 **Notification Permissions**
- **POST_NOTIFICATIONS** (Android 13+)

**Why:** To display playback controls and currently playing song information in the notification shade.

### 📳 **Haptic Feedback Permission**
- **VIBRATE**

**Why:** To provide tactile feedback when you swipe to like/dislike songs, improving user experience.

### 🔋 **Battery Optimization Permission**
- **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**

**Why:** To prevent Android from stopping music playback when the app is in the background. You explicitly grant this permission when requested.

### 📡 **WiFi State Permission**
- **ACCESS_WIFI_STATE**

**Why:** Used only for internal debugging features during development. This permission may be removed in future updates.

### 🎧 **Bluetooth Permissions** (Unused - Will be removed)
- **BLUETOOTH**
- **BLUETOOTH_ADMIN**

**Note:** These were declared for potential future smartwatch integration but are currently unused and will be removed.

---

## Data Storage

### Local Storage Only

All app data is stored locally on your device using Android's standard storage mechanisms:

- **Preferences**: Stored in Android's encrypted SharedPreferences
- **Playlist States**: Maintained locally (liked songs, rejected songs, TODO list)
- **File Organization**: Files are moved within your device's storage only
- **Crash Logs**: Stored locally in app's private directory (never transmitted)
- **Settings**: Theme preferences, waveform settings, etc. - all local

### No Cloud Storage

MobileDigger does not use:
- ❌ Cloud databases
- ❌ Remote servers
- ❌ External APIs
- ❌ Analytics platforms
- ❌ Advertising networks

---

## Third-Party Libraries

MobileDigger uses these open-source libraries for local audio processing:

### Audio Playback
- **VLC (VideoLAN)** - Local audio playback engine
  - License: LGPL/GPL
  - Privacy: Processes audio locally, no data transmission
  - Website: https://www.videolan.org/

- **FFmpeg** - Audio format support (AIFF, FLAC, etc.)
  - License: LGPL/GPL
  - Privacy: Local audio decoding only
  - Website: https://ffmpeg.org/

- **Media3 (AndroidX)** - Modern media playback framework
  - Developer: Google/AndroidX
  - Privacy: Local media handling
  - Website: https://developer.android.com/jetpack/androidx/releases/media3

### Audio Analysis
- **Amplituda** - Waveform generation
  - License: Apache 2.0
  - Privacy: Local audio analysis
  - GitHub: https://github.com/lincollincol/Amplituda

- **JTransforms** - FFT for spectrograms
  - License: BSD
  - Privacy: Local mathematical computations
  - GitHub: https://github.com/wendykierp/JTransforms

**Important:** None of these libraries collect, transmit, or share user data. All processing is performed locally on your device.

---

## Network Usage

### Internet Permission

The app declares the `INTERNET` permission in the AndroidManifest due to library requirements. However:

✅ **No network requests are made by the app**  
✅ **No data is transmitted over the internet**  
✅ **No analytics or tracking occur**  
✅ **Files are never uploaded**  

This permission may be required by VLC or FFmpeg libraries for potential streaming features (not used in MobileDigger) and will be evaluated for removal.

---

## Children's Privacy

MobileDigger does not knowingly collect information from anyone, including children under 13. Since we collect no data at all, the app is safe for users of all ages.

**COPPA Compliance:** No personal information is collected from any users.

---

## Data Security

While we don't collect data, we take security seriously:

- ✅ Files remain on your device with Android's built-in encryption
- ✅ Permissions follow Android's security model
- ✅ No external data transmission means no interception risk
- ✅ App uses Storage Access Framework (SAF) for secure file operations
- ✅ No passwords or credentials are stored (no account system)

---

## Your Rights

### Data Access
All your data (music files, preferences) is already on your device and accessible to you at any time.

### Data Deletion
To delete all app data:
1. **Settings → Apps → MobileDigger → Storage → Clear Data**, or
2. **Uninstall the app** - removes all app data automatically

Your music files will remain in their folders (we never delete your music without your explicit action).

### Data Portability
Since all data is local and no proprietary formats are used, your music files are already portable and accessible to any app.

---

## Changes to This Policy

We may update this privacy policy to reflect changes in:
- Android operating system requirements
- App features or functionality
- Legal or regulatory requirements

**Notification of Changes:**
- Updated policy will be included in app updates
- "Last Updated" date will be modified
- Material changes will be highlighted in the app's release notes

**Your Continued Use:** By continuing to use MobileDigger after policy updates, you accept the revised policy.

---

## App Permissions Summary Table

| Permission | Purpose | Data Access | Data Transmission |
|------------|---------|-------------|-------------------|
| File Access | Read/organize music files | Local files only | None |
| Media Playback | Play audio in background | Local files only | None |
| Notifications | Show playback controls | Song metadata | None |
| Vibration | Haptic feedback | None | None |
| Battery Optimization | Uninterrupted playback | None | None |
| WiFi State | Debug features (dev only) | None | None |

---

## Legal Compliance

### GDPR (European Union)
MobileDigger is GDPR-compliant because:
- ✅ No personal data is collected
- ✅ No processing of EU residents' data
- ✅ No data controllers or processors involved
- ✅ No cookies or tracking

### CCPA (California)
MobileDigger is CCPA-compliant because:
- ✅ No personal information is collected or sold
- ✅ No tracking across apps/websites
- ✅ California residents have nothing to opt-out of

### Other Jurisdictions
Since no data is collected, MobileDigger complies with data protection laws worldwide.

---

## Open Source

MobileDigger is open source and available for review:
- **GitHub Repository:** https://github.com/pneooma/MobileDigger_Android
- **License:** [Specify your license]

You can inspect the source code to verify our privacy claims.

---

## Contact Information

If you have questions, concerns, or requests regarding this privacy policy:

**Developer:** Alin Florescu  
**Email:** [YOUR EMAIL ADDRESS]  
**GitHub:** https://github.com/pneooma/MobileDigger_Android  
**GitHub Issues:** https://github.com/pneooma/MobileDigger_Android/issues

**Response Time:** We aim to respond to privacy inquiries within 48 hours.

---

## Disclaimer

This app is provided "as is" without warranties. While we've designed MobileDigger to respect your privacy, you use the app at your own risk. Always maintain backups of important music files.

---

## Acknowledgments

Thank you for choosing MobileDigger. Your trust in our privacy-first approach is greatly appreciated.

**Remember:** Your music stays on your device. Always.

---

**Privacy Policy Version:** 1.0  
**App Version:** 10.46  
**Last Review Date:** November 4, 2024  

---

*This privacy policy is effective as of the date above and applies to all users of MobileDigger.*

