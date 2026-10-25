# Design — Gallery preview, settings entry, claymorphism theme

Date: 2026-10-24
Size: small change (extends the existing Arka Pet gallery flow)

## Intent

The gallery currently lists pets as bare `TextView` rows with no visual identity, no
way to reach the already-built `SettingsActivity`, and no way to see what a pet looks
like before downloading it. The user wants a claymorphism theme, a settings entry
point, and a **separate detail screen** per pet that shows the character preview with
a download button under it.

## Goals

1. Claymorphism visual theme applied app-wide (soft extruded cards, warm palette,
   buttons that depress when pressed).
2. Reachable Pengaturan (settings) from the gallery.
3. Tap a pet row → separate detail screen showing the character preview image
   (`manifest.json` `previewUrl`) + name/type/license/author.
4. Download button below the preview, driving the existing `PetRepository.install()`.

## Non-goals

- No new dependencies (no Coil/Glide — preview loads through the existing OkHttp).
- No dark mode: one light clay palette.
- No change to the overlay renderer or its placeholder circle.
- No change to the AI/chat flow, boot receiver, or asset manifest contents.
- No on-disk preview cache (loaded per visit, one small PNG).

## Constraints

- minSdk 26 / targetSdk 35 / Kotlin only; plain `android.app.Activity` +
  programmatic views (the codebase has no layout XML — keep it that way).
- `res/` XML (themes, colors, drawables) is expected and already used.
- Already shipped: `Theme.ArkaPet` + system-bar insets + dp padding on gallery and
  settings; `SettingsActivity` exists but has no entry point.

## Approaches

**A. Separate detail screen — chosen (user picked this).**
New `PetDetailActivity` launched from a row tap with the pet id as an extra; it
re-reads the bundled manifest to resolve the pet. Keeps the gallery a simple list,
gives the preview room to be large, and back-navigation works for free.

**B. Inline accordion expansion under the row.**
No navigation, but pushes the download button around as rows expand, and a 200dp
preview makes the list scroll constantly. Rejected by user.

**C. Dialog / bottom sheet over the gallery.**
Needs AppCompat/Fragment plumbing the app doesn't use (plain `Activity`, no
`appcompat` activity base). Rejected as more machinery than the feature is worth.

## Decisions

- **Theme:** `Theme.ArkaPet` switches to a `NoActionBar` base; each screen draws its
  own clay header (title + gear button). The platform action bar can't be restyled
  into claymorphism without fighting `windowTitleStyle`, and the header is 4 lines of
  code. Root background is a warm cream; cards and buttons are layer-list drawables
  with a light top-left layer and an offset dark bottom-right layer to read as clay
  extrusion. Pressed state swaps to a variant offset by 2dp (the "depress").
- **Settings entry:** gear button in the gallery header → `SettingsActivity`. Platform
  drawable `android.R.drawable.ic_menu_manage`, not an emoji (icon discipline).
- **Row → detail:** the whole row is clickable, `minHeight` 64dp, 16dp margins.
- **Preview loading:** `OkHttpClient` on `Dispatchers.IO`, decode to `Bitmap`, set on
  an `ImageView`. Show a "Loading preview…" placeholder while it fetches and an error
  state if it fails (never a blank box).
- **Download button:** full-width clay button under the preview. Toggles to a
  disabled "Downloading…" state during `repository.install()` (no double-tap), then
  sets the pet active and finishes, matching the existing `select()` behaviour.
  Already-installed pets show "Aktifkan" instead.
- **Settings screen:** gets the same clay background/margins as the gallery so the
  theme reads consistently; its fields stay as they are.

## Open / builder's call

- Exact hex values for the clay palette (cream base, card, light/dark shadow layers,
  accent) — picked to keep 4.5:1 text contrast.
- Exact spacing/corner-radius values within the clay convention.
