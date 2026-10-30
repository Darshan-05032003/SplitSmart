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
