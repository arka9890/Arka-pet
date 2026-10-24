# Desktop Pet (Android) — Design

Date: 2026-10-23
Approach: Native Android (Kotlin)

## Intent and why

A floating virtual pet that lives on top of every other app on Android — like a desktop pet, but on a phone. Tap it to chat via an AI model; leave it alone and it wanders/does random things. Swappable skins: animals (sprite sheets) and anime chibi (Live2D). Assets download on demand so the APK stays small.

## Goals

- Floating overlay window visible above other apps (draggable, tappable).
- Idle: random wander + activities (sit, sleep, etc.) via a state machine.
- Tap → chat panel → send/receive messages with an AI model.
- Skin gallery: browse available pets, download on demand, switch active pet.
- Two renderers: sprite-sheet (animals) and Live2D (chibi anime).
- AI provider settings: presets (Gemini, OpenAI, Claude, Groq, DeepSeek, …) + custom OpenAI-compatible endpoint, per-provider API key.

## Non-goals

- No home-screen widget or wallpaper variant (overlay only).
- No voice input/TTS, no push notifications, no multiplayer.
- No in-app asset editor / rigging tools.
- No cloud sync of chat history (local only).
- No paid store or IAP; assets come from curated free CC0 sources.

## Anti-goals

- Must not drain battery or cause jank while other apps are in use.
- Must not require root or obscure permissions beyond overlay + internet.
- Must not bundle multi-MB assets into the APK.

## Constraints

- Android overlay requires `SYSTEM_ALERT_WINDOW` (user grants via settings) + a foreground service to keep the pet alive.
- Live2D Cubism SDK for Native: free for small-scale use; needs NDK/CMake integration; models must be `.moc3` + `.model3.json`.
- Sprite pets: PNG sprite sheets with defined frame rects.
- Asset policy: **CC0 only** (no attribution-required models). Realistic chibi Live2D count is small (target 3-5 curated models); sprite animals are plentiful.
- AI calls happen from device → user supplies own API key (stored locally, never shipped).
- Network: on-demand asset download; must handle offline (use cached pets).">

## Approaches considered

1. **Native Android (Kotlin)** — chosen. Best battery/perf for a long-running overlay; official SDK support for Live2D; one codebase.
2. **WebView hybrid** — rejected: easier Live2D via JS, but overlay + transparent WebView is battery-heavy and device-quirky; duplicates UI work.
3. **Flutter** — rejected: overlay + Live2D still require native plugins; no net benefit.

## Architecture (high level)

Single Android app module + a foreground service:

- `PetOverlayService` (foreground service) hosts a `WindowManager` overlay view containing the active pet renderer + a collapsible chat panel. Auto-starts on boot via `BOOT_COMPLETED` receiver (user can disable in settings).
- `PetRenderer` interface with two impls: `SpriteRenderer`, `Live2DRenderer`.
- `PetManager`: active pet id, list of installed pets, download/install from manifest.
- `AssetManifest`: bundled JSON list of available pets (name, type, preview url, download url, license, author).
- `ChatRepository`: builds request per selected provider, returns full response (no streaming); stores history in Room/SQLite (local).
- `Settings`: provider presets + custom endpoint, API key (EncryptedSharedPreferences), idle toggles.

## Data model (sketch)

- `Pet(id, name, type: SPRITE|LIVE2D, assetPath, previewUrl, license, author)`
- `ChatMessage(id, petId, role, text, timestamp)`
- `ProviderConfig(id, type: preset|custom, baseUrl, model, apiKey)`

## Permissions

- `SYSTEM_ALERT_WINDOW` (overlay) — requested with in-app guide to settings.
- `INTERNET` — AI + asset download.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (or dataSync) — keep service alive.
- `POST_NOTIFICATIONS` — foreground service notification (Android 13+).

## AI providers

Preset list with default base URL + suggested model:
Gemini, OpenAI, Anthropic (Claude), Groq, DeepSeek, plus "Custom (OpenAI-compatible)".
User pastes API key; stored encrypted. Provider chosen in settings; chat uses active provider.

## Asset loading

- Bundled `assets/manifest.json` with curated CC0 pets (URLs + licenses).
- Download to app-private storage on first selection; cache; offline fallback to installed pets.
- Show license/author in gallery.

## Idle behavior

State machine: WANDER (move along screen edge) → IDLE → random SIT/SLEEP/BLINK etc. → back to WANDER. Tap interrupts → CHAT_OPEN. Cooldowns to avoid spam.

## Resolved decisions (from brainstorming)

- Live2D licensing: CC0 only → chibi target 3-5 curated models.
- Chat: full response (no streaming).
- Auto-start on boot: yes (toggleable in settings).
- Technical calls by builder: minSdk 26 (overlay requirement); foreground service type `specialUse` with Play justification; ABIs arm64-v8a (+ armeabi-v7a).

- Exact gesture handling (drag vs tap) threshold.
- Live2D motion mapping (which motions for idle/chat).
- Chat panel size/position relative to pet.
- Whether to pause pet animation while chat open.

## What's left to the builder

All of the above; implementation plan to follow.

## Size

This is a project (multi-week). Full design above is the spec.
