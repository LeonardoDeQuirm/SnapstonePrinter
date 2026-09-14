# SnapstonePrinter — Handoff

**Read this first.** A future session has zero memory of how this app got here. Several
of the decisions below look arbitrary or removable. They are not. Each one is a fix for a
real, reproduced failure. Undoing them re-breaks the app.

App: Android app that rolls a random Magic: The Gathering card from Scryfall, renders it as
a 384px-wide 1-bit dithered "proxy slip", and hands it to an external Bluetooth thermal
printer app via `ACTION_SEND`.

Package: `com.example.snapstoneprinter` · Module: `:app` · Single-module Gradle project.

---

## 1. Current state

**Compiles clean.** `:app:assembleDebug` — SUCCESS, no fixes required.

| Check | Result |
|---|---|
| `:app:assembleDebug` | ✅ SUCCESS |
| `:app:testDebugUnitTest` | ✅ **67 passed**, 0 failed, 0 skipped |
| `:app:connectedDebugAndroidTest` | ✅ **31 passed**, 0 failed (Pixel 10 Pro XL, API 36) |

Baselines going into this session were 63 unit / 31 instrumented, so unit coverage grew by 4
and instrumented held steady. Nothing is red, nothing is stubbed out, nothing was disabled.

### Finished

- Scryfall random-card fetch with junk-layout re-roll (`CardRepository`, `CardLayouts`,
  `ScryfallQueryBuilder`).
- Moshi codegen parsing of the card + `card_faces` model.
- Slip planning / DFC detection and label wording (`SlipPlanner`).
- Mana cost formatting (`ManaCostFormatter`).
- Image pipeline: auto-levels → Floyd-Steinberg → 384px mono output (`ImageProcessor`).
- Compose UI shell: generator screen, thermal preview, DFC pager.

### Half-done — the four files from the cancelled batch

These were written but had never been compiled or run before this session. They **do compile
and the suite is green**, but they are not fully wired through to a working user flow.

| File | Intended purpose | State |
|---|---|---|
| `image/ArtDownloader.kt` | Coil-based art fetch that returns a **software** `Bitmap` the ditherer can read pixel-by-pixel. Wraps `ImageLoader`/`ImageRequest`, rasterises any `Drawable` to `ARGB_8888`, returns `ArtResult.Success/Failure` with a logged reason instead of a bare null. | Compiles; constructed at `ProxyGeneratorViewModel.kt:133`. Believed to be the in-progress fix for the missing-art bug (see §5). Not yet verified end-to-end on device. |
| `data/print/PrinterTargetStore.kt` | DataStore persistence of the user's chosen printer app `ComponentName`, so the chooser is shown once and subsequent prints go straight to the remembered target. | Compiles; persistence path unverified. Needs a reset affordance in the UI. |
| `data/print/ChosenComponentReceiver.kt` | `BroadcastReceiver` for `Intent.EXTRA_CHOSEN_COMPONENT`, captures which app the user picked out of the share sheet and feeds it to `PrinterTargetStore`. | Compiles; not confirmed registered/firing. |
| `ui/ProxyPanels.kt` | Extracted Compose panels for the generator screen (card detail / controls / preview panels). | Compiles and renders. |

---

## 2. Immutable spec — the user is firm on all of these

- **384px output width.** 384 dots = 57mm at 203dpi. This is the printer's native width. Do
  not parameterise it away or "scale to fit".
- **Floyd-Steinberg dithering, 7/3/5/1 ÷ 16 kernel.** Exactly this kernel. Not Atkinson, not
  ordered/Bayer, not a threshold.
- **Frameless native Canvas text on pure white.** No borders, no card frame, no background
  tint. Text is drawn with the platform `Canvas`/`Paint`, not rendered from Compose.
- **FileProvider + `ACTION_SEND` with `image/png`** to an *external* Bluetooth printer app.
  This app does not talk to the printer itself and should not start doing so.
- **`minSdk 36`.** Deliberate. Modern phones only. **Do not lower it** to widen device
  support — it is not an oversight.
- **Plain-text mana costs. NO pip symbols.** `{2}{U}{U}` renders as text. No icon font, no
  image pips, no drawable substitution.

---

## 3. Hard-won decisions that must not be undone

Every item here has a body count. Keep the reason attached to the code if you refactor it.

### 3.1 `ManaCostFormatter.PIP_PATTERN` — the brace MUST stay escaped

A dangling `}` in a regex is tolerated by desktop `java.util.regex`, which silently treats it
as a literal. **Android's ICU regex engine does not** — it throws `PatternSyntaxException`.
Because the pattern is a `static`/companion constant, that throw happens in `<clinit>`, which
poisons the whole class: every later touch throws `NoClassDefFoundError` and **every single
render crashes**.

JVM unit tests cannot catch this, because on the JVM the bad pattern compiles fine. That is
precisely why there are **device-side (androidTest) `ManaCostFormatter` tests**. Do not
"consolidate" them into the JVM suite — that deletes the only thing that can detect the bug.

### 3.2 `safeManaCost` catches `Throwable`, not `Exception`

Direct consequence of 3.1. The original failure was an `Error`
(`ExceptionInInitializerError` / `NoClassDefFoundError`), which is **not** an `Exception` and
sails straight through `catch (e: Exception)`. Narrowing this catch re-opens the crash.

### 3.3 Two-slip DFC detection is structural, not layout-string based

The test is exactly:

> `card_faces.size >= 2` **AND** *every* face has its own `image_uris`

- **Not** keyed on layout strings.
- **Not** keyed on `card_faces` merely being present.

If you key on the presence of `card_faces` alone, split / flip / adventure cards
(e.g. **Fire // Ice**) print **twice with duplicate art**, because those layouts have two
faces sharing one image.

Layout strings are used **only** to choose label wording — never to decide slip count.

### 3.4 Slip label wording

| Case | Label |
|---|---|
| `transform`, front slip | `Transforms into: {back}` |
| `transform`, back slip | `Transforms from: {front}` |
| `modal_dfc` and `reversible_card` | `Other side: {other}` |
| Unknown multi-face fallback | `Other side: {other}` |
| Single slip | `null` (no label drawn) |

### 3.5 Thermal preview MUST use `FilterQuality.None`

Bilinear filtering averages neighbouring 1-bit pixels back into grey, so the preview shows a
soft greyscale image the printer can never produce. Measured: `FilterQuality.None` → **0%**
grey midtones; `FilterQuality.Low` → **71.34%** grey midtones. The preview's entire job is to
be an honest representation of printer output.

### 3.6 Auto-levels runs BEFORE Floyd-Steinberg, never after

The histogram stretch must precede the dither pass. Without it, dark art dithers to a solid
black smear: measured **3.2% white** pixels without auto-levels vs **49.5% white** with it.
Running it after the dither is meaningless — there are only two levels left to stretch.

### 3.7 Scryfall client requirements

Scryfall **will return 403** without:

- a `User-Agent` header,
- an `Accept` header,
- and roughly **100ms throttling** between requests.

All three are required. This is Scryfall's documented policy, not a guess.

### 3.8 `-is:extra` instead of explicit `-layout:` negations

Scryfall applies a default "exclude extras" filter — but **naming a layout in the query LIFTS
that default exclusion**. So `-layout:token` paradoxically drags tokens and other extras back
into the result pool. `-is:extra` is used server-side instead. Do not "clarify" this by
expanding it into layout negations.

### 3.9 androidTest dependency versions are pinned forward deliberately

Espresso **3.7.0**, junit **1.3.0**, core **1.7.0**, runner **1.7.0**.

Espresso 3.5.1 reflects on `InputManager#getInstance`, which was **removed in Android 15**.
On an API 36 device, no Compose test can run at all on the old versions. Do not roll these
back to match the rest of the dependency block.

### 3.10 `scrollContent: Boolean` on `ProxyPreview`

Prevents an **"infinity maximum height constraints"** crash caused by nesting a vertically
scrollable preview inside another vertical scroller. The flag lets the caller turn the
preview's own scrolling off. It is not redundant with the parent's scroll state.

---

## 4. Approved UX decisions

- **Re-roll lives in the `TopAppBar`.** Explicitly requested there so it cannot be hit by
  accident. Do not move it next to PRINT or into a FAB.
- **PRINT is the large bottom button.** Primary action, bottom of screen, full prominence.
- **The preview must be LARGE** — fill the available width via
  `fillMaxWidth().aspectRatio(...)` with `ContentScale.FillWidth`. Do not shrink it into a
  thumbnail or card.
- **DFC uses a `HorizontalPager` with a page indicator**, one page per slip.
- **Two-slip cards dispatch as TWO SEQUENTIAL `ACTION_SEND` calls.** Never
  `ACTION_SEND_MULTIPLE` — the cheap BT printer apps this targets mishandle it (drop the
  second image or print garbage).

---

## 5. Known open bugs

### Card art missing from slips

Slips render with text but no art. `downloadBitmap` was returning `null`.

**Prime suspect:** Coil handing back a **hardware-config** bitmap. A `Bitmap.Config.HARDWARE`
bitmap has no CPU-readable pixel buffer, and Floyd-Steinberg is nothing but a pixel-by-pixel
read — so the dither pass gets nothing. The fix is `allowHardware(false)`.

**Status:** `ArtDownloader.kt` (one of the four uncommitted-feature files) appears to be
exactly this fix mid-flight — it sets `allowHardware(false)` on both the `ImageLoader` and the
`ImageRequest`, forces `ARGB_8888`, disables RGB565, and requests `Size.ORIGINAL`. It is
constructed in `ProxyGeneratorViewModel.kt:133`. **It has never been verified end-to-end on a
device.** Start here: run the app, roll a card, and check whether art now appears. If it still
fails, `ArtDownloader` now logs a concrete failure reason instead of returning a silent null.

---

## 6. Remaining work

### Sharing / dispatch
- Finish the sequential two-slip send.
- Remembered `ComponentName` via `PrinterTargetStore` + `ChosenComponentReceiver`, plus a
  **reset affordance** so the user can re-pick the target app.
- Fallback path when the remembered printer app has been uninstalled.

### Image controls
- Contrast / brightness sliders that **re-dither from the cached art bitmap** — must not
  refetch from Scryfall on every slider tick.
- Thermal-vs-full-res preview toggle.

### History
- ~20-entry session history with reprint.

### Housekeeping (dependency + code hygiene)
- **Strip unused deps:** CameraX (×4), Room (×3), `play-services-location`, Accompanist,
  `material-views`.
- **KEEP `datastore`** — `PrinterTargetStore` depends on it.
- **Pin versions:** `1.3.+`, `2.11.+`, `1.4.+`.
- Gate `HttpLoggingInterceptor` behind `BuildConfig.DEBUG`.
- Remove `Context` from the ViewModel.
- Drop the redundant Moshi `KotlinJsonAdapterFactory` (codegen is already in use).
- Clean unused imports.

---

## 7. Reference

**github.com/MoritzHayden/momir-basic-printer** — a Raspberry Pi equivalent of this app.
Worth reading for ideas, not for architecture.

What it does:
- Prints name + mana cost **justified on ONE line** (name left-aligned, cost right-aligned).
- Same physical target: **384 dots / 203dpi / 57mm**.
- Dithers to mono.
- Uses **Scryfall bulk data** rather than per-card `cards/random` calls — a meaningfully
  different approach that avoids rate limiting entirely.
- **No DFC handling at all** — this app is ahead of it there.

Ideas worth stealing later: the one-line justified name+cost header, and the bulk-data
strategy if per-card throttling ever becomes painful.
