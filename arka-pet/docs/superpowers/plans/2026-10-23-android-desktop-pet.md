# Android Desktop Pet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app with a floating overlay pet that wanders, chats via AI, and supports swappable sprite + Live2D skins downloaded on demand.

**Architecture:** Single `app` module. A foreground `PetOverlayService` owns a `WindowManager` overlay view hosting the active `PetRenderer` (sprite or Live2D) plus a collapsible chat panel. State, data, and AI live in plain Kotlin classes testable on the JVM. Assets are CC0 and fetched on demand from a bundled manifest.

**Tech Stack:** Kotlin 2.0, Android Gradle Plugin 8.7, minSdk 26 / targetSdk 35, Jetpack (Lifecycle, Room, DataStore, WorkManager not needed), OkHttp 4, Gson, Live2D Cubism Native SDK (fetched in Task 2). Tests: JUnit4 + MockK + Robolectric for JVM; Espresso optional.

**Spec:** `docs/superpowers/specs/2026-10-23-android-desktop-pet-design.md`

## Global Constraints

- minSdk 26, targetSdk 35, compileSdk 35.
- Kotlin only; no Java source.
- Assets: CC0 only; every manifest entry must carry `license` and `author`.
- AI: user-supplied API key stored in `EncryptedSharedPreferences`; never logged.
- No network calls on the main thread.
- Overlay never blocks touch to underlying apps except on the pet view itself.

## Review Focus

1. Overlay permission missing at launch → app must route to settings, not crash.
2. Pet dragged partially off-screen → must clamp to visible bounds.
3. Interrupted asset download → must not leave a half-installed pet selectable.
4. Empty/invalid API key → chat shows a friendly error, no crash, no retry storm.
5. Boot auto-start with zero installed pets → service must no-op gracefully and show onboarding on next open.

---

### Task 1: Scaffold Android project

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/src/main/java/com/example/pet/PetApplication.kt`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: buildable empty app with `PetApplication` registered.

- [ ] **Step 1: Create project skeleton** using the existing Gradle wrapper pattern from `/root/workspace/Arka-Code/android` (copy `gradle/wrapper/*` and `gradlew`). Set applicationId `com.example.pet`, namespace same.
- [ ] **Step 2: Add dependencies** in `app/build.gradle.kts`: androidx.core-ktx, appcompat, material, lifecycle-runtime-ktx, room-runtime + ktx + compiler (ksp), datastore-preferences, security-crypto, okhttp, gson, coroutines-android, live2d (placeholder, added Task 2).
- [ ] **Step 3: Write `PetApplication`** extending `Application`, initializing a singleton `ServiceLocator` (empty for now).
- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** `chore: scaffold android project`.

---

### Task 2: Add Live2D Cubism SDK

**Files:**
- Modify: `app/build.gradle.kts` (add NDK/CMake, native lib)
- Create: `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/live2d_bridge.cpp` (thin JNI shim)
- Create: `app/src/main/java/com/example/pet/render/Live2DRenderer.kt`

**Interfaces:**
- Produces: `Live2DRenderer : PetRenderer` (may throw `UnsupportedOperationException` until model loaded; smoke-tested by loading sample model).

- [ ] **Step 1: Download Cubism Native SDK** into `third_party/cubism/` (documented in README; commit a fetch script `scripts/fetch_cubism.sh`). If offline, task is skipped and Live2D renderer returns "unavailable".
- [ ] **Step 2: Wire CMake** to compile the shim exposing `init`, `load`, `update`, `draw`.
- [ ] **Step 3: Implement `Live2DRenderer`** wrapping the shim; load model from a directory.
- [ ] **Step 4: Verify build** (with SDK present) or confirm graceful fallback (without).
- [ ] **Step 5: Commit**.

---

### Task 3: Permissions and overlay entry

**Files:**
- Create: `app/src/main/java/com/example/pet/overlay/OverlayPermissionHelper.kt`
- Modify: `AndroidManifest.xml` (permissions: `SYSTEM_ALERT_WINDOW`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`)
- Create: `app/src/main/java/com/example/pet/ui/PermissionActivity.kt`

**Interfaces:**
- Produces: `OverlayPermissionHelper.hasOverlayPermission(context): Boolean`, `requestOverlayPermission(activity)`.

- [ ] **Step 1: Write failing unit test** `OverlayPermissionHelperTest` asserting `hasOverlayPermission` returns false when `Settings.canDrawOverlays` is false (Robolectric).
- [ ] **Step 2: Run test** → FAIL.
- [ ] **Step 3: Implement** helper wrapping `Settings.canDrawOverlays`.
- [ ] **Step 4: Run test** → PASS.
- [ ] **Step 5: Implement `PermissionActivity`** that checks permission and routes to system settings if missing (Review Focus #1).
- [ ] **Step 6: Commit**.

---

### Task 4: Foreground overlay service

**Files:**
- Create: `app/src/main/java/com/example/pet/overlay/PetOverlayService.kt`
- Create: `app/src/main/java/com/example/pet/overlay/OverlayView.kt`
- Modify: `AndroidManifest.xml` (service declaration with `foregroundServiceType="specialUse"` and property).

**Interfaces:**
- Consumes: `OverlayPermissionHelper`, `PetRenderer`.
- Produces: `PetOverlayService.start(context)`, `stop(context)`, `setPet(petId)`.

- [ ] **Step 1: Implement service** with notification, `WindowManager` overlay view, touch handling.
- [ ] **Step 2: Drag handling** with bounds clamping to screen (Review Focus #2) — unit-test the clamp math in `OverlayView.clampPosition(x,y,screenW,screenH,viewW,viewH)`.
- [ ] **Step 3: Write test** `OverlayViewTest.clampPosition_keepsViewOnScreen`.
- [ ] **Step 4: Run test** → PASS.
- [ ] **Step 5: Commit**.

---

### Task 5: Pet model and manifest

**Files:**
- Create: `app/src/main/java/com/example/pet/model/Pet.kt`, `app/src/main/java/com/example/pet/data/AssetManifest.kt`
- Create: `app/src/main/assets/manifest.json` (3-5 CC0 pets: 2 animals sprite, 3 chibi live2d)
- Create: `app/src/test/.../AssetManifestTest.kt`

**Interfaces:**
- Produces: `data class Pet(id, name, type: PetType, assetUrl, previewUrl, license, author)`; `AssetManifest.load(json): List<Pet>`.

- [ ] **Step 1: Write failing test** parsing a sample manifest JSON, asserting fields and that all entries have non-blank license/author.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `Pet`, `PetType`, `AssetManifest`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Add real `manifest.json`** with CC0 entries (LuizMelo cats, Live2D official samples).
- [ ] **Step 6: Commit**.

---

### Task 6: Asset download and install

**Files:**
- Create: `app/src/main/java/com/example/pet/data/PetRepository.kt`
- Create: `app/src/test/.../PetRepositoryTest.kt`

**Interfaces:**
- Consumes: `AssetManifest`, OkHttp.
- Produces: `PetRepository.listInstalled(): List<Pet>`, `install(pet): Result<Unit>`, `setActive(petId)`.

- [ ] **Step 1: Write failing test** (MockK OkHttp) that a failed download leaves the pet not in `listInstalled()` (Review Focus #3).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** download to temp file, verify (non-empty + checksum if provided), then atomically move into app-private `pets/<id>/`; on any failure delete temp.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit**.

---

### Task 7: Sprite renderer

**Files:**
- Create: `app/src/main/java/com/example/pet/render/PetRenderer.kt` (interface), `SpriteRenderer.kt`
- Create: `app/src/test/.../SpriteRendererTest.kt`

**Interfaces:**
- `interface PetRenderer { fun load(pet: Pet); fun update(dt: Float); fun draw(canvas: Canvas); fun onTap() }`.

- [ ] **Step 1: Define interface.**
- [ ] **Step 2: Implement `SpriteRenderer`** loading sprite sheet + frame rect config JSON; frame-based animation.
- [ ] **Step 3: Unit test** frame advancement logic (pure function extracted).
- [ ] **Step 4: Commit**.

---

### Task 8: Idle state machine

**Files:**
- Create: `app/src/main/java/com/example/pet/idle/IdleStateMachine.kt`
- Create: `app/src/test/.../IdleStateMachineTest.kt`

**Interfaces:**
- Produces: `IdleStateMachine` with states `WANDER, IDLE, SIT, SLEEP` and `nextState(current, random, dt)`.

- [ ] **Step 1: Write failing tests** for transitions (e.g., after N seconds idle → SIT; tap → CHAT).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** pure state machine.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit**.

---

### Task 9: AI provider abstraction

**Files:**
- Create: `app/src/main/java/com/example/pet/ai/AiProvider.kt`, `OpenAiCompatibleClient.kt`
- Create: `app/src/test/.../OpenAiCompatibleClientTest.kt`

**Interfaces:**
- `data class ProviderConfig(type, baseUrl, model, apiKey)`.
- `class OpenAiCompatibleClient(config): suspend fun chat(messages): Result<String>`.

- [ ] **Step 1: Write failing test** building request JSON with correct URL/headers/body (MockWebServer).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** client; on empty apiKey return `Result.failure` with friendly message (Review Focus #4).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Add preset list** (Gemini, OpenAI, Claude, Groq, DeepSeek, Custom) as config factory with default base URLs/models.
- [ ] **Step 6: Commit**.

---

### Task 10: Chat history persistence

**Files:**
- Create: `app/src/main/java/com/example/pet/data/ChatMessage.kt`, `ChatDao.kt`, `AppDatabase.kt`
- Create: `app/src/test/.../ChatDaoTest.kt` (Robolectric + Room in-memory).

- [ ] **Step 1: Define entity + DAO** (insert, getByPet, clear).
- [ ] **Step 2: Write test** insert + query round-trip.
- [ ] **Step 3: Implement**.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit**.

---

### Task 11: Chat panel UI

**Files:**
- Create: `app/src/main/java/com/example/pet/ui/ChatPanel.kt` (overlay sub-view), layout XML.
- Modify: `OverlayView.kt` to host panel.

**Interfaces:**
- Consumes: `OpenAiCompatibleClient`, `ChatDao`.
- Produces: `ChatPanel.show/hide`, `send(text)`.

- [ ] **Step 1: Layout** chat bubble + input + send button.
- [ ] **Step 2: Wire send** → append user msg, call client, append reply or error.
- [ ] **Step 3: Manual smoke test** on emulator.
- [ ] **Step 4: Commit**.

---

### Task 12: Gallery UI

**Files:**
- Create: `app/src/main/java/com/example/pet/ui/GalleryActivity.kt`, layout, adapter.
- Create: `app/src/test/.../GalleryViewModelTest.kt` (if VM added).

- [ ] **Step 1: List available pets** from manifest + installed state.
- [ ] **Step 2: Download on select** with progress; disable selection while downloading (Review Focus #3).
- [ ] **Step 3: Switch active pet** → service updates renderer.
- [ ] **Step 4: Commit**.

---

### Task 13: Settings UI

**Files:**
- Create: `app/src/main/java/com/example/pet/ui/SettingsActivity.kt`, layout.

- [ ] **Step 1: Provider picker** + base URL/model/api key inputs; save to EncryptedSharedPreferences.
- [ ] **Step 2: Idle toggles** (wander on/off, auto-start on/off).
- [ ] **Step 3: Commit**.

---

### Task 14: Boot receiver (auto-start)

**Files:**
- Create: `app/src/main/java/com/example/pet/boot/BootReceiver.kt`
- Modify: `AndroidManifest.xml`.

**Interfaces:**
- Consumes: `PetRepository` (installed count), settings.

- [ ] **Step 1: Write test** logic: if auto-start enabled AND at least one pet installed → start service; else no-op (Review Focus #5).
- [ ] **Step 2: Implement** receiver delegating to a pure function `shouldAutoStart(enabled, installedCount)`.
- [ ] **Step 3: Run test** → PASS.
- [ ] **Step 4: Commit**.

---

### Task 15: Main activity / onboarding

**Files:**
- Create: `app/src/main/java/com/example/pet/ui/MainActivity.kt`, layout.

- [ ] **Step 1: Onboarding flow**: request overlay permission → if none installed, open gallery; else start service + finish.
- [ ] **Step 2: Commit**.

---

### Task 16: End-to-end smoke

- [ ] **Step 1: Build debug APK** `./gradlew assembleDebug`.
- [ ] **Step 2: Install on emulator**, grant overlay, download a cat, verify wander + chat with a real provider key.
- [ ] **Step 3: Fix issues** found.
- [ ] **Step 4: Commit** any fixes.

---

### Task 17: README and licenses

**Files:**
- Create: `README.md` with build/run instructions, asset credits, Live2D fetch script usage.

- [ ] **Step 1: Write README.**
- [ ] **Step 2: Commit**.

---

## Self-review notes

- Spec coverage: overlay (T4), idle (T8), chat (T9/T11), gallery (T12), settings (T13), two renderers (T2/T7), CC0 manifest (T5), on-demand download (T6), auto-start (T14), permission route (T3). All covered.
- Type consistency: `PetRenderer`, `Pet`, `ProviderConfig`, `PetRepository`, `IdleStateMachine` signatures defined once in Interfaces blocks and reused.
- Review Focus items each have an owning task and test.
