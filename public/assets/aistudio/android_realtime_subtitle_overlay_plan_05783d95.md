# Real-Time Local AI Subtitle Overlay App — Architecture & Development Plan

## 1. Project Overview & Key Requirements

* **Core Functionality:** Capture live audio played by third-party media apps (YouTube, Netflix, local media players), process the audio locally using AI speech-to-text / translation, and render real-time subtitles as a floating overlay.
* **Inference Pipeline:** Completely offline / local processing using `whisper.cpp` (via JNI/NDK) or `sherpa-onnx`.
* **Model Selection:** Dynamic user choice between **Tiny**, **Base**, and **Small** models (`ggml` quantized variants) to let users balance speed, RAM usage, battery, and accuracy (specifically optimized for English and Korean $\rightarrow$ English translation).
* **Target Hardware:** Android 10+ (API 29+) with 8 GB RAM target devices.

---

## 2. High-Level Architecture & Data Flow

```text
 System Audio / Micro-Buffer 
           │
           ▼
 [ AudioPlaybackCapture API / MediaProjection ] ──► (16 kHz 16-bit Mono PCM Chunks)
           │
           ▼
 [ Ring Audio Buffer Manager ] ──► (Rolling 2.5s – 3.0s window)
           │
           ▼
 [ Native Inference Engine ] ──► (C++ / NDK whisper.cpp with NEON optimization)
   ├── Model: Tiny / Base / Small (User Selected)
   └── Task: Transcribe OR Translate (Korean/Foreign -> English)
           │
           ▼
 [ Real-time Text Buffer & Smoother ]
           │
           ▼
 [ Floating Window Service ] ──► (Android System Alert Overlay UI)
```

---

## 3. Component Breakdown

### A. System Audio Capture Module
* **API Required:** `AudioPlaybackCapture` (Requires Android 10 / API 29+).
* **Permissions:** `RECORD_AUDIO`, `MEDIA_PROJECTION`.
* **Audio Format:** 16,000 Hz sample rate, single channel (Mono), 16-bit PCM encoding.
* **Mechanism:** A foreground service initializes a `MediaProjection` session (with user consent prompt), feeding an `AudioRecord` stream into an in-memory rolling ring-buffer.

### B. Dynamic Model Manager & NDK Inference Engine
* **Engine:** `whisper.cpp` compiled into native libraries (`.so`) using Android NDK with ARM NEON instructions enabled for low-latency CPU matrix operations.
* **Model Configs:**
  * **Tiny (`ggml-tiny.bin` / `ggml-tiny-q4_0.bin`):** ~40–75 MB download, sub-300ms latency. Low accuracy on Korean; ideal for fast simple speech.
  * **Base (`ggml-base.bin` / `ggml-base-q4_0.bin`):** ~140–200 MB download, ~700–1000ms latency. Balanced for Korean-to-English live translation.
  * **Small (`ggml-small.bin` / `ggml-small-q4_0.bin`):** ~460–600 MB download, ~2.0s–3.5s latency. High Korean nuance accuracy, suitable when accuracy is prioritized over ultra-low delay.
* **Memory Management:** Models loaded directly into C++ heap via `mmap` to prevent Java garbage collection overhead and keep overall RAM consumption optimized under 1.5 GB.

### C. Floating System Overlay Service
* **API Required:** `SYSTEM_ALERT_WINDOW` (`TYPE_APPLICATION_OVERLAY`).
* **UI Framework:** Jetpack Compose wrapped inside a custom Android `Service` or `WindowManager`.
* **Features:**
  * Draggable subtitle box (touch-and-drag re-positioning).
  * Transparency & font size controls.
  * Toggle button to quick-pause/resume live captioning.

---

## 4. Audio Streaming & Buffering Strategy (Solving Korean SOV Latency)

Korean uses **Subject-Object-Verb (SOV)** structure, whereas English uses **Subject-Verb-Object (SVO)**. To avoid inaccurate early translations:

1. **Sliding Window:** Process audio in **2.5s to 3.0s overlapping windows** with a step size of 0.5s–1.0s.
2. **Dynamic Text Stabilization:** Use a text-diffing algorithm on consecutive outputs to prevent subtitle flicker as Whisper re-evaluates the sentence structure when new audio chunks arrive.

---

## 5. System Permissions & Requirements Manifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.INTERNET" /> <!-- For initial model downloader only -->
</manifest>
```

---

## 6. Development Phasing & Roadmap

### Phase 1: Native Core Setup (NDK & Whisper integration)
* Setup Android NDK and CMake build scripts for `whisper.cpp`.
* Implement JNI wrappers in C++ to load models dynamically from mobile storage (`/sdcard/Android/data/...` or internal storage).
* Benchmark inference latency for Tiny, Base, and Small models on a sample 16kHz PCM buffer.

### Phase 2: Audio Pipeline & Capture
* Implement `MediaProjection` permission flow in Kotlin.
* Configure `AudioPlaybackCapture` to read system audio output.
* Create thread-safe ring-buffer transferring PCM byte arrays from Kotlin into C++ memory allocations.

### Phase 3: System Overlay & UI Settings
* Build Jetpack Compose UI for App Dashboard:
  * Model Downloader / Selector (Tiny, Base, Small).
  * Target Language Switch (Transcribe Native vs. Translate to English).
  * Subtitle Appearance Customizer (Font Size, Opacity, Background Color).
* Build floating `WindowManager` service with drag-and-drop support.

### Phase 4: Korean Real-time Optimization & Tuning
* Fine-tune sliding window step intervals (e.g., test 2.0s window / 500ms step vs. 3.0s window / 1.0s step).
* Implement text stabilization algorithms to minimize flickering of overlay captions.
* Thermal & Battery Profiling using Android Profiler.

---

## 7. Potential Technical Challenges & Solutions

| Challenge | Cause | Mitigation Strategy |
| :--- | :--- | :--- |
| **DRM Protected Content** | Apps like Netflix or Disney+ block `AudioPlaybackCapture` via secure flags. | Fallback option to use device Microphone audio capture (listening through speakers) if system stream is silent/blocked. |
| **Subtitle Flicker** | Frequent inference updates recalculate words. | Apply partial text matching and word hysteresis buffer before pushing updates to the UI overlay. |
| **CPU Thermal Throttling** | Continuous 100% CPU thread usage. | Restrict C++ thread count to `threads = min(4, available_cores - 2)` and introduce dynamic throttling if CPU temp rises. |