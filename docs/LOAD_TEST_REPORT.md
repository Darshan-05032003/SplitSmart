# Load / stress report

Stress tests live in `app/src/test/kotlin/com/darshan/splitsmart/stress/` and run only via `./gradlew stressTest`
(excluded from `testDebugUnitTest`). Every run prints `STRESS [...]` lines; the numbers below are copied from real runs
on the build machine (Robolectric SQLite on the JVM, JDK 17 toolchain, Gradle `-Xmx2g`, test JVM `-Xmx1g`).

## Feature: Groups, members, bill history (Room)
- Scenario and scale:
  - `RoomStressTest.100k bills in room`: 100 groups × 1,000 bills = 100,000 bills + 100,000 items inserted in chunks of 2,000 inside transactions; then the history query for one group (1,000 bills with their items via `@Relation`), a paged query (`LIMIT 50 OFFSET 500`), the cross-group "recent 50" query and the 100-group summary join.
  - `RoomStressTest.1000 concurrent member and group writes`: 1,000 coroutines on `Dispatchers.IO`, each adding a member and saving a bill with one item through the real repositories.
- Results (numbers):
  - Insert 200k rows: **1,380 ms (≈72,000 rows/s)**, heap 77 → 80 MB.
  - History for one group (1,000 bills + items): **73 ms**. Paged (50): **5 ms**. Recent 50 across 100k: **3 ms**. Summaries (100 groups, 2 correlated counts each): **7 ms**.
  - 1,000 concurrent members + 1,000 bills: **734 ms** total, heap 100 MB, all 2,000 rows present.
- What broke:
  - Nothing at this scale. Without the `(group_id, created_at)` and `created_at` indices the recent/history queries would be table scans; they are part of the schema from v1.
  - Unpaged history is O(n) per group; a group with 100k bills would hold ~100k objects in memory (an unrealistic number of dinners, but see recommendation).
- Failure flow (step by step):
  1. User opens a group with tens of thousands of bills.
  2. `BillDao.observeByGroup` loads every bill and runs the `@Relation` item query in 999-id batches.
  3. The Flow emits one huge list on every change; Compose diffing keys 100k cards.
  4. UI jank, not a crash: memory stays bounded by Room's cursor window per batch.
- Fix applied / recommendation:
  - `BillDao.getPage(groupId, limit, offset)` exists and is covered by the stress test for a future Paging 3 adoption.
  - Bulk insert path (`insertBillsAndItems`) stays transactional so a mid-import crash leaves no half-written bill.

## Feature: Receipt scanning (OCR normalizer, image store)
- Scenario and scale:
  - `OcrStressTest.10000 synthetic OCR receipts`: 10,000 deterministic receipts (2–15 items, ₹/Rs markers, O-for-0 noise, GST lines, noise lines; 3.0 M chars in total) through `OcrTextNormalizer`.
  - `OcrStressTest.pathological inputs`: 50k `O`s, 20k `1,` pairs, 20k ` .` pairs, 50k zero-width spaces, 30k `₹`, a 5,000-line receipt.
  - `OcrStressTest.1000 concurrent image imports`: 1,000 coroutines importing into the image store at once.
  - Room scenarios re-run with the v2 schema (`receipt_drafts` added).
- Results (numbers):
  - 10,000 receipts normalised in **897 ms (89 µs / receipt)**, 3,036 k chars → 2,667 k chars, heap 57 → 54 MB (no growth).
  - 6 adversarial inputs: **214 ms** total, every output ≤ 20,000 chars.
  - 1,000 concurrent imports: **59 ms**, 1,000 files present, none lost.
  - Room v2: 200k rows in 1,390 ms (≈72k rows/s); 1k-bill history 86 ms; page 4 ms; recent 3 ms; 1,000 concurrent members + bills 629 ms.
- What broke:
  - Nothing failed. The first normaliser draft ran the regexes over the whole text at once; on the 5,000-line receipt the look-behind patterns were measurably slower, so normalisation is now per line (bounded work per regex call).
- Failure flow (step by step):
  1. A receipt photo of a long itemised bill (or a hostile image producing 100k+ chars of OCR text).
  2. `OcrTextNormalizer` processes at most `2 × MAX_CHARS` input characters and emits ≤ 20k, so the LLM prompt and the regex parser get a bounded payload.
  3. Beyond that the text is truncated: the last lines (often totals) can be lost. The review screen lets the user fix totals by hand.
- Fix applied / recommendation:
  - Per-line normalisation and an input cap (applied). Recommendation: when truncation happens, keep the first and last N lines (headers + totals) instead of a plain prefix; tracked as scope for improvement for feature 3.

## Feature: LLM extraction (Gemini client, JSON repair, regex parser)
- Scenario and scale:
  - `LlmStressTest`: 1,000 concurrent `GeminiExtractor.extract` calls (real OkHttp, 128 connections) against a MockWebServer whose dispatcher answers 429 to 60 % of the first 1,000 requests and 200 afterwards; backoff 200 ms → 2 s, full jitter, 4 attempts.
  - `ParserStressTest.10000 synthetic OCR receipts`: `OcrTextNormalizer` + `RegexReceiptParser` + `ReceiptValidator` on 10,000 generated receipts (2–15 items, GST lines, OCR noise).
  - `ParserStressTest.10000 malformed json blobs`: 2,500 random-character blobs, 2,500 truncated JSON, 2,500 fenced JSON with extra commas, 2,500 well-formed, through `JsonRepair` + `ReceiptJsonParser`.
  - Earlier scenarios re-run unchanged.
- Results (numbers):
  - LLM storm: **1,600 requests (1,000 + 600 retries) completed in 793 ms, 0 failures**; retry delays spread over 0..200 ms with **at most 74 of 600 retries in any 20 ms slot** (uniform jitter would give ~60), heap 16 → 138 MB.
  - Regex parser: **10,000 receipts in 1,151 ms (115 µs each)**, 8.0 items/receipt, total found on 10,000/10,000, 10,000 validated; heap flat.
  - JSON repair: **10,000 blobs in 275 ms**, 3,944 repaired → 3,944 parsed (every repaired blob parsed; the well-formed and fenced quarters recovered, random garbage rejected cleanly; no unexpected exception).
  - Real Gemini call (manual, once): 147 s latency on `gemini-3.8-flash` — see DECISIONS.
- What broke:
  - The first stress run with OkHttp's default dispatcher (5 requests per host) serialised the 1,000 calls; the client now accepts an injected `Dispatcher` (production keeps the default: one receipt at a time).
  - The real model latency (147 s) exceeded the original 45 s call timeout: in production every real scan would have fallen back to the regex parser. Timeouts raised to 60/90 s; see recommendation.
- Failure flow (step by step):
  1. 1,000 users scan at once → 600 get 429 from the API.
  2. Each throttled client sleeps a random `[0, 200 ms]` (then `[0, 400]`, `[0, 800]`…), so retries arrive spread out instead of as a second spike.
  3. After 4 attempts the call fails with `RateLimited` → the regex parser produces the review form and the banner offers "Try AI again".
  4. A slow model answer hits the 60 s read timeout → same fallback; no thread is blocked (suspending IO on `Dispatchers.IO`).
- Fix applied / recommendation:
  - Full-jitter backoff + Retry-After cap (applied). Injected dispatcher for stress (applied).
  - Recommendation: show the on-device result immediately and upgrade it when the AI answer arrives; add `thinkingConfig` to shorten model latency once a further real call is permitted.

## Feature: Split calculator (apportionment, debt netting, Room v3 shares)
- Scenario and scale:
  - `SplitStressTest.1000 people x 10000 items`: 10,000 items with 1–5 random assignees each, 1,000 participants, 5 % tax, a tip and a 7-paisa round-off, split by item, then equally and by random shares.
  - `SplitStressTest.10000 random splits`: 10,000 bills (1–24 people, 0–29 items, random tax/tip and a printed total off by up to ₹3) across all three modes.
  - `SplitStressTest.debt simplification`: 1,000 equal-split bills over 1,000 members netted and simplified.
  - Room scenarios re-run with the v3 schema (each bill now also loads its `bill_shares` relation).
- Results (numbers):
  - By-item split of **10,000 items over 1,000 members: 20 ms**, total 5,216,952.40 ₹ exact to the paisa, heap 110 → 127 MB; equal **1 ms**; by-share **3 ms**.
  - **10,000 random splits in 785 ms** (79 µs each), every one summing exactly, item sums exact.
  - 1,000 bills → 1,000 balances → **985 transfers in 11 ms** (n − 1 = 999 bound respected), heap flat.
  - Room v3: 200k rows inserted at ~47k rows/s (2.1 s, slower run on a busy machine); 1k-bill history **118 ms** (was 86 ms in v2: the extra `bill_shares` relation adds one batched query); paged 7 ms; 1,000 concurrent members + bills 1.5 s.
- What broke:
  - Nothing failed. The by-item path allocates one `LargestRemainder` call per item (10,000 BigInteger divisions) and stays at 20 ms; BigInteger was kept because exactness beats the microseconds.
  - History load grew ~35 % with the shares relation; still far below one frame per 100 bills.
- Failure flow (step by step):
  1. A group with thousands of bills opens the detail screen.
  2. `observeByGroup` loads bills, items and shares (three queries, Room batches ids by 999).
  3. `DebtSimplifier.balances` + `simplify` run on every emission on `Dispatchers.Default` (11 ms for 1,000 bills), never on the main thread.
  4. Worst case is UI latency on huge histories, not incorrect money: every path is exact or throws before saving.
- Fix applied / recommendation:
  - Balances computed off the main thread inside the ViewModel's `flowOn(default)` chain (applied).
  - Recommendation: a `bill_shares`-only query (`SELECT member_id, SUM(total_paise)`) for the balances section once groups exceed a few thousand bills.

## Feature: UPI settle-up links (builder / parser, fuzzing, QR rendering)
- Scenario and scale:
  - `UpiStressTest.10000 links built and parsed back`: 10,000 links with random amounts up to ₹10 lakh, names with `& ₹`, parsed back and compared field by field.
  - `UpiStressTest.10000 hostile payee names and vpas`: 10,000 inputs; half with syntactically valid random VPAs, half random garbage; names/notes of 1–59 characters drawn from `abcXYZ019 @.&=?#%/\"'<>` plus NUL, bidi override, ₹, newline, tab, é and an emoji whose UTF-16 halves can be picked separately.
  - `UpiStressTest.1000 qr renders`: 1,000 QR matrices for 1,000 distinct links.
- Results (numbers):
  - 10,000 build + parse round trips: **260–470 ms**, 10,000/10,000 exact.
  - Fuzz: **10,000 inputs in 387 ms; 4,977 links built, 5,023 rejected** (5,000 invalid VPAs, 23 names that became blank after sanitising); every built link had only percent-encoded values and parsed back to the same object.
  - 1,000 QR codes: **371–842 ms (0.4–0.8 ms each)**, 45×45 modules (2,025) on average, heap stable.
- What broke:
  - The first fuzz run rejected 100 % of inputs (random VPAs are almost never valid), so it never exercised the encoder; fixed by generating valid VPAs for half the inputs.
  - The second run exposed the unpaired-surrogate round-trip mismatch (see SECURITY_REVIEW feature 5), fixed in `UpiLinkBuilder.clean`.
- Failure flow (step by step):
  1. A member's name is pasted with a broken emoji (lone surrogate).
  2. Before the fix the link was built with the lone surrogate, encoded as `%3F`, and `AndroidUpiAppLauncher` re-parsed it into a different object (still valid, so it would have launched; the mismatch only affected equality and the share text).
  3. After the fix the surrogate is dropped before validation and the link, its parse and the share text agree.
- Fix applied / recommendation:
  - Sanitiser drops unpaired surrogates (applied); fuzz asserts `built > 1,000` so it can never silently degrade to rejecting everything again.
  - QR rendering is fast enough to do on the main thread, but it runs inside `remember` once per dialog; recommendation: pre-render on `Dispatchers.Default` if the link ever exceeds a few hundred characters.

## Feature: Firebase Crashlytics + Analytics (telemetry policy)
- Scenario and scale:
  - `AnalyticsStressTest.10000 concurrent events`: 10,000 random typed events across all six event types, tracked from 100 concurrent coroutines through the recording tracker (which runs the same `AnalyticsPolicy` as the Firebase adapter).
  - `AnalyticsStressTest.1000 non-fatals`: 1,000 non-fatal exceptions with sanitised custom keys through the recording reporter.
- Results (numbers):
  - **10,000 events sanitised and recorded in 81 ms** (8 µs each), none containing `@` or a 7+ digit run.
  - **1,000 non-fatals in 8 ms.**
- What broke:
  - Nothing. The policy is regex + map work per event; the Firebase SDK's own batching/upload is not exercised on the JVM.
- Failure flow (step by step):
  1. A burst of scans (e.g. a 50-person dinner) produces hundreds of events in seconds.
  2. Each event is validated synchronously on the calling coroutine (`Dispatchers.Default` in the use cases) and handed to Firebase, which queues locally and uploads in batches; no network wait on the caller.
  3. If Firebase is not configured the no-op implementations make every call free.
- Fix applied / recommendation:
  - None needed. Recommendation: cap `text_length`-style parameters (already numbers) and keep the event set small (six) so Analytics' 500-distinct-events quota is never a concern.

## Feature: Performance (off-main work, perf modules)
- Scenario and scale:
  - `OffMainStressTest`: 1,000 concurrent `ExtractReceiptUseCase.extract` calls (fake LLM with 1–10 ms latency, synthetic receipts) launched from one caller coroutine; records every thread the LLM call ran on.
  - Compile-only: `./gradlew :baselineprofile:assemble :macrobenchmark:assemble` (both test APKs) and `./gradlew :app:assembleBenchmark` (release-like app variant with R8 and the keep rules).
- Results (numbers):
  - **1,000 concurrent extractions in 182 ms on 16 worker threads (16 cores); caller thread never used.**
  - Perf test modules compile in **38 s**; the R8 `benchmark` app variant builds in **2 m 34 s** with no missing-class warnings; `app-benchmark.apk` is **46 MB** (bundled ML Kit Latin model, CameraX, Firebase, Compose).
  - Baseline Profile generation and macrobenchmarks: **not run (no device)**. Commands: `./gradlew :app:generateBaselineProfile`, `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`.
- What broke:
  - Nothing. The first version of the stress print reported "1,000 worker threads" because kotlinx-coroutines debug mode suffixes thread names with the coroutine id; the print now strips the suffix (16 real threads).
- Failure flow (step by step):
  1. The user taps "Continue" after a scan; the review screen starts extraction.
  2. OCR text normalisation, the Gemini call (up to 90 s), JSON repair and validation all run under `withContext(dispatchers.default)`; the main thread only renders the progress state.
  3. If the main thread were blocked for 5 s the system would raise an ANR; the StrictMode thread policy in debug builds logs any disk/network access that slips onto the main thread before that can happen.
- Fix applied / recommendation:
  - Thread-name parsing in the stress print (applied).
  - Recommendation: once a device is available, commit the generated `baseline-prof.txt` and record startup/scroll medians in the README's Performance section; consider `R8 fullMode` size tuning if the 46 MB APK matters (the ML Kit unbundled variant downloads the model via Play services instead).

## Polish: real Gemini latency and fallback behaviour (2026-10-30/31)
- Scenario and scale:
  - One real `generateContent` call per candidate model with the same 7-item restaurant receipt (508 prompt tokens, ~405 output tokens) and the production request shape (schema, temperature 0, minimal thinking); then `realApiTest` once; then three in-app extractions on the emulator (5-, 7- and 8-item receipts) and one in airplane mode. 10 real Gemini requests in total (1 model list, 5 measurements, 3 in-app, 1 `realApiTest`).
  - `./gradlew stressTest` re-run after the changes (all 17 simulations).
- Results (numbers):
  - `gemini-3.1-flash-lite` **3,050 ms**, `gemini-flash-lite-latest` 3,509 ms, `gemini-3.5-flash-lite` 4,005 ms (all HTTP 200 and exact: 7 items, tax ₹191.00, total ₹2,101.00); `gemini-3.8-flash` 400 for `minimal`, > 120,000 ms (client timeout) at `low`.
  - `realApiTest` (production pinned client and timeouts): **1,178 ms**, 2 items, total 21,000 paise, 0 warnings.
  - In-app on the emulator, tap "Continue" → `llm_parse_success` in logcat: **~2.7 s** (Spice Garden, 7 items), **~2.4 s** (Cafe Marigold Beans, 5 items, rotated 4°, tip ₹100), **3.3-7 s** (Golden Wok, 8 items; not timed exactly: the provisional draft was visible in every capture for the first ~3.3 s, the AI result by the next screenshot; 1 warning: a 10 % discount makes items + tax exceed the total by ₹270, shown as a mismatch).
  - Airplane mode: `UnknownHostException` → `llm_parse_failure failure=offline`, on-device result shown at once, no retries, "Try AI again" offered.
  - Stress after the changes: all green; e.g. 1,000 concurrent LLM calls with 600 × 429 → 1,600 requests, 0 failures in 746 ms; 10,000 receipts through the regex parser in 1.0 s; 1,000 extractions off the main thread in 66 ms.
- What broke:
  - Nothing under load. On the device the OCR layout (column order) and the regex fallback (dropped qty column) needed fixes, both now covered by regression tests with the real OCR text.
- Failure flow (step by step):
  1. The user taps "Continue" after OCR; the review screen shows the AI spinner.
  2. After 400 ms without an answer the on-device draft appears ("AI is refining…"); the user can already edit or save.
  3. The pinned client waits at most 30 s per read and 45 s per call; 429/5xx/timeouts retry with full-jitter backoff (0.5 s → 8 s, 4 attempts, `Retry-After` honoured); 401/403, other 4xx, offline and pin failures stop at once.
  4. A 404 tries the next model in the list once and caches the one that answers.
  5. On any final failure the draft stays, the banner names the reason and "Try AI again" re-runs the extraction (except for key problems).
- Fix applied / recommendation:
  - Timeouts lowered from 60/90 s to 30/45 s (~10x the measured latency). Recommendation: watch `llm_parse_failure` by `failure` in Analytics; if `timeout` rises, consider `gemini-flash-lite-latest` first.

## UI overhaul baseline (before)
**Emulator, software rendering, not a mid-range phone.** These numbers are only for comparing before and after on this setup. They say nothing about real-device speed.

- Commit: `db9f704` (the current UI, before any overhaul screen work).
- Device: `Pixel_7` AVD, API 37, x86_64 (`sdk_gphone16k_x86_64`), 4 vCPUs, `-gpu swiftshader_indirect`. Guest RAM is 4 GB: the emulator raises `-memory 2048` to 4096 MB. Animation scales 1.
- Host: 15 GB RAM with other applications open, so runs are noisy. Treat small differences as no change.
- Build type: `benchmark` (`initWith(release)`: R8 and resource shrinking on, non-debuggable, debug-signed, profileable). No Baseline Profile is in the APK yet.
- Method: Macrobenchmark 1.5.0 with `androidx.benchmark.suppressErrors=EMULATOR`. `StartupTimingMetric` over 10 cold starts; `FrameTimingMetric` over 5 iterations of 3 flings down and 3 up, with a new process each iteration.
- Data: 200 groups with 4 members each, the newest being "Benchmark trip" with 200 bills (half split equally), inserted through `GroupRepository` and `BillRepository`.
- Scenarios: `homeScroll*` flings the groups home; `billsScroll*` opens "Benchmark trip" and flings its list.
- Reading the tables: CPU is `frameDurationCpuMs`. Overrun is `frameOverrunMs`, how far a frame missed its deadline (negative means early). Jank % is the share of frames with an overrun above 0. Software rendering pushes nearly every frame over budget here, so compare the frame times rather than expecting low jank.
- `NoCompilation` is the fair before/after comparison. `Partial` compiles from a 3-iteration warm-up and will also use a Baseline Profile once one ships.
- **Not run yet (`billsScrollNoCompilation` pending):** `startupPartial`, `startupFullCompilation`, `homeScrollPartial`, `billsScrollNoCompilation`, `billsScrollPartial`. Sessions on this machine were restarting every 10 to 60 minutes, so the `NoCompilation` tests came first. Compare only tests that exist on both sides.

| Cold start (time to initial display, ms) | min | median | max | iterations |
|---|---|---|---|---|
| startupNoCompilation | 767 | 789 | 822 | 10 |

| Scroll | CPU P50 ms | CPU P90 ms | CPU P99 ms | Overrun P50 ms | Overrun P90 ms | Overrun P99 ms | Jank % | frames |
|---|---|---|---|---|---|---|---|---|
| homeScrollNoCompilation | 21.9 | 26.3 | 38.4 | 5.9 | 10.8 | 29.5 | 86.1 | 3696 |

Release APK size: 46,386,407 bytes (44.2 MiB), from `./gradlew assembleRelease` (unsigned).

Seed data is benchmark-only. `aapt2 dump xmltree` shows `SeedDataReceiver` in the `benchmark` manifest and not in the debug or release manifests, and the release dex has no reference to it.

Reproduce (run on an emulator, and set the serial explicitly so no other connected device is used):

    ./gradlew --stop
    emulator -avd Pixel_7 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-load -memory 2048
    ANDROID_SERIAL=emulator-5554 scripts/run_benchmarks.sh /path/to/results
    ./gradlew assembleRelease

The script installs a fresh copy per test and skips tests that already have a result in that directory, so it can be re-run after an interruption. `BENCHMARK_TESTS="ScrollBenchmark#homeScrollNoCompilation"` limits it to named tests.

To keep these benchmarks working as the UI changes:

- The group card keeps the group name in its text or content description, and it stays on the first screen for the newest group.
- The group screen shows the group name as an exact text once open (the benchmark waits on it).
- Groups home and group detail stay vertically scrollable: the benchmark picks the tallest scrollable node, whichever component draws it.
- No new first-launch screen may block a fresh install. The planned onboarding must be marked done by `SeedDataReceiver`, or the benchmarks will time out.
- `SeedDataReceiver` keeps writing through the repositories; update it if the models or Room schema change.
- The journeys (`JourneyBenchmark`, `BaselineProfileGenerator`, row 14) find their way by words on screen: the group tiles "Scan receipt" and "Enter manually", the shutter's description "Take photo", "Allow camera", "Save bill", "Split this bill" / "Edit split", "Save split", "Settle up", "Paid another way", "Yes, mark as paid", the top bar's "History", the sheets' "Create group", "Add member" (also the description of the members heading's Add) and "Add bill", and "Skip" on the intro. Rename one and the journey must follow.


## UI Feature: Design system + catalog
- Scenario and scale (JVM, Robolectric with native graphics, `./gradlew stressTest`, `DesignSystemStressTest`; not a device):
  - 1,000 injected taps on a settle button whose action sets `loading`; 1,000,000 same-instant taps through `ClickGuard`.
  - A 200-member `AvatarStack`; 10,000 group stacks laid out and described.
  - A 10,000-row `LazyColumn` of `SsListRow` (avatar + amount pill, keys and content types), 300 scroll jumps.
  - 200 interrupted rolls of `RollingAmount` up to ₹99,99,99,999.99 at 1.3× font in a 300dp card; 100,000 amounts to glyphs.
  - 1,000 counter bumps, one per frame; 1,000 undo snackbars with a double-tapped Undo; 1,000 confetti bursts × 96 frames × 48 pieces; animator scales 0, 0.5, 1, 3, 10.
- Results (numbers):
  - Taps: 1 settlement and 1 haptic from 1,000 taps (671 ms); guard: 1 accepted from 1,000,000 (4 ms).
  - 200 members compose 5 faces and one TalkBack sentence; 10,000 stacks in 38 ms.
  - 10,000 rows: 300 jumps in 3,702 ms to item 9,867, 0 recomposer change cycles from scrolling, heap 113 → 108 MB.
  - Maximum amount: still inside the card at 1.3× after 200 interrupted rolls (454 ms, 402 recomposer cycles = about 2 per value change, none per frame); 100,000 amounts to glyphs in 71 ms.
  - Counter: 1,000 bumps in 540 ms, settles on "99+", then on 7.
  - Undo: 1,000 restores from 2,000 Undo taps (24 ms), nothing left on screen, heap 116 → 118 MB.
  - Confetti: 4,608,000 particle placements in 56 ms (12 ns each), into one reused array.
  - Animator scale: only 0 gives reduced motion.
  - Per-frame recomposition, from the Compose tests: rolling amount < 8 recomposer cycles over 40 frames, bottom bar blob < 4 over 30 frames (animated values are read in layout, draw or layer only).
  - Release APK: 46,555,887 bytes, +169,480 bytes (+0.37 %) over the 46,386,407-byte baseline, of which 257,584 bytes are the two subset fonts before compression.
- What broke:
  - Nothing at these scales.
  - During design QA the full-width pressed button grew 19dp wider and showed its base above the face; the squash is now capped at 8dp of growth and the base closes as the face sinks.
  - Letting the bottom bar grow with `heightIn` made its full-height tabs fill the screen; caught by the screenshot verify, reverted to a fixed height (1.3× labels fit, see `ds_bottom_bar_light_large_font`).
- Failure flow (step by step): not reached. The breaking point for the stack is the description, which names at most four people, so it stays constant; the list is bounded by Compose's lazy layout, not by the row.
- Fix applied / recommendation:
  - Frame timing on a device (Macrobenchmark) waits for the screens to use the design system (rows 9-13) and the QA row; numbers above are JVM only.

## UI Feature: Shell, splash and intro
- Scenario and scale (JVM, Robolectric, `./gradlew stressTest` → `ShellStressTest`, `AppStartStressTest`; not a device):
  - 1,000 taps on Skip; 1,000 taps on Next (one per frame, 600 ms apart on the click clock); a 40-frame drag across the intro.
  - 1,000 cold starts of the start ViewModel (half fresh installs, half returning), each finished 10 times.
  - 1,000 tab switches through the real `NavHost` with the shell's `openTab`.
  - 10,000 status-bar surfaces registered, settled and removed; 1,000 resolutions over 10,000 entries.
  - Bar Scan routing over 10,000 groups (1,000 resolutions); every one of the 64 route pairs through the transition rule.
- Results (numbers):
  - Skip: 1,000 taps → 1 finish (1,599 ms). Next: 1,000 taps → last page and 1 finish (607 ms), because the rising "Let's split a bill" button covers the spot where Next was; never twice.
  - Swipe: 40 frames of drag plus settle → 3 recomposer cycles (the layers, words and indicator pill read the pager position in layer and draw lambdas).
  - Start: 1,000 launches → 500 intro, 500 app, every stored flag written once (110 ms).
  - Tabs: 1,000 switches → 1 destination above the graph root on the back stack (5,171 ms, Robolectric).
  - Status bar: 0 entries left after 10,000 cycles (19 ms); 1,000 resolutions over 10,000 entries in 2 ms.
  - Scan routing: 1,000 resolutions over 10,000 groups in 3 ms; the picker sheet is a lazy list capped at 360dp (`Sizes.sheetListMax`).
  - Release APK: 46,535,708 bytes, −20,179 bytes (−0.04 %) against row 8's 46,555,887 (the coral palette and system typography went, the splash, intro, shell and Me came in).
- What broke:
  - Nothing in the app at these scales.
  - Test environment: Robolectric screens taller than its default 320×470dp made two not-yet-rebuilt Material screens loop in layout (typing in the New group dialog, opening the group screen) until the test JVM ran out of heap; reproduced with the old navigation host and old groups screen too, so it is not shell code. App-level tests stay on the default screen; the "newest card on the first screen" benchmark rule is checked at phone size by `GroupsUnderBarTest`. Row 10 rebuilds both screens; worth re-checking there.
- Failure flow (step by step): typing into the dialog field on a 411×891dp Robolectric screen → text re-measured every frame → the trace buffer grows → `OutOfMemoryError` after about 70 s → the rest of that JVM's tests fail with looper errors. Not seen at 320×470.
- Fix applied / recommendation:
  - Frame timing of the splash exit, intro swipe, container transform and predictive back on a device belongs to the batched device session after row 14 (memory rule: no emulator in this row).

## UI Feature: Groups home and group screen
- Scenario and scale (JVM, Robolectric, `./gradlew stressTest` → `GroupsStressTest`, plus `GroupsHomeLogicTest` and `GroupDetailLogicTest`; not a device):
  - Home cards for 10,000 groups with 40,000 members and 100,000 split bills, matched against "your name".
  - The home list with 10,000 cards: 300 jumps to scattered positions, one frame each.
  - 1,000 taps on the FAB menu's Scan between frames; 1,000 archive-and-undo cycles through the view model, each with three archive requests (a swipe and two stray taps); 1,000 delete confirmations.
  - The group screen with 200 members and 500 bills.
  - ₹99,99,99,999.99 in the hero at 1.3× text; 2,000 bills and 2,000 debts at the ₹1 crore cap summed in the group hero.
  - The opening stagger over 10,000 cards.
- Results (numbers):
  - Cards and overview for 10,000 groups / 40,000 members / 100,000 bills: 46 ms, off the main thread (`Dispatchers.Default` in the view model).
  - 300 jumps over 10,000 cards: 3,402 ms on Robolectric, 0 recomposer cycles from scrolling, 5 cards composed at the end.
  - Opening the FAB menu: under 6 recomposer cycles over 30 frames (the items, scrim and turning plus read the spring in layer and draw lambdas). Hero colour morph and roll: under 8 cycles over 40 frames, all from the value change itself.
  - FAB menu: 13 taps landed on Scan while it closed → 1 scan (87 ms).
  - Archive/undo: 1,000 cycles (3,000 archive requests) → exactly 1,000 writes and 1,000 Undo offers (239 ms); the group ends active, nothing left pending.
  - Delete: 1,000 confirmations → 1 delete, 1 event (2 ms).
  - Group screen, 200 members + 500 bills: 5 faces and 1 bill row composed on a 411dp-wide screen, first frame in 81 ms.
  - Entrance: 10,000 cards → 8 entrance animations (the first screenful); the rest never animate.
  - Amounts: the largest fits the hero at 1.3×; the group hero saturates at ₹99,99,99,999.99 instead of overflowing.
  - Release APK: 46,602,040 bytes, +66,332 bytes (+0.14 %) against row 9's 46,535,708 (two screens, the archive migration, the FAB menu and the swipe).
- What broke:
  - Nothing in the app at these scales.
  - Test environment: `performScrollToNode` on a paused test clock keeps requesting scrolls that never land and exhausted the heap in one screenshot test (`OutOfMemoryError`); screenshot tests now scroll by key. The row 9 layout loop on tall Robolectric screens is gone with the old screens: app-level tests can run at phone size (`MainActivityTest`, 7.9 s).
- Failure flow (step by step): paused clock → `performScrollToNode` asks the lazy list to scroll → the scroll waits for a frame that never comes → the helper asks again → heap grows until the JVM gives up. Not an app path.
- Fix applied / recommendation:
  - Key-based scrolling in screenshot tests; behaviour tests keep the auto-advancing clock.
  - Frame timing of the hero morph, card swipe, FAB burst and the card-to-screen transform on a device belongs to the batched device session after row 14.

## UI Feature: Scan, OCR, AI reading and receipt review
- Scenario and scale (JVM, Robolectric, `./gradlew stressTest` → `ScanReviewStressTest`, plus `ScanScreenTest`, `ReviewScreenTest` and the view-model tests; not a device):
  - A 500-line receipt through the on-device parser into the review (the bill cap is 200 lines), its first frame, the whole line-by-line reveal frame by frame, then 300 jumps across the list.
  - 10,000 live text frames at the analyzer's pace, each with 20 to 79 outlines, on the camera screen.
  - 1,000 shutter calls through the view model; 100 injected taps on the real shutter of the real route; 1,000 capture-then-rescan cycles; 1,000 saves of one review.
  - The largest amounts: a line at the ₹1 crore cap and a ₹99,99,99,999.99 subtotal at 1.3× text.
- Results (numbers):
  - 500-line receipt: parsed in 21 to 29 ms → 200 rows with the "only the first 200 lines" note; first frame of the 200-row review 314 to 378 ms on Robolectric (only the visible rows are composed: the old screen built a `Column` of 600 text fields); the reveal is capped at 1,120 ms and caused 0 recomposer cycles; 300 jumps took 3,411 to 3,672 ms.
  - 10,000 live text frames: 384 to 452 ms, 8 recomposer cycles in total (the hint pill changing wording; the outlines are drawn, never composed).
  - Shutter: 1,000 calls → 1 picture, 1 heavy haptic, 1 hand-off to the review; 100 injected taps → 1 picture.
  - 1,000 capture + rescan cycles in 343 to 357 ms: 0 drafts and 0 photos left behind, exactly 1,000 haptics and 1,000 hand-offs, live text back on at the end.
  - 1,000 saves → 1 bill.
  - Amounts: the ₹1 crore line and the ₹99,99,99,999.99 subtotal stay inside their cards at 1.3× text (`ReviewScreenTest`).
  - Release APK: 46,647,222 bytes, +45,182 bytes (+0.10 %) against row 10's 46,602,040 (two rebuilt screens, the camera illustration and `camera-mlkit-vision`).
- What broke:
  - Nothing in the app at these scales.
  - Test environment: a stress step called `runBlocking` on a use case whose dispatcher was a paused test dispatcher, and waited forever; the step now uses real dispatchers.
- Failure flow (step by step): `runBlocking { provisional(text) }` → `withContext(test dispatcher)` → nobody advances that dispatcher → the test thread waits. Not an app path.
- Fix applied / recommendation:
  - The live analyzer's real cost (frames per second, CPU, battery, heat) and the shutter-to-review time need a device; both are on the batched device list after row 14.

## UI Feature: Assign, split, bill and simplify debts
- Scenario and scale (JVM, Robolectric, `./gradlew stressTest` → `SplitUiStressTest`, plus `SplitScreenTest`, `BillDetailScreenTest`, `SimplifyTest` and the view-model tests; not a device):
  - A ₹1 crore bill (the cap) with 200 lines split by item between 200 people: first frame, 300 jumps through the list, the donut's layout.
  - 1,000 drops and mode changes on that bill through the view model; 100 real drags on the screen, one after another.
  - 1,000 saves, 1,000 bill deletes; 10,000 random bills over 3 and 7 people, equally and by even percentages.
  - The simplify picture for 200 people and 10,000 bills.
- Results (numbers):
  - Split screen, 200 people × 200 lines by item: first frame 173 to 242 ms; 300 jumps in 4,317 to 5,000 ms with 0 recomposer cycles from scrolling; 1,000 donut layouts in 2.0 to 2.2 ms (one reused array, no allocation per frame).
  - Donut morph between modes: at most 6 recomposer cycles over 30 frames (the rolling amounts settling), the arcs move in draw.
  - 1,000 drops and mode changes: 1,241 to 1,259 ms, worst single operation 15.7 to 30.9 ms (a full by-item split of 200 lines over 200 people each time), every preview exactly ₹1,00,00,000.00; 1,000 light haptics nobody collected left 16 effects buffered (the channel's cap).
  - 100 drags in 741 ms → 100 assignments, no drag pill left behind.
  - 1,000 saves → 1 write and 1 confirm haptic; 1,000 rapid taps on Save → 1 save; 1,000 bill deletes → 1 delete.
  - 10,000 random bills over 3 and 7 people in 122 to 128 ms: every split exact, equal shares at most 1 paisa apart (₹100.00 over 3 = 33.34 + 33.33 + 33.33).
  - Bill screen with 200 shares and 200 lines: first frame 847 to 963 ms on Robolectric (only a screenful is composed).
  - Simplify, 200 people and 10,000 bills: 16,382 IOUs bill by bill → 198 payments, drawn as 8 faces (+193 folded), 35 to 43 ms per graph; the picture's animation causes at most 3 recomposer cycles over 60 frames.
  - Release APK: 46,697,606 bytes, +50,384 bytes (+0.11 %) against row 11's 46,647,222 (two rebuilt screens and the graph).
- What broke:
  - Nothing in the app at these scales.
  - Test environment: with a paused test clock, a drag injected as one batch reaches the gesture detector only partly (it resumes on frames), so mid-drag screenshots send one event per frame. The sticky row of faces covers a line that a test scrolls to the very top, so tests scroll to the section header instead.
- Failure flow (step by step): `performTouchInput { down; moveBy × 10 }` with the clock paused → events queued in one batch → the detector's coroutine waits for a frame → most moves arrive before it resumes → no drag starts. Not an app path.
- Fix applied / recommendation:
  - The drag's feel (hover growth, gulp, haptic), the donut morph and the graph's two phases at 60/120 Hz need a device; they are on the batched device list after row 14.
  - A bill holds at most 200 lines, so a 500-line receipt arrives here as 200 lines (row 11 shows the note).

## UI Feature: Settle up, history and insights
- Scenario and scale (JVM, Robolectric, `./gradlew stressTest` → `SettleUpStressTest`, `SettleUpTapStressTest`, plus the screen, view-model and DAO tests; not a device):
  - 200 people and 2,000 random bills: every payment settled one by one through the view model (Pay or Paid another way, back, Yes) until all square; the same screen with its real view model, 300 jumps through the list and 1,000 taps on Pay.
  - 1,000 mark-and-undo cycles on one payment.
  - Balances and the split check for 10,000 bills and 10,000 payments over 200 people.
  - History for 10,000 bills and 10,000 payments (20,000 rows over 14 months); insights over 100,000 bills.
  - ₹99,99,99,999.99 at 1.3× text in a 316dp column, with a right-to-left override and emoji in a name.
- Results (numbers, two runs):
  - Settle everything: 198 payments in 435 to 657 ms, 198 records and 198 UPI links, all square at the end, no work left on the virtual clock afterwards.
  - Settle-up list, 198 payments with the real view model: first frame 65 to 109 ms, 300 jumps 2,157 to 2,854 ms with 0 recomposer cycles from scrolling (the glint and amounts are drawn, not recomposed); 1,000 taps on Pay → 1 UPI link opened.
  - 1,000 mark + undo cycles: 293 to 417 ms, 1,000 records, 0 left behind.
  - Balances with 10,000 bills + 10,000 payments: 199 payments, 3 to 5 ms per pass, balances sum to exactly 0; split check 0 to 2 ms per pass.
  - History: timeline of 20,000 rows built in 7 to 11 ms; first frame 27 to 838 ms (Robolectric warm-up dominates the high one); 300 jumps 1,238 to 4,235 ms with 0 recomposer cycles; insights over 100,000 bills 27 to 44 ms.
  - Largest amount: inside the hero and the card at 1.3× in 316dp; the bidi override is stripped.
  - Release APK: 46,664,670 bytes, −32,936 bytes (−0.07 %) against row 12's 46,697,606 (two new screens, minus the legacy components and four drawables).
- What broke:
  - Nothing in the app at these scales.
  - Test environment: the button's 500 ms guard alone lets about one tap through per 500 ms of injected time (84 of 1,000 taps over ~30 s of test clock); the one-action guarantee comes from the view model refusing a second payment while a question is open, so the tap run uses the real view model.
- Failure flow (step by step): tap Pay → the guard accepts → the view model opens the UPI link and turns the card into the question → the next accepted tap lands on the next card's Pay → the view model refuses (one question at a time). Without the view model in the loop the count would be the guard's rate, not one.
- Fix applied / recommendation:
  - The glint at 60/120 Hz, the stamp's slam, the bars growing, confetti over a long list and the haptics need a device; they are on the batched device list after row 14.
  - History holds the whole group in memory (as the group screen does); paging is the next step past tens of thousands of rows per group.

## UI Feature: Whole-app design QA (row 14)
- Scenario and scale: no new load scenario; this row changed copy, emoji, error pages and list-row animation specs. The existing stress suite (`stressTest`) re-ran on the final tree.
- Results: see the row 14 entry in the private build history for the stress run; the token audit is clean (50 files, 0 legacy budget); `MotionAuditTest` and `EmojiAuditTest` join `TokenAuditTest` as source audits that run in milliseconds.
- What broke: nothing under load. Two benchmark gaps were closed: the Baseline Profile journey stopped at the camera's permission page, and no benchmark measured the scan → split → settle flow.
- Failure flow: n/a.
- Fix applied / recommendation: `BaselineProfileGenerator` now drives the whole first evening (intro, group, two members, the camera page, a typed bill, split, Paid another way, "Yes, mark as paid", history), idempotent across iterations; `JourneyBenchmark` (FrameTimingMetric, `None` and `Partial`, 3 iterations) runs groups → scan (falls back to a seeded bill when the photo has no text) → review → split → settle → history on seeded data, with the camera permission granted by `pm grant`. Both compile (`:macrobenchmark:assembleBenchmark`, `:baselineprofile:assemble`); neither has run (no device in this row). Device session pending.

## Device session (2026-11-10)
- Scenario and scale:
  - Emulator: `Pixel_7` AVD, API 37, 2 GB guest, 2 vCPUs, software rendering (`-gpu guest`, llvmpipe), inside a 5.8–6.1 GB
    kernel memory cap that protects the desktop it shares the laptop with. Every number below is an emulator number.
  - Builds: after = the redesign at `3c674eb`; before = `db9f704`, the pre-redesign baseline above, built from a git
    worktree. Both are the release-like `benchmark` build type with `SeedDataReceiver`: 200 groups, the newest being
    "Benchmark trip" with 200 bills (and, after, 24 settled payments).
  - Method: `scripts/light_bench.sh` (no Macrobenchmark, no Perfetto). Fresh install, seed, dex reset to `verify` (no AOT
    code, the state Macrobenchmark calls `CompilationMode.None`), one discarded launch, then cold starts (force-stop +
    `am start -W`, TotalTime). Frames: per round a cold launch, `dumpsys gfxinfo` reset, 3 flings down and 3 up, on the
    groups home and on the "Benchmark trip" group screen (its bills).
  - Sample sizes: 4 cold starts per build; 2 frame rounds per screen per build. Startup, home frames and bills frames each
    ran on a fresh boot, because repeated cold starts grow the emulator towards its cap.
- Results (numbers, before vs after):

  | Cold start, `am start -W` TotalTime (ms) | runs (in order) | min | median | max |
  |---|---|---|---|---|
  | before `db9f704` | 917, 941, 1,000, 844 | 844 | 929 | 1,000 |
  | after `3c674eb` | 790, 707, 817, 700 | 700 | 748 | 817 |

  | Frames (gfxinfo, 2 rounds) | frames | janky % | P50 ms | P90 ms | P95 ms | P99 ms |
  |---|---|---|---|---|---|---|
  | Home, before | 831 | 5.3 | 26 | 32 | 37 | 54 |
  | Home, after (a different page now) | 635 | 18.1 | 32 | 50 | 54 | 150 |
  | Group bills, before | 961 | 2.9 | 26 | 32 | 32 | 48 |
  | Group bills, after | 706 | 8.5 | 29 | 39 | 47 | 111 |

  Percentiles are the median over rounds of gfxinfo's per-round percentile; janky % is janky frames over all frames.
  Per-round raw counts (frames / janky): home before 488/19, 343/25; home after 321/63, 314/52; bills before 481/16,
  480/12; bills after 346/31, 360/29.
- What the numbers say:
  - Cold start is about 180 ms faster at the median. The redesign's start path decides between the intro and the app
    from one DataStore read while the splash holds (`AppStartLogic`, 2 s cap), and the seed marks the intro done, so both
    builds land on the groups list. Four runs each, so treat it as "not slower", not as a precise gain.
  - Flinging got slower. The redesigned home is a different page (an aubergine hero with a rolling total, chunky cards
    with avatar stacks and status pills, under a floating bar), so its row compares two designs. The bill list is the
    fairer pair: P90 32 → 39 ms and janky frames 2.9 → 8.5 %, with fewer frames drawn per round. Software rendering puts
    every frame on the CPU; a phone's GPU changes these numbers. Compare only within this table.
- What was not run, and why:
  - Macrobenchmark (`StartupBenchmark`, `ScrollBenchmark`, `JourneyBenchmark`): not run. On every boot the emulator's
    anonymous memory sat at 5.0–5.9 GB of a 6.1 GB cap after five launches; Perfetto tracing on top of that was OOM-killed in
    PayFlow's earlier session.
  - Baseline Profile: generated, committed (`app/src/main/baseline-prof.txt`, 39,151 rules from the first-evening journey,
    `maxIterations = 5`), but its effect is not measured yet. Run 1 (2026-11-10) failed at the "open the new bill" step with
    a `StaleObjectException` (the new bill row is replaced while the list settles after the bill sheet closes); the journey
    now finds the row again (`clickFresh`). On 2026-11-12 the first start was refused (about 6 GB free, it needs 8.2 GB);
    in the second boot the guest's own low-memory killer stopped the generator one second after launch (nothing ran),
    and the next run passed. The profile sat in `src/main/generated/baselineProfiles/` first, but this plugin setup did
    not merge it (`mergeBenchmarkArtProfile` stayed up to date); `src/main/baseline-prof.txt` is merged into every variant:
    the APK's `baseline.prof` went from 12,328 to 13,151 bytes, the release APK from 46,667,346 to 46,683,730 bytes.
    Measuring it needs one more boot: `COMPILE=speed-profile COLD_RUNS=4 SKIP_FRAMES=1 scripts/light_bench.sh` on the new
    benchmark APK against the same command on the APK without the profile.
  - One emulator boot (looks and recordings) was OOM-killed by its cap after about 27 minutes of UI work and a dozen screen
    recordings (cap 5.8 GB; the desktop was unaffected). Its recordings had already been pulled; the rest of the looks ran
    on the next boot.
- Device looks and recordings: see DECISIONS "Device session". Recordings in `docs/media/`: `splash-intro.webp` (264 KB),
  `groups-fab.webp` (89 KB), `scan-review.webp` (175 KB), `split-drag.webp` (86 KB), `settle-stamp.webp` (232 KB),
  `simplify.webp` (97 KB). LeakCanary (debug build): 5 watched objects, including two `MainActivity` recreations from a
  font-scale change, 0 retained.

## UI Feature: Enhancement pass (row 17)
- Scenario and scale:
  - Flinging the group screen's bills (2,000 bills over six people, every status and source) and the groups home (2,000 cards), 60 flings each after 10 warm-up flings, in three set-ups inside one composition so they share a warm JVM: on its own, inside the shell's shared-element layout with no row taking part, and inside the shell as in the app (`ListFlingCostStressTest`).
  - Delete + Undo through Room: a 200-person group with 2,000 split bills (6,000 lines, 4,000 shares) and 500 payments, 12,701 rows, 20 cycles (`TrashStressTest`); 1,000 Undo taps through the shell's view model (`ShellUndoViewModelTest`).
- Results (JVM, Robolectric; comparable only with each other):

  | 60 flings | recomposition cycles | ms per fling | KB allocated per fling |
  |---|---|---|---|
  | Bills, in the shell, before (every row a shared element) | 998 | 17 | 4,173 |
  | Bills, in the shell, after | 0 | 11 | 3,706 |
  | Bills, on their own | 0 | 11 | 3,462 |
  | Groups, in the shell, before | 1,042 | 22 | 5,221 |
  | Groups, in the shell, after | 0 | 15 | 4,666 |
  | Groups, on their own | 0 | 14 | 4,383 |

  Delete + Undo of the 12,701-row group: 36 ms to delete (read and delete in one transaction) and 30 ms to put back, per cycle; balances identical after every cycle. 1,000 Undo taps → 1 restore.
- What broke: the shell's shared-element layout recomposed the visible rows on every fling, about 17 times per fling, because every bill row and group card registered as one end of a container transform as it scrolled in. First measured with separate test methods, the times were dominated by which test ran first (a cold JVM made the first set-up look 3× slower); the final test switches set-ups inside one composition and reports a second, warm round.
- Failure flow: fling → rows scroll in → each row's `sharedBounds` registers with the `SharedTransitionLayout` → the shell's shared-element state changes → visible rows recompose → more main-thread work and allocation per frame.
- Fix applied / recommendation: only the row under a still finger, or the last one opened, carries the transform (`listContainerTransform`); a finger that travels past the touch slop gives it back. The emulator figures above (P90 32 → 39 ms) were measured before this fix; re-measure with `scripts/light_bench.sh` (`SCREENS=bills`) on a device.
- Device (2026-11-12, one emulator boot, same `Pixel_7` AVD and `light_bench.sh` method as the device session): the build
  before this fix (`3c674eb`) flung the bills at P50/P90/P95/P99 27/33/37/75 ms with 5.9 % janky frames (725 frames, 2 rounds;
  rounds 371/18 and 354/25 frames/janky). The emulator was then OOM-killed by its memory cap while installing the build with
  the fix, so there is **no after number from a device**: not a result, and the JVM table above is the only evidence for the
  fix. Re-run `SCREENS=bills ROUNDS=2 SKIP_STARTUP=1 scripts/light_bench.sh <apk> <dir>` for each build on fresh boots.

