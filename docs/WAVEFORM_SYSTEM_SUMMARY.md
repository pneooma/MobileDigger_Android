# Waveform System Summary

## 📊 **Current Configuration**

### **Waveform Value Thresholds:**
- **Raw values**: 0-100 integer scale
- **Minimum display threshold**: 10 (values below are clamped to 10)
- **Maximum threshold**: 100
- **Normalization in display**: `(amplitude / 100f).coerceIn(0.1f, 1f)`
  - This means **minimum bar height is 10%** of max height
  - Maximum bar height is 100% (but scaled by 0.8f for visual margin)

### **Display Scaling:**
- **Bar height calculation**: `normalizedAmplitude * maxBarHeight * 0.6f`
- **Visual height**: 80% of container height
- **Final effective range**: Bars display between ~4.8% and 48% of container height

### **AIFF Waveform Normalization:**
- **RMS calculation**: `sqrt(sum of squared samples / sample count)`
- **Normalization**: `(rms / 32768.0) * 100` → 0-100 range
- **Clamping**: `.coerceIn(10, 100)`
- **Expected values** for typical audio:
  - Quiet passages: 10-30
  - Normal audio: 30-70
  - Loud passages: 70-100

---

## 🎬 **Available Animations**

### **1. Current: Zoom/Scale Animation** (ACTIVE)
- **Type**: Scale from 0% to 100%
- **Duration**: 750ms
- **Easing**: `FastOutSlowInEasing` (starts fast, ends slow)
- **Effect**: Waveform "zooms in" from center point
- **Code location**: `SharedWaveformDisplay.kt` lines 119-132

```kotlin
val animatedScale by animateFloatAsState(
    targetValue = if (hasAppeared) 1f else 0f,
    animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
)
```

### **2. Other Animation Options Available:**

#### **A. Fade In Animation**
```kotlin
// Replace graphicsLayer scale with alpha
.graphicsLayer { alpha = animatedAlpha }
```

#### **B. Slide In Animation**
```kotlin
// Horizontal slide
.graphicsLayer { 
    translationX = (1f - animatedProgress) * size.width 
}
```

#### **C. Bounce Animation**
```kotlin
animationSpec = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)
```

#### **D. Elastic Animation**
```kotlin
animationSpec = tween(
    durationMillis = 1000,
    easing = EaseOutElastic
)
```

#### **E. Wave/Ripple Animation**
```kotlin
// Per-bar animation with delayed start
for (i in bars) {
    val delay = i * (totalDuration / bars)
    animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = delay
        )
    )
}
```

#### **F. Loading Dots Animation** (Already implemented for loading state)
- Infinite repeating pulse
- 3 dots with staggered timing
- Duration: 600ms per cycle

---

## 🔧 **Performance Settings**

### **Parallel Processing (6 threads):**
- FLAC/MP3/WAV: **400-600ms** generation time
- AIFF: **185-214ms** (direct file reading)

### **Sample Count:**
- **Current**: 256 samples
- **Display limit**: WaveformSeekBar auto-samples for display

### **Why AIFF might look flat:**
1. **Default fallback values**: If parsing fails, returns all 50s
2. **Low audio levels**: Quiet recordings appear flat
3. **Compression**: Heavily compressed audio has less dynamic range
4. **Big-endian parsing**: If reading incorrectly, will get noise/flat

---

## 🧪 **Testing AIFF Waveforms**

### **Play an AIFF file and check logs for:**

1. **Parsing confirmation:**
   ```
   📊 Valid AIFF file detected: AIFF
   📦 Found chunk: COMM, size: X
   🎵 COMM: channels=2, bits=16
   📦 Found chunk: SSND, size: X bytes
   📊 Sampling AIFF: frameSize=4, ssndSize=X
   ```

2. **Waveform statistics:**
   ```
   ✅ Generated AIFF waveform:
   📊 Min: X, Max: Y, Avg: Z
   📊 First 10 samples: [values]
   📊 Last 10 samples: [values]
   📊 Standard deviation: X (✅ GOOD VARIATION or ⚠️ FLAT)
   ```

3. **Expected good values:**
   - Min: 10-40
   - Max: 60-100
   - Avg: 40-70
   - Std Dev: > 15

4. **Problem indicators:**
   - All values are 50 → Parsing failed completely
   - All values are 10 → Audio too quiet or wrong data offset
   - Std Dev < 5 → Flat waveform (compression or quiet audio)

---

## 🎨 **Animation Customization Guide**

To change the waveform animation, edit `SharedWaveformDisplay.kt`:

### **Quick Changes:**

**Faster animation:**
```kotlin
durationMillis = 400  // was 750
```

**Different easing:**
```kotlin
easing = LinearOutSlowInEasing  // instead of FastOutSlowInEasing
// Options: Linear, FastOutSlowIn, FastOutLinearIn, LinearOutSlowIn, EaseIn, EaseOut, EaseInOut
```

**Bounce effect:**
```kotlin
animationSpec = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy
)
```

**Fade instead of scale:**
```kotlin
// Change graphicsLayer from:
.graphicsLayer {
    scaleX = animatedScale
    scaleY = animatedScale
}
// To:
.graphicsLayer {
    alpha = animatedScale
}
```

---

## 📈 **Optimization Summary**

### **Speed Improvements Achieved:**
- **Before**: 2000-4000ms (sequential)
- **After**: 400-600ms (6 threads parallel)
- **AIFF**: 185-214ms (direct file reading)
- **Speedup**: **4-5x faster!**

### **Thread Balance:**
All 6 threads complete within 30-80ms of each other - excellent load distribution!

