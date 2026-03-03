# Phase 05: sync-integration-fixes - Research

**Researched:** 2026-03-03
**Domain:** Android↔Backend sync integration — HLC format alignment, user identity management, HTTP body serialization
**Confidence:** HIGH (all findings sourced directly from codebase inspection)

---

## Summary

Phase 05 fixes three critical integration blockers that prevent Android↔backend sync from working at all. All three gaps were identified during the v0.4 milestone audit and are well-understood: the exact bug location, the root cause, and the minimal surgical fix are all clear from reading the source files.

**GAP-1 (HLC format mismatch):** Android `HlcClock.kt` generates hex-formatted HLCs (`0000017b05a3a1be-0000-<uuid>`) while the backend `hlc.ts` generates decimal-formatted HLCs (`1772370135754-00003-<deviceId>`). The LWW merge uses lexicographic string comparison — a hex HLC starting with `"0000..."` always sorts before any decimal HLC starting with `"1772..."`, making every Android sync push appear older than any server record. The fix is to update Android to match the backend's decimal format (13-digit wall, 5-digit counter). This is the smaller change and avoids touching tested backend code.

**GAP-2 (userId = JWT token):** `AuthRepositoryImpl.googleSignIn()` stores `response.token` (the JWT) under the key `access_token` and returns it via `getAccessToken()`. Multiple callers misuse `getAccessToken()` to obtain what they believe is a user UUID. The `AuthResponse` already contains `user.id` (the real UUID), but it is never persisted separately. The fix is: store `response.user.id` under a `user_id` DataStore key on sign-in, add `getUserId(): Flow<String?>` to the `AuthRepository` interface, and update three ViewModels to call `getUserId()` instead of `getAccessToken()` wherever a userId is needed.

**GAP-3 (camelCase POST body):** `DocumentListViewModel.createDocument()` constructs a `mapOf("parentId" to ..., "sortOrder" to ...)` and sends it directly. The backend expects snake_case field names (`parent_id`, `sort_order`). The fix is to use a proper `@Serializable` data class with `@SerialName` annotations for this one-off REST call. All the sync DTOs (`SyncPushPayload`, `NodeSyncRecord`, etc.) already correctly use `@SerialName` — this is a gap only in the direct REST `createDocument()` call.

**Primary recommendation:** Fix GAP-2 first (simplest, unblocks all queries), then GAP-1 (requires updating HlcClock and all existing HLC test fixtures), then GAP-3 (one line of code change).

---

## Standard Stack

All dependencies for these fixes are already installed. No new dependencies needed.

### Core (existing)
| Library | Version | Purpose | Role in fix |
|---------|---------|---------|-------------|
| `kotlinx-serialization-json` | 1.7.3 | JSON serialization with `@SerialName` | GAP-3: fix camelCase POST body |
| `androidx.datastore:datastore-preferences` | (BOM-managed) | Persistent key-value store | GAP-2: persist user UUID |
| `io.ktor:ktor-client-mock` | 3.1.3 | Mock HTTP engine for tests | All gaps: existing test pattern |
| `io.mockk:mockk` | (existing) | Mocking framework | All gaps: test mocks for AuthRepository |

### No New Dependencies
All three fixes use existing code patterns and libraries. No additions to `build.gradle.kts` required.

---

## Architecture Patterns

### GAP-1: HLC Format — Change Android to Match Backend

**Decision:** Update Android HlcClock to use 13-digit decimal wall + 5-digit zero-padded decimal counter. The backend format is the authoritative standard per the spec comment in `hlc.ts`: "Lexicographically sortable — a higher HLC string always means a more recent event."

**Why Android changes, not backend:** Backend has more tests, is deployed, and changing it would require a data migration of all existing HLC strings in SQLite. Android local Room data will be reset by clearing app storage (expected during development).

**Exact format change:**

```kotlin
// BEFORE (hex format — never matches backend)
fun format(wallMs: Long, counter: Int, deviceId: String): String {
    val wallHex = wallMs.toString(16).padStart(16, '0')
    val counterHex = counter.toString(16).padStart(4, '0')
    return "$wallHex-$counterHex-$deviceId"
}

fun parse(hlc: String): Triple<Long, Int, String> {
    val firstDash = hlc.indexOf('-')
    val secondDash = hlc.indexOf('-', firstDash + 1)
    val wallMs = hlc.substring(0, firstDash).toLong(16)     // hex parse
    val counter = hlc.substring(firstDash + 1, secondDash).toInt(16)  // hex parse
    val deviceId = hlc.substring(secondDash + 1)
    return Triple(wallMs, counter, deviceId)
}

// AFTER (decimal format — matches backend hlc.ts exactly)
fun format(wallMs: Long, counter: Int, deviceId: String): String {
    val wallStr = wallMs.toString().padStart(13, '0')
    val counterStr = counter.toString().padStart(5, '0')
    return "$wallStr-$counterStr-$deviceId"
}

fun parse(hlc: String): Triple<Long, Int, String> {
    val firstDash = hlc.indexOf('-')
    val secondDash = hlc.indexOf('-', firstDash + 1)
    val wallMs = hlc.substring(0, firstDash).toLong()       // decimal parse
    val counter = hlc.substring(firstDash + 1, secondDash).toInt()    // decimal parse
    val deviceId = hlc.substring(secondDash + 1)
    return Triple(wallMs, counter, deviceId)
}
```

**Impact on tests:** All existing `HlcClockTest.kt` in `sync/` package uses regex `^[0-9a-f]{16}-[0-9a-f]{4}-.*` (hex format). These regexes must be updated to `^\d{13}-\d{5}-.*`. The wall part is 13 digits (timestamp in ms, e.g., `1772370135754`). The counter is 5 digits (`00000`).

**The prototype `HlcClockTest.kt`** in `prototype/` package tests the prototype `HlcClock.kt` which is unrelated — leave it unchanged.

**LwwMergeTest.kt** in `sync/` uses hardcoded HLC strings like `"0000017b05a3a1be-0000-device-1"`. These must be updated to decimal format like `"1772370135754-00000-device-1"`.

**SettingsViewModelTest.kt**, **DocumentListViewModelTest.kt**, **DocumentListSyncTest.kt** all use mock HLC strings like `"0000017b05a3a1be-0000-device-1"`. The regex assertions like `^[0-9a-f]{16}-[0-9a-f]{4}-.*` in these test files must also be updated.

### GAP-2: userId — Store UUID at Login, Add getUserId()

**Decision:** Add a new `user_id` DataStore key. Persist `response.user.id` during `googleSignIn()`. Add `getUserId(): Flow<String?>` to `AuthRepository` interface and `AuthRepositoryImpl`.

**Files to change:**
1. `AuthRepository.kt` (interface) — add `getUserId(): Flow<String?>`
2. `AuthRepositoryImpl.kt` — add `USER_ID_KEY`, persist in `googleSignIn()`, implement `getUserId()`
3. `DocumentListViewModel.kt` — replace 2 calls to `getAccessToken()` with `getUserId()`
4. `NodeEditorViewModel.kt` — replace 1 call to `getAccessToken()` with `getUserId()`
5. `SettingsViewModel.kt` — replace 1 call to `getAccessToken()` with `getUserId()`

**Exact change to AuthRepositoryImpl:**

```kotlin
companion object {
    private val ACCESS_TOKEN = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    private val USER_ID_KEY = stringPreferencesKey("user_id")   // NEW
}

override suspend fun googleSignIn(idToken: String): Result<AuthResponse> = runCatching {
    val response: AuthResponse = httpClient.post("$baseUrl/api/auth/google") {
        contentType(ContentType.Application.Json)
        setBody(mapOf("id_token" to idToken))
    }.body()
    dataStore.edit { prefs ->
        prefs[ACCESS_TOKEN] = response.token
        prefs[REFRESH_TOKEN_KEY] = response.refreshToken
        prefs[USER_ID_KEY] = response.user.id   // NEW — store real UUID
    }
    response
}

override fun getUserId(): Flow<String?> =
    dataStore.data.map { prefs -> prefs[USER_ID_KEY] }
```

**ViewModels: replace getAccessToken() with getUserId():**

```kotlin
// BEFORE (in DocumentListViewModel.loadDocuments)
val userId = authRepository.getAccessToken().filterNotNull().first()

// AFTER
val userId = authRepository.getUserId().filterNotNull().first()
```

Same pattern for all 4 affected call sites across the 3 ViewModels.

**Critical:** `getAccessToken()` must remain on the interface — the Ktor 401 interceptor in `KtorClientFactory.kt` uses it as the bearer token. Do not remove it.

### GAP-3: POST /api/documents — Use @Serializable Data Class

**Decision:** Create a minimal `CreateDocumentRequest` data class with proper `@SerialName` annotations instead of the raw `mapOf()`. Place it in `network/model/Document.kt` (create new file) or inline in `DocumentListViewModel.kt`.

**Exact fix in DocumentListViewModel.createDocument():**

```kotlin
// BEFORE
httpClient.post("$baseUrl/api/documents") {
    contentType(ContentType.Application.Json)
    setBody(mapOf(
        "id" to doc.id,
        "title" to doc.title,
        "type" to doc.type,
        "parentId" to doc.parentId,      // WRONG — backend ignores this
        "sortOrder" to doc.sortOrder     // WRONG — backend ignores this
    ))
}

// AFTER — use a proper @Serializable data class
@Serializable
data class CreateDocumentRequest(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String,
    @SerialName("parent_id") val parentId: String?,
    @SerialName("sort_order") val sortOrder: String
)

httpClient.post("$baseUrl/api/documents") {
    contentType(ContentType.Application.Json)
    setBody(CreateDocumentRequest(
        id = doc.id,
        title = doc.title,
        type = doc.type,
        parentId = doc.parentId,
        sortOrder = doc.sortOrder
    ))
}
```

**Note:** The backend also ignores the `id` field from clients and generates its own UUID. However, including `id` in the request body is harmless (backend reads only what it validates).

**Also note:** The backend validates `sort_order` as required. `DocumentListViewModel.createDocument()` receives `sortOrder` as a parameter, so this is already properly set.

### Recommended Project Structure (unchanged)

No structural changes needed. All fixes are within existing files.

### Anti-Patterns to Avoid

- **Don't change the backend HLC format.** The Android format is the outlier; the backend has more tests and live data. Changing the backend would require migrating all stored HLC strings.
- **Don't remove `getAccessToken()` from the interface.** The Ktor auth interceptor needs it. Only callers who need a userId should switch to `getUserId()`.
- **Don't configure a global snake_case serializer in Ktor.** The sync DTOs already use explicit `@SerialName` annotations. A global policy could cause unexpected behavior in other requests. Use targeted `@SerialName` on the new data class only.
- **Don't use `mapOf()` for POST bodies.** Kotlin's `Map<String, Any?>` with Ktor's content negotiation plugin does not apply `@SerialName` annotations — it serializes the literal map key strings.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Snake_case serialization | Custom serializer or interceptor | `@SerialName` on `@Serializable` data class | Already the pattern used in `Sync.kt`, `Auth.kt` |
| User identity storage | New table/complex store | DataStore `stringPreferencesKey("user_id")` | Same mechanism as `access_token` |
| HLC format bridge | Conversion layer | Update the format at the source | A conversion layer would be more fragile and harder to test |

**Key insight:** All three fixes are "remove the wrong thing, use the right thing" — not adding complexity.

---

## Common Pitfalls

### Pitfall 1: HLC Format — Existing Test Fixtures Break
**What goes wrong:** After changing `HlcClock.format()` to decimal, all tests that assert regex `^[0-9a-f]{16}-[0-9a-f]{4}-.*` or use hardcoded hex HLC strings (`"0000017b05a3a1be-0000-device-1"`) will fail.
**Why it happens:** The HLC format is pervasive — test data, mock return values, and regex assertions all assumed hex.
**How to avoid:** Before running tests, grep for `0000017b05a3a1be` and `[0-9a-f]{16}` and update every occurrence. Use a valid decimal HLC like `"1772370135754-00000-device-1"` as the standard test fixture.
**Warning signs:** 30+ test failures after the format change — expect this; it's not a logic error.

### Pitfall 2: GAP-2 — DataStore User ID Not Populated on First Test Run
**What goes wrong:** Tests that mock `authRepository.getUserId()` will work fine. But `AuthRepositoryTest` tests calling real `AuthRepositoryImpl.getUserId()` without first calling `googleSignIn()` will return null.
**Why it happens:** The `user_id` DataStore key is only written during sign-in. A fresh DataStore has no value.
**How to avoid:** Test `getUserId()` both cases: after sign-in (returns UUID) and before sign-in (returns null). The callers use `.filterNotNull().first()` which will suspend forever if null — this is correct behavior (auth guard).

### Pitfall 3: GAP-2 — SettingsViewModel Uses userId for Both DB Key and Room Queries
**What goes wrong:** `SettingsViewModel` stores `userId` in a field (`internal var userId: String = ""`). If `getUserId()` returns null before settings are loaded, the userId field stays empty string and all DAO queries use `""` as userId.
**Why it happens:** `loadSettings()` calls `authRepository.getUserId().filterNotNull().first()` — this suspends until a value is emitted. If the user is not logged in, this blocks forever. This is the intended behavior (screen should not be reachable without auth).
**How to avoid:** Keep the `.filterNotNull()` chain. The auth guard in the nav host prevents unauthenticated access to the settings screen.

### Pitfall 4: GAP-3 — Backend Generates Its Own Document ID
**What goes wrong:** The `id` field sent in the POST body is ignored by the backend (it generates its own `randomUUID()`). The local Room `DocumentEntity` has a client-generated UUID, but the backend creates a different UUID. After sync pull, the same document appears twice.
**Why it happens:** The backend's POST `/documents` handler does `const id = randomUUID()` unconditionally (line 85 in documents.ts), ignoring any `id` in the body.
**How to avoid:** This is a pre-existing design limitation, NOT a GAP-3 issue per se. The proper path for creating a synced document is via the sync push endpoint, not via POST `/documents`. The `createDocument()` in the VM uses the direct REST endpoint as a "best effort" call and falls back to sync for reconciliation. This is acceptable for Phase 05. Document the behavior in code comments.

### Pitfall 5: HLC Counter Width — 5 Digits vs 4 Digits
**What goes wrong:** The backend uses 5-digit counter (`00003`). The current Android format uses 4-digit counter (`0003`). If not aligned, lexicographic comparison breaks for counter values >= 10000.
**Why it happens:** Different choices were made when the two sides were implemented.
**How to avoid:** Use 5-digit counter in Android to match the backend. The `HlcClock.format()` method must use `counter.toString().padStart(5, '0')`.

---

## Code Examples

Verified from source files:

### Backend HLC format (source of truth)
```typescript
// Source: backend/src/hlc/hlc.ts line 14-16
function format(ms: number, counter: number, nodeId: string): string {
  return `${String(ms).padStart(13, '0')}-${String(counter).padStart(5, '0')}-${nodeId}`
}
// Example output: "1772370135754-00003-device_abc123"
```

### DataStore key pattern (existing — for userId fix)
```kotlin
// Source: android/.../repository/impl/AuthRepositoryImpl.kt
companion object {
    private val ACCESS_TOKEN = stringPreferencesKey("access_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    // ADD: private val USER_ID_KEY = stringPreferencesKey("user_id")
}
// getUserId() follows same pattern as getAccessToken()
override fun getUserId(): Flow<String?> =
    dataStore.data.map { prefs -> prefs[USER_ID_KEY] }
```

### AuthResponse already has user.id (verified)
```kotlin
// Source: android/.../network/model/Auth.kt
@Serializable
data class AuthResponse(
    @SerialName("token") val token: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user") val user: UserProfile,   // UserProfile.id is the real UUID
    @SerialName("is_new_user") val isNewUser: Boolean
)

@Serializable
data class UserProfile(
    @SerialName("id") val id: String,   // THIS is the real user UUID
    ...
)
```

### @SerialName pattern (existing — for GAP-3 fix)
```kotlin
// Source: android/.../network/model/Sync.kt (already correct pattern)
@Serializable
data class NodeSyncRecord(
    @SerialName("id") val id: String,
    @SerialName("document_id") val documentId: String,
    @SerialName("sort_order") val sortOrder: String,
    ...
)
// Apply same pattern to CreateDocumentRequest
```

### Mock pattern for getUserId() in tests
```kotlin
// Pattern from existing tests (e.g., DocumentListViewModelTest.setUp())
every { authRepository.getAccessToken() } returns flowOf("user-1")
// ADD parallel mock for getUserId:
every { authRepository.getUserId() } returns flowOf("user-uuid-1")
```

### Decimal HLC test fixture (replacement for hex fixtures)
```kotlin
// BEFORE: every { hlcClock.generate(any()) } returns "0000017b05a3a1be-0000-device-1"
// AFTER:
every { hlcClock.generate(any()) } returns "1772370135754-00000-device-1"

// BEFORE regex: assertTrue(hlc.matches(Regex("^[0-9a-f]{16}-[0-9a-f]{4}-.*")))
// AFTER regex:  assertTrue(hlc.matches(Regex("""^\d{13}-\d{5}-.*""")))
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hex HLC (16-char wall, 4-char counter) | Decimal HLC (13-char wall, 5-char counter) | Phase 05 | All LWW comparisons become valid |
| JWT token used as userId | Real UUID from AuthResponse.user.id | Phase 05 | Queries return correct data |
| `mapOf("parentId" to ...)` for POST body | `@Serializable` class with `@SerialName` | Phase 05 | Backend receives correct field names |

**Deprecated/outdated after Phase 05:**
- `getAccessToken()` as a userId source — deprecated for DAO queries, remains valid only for Ktor auth header

---

## Open Questions

1. **GAP-3: Backend ignores client-provided document ID**
   - What we know: POST `/documents` always generates its own UUID (line 85, `documents.ts`). The Android client generates a local UUID for Room, then the backend creates a different one. After sync pull, the document appears twice.
   - What's unclear: Is this the intended Phase 05 behavior or should we fix it now?
   - Recommendation: Accept the limitation for Phase 05. The audit says GAP-3 is "POST body camelCase" — the duplicate-UUID problem is a separate pre-existing design decision. The sync push path (via `SyncRepositoryImpl.push()`) uses the sync endpoint which does `INSERT OR REPLACE` by ID and will reconcile. Add a code comment noting the limitation.

2. **Cascading test fixture updates — scope**
   - What we know: Changing HLC format invalidates fixtures in at least: `HlcClockTest.kt` (sync/), `LwwMergeTest.kt`, `SettingsViewModelTest.kt`, `DocumentListViewModelTest.kt`, `DocumentListSyncTest.kt`, `NodeEditorSyncTest.kt`, `NodeEditorPersistenceTest.kt`
   - What's unclear: Are there other test files using `"0000017b05a3a1be"` pattern not yet found?
   - Recommendation: Run `grep -r "0000017b05a3a1be"` across `src/test/` to find all affected files before starting the fix.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `android/.../sync/HlcClock.kt` — current hex format
- Direct code inspection: `backend/src/hlc/hlc.ts` — authoritative decimal format
- Direct code inspection: `android/.../repository/impl/AuthRepositoryImpl.kt` — missing user_id storage
- Direct code inspection: `android/.../network/model/Auth.kt` — AuthResponse already has user.id
- Direct code inspection: `android/.../ui/screen/documentlist/DocumentListViewModel.kt` — camelCase mapOf, getAccessToken() misuse
- Direct code inspection: `android/.../ui/screen/nodeeditor/NodeEditorViewModel.kt` — getAccessToken() misuse
- Direct code inspection: `android/.../ui/screen/settings/SettingsViewModel.kt` — getAccessToken() misuse
- Direct code inspection: `backend/src/routes/documents.ts` — backend expects snake_case, ignores id field
- Direct code inspection: `.planning/v0.4-MILESTONE-AUDIT.md` — audit findings

### Secondary (MEDIUM confidence)
- Existing test patterns in `AuthRepositoryTest.kt`, `DocumentListViewModelTest.kt` — verified patterns for mock setup and DataStore testing

---

## Metadata

**Confidence breakdown:**
- GAP-1 (HLC format): HIGH — both formats read directly from source; fix is clear
- GAP-2 (userId): HIGH — all affected sites identified; AuthResponse.user.id confirmed available
- GAP-3 (camelCase): HIGH — both sides inspected; @SerialName pattern already in use elsewhere
- Test fixture scope: MEDIUM — known files identified; grep needed to confirm completeness

**Research date:** 2026-03-03
**Valid until:** 2026-04-03 (stable domain)

---

## GAP Summary for Planner

### Precise file changelist

**GAP-1 — HLC Format (Android side only):**
- `sync/HlcClock.kt` — change `format()` and `parse()` to decimal (13-char wall, 5-char counter)
- `sync/HlcClockTest.kt` — update 2 regexes and all hardcoded HLC strings
- `sync/LwwMergeTest.kt` — update all hardcoded HLC strings
- `repository/AuthRepositoryTest.kt` — update HLC regex in deviceId test (if any)
- `ui/screen/settings/SettingsViewModelTest.kt` — update mock HLC strings
- `ui/screen/documentlist/DocumentListViewModelTest.kt` — update mock HLC strings
- `ui/screen/documentlist/DocumentListSyncTest.kt` — update mock HLC strings
- `ui/screen/nodeeditor/NodeEditorSyncTest.kt` — update mock HLC strings (if any)
- `ui/screen/nodeeditor/NodeEditorPersistenceTest.kt` — update mock HLC strings (if any)

**GAP-2 — userId storage:**
- `repository/AuthRepository.kt` — add `getUserId(): Flow<String?>`
- `repository/impl/AuthRepositoryImpl.kt` — add `USER_ID_KEY`, persist in `googleSignIn()`, implement `getUserId()`
- `ui/screen/documentlist/DocumentListViewModel.kt` — replace `getAccessToken()` with `getUserId()` at lines ~65 and ~110
- `ui/screen/nodeeditor/NodeEditorViewModel.kt` — replace `getAccessToken()` with `getUserId()` at line ~64 and ~659
- `ui/screen/settings/SettingsViewModel.kt` — replace `getAccessToken()` with `getUserId()` at line ~33
- `repository/AuthRepositoryTest.kt` — add tests for `getUserId()` (after-signin returns UUID, before-signin returns null)
- All ViewModel test files — add `every { authRepository.getUserId() } returns flowOf("user-uuid-1")` mocks

**GAP-3 — camelCase POST body:**
- `ui/screen/documentlist/DocumentListViewModel.kt` — replace `mapOf()` with `CreateDocumentRequest` data class
- `network/model/Document.kt` (new file) — define `CreateDocumentRequest` with `@SerialName`
- `ui/screen/documentlist/DocumentListViewModelTest.kt` — test that the POST body contains snake_case fields (via mock capture)
