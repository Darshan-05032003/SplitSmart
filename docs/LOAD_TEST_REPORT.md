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
  - In-app on the emulator, tap "Continue" → `llm_parse_success` in logcat: **~2.7 s** (Spice Garden, 7 items), **~2.4 s** (Blue Tokai, 5 items, rotated 4°, tip ₹100), **3.3-7 s** (Mainland China, 8 items; not timed exactly: the provisional draft was visible in every capture for the first ~3.3 s, the AI result by the next screenshot; 1 warning: a 10 % discount makes items + tax exceed the total by ₹270, shown as a mismatch).
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
