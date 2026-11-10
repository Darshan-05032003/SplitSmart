# Decisions

Decisions taken during the build, with the reasoning, so nothing is a surprise later.

## Toolchain (Phase 0)
- **AGP 9.5.0** (latest stable on maven.google.com at scaffold time) with AGP's **built-in Kotlin** support, so the `org.jetbrains.kotlin.android` plugin is not applied.
- **Kotlin 2.4.21**, **KSP 2.3.12**, **Compose BOM 2026.09.00**, **Hilt 2.60.1** (KSP, not kapt).
- **Gradle 9.6.0** wrapper instead of the newest 9.8.2: 9.6.0 was already cached on the build machine and the disk had less than 7 GB free; every new Gradle version adds ~2 GB of per-version caches. 9.6.0 is fully compatible with AGP 9.5.0 (verified by building). Upgrading later is a one-line change in `gradle/wrapper/gradle-wrapper.properties`.
- **JDK toolchain 17** (`jvmToolchain(17)`), built on a JDK 21 host. JitPack is configured for JDK 17.
- **compileSdk / targetSdk 37** (latest stable platform installed), **minSdk 26**.
- Robolectric tests pin `@Config(sdk = [35])` because Robolectric 4.17 doesn't ship an SDK 37 runtime yet.

## Feature 1: groups, members, bills
- **Table names:** `split_groups` instead of `groups` because `GROUPS` is an SQLite keyword (window functions); `group_members`, `bills`, `bill_items`.
- **`@Relation` ordering:** Room cannot order a relation, so `BillWithItems` exposes `rawItems` (Room) and `items` (sorted by `sort_order`).
- **Payer deletion = SET NULL:** removing a member must never delete the bills they paid; the UI shows "Paid by nobody yet".
- **Dialog drafts in SavedStateHandle:** an open Add-member / Add-bill dialog survives rotation and process death; validation errors are transient on purpose.
- **Money cap:** `Money.MAX_PAISE` = ₹1 crore. A first draft had the literal 100× too large; the unit test caught it (see SECURITY_REVIEW).
- **Build script:** `LLM_API_KEY` read from `local.properties` (or the environment), quotes and whitespace stripped, into `BuildConfig.LLM_API_KEY` (empty when absent). Firebase plugins are applied only when `app/google-services.json` exists so a clean clone builds. `stressTest` and `realApiTest` are filtered `Test` tasks excluded from `testDebugUnitTest`.
- **SSL pins (prepared for feature 3):** `generativelanguage.googleapis.com` serves two chains depending on the negotiated signature algorithm: ECDSA (leaf → `WE2` → `GTS Root R4`) and RSA (leaf → `WR2` → `GTS Root R1`). Leaf certs rotate every few weeks, so the app pins the intermediates (primary) and the roots (backup) of both chains; roots were cross-checked against `https://pki.goog/repo/certs/`.

## Feature 2: receipt scanning
- **Draft table instead of nav args:** OCR text (up to 20k chars) is saved to Room (`receipt_drafts`, DB v2 via `AutoMigration(1, 2)`) and only the draft id travels through navigation / `SavedStateHandle`. Survives process death and keeps Bundles small.
- **Feature 2 ends at the saved draft:** "Continue" pops back to the group until feature 3 adds the editable review screen.
- **Camera through `LifecycleCameraController`:** `CameraXReceiptCamera` is `@ViewModelScoped`, created lazily on first preview, so constructing the ViewModel (e.g. in tests) never touches hardware. Preview is a `@Composable` parameter so Robolectric tests inject a plain `Box`.
- **Permission launchers live in the production `ScanRoute` overload** and are passed as lambdas to the testable overload.
- **Migration test without `MigrationTestHelper`:** Robolectric could not see the exported schema through the test source set's assets, so the test builds the v1 database from `app/schemas/.../1.json` (`createSql` + `setupQueries`) and lets Room run the real AutoMigration on open. Same production code path, no asset plumbing.
- **ML Kit model:** `com.google.mlkit:text-recognition` bundles the Latin model in the APK (fully offline, ~4 MB) instead of the Play-services-downloaded variant, so the first scan works without a network.

## Feature 3: LLM extraction
- **Model:** `gemini-2.5-flash` via `v1beta/models/{model}:generateContent`, `temperature 0`, `responseMimeType: application/json` and a `responseSchema` for `{items:[{name,qty,unitPaise,totalPaise}],taxPaise,tipPaise,totalPaise}`. Integers in paise avoid floating point in the model output; a decimal value is still accepted and read as rupees.
- **Key in a header** (`x-goog-api-key`), never as `?key=` in the URL, so URL logging / proxies never see it.
- **Pins:** intermediates `WE2` + `WR2` as primary, roots `GTS Root R4` + `GTS Root R1` as backup (both chains Google serves). Leaf pins were rejected: they rotate every few weeks. Pinner is injected (`LlmHttpClient.create(pinner)`) so MockWebServer tests run unpinned; `PinningTest` proves rejection of a wrong pin on a real TLS handshake with `okhttp-tls`.
- **Retries only for transport failures** (429 with `Retry-After`, 5xx, timeout, IO) with `BackoffPolicy.LLM` (0.5 s → 8 s, 4 attempts, full jitter). 4xx and malformed bodies are terminal → regex fallback.
- **JsonRepair is a single string-aware pass** (fences, prose, smart quotes, comments, NaN/Infinity, trailing commas, truncation) followed by a real parse; it never edits string contents.
- **Review form lives in SavedStateHandle as JSON** (`ReviewForm` is `@Serializable`), so edits survive process death and the LLM is not called twice for one draft (test `process death restores the edited form without calling the LLM again`).
- **Review form is a scrollable `Column`, not a `LazyColumn`:** ≤ 200 rows of text fields; LazyColumn recycling breaks text-field focus and leaves off-screen rows uncomposed (which also made the Compose tests blind to them).
- **Model id:** the spec named `gemini-2.5-flash`, but on 2026-10-30 `generateContent` on it returned HTTP 404 "This model is no longer available to new users. Please update your code to use models/gemini-3.8-flash". The default is therefore `gemini-3.8-flash` (`GeminiExtractor.DEFAULT_MODEL`, one constant to change). `gemini-flash-latest` was verified to exist as an alias and is the alternative.
- **Real Gemini call (`./gradlew realApiTest`, `realapi` package, excluded from the normal suite):** 4 HTTP attempts in total: two 404s (model gating, no generation), one read timeout at 45 s, then **success: HTTP 200, latency 147,222 ms, 2 items, total 21,000 paise, 0 validator warnings**, through the production pinned client (so the pin set was validated against the live host). The key never appears in any log or document.
- **Timeouts raised to 60 s read / 90 s call** (from 30/45) after that measurement. A slower answer falls back to the on-device parser and the review screen offers "Try AI again". Follow-up (not done, no more real calls allowed): `generationConfig.thinkingConfig` to cut model thinking time, and showing the regex result immediately while the AI answer streams in.

## Feature 4: split calculator
- **Largest-remainder (Hamilton) apportionment** for every monetary component instead of "round and give the remainder to the first person": every member is within one paisa of the ideal share and the parts sum exactly. BigInteger for the products so ₹1 crore × 10^9 weights cannot overflow.
- **Four components per member** (`itemsPaise`, `taxPaise`, `tipPaise`, `adjustmentPaise`). Tax and tip are apportioned proportionally to each member's item share (by-item) or by the same weights (equal / by-share). `adjustmentPaise` holds the difference between the printed total and items + tax + tip (discounts, round-off) plus paisa rounding, so the bill's total is what people actually pay.
- **Unassigned item = shared by everyone**; assignees who are not participants are ignored (a removed member cannot make the split fail).
- **Debt simplification is greedy** (largest debtor pays largest creditor, priority queues, ties by name): deterministic, at most n − 1 transfers, O(n log n). The NP-hard minimal-transfer problem is not attempted; the greedy result is what every popular splitting app shows.
- **Storage:** DB v3 adds `bills.split_config` (JSON `SplitConfig`) and the `bill_shares` table (PK bill+member, cascades on both). Shares are denormalised so group balances are a simple read; the config is kept so the split can be edited. Inconsistent rows (shares no longer summing to the total) are read as "not split".
- **Balances need a payer:** bills without a recorded payer are counted as "not settled yet" and shown as such rather than guessed.

## Feature 5: UPI settle-up
- **Link shape:** `upi://pay?pa=&pn=&am=&cu=INR&tn=` (+ optional `tr`), amount as rupees with exactly two decimals from paise, values RFC 3986 percent-encoded (`%20`, never `+`, which some UPI apps mishandle). Built and parsed by one pure object (`UpiLinkBuilder`) so the parser doubles as the hostile-input gate for anything we launch.
- **Payee note:** `"<group name> via SplitSmart"` as `tn`; it is a payment memo shown in the payer's UPI app, kept as a constant in the ViewModel rather than a localisable string.
- **No automatic "paid" state:** without a PSP status API a UPI app's return cannot be trusted (see PayFlow's design); settlement stays a visible debt until bills change. Logged as scope for improvement.
- **QR codes with zxing core** rendered from a platform-free `QrMatrix` (tests decode it with zxing's reader on the JVM); the Android `Bitmap` is only created in the Compose dialog.
- **Launcher through an interface** (`UpiAppLauncher`) with `queryIntentActivities` for "no UPI app" detection and a chooser for every launch; Robolectric tests register a fake UPI activity.
- **Inline UPI-ID entry** on the settle-up screen (members can be created without a VPA) reuses `GroupRepository.updateMember`.

## Feature 6: Firebase Crashlytics + Analytics
- **Interfaces in `domain/analytics`** (`AnalyticsTracker`, `CrashReporter`, typed `AnalyticsEvent`), Firebase adapters in `data/analytics`, bindings in `di/AnalyticsModule`. Real implementations only when `BuildConfig.FIREBASE_CONFIGURED` (i.e. `app/google-services.json` existed at build time), otherwise no-ops; Hilt tests replace the module with recording fakes.
- **Typed events, not free-form `logEvent(name, bundle)` calls:** `scan_success`, `scan_failure`, `llm_parse_success`, `llm_parse_failure` (with `fallback_used`), `split_created`, `settle_up_tap`. Parameters are enums, counts and booleans; `AnalyticsPolicy` rejects anything PII-shaped at the sink.
- **Non-fatal policy:** record a non-fatal (with custom keys `scan_error`, `llm_failure`, `text_length`, `group_size`, `item_count`) only for defects — recogniser/database exceptions, LLM transport failures, unrepairable model output. Expected outcomes (no text in the photo, no API key configured, invalid split config) are metrics only.
- **Collection enabled in debug** so the Firebase console can be checked with a debug install; flip `setAnalyticsCollectionEnabled`/`setCrashlyticsCollectionEnabled` on `BuildConfig.DEBUG` before a store release.
- **Crashlytics Gradle plugin** is applied with the google-services plugin; release builds upload mapping files (needs network at release build time).

## Feature 7: performance
- **Two `com.android.test` modules:** `:baselineprofile` (AndroidX Baseline Profile Gradle plugin 1.5.0, `BaselineProfileRule`, uiautomator journey: start → create group → open → scan rationale) and `:macrobenchmark` (`MacrobenchmarkRule`: cold startup in None / Partial(baseline) / Full compilation modes, frame timing while flinging the group screen). Both target the app's non-debuggable, debug-signed `benchmark` build type (R8 on) so numbers reflect release code.
- **`profileinstaller`** in the app so a committed profile is installed on devices without Play; `baselineProfile(project(":baselineprofile"))` wires generation into `:app:generateBaselineProfile`.
- **LeakCanary 2.14** as `debugImplementation` only; **StrictMode** installed in `SplitSmartApp.onCreate` before `super` when `BuildConfig.DEBUG`, `penaltyLog` (never death) so testers are not crashed.
- **ANR safety is tested, not assumed:** `MainThreadGuardTest` records the thread each heavy path runs on; `OffMainStressTest` does it for 1,000 concurrent extractions.
- **Compile-only verification of the device modules:** `./gradlew :baselineprofile:assemble :macrobenchmark:assemble` builds both test APKs and the app's `benchmark` variant (exercising the R8 keep rules).

## Compromises
- **No device or emulator:** CameraX capture and ML Kit recognition are implemented and compile/lint clean but were not executed on hardware; the Baseline Profile was not generated and the macrobenchmarks were not run (commands in README: `./gradlew :app:generateBaselineProfile`, `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`). The scan flow is proven on the JVM with `FakeReceiptCamera` + `FakeTextRecognizer`. Manual check: `./gradlew installDebug`, open a group → Scan receipt.

## Polish: fast Gemini model, provisional draft, AI error states (2026-10-30)
- **Model switch to `gemini-3.1-flash-lite`** with a fallback list `gemini-3.1-flash-lite` → `gemini-flash-lite-latest` → `gemini-3.5-flash-lite` (`GeminiExtractor.MODELS`, one constant). Candidates came from `GET v1beta/models` for this key (generateContent support). Each was measured once with the same 7-item restaurant receipt (508 prompt tokens) and the production request shape:

  | Model | thinking | HTTP | Latency | Result |
  |---|---|---|---|---|
  | `gemini-3.1-flash-lite` | `thinkingLevel: minimal` | 200 | **3,050 ms** | 7 items, tax ₹191.00, total ₹2,101.00, exact |
  | `gemini-flash-lite-latest` | minimal | 200 | 3,509 ms | exact |
  | `gemini-3.5-flash-lite` | minimal | 200 | 4,005 ms | exact |
  | `gemini-3.8-flash` | minimal | 400 | 446 ms | "Thinking level MINIMAL is not supported for this model" |
  | `gemini-3.8-flash` | low | — | > 120,000 ms | client timeout (was 147 s at default thinking) |
  | `gemini-2.5-flash` | — | 404 | — | not available to new keys (recorded earlier) |

  The Flash-Lite models are ~40x faster than `gemini-3.8-flash` and were exact on this receipt; the fallback order is by measured latency.
- **Latency levers:** `thinkingConfig.thinkingLevel = "minimal"` (the Gemini 3 replacement for `thinkingBudget: 0`; the lowest level the Flash-Lite models accept), `temperature 0`, `responseMimeType: application/json` + `responseSchema`, `maxOutputTokens 8192`, and a compact prompt (7 rules instead of 9, same semantics, still declaring the `<receipt>` block as data).
- **404 → next model once, then cache:** a 404 from one model moves to the next model in the list (each tried at most once per extraction, no backoff because a 404 is not transient). The model that answered is stored in DataStore (`splitsmart_settings`, key `llm_model`, `DataStoreModelCache`) and tried first next time; a cached id no longer in the list is ignored. Other 4xx (400/401/403) never switch models.
- **Timeouts back to 10 s connect / 30 s read / 15 s write / 45 s call** (`LlmHttpClient.*_TIMEOUT_S`): ~10x the measured 3-4 s. Retries (429/5xx/timeout/IO) keep `BackoffPolicy.LLM`.
- **Provisional draft:** if the AI has not answered within 400 ms (`ReviewViewModel.PROVISIONAL_DELAY_MS`), the on-device parser's result is shown as an editable draft with an "AI is refining…" banner. The AI result replaces it when it arrives; if the user already edited the draft, the AI result is offered ("Use AI result" / "Keep mine") instead of overwriting their edits. Saving the draft cancels the AI call. A fast answer goes straight from the spinner to the AI form (no flicker).
- **Error mapping:** new `LlmFailure.OFFLINE` (`UnknownHostException`, not retried, no non-fatal) and `LlmFailure.INSECURE` (`SSLException` incl. `SSLPeerUnverifiedException` from a pin mismatch: never retried, recorded as a non-fatal; before this a pin failure was an `IOException` → "network" and was retried 4 times). 429 carries `Retry-After` to the screen ("Try again in N s"). 401/403 and a missing key explain how to set `LLM_API_KEY`.
- **OCR rows re-assembled (`OcrLayout`)**, found on the emulator: ML Kit returned the item-name column and the amount column of a real receipt photo as separate blocks, and sorting lines by `top` put "320.00 320.00" above "Paneer Tikka" and swapped later rows. Lines whose deskewed vertical centres are within half the median line height are now joined left to right into one row ("Paneer Tikka 320.00 320.00"); centres are rotated by the median ML Kit line angle first so a tilted photo does not merge neighbouring rows. Regression test `OcrLayoutTest` uses the boxes from that run.
- **"You" for balances:** groups only know member names, so the home screen asks once for "your name" (DataStore key `your_name`) and matches it case-insensitively against each group's members to show "you owe" / "you are owed". Without it, the home header shows the total still to settle.
- **Brand and design system:** coral primary (`#B5401F` light / `#FFB59F` dark), indigo secondary reserved for AI-made things (badges, "AI is refining…", the review source chip), saffron tertiary for warnings, and a separate money pair (green = owed to you, red = you owe) so balances never depend on the brand colour. Full hand-tuned light and dark Material 3 schemes, no dynamic colour. Coral → saffron gradient `HeroCard` for headline numbers; shapes 8/12/18/24/32 dp; 16 dp screen padding. Shared components in `ui/components/Brand.kt` (avatars, avatar stack, emoji group tile, AI badge, balance chip, info banner, empty-state illustration). Icons beyond `material-icons-core` are vector drawables (receipt, camera, gallery, QR, sparkle, group, split, wallet) instead of the multi-MB extended icon set.
- **Adaptive launcher icon** (receipt splitting into two arrows on coral, with a monochrome layer for themed icons). It stays in `mipmap-anydpi-v26` although minSdk is 26: aapt2 failed to link `@mipmap/ic_launcher` from an unqualified `mipmap-anydpi` folder, so lint's `ObsoleteSdkInt` warning is accepted.
- **Add bill = Scan / From gallery / Enter manually** on the group screen; "From gallery" navigates to the scan route with `gallery = true`, which opens the photo picker once (saved across rotation). The home "Scan receipt" goes straight to the only group, asks which group when there are several, and opens "New group" when there is none.
- **Compose tests and the taller layouts:** the group screen's bills and balances moved below the summary and add-bill cards, so two integration tests now scroll the lazy list to them (`scrollListTo`) instead of expecting them composed on first frame; assertions unchanged. Visible text that tests match exactly (`"Ravi owes Asha"`, `"₹150.00"`) stays unique per screen: summary figures use combined strings ("₹1,118.70 to settle in 2 payments").

## UI overhaul: library decisions (2026-11-09)
| Library | Latest stable checked 2026-11-09 | Decision |
|---|---|---|
| Compose BOM | 2026.09.00 (Compose 1.12.1, Material3 1.4.0) | **Keep.** Already on the latest. |
| Material 3 Expressive | Expressive APIs are still graduating inside `material3` 1.5.0-rc01 (Nov 2026), not in stable 1.4.0 | **Do not adopt.** Both apps use a custom design system on top of plain Material 3 slots, so nothing depends on it. Revisit when 1.5.0 is stable. |
| `SharedTransitionLayout`, shared elements | Compose Animation 1.12.1 | **Adopt** for container transforms. |
| Predictive back | Navigation Compose 2.10.2 | **Adopt** with `android:enableOnBackInvokedCallback`. |
| `core-splashscreen` | 1.2.0 | **Adopt** for the icon → home hand-off. |
| Lottie Compose | 6.7.1 | **Do not adopt by default.** Every animation is code-drawn (Canvas/AGSL), which keeps the APK small and gives full control. Only added if you supply Lottie files. |
| Haze | 2.1.0 (Compose 1.12) | **Adopt only where glass adds depth** (bottom bar, sheets), API 31+, with a dim scrim below. |
| Roborazzi | 1.78.0 (`roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`, `roborazzi-gradle-plugin`) | **Adopt** for screenshot tests. It runs on the existing Robolectric 4.17 setup. Fallback: Paparazzi 1.3.5. |
| Macrobenchmark, Baseline Profile plugin | 1.5.0 | **Adopt** (SplitSmart already has both modules; PayFlow gets them in Wave 2). |
| CRED `neopop` | Apache-2.0, View/XML based, repo last active July 2024, no `club.cred:neopop` artifact on Maven Central | **Reject.** Not Compose, unmaintained, and the brief rules out a NeoPOP look. |

- **Baseline before the overhaul:** `testDebugUnitTest` 284 tests, 17 stress tests, `assembleDebug` and `lintDebug` with no errors, all green at `578ae11`.
- **Order and worktrees:** this machine has 15 GB of RAM, and only one Gradle build runs safely at a time. PayFlow is done first, then SplitSmart. Feature work is integrated one at a time.

## UI overhaul: chosen direction (2026-11-09)
- **Marigold Mela** (see `DESIGN_DIRECTION.md`), no tweaks. No Lottie: all motion is code-drawn.
- Features are built one at a time on `main`, because this machine runs one Gradle build safely at a time; no parallel worktrees are needed.
- Performance numbers come from the Pixel 7 emulator with software rendering and are labelled as such. They show before/after differences, not what a real mid-range phone would measure.

## UI overhaul row 8: design system (2026-11-10)
- **Fonts:** Bricolage Grotesque and Figtree variable fonts from `google/fonts`, subset to Latin, Latin-1, Latin Extended-A, punctuation, arrows, ×, ₹ and − (same range as PayFlow), Bricolage's width axis pinned to 100. 408 KB + 63 KB → 206 KB + 52 KB. Weight and optical size stay variable and are set through `FontVariation`: display styles at optical size 48, amounts at 20. No Reserved Font Name is declared, so the subset keeps its names. Details in `CREDITS.md`.
- **Theme entry:** `SplitSmartTheme` provides the Marigold tokens, the motion level and haptics at the app root now (`MainActivity` feeds it the stored `UiPreferences`). The Material slots stay on the pre-overhaul coral look (`MaterialLook.Legacy`) so screens not rebuilt yet look exactly as before; their old colours moved from `SplitSmartTheme.colors` to `SplitSmartTheme.legacy` (a rename, no visual change). Row 9 switches the root to `MaterialLook.Marigold`; rows 9-13 remove `legacy`.
- **Bottom bar on aubergine in both themes:** marigold on white is only 1.9:1, too weak for the only sign of the selected tab. On aubergine it is 9.6:1.
- **Depth without blur:** a solid offset base under buttons, chips and cards that closes as the element is pressed. Rounded radii and a tinted (not black) base keep it away from the hard-edged NeoPOP look.
- **Squash and stretch from one spring:** press = Snappy spring to 1, release = Bouncy spring to 0; its overshoot below 0 is the stretch, amplified 2.5× so it reads. Wide elements grow at most 8dp in total.
- **Avatar stacks show four faces and "+N"**, except when only one would hide (five members show five faces, not "+1"). The first name is drawn on top; "+N" above all.
- **Undo snackbar timing on the frame clock** (`withFrameMillis`), so tests control it and it pauses with the app; with TalkBack on it uses the system's recommended timeout.
- **Token audit with a ratchet:** the ten pre-overhaul screen files keep their current raw-value counts in `scripts/token_audit_legacy.txt`; a count may only go down, new files must be clean.
- **Compromise:** a `heightIn` bottom bar (to grow past 2× font) filled the screen because its tabs fill the height; kept the fixed 68dp height, which fits 1.3×.

## UI overhaul row 9: shell, splash and intro (2026-11-10)
- **Marigold everywhere now.** The app root no longer has a "legacy" Material look: every Material slot carries Marigold, so the six screens not rebuilt yet (rows 10-13) already match the palette. Material draws `primary` as text (text buttons, focused field labels, cursors) and marigold on cream is only 1.66:1, so `primary` is the accent (aubergine in light, marigold in dark, the design system's text-action colour) and marigold moves to `primaryContainer` (FABs, tonal surfaces). The old hero card and money colours are derived from Marigold: aubergine hero, cream text. `MaterialSlotsContrastTest` checks every Material text pair and every old hero pair in both themes; the old coral palette and system typography are gone. The scan camera's brackets and capture button now read Marigold tokens directly, because the camera is dark in both themes.
- **Bar items: Groups, Scan, Me.** Groups is home. Scan is the hero flow (the AI receipt reading every other screen leads to), so it sits in the middle of the bar as an action, not a tab: it opens the camera for the only group, asks which group when there are several (a sheet), and explains that a group comes first when there is none (its button opens New group on the groups list). Me holds what had no home: your name, haptics, reduced motion, theme, and plain facts (AI reading on or off in this build, what UPI settle-up does) plus "Show the intro again". An Activity or History tab was rejected for now: there is no cross-group history screen yet (row 13 builds it), and an empty tab is worse than none. Row 10's FAB menu may also offer Scan; both go through `ScanTarget`.
- **One motion vocabulary for navigation** (`ShellLogic.transition`, symmetric, tested for every pair): tab to tab fades through; forward and back in a flow is a shared-axis slide; the camera rises over the app and sinks back; a group card grows into its group screen (container transform with `sharedBounds`, its corners easing from the card's 24dp). Reduced motion turns all of them into short fades and drops the container transform. Predictive back is on (`enableOnBackInvokedCallback`), so the back gesture scrubs these pop animations; the intro also lets the previous page peek in while the gesture is held.
- **Status-bar icons follow the surface, and change hands only when the new surface has arrived.** Each screen declares the colour under the status bar; the newest one whose enter transition has finished owns it, and with nothing declared the app theme decides (it can differ from the system's). This is PayFlow's device bug (icons flipped during the lock reveal) solved as a rule, tested on the JVM with a paused clock.
- **The groups list keeps its own Scaffold until row 10.** It is padded to end above the floating bar instead of drawing under it. Row 10 removes that padding and uses `LocalBottomBarInset`.
- **Splash mark and launcher icon.** An original torn-receipt mark: an aubergine receipt torn into two shares that lean apart, on marigold. The splash plays it (pop, then tear, 460 ms) and leaves with a squash and pop; the launcher icon, its themed-icon layer and the window background moved from coral to Marigold so nothing coral flashes before the first frame. The splash is marigold in both themes because the stored app theme can differ from the system's.
- **The intro is shown once and never blocks.** Completion lives in DataStore (`onboarding_done`); only a real `true` counts (a value of another type, written by someone editing the file, shows the intro again rather than crashing), and an unreadable store counts as done. The first stored answer decides; the splash waits for it for at most 2 s, after which the app opens. Replaying it from Me does not clear the stored flag. Existing users who update see it once, which is acceptable for a skippable intro and cheaper than a database read on every cold start.
- **Benchmarks:** `SeedDataReceiver` marks the intro done before writing any data (its code is compiled into the debug unit tests, so every gate checks it); the Baseline Profile journey taps Skip on a fresh install.
- **Launch intent hardening.** `MainActivity` is exported, and Navigation reads the launch intent's own explicit deep-link extras (destination ids and arguments). SplitSmart has no deep links, so the launch intent is cut down to a plain launcher intent before Navigation sees it.
- **Compromise (tests):** app-level Hilt tests stay on Robolectric's default 320×470dp screen. On taller Robolectric screens two not-yet-rebuilt Material screens loop in layout (typing in the New group dialog, opening the group screen) until the test JVM runs out of heap; the same happens with the old navigation host, so it is not the shell. On the small screen the newest group card sits just below the fold under the floating bar, so those tests scroll to it; the benchmark rule (newest card on the first screen of a phone, named) is checked at 411×891dp by `GroupsUnderBarTest`. Row 10 rebuilds both screens and should re-check this.
