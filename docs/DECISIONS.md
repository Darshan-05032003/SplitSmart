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

## Compromises
- **No device or emulator:** CameraX capture and ML Kit recognition are implemented and compile/lint clean but were not executed on hardware. The scan flow is proven on the JVM with `FakeReceiptCamera` + `FakeTextRecognizer`. Manual check: `./gradlew installDebug`, open a group → Scan receipt.
