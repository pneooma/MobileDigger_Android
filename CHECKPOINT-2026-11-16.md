# Checkpoint — 2026-11-16 (v13.005)

State of MobileDigger after the full working session. Everything below is built, installed on the phone (192.168.1.114, wireless adb), and verified running.

## Versions
- App: **13.005** (versionCode 13005); session started at 12.23.
- Toolchain (this Mac): JDK 17 (`/opt/homebrew/opt/openjdk@17`), Android SDK at `/opt/homebrew/share/android-commandlinetools` (platform 36, build-tools 36.0.0). Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug`.
- Kotlin 2.0.21 → **2.2.10**, libvlc 3.6.0 → **3.7.7**, FFmpegMediaPlayer **native libs removed** (core classes kept; constructor fails gracefully via Throwable catch).

## Playback fixes (AudioManager/MusicService)
- Dead controls fixed: FFmpeg-error auto-skip used wrong broadcast action; notification/lockscreen rewind-forward seeked the service's EMPTY AudioManager (now broadcast ACTION_SEEK to ViewModel); hardware STOP sent an unmatched intent.
- Preload could audibly start the next track (shared FFmpeg player auto-start) and killed active playback (shared ExoPlayer) — both guarded.
- ExoPlayer wrong-thread access wrapped in runOnMainSync; `stopAllPlayback` now resets `isUsingVlc`; VLC temp-path protection fixed.
- VLC no longer re-copies every file per play: file:// plays in place; content:// uses URI-keyed temp cache.
- Fidelity: VLC `--no-audio-time-stretch`, replay-gain off, `-vvv` removed; ExoPlayer float output (no 16-bit truncation). True bit-perfect not possible on stock Android (mixer); this is the max.
- MusicService: second full playback stack no longer initialized; **crash fix**: onStartCommand now ALWAYS calls startForeground (ForegroundServiceDidNotStartInTimeException killed the app ~60s into idle).

## 16 KB page size (Android 16 / Play requirement)
- All bundled .so now 16 KB-aligned (VLC 3.7.7, graphics-path 1.0.1 pinned, FFmpeg natives dropped). Verified `pageSizeCompat=0` on device; warning dialog gone; APK ~25 MB smaller.

## Pi network source (new feature)
- Pi server: `http://192.168.1.129:8080` — `/library/list?path=…`, files stream with HTTP Range (verified 206); optional `X-Dashboard-Password` header; **server returns RAW unencoded URLs — app percent-encodes per segment**.
- `NetworkFileManager.kt` (browse one level + recursive listing, 1000-file cap), Pi host/password/**path** in PreferencesManager, additive load in FileManager, `setPiSource/clearPiSource/piBrowseTo` in ViewModel.
- UI: "Pi" button on empty home + "Pi Source…" in filter dropdown → dialog: host/password → **folder browser** (📁 navigate, ⬆ up, "Load this folder") — scoped loading because full-library load crashed.
- Remote http(s) URIs: VLC streams directly; AIFF validation/preload/sort/move/rename skip by scheme. Manifest: `usesCleartextTraffic="true"` (plain-HTTP Pi was blocked by default).

## Spectrogram correctness + OOM
- Time axis fixed: real sample rate (was hard-coded 44100 → 48/96 kHz axes wrong), real channel count (mono was halved by even-count guess), channel-aware caps (stereo analyzed only half the window).
- OOM crash on big WAVs fixed: primitive ShortArray accumulation (was boxed list ~20× memory), hard 12M-interleaved-sample cap in all 3 extraction paths; CrashLogger catches Throwable + bounded log tail (was building 93 MB string).

## UI
- Loading: animated card (pulsing note, spinner, elapsed seconds, green bar). Home: welcome hero card. Rejected-deletion: sliding progress banner + "✓ All rejected files deleted" flash.
- **New app icon** ("Crate Finder": magnifier + vinyl + waveform) as adaptive icon — layers in `drawable-nodpi/icon_crate_{bg,fg}.png`, wired via `mipmap-anydpi/ic_launcher_glasses.xml` (+_round). Concept PNGs: /tmp/icon_{A,B,C}.png.

## Conventions
- Bump versionCode/versionName before EVERY phone deploy (13.00x scheme).
- Desktop export for sharing: `~/Desktop/MobileDigger-v13.004.apk` (old icon — regenerate from v13.005 if sharing).

## Known caveats / next steps
- Release build has no signing config (debug-only distribution).
- VLC streams Pi files WITHOUT the password header (listing only sends it).
- Loading card shows elapsed time, not true % (needs pre-count pass).
- Hi-res (88.2/96 kHz stereo) spectrograms cover ~60–68s due to the 12M sample memory cap.
- Optional: arm64-only APK (~⅓ size) for sharing.
