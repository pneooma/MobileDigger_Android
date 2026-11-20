# MobileDigger — Code Audit & Fix Handoff

**For:** an AI engineer implementing fixes.
**Repo:** `MobileDigger` (Android, package `com.example.mobiledigger`), branch `v12.00-release`.
**Toolchain:** AGP 8.12.0, Kotlin 2.0.21, Gradle 8.13, compileSdk/targetSdk 36, **minSdk 31**, Jetpack Compose.
**Module:** only `:app` is built (`settings.gradle.kts`). `Amplituda-master/` and `spek-X-main/` are reference copies, NOT modules — ignore them.

## CRITICAL CONTEXT — read before doing anything

1. **The app currently works. Do not regress it.** Every change below is graded by regression risk. Apply only what you are told, the way you are told.
2. **There is NO multi-process split.** Despite comments/notes claiming a `:main`/`:music` split, `AndroidManifest.xml` has **zero `android:process` attributes**. The strings `main_activity`/`music_service` are `android:attributionTags`, not process names. Everything runs in ONE process.
3. **Dual-player architecture:** The REAL player lives in `MusicViewModel.audioManager` (`MusicViewModel.kt:66`). `MusicService` constructs its OWN `AudioManager(this)` (`MusicService.kt:268`) + an ExoPlayer that **never get a media item** — they are a notification/MediaSession shell. State is shipped ViewModel→Service via Intent extras. This redundancy is the root of the recurring notification play/pause/seek bugs but is OUT OF SCOPE for this pass (too risky to refactor now).
4. **`USE_VLC_FOR_ALL = true`** (`AudioManager.kt:62`) — VLC is the active backend for every file. ExoPlayer and FFmpegMediaPlayer are fallbacks.
5. **AIFF is a core feature.** `FFmpegMediaPlayer` is the last-resort AIFF decoder. Do NOT remove it.
6. **Build after each group and confirm it compiles.** Where physical-device testing is noted, flag it for the human — you cannot do it.

Line numbers are approximate (file drift). Always grep/confirm the symbol before editing.

---

## TIER 1 — SAFE. Apply these. Zero expected behavior change.

### 1.1 Add `setPackage(packageName)` to internal broadcasts
**Why:** Implicit broadcasts leak internal control actions (NEXT/LIKE/DISLIKE/etc.) app-wide; on targetSdk 36 implicit broadcasts are also restricted. All consumers are `RECEIVER_NOT_EXPORTED` in this package, so scoping to the package keeps them working.
**Files/locations:**
- `audio/MusicService.kt` — `sendBroadcast(...)` at approx lines 168, 172, 199, 203, 207, 211, 348, 799, 820.
- `audio/MediaButtonReceiver.kt` — `sendBroadcast(...)` at approx lines 30, 35, 40, 47, 51, 56.
**How:** For each, change `context.sendBroadcast(Intent(ACTION_X))` → `context.sendBroadcast(Intent(ACTION_X).apply { setPackage(context.packageName) })` (use `packageName` inside the Service).
**Verify:** Build. Notification buttons (play/pause/next/prev/like/dislike) still work in-app.

### 1.2 `allowBackup="false"`
**File:** `AndroidManifest.xml` (`<application android:allowBackup="true"` ~line 29).
**How:** set to `"false"`. Backup rules are empty templates; SAF grants aren't backed up anyway.

### 1.3 Remove unused permissions
**File:** `AndroidManifest.xml`.
- Remove `BLUETOOTH` (~line 20) and `BLUETOOTH_ADMIN` (~line 21) — zero code references.
- Remove `ACCESS_WIFI_STATE` (~line 14) — zero code references.
- **Do NOT** touch `WRITE_EXTERNAL_STORAGE` here — see 2.6 (it has a code dependency).
- **Do NOT** remove `INTERNET` — used by the downloadable Google Fonts provider.

### 1.4 Remove unused Gradle dependencies
**File:** `app/build.gradle.kts`. All verified zero imports in `app/src`:
- JTransforms (~line 113) — FFT is hand-rolled in `AudioManager.kt`.
- amplituda (~line 109) — `waveformSeekBar` does NOT transitively need it.
- palette-ktx (~line 96)
- media3-ui (~line 88) — keep media3-exoplayer/session/common/extractor/datasource (those ARE used).
- runtime-livedata (~line 70)
- navigation-compose (~line 103)
- animation-graphics (~line 66)
**Verify:** Gradle sync + build.

### 1.5 Delete the dead WaveformSettings feature chain
**Delete these 4 files** (verified zero external call sites):
- `app/src/main/java/com/example/mobiledigger/utils/WaveformGenerator.kt` (the `object` — NOT the real one)
- `app/src/main/java/com/example/mobiledigger/ui/components/WaveformSettingsDialog.kt`
- `app/src/main/java/com/example/mobiledigger/ui/components/WaveformWarningDialog.kt`
- `app/src/main/java/com/example/mobiledigger/model/WaveformSettings.kt`
**⚠️ COUPLED EDIT (build breaks otherwise):** delete the now-dangling import at `MusicPlayerScreen.kt:~45`:
`import com.example.mobiledigger.utils.WaveformGenerator`
**⚠️ DO NOT confuse with the REAL generator** `audio/WaveformGenerator.kt` (a `class`, used by `SharedWaveformState.kt:12,106`) — KEEP it. Also do not touch `AudioManager.getCurrentSettings()` — unrelated method.

### 1.6 Delete `AnimationSpecs.kt` (the dead duplicate)
**Delete:** `app/src/main/java/com/example/mobiledigger/ui/components/AnimationSpecs.kt`
Contains the dead `ui.components.rememberOptimizedAnimationSpecs` + `OptimizedAnimationSpecs` (zero usages).
**⚠️ DO NOT delete `util/AnimationUtils.kt` or its methods** — `util.rememberOptimizedAnimationSpecs` IS used at `MusicPlayerScreen.kt:~296` and its helpers (`getOptimizedDuration/Spring/Tween`) are live transitively.

### 1.7 Remove dead helpers
- Delete file `app/src/main/java/com/example/mobiledigger/util/FileOperationHelper.kt` AND its field at `MusicViewModel.kt:~125` (`fileOperationHelper`). Zero usages.
- Remove the `errorHandler` field at `MusicViewModel.kt:~122` and the methods `runSafely` / `runSafelyOrNull` in `util/ErrorHandler.kt`. Zero callers. **KEEP** `ErrorHandler.createHandler` + private `handleException` (file stays).
- Remove dead methods (each grep-confirmed zero callers): `ResourceManager.delayWithProgress`, `PerformanceProfiler.measureIterations`, `PerformanceProfiler.clearStats`, `MemoryMonitor.stopMonitoring`.
  - **⚠️ KEEP** `ResourceManager.withMediaMetadataRetriever` and `withMediaExtractor` — they ARE used by `extractDuration`/`extractDurationWithFallback`. The earlier "dead" claim was wrong.

### 1.8 Logging cleanup (partial — be precise)
In `util/UnifiedLogger.kt` and `util/CrashLogger.kt`:
- SAFE to remove (zero callers): `CrashLogger.startTiming/endTiming`, `UnifiedLogger.startTiming/endTiming`, `UnifiedLogger.writeToFile` (private no-op), `UnifiedLogger.getLogCount`, `UnifiedLogger.clearLogs`.
- `UnifiedLogger.flushLogs` (private no-op) is called internally at line ~73 (`if (throwable != null) flushLogs()`) and by `forceFlush`. Remove it ONLY if you also remove `forceFlush` and that line-73 call together.
- **⚠️ DO NOT remove** `UnifiedLogger.initialize` (used `MobileDiggerApplication.kt:29`) or `UnifiedLogger.setResultsFolder` (used `FileManager.kt:94`). Earlier "dead" claim was wrong.
- **⚠️ DO NOT touch `CrashLogger.log()`** or its file-write path — it backs the uncaught-exception handler (`MobileDiggerApplication.kt:53-61`).

### 1.9 FIX 3 — Remove the cache-trim reflection
**Where:** `MusicViewModel.kt:~1969-2009` reflects `audioManager` (already a public `val` at line 66) and private `AudioManager` fields `currentFFmpegDataSourcePath` / `currentTempWavPath`, wrapped in `catch(_)`.
**Why safe + better:** Reflection currently fails SILENTLY on rename → can let the cache delete the in-use temp file. Direct access removes that failure mode. Cannot delete MORE files (the `protected` set only ever shrinks the deletion candidate list).
**How:**
1. Add a public accessor to `AudioManager`, e.g. `fun getProtectedTempPaths(): Set<String> = setOfNotNull(currentFFmpegDataSourcePath, currentTempWavPath)`.
2. In `MusicViewModel`, replace the reflection block with `audioManager.getProtectedTempPaths()` merged into the existing `protected` set passed to `CacheManager.trimDiskCache`.
**Verify:** Build; play a file; trigger cache trim; the playing file is not deleted.

### 1.10 FIX 7 — Move VLC position/duration polling off the Main thread
**Where:** `MusicViewModel.kt:~1891-1937`. Poll loop runs on `viewModelScope.launch {}` (Main) and calls `audioManager.getCurrentPosition()` / `getDuration()` → libVLC native getters, at ~30 Hz.
**Why safe:** Native getters aren't Main-bound; `MutableStateFlow.value` is thread-safe; the existing `@Volatile suppressPositionPushUntil` guard (set in `seekTo`, `~line 208/1190`) already protects in-flight seeks (700 ms window >> 33 ms poll).
**How:** Read position/duration inside `withContext(Dispatchers.IO)` (or run the loop on IO/Default) and assign the StateFlows directly. **KEEP the existing suppress guard untouched.** If the loop also calls `updateNotification`/`updateNotificationPosition`, keep those on Main (marshal back).
**Verify:** Build; seekbar tracks playback smoothly; seeking is not clobbered by the poll.

---

## TIER 2 — NEEDS CARE. Apply only with the exact guardrails. Test after each.

### 2.1 FIX 6 — Remove `Thread.sleep(50)` from the waveform `LaunchedEffect`
**Where:** `SharedWaveformState.kt:~81-84` — `System.gc(); Thread.sleep(50)` on the Main thread on every track change.
**How (do NOT just delete both):**
- DELETE the `Thread.sleep(50)` (removes ~50 ms UI freeze per track switch).
- MOVE `System.gc()` into the existing `scope.launch(Dispatchers.IO)` block (~line 103), right before `WaveformGenerator.generateWaveform`, so the OOM-avoidance hint still precedes the big allocation but no longer blocks Main.
**Verify:** Rapid track switching is smoother; no OOM on large AIFF.

### 2.2 FIX 1 — Make `isCurrentlyPlaying()` honest for VLC (method-only; do NOT change callers)
**Where:** `AudioManager.kt:~1065-1076` — branches on `isUsingFFmpeg` else `exoPlayerFallback?.isPlaying`; never checks VLC.
**Subtlety:** On the SERVICE's file-less AudioManager this always returns false TODAY, and callers compensate via the IPC-synced `isPlaying` flag — that's load-bearing. Adding a VLC branch is a no-op on the Service instance (its vlcBackend has no media), so it's safe IF you don't change the callers.
**How:** Add `if (isUsingVlc) return vlcBackend?.isPlaying() ?: false` before the existing branches. (`VlcAudioBackend.isPlaying()` exists and is event-driven/correct.)
**⚠️ DO NOT** modify the callers in `MusicService.kt` (217, 511, 623, 675, 832) or `togglePlayPause` (~803). Leave the broadcast/IPC toggle path alone — changing it risks double-toggle.
**Verify:** Build; on-device, confirm notification play/pause state still correct (flag for human device test).

### 2.3 Gate verbose logging behind `BuildConfig.DEBUG`
**Prerequisite (or it won't compile):** add `buildConfig = true` to `buildFeatures` in `app/build.gradle.kts` (currently only `compose = true`).
**Where:** `UnifiedLogger.kt:~70` `android.util.Log.d(...)` and any URI/filename logging.
**How:** wrap in `if (BuildConfig.DEBUG) { ... }`. Import `com.example.mobiledigger.BuildConfig`. Leave `CrashLogger` crash reporting intact.

### 2.4 Version string from `BuildConfig`
**Prerequisite:** `buildConfig = true` (same as 2.3).
**Where:** `MusicPlayerScreen.kt:~888` hardcoded `text = "v12.xx"`.
**How:** `text = "v${BuildConfig.VERSION_NAME}"` (note: `VERSION_NAME` has no `v` prefix). Eliminates the manual 3-place version drift.

### 2.5 FIX (VLC temp-file eviction) — only with an active-file guard
**Problem:** The VLC playback temp file (created `AudioManager.kt:~706` via `copyUriToTempFile`) is tracked NOWHERE — not in `tempFileCache`, not in the protected fields. So it currently leaks (never evicted until `clearAllCaches`). Naively registering it in `tempFileCache` could let the LRU **delete the file VLC is currently playing** (the eviction guards at `AudioManager.kt:~243` and `~1118` only protect `currentFFmpegDataSourcePath`).
**How (in this order):**
1. Add a field `currentVlcTempPath` (set when the VLC temp file is created ~line 712; clear on stop/reset/new-load).
2. Extend BOTH eviction guards (`~243` and `~1118`) to also skip `currentVlcTempPath`.
3. Add `currentVlcTempPath` to `getProtectedTempPaths()` from 1.9.
4. THEN register the temp file into `tempFileCache` for normal LRU eviction of OLD temps.
**Verify:** Long playback session doesn't accumulate temp files in cacheDir; the active file is never deleted mid-playback.

### 2.6 Remove `MANAGE_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE`
**Why:** All real I/O is SAF/DocumentFile/ContentResolver; these perms are over-broad (Play-policy risk) and largely vestigial on minSdk 31.
**⚠️ COUPLED EDITS:**
- `WRITE_EXTERNAL_STORAGE` is actively requested at `MainActivity.kt:~227-230` (pre-Android-13 `else` branch, reachable on API 31-32). DELETE that request block too, or the runtime request dangles.
- Verify the raw-`File` scan branch at `FileManager.kt:~31-35` (activates only on a `file://` folder URI) is unreachable — folders are picked via SAF (`content://` tree URIs) so it's effectively dead. Confirm no entry point feeds a `file://` directory.
**How:** Remove both `<uses-permission>` lines from `AndroidManifest.xml` (~7-8). Optionally also drop `READ_EXTERNAL_STORAGE` (no-op on 31+, superseded by `READ_MEDIA_AUDIO`).
**Verify:** Pick a source folder, scan, play, sort (move) a file, share/ZIP export — all via SAF. Flag for human on-device test.

---

## TIER 3 — RISKY / DO NOT DO in this pass (documented for completeness)

- **DO NOT remove `FFmpegMediaPlayer-native:1.0.5`** (`build.gradle.kts:~80-84`). It is the last-resort AIFF decoder in the VLC→FFmpeg fallback chain (`AudioManager.kt:616-646`, listeners 358-424, crash-recovery 388-394). Removing it makes FFmpeg-only AIFF files unplayable. AIFF is a core feature.
- **DO NOT enable `isMinifyEnabled`/`isShrinkResources`/R8** without a full keep-rule set. R8 would rename the reflected field names (breaking 1.9 silently if any reflection remains), and break JNI callbacks. Required keeps before ever enabling: `org.videolan.**`, `wseemann.media.**`, the masoudss waveform lib, plus any reflected field names. Treat as a separate, test-gated project.
- **libVLC version bump:** only within `3.6.x` (stable API). Do NOT jump to 4.x (API rework).
- **`MediaButtonReceiver android:exported="false"`:** the `setPackage` half (1.1) is safe, but flipping `exported` risks the system not delivering hardware/Bluetooth media-button events to the legacy receiver. The MediaSession path should cover it, but this is a "works in emulator, dead on real headset" trap — **requires physical Bluetooth/wired headset testing**. Defer unless a human can test.
- **m4a lossy/lossless classification** (`MusicViewModel.kt:~386`): leave as-is. Most `.m4a` is AAC (lossy), so current classification is correct for the common case; removing it hides files. Only rare ALAC `.m4a` is mislabeled. A correct fix needs codec detection at scan time (store on `MusicFile`), not in the reactive filter — out of scope.
- **Dual-player consolidation** (Service ExoPlayer vs ViewModel AudioManager): the real root cause of recurring notification bugs, but high risk. Separate project.

---

## Suggested order of execution
1. Tier 1 in one branch, build after each numbered item (or each small group). Run the app once at the end.
2. Tier 2 items individually, build + smoke-test after each. Flag the device-test items (2.2, 2.6) for the human.
3. Do NOT touch Tier 3.

## Smoke-test checklist (after changes)
- App launches; pick source folder (SAF); list populates.
- Play MP3, FLAC, WAV, AIFF, M4A, OGG. AIFF especially (FFmpeg fallback).
- Pause/resume; next/prev; seek via seekbar; seek via notification.
- Notification play/pause state matches UI.
- Like/dislike sorts (moves) a file to destination.
- Waveform renders; spectrogram popup opens and closes without OOM.
- Share/ZIP export works.
- Rotate? (app is portrait-locked — N/A).
- Long session: cacheDir doesn't grow unbounded; playing file never deleted.

## Notes on stale documentation (fix if you maintain docs)
- The "`:main`/`:music` multi-process" description is FALSE — single process. Correct any architecture notes.
- Several items earlier flagged as "dead" are actually live: `util/AnimationUtils.kt` methods, `SettingsRepository` (constructs the 3 settings managers, read at `MusicViewModel.kt:69-71`), `UnifiedLogger.initialize`/`setResultsFolder`, `ResourceManager.withMediaMetadataRetriever`/`withMediaExtractor`. Do not remove these.
