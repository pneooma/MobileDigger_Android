# MobileDigger Audio Visualization Technical Report

## Executive Summary
This report documents how MobileDigger generates spectrograms and waveforms, including algorithms, parameters, libraries, rendering, guardrails, and recommended improvements. It also proposes two default profiles: Critical Listening and Performance.

## Spectrogram System
- Input: PCM samples (stereo averaged to mono).
- Sample rate assumption (current path): 44.1 kHz.
- Windowing: Hanning (current path, Blackman helper present but unused).
- FFT: Custom in-place Cooley–Tukey; power computed per bin.
- Time stride: hopSize = fs / temporalResolution, min 64 samples.
- Dimensions: height depends on quality preset; width limited (≤ 2000 px) and analysis duration capped to 30 s.
- Freq mapping: precomputed row→FFT bin index up to a configurable max frequency.
- Smoothing: 3×3 box filter on the power image.
- Normalization: power→dB relative to max power in image (~120 dB span).
- Color mapping: multi-stop gradient (inline) with optional SpectrogramColorMapper schemes.
- Parallelization: time columns split across up to 8 workers.
- Guardrails: memory/OOM checks; 1-item bitmap cache.

### Current Presets/Parameters (Code)
- Quality → windowSize, height:
  - FAST: 1024, 128
  - BALANCED: 4096, 256
  - HIGH: 8192, 512
- Frequency ranges:
  - FULL: 0–25 kHz
  - EXTENDED: 0–22 kHz (default)
  - FOCUSED: 0–8 kHz
- TemporalResolution (px/s): user-set, clamped 3–10 → hop = fs / pxPerSec
- dB mapping: reference = max power in analysis; floor ≈ −120 dB

### Key Files
- AudioManager (STFT, smoothing, coloring, bitmap): app/src/main/java/com/example/mobiledigger/audio/AudioManager.kt
- SpectrogramColorMapper (palette options): app/src/main/java/com/example/mobiledigger/audio/SpectrogramColorMapper.kt
- OptimizedFFTService (alt FFT path, Blackman, helpers): app/src/main/java/com/example/mobiledigger/audio/OptimizedFFTService.kt
- UI Surface: app/src/main/java/com/example/mobiledigger/ui/components/SpectrogramView.kt

## Waveform System
There are multiple generators used depending on UI context and fallbacks:

1) LightweightWaveformGenerator (default for WaveformView)
- Decoder: MediaExtractor buffers.
- Amplitude basis: average absolute sample values per bar (with small sample cap per buffer) and skipping to reduce decoding.
- Scaling: normalized to max, mapped to [10..100].
- Buffer: 4 KB; process up to 512 samples per buffer; samplesPerBar = 1000.

2) Amplituda + WaveformSeekBar (ScrollableWaveformView)
- Library: linc.com.amplituda (vendored FFmpeg).
- Returns amplitudes in [0..100].
- Compression: Compress.AVERAGE with 1–3 samples/sec (AIFF lower sampling).
- Rendering: com.masoudss.lib.WaveformSeekBar Android view.
- Fallback: custom utils.WaveformGenerator if Amplituda fails.

3) Custom WaveformGenerator (MediaExtractor/MediaCodec, parallel)
- Peak-based per bucket, downsample to target bars, then smoothing and visual dynamic range compression (e.g., 30–85).
- AIFF special handling via manual SSND parsing.

4) iOS-inspired utils/WaveformGenerator
- RMS-based per bar: frame-based seek or chunk-based loop.
- Normalizes to [0..100].

### Key Files
- Lightweight: app/src/main/java/com/example/mobiledigger/utils/LightweightWaveformGenerator.kt
- Amplituda UI flow: app/src/main/java/com/example/mobiledigger/ui/components/ScrollableWaveformView.kt
- Custom heavy: app/src/main/java/com/example/mobiledigger/audio/WaveformGenerator.kt
- iOS-inspired: app/src/main/java/com/example/mobiledigger/utils/WaveformGenerator.kt
- Render (Compose): app/src/main/java/com/example/mobiledigger/ui/components/WaveformView.kt

## Technical Considerations and Opportunities
- STFT parameterization: prefer window length (ms) and overlap (%) vs pixels/s. Typical overlap 50–75%.
- Windowing: expose Hanning/Hamming/Blackman(-Harris)/Kaiser(beta) choices; document trade-offs.
- Frequency axis: add logarithmic / mel / CQT for musical content; keep linear as option.
- Amplitude reference: dBFS calibration (fixed reference), fixed dynamic range; optional noise floor suppression.
- Pre-emphasis / whitening: optional filtering; median filtering across frequency/time for artifact control.
- Stereo policy: mono sum vs L/R vs mid/side views.
- Sample rate: detect and respect actual fs; adjust maxFreq and bin mapping accordingly.
- Colormap: switch to perceptually uniform palettes (Turbo/Inferno/Viridis) and include legend.
- Caching/tiling: tile spectrogram for long content; precompute multi-resolution overview.
- Waveform amplitude basis: standardize (RMS or peak) across all generators; provide channel modes and zoomable min/max envelopes.
- Amplituda tuning: pick PEAK/AVERAGE and samples/sec to match UI bar density.

## Proposed Default Profiles

### Critical Listening (High fidelity)
- Sample rate: actual file fs (no hard-coded 44.1 kHz).
- Window length: 46–92 ms (e.g., 2048–4096 @ 44.1 kHz), FFT = same or next power of two (4096–8192).
- Overlap: 75% (hop = 0.25 × window).
- Window: Blackman-Harris (or Blackman); Hanning acceptable if CPU-bound.
- Frequency axis: log or mel; maxFreq = min(22 kHz, fs/2).
- dB mapping: reference = fixed dBFS; dynamic range 80–100 dB; floor ≈ −100 dBFS.
- Smoothing: minimal (3×3 median or none); allow toggle.
- Stereo: selectable L/R or M/S.
- Color: perceptual (Turbo/Inferno); include scale legend.

Waveform (Critical)
- Basis: RMS per bar (or min/max envelope per pixel for zoom levels).
- Bars: 500–1000; render multi-resolution levels for smooth zooming.
- Channels: L/R overlay or split.
- Scaling: soft-knee/log visual mapping; avoid hard clamp (30–85) except for legacy modes.

### Performance (Responsive/Low CPU)
- Window length: ~23 ms (1024 @ 44.1 kHz), FFT 1024–2048.
- Overlap: 50% (hop = 0.5 × window) or use current hop = fs / pxPerSec; px/s ≈ 5–8.
- Frequency axis: linear; maxFreq = 20–22 kHz.
- dB mapping: 60 dB dynamic range; floor ≈ −80 dBFS.
- Smoothing: 3×3 box; optional temporal downsampling.
- Guardrails: reuse current 30 s cap and width ≤ 2000 px.

Waveform (Performance)
- Basis: average abs (Lightweight) or Amplituda(AVERAGE) with ~3 samples/sec.
- Bars: 250–300 (or adaptive to width).
- Channels: mono sum.
- Scaling: simple normalization with optional mild compression for readability.

## Engineering Checklist
- Confirm actual sample rate from source; avoid fixed 44.1 kHz in analysis.
- Expose STFT controls: window length (ms), FFT size, and overlap (%).
- Offer window functions (Hann/Hamming/Blackman/Blackman-Harris/Kaiser) with defaults per profile.
- Add frequency scale options: linear / log / mel / CQT.
- Normalize to calibrated dBFS; configurable dynamic range and noise floor.
- Provide stereo modes: Mono, L/R, M/S selection in UI.
- Adopt perceptual colormaps; render color scale legends and gridlines.
- Implement multi-resolution tiling for long spectrograms and waveform overviews.
- Standardize waveform amplitude basis (RMS or peak) across generators.
- For zoomable waveforms, store per-pixel min/max envelopes per LOD.
- Tune Amplituda `Compress` mode and sampling density to target bar density.
- Validate against reference tools (e.g., Audacity/Spek) on a test corpus.

## Appendix: Code Pointers
- Spectrogram generation (STFT, smoothing, coloring): `AudioManager.generateSpectrogramFromAudioData`
- Color schemes: `SpectrogramColorMapper`
- Alternate FFT service: `OptimizedFFTService`
- Waveform generators: `LightweightWaveformGenerator`, `audio/WaveformGenerator`, `utils/WaveformGenerator`
- UI: `SpectrogramView`, `WaveformView`, `ScrollableWaveformView`
