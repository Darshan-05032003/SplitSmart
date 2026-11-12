# Security review

One entry per feature, written after attacking the feature as a pentester. Severity: Critical / High / Medium / Low.

## Feature: Groups, members, bill history (Room)
- Attacks tried:
  - Hostile names: empty, whitespace-only, 61+ chars, NUL and other control characters, bidi override (`U+202E`) and isolates, line breaks, emoji, Devanagari, repeated internal whitespace (`ValidatorsTest`).
  - Hostile UPI IDs on members: no `@`, empty user/handle, spaces, `javascript:` scheme, `?am=1` / `&pa=x` query smuggling, `%40` encoding, `/`, non-ASCII, 260-char user part, NUL (`ValidatorsTest.vpa validator rejects hostile input`).
  - Money input: negative, three decimals, scientific notation, double dots, Arabic-Indic digits, `NaN`, `Infinity`, 20-digit overflow, embedded spaces, amounts above the ₹1 crore cap (`MoneyTest`).
  - Model invariants: negative tax/tip/total, `Long.MAX_VALUE` totals, zero quantity, inconsistent `qty × unit != total`, 201 items (`BillTest`).
  - Rapid double tap on Create group / Add member / Add bill (`GroupsViewModelTest.double submit creates only one group`, `submitting` guard in both ViewModels).
  - Process death with a dialog open (`GroupDetailViewModelTest.add member validates and stores the draft in saved state`).
  - Referential integrity: member inserted for a missing group, deleting a group with members and bills, deleting a member who paid a bill (`SplitSmartDaoTest`).
  - Corrupt rows in the database (unknown enum string) → repository skips the row instead of crashing the list.
  - Backup / extraction: `allowBackup=false`, `dataExtractionRules` exclude everything, `usesCleartextTraffic=false`; only `MainActivity` is exported.
- What broke:
  - **Medium:** `Money.MAX_PAISE` was written as `1_000_000_000_00L` (₹100 crore, 100× the intended cap); the cap test caught it before any UI used it.
  - **Low:** Room `@Relation` returns items in arbitrary order; the receipt order would have been lost in the UI.
  - **Low:** The text field in the create-group dialog accepted unbounded input before validation ran (memory pressure with a pasted novel).
- Fixed now:
  - `MAX_PAISE = 1_000_000_000L` with `MoneyTest.rejects amounts above the hard cap` as the regression test.
  - `BillWithItems.items` sorts by `sort_order` (`SplitSmartDaoTest.bills are observed newest first per group with items in sort order`).
  - Input length caps in the ViewModels (`NAME_INPUT_LIMIT`, `INPUT_LIMIT`, `AMOUNT_INPUT_LIMIT`) before the validators enforce the real limits.
  - Member removal uses `ON DELETE SET NULL` for `bills.paid_by_member_id` so a bill is never silently deleted (`member removal sets bill payer to null`).
- Scope for improvement:
  - SavedStateHandle drafts live in the activity Bundle unencrypted (names only, no amounts of value); acceptable for a bill splitter.
  - Room database is unencrypted at rest; SQLCipher would be the next step if receipts were sensitive.
  - No per-group rename/edit UI yet, so no additional validation paths to attack there.

## Feature: Receipt scanning (CameraX + gallery import → ML Kit OCR)
- Attacks tried:
  - FileProvider path traversal: `../splitsmart.db`, `../../databases/splitsmart.db`, `/etc/passwd`, `a/../../b.jpg`, `..`, empty name, NUL byte, nested `sub/dir.jpg` against `resolveOwnedFile` (`CacheReceiptImageStoreTest.path traversal names are never resolved outside the receipts dir`). The provider only exposes `cache/receipts/` (`res/xml/file_paths.xml`), is `exported=false` and grants URI permissions per intent only.
  - Hostile gallery picks: unparseable URI, unknown `content://` authority, `file://` pointing into the app's private data dir (database), empty stream, a 20 MB + 1 byte stream (`import rejects unreadable and oversized sources`, `import refuses sources that point inside app private storage`).
  - Hostile OCR output: NUL / zero-width / bidi-override characters, 50k-char tokens, 20k-line receipts, 5k `1O.OO` lines (`OcrTextNormalizerTest`, `OcrStressTest.pathological inputs`).
  - Double tap on "Take photo" while a capture is in flight (`ScanViewModelTest.double tap on capture takes one picture`); gallery pick during recognition is ignored.
  - Permission denied / permanently denied; process death between scan and review (draft id in `SavedStateHandle`, text in Room).
  - Camera failure, recogniser crash, missing image file, draft save failure each mapped to a typed `ScanError` (no raw exception text reaches the UI).
- What broke:
  - **High:** the first import implementation accepted any `file://` URI. A crafted picker result pointing at `/data/data/com.darshan.splitsmart/databases/splitsmart.db` would have copied the database into the shareable `receipts/` directory, from where a FileProvider URI could leak it. Found by the private-storage test.
  - **Medium:** no size cap on imported images meant a malicious provider could stream gigabytes into the cache.
  - **Low:** images and drafts stay in the cache indefinitely.
- Fixed now:
  - `file://` sources under `context.dataDir` are rejected with `ImageImportException` (regression test above); only `content://` and external `file://` sources are copied.
  - `CacheReceiptImageStore.MAX_BYTES` (20 MB) enforced while streaming; the partial copy is deleted on failure (test asserts the directory is empty afterwards).
  - `purgeOlderThan` + `ReceiptDraftRepository.deleteOlderThan` exist and are tested; `rescan()` deletes the draft and its image immediately.
  - File names are always app-generated UUIDs; no caller-controlled path is ever joined to the receipts directory.
- Scope for improvement:
  - Schedule the purge with WorkManager (currently only explicit `rescan`/delete paths free space).
  - `MlKitTextRecognizer` and `CameraXReceiptCamera` are device-only; their error paths are covered through the interfaces but not on hardware in this build.
  - Captured photos are stored unencrypted in the cache (receipts are low sensitivity; a Keystore-wrapped cipher would be the next step).

## Feature: LLM extraction (Gemini over pinned OkHttp, JSON repair, regex fallback, review screen)
- Attacks tried:
  - **SSL pinning bypass:** a TLS MockWebServer with a *trusted* certificate but a non-matching SPKI pin must be rejected (`PinningTest.wrong pin is rejected even though the certificate is trusted`, asserts `SSLPeerUnverifiedException` and that no request reached the server). Correct pin and no-pinner paths accepted. Pins apply only to `generativelanguage.googleapis.com`; a look-alike host (`notgenerativelanguage.googleapis.com`) has no pins (so it would also never be contacted with our key, see below).
  - **Pin-rotation risk:** Google rotates leaf certificates every few weeks and serves two different chains (ECDSA via `WE2`/`GTS Root R4`, RSA via `WR2`/`GTS Root R1`) depending on the negotiated signature algorithm. Pinning only the ECDSA leaf would brick the feature within weeks or on an RSA negotiation. Mitigation: pin both intermediates (primary) and both roots (backup, valid to 2028); fallback to the regex parser keeps the app usable if all pins fail; the `realApiTest` smoke test validates the pin set against the live host.
  - **Key exposure:** the key goes in the `x-goog-api-key` header, never the URL (`GeminiExtractorTest` asserts `url.query == null`), so it cannot leak through proxies' access logs or OkHttp URL logging. No logging interceptor exists; exceptions carry only HTTP codes. `BuildConfig.LLM_API_KEY` is still extractable from the APK (documented in README; a store release should proxy through a backend).
  - **Prompt injection:** receipt text is wrapped in `<receipt>` tags and declared as data; `responseSchema` + `responseMimeType=application/json` constrain the output shape; the output is still treated as untrusted (repair → parse → validate, every number range-checked).
  - **Hostile model output:** prose instead of JSON, fenced JSON, trailing commas, smart quotes, `NaN`, truncated JSON, comments, 600 kB blobs, `1e40` amounts, negative amounts, 250 items, blank names, qty 0 / 5000 (`JsonRepairTest`, `ReceiptJsonParserTest`, `ReceiptValidatorTest`, `ParserStressTest.10000 malformed json blobs`).
  - **Network abuse:** 400/401/403/404/429/5xx/timeout/dropped socket/malformed body each mapped to a typed `LlmException`; 4xx and malformed bodies are never retried (no amplification); retries use full-jitter backoff and honour `Retry-After` (capped at 8 s so a hostile `Retry-After: 999999` cannot freeze the UI).
  - **Hostile OCR into the regex parser:** 11-digit amounts, negative discounts, GSTIN/phone/date lines, unicode names (`RegexReceiptParserTest`); 10,000 synthetic receipts (`ParserStressTest`).
  - **Review screen:** every field re-validated on save (name, qty 1..999, amount, tax/tip/total), 200-item cap, rapid triple save, process death mid-edit (`ReviewViewModelTest`).
- What broke:
  - **Medium:** the first `JsonRepair` cut the text at the *last* `}`, which silently dropped the trailing items of a truncated response (the LLM output limit case) instead of closing them; found by `closes truncated json`.
  - **Medium:** the validator re-derived a unit price from a line total that contradicted the stated unit price (inventing data); changed to "unit unknown" so the user sees the gap.
  - **Low:** `Retry-After` was initially honoured uncapped.
- Fixed now:
  - Walker stops at the first balanced top-level object and closes truncated structures (regression test `closes truncated json`).
  - `ReceiptValidator` sets `unitPaise = 0` + `ITEM_MATH_FIXED` when the stated unit contradicts the total (`item math is repaired towards the line total`).
  - `BackoffPolicy.delayMs` caps `Retry-After` at `maxDelayMs` (`retry-after wins over the computed delay but is capped`).
- Scope for improvement:
  - The API key lives in the APK (`BuildConfig`); production should call Gemini through a backend or Firebase App Check-gated proxy.
  - Pin expiry: both roots expire 2028-01-28; schedule a pin refresh (and keep the regex fallback) before then.
  - Receipt text is sent to Google; a privacy notice in the UI/onboarding would be appropriate for a store release.

## Feature: Split calculator (equal / by item / by share, proportional tax + tip, debt simplification)
- Attacks tried:
  - Money math: 2,000 random weight vectors (zeros, huge weights up to 10^5, totals to ₹1 lakh) and 1,000 random bills across all three modes must sum exactly to the paisa and never deviate from the ideal share by a full paisa (`LargestRemainderTest.property`, `SplitCalculatorTest.property`); totals up to ₹1 crore with 1,000 weights of 10^9 (BigInteger arithmetic, no overflow).
  - Hostile configurations: no participants, duplicate participants, all-zero shares, negative shares, assignees who are not participants, empty assignment sets, items beyond the bill, a printed total smaller than the items (discount) or larger (rounding) (`SplitCalculatorTest`).
  - Share text from the UI: letters, blanks, negatives, 6+ digits (`SplitViewModelTest`); only the ratio of parsed non-negative integers is used.
  - Debt simplification: unbalanced balances (rejected), 1,000 random balance vectors always settle with ≤ n − 1 transfers (`DebtSimplifierTest.property`); 1,000 members × 1,000 bills (`SplitStressTest`).
  - Persistence: shares row deleted behind the bill's back (member removed) → the bill reports "no split" instead of violating the invariant; a bill whose stored shares do not sum to its total can never be constructed (`Bill.init`, `BillSplitDaoTest`).
  - Double tap on Save split; process death mid-edit (config, share texts and payer in SavedStateHandle).
- What broke:
  - **Low:** the first draft of the by-weights path derived item shares as "total − tax − tip", which could produce a −1 paisa item share for tiny bills; now items, tax and tip are each apportioned independently and the adjustment line absorbs rounding, so every component is ≥ 0 and the four component sums equal the bill's components exactly.
  - **Low (test bug):** an expected value in the largest-remainder test was wrong (429/286/285 for 1000 over 3:2:2); the correct Hamilton result 428/286/286 is now asserted together with a second worked example.
- Fixed now:
  - Independent apportionment per component with `adjustmentPaise` carrying rounding and printed-total differences (`SplitCalculatorTest.a total that does not add up…`).
  - `Bill` invariant: shares non-empty, unique members, sum == total; `BillWithItems.toSplit()` returns null for inconsistent rows (`BillSplitDaoTest.shares are cascade deleted…`).
  - Group balances are computed inside `runCatching` so a corrupt split can never take the group screen down.
- Scope for improvement:
  - Shares that reference a deleted member fall back to "Former member" in the UI; a background cleanup could re-split such bills automatically.
  - Percentages are not required to sum to 100 (only ratios matter); the UI could show the normalised percentage next to each share.

## Feature: One-tap UPI settle-up links (`upi://pay` per debt, share as text / QR)
- Attacks tried:
  - **Tampered / hostile payee data into the link builder:** VPAs with spaces, `javascript:` scheme, `?am=1` and `&pa=evil@upi` smuggling; names and notes containing `& = ? # % ' " < >`, NUL, bidi override, newlines, 500-char notes; amounts of 0, negative, `Long.MIN/MAX`, above ₹1 crore (`UpiLinkBuilderTest`); 10,000 random hostile VPA/name strings (`UpiStressTest.hostile…`): every value that survives is RFC 3986 percent-encoded, so no raw reserved character can create a second `pa=` or `am=` parameter, and every rejection is a typed `IllegalArgumentException`.
  - **Tampered links into the parser / launcher:** `http://`, `javascript:`, `upi://collect`, missing `am`, negative / exponent / >8-digit amounts, `cu=USD`, duplicate `pa`, unknown `url=` parameter, bad `tr`, malformed percent escapes (`parse rejects tampered or foreign links`); the Android launcher refuses to start any intent whose data does not parse as a well-formed INR `upi://pay` link (`AndroidUpiAppLauncherTest.only upi scheme links are ever launched`).
  - **Intent handling:** no UPI app installed → `NoUpiApp` without starting anything; a chooser is always used (no silent default app); `FLAG_ACTIVITY_NEW_TASK` from the application context; share uses `ACTION_SEND text/plain` through a chooser.
  - **QR payload:** the rendered QR decodes back to exactly the link (`QrCodeGeneratorTest`), quiet zone enforced, content capped at 2,000 chars; the QR is generated from the validated link only.
  - **Inline UPI-ID entry:** validated with `VpaValidator` and lowercased before storage; invalid input never reaches the member row.
  - **Response trust:** the app never marks a debt as paid because a UPI app returned — there is no PSP status check available to a client-only app, so settlement confirmation stays manual (see improvement).
- What broke:
  - **Medium (found by the fuzz):** a payee name containing an unpaired UTF-16 surrogate (a split emoji from a bad paste or OCR) passed validation, was UTF-8-encoded as `?` and therefore produced a link whose parse differed from the object that built it (the launcher would then have refused our own link). The sanitiser now drops unpaired surrogates and keeps valid pairs (`UpiLinkBuilderTest.unpaired surrogates are dropped so build and parse agree`).
  - **Low:** the first fuzz generated only invalid VPAs (10,000 rejections, 0 links built), i.e. it tested nothing about encoding; it now builds valid VPAs with hostile names for half the inputs (4,977 links built, all round-tripped).
  - **Low:** `VpaValidator` required at least two characters before `@`; single-character handles are legal and were rejected (found by the link tests; relaxed to one).
  - **Low:** the first ViewModel tests advanced virtual time past the `WhileSubscribed` window and read stale state; test bug, fixed (no production change).
- Fixed now:
  - Validator minimum length (ValidatorsTest still rejects empty user parts and `@ybl`).
  - Launcher validates with `UpiLinkBuilder.parse` before `startActivity` (`only upi scheme links are ever launched`).
- Scope for improvement:
  - Settlement is not recorded automatically (no PSP callback exists for a client-only app); a "mark as paid" that stores a settlement entry would let balances reach zero inside the app.
  - A merchant-style `tr` (transaction reference) per debt would help reconcile payments if a PSP status API were added later.
  - The share text includes names and the amount by design (the user chooses the recipient); nothing is sent anywhere by the app itself.

## Feature: Firebase Crashlytics + Analytics (success metrics, custom keys, non-fatals)
- Attacks tried:
  - **PII leakage through telemetry:** every event parameter and custom key goes through `AnalyticsPolicy`: keys must be snake_case and must not contain `name`, `vpa`, `upi`, `email`, `phone`, `mobile`, `amount`, `total`, `paise`, `address`; string values are capped at 100 chars and rejected when they contain `@` or ten or more digits (phone numbers). Tried `asha@okicici`, `someone@example.com`, `9876543210`, `+91 98765 43210`, 101-char strings, keys `member_name`, `payee_vpa`, `amount_paise` (`AnalyticsPolicyTest`). 10,000 random events: none carries `@` or a 7+ digit run (`AnalyticsStressTest`).
  - **Receipt text in crash logs:** `FirebaseCrashReporter.log` scrubs `\S+@\S+` and digit runs ≥ 7 and caps at 1,000 chars; the hooks never log OCR text or item names (`AnalyticsHooksTest` asserts no "Paneer" anywhere; `FirebaseAdaptersTest.a pii-looking custom key never reaches crashlytics`).
  - **Amounts tied to names:** events carry counts (`item_count`, `member_count`, `line_count`, `text_length`), enums and booleans only; no amount parameter exists at all.
  - **Clean clone without Firebase:** `google-services.json` absent → plugins not applied, `BuildConfig.FIREBASE_CONFIGURED=false`, no-op tracker/reporter bound; nothing touches `FirebaseApp` (Hilt tests replace the module with recording fakes, so the Robolectric graph never initialises Firebase).
  - **Hostile event volume:** 10,000 concurrent events through the policy (synchronized recording list in tests; the Firebase SDK batches on its own).
- What broke:
  - **Medium (caught by the policy itself):** my first `settle_up_tap` parameter was named `payee_has_vpa`; the PII gate rejected the word `vpa` and five tests failed. Renamed to `payee_has_id`. This is exactly the failure mode the gate exists for: a boolean about a UPI ID is fine, a key that *looks* like it could carry one is not.
- Fixed now:
  - Key rename (`SettleUpViewModelTest` asserts `payee_has_id`).
  - Non-fatals are recorded only for unexpected failures (recogniser/database crash, LLM transport or malformed answers) and never for expected outcomes (no text, no API key), so Crashlytics is not flooded with user conditions (`AnalyticsHooksTest`).
- Scope for improvement:
  - Analytics collection is enabled in debug builds too (useful to verify the console); gate it with `setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)` for a store release.
  - Firebase receives device identifiers by design; a consent screen would be needed in some jurisdictions.
  - Custom keys are process-wide in Crashlytics; `group_size`/`item_count` reflect the last operation, not the crashing one, if several run concurrently.

## Feature: Performance (Baseline Profile + Macrobenchmark modules, LeakCanary, StrictMode, work off the main thread)
- Attacks tried:
  - **Debug tooling leaking into release:** LeakCanary is `debugImplementation` only; StrictMode is installed only when `BuildConfig.DEBUG` (`PerformanceSetupTest.strict mode is installed only for debuggable builds`); the `benchmark` build type is non-debuggable, R8-minified and signed with the debug key — it must never be shipped (documented).
  - **ANR / main-thread abuse:** OCR, LLM and persistence paths proven to run on `Dispatchers.Default`/`IO`, never on the caller (`MainThreadGuardTest`), and 1,000 concurrent extractions never touch the calling thread (`OffMainStressTest`).
  - **StrictMode policies:** disk reads/writes, network and custom slow calls on the main thread; activity leaks, leaked closables and leaked registrations; `detectFileUriExposure` (would flag any `file://` URI handed to another app; the app only shares `content://` FileProvider URIs).
  - **R8 keep rules:** building the `benchmark` variant (R8 on) with the Firebase, OkHttp and kotlinx-serialization rules checks that nothing needed at runtime is stripped at compile time; runtime verification needs a device.
  - **Benchmark modules as attack surface:** `com.android.test` modules are separate APKs that only exist in developer builds; they are not part of the app APK.
- What broke:
  - Nothing at the JVM level. `StrictMode` penalties are `penaltyLog`, deliberately not `penaltyDeath`, so a tester's debug build never crashes on a violation.
- Fixed now:
  - n/a (no Critical/High).
- Scope for improvement:
  - Run the generator and benchmarks on a device and commit `baseline-prof.txt`; until then cold start has no profile.
  - Consider `penaltyDeath` for disk-on-main in CI-driven instrumented runs once a device is available.
  - LeakCanary heap dumps live in the debug app's files dir; nothing to do for release.

## Polish: fast Gemini model, pins verified on a device, provisional draft, AI error mapping, UPI visibility (2026-10-30/31)
- Attacks tried:
  - **Model switch:** `gemini-3.1-flash-lite` with a fallback list. A 404 only moves to the next *listed* model (each at most once per extraction, no loop); a cached model id that is not in the compiled list is ignored, so a tampered DataStore value cannot point the app at an arbitrary model or path (`a cached id that is no longer in the list is ignored`). The model id is a path segment built from constants only.
  - **Key handling during the measurements:** the key was read from `local.properties` inside a Python process and sent only in the `x-goog-api-key` header; it was never printed, echoed, logged or written to a file. `git grep -F <key>` and the diff check stayed empty before every commit. Release builds still carry the key in `BuildConfig` (extractable from the APK); documented, unchanged.
  - **Pins from a real device:** every in-app call on the emulator used `LlmHttpClient.create(GeminiPins.pinner())` and succeeded (HTTP 200, `llm_parse_success` in logcat), so the pin set matches what Android's TLS stack sees, not only the JVM. Live chains re-fetched with openssl on 2026-10-31 (ECDSA: leaf → WE2 → GTS Root R4; RSA: leaf → WR2 → GTS Root R1): all four SPKI pins unchanged.
  - **Pin mismatch behaviour:** before this change a pin failure surfaced as an `IOException` → "network" and was retried 4 times with backoff. Now `SSLException` (incl. `SSLPeerUnverifiedException`) becomes `LlmException.Insecure`: never retried, recorded as a non-fatal, shown as "certificate did not match the pinned keys, so nothing was sent" (OkHttp checks pins before writing the request; `PinningTest` asserts `requestCount == 0` and no backoff sleeps).
  - **Provisional draft:** the on-device parse shown while the AI runs is the same validated regex result as the fallback (no new trust); it is not persisted until the user edits it; AI output never overwrites user edits (offered instead); saving cancels the in-flight call so a late answer cannot change a saved bill.
  - **Error mapping:** 401/403 (bad key) and a missing key never offer "Try AI again" (pointless and it would spam the API); 429 shows the server's `Retry-After` (capped by `BackoffPolicy`); offline (`UnknownHostException`) is not retried and not a non-fatal; error detail strings from the server are truncated and control-character-stripped and never contain the key.
  - **UPI package visibility:** no `<queries>` meant Android 11+ hid every UPI app from `queryIntentActivities`, so the app always answered "no UPI app" on a real phone. Added `VIEW upi://pay` (scheme + host only, no broad `QUERY_ALL_PACKAGES`); `UpiPackageVisibilityTest` guards it. Links are still parsed by `UpiLinkBuilder` before launch and go through the system chooser.
  - **Analytics on a device:** logcat (debug-only Firebase verbose logging) showed `llm_parse_success`, `llm_parse_failure` (`failure=offline`) and `settle_up_tap` with counts/enums only; no names, UPI IDs, amounts or receipt text.
- What broke:
  - **Medium (functional, fixed):** missing UPI `<queries>` (above).
  - **Low (fixed):** pin failures were misreported as network errors and retried.
- Fixed now:
  - `LlmException.Insecure` / `Offline`, `<queries>` + test, model allow-list for the cache.
- Scope for improvement:
  - Move the Gemini call behind a small backend (key off the device, server-side quota per user).
  - `gemini-flash-lite-latest` is an alias whose target can change; it is only the second fallback.
  - The "your name" match is by display name; a member id picked once would be sturdier.

## UI Feature: Design system + catalog
- Attacks tried:
  - 10 and 1,000 rapid taps on a `ChunkyButton`; a button whose action sets `loading`; a disabled button (`ButtonsUiTest`, `DesignSystemStressTest`). One million same-instant taps through `ClickGuard`.
  - Double tap on Undo; a second message arriving while an undo is on screen; the caller cancelled mid-undo (`SnackbarStateTest`, `OverlaysUiTest`).
  - A locked bottom sheet: scrim tap, back press and drag while `dismissible = false` (`OverlaysUiTest`).
  - Names with bidi overrides and isolates (`U+202E`, `U+2066`), control characters, newlines, a 10,000-character name and emoji-only names in the top bar, list rows, avatars and avatar-stack descriptions (`DesignSystemAttackTest`, `DisplayTextTest`, `AvatarLogicTest`).
  - Reduced motion: does any path skip a step? Undo window and Undo action under reduced motion (`DesignSystemAttackTest`).
  - Debug catalog reachable in release: merged release manifest and release dex searched for the catalog; `CatalogReleaseIsolationTest` keeps `src/main` and `src/benchmark` free of it.
  - Screenshot baselines and catalog samples checked for real-looking keys, names or UPI IDs (`catalog_samples_hold_no_real_looking_secrets`; sample people are invented, the sample UPI handle is fictional).
  - Bottom sheet as a separate window: it is drawn in the screen's own window, so it cannot outlive the screen or carry different window flags.
- What broke:
  - Nothing Critical or High.
  - **Low (fixed):** a member with an empty name produced "Asha, Ravi and " for TalkBack; blank names now read as "Someone".
  - **Low (by design, noted):** the catalog activity is `exported` in debug builds so it can be opened with `adb`; it shows sample data only and does not exist in release.
- Fixed now:
  - `ClickGuard` (one action per 500 ms) in every button and clickable card; `loading` ignores taps; result: 1,000 injected taps on a settle button = 1 settlement, 1 haptic.
  - `DisplayText.safe` strips bidi controls, zero-width space, BOM and control characters, collapses whitespace and cuts at 120 code points on a code-point boundary; used by rows, the top bar, avatars and stack descriptions.
  - Snackbar results resolve exactly once; the undo window stretches to the system's recommended timeout when TalkBack is on, and reduced motion never shortens it.
- Scope for improvement:
  - Settle and Pay actions in later rows should also refuse touches while the window is obscured (`filterTouchesWhenObscured` on the root view); the design system does not set it globally because most taps are harmless.
  - `DisplayText` keeps zero-width joiners for emoji sequences, so a name can still contain invisible joiners between letters; harmless for layout, noted for search.

## UI Feature: Shell, splash and intro
- Attacks tried:
  - A crafted launch intent: `MainActivity` is exported, so another app started it with `ACTION_VIEW`, a `data` URI and Navigation's own explicit deep-link extras (`deepLinkIds` naming the review screen by its route-hash id, empty `deepLinkArgs`, a nested `deepLinkIntent`) to open a screen out of order or crash it with missing arguments (`ShellFlowTest`, `LaunchIntentPolicyTest`).
  - Tampering with the intro flag: a string and an int written under `onboarding_done`, an unreadable store, a store that never answers, a late answer arriving after the app is already open (`OnboardingRepositoryTest`, `AppStartTest`).
  - Skipping the intro to reach anything protected: SplitSmart has no lock or protected screen, and the intro gates nothing; checked that a deep link cannot skip it either (the launch intent is cleaned first, and the app is not composed while the intro shows).
  - Rapid taps: 10 and 1,000 taps on Skip and on "Let's split a bill" (one finish, one stored write), 1,000 taps on Next (lands on the last page, never finishes), 1,000 `finish()` calls on the start ViewModel (one write) (`OnboardingScreenTest`, `ShellStressTest`, `AppStartTest`).
  - Back and predictive back mid-intro: back walks pages and leaves the app only from page one; a cancelled predictive gesture stays on its page (`OnboardingScreenTest`). Replaying the intro from Me and leaving it returns to Me with the app's state intact (`ShellFlowTest`).
  - Process death during the intro: the stored flag is written only on finish, so a killed intro simply shows again; the pager page is saveable.
  - The name editor on Me: bidi override in the name, a 10,000-character paste, blank names (`MeViewModelTest`); names are shown through `DisplayText.safe` by the design-system rows.
  - Honesty: the intro and Me say AI reading needs a Gemini key, a built-in parser covers without one, scanned text is sent to Gemini only when a key is present, and Pay opens the UPI app without SplitSmart marking anything paid (`OnboardingScreenTest`, `MeScreenTest`). Me shows whether a key exists, never the key.
  - Screenshot baselines checked for real names, UPI IDs or keys: sample people and groups are invented.
- What broke:
  - **Medium (fixed):** before this row Navigation would act on a crafted launch intent's explicit deep-link extras. With no lock in SplitSmart the worst case was opening a screen out of order or crashing it with missing arguments (a nuisance, not a data leak). The launch intent is now replaced by a plain launcher intent before Navigation reads it, and later intents are cleaned the same way.
  - **Low (fixed):** a stored intro flag of the wrong type could have crashed a typed read; the raw map is read and only a real `true` counts.
  - Nothing Critical or High.
- Fixed now:
  - `LaunchIntentPolicy` (component kept, action MAIN, no data, no extras) in `onCreate` and `onNewIntent`, with a Robolectric attack test on the real activity.
  - `DataStoreOnboardingRepository`: tolerant read, IOException = done, failed write dropped. `AppStartLogic`: only the first answer decides, 2 s cap.
  - One finish per intro (screen guard + ViewModel guard); bar Scan ignores taps until groups are known rather than guessing.
- Scope for improvement:
  - `filterTouchesWhenObscured` for settle actions (row 13), as the design-system review noted.
  - If SplitSmart ever adds deep links (a shared settle-up link), they should be parsed by a dedicated allow-list like PayFlow's `IncomingIntentPolicy`, not by Navigation's generic deep-link handling.

## UI Feature: Groups home and group screen
- Attacks tried:
  - Destructive actions: one tap on delete (before this row it deleted the group, its bills and members at once); 5 taps on the confirmation within a second, 1,000 confirmations through the view model, a failed delete followed by a retry (`GroupDetailScreenTest`, `GroupDetailViewModelTest`, `GroupsStressTest`).
  - Archive storms: a swipe plus stray taps, 1,000 archive requests for one group, 1,000 archive/undo cycles, a failing write mid-swipe (the card must come back), archive through TalkBack's actions menu (`GroupsViewModelTest`, `GroupsScreenTest`, `GroupsFlowIntegrationTest`, `GroupsStressTest`).
  - Rapid taps on the FAB menu: taps landing on an item while the menu closes (13 to 100 taps landed), 1,000 attempts in the stress run (`GroupsScreenTest`, `GroupsStressTest`).
  - Hostile names: a bidi override and a 10,000-character group name on the home card and in the hero's greeting; member names in the faces row, the member sheet, the payer chips and the debt sentences all pass through `DisplayText.safe` (`GroupsScreenTest`, plus the design-system attack tests).
  - Huge amounts: ₹99,99,99,999.99 in the hero at 1.3× text; 2,000 bills or debts at the ₹1 crore cap summed in the group hero (saturates at the odometer's ceiling instead of wrapping negative) (`GroupsScreenTest`, `GroupDetailLogicTest`).
  - Process death: the add-expense entry offers its bill sheet once and not again after a restore; an archive survives (it is written before the Undo snackbar shows); open sheets keep their drafts in `SavedStateHandle` as before (`GroupDetailViewModelTest`).
  - Overlays: in-window sheets cannot outlive their screen; with a sheet or the menu up, the page behind is hidden from TalkBack and the floating bar steps aside, so no stray tap reaches the page or the bar.
  - Migration: a v3 database with groups and members opens on v4 with every group active (`GroupArchiveDaoTest`).
  - Screenshot baselines checked for real names, UPI IDs or keys: sample people, groups, cafés and UPI IDs are invented.
- What broke:
  - **Medium (fixed):** deleting a group took one tap on an icon beside the title, with no confirmation and no undo, and removed every bill in it. Now behind the options, with a confirmation sheet, single-shot in the view model.
  - **Low (fixed):** the floating bar was drawn over in-window sheets on tab screens, covering their buttons (Me's Save, the New group button); a tap there went to the bar. The bar now hides while a sheet or menu is up.
  - **Low (fixed):** removing a member was a 36dp × beside their name; it is now inside the member's sheet, with what removal does spelled out.
  - Nothing Critical or High.
- Fixed now:
  - Delete confirmation sheet + `GroupDetailViewModel.deleteGroup` guard (`deleting` state) with tests; archive as the undoable alternative.
  - `HideShellBarWhile` + `ShellLogic.barVisible(route, modalOpen)` with tests; applied to Groups and Me.
  - One action per FAB menu opening; one archive write in flight per group.
- Scope for improvement:
  - `filterTouchesWhenObscured` for settle actions remains row 13's job; nothing on these two screens moves money.
  - A pending-delete with undo would need a soft-delete column and a purge job; not worth it while archive exists.

## UI Feature: Scan, OCR, AI reading and receipt review
- Attacks tried:
  - Shutter storms: 10 taps on the shutter inside its tap window, 1,000 calls to `capture()` through the view model, 100 injected taps on the real shutter of the real route (`ScanScreenTest`, `ScanViewModelTest`, `ScanReviewStressTest`).
  - Save storms: 10 taps on Save bill, then taps while it is saving; 1,000 saves through the view model (`ReviewScreenTest`, `ScanReviewStressTest`).
  - What the camera keeps: live recognition returns only rectangles (`LiveTextBox` has four floats and no other field, checked by reflection in `LiveTextTest`); nothing is logged; analysis stops at the shutter and when the screen leaves.
  - Hostile receipt text: a right-to-left override inside a line the OCR or the AI returned, shown in a review row and its TalkBack sentence, and in the saved-scan slip (`ReviewScreenTest`, `ScanScreenTest`).
  - Invented items: while the bill is read the screen shows placeholders only, hidden behind one progress node; no row exists until a reader produced it (`ReviewScreenTest`).
  - Honesty of the AI path: every failure kind still lands on the parser's reading with a reason; no retry is offered for a missing or rejected key; a late AI answer is offered, never forced (`ReviewScreenTest`, `ReviewFlowIntegrationTest`).
  - Process death: a finished scan restored from its saved draft does not open the review again; a restored review form has no reveal to replay (`ScanViewModelTest`, `ReviewViewModelTest`).
  - Back mid-capture and mid-reading: the preview stays composed through the shutter, so the camera is not unbound while a picture is taken; Back stays bright and active over the reading card.
  - Reduced motion: the flash still marks the shutter (a dim instead of a cream flash), the reveal shows every row at once; no step is skipped (`ScanScreenTest`, `ReviewScreenTest`).
  - Copy that promises privacy: the permission page, the reading card and the review were checked against what the code sends where.
  - Screenshot baselines checked for real names, receipts or keys: the sample restaurant and dishes are invented ("Hotel Marigold Bhavan"), the camera "photo" is painted.
- What broke:
  - **Medium (fixed):** the camera permission page said "Nothing is uploaded; text is recognised on this device." With a Gemini key the recognised text is sent to Gemini. It now says the photo stays on the phone and, with AI reading on, only the text goes to Gemini.
  - **Low (fixed):** the old scan screen replaced the preview with a spinner the moment the shutter was pressed, unbinding the camera while the picture was being taken (a capture could fail on a slow camera). The preview now stays composed until the reading is done.
  - **Low (fixed):** the old result page printed the raw OCR text without removing direction overrides; the saved-scan slip now passes each line through `DisplayText.safe`.
  - Nothing Critical or High.
- Fixed now:
  - Copy fixes with a test on the permission text; preview kept composed (tested: the fake preview is still there during Capturing and Recognizing); slip and rows cleaned with tests.
- Scope for improvement:
  - Live recognition's frame rate and battery cost need a device measurement (batched after row 14).
  - `filterTouchesWhenObscured` on Save bill is not set; saving a draft bill moves no money (row 13 handles settle actions).
  - Gemini still runs with the key in the APK; a backend proxy remains the real fix (unchanged from feature 3).

## UI Feature: Assign, split, bill and simplify debts
- Attacks tried:
  - Destructive actions: one tap on the bill's delete icon (before this row it deleted the bill, its lines and its split at once); Keep it then delete again; 1,000 taps on the confirmation with the clock paused; 1,000 `deleteBill()` calls on the view model (`BillDetailScreenTest`, `SplitUiStressTest`).
  - Rapid saves: 1,000 taps on Save split while the first save is in flight, and 1,000 `save()` calls on the view model (`SplitScreenTest`, `SplitUiStressTest`).
  - Saving a split that does not add up: percentages totalling 116 % or 80 % (the ratio would still split the bill, quietly wrong), all-zero shares, nobody in the split. Save stays off on the screen and the view model refuses with a reject haptic (`SplitViewModelTest`).
  - Drops onto the wrong target: a line dropped on nobody, on a face of someone outside the split (a stale or forged id), the same line on the same face twice (`SplitViewModelTest`, `SplitScreenTest`).
  - Stale state after process death: picked lines saved as ids are filtered against the bill's real lines on restore, so a removed line cannot be assigned (`SplitViewModelTest`).
  - Hostile names and lines: member and line names pass through `DisplayText.safe` in the faces, the rows, the drag pill, the receipt and the graph; share and percent fields hold six digits at most.
  - Money integrity of the picture: bill-by-bill IOUs and the simplified payments give every person the same net balance (`DebtGraphLogicTest`), and folding 200 people into "+N" changes the picture only, not the listed payments.
  - TalkBack and reduced motion: assigning without dragging (long-press pick or the Pick action, then tap a face), one sentence for the graph, confetti never the only signal (the "Every line has an owner" pill says it); the delete confirmation is the same with reduced motion.
  - Screenshot baselines checked for real names, UPI IDs or keys: the people, the beach shack, the wedding caterer and the trip are invented.
- What broke:
  - **Medium (fixed):** deleting a bill was one tap on the trash icon in the top bar, with no confirmation and no undo, and removed its lines and its split. It now opens a confirmation sheet ("Delete Beach shack dinner? Its lines and its split go too."), with a heavy haptic and a busy button; the view model already took only one delete.
  - **Low (fixed):** percent mode would have saved percentages that do not total 100 as plain weights. The preview is withheld until they total 100, the check says how much is left or over, and save is refused.
  - **Low (fixed):** an adjustment that was a service charge was labelled "Adjustment" next to discounts; it is now named for what it is (discount, other charges or round-off), so a reader can tell when the printed total is higher than the lines.
  - Nothing Critical or High.
- Fixed now:
  - `BillDetailScreen` delete sheet (`BillDetailTags.DELETE_SHEET/CONFIRM/KEEP`) with UI tests for the first tap, Keep it, the confirmation and 1,000 taps.
  - `SplitViewModel.save` rejects an incomplete split with a reject haptic; `SplitLogic.percentLeft`; `SplitLogic.assign` ignores non-participants and never toggles off.
  - `SplitLogic.adjustmentKind` and `BillDetailLogic.extras`.
- Scope for improvement:
  - A bill delete could offer Undo like archive does; today it cannot be undone (as for groups, a soft delete would be a second archive).
  - Settle-up (row 13) should check that a bill's split still matches its members before offering payments, as people can be removed after a split.

## UI Feature: Settle up, history and insights
- Attacks tried:
  - A forged or optimistic reply from a UPI app: the link is started without asking for a result (application context, chooser, `FLAG_ACTIVITY_NEW_TASK`, `AndroidUpiAppLauncherTest`), and coming back to the screen only asks the question; nothing is recorded until "Yes" (`SettleUpViewModelTest`: resuming records nothing; confirming while still away does nothing).
  - Tapjacking: real `MotionEvent`s with `FLAG_WINDOW_IS_OBSCURED` and `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` dispatched at Pay, Paid another way and Yes; the window filter is on only while settle-up shows and restored after; the action modifier alone drops a fully covered touch (`SettleUpScreenTest`). Remove this payment in the history carries the same modifier.
  - Rapid taps: 1,000 taps on Pay through the real view model and screen → 1 UPI link (`SettleUpTapStressTest`); 1,000 `pay()` calls → 1; 1,000 `confirmPaid()` → 1 record; 1,000 taps on Yes while recording → 1; 1,000 taps on Remove → 1 (`HistoryScreenTest`, `HistoryViewModelTest`).
  - Process death while the UPI app is open: the question is restored from the SavedStateHandle and records the amount that was opened, once (`SettleUpViewModelTest`).
  - A member removed while the question was open: no payment is recorded between people who are no longer both in the group, the question closes with a reject haptic.
  - Removed members and stale splits: bills whose share rows were dropped, a stranger's share, a payer who left; each is named and left out of the payments (`SplitCheckTest`, `SettleUpFlowIntegrationTest` through Room). A payment whose member left stops counting but stays in the history.
  - Money integrity: settlements keep every group's balances summing to zero (10,000 bills + 10,000 payments over 200 people), overpaying turns the debt around, a corrupt row (negative amount, unknown method) is skipped or read as "another way" (`SettlementDaoTest`).
  - Amounts: ₹99,99,99,999.99 in the hero and a card at 1.3× in a 316dp column; a debt over the UPI link's ₹1 crore cap offers no Pay and says why.
  - Hostile names: a right-to-left override and emoji in a payee name are cleaned by `DisplayText.safe` everywhere they are drawn (`SettleUpStressTest`).
  - Telemetry: `settlement_marked` carries the method and whether the group is square, nothing else; the policy test covers it.
  - Screenshots and fixtures: every UPI ID is a made-up handle (`asha@okfake`, `meera@fakebank`); the old settle-up tests and the UPI ID hint used a real bank's handle and were changed.
- What broke:
  - **Medium (fixed):** bills whose split lost a member (or whose payer left) silently dropped out of settle-up, so the payments shown could be wrong without a word. They are now named with the reason and a way to fix them.
  - **Medium (fixed):** no tapjacking protection on settle actions (row 10's open item). An overlay could have driven "Paid another way" → "Yes" and recorded a payment nobody made. Fixed at window and action level.
  - **Low (fixed):** a payee with a UPI ID and a debt over ₹1 crore showed "Add UPI ID" (the link could not be built, and the old card took that for a missing ID).
  - **Low (fixed):** the UPI ID hint suggested a real bank's handle; now `name@yourbank`.
  - Nothing Critical or High.
- Fixed now:
  - `SettleUpViewModel` (question in SavedStateHandle, single-flight record, fresh member check), `SplitCheck`, `Modifier.filterTouchesWhenObscured()` + `FilterObscuredTouches()`, `SettleUpItem.tooLargeForUpi`, history removal behind its sheet.
- Scope for improvement:
  - A real status check would need a payment service provider; without one, the person's word is the only source, and the UI says so.
  - Settle-up could show only the payments that involve "you" once "you" is a member id rather than a name match.
  - Removing a payment from the history has no undo (it can be marked again).

## UI Feature: Whole-app design QA (row 14)
- Attacks tried: build and configuration details in user-facing copy (the review card named `LLM_API_KEY`, `local.properties` and HTTP status codes); error pages that offered an action that could not work (Try again on a deleted bill) or none (settle-up and history after a failed read); the new retry on settle-up re-subscribing while a "Did it go through?" question is open; money emoji in screenshots implying another currency; real business names left in docs and test receipts.
- What broke: Low: the review card showed developer setup details and HTTP codes to every user of a build without a key. Low: two real chain names and a real mall name remained in docs and a parser fixture.
- Fixed now: the review card speaks in user words for no key, a rejected key and a pin mismatch (tests assert that `LLM_API_KEY` and `local.properties` do not appear there); the setup hint is shown only in Me, only when AI is off. Errors offer Try again only when reading again can help and Back when the item is gone. Settle-up's retry re-reads the group only; the open question lives in `SavedStateHandle` and is untouched (`SettleUpViewModelTest`). Names replaced with invented ones.
- Scope for improvement: the Me hint still names the build property; a store build would hide it behind `BuildConfig.DEBUG`.

## Device session (2026-11-10, emulator)
- Attacks tried: the app's process killed while a stand-in UPI app was in front during settle-up; a stand-in that reports "success" for a payment that was never made; a stand-in that answers instantly (no time spent away).
- What broke: nothing. The "Did it go through?" question came back after process death, the reported "success" recorded nothing, and only "Yes, mark as paid" did.
- Fixed now: no change needed.
- Scope for improvement: tapjacking with a real overlay app and a QR scanned by a second device were not tried on the emulator; the JVM tests with obscured `MotionEvent`s still cover the touch filter.

## UI Feature: Enhancement pass (row 17)
- Attacks tried:
  - Undo integrity: delete + Undo of a full group (archived, three people, a split bill with lines and shares, a draft bill, two payments, an unsaved scan) beside another group, comparing every row of all seven tables before and after, and the balances (`TrashDaoTest`); 20 cycles over a 200-person group with 2,000 split bills, 6,000 lines, 4,000 shares and 500 payments (`TrashStressTest`).
  - Half-states: a put-back that fails after the group, its people and bills were already written (a clashing payment id) must roll back to nothing; a bill whose payer was removed, or whose group was deleted, inside the window must not come back with a dangling or missing payer; Undo tapped twice must restore once (`TrashDaoTest`, `ShellUndoViewModelTest`: 1,000 Undo taps → 1 restore).
  - Process death inside the Undo window: the copy is in memory only, so the delete must stand after a restart, never a partial restore (`TrashDaoTest` reopens a file database).
  - A late Undo: after the window closed (timeout, swipe) or a newer delete replaced the offer, Undo must do nothing.
  - Torch: a camera without a flash unit (no button, `setTorch` ignored), toggles once the camera has gone (an error page), and that it never runs while the photo is being read on another page.
  - Save and scan the next one: a burst of both save actions saves one bill; the new camera carries only the group id and a flag, no bill data; the flag clears on the next picture.
  - Intro skip: an unreadable or slow database, and a returning user whose start must never wait on it.
  - Large text: the payee's name, UPI ID and amount on a payment card at 1.3×, 1.5× and 2×, since a cut UPI ID is what a person would fail to check before paying.
  - Tapjacking: no new money action was added; Pay, Paid another way and Yes keep `filterTouchesWhenObscured` (the settle-up card layout changed at 2× text, the action modifiers did not; the existing obscured-touch tests still pass).
- What broke:
  - **Medium (fixed):** at 1.3× text the payee's UPI ID on a payment card was cut with an ellipsis, and at 2× the "who pays whom" sentence broke mid-word, so the person could not read whom they were about to pay.
  - **Low (fixed):** a group card's balance was cut at 2× text ("you're owed ₹1,1…").
  - Nothing Critical or High. No delete path could leave a partial group or bill in any test.
- Fixed now: UPI ID and sentence wrap (and stack under the amount from 1.4×), card balances move to their own line, snapshot-and-restore in single Room transactions with ABORT on conflict, the single-slot `UndoCenter` with `take()` ending an offer exactly once.
- Scope for improvement:
  - The Undo copy is not encrypted at rest because it is never at rest; if it were ever persisted (to survive process death) it would hold names and UPI IDs and would need the same care as the database.
  - Undo of a removed member or a removed payment in the history is still not offered (both can be added or marked again).
