# Cactus Gemma Chat Android Demo

A minimal Android app that uses Cactus Kotlin SDK to download and run `google/gemma-4-E2B-it` locally on an Android phone.

## What it does

- Builds a small Android APK.
- On first launch, the app downloads the Cactus-Compute pre-converted Gemma 4 E2B INT4 weights.
- After download and initialization, chat generation runs locally on device.

## Requirements

- Android phone with arm64-v8a CPU.
- Android 7.0 / API 24 or newer.
- Wi-Fi and several GB of free storage for the first model download.
- For Gemma 4 E2B, a recent flagship or upper-midrange Android device is recommended.

## Build in GitHub Actions

1. Create a new GitHub repo.
2. Upload this project.
3. Open the repo's Actions tab.
4. Run **Build Debug APK**.
5. Download the `cactus-gemma-chat-debug-apk` artifact.
6. Install `app-debug.apk` on your phone.

## Change model

In `MainActivity.kt`, change:

```kotlin
private val modelName = "google/gemma-4-E2B-it"
```

For a tiny smoke test, you can try:

```kotlin
private val modelName = "google/gemma-3-270m-it"
```

The app uses the canonical model names from Cactus `models.json`; Cactus maps them to pre-converted weights under Hugging Face `Cactus-Compute`.
