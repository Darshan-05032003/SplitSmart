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

## Compromises
_None yet._
