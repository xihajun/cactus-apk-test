# LiteRT-LM Gemma 4 Chat — Android Demo

A minimal Android app that uses **Google AI Edge LiteRT-LM** to run Gemma 4
locally on an Android phone with GPU acceleration.

## What it does

- Builds a small Android APK.
- Runs a `.litertlm` model file on-device via the LiteRT-LM Kotlin SDK.
- Supports GPU acceleration (via OpenCL) for fast inference.
- Streaming token generation with chat history.

## Requirements

- Android phone with arm64-v8a CPU.
- Android 8.0 / API 26 or newer.
- A `.litertlm` model file (e.g., from
  [`xihajun/gemma4-e4b-mixed-en-lora-r16-v6e-1536-litert-lm`](https://huggingface.co/xihajun/gemma4-e4b-mixed-en-lora-r16-v6e-1536-litert-lm)
  or the
  [LiteRT community models](https://huggingface.co/litert-community)).

## Build in GitHub Actions

1. Create a new GitHub repo.
2. Upload this project.
3. Open the repo's **Actions** tab.
4. Run **Build Debug APK**.
5. Download the `litertlm-gemma-chat-debug-apk` artifact.
6. Install `app-debug.apk` on your phone.
7. Push a `.litertlm` model file to the device:
   ```bash
   adb push model.litertlm /data/data/com.example.litertlmchat/files/model.litertlm
   ```

## Model setup

The app looks for the model file at:

```
/data/data/com.example.litertlmchat/files/model.litertlm
```

You can:
- `adb push` the file directly.
- Download from HuggingFace using the app's download button (requires HF token).
- Place it in external storage at `<app-external-files>/model.litertlm`.

## Change backend

In `MainActivity.kt`, change:

```kotlin
backend = Backend.GPU()     // GPU via OpenCL (fastest)
backend = Backend.CPU()     // CPU fallback
backend = Backend.NPU(nativeLibraryDir = applicationInfo.nativeLibraryDir)  // NPU
```

## Key difference from the old Cactus version

| | Old (Cactus SDK) | New (LiteRT-LM) |
|---|---|---|
| SDK | `com.cactuscompute:cactus` | `com.google.ai.edge.litertlm:litertlm-android` |
| Format | GGUF | `.litertlm` |
| Backend | CPU only (llama.cpp) | GPU / NPU / CPU |
| Status | Community SDK | Google official, production-grade |
| Models | E2B INT4 | E4B dynamic_wi8_afp32 |
