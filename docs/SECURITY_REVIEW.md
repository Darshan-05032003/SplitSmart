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
