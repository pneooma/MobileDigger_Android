# MobileDigger Architecture Documentation

## Overview

MobileDigger is an Android audio file management and playback application built with Kotlin, Jetpack Compose, and Coroutines. This document outlines the architectural improvements made through Phases 1-6.

## Architecture Pattern

The app follows **MVVM (Model-View-ViewModel)** pattern with additional utility layers:

```
┌─────────────────────────────────────┐
│        UI Layer (Compose)           │
│  - MusicPlayerScreen                │
│  - SharedWaveformDisplay            │
│  - Dialogs & Components             │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      ViewModel Layer                │
│  - MusicViewModel (3,351 lines)     │
│    * Playlist management            │
│    * Playback control               │
│    * File operations coordination   │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Utility Layer (Phase 1-6)      │
│  - FileOperationHelper              │
│  - PlaylistManager                  │
│  - ErrorHandler                     │
│  - PerformanceProfiler              │
│  - MemoryMonitor                    │
│  - LowMemoryHandler                 │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Data/Service Layer             │
│  - AudioManager                     │
│  - FileManager                      │
│  - SettingsRepository               │
│  - MusicService                     │
└─────────────────────────────────────┘
```

## Phase-by-Phase Improvements

### Phase 1 (v10.39) - Code Quality
**Goal:** Establish consistent logging and memory monitoring baseline

**Changes:**
- Replaced 126 `println()` calls with `CrashLogger.log()`
- Added `MemoryMonitor` utility (85%/95% thresholds)
- Verified existing `Mutex` and `Semaphore` implementations

**Impact:** Consistent logging across codebase, proactive memory monitoring

---

### Phase 2 (v10.40) - Memory Management
**Goal:** Prevent memory leaks and improve resource management

**Changes:**
- Bounded file operation queue (100 capacity, was `Channel.UNLIMITED`)
- Added cache statistics logging in `AudioManager`
- StateFlow cleanup in `onCleared()` to prevent leaks
- Fixed deprecated API warnings (`menuAnchor`, coroutine opt-in)

**Impact:** Eliminated unbounded queue memory leak, better StateFlow lifecycle

---

### Phase 3 (v10.41) - Architecture & Error Handling
**Goal:** Extract utilities and add robust error handling

**New Classes:**
- `FileOperationHelper.kt` - Safe file operations with comprehensive error handling
- `ErrorHandler.kt` - Centralized coroutine exception handling
- `LowMemoryHandler.kt` - Automatic cleanup on system memory pressure

**Changes:**
- Extracted file operations from `MusicViewModel` (reduced God Object)
- Added `OutOfMemoryError` detection and emergency GC
- System `TRIM_MEMORY` event handling with aggressive cleanup
- Safe execution wrappers (`runSafely`, `runSafelyOrNull`)

**Impact:** More robust error handling, automatic memory management, reduced crash risk

---

### Phase 4 (v10.42) - UX Improvements
**Goal:** Improve user experience for file operations

**Changes:**
- Keep playback active when moving file to subfolder (no interruption)
- Smart playlist index management after file removal
- Works across all playlists (TODO → subfolder, LIKED → subfolder, REJECTED → subfolder)

**Impact:** Seamless file organization without disrupting listening experience

---

### Phase 5 (v10.43) - Performance Profiling
**Goal:** Add visibility into performance bottlenecks

**New Classes:**
- `PerformanceProfiler.kt` - Operation timing and statistics

**Changes:**
- Automatic slow operation detection (>1000ms)
- Waveform generation profiling
- Aggregate statistics (count, avg, min, max)
- Auto-logging every 5 minutes
- `recordOperation()` API for custom metrics

**Impact:** Better visibility into performance issues, proactive bottleneck detection

---

### Phase 6 (v10.44) - Architecture Improvements
**Goal:** Further reduce God Object, document architecture

**New Classes:**
- `PlaylistManager.kt` - Playlist operations and statistics
- `ARCHITECTURE.md` - This document

**Changes:**
- Extracted playlist management logic from `MusicViewModel`
- Added architecture documentation
- Documented all utility classes and their responsibilities

**Impact:** More modular codebase, easier to maintain and test

---

## Key Components

### MusicViewModel (3,351 lines)
**Status:** Partially refactored (God Object pattern being addressed)

**Responsibilities:**
- Playlist state management (TODO, LIKED, REJECTED)
- Playback coordination with `AudioManager`
- File operation coordination with `FileOperationHelper`
- UI state management (30+ StateFlows)

**Dependencies:**
- `AudioManager` - Audio playback
- `FileManager` - File system operations
- `SettingsRepository` - User preferences
- `FileOperationHelper` - Safe file operations (Phase 3)
- `PlaylistManager` - Playlist utilities (Phase 6)
- `ErrorHandler` - Exception handling (Phase 3)

---

### AudioManager (3,098 lines)
**Status:** Stable, with monitoring added

**Responsibilities:**
- Audio playback (VLC, FFmpeg, ExoPlayer backends)
- Waveform generation
- Spectrogram generation
- Cache management (bounded caches since Phase 2)

**Performance:**
- Spectrogram cache: 1 item max
- Temp file cache: 3 items max
- Cache eviction logging (Phase 2)

---

### Utility Classes

#### FileOperationHelper (Phase 3)
**Purpose:** Safe file operations with comprehensive error handling

**Key Methods:**
- `safelyMoveFile()` - Move with SecurityException/IOException handling
- `batchMoveFiles()` - Batch operations with progress tracking

---

#### PlaylistManager (Phase 6)
**Purpose:** Playlist manipulation and statistics

**Key Methods:**
- `removeFile()`, `addFile()` - Playlist modifications
- `moveFileBetweenPlaylists()` - Cross-playlist moves
- `calculateNewIndex()` - Index management after removal
- `getStats()` - Playlist statistics

---

#### ErrorHandler (Phase 3)
**Purpose:** Centralized exception handling

**Key Methods:**
- `createHandler()` - CoroutineExceptionHandler with OOM detection
- `runSafely()` - Safe execution with fallback values
- `runSafelyOrNull()` - Safe execution with nullable return

---

#### PerformanceProfiler (Phase 5)
**Purpose:** Operation timing and bottleneck detection

**Key Methods:**
- `profile()` - Profile code block execution
- `recordOperation()` - Track operation timing
- `logStats()` - Aggregate statistics

---

#### MemoryMonitor (Phase 1)
**Purpose:** Proactive memory monitoring

**Features:**
- Checks every 10 seconds
- Warnings at 85% usage
- Critical alerts at 95% with auto-GC
- Integration with `PerformanceProfiler`

---

#### LowMemoryHandler (Phase 3)
**Purpose:** Respond to system memory pressure

**Features:**
- `onLowMemory()` callback - Emergency cleanup
- `onTrimMemory()` callback - 7 trim levels
- Aggressive cleanup on CRITICAL memory warnings

---

## Memory Management Strategy

### Bounded Queues (Phase 2)
- File operation channel: 100 max capacity
- Prevents unbounded growth

### Cache Limits
- Spectrogram: 1 item (extremely reduced for safety)
- Temp files: 3 items max
- LRU eviction with logging

### StateFlow Lifecycle (Phase 2)
- Clear references in `onCleared()`
- Prevents UI holding large playlists after cleanup

### Automatic Cleanup (Phase 3)
- System `TRIM_MEMORY` events
- `onLowMemory()` emergency GC
- Periodic cache clearing (5 minutes)

---

## Performance Monitoring (Phase 5)

### Automatic Profiling
- Waveform generation (>1000ms logged as slow)
- File operations (tracked by `FileOperationHelper`)
- Custom operations via `recordOperation()`

### Statistics
- Operation count
- Average, min, max duration
- Total duration
- Logged every 5 minutes

---

## Error Handling Strategy (Phase 3)

### Exception Types
1. **OutOfMemoryError** → Emergency GC + memory stats
2. **SecurityException** → Permission denied logging
3. **IOException** → File operation failure
4. **CancellationException** → Normal coroutine cancellation
5. **Generic** → Unhandled exception logging

### Safe Execution Wrappers
```kotlin
ErrorHandler.runSafely("Operation", fallback) { /* code */ }
ErrorHandler.runSafelyOrNull("Operation") { /* code */ }
```

---

## Future Improvements

### Remaining Issues (from code review)

1. **God Object (MusicViewModel)** - Still 3,351 lines
   - ✅ Partially addressed (Phases 3 & 6)
   - ⚠️ Needs further extraction (playback control, state management)

2. **Monolithic UI (MusicPlayerScreen)** - Still 4,235 lines
   - ⚠️ High risk - needs careful component extraction
   - Deferred (requires extensive testing)

3. **Audio Backend Leaks** - Multiple backends loaded
   - ⚠️ Very high risk - would require VLC-only migration
   - Deferred (weeks of testing across file formats)

4. **Dependency Injection** - Services created directly
   - ⚠️ Would break everything - major refactor
   - Deferred (requires Hilt/Koin integration)

### Recommended Next Steps

1. **Unit Tests** - Add tests for new utility classes
2. **More Extraction** - Continue reducing MusicViewModel
3. **Component Split** - Break up MusicPlayerScreen into smaller components
4. **DI Migration** - Consider Hilt for dependency injection

---

## Testing Strategy

### Current State
- No formal unit tests (should be added)
- Manual testing on physical device via WiFi ADB
- Crash logging to destination folder
- Performance profiling in production

### Recommended
1. Unit tests for utility classes (`PlaylistManager`, `FileOperationHelper`)
2. Integration tests for `MusicViewModel`
3. UI tests for critical flows (playback, file sorting)

---

## Build & Version Management

### Version Scheme
- Format: `X.YY` (e.g., 10.43)
- `versionCode` = 1000 + (X * 100) + YY
- Every build increments version ([[memory:10658027]])

### Changelog
- Located at `/changelog.txt`
- Updated for every version
- Phase-by-phase documentation

### GitHub
- Repository: `https://github.com/pneooma/MobileDigger_Android.git`
- Branch: `main`
- Commits: Phase-based with detailed messages

---

## Conclusion

Through Phases 1-6, MobileDigger has evolved from a monolithic architecture with memory leaks and inconsistent logging to a more modular, robust, and maintainable codebase. The app now features:

- ✅ Consistent logging (Phase 1)
- ✅ Proactive memory monitoring (Phases 1, 3)
- ✅ Bounded resource usage (Phase 2)
- ✅ Comprehensive error handling (Phase 3)
- ✅ Seamless UX for file operations (Phase 4)
- ✅ Performance visibility (Phase 5)
- ✅ Documented architecture (Phase 6)

**The app is now significantly more stable and production-ready!** 🎉

---

_Last Updated: Phase 6 (v10.44)_

