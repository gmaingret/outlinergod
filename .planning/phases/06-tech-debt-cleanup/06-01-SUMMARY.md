---
phase: 06-tech-debt-cleanup
plan: "01"
subsystem: ui, database
tags: [FractionalIndex, sort-order, Room, entity, tech-debt, kotlin]

requires:
  - phase: 03-android-setup
    provides: Room entity definitions (NodeEntity, DocumentEntity, BookmarkEntity)
  - phase: 04-android-core
    provides: DocumentListScreen with generateNextSortOrder, DocumentListViewModel
  - phase: 00-prototypes
    provides: FractionalIndex implementation

provides:
  - generateNextSortOrder using FractionalIndex.generateKeyBetween (returns "aV" for empty list)
  - Clean Room entities without dead syncStatus field
  - 4 new sort-order tests verifying correct FractionalIndex behavior

affects:
  - future sync correctness (consistent sort-order alphabet across documents and nodes)
  - any phase adding new entity fields (syncStatus is now gone)

tech-stack:
  added: []
  patterns:
    - "Option B entity field removal: drop Kotlin field, keep SQLite column - Room ignores unmapped columns, no migration needed"
    - "FractionalIndex.generateKeyBetween(null, null) returns aV (DIGITS[31], midpoint of 62-char alphabet)"

key-files:
  created: []
  modified:
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListScreen.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListScreenTest.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/db/entity/NodeEntity.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/db/entity/DocumentEntity.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/db/entity/BookmarkEntity.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/db/entity/EntitySchemaTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/mapper/FlatNodeMapperTest.kt
    - android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModelTest.kt
    - android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt

key-decisions:
  - "FractionalIndex.generateKeyBetween(null, null) returns aV not aP — DIGITS has 62 chars, midpoint index 31 = V (not P which is index 25). Plan comment was incorrect."
  - "Option B for syncStatus removal: remove Kotlin field, leave SQLite column. No schema version bump needed since Room ignores unmapped columns."

patterns-established:
  - "generateNextSortOrder: FractionalIndex.generateKeyBetween(maxSortOrder, null) for appending after max"
  - "generateNextSortOrder: FractionalIndex.generateKeyBetween(null, null) = aV for empty list"

duration: 8min
completed: 2026-03-03
---

# Phase 06 Plan 01: Tech Debt Cleanup — Sort Order + Entity Fields Summary

**Fixed document sort-order generation to use FractionalIndex (aV midpoint), removed dead syncStatus field from 3 Room entities with no schema migration**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-03T18:25:07Z
- **Completed:** 2026-03-03T18:32:49Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments

- `generateNextSortOrder` now uses `FractionalIndex.generateKeyBetween` so new documents use the same 62-char alphabet as nodes, preventing merge ordering issues across devices
- Dead `syncStatus: Int = 0` field removed from `NodeEntity`, `DocumentEntity`, and `BookmarkEntity` (SQLite column left in place, no migration needed)
- 4 new sort-order tests added confirming: empty-list returns "aV", single/multiple items return strictly greater keys, sequential creation maintains sort order

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix generateNextSortOrder to use FractionalIndex** - `a9e8143` (fix)
2. **Task 2: Remove dead syncStatus field from Room entities** - `c492f4c` (refactor)

**Plan metadata:** (docs commit follows this summary)

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListScreen.kt` - Added FractionalIndex import, replaced string concatenation sort-order logic
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListScreenTest.kt` - Added 4 new generateNextSortOrder tests
- `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/NodeEntity.kt` - Removed syncStatus field
- `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/DocumentEntity.kt` - Removed syncStatus field
- `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/BookmarkEntity.kt` - Removed syncStatus field
- `android/app/src/test/java/com/gmaingret/outlinergod/db/entity/EntitySchemaTest.kt` - Removed syncStatusDefaultsToZero test, removed "syncStatus" from expectedColumns
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/mapper/FlatNodeMapperTest.kt` - Removed syncStatus = 0 from fakeNode helper
- `android/app/src/test/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModelTest.kt` - Removed syncStatus = 0 from fakeDocument helper
- `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/documentlist/DocumentListViewModel.kt` - Removed syncStatus = 0 from createDocument() entity constructors

## Decisions Made

- **D13: FractionalIndex midpoint is "aV" not "aP"** — The plan's comment `// "aP"` was wrong. `DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ..."` (62 chars), `MID_CHAR = DIGITS[31] = 'V'`. Tests and code comments updated to reflect "aV".
- **D14: Option B for syncStatus removal** — Remove the Kotlin field, leave the `sync_status` SQLite column in place. Room ignores unmapped columns automatically. No `autoMigrations` entry or manual migration needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan comment "aP" was incorrect — actual FractionalIndex midpoint is "aV"**
- **Found during:** Task 1 (Fix generateNextSortOrder) — test `generateNextSortOrder_emptyList_returnsInitialKey` failed with `expected:<a[P]> but was:<a[V]>`
- **Issue:** Plan stated `FractionalIndex.generateKeyBetween(null, null) // "aP"` but the actual return value is `"aV"`. `DIGITS` has 62 characters, midpoint index is 31, `DIGITS[31] = 'V'` not `'P'` (which is at index 25).
- **Fix:** Updated test assertion from `assertEquals("aP", result)` to `assertEquals("aV", result)`. Updated code comment. Updated fakeDocument default sort order and related test comparisons from "aP"/"aPP" to "aV"/"aVV".
- **Files modified:** DocumentListScreen.kt, DocumentListScreenTest.kt
- **Verification:** All 8 DocumentListScreenTest tests pass including new sort-order tests
- **Committed in:** a9e8143 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug: incorrect plan assumption about FractionalIndex return value)
**Impact on plan:** Fix was necessary for test correctness. The actual behavior (using FractionalIndex midpoint) is correct and superior to the old "a0"/"concat 0" approach — the plan's intent was correct, only the specific expected string in comments/tests was wrong.

## Issues Encountered

- **BookmarkDaoTest.observeAllActive_excludesOtherUsers** (1 failure in full suite run) — This is the pre-existing flaky Robolectric/Room Invalidation Tracker race condition documented in STATE.md. It passes on immediate retry. Not caused by any changes in this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Sort-order fix is live: all new documents created via DocumentListScreen now use proper FractionalIndex keys compatible with node sort orders
- syncStatus field eliminated from entities — any future sync infrastructure can use proper dirty-tracking if needed
- Plan 06-02 (if any) can proceed immediately

---
*Phase: 06-tech-debt-cleanup*
*Completed: 2026-03-03*
