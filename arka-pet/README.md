# Arka Pet

A floating virtual pet for Android: an overlay window that lives on top of every other app, wanders when idle, and chats via an AI model. Supports swappable skins (sprite-sheet animals and Live2D chibi) downloaded on demand.

## Build

Requirements: JDK 17, Android SDK (API 35, build-tools 35.0.0), Gradle 8.13.

```bash
./gradlew :app:assembleDebug
```

Install the resulting APK (`app/build/outputs/apk/debug/app-debug.apk`) on a device or emulator (API 26+).

## First run

1. Launch the app; it requests "Display over other apps" permission and sends you to system settings. Enable it, then return.
2. With no pet installed, the gallery opens. Pick a pet (CC0) and wait for the download.
3. The pet appears as a floating overlay. Drag to move; tap to open chat.
4. Configure the AI provider in Settings (preset + API key). Keys are stored encrypted on device.

## AI providers

Presets: Gemini, OpenAI, Claude, Groq, DeepSeek, plus a Custom (OpenAI-compatible) option. Enter your own API key; it is stored in EncryptedSharedPreferences and never shipped.

## Assets

All bundled pets are CC0. Sprite pets (Kenney animal pack) and one Live2D chibi (Arch-chan, CC0). Additional pets can be added to `app/src/main/assets/manifest.json` (license + author required; CC0 only).

Live2D Cubism Native SDK is not vendored. To enable real Live2D rendering:

```bash
scripts/fetch_cubism.sh ~/Downloads/CubismSdkForNative-*.zip
```

Then wire NDK/CMake (TODO in `app/build.gradle.kts`) and implement `Live2DRenderer`.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Note: Room-based tests are skipped on aarch64 CI where Robolectric's native SQLite runtime is unavailable.

## License

Code: see repository license. Pet assets: CC0 (see manifest entries for author attribution).
