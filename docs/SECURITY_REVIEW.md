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
