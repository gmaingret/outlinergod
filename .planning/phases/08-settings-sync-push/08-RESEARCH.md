# Phase 08: settings-sync-push - Research

**Researched:** 2026-03-03
**Domain:** Android sync push pipeline — settings entity wiring
**Confidence:** HIGH

---

## Summary

Phase 08 closes GAP-B from the v0.4 milestone audit: Android-originating settings changes are never pushed to the backend. The pull direction already works correctly — `SyncWorker.doWork()` applies pulled settings via `settings.toSettingsEntity()`, and `DocumentListViewModel.triggerSync()` does the same. What is missing is the **push** direction.

The fix is entirely additive. All required data structures already exist in the codebase:

- `SyncPushPayload.settings: SettingsSyncRecord?` is already declared (line 104 of `Sync.kt`), but always left `null`
- `SettingsSyncRecord` is fully defined and `@Serializable`
- `toSettingsSyncRecord()` mapper does NOT exist in `SyncMappers.kt` — this is the primary missing piece
- `SettingsDao.getPendingSettings()` does NOT exist — this is the secondary missing piece
- The backend `POST /api/sync/changes` already handles `body.settings` with full per-field LWW merge
- `SyncWorker` and `DocumentListViewModel.triggerSync()` both need one additional step: query pending settings and include them in the push payload

The plan is: (1) add `SettingsDao.getPendingSettings()`, (2) add `SettingsEntity.toSettingsSyncRecord()` to `SyncMappers.kt`, (3) wire both into `SyncWorker` and `DocumentListViewModel.triggerSync()`, (4) write tests.

**Primary recommendation:** Add `getPendingSettings(userId, sinceHlc, deviceId)` to SettingsDao, add `toSettingsSyncRecord()` to SyncMappers.kt, then thread the result into both push payload sites. No new files needed beyond tests.

---

## Standard Stack

All libraries and patterns are already in the project. No new dependencies required.

### Existing Infrastructure Used

| Component | File | Status |
|-----------|------|--------|
| `SettingsSyncRecord` | `network/model/Sync.kt:76-88` | Exists, fully `@Serializable` |
| `SyncPushPayload.settings` | `network/model/Sync.kt:104` | Field exists, always `null` |
| `SettingsEntity` | `db/entity/SettingsEntity.kt` | Exists, 4 HLC fields |
| `SettingsDao` | `db/dao/SettingsDao.kt` | Exists, missing `getPendingSettings()` |
| `SyncMappers.kt` | `sync/SyncMappers.kt` | Exists, missing `toSettingsSyncRecord()` |
| `SyncWorker` | `sync/SyncWorker.kt` | Exists, push section needs one line |
| `DocumentListViewModel` | `ui/screen/documentlist/DocumentListViewModel.kt` | Exists, triggerSync needs one line |

---

## Architecture Patterns

### Pending Changes Pattern (used by nodes, documents, bookmarks)

All three existing entity types use identical pattern for determining what needs to be pushed. The query filters by `device_id = localDevice` AND any HLC field `> sinceHlc`:

**NodeDao pattern (no userId — nodes scoped by deviceId only):**
```kotlin
// Source: db/dao/NodeDao.kt:30-42
@Query("""
    SELECT * FROM nodes
    WHERE device_id = :deviceId
    AND (content_hlc > :sinceHlc
      OR note_hlc > :sinceHlc
      OR parent_id_hlc > :sinceHlc
      OR sort_order_hlc > :sinceHlc
      OR completed_hlc > :sinceHlc
      OR color_hlc > :sinceHlc
      OR collapsed_hlc > :sinceHlc
      OR deleted_hlc > :sinceHlc)
""")
suspend fun getPendingChanges(sinceHlc: String, deviceId: String): List<NodeEntity>
```

**DocumentDao pattern (userId + deviceId — used by settings too):**
```kotlin
// Source: db/dao/DocumentDao.kt:30-40
@Query("""
    SELECT * FROM documents
    WHERE user_id = :userId
    AND device_id = :deviceId
    AND (title_hlc > :sinceHlc
      OR parent_id_hlc > :sinceHlc
      OR sort_order_hlc > :sinceHlc
      OR collapsed_hlc > :sinceHlc
      OR deleted_hlc > :sinceHlc)
""")
suspend fun getPendingChanges(userId: String, sinceHlc: String, deviceId: String): List<DocumentEntity>
```

**Settings equivalent (single row — returns SettingsEntity? not List):**

Settings is a singleton per user (one row per `user_id`, `PRIMARY KEY user_id`). The query should return `SettingsEntity?` (nullable), not a `List`. It checks if the single row has any HLC field `> sinceHlc` and was written by this device.

```kotlin
// Pattern to implement in SettingsDao:
@Query("""
    SELECT * FROM settings
    WHERE user_id = :userId
    AND device_id = :deviceId
    AND (theme_hlc > :sinceHlc
      OR density_hlc > :sinceHlc
      OR show_guide_lines_hlc > :sinceHlc
      OR show_backlink_badge_hlc > :sinceHlc)
    LIMIT 1
""")
suspend fun getPendingSettings(userId: String, sinceHlc: String, deviceId: String): SettingsEntity?
```

### Mapper Pattern (used by nodes, documents, bookmarks)

`SyncMappers.kt` contains bidirectional converters. The `toSettingsEntity()` direction already exists (lines 78-90). The missing direction is `SettingsEntity.toSettingsSyncRecord()`.

**Pattern from BookmarkEntity (most complete example):**
```kotlin
// Source: sync/SyncMappers.kt:136-156
fun BookmarkEntity.toBookmarkSyncRecord() = BookmarkSyncRecord(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    ...
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
```

**Settings equivalent (to implement):**

`SettingsSyncRecord` has `userId` but NOT `id` on the Android side. The backend `SettingsSyncRecord` interface has an `id` field (used by the backend DB `PRIMARY KEY id`). The Android `SettingsSyncRecord` also has `userId` but no `id` field (line 77-88 of `Sync.kt`).

CRITICAL: Look at `SettingsSyncRecord` definition — it has NO `id` field on the Android side (unlike the backend TS interface which has `id: string`). The Android `SettingsSyncRecord` is keyed by `user_id` only. The backend `upsertSettings()` function accepts an object with `id` from the body, but the Android client sends `userId` only.

Wait — checking again. The backend `sync.ts` `handleSyncPush` reads `body.settings` as `SettingsSyncRecord | null` where `SettingsSyncRecord` is the backend TS interface that includes `id`. But the Android `SettingsSyncRecord` in `Sync.kt` lines 76-88 does NOT include `id`. This means Android sends settings without `id` — the backend upsert uses `@id` which would be `undefined` in SQLite.

This is an important discrepancy. Looking at the backend test `seedSettings()`, it provides an `id` field. The existing pull path means settings arrive from backend WITH `id`, and `toSettingsEntity()` maps to `SettingsEntity` which has NO `id` (primary key is `user_id`). Then for push, Android needs to send a `SettingsSyncRecord` back — but it has no `id` to send.

Resolution options:
1. Android sends `userId` as the `id` field (consistent with "settings row is identified by user") — the backend receives `id = userId` and upserts with that as the primary key. This works because the backend `upsertSettings` does `ON CONFLICT(id) DO UPDATE`.
2. Add `id` to Android `SettingsSyncRecord`.

The simplest and cleanest approach: when building `toSettingsSyncRecord()`, set an `id` field if one is needed. But `SettingsSyncRecord` on Android has no `id` field. The backend test `settings_mergedPerField` passes `id: 'settings-merge-test'` — so the backend definitely uses `id`.

**Key finding:** The Android `SettingsSyncRecord` is missing the `id` field that the backend expects. The `toSettingsSyncRecord()` mapper will need to either:
- Use `userId` as the `id` (since settings row identity IS the userId)
- Or add `id: String = userId` to `SettingsSyncRecord` on the Android side

Looking at `SyncConflicts.settings: SettingsSyncRecord?` — when the backend returns a conflict settings record, it will include `id` in the JSON. The current `SettingsSyncRecord` (no `id` field) would silently ignore it since kotlinx.serialization skips unknown keys (confirmed by `Json { ignoreUnknownKeys = true }` in `SyncRepositoryTest`). So the pull/conflict direction works fine without `id` on Android.

For the push direction, the backend `upsertSettings()` will receive `@id = undefined` which in SQLite binds as `null` or causes an error — this is a real bug that must be handled.

**Recommended fix:** Add `val id: String = userId` to `SettingsSyncRecord` OR use the `userId` field as `id` in the mapper. Since `SettingsSyncRecord` on Android has `userId` as primary identity and the backend treats `id` as the PK, the cleanest fix is to add `@SerialName("id") val id: String` to `SettingsSyncRecord`, defaulting to `userId`. This is a non-breaking addition because `ignoreUnknownKeys` handles responses that include the extra field.

### SyncWorker Push Wiring Pattern

```kotlin
// Source: sync/SyncWorker.kt:70-80 (current — missing settings)
val userId = authRepository.getUserId().filterNotNull().first()
val pendingNodes = nodeDao.getPendingChanges(lastSyncHlc, deviceId)
val pendingDocs = documentDao.getPendingChanges(userId, lastSyncHlc, deviceId)
val pendingBookmarks = bookmarkDao.getPendingChanges(userId, lastSyncHlc, deviceId)

val pushPayload = SyncPushPayload(
    deviceId = deviceId,
    nodes = pendingNodes.map { it.toNodeSyncRecord() }.ifEmpty { null },
    documents = pendingDocs.map { it.toDocumentSyncRecord() }.ifEmpty { null },
    bookmarks = pendingBookmarks.map { it.toBookmarkSyncRecord() }.ifEmpty { null }
    // settings = null (the gap)
)
```

**Settings addition (pattern to follow):**
```kotlin
val pendingSettings = settingsDao.getPendingSettings(userId, lastSyncHlc, deviceId)

val pushPayload = SyncPushPayload(
    deviceId = deviceId,
    nodes = pendingNodes.map { it.toNodeSyncRecord() }.ifEmpty { null },
    documents = pendingDocs.map { it.toDocumentSyncRecord() }.ifEmpty { null },
    bookmarks = pendingBookmarks.map { it.toBookmarkSyncRecord() }.ifEmpty { null },
    settings = pendingSettings?.toSettingsSyncRecord()
)
```

### DocumentListViewModel Push Wiring Pattern

Same pattern as SyncWorker — `DocumentListViewModel.triggerSync()` lines 100-111 also build the push payload without settings. Same fix applies.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HLC comparison for "pending" detection | Custom timestamp diff | Existing `> sinceHlc` string compare in Room SQL | HLC strings are lexicographically ordered — string compare IS the right comparison |
| Settings merge logic | Custom Android merge | Backend handles LWW merge in `mergeSettings()` | Android only pushes; server applies LWW. Android applies pulled conflicts as-is via `upsertSettings()` |
| JSON serialization of `SettingsSyncRecord` | Manual JSON building | `@Serializable` + `@SerialName` already on `SettingsSyncRecord` | Already works end-to-end for pull direction |

---

## Common Pitfalls

### Pitfall 1: Missing `id` field in SettingsSyncRecord

**What goes wrong:** Android pushes `{"user_id":"...", "theme":"dark", ...}` without `"id"`. Backend `upsertSettings()` runs `INSERT INTO settings (id, ...)` with `@id` bound to `undefined` or `null`, causing a SQLite error or silent null PK.

**Why it happens:** The backend TS interface `SettingsSyncRecord` includes `id: string`, but Android's `SettingsSyncRecord` (Sync.kt:76-88) does not have an `id` field. The pull path works because incoming JSON is deserialized with `ignoreUnknownKeys = true`.

**How to avoid:** Add `@SerialName("id") val id: String` to Android `SettingsSyncRecord`. In `toSettingsSyncRecord()`, set `id = userId` (settings row identity equals userId in this single-user app design).

**Warning signs:** Backend returns 500 error on push when settings field is non-null.

### Pitfall 2: Settings query returning List instead of nullable single

**What goes wrong:** If `getPendingSettings()` is declared to return `List<SettingsEntity>`, calling `.ifEmpty { null }` would return a list (possibly multi-element), but `SyncPushPayload.settings` expects `SettingsSyncRecord?` (single, not a list).

**Why it happens:** Copy-paste from `getPendingChanges()` patterns which return `List`.

**How to avoid:** Declare `getPendingSettings()` as `suspend fun getPendingSettings(...): SettingsEntity?` (nullable, not list). Settings is a singleton per user.

### Pitfall 3: SyncWorker test not verifying settings in push payload

**What goes wrong:** Tests for SyncWorker mock `settingsDao` with `relaxed = true`, which returns `null` by default for suspend functions returning nullable. The push succeeds but settings never appear in payload even when there is pending data.

**Why it happens:** `coEvery { settingsDao.getPendingSettings(any(), any(), any()) } returns mockSettings` must be explicitly set in tests that verify settings push behavior.

**How to avoid:** Add a dedicated `SyncWorkerTest` case that seeds pending settings and verifies `syncRepository.push()` is called with `payload.settings != null`.

### Pitfall 4: HLC field coverage — must check ALL four HLC fields

**What goes wrong:** The `getPendingSettings()` query must check all four HLC fields (`theme_hlc`, `density_hlc`, `show_guide_lines_hlc`, `show_backlink_badge_hlc`). If any are omitted, settings changes for that field are never pushed.

**Why it happens:** Copy-paste error or forgetting a field.

**How to avoid:** The backend pull query in `sync.ts:490-496` shows the exact four fields — match those exactly in the Android Room query.

### Pitfall 5: SettingsDaoTest database schema lacks the new method

**What goes wrong:** `SettingsDaoTest` uses `TestSettingsDb` which is declared with `@Database(entities = [SettingsEntity::class], version = 1)`. Adding `getPendingSettings()` to `SettingsDao` does not require any schema change — it is a pure query method. No migration needed.

**Why it happens:** Confusion about whether adding a DAO method requires a schema change.

**How to avoid:** `getPendingSettings()` is a `@Query` annotation — it reads existing columns. No `@Entity` change, no migration, no database version bump needed.

---

## Code Examples

### Complete SettingsEntity.toSettingsSyncRecord() mapper

```kotlin
// Add to sync/SyncMappers.kt after toBookmarkSyncRecord()
fun SettingsEntity.toSettingsSyncRecord() = SettingsSyncRecord(
    id = userId,          // backend PK; settings row identity = userId
    userId = userId,
    theme = theme,
    themeHlc = themeHlc,
    density = density,
    densityHlc = densityHlc,
    showGuideLines = showGuideLines,
    showGuideLinesHlc = showGuideLinesHlc,
    showBacklinkBadge = showBacklinkBadge,
    showBacklinkBadgeHlc = showBacklinkBadgeHlc,
    deviceId = deviceId,
    updatedAt = updatedAt
)
```

Note: `SettingsSyncRecord` currently has no `id` field. Either add one or serialize `userId` under `"id"` key. See Pitfall 1.

### SettingsSyncRecord with id field added

```kotlin
// network/model/Sync.kt - update SettingsSyncRecord to add id field
@Serializable
data class SettingsSyncRecord(
    @SerialName("id") val id: String = "",       // ADD THIS — backend PK
    @SerialName("user_id") val userId: String,
    @SerialName("theme") val theme: String = "dark",
    @SerialName("theme_hlc") val themeHlc: String = "",
    @SerialName("density") val density: String = "cozy",
    @SerialName("density_hlc") val densityHlc: String = "",
    @SerialName("show_guide_lines") val showGuideLines: Int = 1,
    @SerialName("show_guide_lines_hlc") val showGuideLinesHlc: String = "",
    @SerialName("show_backlink_badge") val showBacklinkBadge: Int = 1,
    @SerialName("show_backlink_badge_hlc") val showBacklinkBadgeHlc: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("updated_at") val updatedAt: Long = 0L
)
```

### getPendingSettings DAO method

```kotlin
// Add to db/dao/SettingsDao.kt
@Query("""
    SELECT * FROM settings
    WHERE user_id = :userId
    AND device_id = :deviceId
    AND (theme_hlc > :sinceHlc
      OR density_hlc > :sinceHlc
      OR show_guide_lines_hlc > :sinceHlc
      OR show_backlink_badge_hlc > :sinceHlc)
    LIMIT 1
""")
suspend fun getPendingSettings(userId: String, sinceHlc: String, deviceId: String): SettingsEntity?
```

### SyncWorker push section (after wiring)

```kotlin
// sync/SyncWorker.kt — replace lines 70-80
val userId = authRepository.getUserId().filterNotNull().first()
val pendingNodes = nodeDao.getPendingChanges(lastSyncHlc, deviceId)
val pendingDocs = documentDao.getPendingChanges(userId, lastSyncHlc, deviceId)
val pendingBookmarks = bookmarkDao.getPendingChanges(userId, lastSyncHlc, deviceId)
val pendingSettings = settingsDao.getPendingSettings(userId, lastSyncHlc, deviceId)

val pushPayload = SyncPushPayload(
    deviceId = deviceId,
    nodes = pendingNodes.map { it.toNodeSyncRecord() }.ifEmpty { null },
    documents = pendingDocs.map { it.toDocumentSyncRecord() }.ifEmpty { null },
    bookmarks = pendingBookmarks.map { it.toBookmarkSyncRecord() }.ifEmpty { null },
    settings = pendingSettings?.toSettingsSyncRecord()
)
```

### SyncWorkerTest — settings push verification

```kotlin
// Pattern: test that settings appear in push payload when pending
@Test
fun doWork_includesSettings_whenPendingSettingsExist() = runTest {
    val pendingSettings = SettingsEntity(
        userId = "user-1",
        theme = "light",
        themeHlc = "BBBB",
        density = "cozy",
        densityHlc = "AAAA",
        showGuideLines = 1,
        showGuideLinesHlc = "AAAA",
        showBacklinkBadge = 1,
        showBacklinkBadgeHlc = "AAAA",
        deviceId = "device-1",
        updatedAt = System.currentTimeMillis()
    )
    coEvery { settingsDao.getPendingSettings(any(), any(), any()) } returns pendingSettings
    coEvery { syncRepository.pull(any(), any()) } returns Result.success(
        SyncChangesResponse(serverHlc = "hlc1")
    )
    coEvery { syncRepository.push(any()) } returns Result.success(
        SyncPushResponse(serverHlc = "hlc1")
    )

    val result = runSyncWorker()

    assertEquals(ListenableWorker.Result.success(), result)
    coVerify { syncRepository.push(match { payload ->
        payload.settings != null && payload.settings!!.theme == "light"
    }) }
}

@Test
fun doWork_omitsSettings_whenNoPendingSettings() = runTest {
    coEvery { settingsDao.getPendingSettings(any(), any(), any()) } returns null
    coEvery { syncRepository.pull(any(), any()) } returns Result.success(
        SyncChangesResponse(serverHlc = "hlc1")
    )
    coEvery { syncRepository.push(any()) } returns Result.success(
        SyncPushResponse(serverHlc = "hlc1")
    )

    val result = runSyncWorker()

    assertEquals(ListenableWorker.Result.success(), result)
    coVerify { syncRepository.push(match { payload ->
        payload.settings == null
    }) }
}
```

### SettingsDaoTest — getPendingSettings

```kotlin
// Pattern from DocumentDaoTest for getPendingChanges
@Test
fun getPendingSettings_returnsRow_whenAnyHlcAboveSince() = runTest {
    dao.upsertSettings(SettingsEntity(
        userId = "u1",
        theme = "light",
        themeHlc = "BBBB",
        density = "cozy",
        densityHlc = "AAAA",
        showGuideLines = 1,
        showGuideLinesHlc = "AAAA",
        showBacklinkBadge = 1,
        showBacklinkBadgeHlc = "AAAA",
        deviceId = "device-1",
        updatedAt = 0L
    ))
    val result = dao.getPendingSettings("u1", "0000", "device-1")
    assertNotNull(result)
    assertEquals("light", result!!.theme)
}

@Test
fun getPendingSettings_returnsNull_whenAllHlcsAtOrBelowSince() = runTest {
    dao.upsertSettings(SettingsEntity(
        userId = "u1",
        theme = "light",
        themeHlc = "AAAA",
        density = "cozy",
        densityHlc = "AAAA",
        showGuideLines = 1,
        showGuideLinesHlc = "AAAA",
        showBacklinkBadge = 1,
        showBacklinkBadgeHlc = "AAAA",
        deviceId = "device-1",
        updatedAt = 0L
    ))
    val result = dao.getPendingSettings("u1", "BBBB", "device-1")
    assertNull(result)
}

@Test
fun getPendingSettings_returnsNull_whenDifferentDevice() = runTest {
    dao.upsertSettings(SettingsEntity(
        userId = "u1",
        theme = "light",
        themeHlc = "BBBB",
        ...
        deviceId = "device-other",
        updatedAt = 0L
    ))
    val result = dao.getPendingSettings("u1", "0000", "device-1")
    assertNull(result)
}
```

---

## State of the Art

| Old State | Current State | Notes |
|-----------|---------------|-------|
| Settings never pushed (GAP-B) | Settings push wired | This phase closes the gap |
| `SyncPushPayload.settings` always null | `settings = pendingSettings?.toSettingsSyncRecord()` | Additive change |
| No `toSettingsSyncRecord()` mapper | Mapper added to SyncMappers.kt | Parallel to existing `toBookmarkSyncRecord()` |
| No `getPendingSettings()` DAO method | DAO method added | Parallel to `getPendingChanges()` on other DAOs |

---

## Open Questions

1. **`SettingsSyncRecord.id` field**
   - What we know: Backend TS `SettingsSyncRecord` has `id: string` as the DB primary key. Android `SettingsSyncRecord` has no `id` field. Backend `upsertSettings()` uses `@id` in the prepared statement.
   - What's unclear: Will the backend gracefully handle `id = null` (from unbound `@id`)? Or does it throw a NOT NULL violation?
   - Recommendation: Add `@SerialName("id") val id: String = ""` to Android `SettingsSyncRecord` and set `id = userId` in `toSettingsSyncRecord()`. This is the safest fix and is backward compatible (pull responses that include `id` are already ignored by `ignoreUnknownKeys`; now Android will send it back correctly).

2. **DocumentListViewModel test coverage for settings push**
   - What we know: `DocumentListSyncTest` verifies nodes/documents/bookmarks in pull. There is no test verifying settings appears in the push payload from `triggerSync()`.
   - What's unclear: Whether to add a new test class or extend `DocumentListSyncTest`.
   - Recommendation: Add 2 test cases to `DocumentListSyncTest` — one verifying settings are included when `getPendingSettings()` returns non-null, one verifying omission when it returns null.

---

## Sources

### PRIMARY (HIGH confidence — direct code inspection)

- `android/.../network/model/Sync.kt` — `SyncPushPayload`, `SettingsSyncRecord`, `SyncChangesResponse`, `SyncConflicts` definitions
- `android/.../sync/SyncMappers.kt` — all existing bidirectional mappers; confirms `toSettingsSyncRecord()` is absent
- `android/.../sync/SyncWorker.kt` — doWork() push section; confirms `settings = null` (gap)
- `android/.../ui/screen/documentlist/DocumentListViewModel.kt` — triggerSync(); confirms same gap
- `android/.../db/dao/SettingsDao.kt` — confirms `getPendingSettings()` absent
- `android/.../db/entity/SettingsEntity.kt` — 4 HLC fields: themeHlc, densityHlc, showGuideLinesHlc, showBacklinkBadgeHlc
- `backend/src/routes/sync.ts` — backend `handleSyncPush()` settings section; `upsertSettings()` prepared statement uses `@id`
- `backend/src/routes/sync.test.ts` — `settings_mergedPerField` test passes `id` in payload; `seedSettings()` seeds with `id`
- `backend/src/db/schema/settings.ts` — backend SQLite schema: `id TEXT PRIMARY KEY`
- `android/.../sync/SyncWorkerTest.kt` — existing test structure with WorkerFactory + TestListenableWorkerBuilder
- `android/.../ui/screen/documentlist/DocumentListSyncTest.kt` — existing Orbit MVI test structure
- `android/.../db/dao/SettingsDaoTest.kt` — existing DAO test structure with in-memory Room
- `android/.../sync/NodeMerge.kt`, `BookmarkMerge.kt` — `lwwStr`, `lwwInt` helpers pattern
- `.planning/v0.4-MILESTONE-AUDIT.md` — GAP-B definition and exact code references

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all components directly inspected in codebase
- Architecture: HIGH — existing patterns for nodes/documents/bookmarks are identical; settings follows same shape
- Pitfalls: HIGH — `id` field gap is verified against backend prepared statement
- Open questions: MEDIUM — `id = null` backend behavior not load-tested, but SQLite NOT NULL constraint is definitive

**Research date:** 2026-03-03
**Valid until:** N/A — this is internal codebase research, not third-party library research
