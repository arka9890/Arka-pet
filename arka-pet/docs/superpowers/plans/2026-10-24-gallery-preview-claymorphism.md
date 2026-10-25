# Gallery Preview & Claymorphism Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Arka Pet a claymorphism theme, a reachable Pengaturan entry, and a per-pet detail screen that previews the character image with a download button under it.

**Architecture:** Three tasks, each independently testable. Task 1 is pure Android resources (colours, dimens, layer-list drawables, theme) plus the one-line settings restyle. Task 2 adds `PetDetailActivity` with a pure decision object and a thin OkHttp preview loader, both unit-tested without network. Task 3 restyles the gallery into clay cards with a header (title + gear) and wires row taps to Task 2's activity.

**Tech Stack:** Kotlin, plain `android.app.Activity` with programmatic views (no layout XML — the repo has none), platform themes, layer-list drawables, OkHttp, Robolectric + mockk for tests.

**Spec:** `docs/superpowers/specs/2026-10-24-gallery-preview-claymorphism-design.md`

## Global Constraints

- minSdk 26, targetSdk 35, compileSdk 35, Kotlin only.
- Package stays `com.example.pet`.
- **No new dependencies** — preview loading uses the existing `com.squareup.okhttp3:okhttp`.
- **No layout XML.** Views are built in Kotlin; `res/` XML is for values and drawables only.
- Assets remain CC0-only; `manifest.json` contents are not changed.
- No main-thread network: preview fetch and `PetRepository.install()` run on `Dispatchers.IO`.
- Every Robolectric activity test carries `@Config(sdk = [34])` + `@ConscryptMode(ConscryptMode.Mode.OFF)` (no linux-aarch64 native runtime — see `PermissionActivityTest`).
- Build: `./gradlew :app:assembleDebug` from `arka-pet/`. Tests: `./gradlew :app:testDebugUnitTest --offline`.

## Review Focus

1. **Returning from the detail screen after a download leaves the gallery claiming "not installed".** Gallery must re-read `PetRepository.listInstalled()` in `onResume()`, not only `onCreate()` → tested in Task 3 by writing a `.installed` marker behind the activity and asserting the row text flips.
2. **Preview body that is not a decodable image** (truncated PNG, HTML 404 page, zero bytes) must make the loader return `null` rather than throw or yield a blank bitmap → tested in Task 2, `PreviewLoaderTest.unrecognizedBytes_returnNull`.
3. **Double-tapping Download fires two concurrent installs** → `DetailState.buttonEnabled(downloading = true)` is `false` and the label is `"Downloading…"` → tested in Task 2, `DetailStateTest`.
4. **Clay palette text falls below 4.5:1 contrast** (the design's stated floor) → tested in Task 1, `ClayThemeTest`, computing WCAG relative luminance over the real colour resources.
5. **Pet rows shrink below a comfortable touch target** at large font scale → rows carry `R.dimen.row_min_height` (64dp) → tested in Task 3, `GalleryActivityTest`.

---

### Task 1: Claymorphism resources, theme, settings restyle

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/dimens.xml`
- Create: `app/src/main/res/drawable/clay_bg.xml`
- Create: `app/src/main/res/drawable/clay_card.xml`
- Create: `app/src/main/res/drawable/clay_card_pressed.xml`
- Create: `app/src/main/res/drawable/clay_card_bg.xml`
- Create: `app/src/main/res/drawable/clay_button.xml`
- Create: `app/src/main/res/drawable/clay_button_pressed.xml`
- Create: `app/src/main/res/drawable/clay_button_bg.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/java/com/example/pet/ui/SettingsActivity.kt`
- Test: `app/src/test/java/com/example/pet/ui/ClayThemeTest.kt`

**Interfaces:**
- Produces (Tasks 2 and 3 consume these exact names):
  - Colours: `R.color.clay_bg`, `clay_card`, `clay_shadow_light`, `clay_shadow_dark`, `clay_accent`, `clay_text`, `clay_text_secondary`, `clay_on_accent`
  - Dimens: `R.dimen.screen_padding`, `card_margin`, `card_padding`, `row_min_height`, `button_min_height`, `corner`
  - Drawables: `R.drawable.clay_card_bg` (pressed selector, used by list rows), `clay_button_bg` (pressed selector, used by primary buttons), `clay_bg` (window background)
  - Style: `@style/Theme.ArkaPet` — parent `@android:style/Theme.Material.Light.NoActionBar`, `android:windowBackground = @color/clay_bg`

- [ ] **Step 1: Write the failing test**

`ClayThemeTest.kt`, Robolectric as above:

```kotlin
@Test fun theme_isNoActionBar_andUsesClayWindowBackground() {
    val ctx = RuntimeEnvironment.getApplication()
    ctx.setTheme(R.style.Theme_ArkaPet)
    val a = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.windowActionBar))
    assertFalse("theme must be NoActionBar", a.getBoolean(0, true))
    val b = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
    assertEquals(ContextCompat.getColor(ctx, R.color.clay_bg), b.getColor(0, 0))
    a.recycle(); b.recycle()
}

@Test fun clayCard_drawable_inflatesWithNonZeroCornerRadius() {
    val d = ResourcesCompat.getDrawable(RuntimeEnvironment.getApplication().resources, R.drawable.clay_card, null)
    assertTrue(d is GradientDrawable)
    assertTrue((d as GradientDrawable).cornerRadius > 0f)
}

@Test fun clayPalette_meets45Contrast() {
    // WCAG relative luminance over the real resources
    assertTrue("clay_text on clay_card", contrast(R.color.clay_text, R.color.clay_card) >= 4.5)
    assertTrue("clay_text on clay_bg", contrast(R.color.clay_text, R.color.clay_bg) >= 4.5)
    assertTrue("clay_on_accent on clay_accent", contrast(R.color.clay_on_accent, R.color.clay_accent) >= 4.5)
}
```

with a private `contrast(fg: Int, bg: Int): Double` helper in the test computing `(max(l1,l2)+0.05)/(min(l1,l2)+0.05)` from `android.graphics.Color`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.ClayThemeTest" --offline`
Expected: FAIL — `unresolved reference: clay_bg` (resources do not exist yet).

- [ ] **Step 3: Add the colour and dimen resources**

`colors.xml` (exact values — the contrast test pins them):

```xml
<color name="clay_bg">#F3E9DF</color>
<color name="clay_card">#FDF7F0</color>
<color name="clay_shadow_light">#FFFFFF</color>
<color name="clay_shadow_dark">#D9C7B6</color>
<color name="clay_accent">#F2A57E</color>
<color name="clay_text">#3E2E24</color>
<color name="clay_text_secondary">#6B5647</color>
<color name="clay_on_accent">#4A3428</color>
```

`dimens.xml`: `screen_padding=16dp`, `card_margin=12dp`, `card_padding=16dp`, `row_min_height=64dp`, `button_min_height=56dp`, `corner=24dp`.

- [ ] **Step 4: Add the clay drawables**

`clay_card.xml` / `clay_card_pressed.xml` are three-item `<layer-list>`s inside one `<shape android:shape="rectangle">` per layer, radius `@dimen/corner`, giving the clay extrusion:

1. **dark layer** (bottom-right extrusion): item insets `left=0dp top=4dp right=4dp bottom=0dp`, solid `@color/clay_shadow_dark`
2. **light layer** (top-left rim): item insets `left=3dp top=0dp right=0dp bottom=3dp`, solid `@color/clay_shadow_light`
3. **face**: item insets `left=3dp top=3dp right=0dp bottom=0dp`, solid `@color/clay_card`

`clay_card_pressed.xml` is identical but the **face layer sits 2dp lower** (`left=5dp top=5dp`) so the card visibly depresses. `clay_button.xml` / `clay_button_pressed.xml` use the same structure with `solid @color/clay_accent` on the face.

`clay_card_bg.xml` and `clay_button_bg.xml` are `<selector>`s: `state_pressed="true"` → the `_pressed` variant, `@drawable/clay_card` / `@drawable/clay_button` otherwise. `clay_bg.xml` is a single solid shape of `@color/clay_bg`.

- [ ] **Step 5: Switch the theme to NoActionBar with the clay background**

Replace the existing `Theme.ArkaPet` body in `themes.xml` (the `parent` changes from `Theme.Material.Light` to `Theme.Material.Light.NoActionBar`; add `android:windowBackground = @color/clay_bg`).

Note: the gallery has no title until Task 3 adds its own header — that is the intended intermediate state.

- [ ] **Step 6: Restyle the settings screen padding**

In `SettingsActivity.onCreate`, replace the literal `inset` (`24 * density`) with `resources.getDimensionPixelSize(R.dimen.screen_padding)`. Nothing else changes; the theme now supplies the background.

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.ClayThemeTest" --offline`
Expected: PASS (3 tests). If the contrast test fails, adjust the offending colour — not the assertion.

- [ ] **Step 8: Run the full suite and build**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --offline`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res app/src/main/java/com/example/pet/ui/SettingsActivity.kt app/src/test/java/com/example/pet/ui/ClayThemeTest.kt
git commit -m "feat: claymorphism theme, drawables and palette"
```

---

### Task 2: Pet detail screen (preview + download)

**Files:**
- Create: `app/src/main/java/com/example/pet/ui/DetailState.kt`
- Create: `app/src/main/java/com/example/pet/ui/PreviewLoader.kt`
- Create: `app/src/main/java/com/example/pet/ui/PetDetailActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (activity entry, `android:exported="false"`)
- Test: `app/src/test/java/com/example/pet/ui/DetailStateTest.kt`
- Test: `app/src/test/java/com/example/pet/ui/PreviewLoaderTest.kt`
- Test: `app/src/test/java/com/example/pet/ui/PetDetailActivityTest.kt`

**Interfaces:**
- Consumes: `R.color.clay_*`, `R.dimen.*`, `R.drawable.clay_card_bg`, `clay_button_bg` (Task 1); `AssetManifest.load`, `PetRepository.install/setActive/listInstalled`, `model.Pet` (existing).
- Produces (Task 3 relies on these):
  - `object DetailState { fun buttonLabel(installed: Boolean, downloading: Boolean): String; fun buttonEnabled(downloading: Boolean): Boolean }`
  - `class PreviewLoader(client: OkHttpClient) { suspend fun load(url: String): Bitmap? }`
  - `class PetDetailActivity : Activity()` with `companion object { const val EXTRA_PET_ID = "pet_id" }`

- [ ] **Step 1: Write the failing `DetailStateTest`**

```kotlin
@Test fun labels_switchByInstallAndDownloadState() {
    assertEquals("Download", DetailState.buttonLabel(installed = false, downloading = false))
    assertEquals("Downloading…", DetailState.buttonLabel(installed = false, downloading = true))
    assertEquals("Aktifkan", DetailState.buttonLabel(installed = true, downloading = false))
    assertEquals("Aktifkan", DetailState.buttonLabel(installed = true, downloading = true))
}

@Test fun buttonDisablesWhileDownloading() {
    assertTrue(DetailState.buttonEnabled(downloading = false))
    assertFalse(DetailState.buttonEnabled(downloading = true))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.DetailStateTest" --offline`
Expected: FAIL — `unresolved reference: DetailState`.

- [ ] **Step 3: Implement `DetailState`**

Plain `object` with the two functions; no Android imports. Label precedence: `installed` wins over `downloading` (the button only downloads when the pet is missing, so an installed pet never enters a download state).

- [ ] **Step 4: Run it to verify it passes**

Run: same command. Expected: PASS (2 tests).

- [ ] **Step 5: Write the failing `PreviewLoaderTest`**

mockk, following `PetRepositoryTest`'s OkHttp mocking (`Call` returning a `Response` with a `toResponseBody()` body):

```kotlin
@Test fun unrecognizedBytes_returnNull() = runBlocking {
    every { call.execute() } returns response(ByteArray(64) { 0x7f })   // not an image
    assertNull(loader.load("https://example.test/preview.png"))
}

@Test fun emptyBody_returnNull() = runBlocking {
    every { call.execute() } returns response(ByteArray(0))
    assertNull(loader.load("https://example.test/preview.png"))
}

@Test fun validPngBytes_decodeToBitmap() = runBlocking {
    every { call.execute() } returns response(pngBytes())                // 1x1 PNG built in the test
    assertNotNull(loader.load("https://example.test/preview.png"))
}
```

plus a test for a non-URL string asserting `load("not-a-url")` returns `null` (bad input must not throw).

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.PreviewLoaderTest" --offline`
Expected: FAIL — `unresolved reference: PreviewLoader`.

- [ ] **Step 7: Implement `PreviewLoader`**

`class PreviewLoader(private val client: OkHttpClient)` with `suspend fun load(url: String): Bitmap?`:
runs on `Dispatchers.IO`, builds the `Request` (return `null` if the URL does not parse), executes, checks `isSuccessful` and a non-empty body, then `BitmapFactory.decodeByteArray`. Any exception or decode failure returns `null` — it never throws and never returns an empty bitmap.

- [ ] **Step 8: Run it to verify it passes**

Run: same command. Expected: PASS.

- [ ] **Step 9: Write the failing `PetDetailActivityTest`**

```kotlin
@Test fun showsPetMetadata_andDownloadButton() {
    val activity = launch("panda")
    assertTrue(texts(activity).any { it.contains("Panda") })
    assertTrue(texts(activity).any { it.contains("Kenney") })          // author
    assertEquals("Download", downloadButton(activity).text)
    assertTrue(downloadButton(activity).isEnabled)
}

@Test fun installedPet_offersActivateInsteadOfDownload() {
    // write the marker behind the activity: filesDir/pets/panda/.installed
    File(RuntimeEnvironment.getApplication().filesDir, "pets/panda/.installed")
        .apply { parentFile.mkdirs(); createNewFile() }
    assertEquals("Aktifkan", downloadButton(launch("panda")).text)
}
```

where `launch(id)` builds `Intent` with `PetDetailActivity.EXTRA_PET_ID`, and `texts(activity)` walks `activity.window.decorView` collecting `TextView.text`. A shared `walk()` helper lives in the test.

- [ ] **Step 10: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.PetDetailActivityTest" --offline`
Expected: FAIL — `unresolved reference: PetDetailActivity`.

- [ ] **Step 11: Implement `PetDetailActivity`**

Layout, all programmatic, built on a vertical `LinearLayout` with `R.dimen.screen_padding`:

1. Header row: back affordance (`android.R.drawable.ic_menu_revert`, `contentDescription = "Kembali"`, `finish()` on click) + pet name in `R.dimen.card_padding`.
2. Preview block: an `ImageView` (`clay_card_bg`, `adjustViewBounds`) plus a `TextView` status line. Initially the status reads `"Memuat pratinjau…"` and the image is empty. On `PreviewLoader.load(pet.previewUrl)` success → set bitmap, clear the status; on `null` → status becomes `"Pratinjau tidak tersedia"` and the image stays empty (never a blank white box).
3. Meta card (`clay_card_bg`): type, license, author — colour `R.color.clay_text_secondary`.
4. Primary button: `clay_button_bg`, `minHeight = R.dimen.button_min_height`, text from `DetailState`, text colour `R.color.clay_on_accent`.

`onCreate` reads the manifest via `AssetManifest.load`, builds `PetRepository(OkHttpClient(), filesDir, pets)`, resolves the pet from `EXTRA_PET_ID`, and `finish()`s if the id is unknown. Button click: set `downloading = true` first (so a second tap hits `buttonEnabled == false`), then on `Dispatchers.IO` call `repository.install(pet)`; on success `repository.setActive(pet.id)` + `finish()`, on failure show the message in the status line. Refresh `downloading`/label in `onResume`.

- [ ] **Step 12: Register the activity in the manifest**

Next to the other `<activity>` entries, `android:exported="false"`.

- [ ] **Step 13: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.*" --offline`
Expected: PASS. The activity test only asserts synchronous structure; preview I/O correctness is covered by `PreviewLoaderTest`, so no test waits on the network.

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/com/example/pet/ui app/src/main/AndroidManifest.xml app/src/test/java/com/example/pet/ui
git commit -m "feat: pet detail screen with preview and download"
```

---

### Task 3: Gallery clay cards, header, and navigation

**Files:**
- Modify: `app/src/main/java/com/example/pet/ui/GalleryActivity.kt`
- Test: `app/src/test/java/com/example/pet/ui/GalleryActivityTest.kt`

**Interfaces:**
- Consumes: everything from Task 1 (`R.dimen.row_min_height`, `R.drawable.clay_card_bg`, …) and Task 2 (`PetDetailActivity.EXTRA_PET_ID`).
- Produces: nothing — this is the terminal task.

- [ ] **Step 1: Write the failing `GalleryActivityTest`**

Four tests, same `walk()` helper as Task 2:

1. `showsAllThreePetsAsRows` — `Robolectric.buildActivity(GalleryActivity::class.java).setup()`; assert the view tree contains a `TextView` for each of `Panda`, `Rabbit`, `Arch-chan`, and that each row's `minimumHeight >= resources.getDimensionPixelSize(R.dimen.row_min_height)`.
2. `gearButton_opensSettings` — find the button with `contentDescription == "Pengaturan"`, `performClick()`, assert `shadowOf(activity).nextStartedActivity.component?.className == SettingsActivity::class.java.name`.
3. `rowClick_opensDetailWithThatPetId` — `performClick()` on the Panda row, assert the next intent targets `PetDetailActivity` and carries `EXTRA_PET_ID == "panda"`.
4. `onResume_reflectsNewlyInstalledPet` — write `filesDir/pets/panda/.installed`, call `activity.onResume()`, assert the Panda row's text now contains `installed`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.GalleryActivityTest" --offline`
Expected: FAIL — no gear button, no `PetDetailActivity` intent.

- [ ] **Step 3: Restructure the screen root into header + scroll**

`GalleryActivity` currently sets a bare `ScrollView` as content. Wrap it in a vertical `LinearLayout` (`R.dimen.screen_padding` horizontal padding):

- Header `LinearLayout` (horizontal, vertical padding `R.dimen.screen_padding`): title `TextView` `"Arka Pet"` (`textSize 22f`, `R.color.clay_text`, weight 1) + `ImageButton` `setImageResource(android.R.drawable.ic_menu_manage)`, `contentDescription = "Pengaturan"`, `background = null`, `setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }`.
- The `ScrollView` keeps its insets listener but drops the horizontal padding and the `8.dp()` fudge — the root owns horizontal padding now, so the listener becomes `v.setPadding(0, bars.top, 0, bars.bottom)`, otherwise the two paddings stack.

- [ ] **Step 4: Make each row a clay card**

Per pet, replace the bare `TextView` with the existing `TextView` given:
`background = R.drawable.clay_card_bg`, padding `R.dimen.card_padding`, `minimumHeight = R.dimen.row_min_height`, `layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, card_margin, 0, card_margin) }`, `textColor = R.color.clay_text`, `textSize = 16f`, plus a second line for type + status (`R.color.clay_text_secondary`).

`setOnClickListener` → `startActivity(Intent(this, PetDetailActivity::class.java).putExtra(PetDetailActivity.EXTRA_PET_ID, pet.id))`. The old inline `downloading` guard disappears — rows no longer download anything themselves.

- [ ] **Step 5: Refresh on resume**

Add `override fun onResume() { super.onResume(); render(pets) }` — this is Review Focus #1. `render()` already clears and rebuilds `container`, so calling it twice is safe.

- [ ] **Step 6: Run it to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.pet.ui.*" --offline`
Expected: PASS.

- [ ] **Step 7: Full suite + build**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --offline`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/pet/ui/GalleryActivity.kt app/src/test/java/com/example/pet/ui/GalleryActivityTest.kt
git commit -m "feat: claymorphism gallery with settings entry and pet detail navigation"
```
