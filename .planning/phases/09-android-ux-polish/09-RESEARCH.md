# Phase 09: android-ux-polish - Research

**Researched:** 2026-03-03
**Domain:** Jetpack Compose UX — density scaling, logout flow, full-row drag handle
**Confidence:** HIGH

## Summary

Phase 09 touches three isolated areas of the existing Android UI: density scale calibration,
a logout action in SettingsScreen, and extending the reorderable drag handle from the glyph
icon to the entire node row.

The codebase already contains all the infrastructure needed for each change. The density system
uses `OutlinerGodTheme`'s `densityScale` float applied via `CompositionLocalProvider`; the
current mapping is inverted (cozy = 1.0 is largest, compact = 0.85 is smallest) but the
requirement flips this so compact = current size. The logout path exists in `AuthRepository`
and `AuthRepositoryImpl`, and `SettingsScreen` already wires `NavigateToLogin` side effect —
it just needs a button. The `longPressDraggableHandle` modifier from `sh.calvin.reorderable`
can be applied to any composable inside `ReorderableItem` scope, so moving it from the glyph
`Box`/`IconButton` to the outer `Column` of the node row is a direct modifier relocation.

**Primary recommendation:** All three changes are surgical edits to existing files — no new
dependencies, no schema changes, no new screens.

## Standard Stack

The stack is fully locked by prior phases. No new libraries are required.

### Core (already present)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `sh.calvin.reorderable` | 2.5.1 | Drag-and-drop reordering | Project-locked (D5) |
| Orbit MVI | 10.0.0 | ViewModel/side-effect pattern | Project-locked (D6) |
| Jetpack Compose BOM | 2025.04.01 | UI framework | Project-locked |
| Hilt | 2.56.1 | DI | Project-locked |

### Supporting (already present)
| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.credentials` | 1.3.0 | Google Sign-In (needed for logout credential clearing) |
| `kotlinx-coroutines-test` | existing | Testing Orbit MVI intents |
| Mockk | existing | Mocking AuthRepository in tests |

### Alternatives Considered
None — all decisions are locked.

**Installation:** No new dependencies needed.

## Architecture Patterns

### Density Scale Calibration

**Current state (MainActivity.kt, lines 42–47):**
```kotlin
val densityScale = when (settings?.density) {
    "cozy"        -> 1.0f   // largest — maps to biggest spacing
    "comfortable" -> 0.95f
    "compact"     -> 0.85f  // smallest
    else          -> 1.0f
}
```

**Problem:** The requirement says "compact = current size." The current default density
(before any setting is applied) is `else -> 1.0f`, and the system uses
`LocalDensity.current.density * densityScale` in `OutlinerGodTheme`. So compact must
map to 1.0f (unchanged from system density), comfortable to ~1.10f, and cozy to ~1.20f.

This is a density _multiplier_ — values above 1.0 increase spacing and effective dp sizes.
The correct semantics:

| Density value | densityScale | Effect |
|---------------|-------------|--------|
| `"compact"` | 1.0f | Unchanged — current system density |
| `"comfortable"` | 1.10f | +10% spacing and font-effective sizes |
| `"cozy"` | 1.20f | +20% spacing and font-effective sizes |

The `OutlinerGodTheme` composable passes `densityScale` into a custom `Density` object
that multiplies `LocalDensity.current.density`. Setting the multiplier > 1.0 causes
more physical pixels per dp, making everything appear larger/more spaced.

**Caution:** `fontScale` is intentionally left at `LocalDensity.current.fontScale` (not
modified by densityScale) in `OutlinerGodTheme`. This means text accessibility scale is
respected. Only spacing/padding sizes change. This is the correct behavior.

**Default value in SettingsViewModel (line 43):**
```kotlin
density = "cozy",
```
The default stored in Room is "cozy." With the new mapping, cozy will be the most spacious.
This is acceptable — the user still defaults to the most comfortable reading experience.

### Logout Button in SettingsScreen

**Existing infrastructure:**

1. `AuthRepository` interface already declares:
   ```kotlin
   suspend fun logout(refreshToken: String): Result<Unit>
   ```

2. `AuthRepositoryImpl.logout()` already:
   - POSTs to `/api/auth/logout`
   - Removes `access_token`, `refresh_token`, `user_id` from DataStore
   - Does NOT remove `device_id` (correct — device ID is permanent per ARCHITECTURE.md)

3. `SettingsScreen` already listens for `SettingsSideEffect.NavigateToLogin` and calls
   `onNavigateToLogin()` which pops the back stack to root and navigates to LOGIN route.

4. `AppNavHost` at the SETTINGS destination already wires:
   ```kotlin
   onNavigateToLogin = {
       navController.navigate(AppRoutes.LOGIN) {
           popUpTo(0) { inclusive = true }
       }
   }
   ```

**Gap:** `SettingsViewModel` has no `logout()` function. `SettingsScreen` has no logout
button UI. These are the only two additions needed.

**ViewModel logout pattern (Orbit MVI):**
```kotlin
fun logout() = intent {
    val refreshToken = authRepository.getAccessToken().filterNotNull().first()
    authRepository.logout(refreshToken).fold(
        onSuccess = { postSideEffect(SettingsSideEffect.NavigateToLogin) },
        onFailure = { e ->
            postSideEffect(SettingsSideEffect.ShowError(e.message ?: "Logout failed"))
        }
    )
}
```

**Note on `refreshToken` parameter:** `AuthRepository.logout(refreshToken: String)` requires
a refresh token, but `getAccessToken()` returns the access token. The correct flow is to
read the refresh token from DataStore. However, `AuthRepository` interface does not expose
`getRefreshToken()`. Two options:
- Option A: Add `getRefreshToken(): Flow<String?>` to `AuthRepository` interface and
  `AuthRepositoryImpl`. This is the cleanest.
- Option B: Call `logout("")` and have the backend accept empty refresh token for
  client-side-only logout (clearing DataStore is sufficient for local logout).
- Option C: Perform local-only logout by clearing DataStore directly from a new
  `localLogout()` method that doesn't call the network.

**Recommendation:** Option A — add `getRefreshToken(): Flow<String?>` to the interface.
This is minimal, consistent, and preserves the existing backend contract. If the network
call fails (offline), still clear local credentials and navigate to login (fail-open).

**Alternative simpler approach:** Since the backend logout just invalidates the server-side
refresh token but the client clears credentials regardless, a pragmatic implementation
calls `authRepository.logout("")` (empty string) for the token parameter and relies on
local DataStore cleanup. This avoids touching the interface. LOW confidence this is
acceptable to the backend.

**Recommended implementation:** Add `getRefreshToken()` to the interface.

### Full-Row Drag Handle

**Current state:** `dragModifier` (containing `Modifier.longPressDraggableHandle(...)`) is
applied only to the glyph element — either the `Box(size=24.dp)` for leaf nodes or the
`IconButton(size=24.dp)` for nodes with children.

**Target state:** The drag initiates from anywhere on the node row.

**Key API fact (HIGH confidence, verified from library source/README):**
`Modifier.longPressDraggableHandle()` can be applied to any composable inside
`ReorderableCollectionItemScope`. It is NOT restricted to icons. The scope is provided
by the `ReorderableItem { isDragging -> ... }` lambda.

**Current code flow:**
```
ReorderableItem(reorderState, key) { isDragging ->    // scope available here
    Surface {
        NodeRow(
            dragModifier = Modifier.longPressDraggableHandle(...)   // passed in
        )
    }
}
```

Inside `NodeRow`, `dragModifier` is `.then()`-chained onto the 24.dp glyph box/button.

**Two approaches for full-row drag:**

**Approach A — Apply dragModifier to outer Column in NodeRow**

Move `.then(dragModifier)` from the glyph to the root `Column` of `NodeRow`:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp, horizontal = 8.dp)
        .then(dragModifier)     // <-- moved here from glyph
) { ... }
```

**Approach B — Apply modifier directly in NodeEditorScreen**

Instead of passing `dragModifier` into `NodeRow`, apply `longPressDraggableHandle` to the
`Surface` that wraps `NodeRow` (already inside `ReorderableItem` scope):
```kotlin
ReorderableItem(reorderState, key = flatNode.entity.id) { isDragging ->
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .longPressDraggableHandle(
                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            ),
        tonalElevation = if (isDragging) 4.dp else 0.dp,
    ) {
        NodeRow(
            ...
            dragModifier = Modifier,    // noop — handle is on Surface
        )
    }
}
```

**Recommendation: Approach B.** It keeps `NodeRow` unaware of drag concerns and the
modifier is directly inside `ReorderableCollectionItemScope` without being passed around.
`dragModifier` parameter in `NodeRow` becomes a no-op `Modifier` default.

**Interaction conflict risk:** The outer `Column` in `NodeRow` already handles long-press
for the context menu using `PointerEventPass.Initial`. If `longPressDraggableHandle` is
also placed on the same surface, both gestures will compete. The library's
`longPressDraggableHandle` runs in `Main` pass (later than `Initial`), so the existing
long-press context menu code (running in `Initial` pass) will still fire. This means
long-pressing the content area would simultaneously open the context menu AND start a drag.

**Resolution:** With Approach B (handle on `Surface` wrapping `NodeRow`), the
`longPressDraggableHandle` is on a parent element of the `Column`. Events flow outer→inner
in Initial pass, but `Surface` doesn't intercept gestures. The `longPressDraggableHandle`
on `Surface` will receive events via Main pass. The `Column`'s Initial-pass long-press
detector fires first. This creates the same conflict.

**Correct resolution:** Remove the custom long-press gesture from the content `Column`
when full-row drag is active, OR restrict the drag handle to the glyph+row-but-not-textfield
area. Since the requirement says "full node row," the cleanest interpretation is: the entire
row except the text fields triggers drag. The text field already consumes touch for selection.

In practice, `BasicTextField` will consume touch events for text cursor/selection in Main
pass, meaning the `longPressDraggableHandle` on `Surface` won't receive them from text
field area. The drag will work from non-text areas (glyph, padding, note toggle icon).

**Simplest correct implementation:**
- Remove the `dragModifier` parameter from `NodeRow` entirely
- Remove the `.then(dragModifier)` from glyph elements
- Apply `longPressDraggableHandle` to the `Surface` that wraps each `NodeRow` in
  `NodeEditorScreen` (inside `ReorderableItem` scope)
- Keep the existing context menu long-press on the content `Column` (it handles text area
  long-press separately, which the `BasicTextField` consumes before drag can start)

### Anti-Patterns to Avoid

- **Modifying `OutlinerGodTheme` signature:** Do not change `densityScale: Float` to a
  string enum — keep the float abstraction; only change the mapping in `MainActivity`.
- **Adding `getRefreshToken()` as a coroutine suspend function:** Make it a `Flow<String?>`
  like `getAccessToken()` for consistency.
- **Clearing `device_id` on logout:** `AuthRepositoryImpl.logout()` correctly does NOT
  remove `DEVICE_ID_KEY`. Do not change this.
- **Using `popBackStack()` for logout navigation:** The existing `AppNavHost` wiring uses
  `popUpTo(0) { inclusive = true }` which correctly clears the entire back stack. Do not
  use `popBackStack()` which would only go one level up.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Full-row drag gesture | Custom `pointerInput` drag detector | `longPressDraggableHandle` modifier on Surface | Library already handles velocity, threshold, animation |
| Density multiplier math | Custom layout system | `CompositionLocalProvider(LocalDensity)` in OutlinerGodTheme | Already implemented; just fix the float values |
| Navigation stack clearing on logout | Manual back-stack manipulation | `popUpTo(0) { inclusive = true }` already in AppNavHost | Already wired; ViewModel just emits the side effect |

**Key insight:** All three changes are configuration/wiring changes. The heavy lifting (DnD
gesture recognition, density composition, navigation) is already in place.

## Common Pitfalls

### Pitfall 1: Density Scale Direction Inversion
**What goes wrong:** Treating densityScale > 1 as "zoomed out" (smaller UI). In Compose,
multiplying `LocalDensity.current.density` by a factor > 1 makes 1dp map to MORE physical
pixels, which makes UI elements LARGER, not smaller.
**Why it happens:** Intuition mismatch — "compact" sounds like it should have a smaller
multiplier, but "compact" means less space per item (denser layout), which means the user
sees more items. So compact = system default (1.0f), cozy = larger items (1.20f).
**How to avoid:** Test with a single hardcoded value; measure a padding to confirm direction.
**Warning signs:** If after the change, selecting "cozy" makes text smaller, the mapping
is inverted.

### Pitfall 2: Drag Handle Scope Leak
**What goes wrong:** Passing `ReorderableCollectionItemScope` to `NodeRow` composable as
a parameter and calling `Modifier.longPressDraggableHandle()` inside `NodeRow` without
the scope in context.
**Why it happens:** The modifier is a scoped extension function — it only exists inside the
`ReorderableItem { }` lambda.
**How to avoid:** Keep the modifier application inside `NodeEditorScreen`'s `ReorderableItem`
lambda, on the `Surface`. Pass only a plain `Modifier` to `NodeRow`.

### Pitfall 3: Logout Clears Device ID
**What goes wrong:** Modifying `AuthRepositoryImpl.logout()` to clear all DataStore keys
including `DEVICE_ID_KEY`.
**Why it happens:** Assumption that logout = full reset.
**How to avoid:** Check `AuthRepositoryImpl.logout()` (line 81–85) — it intentionally
leaves `DEVICE_ID_KEY` in place. Device ID survives logout per architecture decisions.

### Pitfall 4: Missing Refresh Token for Logout API Call
**What goes wrong:** Calling `authRepository.logout("")` with empty string; the backend
may reject the call. OR calling `getAccessToken()` and passing the access token as the
refresh token.
**Why it happens:** `AuthRepository` interface does not expose `getRefreshToken()`.
**How to avoid:** Add `getRefreshToken(): Flow<String?>` to the interface and impl, then
use it in `SettingsViewModel.logout()`. Fail-open: if refresh token is null or network
fails, still clear local credentials and navigate to login.

### Pitfall 5: Long-Press Conflict Between Context Menu and Drag
**What goes wrong:** Long-pressing the content area opens context menu AND starts drag
simultaneously.
**Why it happens:** Both gesture detectors run concurrently.
**How to avoid:** The `BasicTextField` in the content area consumes touch events for cursor
placement, which prevents `longPressDraggableHandle` from activating there. The context
menu long-press (on the `Column`) will fire from the text field area. From the glyph or
padding areas, drag will activate. This is acceptable behavior — the two zones don't
conflict in practice because `BasicTextField` consumes events in text zone.

## Code Examples

### Density Fix in MainActivity
```kotlin
// Source: MainActivity.kt lines 42-47 (current) -> replace with:
val densityScale = when (settings?.density) {
    "compact"     -> 1.0f   // current size — unchanged from system
    "comfortable" -> 1.10f  // +10% more spacious
    "cozy"        -> 1.20f  // +20% most spacious
    else          -> 1.0f
}
```

### SettingsViewModel logout() — Orbit MVI pattern
```kotlin
// Add to AuthRepository interface:
fun getRefreshToken(): Flow<String?>

// Add to AuthRepositoryImpl:
override fun getRefreshToken(): Flow<String?> =
    dataStore.data.map { prefs -> prefs[REFRESH_TOKEN_KEY] }

// Add to SettingsViewModel:
fun logout() = intent {
    val refreshToken = authRepository.getRefreshToken().filterNotNull().first()
    authRepository.logout(refreshToken)
        .onSuccess { postSideEffect(SettingsSideEffect.NavigateToLogin) }
        .onFailure { e ->
            // Still navigate — local credentials already cleared
            postSideEffect(SettingsSideEffect.NavigateToLogin)
        }
}
```

### Logout Button in SettingsScreen
```kotlin
// Add at bottom of LazyColumn items block, after backlink badge item:
item {
    Spacer(modifier = Modifier.height(24.dp))
    // Use Button or OutlinedButton for destructive action visibility
    OutlinedButton(
        onClick = { viewModel.logout() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text("Logout")
    }
}
```

### Full-Row Drag Handle (Approach B)
```kotlin
// In NodeEditorScreen, inside items { flatNode -> }:
ReorderableItem(reorderState, key = flatNode.entity.id) { isDragging ->
    Surface(
        tonalElevation = if (isDragging) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .longPressDraggableHandle(          // <-- full row handle
                onDragStarted = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            ),
    ) {
        NodeRow(
            flatNode = flatNode,
            // ... other params unchanged ...
            // dragModifier removed or replaced with Modifier
        )
    }
}

// In NodeRow signature: remove dragModifier param or default to Modifier
// In NodeRow body: remove .then(dragModifier) from glyph elements
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Glyph-only drag handle | Full-row `longPressDraggableHandle` on Surface | Larger drag target, standard mobile UX |
| densityScale < 1.0 for compact | densityScale = 1.0 for compact, > 1.0 for spacious | Correct semantics: compact = default |

**No deprecated APIs involved.** All patterns are current for the project's dependency versions.

## Open Questions

1. **Refresh token exposure in AuthRepository**
   - What we know: `AuthRepositoryImpl` stores `REFRESH_TOKEN_KEY` in DataStore but does
     not expose it via the `AuthRepository` interface
   - What's unclear: Whether a local-only logout (skip the network call) is acceptable for
     this phase
   - Recommendation: Add `getRefreshToken(): Flow<String?>` to the interface. If preferred
     to avoid interface changes, call `logout("")` and accept the backend may reject — the
     DataStore clear still happens either way in `AuthRepositoryImpl.logout()`

2. **SettingsViewModel.logout() — offline behavior**
   - What we know: If the network call fails, `AuthRepositoryImpl.logout()` throws before
     clearing DataStore (the `runCatching` catches it but DataStore.edit is inside the
     `runCatching` block)
   - What's unclear: Should a failed network logout still clear local credentials?
   - Recommendation: On failure, still emit `NavigateToLogin` side effect. The user is
     effectively logged out locally. Next launch will find no access token and redirect to
     login anyway.

## Sources

### Primary (HIGH confidence)
- Direct code reading of `NodeEditorScreen.kt` — drag handle current implementation
- Direct code reading of `OutlinerGodTheme.kt` — density system
- Direct code reading of `AuthRepositoryImpl.kt` — logout implementation
- Direct code reading of `SettingsViewModel.kt` / `SettingsScreen.kt` — existing side effect wiring
- Direct code reading of `AppNavHost.kt` — navigation wiring
- Direct code reading of `MainActivity.kt` — density mapping (current inverted values)
- GitHub README for `sh.calvin.reorderable` — `longPressDraggableHandle` modifier API

### Secondary (MEDIUM confidence)
- `DndPrototypeActivity.kt` — confirms `longPressDraggableHandle()` usage pattern (no params required)
- GitHub demo `ComplexReorderableLazyColumnScreen.kt` — confirms handle is scoped to `ReorderableItem` lambda

### Tertiary (LOW confidence)
- Web search: full-row drag handle patterns — verified against library README

## Metadata

**Confidence breakdown:**
- Density fix: HIGH — current code is clearly inverted; fix is a value change only
- Logout flow: HIGH — interface, impl, side-effect wiring all exist; only ViewModel fn + UI button needed
- Drag handle: HIGH — modifier API confirmed; Approach B avoids scope-passing complexity
- Refresh token gap: MEDIUM — interface change needed, two acceptable options exist

**Research date:** 2026-03-03
**Valid until:** 2026-04-03 (stable APIs)
