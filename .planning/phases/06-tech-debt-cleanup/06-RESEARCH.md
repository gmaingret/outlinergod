# Phase 06: tech-debt-cleanup - Research

**Researched:** 2026-03-03
**Domain:** Android Room schema cleanup, fractional indexing, VERIFICATION.md process docs
**Confidence:** HIGH

---

## Summary

Phase 06 closes five non-blocking tech debt items from the v0.4 milestone audit. All items were directly verified against the live codebase. No external library research is required — every fix is intra-project: copy or adapt existing code, remove dead fields, write process stubs.

The most technically interesting item is item 1 (sort-order fix). The existing `FractionalIndex` object in `prototype/FractionalIndex.kt` is exactly what DocumentListScreen needs. The fix is: import it, replace the broken `generateNextSortOrder` function body with a single `FractionalIndex.generateKeyBetween(lastSortOrder, null)` call, and add a test.

Item 2 (syncStatus removal) is constrained: AppDatabase.version = 1 and has no migration callbacks, so a Room migration (ALTER TABLE DROP COLUMN or schema bump) is technically possible but risky given the VERIFICATION.md complexity of testing it. Removing the Kotlin field while leaving the SQLite column in place (Room ignores extra columns when `@ColumnInfo` is absent) is the safer path but requires updates to `EntitySchemaTest` which explicitly asserts `syncStatus` is in the property set. The cleanest path given the audit scope is: remove from entity + add a Room migration (schema version 2) + update entity tests. However, this is classified as "should" not "must" — the planner may choose to defer schema migration to a later phase and instead just annotate the field with a `@Deprecated` marker as a lower-risk alternative.

Items 3–5 are pure documentation tasks with zero code risk.

**Primary recommendation:** Fix item 1 (sort order) and items 3–5 (docs) in this phase. For item 2 (syncStatus), make a deliberate choice: either full Room migration (version bump, migration object, updated tests) or tag as `@Deprecated(level = DeprecationLevel.WARNING)` with a code comment and defer schema change. Do not remove the field silently without updating `EntitySchemaTest`.

---

## Standard Stack

No new dependencies required for this phase. All fixes use existing project infrastructure.

### Core (already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `FractionalIndex` | prototype/ (in-project) | Sort-order generation | Already implemented, tested, used by NodeEditorViewModel |
| Room | 2.8.4 | Android ORM — migration needed if field is removed | Project-mandated |
| Vitest | ^2 | Backend tests | Project-mandated |
| JUnit 4 + Mockk | project-defined | Android unit tests | Project-mandated |

**Installation:** None required.

---

## Architecture Patterns

### Item 1: Fix generateNextSortOrder in DocumentListScreen

**Current broken implementation (DocumentListScreen.kt line 387-392):**
```kotlin
internal fun generateNextSortOrder(items: List<DocumentEntity>): String {
    if (items.isEmpty()) return "a0"
    val maxSortOrder = items.maxOf { it.sortOrder }
    // Simple incrementing: append next character
    return maxSortOrder + "0"
}
```

**Problem:** Produces `a0` → `a00` → `a000`. These are valid lexicographic keys but diverge from the `FractionalIndex.DIGITS` alphabet (`0-9A-Za-z`). When documents from two devices merge, a server-created document with sort order `aP` (midpoint of FractionalIndex) and a client-created document with `a00` will sort incorrectly: `"a00" < "aP"` is true lexicographically, which happens to be correct, BUT `"a0" + "0" = "a00"` then `"a00" + "0" = "a000"` — if many documents are created on one device, they all cluster at the very beginning of the alphabet and conflict resolution degrades. The real issue is that FractionalIndex.DIGITS doesn't include '0' at index 0 — `DIGITS[0]` is `'0'`, so "a0" IS a valid key. The critical divergence: appending '0' does not satisfy the contract that a key after `x` is strictly greater than `x` using `generateKeyBetween(x, null)`.

**Correct fix:**
```kotlin
// Source: android/app/src/main/java/com/gmaingret/outlinergod/prototype/FractionalIndex.kt
import com.gmaingret.outlinergod.prototype.FractionalIndex

internal fun generateNextSortOrder(items: List<DocumentEntity>): String {
    if (items.isEmpty()) return "a${FractionalIndex.DIGITS[FractionalIndex.DIGITS.length / 2]}" // "aP"
    val maxSortOrder = items.maxOf { it.sortOrder }
    return FractionalIndex.generateKeyBetween(maxSortOrder, null)
}
```

**Key facts about FractionalIndex (HIGH confidence — read from source):**
- `DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"` (62 chars)
- `MID_CHAR = DIGITS[31] = 'P'`
- All keys have form `"a" + fractional-digits`
- `generateKeyBetween(before, null)` → `"a" + before_frac + "P"` (appends MID_CHAR = 'P')
- `generateKeyBetween(null, null)` → `"aP"` (initial document)
- First document ever created: use `generateKeyBetween(null, null)` = `"aP"` (not `"a0"`)
- Empty list case: return `"aP"` not `"a0"` to stay consistent with FractionalIndex

**Where generateNextSortOrder is used:**
- `DocumentListScreen.kt` line 108: `val sortOrder = generateNextSortOrder(items)` called in dialog confirm handler
- Definition at line 387 — this is the only definition, there are no other callers

**Test update needed:**
- `DocumentListViewModelTest.kt` line 149: `containerHost.createDocument("Doc", "document", null, "V")` — hardcoded sort order `"V"` is passed directly; test does NOT call `generateNextSortOrder`, so test still passes with the fix
- `DocumentListScreenTest.kt` has no test for `generateNextSortOrder` — must add one

### Item 2: syncStatus Dead Field Analysis

**Scope of syncStatus across the codebase:**

| File | Type | Usage |
|------|------|-------|
| `NodeEntity.kt` line 31 | Entity field `@ColumnInfo(name = "sync_status")` | Declared, never read except in constructors |
| `DocumentEntity.kt` line 25 | Same | Same |
| `BookmarkEntity.kt` line 28 | Same | Same |
| `DocumentListViewModel.kt` lines 181, 201 | Write only | `syncStatus = 0` hardcoded in DocumentEntity/NodeEntity constructors |
| `NodeEditorViewModel.kt` | Write only | Present in NodeEntity constructors as `syncStatus = 0` |
| `EntitySchemaTest.kt` line 78–82 | Test | Explicitly asserts `syncStatus` is in NodeEntity's property set |
| `EntitySchemaTest.kt` lines 122–131 | Test | `syncStatusDefaultsToZero()` tests all three entities |
| `FlatNodeMapperTest.kt` line 46 | Test | `syncStatus = 0` in fake NodeEntity construction |
| `DocumentListViewModelTest.kt` line 105 | Test | `syncStatus = 0` in fakeDocument() helper |

**ViewModel SyncStatus (ui/common/SyncStatus.kt) is DIFFERENT from entity syncStatus field:**
- `SyncStatus` (uppercase, enum) = `Idle/Syncing/Error` in ViewModel UiState. This is kept.
- `syncStatus: Int` (lowercase, entity field) = orphaned Room column. This is removed.

**AppDatabase version:** Currently `version = 1` (AppDatabase.kt line 23), `exportSchema = false`.

**Migration implications for removal:**

Option A — Full Room migration (recommended if removing the field):
```kotlin
// AppDatabase: version = 2
// Add migration callback in DatabaseModule
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // SQLite does not support DROP COLUMN before version 3.35.0
        // Android's bundled SQLite < 3.35 in most API levels
        // Must recreate table:
        database.execSQL("CREATE TABLE nodes_new (...all columns minus sync_status...)")
        database.execSQL("INSERT INTO nodes_new SELECT ...all columns minus sync_status... FROM nodes")
        database.execSQL("DROP TABLE nodes")
        database.execSQL("ALTER TABLE nodes_new RENAME TO nodes")
        // Same for documents, bookmarks if removing there too
    }
}
```

Option B — Safer: remove Kotlin field only, leave SQLite column (Room ignores extra columns):
- Remove `val syncStatus: Int = 0` from NodeEntity, DocumentEntity, BookmarkEntity
- Room will ignore the `sync_status` column in SQLite (it maps only declared fields)
- No schema version bump needed
- But `EntitySchemaTest.syncStatusDefaultsToZero()` and `nodeEntity_tableName_isNodes` tests will fail (they assert syncStatus is in the property set)
- Tests must be updated/deleted

Option C — Annotate as deprecated (lowest risk, defers removal):
- Add `@Deprecated("Dead field — never written to sync DTOs", level = DeprecationLevel.WARNING)` to the field
- No test changes, no schema migration
- Documents the issue without breaking anything

**Planner guidance:** The audit says "dead code" not "blocking." Given `exportSchema = false` and no shipped users, Option B (remove Kotlin field, leave SQLite column, update tests) is safe and clean. Option A (full migration) is correct engineering but heavier for a tech-debt cleanup phase. Option C is valid if the planner wants minimal risk. Choose B or C; avoid A unless explicitly desired.

### Item 3: VERIFICATION.md stubs for phases 01-03

**What exists:**
- `01-UAT.md`: All 5 tests remain `[pending]`. Backend code confirmed working (Phase 02 builds on it).
- `02-UAT.md`: 6/7 passed. 1 gap (deployment doc). Gap root_cause already documented in UAT file.
- `03-UAT.md`: 3/3 passed. Formally `status: complete`.

**What the VERIFICATION.md format looks like (phases 04 and 05 as reference):**

Phase 04 VERIFICATION.md:
- YAML front matter: `phase`, `verified`, `status`, `score`
- Sections: Observable Truths table, Required Artifacts table, Key Link Verification, Anti-Patterns, Human Verification
- Verdict paragraph with final score

Phase 05 VERIFICATION.md:
- YAML front matter: `phase`, `verified`, `status`, `score`
- Sections: Score table, Must-Haves table, Required Artifacts table, Key Link Verification, Requirements Coverage, Anti-Patterns, Human Verification, Gaps Summary

**Stub content rules for 01-03:**
- Phase 01: 5 tests pending — stub should note code is confirmed working but UAT was never formally run. Status = `code_confirmed_not_formally_verified`.
- Phase 02: 6/7 passed, 1 doc gap — stub should record this, note gap is documentation-only, code is correct.
- Phase 03: 3/3 passed — stub should archive passing results. Status = `passed`.

### Item 4: Backend deployment README

**Current state:** No README.md exists at project root or in `backend/`.

**Dockerfile analysis (HIGH confidence — read from source):**
- Stage 1 (builder): `corepack enable && pnpm install && pnpm run build` — TypeScript compilation
- Stage 2 (runner): Copies `dist/`, `node_modules/`, `drizzle/` — runs `node dist/server.js`
- `dist/` is NEVER committed to git (must be built inside Docker)
- pnpm is NOT installed on Docker host server (confirmed by 02-UAT.md gap)

**What the README must document:**
1. Dev path: `cd backend && pnpm install && pnpm dev` (requires local Node.js 20 + pnpm)
2. Production path: `docker compose up -d --build` from project root (only supported deployment)
3. Why `pnpm build` fails on the host: multi-stage Dockerfile, pnpm only in builder stage
4. Health check: `curl http://localhost:3000/health` returns `{"status":"ok",...}`
5. Environment: `.env` file required at project root (see `.env.example` or ARCHITECTURE.md)

**Location:** Create as `backend/README.md` (scoped to backend) or `README.md` at project root. The audit specifically says "Project-level README.md" — create at project root.

### Item 5: UAT test 16 (collapse/expand) — no code needed

**Audit finding:** UAT test 16 marked "unblocked" — needs manual follow-up.

**Verification status from Phase 04 VERIFICATION.md:** Test 16 (collapse/expand) is NOT mentioned in the 04-VERIFICATION.md. The 04-VERIFICATION.md only covers plans 04-02 through 04-05 (auth persistence, gesture fixes, settings wiring, gesture modifier order). UAT test 16 relates to a separate UAT file, not the VERIFICATION.md flow.

**Resolution:** This is explicitly labeled "deferred, no code needed" in the phase requirements. The planner task is only to document the deferral formally in the relevant UAT file or VERIFICATION.md stub.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Sort order for new document | Custom string concatenation (`maxSortOrder + "0"`) | `FractionalIndex.generateKeyBetween(lastSortOrder, null)` | Already implemented, tested, Rocicorp-compatible |
| Room schema migration boilerplate | Ad-hoc SQL | `Migration(1, 2)` object pattern (standard Room migration) | Room's migration API handles version tracking, applies in transaction |

**Key insight:** FractionalIndex already exists in the prototype package and is already imported in NodeEditorViewModel. DocumentListScreen can import it directly — there is no need to move or refactor it.

---

## Common Pitfalls

### Pitfall 1: Conflating entity syncStatus with ViewModel SyncStatus enum

**What goes wrong:** Developer sees `SyncStatus` referenced everywhere in ViewModels and thinks removing `syncStatus` field from entities will break sync. In reality:
- `SyncStatus` (enum, `ui/common/SyncStatus.kt`) = UI state indicator (Idle/Syncing/Error) — keep
- `syncStatus: Int` (entity field) = Room column that was meant for dirty-tracking — dead code, no DAO or sync DTO reads it

**How to avoid:** Check that `NodeDao`, `DocumentDao`, `BookmarkDao` query methods never reference `sync_status` column in SQL — confirmed: none do.

**Warning signs:** If a test starts referencing `entity.syncStatus` after removal, check whether it's the entity field (dead) or the ViewModel SyncStatus enum (alive).

### Pitfall 2: EntitySchemaTest will fail after syncStatus removal

**What goes wrong:** `EntitySchemaTest.nodeEntity_tableName_isNodes()` at line 78 explicitly asserts that `"syncStatus"` is in `NodeEntity`'s property set. `EntitySchemaTest.syncStatusDefaultsToZero()` directly accesses `node.syncStatus`, `doc.syncStatus`, `bookmark.syncStatus`.

**How to avoid:** Update `EntitySchemaTest` in the same commit as the entity change. Remove `"syncStatus"` from the `expectedColumns` set. Delete `syncStatusDefaultsToZero()` test. Update `FlatNodeMapperTest` and `DocumentListViewModelTest` `fakeDocument()`/`fakeNode()` helpers to remove `syncStatus = 0` constructor arg.

**Warning signs:** If `./gradlew test` reports compile errors in `EntitySchemaTest.kt`, the entity field was removed but the test was not updated.

### Pitfall 3: Wrong initial sort order for first document

**What goes wrong:** After fixing `generateNextSortOrder`, the empty-list case returns `"a0"` (FractionalIndex uses DIGITS[0]='0'). But `FractionalIndex.generateKeyBetween(null, null)` returns `"aP"` (midpoint). Returning `"a0"` is technically valid (it IS a DIGITS character) but diverges from what `generateKeyBetween(null, null)` would produce.

**How to avoid:** For the empty-list case, use `FractionalIndex.generateKeyBetween(null, null)` = `"aP"`. This is consistent: first document gets `"aP"`, subsequent documents get `generateKeyBetween("aP", null)` = `"aPP"`, etc. The test must assert `"aP"` not `"a0"` for the empty-list case.

**Note:** The existing `createDocument` root-node code in `DocumentListViewModel.kt` line 196 still creates nodes with `sortOrder = "a0"`. That is for the root node inside a document (not document sort order) and is acceptable as the initial sentinel for the first node in an empty document.

### Pitfall 4: SQLite DROP COLUMN not available on older Android

**What goes wrong:** If implementing Option A (full migration), `ALTER TABLE nodes DROP COLUMN sync_status` fails on Android with SQLite < 3.35.0 (which is most API levels below 33).

**How to avoid:** Use the table-recreate pattern (CREATE new, INSERT SELECT, DROP old, RENAME). This is the standard Room migration pattern for column removal.

### Pitfall 5: VERIFICATION.md stubs must not claim tests were run if they weren't

**What goes wrong:** Writing `status: passed` in Phase 01 VERIFICATION.md when 5 UAT tests are still `[pending]`.

**How to avoid:** Use a distinct status like `code_confirmed_unverified` or `legacy_unat_incomplete` for Phase 01. Phase 02 gets `code_confirmed_doc_gap`. Phase 03 gets `passed` (3/3 tests explicitly passed per UAT file).

---

## Code Examples

### Corrected generateNextSortOrder

```kotlin
// Source: prototype/FractionalIndex.kt (DIGITS + generateKeyBetween already verified)
import com.gmaingret.outlinergod.prototype.FractionalIndex

internal fun generateNextSortOrder(items: List<DocumentEntity>): String {
    return if (items.isEmpty()) {
        FractionalIndex.generateKeyBetween(null, null) // "aP"
    } else {
        val maxSortOrder = items.maxOf { it.sortOrder }
        FractionalIndex.generateKeyBetween(maxSortOrder, null)
    }
}
```

### Test for generateNextSortOrder

```kotlin
// In DocumentListScreenTest.kt or a new DocumentListSortOrderTest.kt
@Test
fun generateNextSortOrder_emptyList_returnsInitialKey() {
    val result = generateNextSortOrder(emptyList())
    assertEquals("aP", result) // FractionalIndex.generateKeyBetween(null, null)
}

@Test
fun generateNextSortOrder_singleItem_returnsKeyAfter() {
    val doc = fakeDocument(sortOrder = "aP")
    val result = generateNextSortOrder(listOf(doc))
    // generateKeyBetween("aP", null) = "aPP"
    assertTrue("Result must be > input", result > "aP")
    assertTrue("Result must start with 'a'", result.startsWith("a"))
}

@Test
fun generateNextSortOrder_multipleItems_returnsKeyAfterMax() {
    val docs = listOf(
        fakeDocument(sortOrder = "aP"),
        fakeDocument(sortOrder = "aPP")
    )
    val result = generateNextSortOrder(docs)
    assertTrue("Result must be > max", result > "aPP")
}

@Test
fun generateNextSortOrder_resultsSortCorrectly() {
    val orders = mutableListOf<String>()
    var last: List<DocumentEntity> = emptyList()
    repeat(5) {
        val order = generateNextSortOrder(last)
        orders.add(order)
        last = last + fakeDocument(sortOrder = order)
    }
    assertEquals(orders, orders.sorted())
}
```

### Room Migration Option B (remove Kotlin field, keep SQLite column)

```kotlin
// NodeEntity.kt — remove this line:
// @ColumnInfo(name = "sync_status") val syncStatus: Int = 0,

// No schema version bump needed. Room ignores unmapped columns.
// Update EntitySchemaTest.kt to remove "syncStatus" from expectedColumns set
// and delete syncStatusDefaultsToZero() test.
```

### VERIFICATION.md Stub Template (Phase 01)

```yaml
---
phase: 01-backend-foundation
verified: 2026-03-03
status: code_confirmed_unverified
score: 0/5 formally verified (UAT never run; code confirmed working by Phase 02 build)
note: Legacy UAT workflow — VERIFICATION.md not created at time of phase completion
---

# Phase 01: backend-foundation - Verification Stub

**Status:** Code confirmed working. Formal UAT (5 tests in 01-UAT.md) was never executed.
Evidence of working code: Phase 02 backend API was built directly on top of Phase 01 infrastructure
and all Phase 02 UAT tests pass (6/7, with 1 documentation gap).

## UAT Tests (01-UAT.md)

| # | Test | Result |
|---|------|--------|
| 1 | All backend tests pass | [not formally run — confirmed by P02 test suite passing] |
| 2 | Health endpoint responds | [not formally run — confirmed by P02 UAT test 7] |
| 3 | Database migrations run automatically | [not formally run — confirmed by P02 build] |
| 4 | Auth middleware rejects unauthenticated requests | [not formally run — confirmed by P02 UAT test 3] |
| 5 | Docker image builds | [not formally run — confirmed by P02 UAT test 6] |

## Verdict

Phase 01 code is in working order. Formal UAT was not executed at time of completion.
This stub closes the audit trail gap. No code changes required.
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Append `"0"` to max sort order | `FractionalIndex.generateKeyBetween(max, null)` | Phase 06 (this phase) | Correct Rocicorp-compatible key generation |
| No VERIFICATION.md for phases 01-03 | Stub VERIFICATION.md files | Phase 06 (this phase) | Complete audit trail |
| No deployment README | `README.md` at project root | Phase 06 (this phase) | Operators know only `docker compose up -d --build` works |

**Not changing in this phase:**
- FractionalIndex itself (already correct, in prototype package)
- NodeEditorViewModel sort order logic (already uses FractionalIndex correctly)
- SyncStatus enum (ui/common/SyncStatus.kt — alive and correct)
- Backend code (no tech debt items require backend changes)

---

## Open Questions

1. **syncStatus removal depth**
   - What we know: Field is dead in nodes, documents, bookmarks. Never read by any DAO query. Never included in SyncRecord DTOs.
   - What's unclear: Whether the planner wants full migration (Option A), field-only removal (Option B), or deprecation annotation (Option C).
   - Recommendation: Use Option B (remove Kotlin field, leave SQLite column, update tests). Simplest, zero migration risk, zero runtime impact. Document the decision in a code comment.

2. **generateNextSortOrder test location**
   - What we know: `generateNextSortOrder` is `internal` in `DocumentListScreen.kt`. Tests can access it from `DocumentListScreenTest.kt` (same package in test source set).
   - What's unclear: Whether to add tests to the existing `DocumentListScreenTest.kt` or create a new `GenerateNextSortOrderTest.kt`.
   - Recommendation: Add to existing `DocumentListScreenTest.kt` — file already exists and imports the necessary helpers. Keep the file focused on DocumentListScreen concerns.

3. **README scope — root vs backend/**
   - What we know: The v0.4 audit recommends "Project-level README.md."
   - What's unclear: Whether a root README should also cover Android build instructions.
   - Recommendation: Create `README.md` at project root covering both backend deployment and Android build paths. Keep it short (under 50 lines). Backend section is the primary driver.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `android/app/src/main/java/com/gmaingret/outlinergod/prototype/FractionalIndex.kt` — DIGITS alphabet, generateKeyBetween, generateNKeysBetween verified
- Direct code inspection: `android/app/src/main/java/.../ui/screen/documentlist/DocumentListScreen.kt` — current generateNextSortOrder (lines 387-392) and call site (line 108)
- Direct code inspection: `android/app/src/main/java/.../db/entity/NodeEntity.kt` — syncStatus field at line 31
- Direct code inspection: `android/app/src/main/java/.../db/AppDatabase.kt` — version = 1, no migration callbacks
- Direct code inspection: `android/app/src/test/.../db/entity/EntitySchemaTest.kt` — syncStatus assertions that must be updated
- Direct code inspection: `.planning/phases/04-android-core/04-VERIFICATION.md` — template for verification format
- Direct code inspection: `.planning/phases/05-sync-integration-fixes/05-VERIFICATION.md` — template for verification format
- Direct code inspection: `.planning/phases/01-backend-foundation/01-UAT.md` — UAT status: 5 pending
- Direct code inspection: `.planning/phases/02-backend-api/02-UAT.md` — UAT status: 6/7 passed, doc gap documented
- Direct code inspection: `.planning/phases/03-android-setup/03-UAT.md` — UAT status: 3/3 passed
- Direct code inspection: `backend/Dockerfile` — multi-stage build, pnpm in builder only
- Direct code inspection: `.planning/v0.4-MILESTONE-AUDIT.md` — all tech debt items enumerated

### Secondary (MEDIUM confidence)
- Android Room documentation (training knowledge): SQLite DROP COLUMN not available below API 33 / SQLite 3.35. Table-recreate pattern is the standard Room migration for column removal.

---

## Metadata

**Confidence breakdown:**
- FractionalIndex behavior: HIGH — read directly from source
- syncStatus dead-code scope: HIGH — verified no DAO queries reference sync_status column
- Room migration pattern: MEDIUM — training knowledge, Room migration API well-established
- VERIFICATION.md format: HIGH — read directly from two existing examples
- UAT test status for 01-03: HIGH — read directly from UAT files
- Dockerfile deployment path: HIGH — read directly from Dockerfile + 02-UAT.md gap documentation

**Research date:** 2026-03-03
**Valid until:** 2026-04-03 (stable codebase, no external dependencies to go stale)
