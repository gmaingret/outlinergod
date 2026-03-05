# Phase 13: v0.6-gap-closure - Research

**Researched:** 2026-03-05
**Domain:** Android Kotlin / Jetpack Compose lifecycle wiring + Orbit MVI navigation
**Confidence:** HIGH — all findings verified against actual source files

---

## Summary

Phase 13 closes 5 specific items (2 critical gaps + 3 tech debt) identified by the v0.6 milestone audit. All root causes have been verified against the actual source files. The audit description is accurate in every detail. This is surgical, low-risk work: no new dependencies, no new patterns, no architectural decisions required.

The two critical gaps (GAP-V1 and GAP-V2) share a common root: Phase 11 built features in isolation without connecting to infrastructure built in Phase 12 (zoom-in nav) or Phase 4 (lifecycle sync). Both fixes are confined to a single data class change + 3-4 call site updates each.

The 3 tech debt items (TD-A, TD-B, TD-C) are one-line changes each. TD-C requires adding `nodeId` to a side effect (same pattern as GAP-V1).

**Primary recommendation:** Implement all 5 items in a single plan (13-01-PLAN.md) since they are all small, independent, and test-first friendly.

---

## Verified Root Causes

### GAP-V1: Search result tap navigates to document root, not matched node

**Verified location:** `SearchViewModel.kt` line 83-85

```kotlin
// CONFIRMED: nodeId is available in item but discarded
fun onResultTapped(item: SearchResultItem) = intent {
    postSideEffect(SearchSideEffect.NavigateToNodeEditor(item.documentId))
    //                                                   ^ item.nodeId DROPPED
}
```

**Verified `SearchSideEffect`:** Line 104-106 of SearchViewModel.kt
```kotlin
sealed class SearchSideEffect {
    data class NavigateToNodeEditor(val documentId: String) : SearchSideEffect()
    // nodeId field MISSING
}
```

**Verified `SearchResultItem`:** Line 88-94 — has BOTH `nodeId: String` AND `documentId: String`. The `nodeId` is populated correctly during search result construction (line 57-63). The data is there; it is only dropped at the side effect emission step.

**Verified `SearchScreen.kt`:** Line 56-59 — handler receives `sideEffect.documentId` only, passes to `onNavigateToNodeEditor(sideEffect.documentId)`.

**Verified `AppNavHost.kt`:** Line 99-105 — search composable passes `{ documentId -> navController.navigate(AppRoutes.nodeEditor(documentId)) }`. No `rootNodeId` argument.

**Full fix chain:**
1. `SearchSideEffect.NavigateToNodeEditor`: add `nodeId: String` field
2. `SearchViewModel.onResultTapped`: pass `item.nodeId` in postSideEffect
3. `SearchScreen` sideEffect handler: forward `sideEffect.nodeId` to callback
4. `SearchScreen` composable param: change `onNavigateToNodeEditor: (String) -> Unit` to `(String, String) -> Unit`
5. `AppNavHost`: update lambda to call `AppRoutes.nodeEditor(documentId, nodeId)`

**AppRoutes.nodeEditor already supports rootNodeId:** Verified — `AppRoutes.nodeEditor(documentId: String, rootNodeId: String? = null)` exists. The infrastructure is fully in place.

---

### GAP-V2: NodeEditorScreen has no lifecycle sync hook

**Verified:** `NodeEditorScreen.kt` has NO `DisposableEffect`, `LifecycleEventObserver`, or `LocalLifecycleOwner`. The only `LaunchedEffect` blocks are:
- Line 102-104: `LaunchedEffect(documentId, rootNodeId)` — calls `viewModel.loadDocument`
- Line 106-116: `LaunchedEffect(Unit)` — collects sideEffectFlow

**Verified `NodeEditorViewModel.onScreenResumed/Paused`:** Lines 854-864
```kotlin
fun onScreenResumed() {
    viewModelScope.launch {
        triggerSync()        // pulls from server
    }
    resetInactivityTimer()   // starts 30s inactivity auto-sync
}

fun onScreenPaused() {
    inactivityTimerJob?.cancel()
    inactivityTimerJob = null
}
```
Both methods exist and are correct. They are simply never called.

**DocumentListScreen pattern for comparison:** `DocumentListScreen.kt` line 76-79 uses `LaunchedEffect(Unit) { viewModel.onScreenResumed() }` — this fires once on composition, NOT on every resume. This pattern is simpler but does NOT call `onScreenPaused()`. For `NodeEditorScreen`, the audit specifies `DisposableEffect` + `LifecycleEventObserver` to properly handle both resume and pause.

**Required imports (new to NodeEditorScreen.kt):**
```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

**Dependency situation:**
- `DisposableEffect` — in `androidx.compose.runtime` (already in project via compose-ui)
- `Lifecycle`, `LifecycleEventObserver` — in `androidx.lifecycle:lifecycle-runtime-ktx:2.9.0` (already explicit dep)
- `LocalLifecycleOwner` — in `androidx.lifecycle:lifecycle-runtime-compose` (TRANSITIVE only, not explicit)

`lifecycle-runtime-compose:2.9.0` is in the Gradle cache as a transitive dep (pulled by `lifecycle-viewmodel-compose` which is pulled by `hilt-navigation-compose`). Kotlin compiler can access it without adding it explicitly, but it is best practice to add it explicitly in `build.gradle.kts`. Recommended: add `implementation(libs.androidx.lifecycle.runtime.ktx)` already present; add `lifecycle-runtime-compose` to `libs.versions.toml` and `build.gradle.kts` for correctness.

**Exact code to insert** (from audit, confirmed correct for this codebase):
```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        if (event == Lifecycle.Event.ON_PAUSE) viewModel.onScreenPaused()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

**Placement:** Must be inside the `@Composable fun NodeEditorScreen(...)` block, before or after existing `LaunchedEffect` blocks (either location is fine — composable hooks are unordered relative to each other).

---

### TD-A: SearchRepository.kt docstring says "FTS5"

**Verified location:** `SearchRepository.kt` lines 8-16

```kotlin
/**
 * Searches nodes for [query] using FTS5 MATCH, filtered to [userId].
 * ...
 * Returns results ranked by bm25 relevance, or empty list if query has no FTS terms.
 */
```

**Issues:**
1. Line 9: "FTS5 MATCH" — should say "FTS4 MATCH"
2. Line 14: "ranked by bm25 relevance" — bm25() is FTS5-only; FTS4 uses ORDER BY updated_at (confirmed in SearchRepositoryImpl). Should say "ordered by last-updated"

**Fix:** Two-line docstring correction in SearchRepository.kt interface. No code change.

---

### TD-B: Dead `SyncLogger` import in `DocumentListViewModel`

**Verified location:** `DocumentListViewModel.kt` line 28:
```kotlin
import com.gmaingret.outlinergod.util.SyncLogger
```

**Verified:** No reference to `SyncLogger` anywhere in `DocumentListViewModel.kt` (291 lines, grep confirmed zero call sites). The `SyncLogger` class itself (`SyncLogger.kt`) is not dead — it exists as a utility object. Only the import in `DocumentListViewModel.kt` is dead.

**Fix:** Remove line 28 from `DocumentListViewModel.kt`. Kotlin lint will no longer warn.

---

### TD-C: Bookmark `targetNodeId` not wired to zoom-in navigation

**Verified `BookmarkListViewModel.kt` lines 48-51:**
```kotlin
"node" -> {
    bookmark.targetDocumentId?.let { docId ->
        postSideEffect(BookmarkListSideEffect.NavigateToNodeEditor(docId))
        // targetNodeId DROPPED
    }
}
```

**Verified `BookmarkListSideEffect`:** Lines 77-80:
```kotlin
sealed class BookmarkListSideEffect {
    data class NavigateToDocument(val documentId: String) : BookmarkListSideEffect()
    data class NavigateToNodeEditor(val documentId: String) : BookmarkListSideEffect()
    // nodeId field MISSING from NavigateToNodeEditor
}
```

**Verified `BookmarksScreen.kt` lines 55-57:**
```kotlin
is BookmarkListSideEffect.NavigateToNodeEditor -> {
    onNavigateToNodeEditor(sideEffect.documentId)
    // nodeId not passed
}
```

**Verified `AppNavHost.kt` lines 88-97:**
```kotlin
composable(AppRoutes.BOOKMARKS) {
    BookmarksScreen(
        onNavigateToDocument = { documentId ->
            navController.navigate(AppRoutes.nodeEditor(documentId))
        },
        onNavigateToNodeEditor = { documentId ->
            navController.navigate(AppRoutes.nodeEditor(documentId))
            // rootNodeId missing
        },
        onNavigateBack = { navController.popBackStack() }
    )
}
```

**Verified `BookmarkEntity`:** Has `targetNodeId: String?` field (line 17). Value is available.

**Full fix chain (mirrors GAP-V1 exactly):**
1. `BookmarkListSideEffect.NavigateToNodeEditor`: add `nodeId: String?` field (nullable since not all node bookmarks may have targetNodeId set)
2. `BookmarkListViewModel.onBookmarkTapped`: pass `bookmark.targetNodeId` in postSideEffect
3. `BookmarksScreen` sideEffect handler: forward `sideEffect.nodeId` to callback
4. `BookmarksScreen` composable param: change `onNavigateToNodeEditor: (String) -> Unit` to `(String, String?) -> Unit`
5. `AppNavHost`: update lambda to call `AppRoutes.nodeEditor(documentId, nodeId)`

**Currently unreachable:** `DocumentListViewModel.addBookmark` always creates `targetType = "document"`. The "node" branch in `onBookmarkTapped` can never be reached today. Fix is forward-compatibility work and low-risk.

---

## Test Impact Analysis

### Tests that MUST be updated

**SearchViewModelTest.kt — `onResultTapped posts NavigateToNodeEditor side effect` (line 199-215):**
This test currently asserts `expectSideEffect(SearchSideEffect.NavigateToNodeEditor("doc-1"))`. After adding `nodeId` to the side effect, this assertion will fail compilation since the data class constructor changes. Must update to `SearchSideEffect.NavigateToNodeEditor(documentId = "doc-1", nodeId = "n1")`.

**BookmarkListViewModelTest.kt — `onBookmarkTapped with node type posts NavigateToNodeEditor` (line 131-144):**
Currently asserts `expectSideEffect(BookmarkListSideEffect.NavigateToNodeEditor("doc-2"))`. After adding `nodeId?`, must update to `BookmarkListSideEffect.NavigateToNodeEditor(documentId = "doc-2", nodeId = "node-1")`.

### Tests that need NEW coverage added

**SearchViewModelTest.kt:** Add a test verifying `onResultTapped` passes `nodeId` through. The existing test structure already provides the pattern (orbit-test DSL, `testScheduler.advanceUntilIdle()`).

**BookmarkListViewModelTest.kt:** Update existing node-tap test to assert correct `nodeId` is in the side effect.

**NodeEditorViewModelTest.kt:** No existing `onScreenResumed/onScreenPaused` tests. Should add:
- `onScreenResumed triggers sync` — verify `syncRepository.pull` is called
- `onScreenPaused cancels inactivity timer` — verify behavior (harder to test directly; may verify by checking no sync fires after pause)
Test pattern: follow existing `NodeEditorViewModelTest` setup (no Robolectric, pure JVM, `mockk(relaxed = true)` for syncRepository).

### Tests with NO impact

- `NodeEditorScreenTest.kt` — structural tests, no assertion on lifecycle wiring
- `SearchRepositoryTest.kt` — not affected by docstring change
- `DocumentListViewModelTest.kt` — removing dead import does not affect behavior
- Backend tests (267 passing) — no backend changes in this phase

### Risk: DisposableEffect is not unit-testable

`DisposableEffect` + `LifecycleEventObserver` runs in the Compose composition tree. It is not accessible from JVM unit tests. The pattern works correctly at runtime but cannot be tested with JUnit 4 + Mockk. The correct approach:
- Write a ViewModel-level test (`onScreenResumed` triggers sync) to verify the callback body
- Accept that the `DisposableEffect` wiring itself is tested manually (UI layer is not unit-tested per CLAUDE.md rules: "UI correctness is verified manually per PLAN.md acceptance criteria")
- Document in VERIFICATION.md: manually verify sync fires when returning to node editor

---

## Architecture Patterns

### Pattern 1: Orbit MVI side effect with navigation data

All navigation side effects follow this pattern in this codebase:
1. Add field to sealed class variant
2. Update ViewModel to pass data in `postSideEffect`
3. Update Screen composable to forward data to callback param
4. Update callback param type signature
5. Update AppNavHost call site

The change propagates through exactly 4 files (ViewModel, Screen, AppNavHost, ViewModel test).

### Pattern 2: DisposableEffect lifecycle observer (NEW to this project)

This is the first `DisposableEffect` use in the project. The pattern:
```kotlin
// Inside a @Composable function:
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
            Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
            else -> {}
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

Key: `lifecycleOwner` as the `DisposableEffect` key ensures the observer is re-registered if the lifecycle owner changes (e.g., preview vs real activity). `onDispose` always removes the observer, preventing leaks.

**Import:** `androidx.lifecycle.compose.LocalLifecycleOwner` (from `lifecycle-runtime-compose`)
NOT `androidx.compose.ui.platform.LocalLifecycleOwner` (this was deprecated in Compose 1.7+).

### Pattern 3: LaunchedEffect (simpler alternative, already used)

`DocumentListScreen` uses `LaunchedEffect(Unit) { viewModel.onScreenResumed() }` — fires once on composition, does NOT handle pause or re-resume after navigation. This is intentional for DocumentList (no inactivity timer to cancel). NodeEditorScreen needs the full `DisposableEffect` pattern because it must also call `onScreenPaused()` to cancel the 30s inactivity timer.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Lifecycle observation in Compose | Custom lifecycle tracking | `DisposableEffect` + `LifecycleEventObserver` | Standard pattern; handles cleanup automatically |
| Navigation with optional param | Build new route | `AppRoutes.nodeEditor(docId, rootNodeId?)` | Already exists with optional rootNodeId param |

---

## Common Pitfalls

### Pitfall 1: Using wrong LocalLifecycleOwner import
**What goes wrong:** Importing `androidx.compose.ui.platform.LocalLifecycleOwner` instead of `androidx.lifecycle.compose.LocalLifecycleOwner`
**Why it happens:** The old import existed in compose-ui but was deprecated in Compose 1.7 and removed in 1.8. BOM 2025.04.01 uses Compose 1.8.0.
**How to avoid:** Use `androidx.lifecycle.compose.LocalLifecycleOwner`
**Warning signs:** Deprecation warning or "unresolved reference" compilation error

### Pitfall 2: Forgetting to update test assertions after data class constructor change
**What goes wrong:** Adding `nodeId: String` to `SearchSideEffect.NavigateToNodeEditor` causes compilation failure in `SearchViewModelTest.kt` line 213: `expectSideEffect(SearchSideEffect.NavigateToNodeEditor("doc-1"))` — missing nodeId arg
**How to avoid:** Grep for `NavigateToNodeEditor` and `NavigateToDocument` call sites before building

### Pitfall 3: Placing DisposableEffect inside conditional blocks
**What goes wrong:** Placing `DisposableEffect` inside `when (state.status) { is Success -> { DisposableEffect... } }` means the observer is only registered after load completes. Resume events that fire before load completes (edge case) would be missed.
**How to avoid:** Place `DisposableEffect` at the top level of `NodeEditorScreen`, before the `when (state.status)` branch, alongside the existing `LaunchedEffect` blocks.

### Pitfall 4: Making `nodeId` non-nullable in BookmarkListSideEffect
**What goes wrong:** `BookmarkEntity.targetNodeId` is `String?` (nullable). If `NavigateToNodeEditor.nodeId` is non-nullable, the `?.let` guard in the ViewModel would need restructuring.
**How to avoid:** Keep `nodeId: String?` in the side effect; use `AppRoutes.nodeEditor(documentId, nodeId)` — the helper already accepts `rootNodeId: String? = null`.

### Pitfall 5: Removing SyncLogger import before checking for call sites in other files
**What goes wrong:** SyncLogger might be used elsewhere and the import in DocumentListViewModel removed prematurely.
**Already verified:** SyncLogger is imported only in DocumentListViewModel.kt. SyncLogger.kt itself remains (it's a utility used elsewhere or for future use).

---

## Code Examples

### SearchSideEffect after fix
```kotlin
// Source: SearchViewModel.kt (to be modified)
sealed class SearchSideEffect {
    data class NavigateToNodeEditor(
        val documentId: String,
        val nodeId: String
    ) : SearchSideEffect()
}
```

### SearchViewModel.onResultTapped after fix
```kotlin
fun onResultTapped(item: SearchResultItem) = intent {
    postSideEffect(SearchSideEffect.NavigateToNodeEditor(
        documentId = item.documentId,
        nodeId = item.nodeId
    ))
}
```

### SearchScreen sideEffect handler after fix
```kotlin
// SearchScreen.kt — update onNavigateToNodeEditor param to (String, String)
// and update the handler:
is SearchSideEffect.NavigateToNodeEditor -> {
    onNavigateToNodeEditor(sideEffect.documentId, sideEffect.nodeId)
}
```

### AppNavHost search composable after fix
```kotlin
composable(AppRoutes.SEARCH) {
    SearchScreen(
        onNavigateToNodeEditor = { documentId, nodeId ->
            navController.navigate(AppRoutes.nodeEditor(documentId, nodeId))
        },
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### DisposableEffect in NodeEditorScreen (GAP-V2)
```kotlin
// Place at top of NodeEditorScreen composable function, alongside existing LaunchedEffects
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        if (event == Lifecycle.Event.ON_PAUSE) viewModel.onScreenPaused()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

### BookmarkListSideEffect after TD-C fix
```kotlin
sealed class BookmarkListSideEffect {
    data class NavigateToDocument(val documentId: String) : BookmarkListSideEffect()
    data class NavigateToNodeEditor(
        val documentId: String,
        val nodeId: String?    // nullable: not all node bookmarks guaranteed to have targetNodeId
    ) : BookmarkListSideEffect()
    data class ShowError(val message: String) : BookmarkListSideEffect()
}
```

---

## File Change Summary

| File | Change | Gap/TD | Risk |
|------|--------|--------|------|
| `SearchViewModel.kt` | Add `nodeId` to `SearchSideEffect.NavigateToNodeEditor`; pass in `onResultTapped` | GAP-V1 | LOW |
| `SearchScreen.kt` | Update `onNavigateToNodeEditor` param type; forward `nodeId` in handler | GAP-V1 | LOW |
| `AppNavHost.kt` | Update search lambda to pass `nodeId` to `AppRoutes.nodeEditor` | GAP-V1 | LOW |
| `SearchViewModelTest.kt` | Update `NavigateToNodeEditor` assertion; add nodeId to test item | GAP-V1 | LOW |
| `NodeEditorScreen.kt` | Add `DisposableEffect` + `LifecycleEventObserver`; add 4 imports | GAP-V2 | LOW |
| `SearchRepository.kt` | Fix 2-line docstring (FTS5→FTS4, bm25→updated_at) | TD-A | NONE |
| `DocumentListViewModel.kt` | Remove dead `SyncLogger` import (line 28) | TD-B | NONE |
| `BookmarkListViewModel.kt` | Add `nodeId?` to `NavigateToNodeEditor`; pass `targetNodeId` in onBookmarkTapped | TD-C | LOW |
| `BookmarksScreen.kt` | Update `onNavigateToNodeEditor` param type; forward `nodeId` in handler | TD-C | LOW |
| `AppNavHost.kt` | Update bookmarks lambda to pass `nodeId?` to `AppRoutes.nodeEditor` | TD-C | LOW |
| `BookmarkListViewModelTest.kt` | Update node-type test assertion to include `nodeId` | TD-C | LOW |
| `NodeEditorViewModelTest.kt` | Add 2 new tests: `onScreenResumed` triggers sync, `onScreenPaused` cancels timer | GAP-V2 | LOW |

**Total files changed:** 11 (2 shared: AppNavHost.kt has both GAP-V1 and TD-C changes)
**New dependencies:** `lifecycle-runtime-compose` (add to libs.versions.toml + build.gradle.kts) — already transitively available, explicit dep is best practice

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `LocalLifecycleOwner` from `compose-ui` | `LocalLifecycleOwner` from `lifecycle-runtime-compose` | Compose 1.8 deprecates the compose-ui version |
| `LaunchedEffect(Unit) { onResume() }` | `DisposableEffect + LifecycleEventObserver` | LaunchedEffect fires on composition only; DisposableEffect fires on each actual ON_RESUME event |

---

## Open Questions

None. All root causes verified in source. All fix paths are unambiguous.

---

## Sources

### Primary (HIGH confidence)
- `SearchViewModel.kt` — lines 83-85, 104-106, 88-94 — verified root cause
- `SearchScreen.kt` — lines 54-61 — verified handler
- `AppNavHost.kt` — lines 88-106 — verified both search and bookmark call sites
- `BookmarkListViewModel.kt` — lines 41-57, 77-80 — verified TD-C root cause
- `BookmarksScreen.kt` — lines 49-62 — verified handler
- `NodeEditorScreen.kt` — entire file — confirmed no lifecycle hook present
- `NodeEditorViewModel.kt` — lines 854-872 — confirmed onScreenResumed/Paused exist and correct
- `SearchRepository.kt` — lines 8-16 — confirmed docstring error
- `DocumentListViewModel.kt` — line 28 — confirmed dead import
- `BookmarkEntity.kt` — line 17 — confirmed `targetNodeId: String?` exists
- `AppRoutes.kt` — confirmed `nodeEditor(documentId, rootNodeId?)` already supports optional rootNodeId
- `SearchViewModelTest.kt` — line 213 — confirmed assertion that will break
- `BookmarkListViewModelTest.kt` — line 143 — confirmed assertion that will break
- `build.gradle.kts` + `libs.versions.toml` — confirmed `lifecycle-runtime-ktx:2.9.0` present; `lifecycle-runtime-compose` not explicitly declared
- Gradle cache — confirmed `lifecycle-runtime-compose-android:2.9.0` available transitively

### Secondary (MEDIUM confidence)
- Compose BOM 2025.04.01 POM — Compose runtime is version 1.8.0 (where `lifecycle-runtime-compose` is the canonical source of `LocalLifecycleOwner`)

---

## Metadata

**Confidence breakdown:**
- Root causes: HIGH — verified line-by-line in source files
- Fix patterns: HIGH — same patterns already used in this codebase (GAP-V1 mirrors existing side effect pattern; GAP-V2 mirrors DisposableEffect documentation)
- Test impact: HIGH — verified specific test assertions that will fail
- Dependency situation: HIGH — gradle cache confirmed

**Research date:** 2026-03-05
**Valid until:** 2026-04-05 (stable — no external dependencies changing)
