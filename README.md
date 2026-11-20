# MobileDigger for Android

**Dig through your music collection like a DJ.** MobileDigger is an Android music organizer and player. You swipe through tracks to sort them: swipe right to like, left to reject, and files move into the matching folders automatically. It has waveforms and FFT spectrograms so you can judge quality at a glance.

## Features

- **Swipe to organize:** like or reject tracks, and they're moved to liked/rejected folders.
- **All common formats:** MP3, FLAC, AIFF, WAV, OGG, AAC, M4A.
- **Waveforms and spectrograms:** real-time waveform display plus FFT spectrograms, with quality settings (Fast / Balanced / High).
- **Background playback** with notification controls and a smart mini-player.
- **Multi-select and batch actions:** like or reject whole folders at once.
- **Custom subfolders** such as "Tech House" or "Warm Up", plus TODO / Liked / Rejected playlists.
- **Search** across all playlists by name or metadata.
- **Themes** (dark-mode optimized), **ZIP export** of liked songs and playlist sharing.

## Tech stack

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Coroutines |
| Architecture | MVVM with a utility layer (see [ARCHITECTURE.md](ARCHITECTURE.md)) |
| Audio | FFmpeg-based decoding, [Amplituda](Amplituda-master) for waveforms, spectrogram code adapted from [Spek](spek-X-main) |
| SDK | `minSdk 31` (Android 12), `targetSdk` / `compileSdk 36` (Android 16) |

## Build

Requirements: Android Studio (latest stable) and JDK 17.

```bash
./gradlew assembleDebug        # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease      # release build (signing config required)
```

See [RELEASE_BUILD_GUIDE.md](RELEASE_BUILD_GUIDE.md) for signing and Play Store release steps.

## Documentation

| Doc | Topic |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | App architecture and the phase-by-phase refactor |
| [WAVEFORM_SYSTEM_SUMMARY.md](WAVEFORM_SYSTEM_SUMMARY.md) | How waveforms are generated and cached |
| [ANDROID_16_IMPROVEMENTS_SUMMARY.md](ANDROID_16_IMPROVEMENTS_SUMMARY.md) | Android 16 compatibility work |
| [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md), [PLAY_STORE_COMPLIANCE_REPORT.md](PLAY_STORE_COMPLIANCE_REPORT.md) | Store listing and policy compliance |
| [PRIVACY_POLICY.md](PRIVACY_POLICY.md) | Privacy policy |
| [changelog.txt](changelog.txt) | Version history |

## Branches

- **`main`:** the published line of development.
- **`macbook-copy`:** a newer local snapshot (v13.x) that has diverged from `main` and still needs merging.
