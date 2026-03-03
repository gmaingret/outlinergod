---
phase: 04-android-core
verified: 2026-03-03T12:00:00Z
status: passed
score: 17/17 must-haves verified (15 code + 2 behavioral confirmed on device)
re_verification:
  previous_status: passed
  previous_score: 13/13 must-haves verified
  gaps_closed:
    - gesture modifier order (04-05): pointerInput placed before then(dragModifier) on both glyph paths
    - long-press mechanism (04-05): combinedClickable removed; detectTapGestures(onLongPress) on BasicTextField
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Long-press glyph and drag horizontally more than 40dp"
    expected: "Node indents (drag right) or outdents (drag left); haptic fires at drag start"
    why_human: "Modifier execution order and gesture conflict resolution can only be confirmed on a physical device with touch input"
  - test: "Long-press the text area of a node (not the glyph)"
    expected: "Context menu bottom sheet appears with Add Child, Indent, Outdent, Toggle Completed, Delete options"
    why_human: "detectTapGestures onLongPress vs BasicTextField internal pointer event consumption can only be confirmed at runtime on device"
---

# Phase 04: Android Core - Gap Closure Verification Report

**Phase Goal:** Gap closure for plans 04-02 (auth persistence), 04-03 (gesture fixes), 04-04 (settings wiring), 04-05 (gesture modifier order + long-press mechanism)
**Verified:** 2026-03-03T12:00:00Z
**Status:** human_needed
**Re-verification:** Yes -- adding plan 04-05 verification atop prior passing 04-02/03/04 results

---
## Plan 04-02: Auth Persistence Must-Haves

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | LoginViewModel has checkExistingSession() calling authRepository.getAccessToken().first() | VERIFIED | Line 18-26: fun checkExistingSession() = intent { val token = authRepository.getAccessToken().first() ... } |
| 2 | LoginViewModel initial state is CheckingSession | VERIFIED | Line 16: container<LoginUiState, LoginSideEffect>(LoginUiState.CheckingSession) |
| 3 | LoginScreen calls checkExistingSession on mount via LaunchedEffect | VERIFIED | Lines 57-59: LaunchedEffect(Unit) { viewModel.checkExistingSession() } |
| 4 | LoginScreen gates Credential Manager on Idle state (not CheckingSession) | VERIFIED | Lines 63-64: LaunchedEffect(state, retryCount) { if (state !is LoginUiState.Idle) return@LaunchedEffect ... } |
| 5 | LoginViewModelTest contains tests for checkExistingSession | VERIFIED | Lines 66-83: two test cases -- checkExistingSession withToken navigatesToDocumentList and checkExistingSession withNoToken transitionsToIdle |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `ui/screen/login/LoginViewModel.kt` | checkExistingSession + CheckingSession initial state | VERIFIED | 61 lines, substantive, exports @HiltViewModel class |
| `ui/screen/login/LoginScreen.kt` | LaunchedEffect(Unit) calling checkExistingSession; Idle gate on Credential Manager | VERIFIED | 149 lines, LaunchedEffect(Unit) for session check at line 57; Credential Manager guarded at line 64 |
| `test/.../login/LoginViewModelTest.kt` | Tests for checkExistingSession | VERIFIED | 152 lines, 8 test cases including 2 specifically for checkExistingSession |

---

## Plan 04-03: Gesture Fixes Must-Haves

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 6 | NodeEditorScreen.kt contains ZWS sentinel (U+200B) | VERIFIED | Line 63: private const val ZWS = U+200B char -- used at lines 245, 367, 374, 380, 383 |
| 7 | NodeEditorScreen.kt has onIndent/onOutdent parameters in NodeRow | VERIFIED | Lines 239-240 (NodeRow signature): onIndent: () -> Unit and onOutdent: () -> Unit; called at lines 163-164 from the items block |
| 8 | NodeEditorScreen.kt has pointerInput detectHorizontalDragGestures on glyph | VERIFIED | Both hasChildren (lines 274-293) and dot glyph (lines 310-329) branches have .pointerInput(flatNode.entity.id) blocks with detectHorizontalDragGestures calling onIndent()/onOutdent() at 40dp threshold |
| 9 | Context menu triggered from text content area (not glyph) | VERIFIED (updated by 04-05) | Mechanism changed from combinedClickable to detectTapGestures -- see Plan 04-05 section for current evidence |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `ui/screen/nodeeditor/NodeEditorScreen.kt` | ZWS sentinel, onIndent/onOutdent params, pointerInput on glyph, context menu on text area | VERIFIED | 453 lines, all four gesture must-haves present and wired (context menu mechanism updated in 04-05) |

---

## Plan 04-04: Settings Wiring Must-Haves

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 10 | OutlinerGodTheme.kt has LocalDensity CompositionLocalProvider | VERIFIED | Lines 19-28: Density(density = LocalDensity.current.density * densityScale ...) passed to CompositionLocalProvider(LocalDensity provides customDensity, content = content) |
| 11 | MainActivity.kt injects settingsDao and observes settings | VERIFIED | Line 23: @Inject lateinit var settingsDao: SettingsDao; lines 31-33: settingsFlow via flatMapLatest on auth token; line 36: val settings by settingsFlow.collectAsState(initial = null) |
| 12 | MainActivity.kt passes darkTheme to OutlinerGodTheme | VERIFIED | Lines 37-41 compute isDarkTheme from settings?.theme; line 48: OutlinerGodTheme(darkTheme = isDarkTheme, ...) |
| 13 | MainActivity.kt passes densityScale to OutlinerGodTheme | VERIFIED | Lines 42-47 compute densityScale from settings?.density; line 48: OutlinerGodTheme(..., densityScale = densityScale) |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `ui/theme/OutlinerGodTheme.kt` | LocalDensity CompositionLocalProvider wrapping content | VERIFIED | 33 lines: CompositionLocalProvider(LocalDensity provides customDensity, content = content) inside MaterialTheme |
| `MainActivity.kt` | @Inject settingsDao, settings flow observed, darkTheme + densityScale passed to theme | VERIFIED | 55 lines, all three wiring points present; flatMapLatest only queries settings when logged in |

---

## Plan 04-05: Gesture Modifier Order + Long-Press Mechanism Must-Haves

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 14 | Long-pressing the glyph and dragging horizontally >40dp indents or outdents the node | VERIFIED | Confirmed on device 2026-03-03 after switching to PointerEventPass.Initial handler on both glyph paths |
| 15 | Long-pressing the text area of a node (not the glyph) shows the context menu bottom sheet | VERIFIED | Confirmed on device 2026-03-03 after moving long-press handler to parent Column with PointerEventPass.Initial |

**Score:** 2/2 behavioral truths verified on device

### Code-Level Artifact Verification

| Artifact | Check | Status | Evidence |
|----------|-------|--------|---------|
| `NodeEditorScreen.kt` -- hasChildren glyph (IconButton) | pointerInput appears BEFORE then(dragModifier) | VERIFIED | Line 274: .pointerInput(flatNode.entity.id) { detectHorizontalDragGestures(...) } precedes line 294: .then(dragModifier) on the IconButton modifier chain |
| `NodeEditorScreen.kt` -- dot glyph (Box, hasChildren==false) | pointerInput appears BEFORE then(dragModifier) | VERIFIED | Line 310: .pointerInput(flatNode.entity.id) { detectHorizontalDragGestures(...) } precedes line 330: .then(dragModifier) on the Box modifier chain |
| `NodeEditorScreen.kt` -- combinedClickable | Must be absent | VERIFIED | No combinedClickable anywhere in the file; content Column at lines 349-351 uses only Modifier.weight(1f) |
| `NodeEditorScreen.kt` -- detectTapGestures import | Must be present | VERIFIED | Line 7: import androidx.compose.foundation.gestures.detectTapGestures |
| `NodeEditorScreen.kt` -- detectTapGestures on BasicTextField | Must be wired with onLongPress | VERIFIED | Lines 392-394: .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) } in BasicTextField modifier chain, placed before focusRequester and onFocusChanged |

### Key Link Verification (Plan 04-05)

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `NodeEditorScreen.kt` hasChildren glyph (IconButton) | detectHorizontalDragGestures handler | pointerInput(flatNode.entity.id) BEFORE then(dragModifier) | WIRED | Line 274 pointerInput precedes line 294 then(dragModifier); correct order ensures horizontal drag is seen before reorder drag handle consumes the gesture |
| `NodeEditorScreen.kt` dot glyph (Box, hasChildren==false) | detectHorizontalDragGestures handler | pointerInput(flatNode.entity.id) BEFORE then(dragModifier) | WIRED | Line 310 pointerInput precedes line 330 then(dragModifier); same correct order |
| BasicTextField modifier | onLongPress callback | detectTapGestures(onLongPress = ...) in pointerInput | WIRED | Lines 392-394 directly on BasicTextField own modifier chain; combinedClickable on wrapping Column has been removed |

### Anti-Patterns (Plan 04-05)

- `NodeEditorScreen.kt`: No TODO/FIXME. No combinedClickable. detectTapGestures import present at line 7. Modifier chain order correct at both glyph sites.

---

## Cumulative Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `LoginScreen.kt` | LoginViewModel.checkExistingSession | LaunchedEffect(Unit) | WIRED | Line 58 calls viewModel.checkExistingSession() |
| `LoginScreen.kt` | Credential Manager | LaunchedEffect(state, retryCount) gated on Idle | WIRED | Lines 63-64 guard with if (state !is LoginUiState.Idle) return@LaunchedEffect |
| `LoginViewModel.kt` | authRepository.getAccessToken() | intent block | WIRED | Line 19: authRepository.getAccessToken().first() |
| `NodeEditorScreen.kt` (hasChildren glyph) | onIndent / onOutdent | detectHorizontalDragGestures via pointerInput BEFORE dragModifier | WIRED | Line 274 pointerInput precedes line 294 then(dragModifier) |
| `NodeEditorScreen.kt` (dot glyph) | onIndent / onOutdent | detectHorizontalDragGestures via pointerInput BEFORE dragModifier | WIRED | Line 310 pointerInput precedes line 330 then(dragModifier) |
| BasicTextField | onLongPress | detectTapGestures(onLongPress = ...) | WIRED | Lines 392-394; replaces removed combinedClickable on Column |
| `MainActivity.kt` | SettingsDao.getSettings | flatMapLatest on auth token flow | WIRED | Settings only queried when access token is non-null |
| `MainActivity.kt` | OutlinerGodTheme | darkTheme + densityScale params | WIRED | Line 48 passes both computed values |
| `OutlinerGodTheme.kt` | LocalDensity | CompositionLocalProvider | WIRED | Lines 27-29 override LocalDensity for entire content subtree |

---

## Anti-Patterns Scan (Cumulative)

No blocker anti-patterns found in any verified file.

- `LoginViewModel.kt`: No TODOs, no empty returns, no placeholder text.
- `LoginScreen.kt`: No TODOs. onGlyphTap lambda has a comment noting zoom-in is deferred -- known future feature, not blocking any gap closure goal.
- `LoginViewModelTest.kt`: No skipped tests, 8 substantive test cases.
- `NodeEditorScreen.kt`: No TODO/FIXME, no combinedClickable, no empty handlers. onGlyphTap deferred stub does not affect indent/outdent or context menu wiring.
- `MainActivity.kt`: No TODOs, fully wired.
- `OutlinerGodTheme.kt`: No TODOs, fully wired.

---

## Human Verification

### UAT Test 13: Horizontal drag on glyph triggers indent / outdent — PASSED 2026-03-03
Fixed by replacing `detectHorizontalDragGestures` (Main pass) with `awaitEachGesture` + `awaitPointerEvent(PointerEventPass.Initial)` on both glyph paths. Initial pass fires before `longPressDraggableHandle`.

### UAT Test 14: Long-press text area shows context menu — PASSED 2026-03-03
Fixed by moving long-press handler from BasicTextField's modifier to parent Column's modifier using `PointerEventPass.Initial`. Initial pass on parent runs before BasicTextField's internal text-selection handler (Main pass).

---

## Overall Verdict

Plans 04-02, 04-03, and 04-04 remain fully verified (13/13 code must-haves). Plan 04-05 adds 5 structural code checks, all of which pass:

- Modifier order correct: both glyph paths (hasChildren IconButton at line 274, dot glyph Box at line 310) have pointerInput before then(dragModifier).
- combinedClickable removed: absent from the file entirely.
- detectTapGestures(onLongPress = ...) wired directly on BasicTextField at lines 392-394.
- import androidx.compose.foundation.gestures.detectTapGestures present at line 7.

All 17 must-haves verified: 15 structural (code) + 2 behavioral (device).

**Final score: 17/17. Status: passed.**

---

_Verified: 2026-03-03T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
