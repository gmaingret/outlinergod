# OutlineGod architecture decisions for 2025

**The recommended stack pairs Kotlin-native libraries on Android (Ktor, Hilt, Room) with a lean Fastify + Drizzle backend, using string-based fractional indexing and per-field LWW sync with Hybrid Logical Clocks.** Three technical areas carry genuinely high risk: nested tree drag-and-drop, live WYSIWYG cursor stability, and offline sync correctness. Each has a viable path forward, but all three require early prototyping before committing. This report covers every decision point in the PRD across five sections — Android stack, backend stack, schema design, sync architecture, and critical risks — with specific library versions and implementation patterns.

---

## 1. Android tech stack: seven decisions

### Rich text via BasicTextField with TextFieldState

The stable `BasicTextField(state: TextFieldState)` API (graduated from BasicTextField2 in Compose Foundation 1.9.x) is the correct foundation for live inline Markdown formatting. The new `TextFieldState` eliminates the async recomposition bugs that plagued the old `onValueChange` callback loop — edits via `state.edit {}` are atomic and cursor-safe.

**Two-layer implementation** is the recommended pattern. Store raw Markdown as the source of truth in `TextFieldState`. Apply visual styling through `VisualTransformation` using regex-matched `SpanStyle` annotations. Because styling adds spans without changing text length, `OffsetMapping.Identity` preserves cursor position perfectly. The more advanced `OutputTransformation` API can collapse Markdown markers (hiding `**` around bold text), but **it does not yet support `AnnotatedString` styling as of early 2026** — the Compose team has confirmed this is high-priority but unshipped. Start with visible-markers styling (identity offset), then iteratively add marker-hiding once `OutputTransformation` gains span support.

**Compose Rich Editor** (`com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc13`) is the most mature third-party WYSIWYG library for Compose, but it is toolbar-driven — it does not support typing `**bold**` and seeing live formatting. It's useful as reference architecture, not as OutlineGod's core editor. The `compose-markdown-editor` library by konecny-ondrej (`me.okonecny:markdown-editor:0.2.1`) attempts true inline WYSIWYG but is too early-stage for production.

### Drag-and-drop via flattened list with sh.calvin.reorderable

No native Compose nested tree drag-and-drop exists. The official `dragAndDropSource`/`dragAndDropTarget` modifiers use `ClipData` for inter-app data transfer — they are not designed for list reordering.

**The production approach is a flattened list with indentation.** Render the tree as a `List<FlatNode>` where each node carries a `depth: Int` for visual indentation via `Modifier.padding(start = (depth * 24).dp)`. Use **`sh.calvin.reorderable:reorderable:3.0.0`** (by Calvin Liang) for drag reordering on this flat list. It supports `LazyColumn`, provides `Modifier.draggableHandle()` and `Modifier.longPressDraggableHandle()`, auto-scrolls at edges, includes haptic feedback, and uses `Modifier.animateItem` for smooth animations. On drop completion, translate flat-list position changes back to tree operations — reparenting is determined by horizontal drag offset relative to the indent guides. Indent/outdent can also use horizontal swipe gestures.

### Room 2.8.4 for local persistence with raw SQL for FTS5

**Room `androidx.room:room-runtime:2.8.4`** with KSP is the recommended ORM. It integrates seamlessly with `Flow`, `ViewModel`, and Compose via `collectAsState()`. UUID primary keys work as `@PrimaryKey val id: String`. Room now supports KSP (not just deprecated KAPT) and has KMP support since 2.7+. Note: OutlineGod does NOT use a `children` array — tree structure is always reconstructed from `parent_id` + `sort_order`.

**Critical caveat: Room only supports `@Fts3` and `@Fts4` annotations — there is no `@Fts5`.** Since OutlineGod requires FTS5 for `bm25()` ranking, prefix search, and boolean operators, create the FTS5 virtual table via raw SQL in a Room migration and query it through `@RawQuery` or `SupportSQLiteDatabase`. This hybrid approach keeps Room's type-safe DAOs for all CRUD operations while using raw SQL exclusively for full-text search. If FTS5 integration becomes too awkward, **SQLDelight `app.cash.sqldelight:android-driver:2.2.1`** is the fallback — it generates type-safe Kotlin from raw SQL and has full FTS5 support, but loses Room's annotation-based conciseness.

### Credential Manager API is the only path for Google Sign-In

Legacy `GoogleSignInClient` and One Tap sign-in were deprecated and removed in H2 2025. **Credential Manager** (`androidx.credentials:credentials:1.3.0` + `credentials-play-services-auth:1.3.0` + `com.google.android.libraries.identity.googleid:googleid:1.1.1`) is now the sole supported API. It unifies passkeys, passwords, and federated sign-in into a single bottom-sheet UI.

The flow: build a `GetGoogleIdOption` with your Web OAuth client ID as `serverClientId`, launch via `CredentialManager.create(context).getCredential()`, extract the `GoogleIdTokenCredential.idToken` string, and send it to the backend for verification. The Web client ID (not Android client ID) is used because it matches what the backend verifies.

### Ktor Client 3.4.0 over Retrofit

**Ktor Client `io.ktor:ktor-client-core:3.4.0`** is the recommended HTTP client. It is 100% Kotlin-native by JetBrains, uses coroutines natively (every request is `suspend`), and integrates `kotlinx.serialization` via the `ContentNegotiation` plugin — the same serializer used throughout the codebase. Use the `OkHttp` engine for Android's mature HTTP/2 support. Retrofit 3.0.0 (released May 2025) has improved Kotlin support but still carries the adapter/converter pattern that feels heavier in a pure Kotlin project. **Ktor's ~2MB footprint** is also lighter than Retrofit's ~3MB+ with dependencies.

### Hilt 2.52 for dependency injection

**Hilt `com.google.dagger:hilt-android:2.52`** provides compile-time dependency graph validation — a missing binding produces a compile error, not a runtime crash. For a production app with interleaved Room, Ktor, auth, and sync layers, this safety net is critical. Hilt now supports KSP (no KAPT needed), and `@HiltViewModel` + `hiltViewModel()` integrates seamlessly with Compose. Koin (`io.insert-koin:koin-bom:4.0.4`) is simpler to set up and supports KMP, but resolves dependencies at runtime, which is riskier for complex graphs. **Choose Koin only if KMP is a near-term requirement.**

### Compressor 3.0.1 for image compression

**Compressor `id.zelory:compressor:3.0.1`** is the battle-tested Android image compression library. It's Kotlin-native, uses coroutines, and supports constraint-based compression targeting exact file sizes:

```kotlin
val compressed = Compressor.compress(context, file) {
    resolution(1280, 720)
    quality(80)
    format(Bitmap.CompressFormat.WEBP_LOSSY)
    size(1_000_000) // iterates until sub-1MB
}
```

Use **Coil** (`io.coil-kt:coil-compose:2.7.0`) separately for image display via `AsyncImage` composable — Coil handles loading, caching, and memory management but is not a compression tool.

---

## 2. Backend tech stack: five decisions

### Fastify v5 is purpose-built for this use case

**Fastify `v5.7.4`** outperforms Express ~2–3× on real-world benchmarks (~30K req/s vs ~15K for basic JSON APIs) and ships with built-in JSON Schema validation, Pino structured logging (ideal for Docker), and a mature plugin ecosystem. Express 5 (released October 2024 after a decade) adds async error handling but remains slower with no built-in validation. Hono (`v4.12.2`) excels on edge runtimes but runs on Node.js through an adapter layer, losing its performance advantage. For a self-hosted Docker backend with **~10 REST endpoints**, Fastify's schema validation catches bugs early, its type provider (`@fastify/type-provider-typebox`) gives TypeScript integration, and binding to `0.0.0.0` for containers is a one-line config.

### Drizzle ORM on top of better-sqlite3

**Drizzle ORM `v0.45.1`** paired with **better-sqlite3 `v12.6.2`** is the recommended data layer. Drizzle provides TypeScript-first schema definitions, a SQL-like query API, and automatic migration generation via `drizzle-kit`. better-sqlite3 is synchronous — which is actually optimal for SQLite's serialized write model, avoiding mutex thrashing from async drivers. Enable **WAL mode** and **foreign keys** on connection:

```typescript
const sqlite = new Database('/data/outlinegod.db');
sqlite.pragma('journal_mode = WAL');
sqlite.pragma('foreign_keys = ON');
export const db = drizzle(sqlite);
```

For FTS5 queries, use Drizzle's `sql` template literal for raw SQL. Kysely (`v0.28.11`) has arguably better TypeScript inference for complex queries but lacks auto-migration — Drizzle's integrated tooling is more practical for this project's scope.

### jose v6 for JWT, google-auth-library for token verification

**jose `v6.1.3`** is the modern JWT library: TypeScript-native, zero dependencies, async/Promise-based, ESM-native, and supports all JWA algorithms including EdDSA. It supersedes `jsonwebtoken` (CommonJS, sync, limited algorithms) for all new projects. Use jose for signing and verifying OutlineGod's session JWTs.

For **Google ID token verification**, use **`google-auth-library` `v10.6.1`** — Google's official Node.js library. `OAuth2Client.verifyIdToken()` handles JWKS key fetching, caching, rotation, and all claim validation (`iss`, `aud`, `exp`) automatically. Use `payload.sub` (Google user ID) as the primary user identifier, not email.

### File serving via @fastify/static

Serve attachments at `/api/files/uuid.ext` using **`@fastify/static`** with the Docker volume mount as root. Set `immutable: true` and `maxAge: '30d'` since UUID-named files never change content. Enable ETag generation (on by default) and set `dotfiles: 'deny'`. For additional security, validate filenames against a UUID regex pattern in a route handler before calling `reply.sendFile()`. **Path traversal is handled by the library** — it restricts serving to the specified root directory. For a self-hosted single-user deployment, Fastify's built-in file serving is adequate; a production multi-user deployment would benefit from nginx/caddy as a reverse proxy.

---

## 3. SQLite schema: fractional indexing, soft deletes, and FTS5

### String-based fractional indexing eliminates float precision limits

Store `sort_order` as a **`TEXT` column** using string-based fractional indexing, not `REAL`. IEEE 754 floats collapse after ~52 successive halvings between two values — a pathological but reachable scenario in an outliner where users repeatedly insert between the same two items. String keys (variable-length base-62 encoded) sort lexicographically and have no practical precision limit.

Use the **`fractional-indexing` npm package `v3.2.0`** (by Rocicorp) on the server and port its ~100-line algorithm to Kotlin on Android. The API is simple: `generateKeyBetween(a, b)` returns a string that sorts between `a` and `b`. For multi-device scenarios, use **`fractional-indexing-jittered` `v1.0.0`** which adds random jitter to minimize collision probability to ~1 in 47,000 per insertion. As a tiebreaker for collisions that do occur, **always `ORDER BY sort_order ASC, id ASC`** — Figma's Evan Wallace explicitly recommends using object ID as tiebreaker.

**Rebalance when key length exceeds ~50 characters** (indicating many insertions at the same position). Rebalancing generates fresh, evenly-spaced keys for all children of a parent via `generateNKeysBetween(null, null, n)` — a local-only operation that updates `modified` timestamps.

### Soft deletes with 90-day tombstone retention

Use a `deleted_at INTEGER` nullable column (Unix milliseconds). Active nodes have `deleted_at IS NULL`. Soft-deleting sets `deleted_at` and `modified` simultaneously. During sync, tombstones (records with `deleted_at` set) propagate to all devices. **Purge tombstones after 90 days** — long enough for any offline device to sync. Run the purge on app start or via a daily background task.

For indexes, create partial indexes that exclude deleted rows for common queries, and a dedicated index for sync operations:

```sql
CREATE INDEX idx_nodes_active ON nodes(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_nodes_modified ON nodes(modified);
CREATE INDEX idx_nodes_tombstones ON nodes(deleted_at) WHERE deleted_at IS NOT NULL;
```

### FTS5 external content table with correct trigger pattern

Use an **FTS5 external content table** linked to the nodes table to avoid duplicating text storage. The tokenizer `porter unicode61` combines Porter stemming with Unicode tokenization — best for general English content. A critical implementation detail: **never use regular `DELETE` or `UPDATE` on external content FTS5 tables.** The correct pattern uses the special `'delete'` command:

```sql
CREATE VIRTUAL TABLE nodes_fts USING fts5(
    content, note,
    content=nodes, content_rowid=_rowid,
    tokenize='porter unicode61'
);

-- The ONLY correct delete pattern for external content FTS5:
CREATE TRIGGER nodes_fts_update AFTER UPDATE ON nodes BEGIN
    INSERT INTO nodes_fts(nodes_fts, rowid, content, note)
    VALUES('delete', old._rowid, old.content, old.note);
    INSERT INTO nodes_fts(rowid, content, note)
    VALUES (new._rowid, new.content, new.note);
END;
```

Using regular `DELETE FROM nodes_fts` causes index corruption — a well-documented SQLite pitfall confirmed in multiple forum threads. When a node is soft-deleted, remove it from the FTS index using the same `'delete'` command pattern. Run `INSERT INTO nodes_fts(nodes_fts) VALUES('optimize')` periodically to merge index segments.

For OutlineGod's custom search operators (`has:file`, `is:completed`, `#tag`, `@person`, `color:red`), build a **Kotlin-side query parser** that separates FTS terms from structured WHERE clauses, then combine them in a JOIN query:

```sql
SELECT n.* FROM nodes n
JOIN nodes_fts fts ON n._rowid = fts.rowid
WHERE nodes_fts MATCH ?
  AND n.completed = ?
  AND n.color = ?
ORDER BY bm25(nodes_fts);
```

---

## 4. Sync architecture: offline-first with per-field LWW

### Per-field LWW registers with Hybrid Logical Clocks

Simple physical-timestamp LWW is vulnerable to clock skew between devices — an "earlier" edit can overwrite a "later" one if one device's clock is ahead. **Use Hybrid Logical Clocks (HLC)** which combine physical time with a logical counter, guaranteeing monotonically increasing timestamps across devices regardless of clock drift. Each syncable field gets its own HLC timestamp, making every field an independent LWW-Register. This is a lightweight CRDT pattern — no full sequence CRDT (RGA, Fugue) is needed for an outliner operating at node granularity.

The schema extends each field with a companion HLC column:

```sql
CREATE TABLE nodes (
    id TEXT PRIMARY KEY,
    content TEXT, content_hlc TEXT,
    note TEXT, note_hlc TEXT,
    parent_id TEXT, parent_id_hlc TEXT,
    sort_order TEXT, sort_order_hlc TEXT,
    completed INTEGER, completed_hlc TEXT,
    color INTEGER, color_hlc TEXT,
    deleted_at INTEGER, deleted_hlc TEXT,
    device_id TEXT
);
```

The merge rule is simple: for each field, the version with the higher HLC wins. This is **commutative and idempotent** — applying the same remote change twice produces the same result, preventing merge loops.

### Deletion wins over concurrent edits

When a device deletes a node while another device concurrently edits it, **the deletion wins.** Resurrection of deliberately deleted content is confusing and dangerous. Syncthing switched from "delete loses" to "delete wins" (PR #10207) specifically because the old behavior caused "an entire class of strange out-of-sync bugs."

For children of deleted parents, use **cascade soft-delete with notification**: recursively soft-delete all descendants, but log conflicts in a `sync_conflicts` table so users can be notified and optionally recover content. This preserves data without silently resurrecting deleted subtrees.

### Pull-then-push sync protocol via WorkManager

Always **pull before push** to minimize conflicts. The sync cycle:

1. **PULL**: `GET /sync/changes?since={last_sync_hlc}&device_id={id}` — server returns all changes since last sync. Apply each incoming field using LWW merge.
2. **PUSH**: `POST /sync/changes` — send locally pending changes. Server applies LWW, returns accepted/rejected fields. For rejected fields, server returns the winning version, which the client applies locally.
3. **Update** `last_sync_hlc` to the server's current HLC.

Track pending changes via a `sync_status` column on the nodes table (`0=synced`, `1=pending`, `2=in_progress`, `3=error`). Compress the change set before pushing: if a CREATE is followed by multiple UPDATEs, collapse into a single CREATE with current state; if the last operation is DELETE, discard preceding UPDATEs.

Use **WorkManager** with `NetworkType.CONNECTED` constraint and exponential backoff (30s base, doubling each retry, max 5 attempts). Trigger sync on connectivity change with a 2–5 second delay to prevent flapping. Use `ExistingWorkPolicy.REPLACE` to prevent duplicate sync workers.

### Sort order conflicts resolve via jitter plus UUID tiebreaker

When two offline devices insert children under the same parent, both independently generate fractional index keys. With jittering, collision probability is ~1/47,000 per insertion. After sync merge, scan for duplicate `sort_order` values within the same parent and regenerate one side's key. The interleaving of concurrently-inserted sibling nodes (e.g., Device A's items interspersed with Device B's) is acceptable for outliner items — users can reorder after sync.

---

## 5. Android architecture and the five critical risks

### MVI with Orbit for complex state management

**Orbit MVI `org.orbit-mvi:orbit-core:10.0.0`** (released November 2025) is the recommended architecture framework. MVI's single immutable state object aligns perfectly with Compose's declarative model, and its reducer pattern `(State, Event) → State` captures explicit state transitions that enable undo/redo. Orbit adds minimal boilerplate over raw MVI, provides Compose-first `collectAsState()` and `collectSideEffect {}`, and includes testing utilities. The UiState data class contains tree state, selection, undo/redo availability, sync status, and search results — all in one place, eliminating the "inconsistent drift" problem of multiple scattered StateFlows.

For **undo/redo**, layer a command pattern inside the MVI reducer. Use **delta-based commands** (store only changes, not full state snapshots) with `ArrayDeque` stacks. Coalesce rapid text edits: if keystrokes arrive within ~300ms targeting the same node, merge into a single `EditTextCommand`. Use `CompositeCommand` for multi-step operations like "move node" that involve remove + insert + reorder. Leverage **`kotlinx.collections.immutable`** persistent collections for tree state so undo snapshots share structure via structural sharing, keeping memory overhead low.

For **zoom/hoisting**, use Compose Navigation with a type-safe route: `@Serializable data class OutlineScreen(val rootNodeId: String = "root")`. Each zoom level creates a back stack entry, so system back naturally "zooms out." Animate transitions with `scaleIn`/`scaleOut` to give a zooming visual effect. The navigation back stack IS the zoom history.

### Risk 1: Nested drag-and-drop requires custom drop-zone logic

**Risk: HIGH.** No production-ready nested tree DnD library exists for Compose. The viable path: use `sh.calvin.reorderable:reorderable:3.0.0` for flat-list reordering and **build custom drop-zone logic on top** using `pointerInput` to detect horizontal drag position. During drag, the X-offset relative to the item's left edge determines whether the drop targets a sibling position, a child position (indent), or a parent level (outdent). Visual indicators — a horizontal line that shifts left/right with indent guide lines — communicate the target depth to the user. On drop, recalculate `parentId`, `sortOrder`, and `depth` for the moved node and all descendants. **Budget 2–3 weeks for prototyping this interaction before committing to the approach.**

### Risk 2: Live WYSIWYG works with identity offset, marker-hiding is deferred

**Risk: HIGH for marker-hiding, LOW for visible-marker styling.** The two-phase approach: Phase 1 ships with visible Markdown markers (`**text**` rendered bold but markers visible), using `VisualTransformation` with `OffsetMapping.Identity`. This is cursor-stable by definition since no text length changes. Phase 2 hides markers when the cursor is outside the formatted range — this requires a custom `OffsetMapping` that accounts for collapsed characters. **Phase 2 is the hard cursor-stability problem** and should be deferred until after Phase 1 ships. Compile all regexes once and reuse them — for typical node content under 5,000 characters, regex matching is sub-millisecond per recomposition.

### Risk 3: Enter-key interception via InputTransformation

**Risk: MEDIUM.** Software keyboards do not trigger `onPreviewKeyEvent` — only hardware keys do. The reliable cross-keyboard solution is **`InputTransformation`**, which intercepts at the text buffer level regardless of input source:

```kotlin
class EnterKeyInterceptor(
    private val onEnter: (cursorPosition: Int) -> Unit
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val nl = asCharSequence().indexOfFirst { it == '\n' }
        if (nl != -1) { delete(nl, nl + 1); onEnter(nl) }
    }
}
```

This catches newlines from both hardware and software keyboards before they commit to state. Edge cases to handle: Shift+Enter for intentional newlines within notes, and paste operations containing newlines.

### Risk 4: Cross-area drag uses native Compose DnD modifiers

**Risk: MEDIUM.** Unlike within-list reordering, cross-area drag (outline → sidebar bookmarks) is exactly what the official `Modifier.dragAndDropSource` / `Modifier.dragAndDropTarget` API was designed for. Pack the node ID into `ClipData`, set up drop targets on the sidebar with visual feedback via `onEntered`/`onExited` callbacks. The **`compose-dnd` library `com.mohamedrejeb.dnd:compose-dnd:0.3.0`** provides a more ergonomic API with `DragAndDropContainer` and visual drag shadows. The main challenge is coordinating between the reorderable library (for within-list reorder) and the native DnD API (for cross-area drops) — these are two separate gesture systems that must not conflict.

### Risk 5: Sync correctness demands property-based testing

**Risk: HIGH.** The LWW-Register + HLC pattern is mathematically sound, but implementation bugs are where sync breaks. Key testing strategy: use **property-based testing** (Kotest for Kotlin, fast-check for TypeScript) to verify commutativity (`merge(A, merge(B, C)) == merge(C, merge(A, B))`), idempotency (`merge(A, A) == A`), and convergence (all devices reach the same state regardless of message ordering). Build a sync health dashboard early that shows per-device last sync timestamp, pending change count, and conflict log. **Start with single-device sync (phone ↔ server) before attempting multi-device**, and add deterministic replay tests that simulate network partitions and clock skew.

---

## Conclusion

The recommended stack is deliberately conservative — **Room, Hilt, Ktor, and Fastify are all battle-tested choices** that minimize integration risk. The genuinely novel engineering challenges are concentrated in three areas: tree drag-and-drop interaction design, live WYSIWYG cursor management, and sync correctness. String-based fractional indexing with jitter solves ordering elegantly. Per-field HLC-based LWW registers provide a solid CRDT foundation without the complexity of full sequence CRDTs.

The most actionable insight across this research: **OutlineGod's highest-risk features (nested DnD, marker-hiding WYSIWYG, sync correctness) should each get a dedicated 2-week prototype sprint before the architecture is finalized.** The flattened-list DnD approach, visible-marker WYSIWYG Phase 1, and single-device sync are all achievable with current tooling — but the "polished" versions of each require custom engineering that no library fully solves today.

| Layer | Choice | Version |
|-------|--------|---------|
| UI Framework | Jetpack Compose + Material 3 | BOM 2025.x |
| Architecture | Orbit MVI | 10.0.0 |
| Local DB | Room (KSP) + raw FTS5 | 2.8.4 |
| Auth | Credential Manager API | 1.3.0 |
| HTTP | Ktor Client (OkHttp engine) | 3.4.0 |
| DI | Hilt | 2.52 |
| DnD | sh.calvin.reorderable | 3.0.0 |
| Image compression | Compressor (zetbaitsu) | 3.0.1 |
| Backend framework | Fastify | 5.7.4 |
| Backend ORM | Drizzle + better-sqlite3 | 0.45.1 / 12.6.2 |
| JWT | jose | 6.1.3 |
| Google token verify | google-auth-library | 10.6.1 |
| Sort ordering | fractional-indexing (jittered) | 3.2.0 / 1.0.0 |