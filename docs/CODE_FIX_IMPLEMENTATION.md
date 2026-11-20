# MobileDigger — Verified Code-Fix Implementation Guide

**For:** an AI engineer implementing the fixes.
**Repo:** `MobileDigger` (Android, package `com.example.mobiledigger`), branch `v12.00-release`.
**Toolchain:** AGP 8.12.0, Kotlin 2.0.21, Gradle 8.13, compileSdk/targetSdk 36, **minSdk 31**, Jetpack Compose.
**Module:** only `:app` is built (`settings.gradle.kts`). `Amplituda-master/` and `spek-X-main/` are reference copies, NOT modules — ignore them.

> This guide supersedes `CODE_AUDIT_HANDOFF.md`. Every claim below was verified against the actual source on 2026-06-15. Line numbers are post-verification but the repo drifts — **always grep/confirm the symbol before editing.** Build after each numbered group and confirm it compiles.

## CRITICAL CONTEXT — read before doing anything

1. **The app currently works. Do not regress it.** Apply only what you're told, the way you're told.
2. **There is NO multi-process split.** `AndroidManifest.xml` has **zero `android:process` attributes**. `main_activity`/`music_service` are `android:attributionTags`, not process names. Everything runs in ONE process.
3. **Dual-`AudioManager` architecture:** the REAL player is `MusicViewModel.audioManager` (`MusicViewModel.kt:66`). `MusicService` builds its OWN `AudioManager(this)` (`MusicService.kt:~268`) + an ExoPlayer that **never receive a media item** — a notification/MediaSession shell. On the Service instance `playFile()` is never called, so `vlcBackend == null` and `isUsingVlc == false` permanently; state is shipped ViewModel→Service via Intent extras and a load-bearing IPC `isPlaying` flag. This redundancy is OUT OF SCOPE to refactor.
4. **`USE_VLC_FOR_ALL = true`** (`AudioManager.kt:62`) — VLC is the active backend for every file. ExoPlayer and FFmpegMediaPlayer are fallbacks.
5. **AIFF is a core feature.** `FFmpegMediaPlayer` is the last-resort AIFF decoder. Do NOT remove it.
6. **Two ways a fix here can break the build/app — avoid both:** (a) forgetting a COUPLED EDIT (deletions that leave dangling references); (b) mis-ordering FIX 2.5 (can delete the file VLC is playing). Both are called out inline.

---

## TIER 1 — SAFE. Apply these. Zero expected behavior change (except where noted as a latent-bug fix).

### 1.1 Add `setPackage(packageName)` to internal broadcasts
**Why:** Implicit broadcasts leak internal control actions app-wide. More importantly, on **targetSdk 34+** sending an *implicit* broadcast that a runtime `RECEIVER_NOT_EXPORTED` receiver listens for **throws** — so this is a latent crash fix, not just hardening. All consumers are in-package `RECEIVER_NOT_EXPORTED` (verified: `MusicViewModel.kt:2238`, `MusicService.kt:451`, `AudioManager.kt:1395`).
**Scope only the STILL-UNSCOPED sends (some are already scoped — leave those):**
- `audio/MusicService.kt` — bare `Intent(ACTION_*)` at **168, 172, 199, 203, 207, 211, 348, 820**. (Lines 142, 147, 388–398, 415–418, 798 are ALREADY scoped — skip them.)
- `audio/MediaButtonReceiver.kt` — **30, 35, 40, 47, 51**. (Line 56 `Intent("STOP")` is a dead action no one listens for; scoping is harmless but won't make it work — leave or scope, your call.)
**How:** `context.sendBroadcast(Intent(ACTION_X))` → `context.sendBroadcast(Intent(ACTION_X).apply { setPackage(context.packageName) })` (use `packageName` directly inside the Service).
**Note:** scoping does NOT change which in-package receiver fires — it only blocks other apps — so it cannot cause the old "double-advance" behavior.
**Verify:** Build. Notification buttons (play/pause/next/prev/like/dislike) still work.

### 1.2 `allowBackup="false"`
**File:** `AndroidManifest.xml:29` (`android:allowBackup="true"`). Set to `"false"`. The referenced `@xml/backup_rules` / `@xml/data_extraction_rules` are stock empty templates; SAF grants aren't backed up anyway.

### 1.3 Remove unused permissions
**File:** `AndroidManifest.xml`.
- Remove `BLUETOOTH` (line 20) and `BLUETOOTH_ADMIN` (line 21) — 0 code refs (the "smartwatch integration" comment is aspirational, no code backs it).
- Remove `ACCESS_WIFI_STATE` (line 14) — 0 code refs.
- **Do NOT** touch `WRITE_EXTERNAL_STORAGE` here — see 2.6.
- **⚠️ CORRECTION to prior handoff:** the claim that `INTERNET` is needed for downloadable Google Fonts is **FALSE**. Fonts are bundled local TTFs (`res/font/*.ttf`, loaded in `ui/theme/Type.kt`); there is no `GoogleFont`/`FontRequest`/font-provider code and no network code anywhere. `INTERNET`, the `androidx.compose.ui:ui-text-google-fonts:1.7.8` dep (`build.gradle.kts:73`), and `res/values/font_certs.xml` all appear unused and are **additional removal candidates**. Treat removing `INTERNET` as a separate, optional decision — low risk, but confirm no future streaming plans first.
**Verify:** Build.

### 1.4 Remove unused Gradle dependencies
**File:** `app/build.gradle.kts`. All verified 0 imports in `app/src`:
- JTransforms (~line 113) — FFT is hand-rolled at `AudioManager.kt:3020` (`fun fft`, radix-2 Cooley-Tukey).
- amplituda (~line 109) — **remove the DIRECT line only.** It stays on the classpath transitively via `waveformSeekBar` (massoudss lib depends on it internally), so removing the direct declaration is safe. Do NOT also remove `waveformSeekBar` (~line 110) without separately confirming it's unused.
- palette-ktx (~line 96)
- media3-ui (~line 88) — keep media3-exoplayer/session/common/extractor/datasource.
- runtime-livedata (~line 70)
- navigation-compose (~line 103)
- animation-graphics (~line 66) — NOTE: plain `animation` (~line 65) is a different dep and may be used; keep it.
**Verify:** Gradle sync + build (the definitive check).

### 1.5 Delete the dead WaveformSettings feature chain
**Delete these 4 files** (verified closed dead island — they reference only each other):
- `app/src/main/java/com/example/mobiledigger/utils/WaveformGenerator.kt` (the **`object`** — dead)
- `app/src/main/java/com/example/mobiledigger/ui/components/WaveformSettingsDialog.kt`
- `app/src/main/java/com/example/mobiledigger/ui/components/WaveformWarningDialog.kt`
- `app/src/main/java/com/example/mobiledigger/model/WaveformSettings.kt`

**⚠️ MANDATORY COUPLED EDIT (build breaks otherwise):** remove the now-dangling import at **`MusicPlayerScreen.kt:45`**: `import com.example.mobiledigger.utils.WaveformGenerator` (the symbol is used nowhere else in that file).

**⚠️ DO NOT confuse with the REAL generator** `audio/WaveformGenerator.kt` (a **`class`**, used by `SharedWaveformState.kt:12` and `:106`) — KEEP it.
**⚠️ DO NOT touch** `AudioManager.getCurrentSettings()` (`AudioManager.kt:208`, a `Triple<SpectrogramQuality, FrequencyRange, Int>`) — unrelated method.

### 1.6 Delete `AnimationSpecs.kt` (the dead duplicate)
**Delete:** `app/src/main/java/com/example/mobiledigger/ui/components/AnimationSpecs.kt` (dead `ui.components.rememberOptimizedAnimationSpecs` + `OptimizedAnimationSpecs`, zero usages).
**⚠️ KEEP** `util/AnimationUtils.kt` — `util.rememberOptimizedAnimationSpecs` IS used (imported `MusicPlayerScreen.kt:46`, called `:291`). Its helpers `getOptimizedDuration/Spring/Tween` are live transitively.

### 1.7 Remove dead helpers
- Delete `app/src/main/java/com/example/mobiledigger/util/FileOperationHelper.kt` AND its field `fileOperationHelper` at `MusicViewModel.kt:125` (declaration-only, 0 other usages).
- Remove the `errorHandler` field at `MusicViewModel.kt:122` and the methods `runSafely` (`ErrorHandler.kt:50`) / `runSafelyOrNull` (`:66`). Zero callers. **KEEP** `ErrorHandler.createHandler` + private `handleException` (file stays). NOTE: removing the field orphans `createHandler` — harmless, no build break.
- Remove these (each grep-confirmed zero callers): `ResourceManager.delayWithProgress` (`ResourceManager.kt:166`), `PerformanceProfiler.measureIterations` (`:59`), `PerformanceProfiler.clearStats` (`:121`), `MemoryMonitor.stopMonitoring` (`:51`).
- **⚠️ KEEP** `ResourceManager.withMediaMetadataRetriever` (`:23`, called `:102`) and `withMediaExtractor` (`:62`, called `:135`) — used by `extractDuration`/`extractDurationWithFallback`.

### 1.8 Logging cleanup (be precise)
In `util/UnifiedLogger.kt` and `util/CrashLogger.kt`:
- SAFE to remove (zero callers): `CrashLogger.startTiming` (`:54`)/`endTiming` (`:60`), `UnifiedLogger.startTiming` (`:76`)/`endTiming` (`:77`), `UnifiedLogger.writeToFile` (`:102`, private no-op), `UnifiedLogger.getLogCount` (`:116`), `UnifiedLogger.clearLogs` (`:108`). Remove start/end timing as PAIRS (`endTiming` calls `log()`/pairs with `startTiming`).
- `UnifiedLogger.flushLogs` (private no-op, `:85`) is called at `:73` (`if (throwable != null) flushLogs()`) and by `forceFlush` (`:107`, itself zero external callers). Remove `flushLogs` + `forceFlush` + the line-73 call **together as one set**, or not at all.
- **⚠️ DO NOT remove** `UnifiedLogger.initialize` (used `MobileDiggerApplication.kt:29`) or `UnifiedLogger.setResultsFolder` (used `FileManager.kt:94`).
- **⚠️ DO NOT touch** `CrashLogger.log()` or its file-write path — backs the uncaught-exception handler (`MobileDiggerApplication.kt:53–55`); has hundreds of call sites.
- (Optional, out of scope: `CrashLogger.clearLogs` at `:163` is also dead but leave it unless extending the cleanup.)

### 1.9 Remove the cache-trim reflection
**Where:** `MusicViewModel.kt:1949–1959`, inside `startPeriodicCacheClearing()`. It reflects its own public `audioManager` field and private `AudioManager` fields `currentFFmpegDataSourcePath` (`AudioManager.kt:156`) / `currentTempWavPath` (`:157`), wrapped in `catch (_: Exception)`. Result merged into `protected` and passed to `CacheManager.trimDiskCache(...)` at `:1960`.
**Why safe + better:** `trimDiskCache` (`CacheManager.kt:20–62`) only EXCLUDES protected paths from deletion candidates (`:37 if (!protectedPaths.contains(...)) candidates.add(...)`). Adding paths can only protect MORE files, never delete more. The reflection is pure over-engineering (same instance, same class) and fails SILENTLY on rename → could let the cache delete the in-use temp.
**How:**
1. Add to `AudioManager`: `fun getProtectedTempPaths(): Set<String> = setOfNotNull(currentFFmpegDataSourcePath, currentTempWavPath)` (the `setOfNotNull` handles the `String?` nullability).
2. In `MusicViewModel`, replace the reflection block with `protected.addAll(audioManager.getProtectedTempPaths())`.
**Verify:** Build; play a file; trigger cache trim; the playing file is not deleted.

### 1.10 Move VLC position/duration polling off the Main thread
**Where:** `startPositionUpdates()` at `MusicViewModel.kt:1872–1914`. Launches on `viewModelScope.launch {}` (Main), loops `while (isActive) { delay(33) ... }` (~30 Hz), reads `audioManager.getCurrentPosition()` (`:1885`) / `getDuration()` (`:1887`) → libVLC native getters (`VlcAudioBackend.kt:350`/`:359` → `mediaPlayer?.time`/`.length`, NOT Main-bound), assigns StateFlows.
**Why safe:** Native getters aren't Main-bound; `MutableStateFlow.value` assignment is thread-safe from any thread; the `@Volatile suppressPositionPushUntil` guard (`:208`, set in `seekTo` `:1175` to `+700ms`, read at `:1882`) is `@Volatile` so it works from an IO thread. The loop body also calls `updateNotification()` (`:2245`) / `updateNotificationPosition()` (`:2270`) — these only read StateFlows and call `Context.sendBroadcast`/`startForegroundService`, all thread-safe. The ExoPlayer-fallback read path already self-marshals via `callOnMainSync` (`AudioManager.kt:121`). **No Main-only object is touched in the loop.**
**How:** Launch the entire loop on IO once — `viewModelScope.launch(Dispatchers.IO) { ... }`. **Do NOT** wrap only the two read lines in per-iteration `withContext(Dispatchers.IO)` (30 dispatcher switches/sec). Keep the `@Volatile` suppress guard untouched.
**Verify:** Build; seekbar tracks playback smoothly; seeking is not clobbered by the poll.

---

## TIER 2 — NEEDS CARE. Apply with the exact guardrails. Build + smoke-test after each.

### 2.1 Remove `Thread.sleep(50)` from the waveform `LaunchedEffect`
**Where:** `SharedWaveformState.kt:83–84` — `System.gc(); Thread.sleep(50)` in the `LaunchedEffect` body (runs on Main, on every track change; key is `currentFile.uri + currentFile.name` at `:69`). Real cancellation is already handled by `scope.coroutineContext.cancelChildren()` at `:71` — nothing depends on the 50 ms as a debounce.
**How:**
- DELETE the `Thread.sleep(50)` (removes ~50 ms Main-thread freeze per track switch).
- MOVE `System.gc()` into the existing `scope.launch(Dispatchers.IO)` block (`:103`), right before `generator.generateWaveform(...)` (`:115`).
- Update/delete the now-misleading comment at `:81–82` ("free memory before processing large AIFF").
**Verify:** Rapid track switching is smoother; no OOM on large AIFF.

### 2.2 Make `isCurrentlyPlaying()` honest for VLC (method-only; do NOT change callers)
**Where:** `AudioManager.kt:1065–1076` — branches on `isUsingFFmpeg` else `exoPlayerFallback?.isPlaying`; never checks VLC. (It's the only state method missing the `isUsingVlc` branch its siblings all have.)
**How:** Add `if (isUsingVlc) return vlcBackend?.isPlaying() ?: false` BEFORE the existing branches. (`VlcAudioBackend.isPlaying()` exists at `:275`, event-driven via `AtomicBoolean` — reliable.)
**⚠️ DO NOT** modify callers in `MusicService.kt` (217, 511, 623, 675, 805/`togglePlayPause`, 832). On the Service instance `isUsingVlc` is always false, so this branch is never hit there → existing IPC-flag compensation stays correct.
**Realistic expectation:** the ViewModel's AudioManager never calls `isCurrentlyPlaying()` either, so this change is a **latent-correctness fix with ~zero immediate runtime effect**. It's safe and consistent, but don't expect a visible behavior change from it alone.
**Verify:** Build; on-device, confirm notification play/pause state still correct (**flag for human device test**).

### 2.3 Gate verbose logging behind `BuildConfig.DEBUG`
**Prerequisite (or it won't compile):** add `buildConfig = true` to `buildFeatures` in `app/build.gradle.kts:37–39` (currently only `compose = true`). AGP 8+ defaults `buildConfig` OFF and there are zero existing `BuildConfig` references, so adding it is safe.
**Where:** `UnifiedLogger.log` (the central sink) wraps `android.util.Log.d(...)` at `UnifiedLogger.kt:70`.
**How:**
- Gate **centrally inside `UnifiedLogger.log`** (one wrap), not at call sites.
- Wrap only the verbose (non-throwable) path; **preserve the `throwable != null` force-flush at `:73`** so exceptions still log in release.
- Add `import com.example.mobiledigger.BuildConfig` to any file using it.
- Leave `CrashLogger` crash reporting intact. (If full silence is the goal, consider extending the same gate to `CrashLogger.log`'s verbose path — separate decision.)
**Note:** your debug/QA APK has `BuildConfig.DEBUG = true`, so gated logs still appear there — only a true release build goes quiet.

### 2.4 Version string from `BuildConfig`
**Prerequisite:** `buildConfig = true` (same as 2.3).
**Where:** `ui/components/MusicPlayerScreen.kt:884` — hardcoded `text = "v12.15"`. (File is in `ui/components/`, NOT `ui/screens/`.)
**How:** `text = "v${BuildConfig.VERSION_NAME}"` (`versionName = "12.15"` at `build.gradle.kts:16` has no `v` prefix). Add `import com.example.mobiledigger.BuildConfig`. Eliminates the 3-place version drift.

### 2.5 VLC temp-file eviction — ⚠️ RISKIEST ITEM, strict ordering required
**Problem:** The VLC playback temp file (created `AudioManager.kt:706` via `copyUriToTempFile` → `:1244` `createTempFile`) is tracked NOWHERE — not in `tempFileCache`, not in protected fields. It leaks until `clearAllCaches`/`release`. The LRU guard `removeEldestEntry` (`:243`) and `clearAllCaches` (`:1118`) only protect `currentFFmpegDataSourcePath`. `tempFileCache` max size is 3 — so **registering the VLC temp naively can evict (delete) the file VLC is currently playing.**

**How — DO THESE IN THIS EXACT ORDER:**
1. Add field `currentVlcTempPath: String? = null`. Set it at `:712` (right after the temp is created, before `vlc.start()`).
2. **Extend BOTH eviction guards** (`:243` `removeEldestEntry` and `:1118` `clearAllCaches`) to ALSO skip `currentVlcTempPath`.
3. Add `currentVlcTempPath` to `getProtectedTempPaths()` from 1.9 (`setOfNotNull(currentFFmpegDataSourcePath, currentTempWavPath, currentVlcTempPath)`). **Depends on 1.9 being done first.**
4. ONLY THEN register the temp into `tempFileCache` for normal LRU eviction of OLD temps.
**Clearing the field** (clear, never while VLC still plays it): in `stopAllPlayback()` (`:916`, alongside the existing `currentFFmpegDataSourcePath` clear at `:947`) — this runs at the START of every `playFile` (`:577`), before the new temp is created, so it's the safe clear point; in `release()` (`:1351`); optionally in the VLC completion/error listeners (`setupVlcListeners` `:340`/`:348`).
**Also fix the orphan leak the prior handoff missed:** when `tryPlayWithVlcSync` returns false (VLC fails, `:625`/`:672`), the temp created at `:706` is orphaned (falls through to FFmpeg/ExoPlayer). Register into `tempFileCache` at creation time (`:712`) regardless of success so LRU can reclaim it.
**Note:** `createTempFile` generates a unique suffix each call — paths are never reused, so no aliasing risk.
**Verify:** Long playback session doesn't accumulate temp files in cacheDir; the active file is NEVER deleted mid-playback (test by playing while cache fills).

### 2.6 Remove `MANAGE_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE`
**Why:** All real I/O is SAF/DocumentFile/ContentResolver (writes via persisted tree grants, `FileManager.kt:40–44`); these perms are over-broad (Play-policy risk: MANAGE_EXTERNAL_STORAGE triggers Play Console review) and vestigial on minSdk 31. `MANAGE_EXTERNAL_STORAGE` is referenced NOWHERE in code (no `Environment.isExternalStorageManager`, no `ACTION_MANAGE_*`), so removing it cannot change runtime behavior.
**⚠️ COUPLED EDIT:** `WRITE_EXTERNAL_STORAGE` is actively requested at `MainActivity.kt:227–230` (pre-Tiramisu `else` branch, reachable on API 31–32). **DELETE that request block too.**
**Dead-branch confirmation:** the raw-`File` scan path (`FileManager.kt:30–34` activating on `uri.scheme == "file"`, scan gated at `:122` → `scanForMusicFilesDirect`/`scanDirectoryRecursive` `:305`) is unreachable — all folder pickers use `OpenDocumentTree()` (`MusicPlayerScreen.kt:200, 206, 814`) returning `content://` tree URIs, and the restored source URI comes from the persisted SAF picker. No entry point feeds a `file://` directory. Leave the dead branch in place (harmless) or remove it.
**How:** Remove both `<uses-permission>` lines from `AndroidManifest.xml:7–8`. Optionally also drop `READ_EXTERNAL_STORAGE` (no-op on 31+, superseded by `READ_MEDIA_AUDIO`).
**Verify:** Pick source folder, scan, play, sort (move) a file, share/ZIP export — all via SAF. **Flag for human on-device test.**

---

## TIER 3 — DO NOT DO in this pass (facts verified; rationale holds)

- **DO NOT remove `FFmpegMediaPlayer-native:1.0.5`** (`build.gradle.kts:81`, core at `:80`). It is the last-resort AIFF decoder in the VLC→FFmpeg fallback chain (`tryPlayWithFFmpegSync`, `AudioManager.kt:633–648`). AIFF is a core feature.
- **DO NOT enable `isMinifyEnabled`/`isShrinkResources`/R8** (currently `isMinifyEnabled = false`, `build.gradle.kts:23`) without a full keep-rule set: `org.videolan.**`, `wseemann.media.**`, the masoudss waveform lib, plus any reflected field names. Separate, test-gated project.
- **libVLC version bump:** only within `3.6.x` (currently `3.6.0`, `build.gradle.kts:84`). Do NOT jump to 4.x (API rework).
- **`MediaButtonReceiver android:exported`:** the `setPackage` half (1.1) is safe, but flipping `exported` to false risks the system not delivering hardware/Bluetooth media-button events. Requires physical headset testing — defer.
- **m4a lossy/lossless classification** (`MusicViewModel.kt:387`, `lossyExtensions` includes `m4a`): leave as-is. Most `.m4a` is AAC (lossy); only rare ALAC is mislabeled. A correct fix needs codec detection at scan time — out of scope.
- **Dual-`AudioManager` consolidation** (Service shell vs ViewModel real player): root cause of recurring notification bugs, but high risk. Separate project.

---

## Suggested order of execution
1. **Tier 1** in one branch, build after each numbered item (or small group). Run the app once at the end.
2. **Tier 2:** do 2.3 + 2.4 together (shared `buildConfig = true` prerequisite). Then 2.1, 2.2, 2.6. Do **2.5 LAST and on its own** (only item with a delete-while-playing hazard; depends on 1.9). Build + smoke-test after each.
3. Flag device-test items (2.2, 2.6) for the human.
4. Do NOT touch Tier 3.

## Smoke-test checklist (after changes)
- App launches; pick source folder (SAF); list populates.
- Play MP3, FLAC, WAV, **AIFF** (FFmpeg fallback), M4A, OGG.
- Pause/resume; next/prev; seek via seekbar; seek via notification.
- Notification play/pause state matches UI.
- Like/dislike sorts (moves) a file to destination.
- Waveform renders; spectrogram popup opens and closes without OOM.
- Share/ZIP export works.
- Long session: cacheDir doesn't grow unbounded; playing file never deleted mid-playback.

## Corrections this guide makes to the prior `CODE_AUDIT_HANDOFF.md`
- **1.3:** `INTERNET` is NOT needed for fonts (they're bundled local TTFs); `INTERNET` + `ui-text-google-fonts` dep + `font_certs.xml` are extra removal candidates.
- **1.4:** amplituda must be removed as the DIRECT line only (stays transitively via `waveformSeekBar`).
- **1.10:** launch the whole loop on `Dispatchers.IO`; do not wrap reads in per-iteration `withContext`.
- **2.2:** safe but a latent-correctness fix with ~zero immediate runtime effect (no live caller hits the new branch).
- **2.3:** gate centrally inside `UnifiedLogger.log`; preserve the throwable path; debug APKs still log.
- **2.4:** correct file is `ui/components/MusicPlayerScreen.kt:884` (not `ui/screens/`).
- **2.5:** added the VLC-failure orphan-leak fix and the precise clear points; ordering is mandatory.
- Minor line-number drift corrected throughout (e.g. `MusicPlayerScreen` animation-specs use is `:291`; `CrashLogger.log` uncaught handler is `:53–55`).
