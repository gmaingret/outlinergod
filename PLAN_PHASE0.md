## Phase 0 — Prototypes (HIGH RISK, must complete before Phase 1)

These three tasks are standalone throwaway proofs-of-concept. They prove out the three highest-risk technical interactions before any production code is written. They are independent and can be built in parallel by different engineers.

Do not proceed to Phase 1 until all three prototypes have passing acceptance tests.

---

### P0-1: Drag-and-Drop Prototype

- **Task ID**: P0-1
- **Title**: Flat-list DnD with horizontal reparenting gesture
- **What to build**: Create a standalone Android `Activity` (not integrated into the main app) containing a hardcoded tree of ~10 nodes rendered as a `LazyColumn` with `List<FlatNode>` where each `FlatNode` carries `id: String`, `content: String`, and `depth: Int`. Use `sh.calvin.reorderable:reorderable:3.0.0` with `longPressDraggableHandle()` for vertical reordering. Add a `pointerInput` modifier that measures the finger's X-offset relative to the item's left edge during drag to detect indent (move right past one depth level) or outdent (move left past one depth level) — threshold: 48 dp per depth level. On drag release, compute the new `parentId` and regenerate `sort_order` using the `fractional-indexing-jittered` algorithm. Render depth via `Modifier.padding(start = (depth * 24).dp)`.
- **Inputs required**: Android project skeleton, `sh.calvin.reorderable:reorderable:3.0.0`, `fractional-indexing-jittered` ported to Kotlin (or npm via a thin adapter for testing), Compose Foundation 1.9.x with `LazyColumn`, Hilt not required (hardcoded data only).
- **Output artifact**: `android/app/src/main/java/com/outlinegod/prototype/DndPrototypeActivity.kt` — fully self-contained, showing a working tree that reorders vertically and reparents on horizontal drag. A companion `DndPrototypeViewModel.kt` holding the flat-list state.
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/prototype/DndPrototypeViewModelTest.kt`
  - **Test cases**:
    1. `verticalReorder_updatesListOrder` — given nodes [A, B, C], drag B above A; assert resulting order is [B, A, C] and `sort_order` of B is lexicographically less than that of A.
    2. `horizontalDragRight_indentsNode` — given node B at depth 0 following node A at depth 0, drag B 50+ dp to the right; assert B's `depth` becomes 1 and `parentId` equals A's id.
    3. `horizontalDragLeft_outdentsNode` — given node C at depth 2, drag 50+ dp left; assert depth becomes 1 and `parentId` is C's former grandparent.
    4. `sortOrder_lexicographicInvariant` — after any reorder, assert `sort_order` values within each parent group are strictly ascending by lexicographic order (the tiebreaker rule: `ORDER BY sort_order ASC, id ASC`).
    5. `noChildrenLost_afterReparent` — move a node that has 2 children; assert children move with the parent and their `depth` values are updated by exactly the parent's depth delta.
  - **Pass criteria**: `./gradlew test` exits 0, all 5 cases green.
- **Risk**: HIGH — No production-ready nested tree DnD library exists for Compose. Horizontal-swipe reparenting requires custom `pointerInput` geometry that conflicts with `sh.calvin.reorderable`'s own gesture recognizer; the gesture disambiguation boundary must be tuned empirically. If unresolvable, the fallback is indent/outdent buttons only (no horizontal drag), which is a PRD-acceptable degradation.

---

### P0-2: WYSIWYG Formatting Prototype

- **Task ID**: P0-2
- **Title**: BasicTextField live inline Markdown with cursor stability
- **What to build**: Create a standalone Android `Activity` containing a single `BasicTextField` backed by `TextFieldState` (Compose Foundation 1.9.x). Apply a `VisualTransformation` that uses pre-compiled regexes to detect `**bold**`, `_italic_`, `` `code` ``, `~~strikethrough~~`, and `==highlight==` spans, and returns an `AnnotatedString` with the corresponding `SpanStyle` applied. Use `OffsetMapping.Identity` throughout (markers remain visible — this is Phase 1 scope only; marker-hiding is explicitly deferred). Compile all regexes once as top-level `val`s, not on each recomposition. The prototype must remain responsive while typing in a 2,000-character node with 20+ formatted spans.
- **Inputs required**: Android project skeleton, Compose BOM 2025.x (includes Foundation 1.9.x with stable `TextFieldState`), no third-party editor library.
- **Output artifact**: `android/app/src/main/java/com/outlinegod/prototype/WysiwygPrototypeActivity.kt` — single-screen prototype with one `BasicTextField`. A companion `MarkdownVisualTransformation.kt` containing the reusable `VisualTransformation` implementation and all regex patterns.
- **How to test**:
  - **Test file**: `android/app/src/test/java/com/outlinegod/prototype/MarkdownVisualTransformationTest.kt`
  - **Test cases**:
    1. `bold_appliesBoldSpanStyle` — input `"hello **world** end"`; assert the `AnnotatedString` produced by the transformation contains exactly one `SpanStyle(fontWeight = FontWeight.Bold)` spanning offsets 6..11 (`"world"`).
    2. `italic_appliesItalicSpanStyle` — input `"_hello_"`; assert italic span covers offsets 0..6.
    3. `code_appliesMonospaceStyle` — input `` "`code`" ``; assert span uses a monospace `FontFamily`.
    4. `strikethrough_appliesLineThrough` — input `"~~strike~~"`; assert `TextDecoration.LineThrough` on the inner text.
    5. `highlight_appliesBackgroundColor` — input `"==hi=="`; assert a non-transparent `background` color spans the inner text.
    6. `nestedOrdering_doesNotCrash` — input containing all 5 format types simultaneously; assert no exception thrown and annotation count ≥ 5.
    7. `offsetMapping_identityPreservesCursorAt0` — call `originalToTransformed(0)` and `transformedToOriginal(0)`; assert both return 0 (identity mapping).
    8. `offsetMapping_identityPreservesCursorAtEnd` — given input of length N, assert `originalToTransformed(N) == N`.
    9. `performance_under2ms` — apply transformation to a 2,000-char string containing 20 bold spans; assert wall-clock time < 2 ms (use `System.nanoTime()`).
  - **Pass criteria**: `./gradlew test` exits 0, all 9 cases green.
- **Risk**: HIGH — `OutputTransformation` (the API needed to hide markers and collapse their length from the offset mapping) does not yet support `AnnotatedString` styling as of early 2026. Attempting to implement Phase 2 marker-hiding now will produce a cursor-jump bug that has no documented fix. Shipping with visible markers is the correct Phase 1 behavior; this prototype validates that the identity-offset path is stable, and the Phase 2 deferral decision is formally confirmed here.

---

### P0-3: Sync Round-Trip Prototype

- **Task ID**: P0-3
- **Title**: Single-node HLC-LWW sync cycle, backend + Android
- **What to build**: Build the minimum end-to-end sync proof using a real backend and Android client. **Backend side**: implement the HLC clock module (`hlc.ts`) — `generate(deviceId)` returns a new HLC string `<wall_ms_16hex>-<counter_4hex>-<deviceId>`, `receive(incoming, deviceId)` advances the local clock to `max(local_wall, incoming_wall)` and resets the counter. Implement the LWW merge function (`merge.ts`) — accepts two `NodeSyncRecord` objects and returns a merged record where each field independently keeps the value whose HLC is lexicographically greater. Wire `POST /api/sync/changes` and `GET /api/sync/changes` with a real in-memory SQLite database (`:memory:`) using the full HLC schema from `ARCHITECTURE.md §4` (all `_hlc` companion columns). **Android side**: implement `HlcClock.kt` — mirrors the backend logic in Kotlin. Implement `SyncRepository.kt` with `pull(since: String, deviceId: String)` and `push(records: List<NodeSyncRecord>, deviceId: String)` using Ktor Client. Write a manual integration test script (not a WorkManager job) that creates one node locally on "device A", pushes it, then simulates "device B" editing the same node's `content` field with a higher HLC and pushing, then verifies device A pulls and receives the correct winning value.
- **Inputs required**: Backend: Fastify v5, Drizzle + better-sqlite3, jose (JWT not needed for this prototype — use a stub auth middleware that accepts any token), Vitest. Android: Ktor Client 3.4.0 (OkHttp engine), kotlinx.serialization, Room schema draft for `NodeSyncRecord`. Both sides need the HLC format spec from `API.md §Common Definitions`.
- **Output artifact**:
  - `backend/src/hlc.ts` — HLC clock generate/receive functions
  - `backend/src/merge.ts` — per-field LWW merge function
  - `backend/src/routes/sync.ts` — `GET /api/sync/changes` + `POST /api/sync/changes` (stub auth)
  - `backend/src/routes/sync.test.ts` — Vitest integration tests
  - `android/app/src/main/java/com/outlinegod/prototype/HlcClock.kt`
  - `android/app/src/main/java/com/outlinegod/prototype/SyncRepository.kt`
  - `android/app/src/test/java/com/outlinegod/prototype/HlcClockTest.kt`
  - `android/app/src/test/java/com/outlinegod/prototype/LwwMergeTest.kt`
- **How to test**:
  - **Backend test file**: `backend/src/routes/sync.test.ts`
  - **Backend test cases**:
    1. `hlc_generate_isMonotonicallyIncreasing` — call `generate()` twice in rapid succession; assert second HLC is strictly greater than first (lexicographic comparison).
    2. `hlc_receive_advancesClockPastIncoming` — call `receive()` with an incoming HLC whose wall component is 100 ms in the future; assert resulting HLC wall is ≥ that future value.
    3. `lww_merge_higherHlcWins` — create two `NodeSyncRecord`s differing only in `content` and `content_hlc`; assert merge returns the record with the lexicographically higher `content_hlc`.
    4. `lww_merge_idempotent` — `merge(A, merge(A, B))` equals `merge(A, B)`.
    5. `lww_merge_commutative` — `merge(A, B)` equals `merge(B, A)` for every field independently.
    6. `lww_merge_deleteWins` — record B has `deleted_at` set and `deleted_hlc` > all of A's field HLCs; assert merged result has `deleted_at` set.
    7. `POST /sync/changes` — push a single node; assert `accepted_node_ids` contains the node id and the node is retrievable via `GET /sync/changes?since=0&device_id=X`.
    8. `GET /sync/changes` — after pushing from device A, pull from device B (`device_id` differs); assert the node appears in the response.
    9. `GET /sync/changes` — pull from device A (same `device_id` used to push); assert node does NOT appear (echo suppression).
    10. `conflict_serverVersionWins` — push node from device A; then push same node id from device B with an older HLC; assert `conflicts` array in response contains the A version for every field where B lost.
  - **Android test file**: `android/app/src/test/java/com/outlinegod/prototype/HlcClockTest.kt` + `LwwMergeTest.kt`
  - **Android test cases**:
    1. `HlcClock_generate_monotonic` — same as backend test 1, in Kotlin.
    2. `HlcClock_receive_advancesPastIncoming` — same as backend test 2, in Kotlin.
    3. `LwwMerge_higherHlcWins` — Kotlin port of backend test 3.
    4. `LwwMerge_idempotent` — Kotlin port of backend test 4, using Kotest property-based test with `Arb.string()` HLC inputs.
    5. `LwwMerge_commutative` — Kotlin port of backend test 5, property-based.
    6. `LwwMerge_deleteWins` — Kotlin port of backend test 6.
  - **Pass criteria**: `pnpm test` exits 0 (backend, 10 cases green); `./gradlew test` exits 0 (Android, 6 cases green). Total: 16 tests, zero skipped.
- **Risk**: HIGH — HLC clock drift between Android device and server introduces subtle ordering bugs that only manifest when the device clock is set backward (common during DST transitions or manual clock adjustments). The `receive()` function's max-wall / counter-increment logic must be correct or the entire LWW invariant breaks silently. Property-based commutativity and idempotency tests (Kotest on Android, Vitest on backend) are the only reliable way to catch these bugs before production.

---

## Phase 1 — Backend Foundation

**Goal**: A running, tested Docker container. `GET /health` returns `{"status":"ok","db":"ok","version":"1.0.0","uptime":<number>}`. `pnpm test` exits 0 with zero failures across all test files.

**Prerequisite**: All Phase 0 prototypes must have passing tests before beginning Phase 1.

Tasks run in task ID order. Each task is scoped to approximately 1–2 hours. One table = one task. One module = one task.