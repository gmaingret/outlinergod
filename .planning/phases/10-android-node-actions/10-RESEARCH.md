# Phase 10: android-node-actions - Research

**Researched:** 2026-03-04
**Domain:** Jetpack Compose gesture handling, swipe-to-action, IME toolbar, node state management
**Confidence:** HIGH (all core APIs verified against official docs and Material3 1.4.0 release notes)

## Summary

Phase 10 adds three UI behaviors on top of the existing NodeEditorScreen: swipe gestures on
node rows (right = toggle complete, left = delete with undo), a persistent action toolbar above
the keyboard, and removal of the ModalBottomSheet context menu. All required Compose APIs are
stable in the current BOM (2025.04.01 -> Material3 1.4.0). The existing codebase already has
the `completed` field on `NodeEntity` and the `onCompletedToggled()` / `deleteNode()` intents
on `NodeEditorViewModel`, so this phase is primarily UI plumbing.

The key technical risk is gesture conflict between SwipeToDismissBox (horizontal swipe) and the
existing `longPressDraggableHandle` (long-press vertical drag). Official Compose documentation
confirms these gestures operate on different axes and different detection latencies (swipe fires
on horizontal threshold, DnD fires after long-press timeout), which means they coexist with
proper composable nesting: `ReorderableItem` wraps `SwipeToDismissBox` wraps `Surface`.

The IME toolbar is implemented via `Scaffold.bottomBar` with `Modifier.imePadding()` — a
one-liner approach that automatically lifts the toolbar above the keyboard. No `windowSoftInputMode`
manifest change is needed because `MainActivity` already calls `enableEdgeToEdge()`.

**Primary recommendation:** Use `SwipeToDismissBox` (Material3 stable) for swipe gestures.
Use `Scaffold.bottomBar` + `imePadding()` for the keyboard toolbar. Keep existing `NodeEntity.completed`
and `NodeEditorViewModel.onCompletedToggled()` as-is — no schema change needed.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.compose.material3:material3` | 1.4.0 (via BOM 2025.04.01) | SwipeToDismissBox, SnackbarHost, BottomAppBar | Already in project; SwipeToDismissBox is stable in 1.4.0 |
| `androidx.compose.foundation:foundation` | via BOM | `WindowInsets.ime`, `imePadding()`, `imeNestedScroll()` | Already in project |
| `sh.calvin.reorderable:reorderable` | 2.5.1 | Existing DnD; `longPressDraggableHandle` | Already in project; must not upgrade |
| `org.orbit-mvi:orbit-core/viewmodel/test` | 10.0.0 | Existing Orbit MVI pattern for ViewModel | Already in project |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `kotlinx.coroutines` | 1.10.1 | `rememberCoroutineScope()` for snackbar launch | Already in project |
| `androidx.compose.material3` Icons | via BOM | Delete, Check, FormatIndentIncrease icons | For toolbar and swipe background |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| SwipeToDismissBox | `detectHorizontalDragGestures` | SwipeToDismissBox gives built-in animation, anchor snapping, state machine; custom drags require all of this hand-rolled |
| SwipeToDismissBox | `anchoredDraggable` | Same: more code, no benefit for this use case |
| Scaffold.bottomBar + imePadding | Custom Box pinned to IME bottom | Scaffold handles the inset math automatically; custom approach requires `WindowInsets.ime.getBottom()` polling |

**Installation:** No new dependencies. All required APIs are in existing dependencies.

## Architecture Patterns

### Recommended Composable Structure for NodeRow

The critical nesting order resolves gesture conflicts:

```
LazyColumn
  items(key = node.id)
    ReorderableItem(reorderState, key = node.id) { isDragging ->   ← DnD scope
      SwipeToDismissBox(state, backgroundContent = {...})            ← Swipe scope
        Surface(modifier = .longPressDraggableHandle(...))           ← DnD handle
          NodeRow(...)                                               ← Content
```

**Why this order works:**
- `ReorderableItem` must be the outermost keyed composable so the reorderable library tracks it
- `SwipeToDismissBox` is inside `ReorderableItem` — this is the documented pattern (swipe wraps content)
- `longPressDraggableHandle` stays on `Surface` as it is today (D26 decision)
- Horizontal swipe fires on detecting X-axis movement; long-press fires only after 500ms timeout.
  These gestures self-discriminate by axis and timing — no explicit conflict resolution needed
- If the user actually starts a long-press drag, `detectDragGesturesAfterLongPress` consumes pointer
  events, which prevents SwipeToDismissBox from also handling them

### Scaffold Structure for IME Toolbar

```kotlin
Scaffold(
    topBar = { TopAppBar(...) },
    bottomBar = {
        // Shown only when a node is focused
        if (state.focusedNodeId != null) {
            NodeActionToolbar(
                onIndent = { ... },
                onOutdent = { ... },
                onAddChild = { ... },
                onToggleComplete = { ... },
                onDelete = { ... },
                modifier = Modifier.imePadding()   // lifts above keyboard
            )
        }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) }
) { paddingValues ->
    LazyColumn(modifier = Modifier.padding(paddingValues)) { ... }
}
```

**IME toolbar note:** `Modifier.imePadding()` on the `bottomBar` composable automatically adds
bottom padding equal to the current IME height. Because `MainActivity.enableEdgeToEdge()` is
already called, no `windowSoftInputMode` change is needed in the manifest. The toolbar slides
up with the keyboard animation automatically.

### SwipeToDismissBox State Per-Item Pattern

Each node row gets its own `SwipeToDismissBoxState`. State must use `remember(nodeId)` to prevent
state leakage when items are recomposed or reordered.

```kotlin
// Source: developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss
val swipeState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onToggleComplete()
                false  // Return false = snap back to Settled (NOT dismiss)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onDelete()
                true   // Return true = animate item out (item is removed from list)
            }
            SwipeToDismissBoxValue.Settled -> true
        }
    }
)
```

**Key insight on `confirmValueChange` return value:**
- `false` = veto the state change, snap the item back to `Settled` position
- `true` = confirm the state change, allow item to animate out
- This is how swipe-right-toggle-complete works: fire the action, then snap back

### Snackbar Undo Pattern

The node is soft-deleted immediately (optimistic). The snackbar gives 5 seconds to undo. If the
user taps Undo, the node is restored. Pattern uses `rememberCoroutineScope()` because snackbars
are triggered from composable event callbacks.

```kotlin
// Source: developer.android.com/develop/ui/compose/components/snackbar
val scope = rememberCoroutineScope()
val snackbarHostState = remember { SnackbarHostState() }

// In swipe handler:
onDelete = {
    viewModel.deleteNode(nodeId)  // soft delete (marks deletedAt)
    scope.launch {
        val result = snackbarHostState.showSnackbar(
            message = "Node deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short  // ~4s
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.restoreNode(nodeId)  // clear deletedAt
        }
    }
}
```

### Anti-Patterns to Avoid

- **Nesting `longPressDraggableHandle` inside `SwipeToDismissBox` content** — this would mean the
  DnD handle fires through the swipe layer, causing the swipe to never activate while finger is
  on the handle. Keep `longPressDraggableHandle` on `Surface`, which is the content of `SwipeToDismissBox`.
- **Sharing `SwipeToDismissBoxState` across items** — each item must have its own state instance
  via `remember(nodeId)` or swiping one item affects others
- **Using `rememberSaveable` for swipe state** — `rememberSwipeToDismissBoxState` internally uses
  `rememberSaveable`, which can restore a swiped-away state after navigation. Explicitly reset
  state or use `key(nodeId)` to ensure clean state per item
- **Placing `imePadding()` on the LazyColumn** instead of `bottomBar` — this causes the list to
  compress when the keyboard opens but does NOT lift the toolbar

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Swipe gesture with spring-back animation | Custom `pointerInput` + animation | `SwipeToDismissBox` | SwipeToDismissBox provides: anchor state machine, spring animation back to Settled, velocity-based fling, gesture conflict handling |
| Keyboard-aware bottom bar positioning | Custom `WindowInsets.ime.getBottom()` listener | `Modifier.imePadding()` on bottomBar | imePadding automatically animates with the keyboard and handles edge-to-edge correctly |
| Snackbar timeout + action button | Custom Timer + AlertDialog | `SnackbarHostState.showSnackbar(actionLabel=)` | Returns `SnackbarResult` enum; handles queue, accessibility, auto-dismiss |
| "Toggle complete" visual strike-through | Separate `Text` composable with strike | `VisualTransformation` or `textStyle` with `TextDecoration.LineThrough` | `MarkdownVisualTransformation` already applies SpanStyles; adding a full-text strikethrough via `textStyle` is simpler for the completed state |

**Key insight:** The `completed` field already exists on `NodeEntity` (Int, 0/1) and `onCompletedToggled()`
already exists on `NodeEditorViewModel`. The entity schema does NOT need to change for Phase 10.

## Common Pitfalls

### Pitfall 1: Gesture conflict — swipe vs DnD both triggering
**What goes wrong:** User tries to swipe a node, but the long-press threshold fires first and DnD
activates instead. Or vice versa.
**Why it happens:** `longPressDraggableHandle` uses `detectDragGesturesAfterLongPress`, which requires
holding the finger still for ~500ms. `SwipeToDismissBox` fires on horizontal velocity after a small
touch slop.
**How to avoid:** SwipeToDismissBox detects horizontal movement immediately (touch slop ~8dp).
Long-press requires 500ms without movement. These are mutually exclusive in practice: a quick
horizontal swipe never waits long enough to trigger DnD. Only vertical/ambiguous slow drags can
be ambiguous — those are DnD, not swipes. No explicit fix needed, but verify empirically.
**Warning signs:** If the glyph's `pointerInput` horizontal drag (indent/outdent at 40dp threshold)
conflicts — this fires via `PointerEventPass.Initial` (capture pass) which fires before SwipeToDismissBox's
`Main` pass. This means a horizontal glyph drag may prevent SwipeToDismissBox from seeing the event.
To avoid, only apply SwipeToDismissBox to the area outside the glyph (or accept that horizontal
glyph drags take priority over swipe).

### Pitfall 2: SwipeToDismissBox state survives recomposition
**What goes wrong:** User swipes an item to complete. State resets to `Settled` (via `confirmValueChange`
returning `false`). But then scrolling causes recomposition, and the item shows a partially-swiped
state.
**Why it happens:** `rememberSwipeToDismissBoxState` uses `rememberSaveable`. If the node ID key
changes or the item is recycled, the saved state may not apply to the right item.
**How to avoid:** Use `key(flatNode.entity.id)` around the `SwipeToDismissBox` or ensure the
`remember` call explicitly keys on `nodeId` so state is scoped to that node.

### Pitfall 3: Undo restore after Room has already synced the deletion
**What goes wrong:** User swipes-to-delete. Soft-delete marks `deletedAt`. WorkManager sync fires
within 4 seconds and pushes the deletion to the server. User taps Undo at 3 seconds — node is
restored locally but may already be deleted on server.
**Why it happens:** WorkManager 15-minute periodic sync usually won't fire in 4 seconds. But
`resetInactivityTimer()` fires a sync after 30s, so the undo window is safe. The real risk is
only if a sync is already in-flight when the delete happens.
**How to avoid:** Undo restores the node locally by clearing `deletedAt` and updating `deletedHlc`
to a new HLC. On next sync, this "undelete" pushes to server. Since deletion-wins is a hard rule
from CLAUDE.md, this is a real risk — **document that undo is best-effort if already synced**.
For now, treat it as a local-only undo (the existing 30s sync timer makes it safe in most cases).

### Pitfall 4: Persistent toolbar obscures content when keyboard is closed
**What goes wrong:** Toolbar appears above the keyboard but also floats above the list content
when the keyboard is closed and no node is focused.
**Why it happens:** `bottomBar` always renders in Scaffold, taking bottom space even when the
keyboard is closed.
**How to avoid:** Conditionally render the toolbar based on `state.focusedNodeId != null`.
When no node is focused, `bottomBar` renders nothing, and Scaffold computes zero bottom space.

### Pitfall 5: `SnackbarHostState.showSnackbar` is called from wrong scope
**What goes wrong:** Snackbar launched from within `confirmValueChange` callback — this callback
runs in the composition, not a coroutine. `showSnackbar` is a suspending function and can only
be called from a coroutine.
**Why it happens:** `confirmValueChange` is a synchronous lambda, not a suspend lambda.
**How to avoid:** Use `rememberCoroutineScope()` at the composable level and call
`scope.launch { snackbarHostState.showSnackbar(...) }` from within `confirmValueChange` or
a `LaunchedEffect`. Do NOT call `showSnackbar` directly in `confirmValueChange`.

### Pitfall 6: ModalBottomSheet contextMenuNodeId state not cleaned up
**What goes wrong:** `contextMenuNodeId` remains set in `NodeEditorUiState` after removing the
BottomSheet. If `showContextMenu()` is called somewhere and nothing dismisses it, nothing visible
happens but state is dirty.
**How to avoid:** Remove `contextMenuNodeId` from `NodeEditorUiState`. Remove `showContextMenu()`,
`dismissContextMenu()` intents. Remove `onLongPress` parameter from `NodeRow`.

## Code Examples

### SwipeToDismissBox for Node Row (toggle + delete)
```kotlin
// Source: developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss
val swipeState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onToggleComplete()
                false  // snap back to settled — NOT a dismiss
            }
            SwipeToDismissBoxValue.EndToStart -> {
                true   // allow item to animate away; caller handles removal
            }
            SwipeToDismissBoxValue.Settled -> true
        }
    }
)

SwipeToDismissBox(
    state = swipeState,
    enableDismissFromStartToEnd = true,
    enableDismissFromEndToStart = true,
    backgroundContent = {
        val color = when (swipeState.dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        }
        Box(
            modifier = Modifier.fillMaxSize().background(color),
            contentAlignment = when (swipeState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
        ) {
            Icon(
                imageVector = when (swipeState.dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd ->
                        if (flatNode.entity.completed == 1) Icons.Default.Undo
                        else Icons.Default.Check
                    else -> Icons.Default.Delete
                },
                contentDescription = null,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
) {
    Surface(
        tonalElevation = if (isDragging) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .longPressDraggableHandle(onDragStarted = { haptic.performHapticFeedback(...) })
    ) {
        NodeRow(...)
    }
}
```

### Strike-through for completed nodes
```kotlin
// Completed visual: pass isCompleted to NodeRow, apply to textStyle
BasicTextField(
    value = textFieldValue,
    textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = if (flatNode.entity.completed == 1)
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.onSurface,
        textDecoration = if (flatNode.entity.completed == 1)
            TextDecoration.LineThrough
        else
            TextDecoration.None
    ),
    ...
)
```

Note: `textStyle.textDecoration` applies to the entire field. `MarkdownVisualTransformation`
applies span-level decorations on top. Both can coexist — `VisualTransformation` styles layer
over `textStyle` styles in Compose.

### Node action toolbar above keyboard
```kotlin
// Source: developer.android.com/develop/ui/compose/system/insets
@Composable
fun NodeActionToolbar(
    nodeId: String,
    isCompleted: Boolean,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onAddChild: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(modifier = modifier) {
        IconButton(onClick = onOutdent) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent")
        }
        IconButton(onClick = onIndent) {
            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent")
        }
        IconButton(onClick = onAddChild) {
            Icon(Icons.Default.Add, "Add child")
        }
        IconButton(onClick = onToggleComplete) {
            Icon(
                if (isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                "Toggle complete"
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

// Usage in Scaffold:
Scaffold(
    bottomBar = {
        val focusedNode = state.flatNodes.firstOrNull { it.entity.id == state.focusedNodeId }
        if (focusedNode != null) {
            NodeActionToolbar(
                nodeId = focusedNode.entity.id,
                isCompleted = focusedNode.entity.completed == 1,
                ...,
                modifier = Modifier.imePadding()  // lifts above keyboard
            )
        }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) }
) { paddingValues -> ... }
```

### Undo snackbar for delete
```kotlin
// Source: developer.android.com/develop/ui/compose/components/snackbar
val scope = rememberCoroutineScope()
val snackbarHostState = remember { SnackbarHostState() }

// Called from swipe EndToStart confirmValueChange:
fun handleDeleteWithUndo(nodeId: String) {
    viewModel.deleteNode(nodeId)
    scope.launch {
        val result = snackbarHostState.showSnackbar(
            message = "Node deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.restoreNode(nodeId)
        }
    }
}
```

### ViewModel: restoreNode intent
```kotlin
// New intent on NodeEditorViewModel (not currently present)
fun restoreNode(nodeId: String) = intent {
    val deviceId = authRepository.getDeviceId().first()
    val hlc = hlcClock.generate(deviceId)
    val now = System.currentTimeMillis()
    // Clear deletedAt: Room's softDeleteNode set it; restore by updating directly
    val entity = nodeDao.getNodesByDocumentSync(state.documentId)
        .firstOrNull { it.id == nodeId } ?: return@intent
    nodeDao.updateNode(
        entity.copy(
            deletedAt = null,
            deletedHlc = hlc,
            updatedAt = now,
            deviceId = deviceId,
        )
    )
    resetInactivityTimer()
}
```

Note: `NodeDao.updateNode()` already uses `@Update` which sets all columns including `deleted_at`.
Setting it to `null` effectively undeletes the node.

## What Changes in Existing Files

### NodeEditorScreen.kt
1. Add `SwipeToDismissBox` wrapping `Surface` inside each `ReorderableItem`
2. Add `Scaffold.bottomBar` with `NodeActionToolbar` (new private composable)
3. Add `Scaffold.snackbarHost` with `SnackbarHost`
4. Remove the `ModalBottomSheet` block (context menu)
5. Remove `onLongPress` from `NodeRow` call site
6. Add `rememberCoroutineScope()` and `SnackbarHostState` at screen level
7. Add `deleteWithUndo()` local function using snackbar
8. `NodeRow` no longer needs `onLongPress` parameter — remove it

### NodeEditorViewModel.kt
1. Add `restoreNode(nodeId: String)` intent (clears `deletedAt`, sets new HLC)
2. Remove `showContextMenu()` and `dismissContextMenu()` intents (context menu gone)

### NodeEditorUiState
1. Remove `contextMenuNodeId: String?` field (context menu gone)

### NodeRow composable signature
1. Remove `onLongPress: () -> Unit` parameter

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Modifier.swipeable` | `SwipeToDismissBox` (Material3) | Compose 1.6.0 | Swipeable is deprecated; Material3 provides ready-made component |
| `SnackbarHost` in `ScaffoldState` | `SnackbarHostState` standalone | Material3 migration | `ScaffoldState` removed in M3; pass `SnackbarHostState` directly |
| `confirmValueChange` for side effects | `snapshotFlow` + `confirmValueChange` only for vetoing | Material3 1.4.0 | `confirmValueChange` should only return true/false to allow/veto; use snapshotFlow for reactions. For our use case, returning `false` to snap back IS vetoing, so `confirmValueChange` is still the correct hook. |

**Deprecated/outdated:**
- `Modifier.swipeable`: Removed in recent Compose; use `SwipeToDismissBox`
- `ScaffoldState.snackbarHostState`: Use standalone `remember { SnackbarHostState() }` in Material3
- `ModalBottomSheet` for context actions: Removing in this phase, replaced by persistent toolbar

## Open Questions

1. **Glyph horizontal drag vs SwipeToDismissBox conflict**
   - What we know: The glyph `pointerInput` uses `PointerEventPass.Initial` (capture phase) at
     40dp horizontal threshold. SwipeToDismissBox uses `Main` pass.
   - What's unclear: Does `Initial`-pass consumption in the glyph area prevent SwipeToDismissBox
     from receiving the horizontal drag at all? The glyph is only 24dp wide but positioned at
     the left edge of the row.
   - Recommendation: Accept this behavior — swipe on the glyph area triggers indent/outdent, swipe
     on the content area triggers SwipeToDismissBox. This is acceptable UX since the glyph is small.

2. **`restoreNode()` after server-side sync**
   - What we know: The 30-second inactivity timer means sync rarely fires within the ~4s undo window.
   - What's unclear: What happens if the user restores a node that was already pushed as deleted?
   - Recommendation: Restoring sets a new `deletedHlc` with a later timestamp. On next sync, the
     server applies LWW: the restore HLC is newer than the delete HLC, so the node comes back.
     This is actually correct behavior — document it as "undo works even after sync" because LWW
     handles it. Verify against the server's merge logic in `ARCHITECTURE.md §4`.

3. **Icons for the toolbar**
   - What we know: Material Icons available via BOM, but the exact icon names for indent/outdent
     need verification. `Icons.AutoMirrored.Filled.FormatIndentIncrease` may require the extended
     icons package.
   - Recommendation: Check if `implementation(libs.androidx.compose.material.icons.extended)`
     is needed. If not already in deps, add it. The core icons set has `Add`, `Delete`, `Check`
     but NOT `FormatIndentIncrease`/`FormatIndentDecrease`.

## Sources

### Primary (HIGH confidence)
- Official Android Docs: developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss — SwipeToDismissBox API, confirmValueChange, backgroundContent patterns
- Official Android Docs: developer.android.com/develop/ui/compose/components/snackbar — Snackbar pattern, SnackbarHostState, showSnackbar with action label
- Official Android Docs: developer.android.com/develop/ui/compose/system/keyboard-animations — imePadding(), imeNestedScroll()
- Official Android Docs: developer.android.com/develop/ui/compose/system/insets — WindowInsets.ime, enableEdgeToEdge interaction
- Official Android Docs: developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures — gesture pass system (Initial/Main/Final), event consumption
- Official Android Docs: developer.android.com/develop/ui/compose/bom/bom-mapping — BOM 2025.04.01 maps to material3 1.4.0
- Codebase: NodeEditorViewModel.kt — confirmed `completed` field, `onCompletedToggled()`, `deleteNode()`, `softDeleteNode()` all already exist
- Codebase: NodeEntity.kt — confirmed `completed: Int = 0`, `completedHlc: String = ""` already in schema
- Codebase: NodeEditorScreen.kt — confirmed `longPressDraggableHandle` on `Surface`, `ModalBottomSheet` context menu structure to remove

### Secondary (MEDIUM confidence)
- composables.com/material3/swipetodismissbox — SwipeToDismissBox API shape, `rememberSwipeToDismissBoxState`, parameter names (verified against official docs)
- dev.to/myougatheaxo/drag-drop-in-compose — Pattern for nesting SwipeToDismissBox inside ReorderableItem (not Calvin-LL specific but general principle)

### Tertiary (LOW confidence)
- tutorialpedia.org — Scaffold + imePadding pattern for bottomBar; general principle confirmed by official docs but specific code not from official source

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all APIs confirmed stable in Material3 1.4.0 / Compose BOM 2025.04.01
- Architecture (swipe gesture nesting): HIGH — official gesture pass documentation confirms the
  conflict resolution logic; nesting order verified against component design
- IME toolbar: HIGH — imePadding() is documented and MainActivity already uses enableEdgeToEdge()
- Pitfalls: HIGH (gesture conflicts), MEDIUM (undo-after-sync behavior: correct per LWW theory
  but not explicitly tested against the backend)
- Code examples: HIGH — all examples derived from official API signatures

**Research date:** 2026-03-04
**Valid until:** 2026-06-01 (stable APIs; re-verify if upgrading Compose BOM)
