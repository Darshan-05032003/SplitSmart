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
