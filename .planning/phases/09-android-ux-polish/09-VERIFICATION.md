---
status: passed
phase: 09-android-ux-polish
verified: 2026-03-03
must_haves_checked: 4
must_haves_passed: 4
---

# Phase 09 Verification

**Phase Goal:** Fix density scale (compact = current size; comfortable/cozy add more spacing); add logout button to SettingsScreen; extend drag handle to full node row instead of glyph-only

**Verified:** 2026-03-03
**Status:** passed
**Re-verification:** No - initial verification

## Must-Haves

| # | Requirement | Status | Evidence |
|---|-------------|--------|----------|
| 1 | Compact density = 1.0f, comfortable = 1.10f, cozy = 1.20f | VERIFIED | `MainActivity.kt` lines 42-47 |
| 2 | SettingsScreen has full-width Logout button with error color | VERIFIED | `SettingsScreen.kt` lines 191-203 |
| 3 | Logout navigates to LoginScreen regardless of network | VERIFIED | `SettingsViewModel.kt` lines 106-116; `SettingsViewModelTest.kt` lines 202-229 |
| 4 | Long-pressing anywhere on node row activates drag handle | VERIFIED | `NodeEditorScreen.kt` lines 143-151; `NodeRow` has no `dragModifier` parameter |

**Score:** 4/4 truths verified

## Observable Truths

### Truth 1: Density scale direction (VERIFIED)

File: `android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt`

Lines 42-47:

    val densityScale = when (settings?.density) {
        "compact"     -> 1.0f
        "comfortable" -> 1.10f
        "cozy"        -> 1.20f
        else          -> 1.0f
    }

compact = 1.0f (smallest), cozy = 1.20f (largest). Direction is correct.

### Truth 2: Logout button in SettingsScreen (VERIFIED)

File: `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsScreen.kt`

Lines 189-203: item block contains OutlinedButton with:
- modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
- colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
- Text("Logout")
- Placed at end of LazyColumn inside SettingsUiState.Success branch
- Imports OutlinedButton (line 20) and ButtonDefaults (line 17) present

### Truth 3: Fail-open logout navigates to LoginScreen (VERIFIED)

File: `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/settings/SettingsViewModel.kt`

Lines 106-116:

    fun logout() = intent {
        try {
            val refreshToken = authRepository.getRefreshToken().filterNotNull().first()
            authRepository.logout(refreshToken)
                .onSuccess { postSideEffect(SettingsSideEffect.NavigateToLogin) }
                .onFailure { postSideEffect(SettingsSideEffect.NavigateToLogin) }
        } catch (e: Exception) {
            postSideEffect(SettingsSideEffect.NavigateToLogin)
        }
    }

All three paths (success, failure, no stored token) emit NavigateToLogin.

Wiring:
- SettingsScreen.kt lines 52-60: LaunchedEffect collects side effects; NavigateToLogin -> onNavigateToLogin()
- AuthRepositoryImpl.kt lines 76-86: logout() calls /api/auth/logout then removes ACCESS_TOKEN, REFRESH_TOKEN_KEY, USER_ID_KEY from DataStore
- AuthRepository.kt line 16: getRefreshToken(): Flow<String?> declared in interface
- AuthRepositoryImpl.kt lines 106-107: override reads REFRESH_TOKEN_KEY from DataStore
- SettingsViewModelTest.kt lines 202-229: two tests confirm both network-success and failure navigate to login

### Truth 4: Drag handle on full node row Surface (VERIFIED)

File: `android/app/src/main/java/com/gmaingret/outlinergod/ui/screen/nodeeditor/NodeEditorScreen.kt`

Lines 143-151: Surface modifier chain contains .longPressDraggableHandle(onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) })

NodeRow signature (line 228): parameters are flatNode, isFocused, isNoteExpanded, onContentChanged, onEnterPressed, onBackspaceOnEmpty, onFocusLost, onNoteChanged, onToggleNote, onGlyphTap, onToggleCollapse, onLongPress, onIndent, onOutdent. No dragModifier parameter present.

Glyph modifier chains (lines 271-297, 311-333): only pointerInput for horizontal indent/outdent and .clickable. No .then(dragModifier) anywhere in NodeRow.

@OptIn(ExperimentalFoundationApi::class) annotation present at line 226.

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android/.../MainActivity.kt` | Corrected density scale mapping | VERIFIED | 54 lines; compact=1.0f, comfortable=1.10f, cozy=1.20f at lines 42-47 |
| `android/.../repository/AuthRepository.kt` | getRefreshToken() interface method | VERIFIED | 17 lines; method at line 16 |
| `android/.../repository/impl/AuthRepositoryImpl.kt` | getRefreshToken() reads REFRESH_TOKEN_KEY | VERIFIED | 108 lines; override at lines 106-107 |
| `android/.../ui/screen/settings/SettingsViewModel.kt` | logout() Orbit MVI intent | VERIFIED | 129 lines; fun logout() at lines 106-116 |
| `android/.../ui/screen/settings/SettingsScreen.kt` | OutlinedButton Logout in LazyColumn | VERIFIED | 209 lines; button at lines 191-201; imports at lines 17 and 20 |
| `android/.../ui/screen/nodeeditor/NodeEditorScreen.kt` | longPressDraggableHandle on Surface; no dragModifier in NodeRow | VERIFIED | 472 lines; Surface modifier at lines 145-151; NodeRow at line 228 has no dragModifier |
| `android/.../test/.../settings/SettingsViewModelTest.kt` | 2 new logout test cases | VERIFIED | 259 lines; tests at lines 202-213 and 217-229 |

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| SettingsViewModel.logout() | AuthRepository.logout(refreshToken) | getRefreshToken().filterNotNull().first() | WIRED | SettingsViewModel.kt lines 108-111 |
| SettingsScreen logout button | SettingsSideEffect.NavigateToLogin | viewModel.logout() -> postSideEffect -> LaunchedEffect -> onNavigateToLogin() | WIRED | SettingsScreen.kt lines 53-55 |
| ReorderableItem Surface | longPressDraggableHandle | Modifier chain on Surface | WIRED | NodeEditorScreen.kt lines 145-151 |

## Anti-Patterns Found

None. No TODO/FIXME/placeholder patterns in any of the 7 modified files. No empty handlers, stub returns, or console.log-only implementations.

## Human Verification Items

The following behaviors require a running device to confirm, but the code structure fully supports them.

### 1. Density scale visual difference

**Test:** Open Settings, switch between Compact, Comfortable, and Cozy density options.
**Expected:** Compact shows smallest spacing (same as system default), Comfortable slightly larger, Cozy the most spacious.
**Why human:** Scale factor applied to OutlinerGodTheme(densityScale = ...) - visual rendering cannot be verified from source alone.

### 2. Logout navigation flow

**Test:** Tap Logout in SettingsScreen while logged in.
**Expected:** App navigates to LoginScreen. Subsequent app reopen also shows LoginScreen (tokens cleared).
**Why human:** Navigation graph wiring and DataStore persistence after token removal require device runtime to confirm end-to-end.

### 3. Full-row drag activation

**Test:** Long-press on the text content area (not the glyph) of any node row in the node editor.
**Expected:** Haptic feedback triggers and the row enters drag mode, allowing reorder.
**Why human:** Gesture interaction between longPressDraggableHandle on Surface and pointerInput long-press handler on the content Column requires device runtime to confirm no gesture conflict.

## Verdict

**passed** - All 4 must-haves are verified in the actual codebase.

1. **Density scale** - correctly ordered: compact=1.0f (tightest) through cozy=1.20f (widest) in `MainActivity.kt` lines 42-47.
2. **Logout button** - `SettingsScreen.kt` contains a full-width `OutlinedButton` with `contentColor = MaterialTheme.colorScheme.error` at the bottom of the LazyColumn.
3. **Fail-open logout** - `SettingsViewModel.logout()` posts NavigateToLogin on all three paths. Side effect wired in SettingsScreen. AuthRepositoryImpl.logout() clears all DataStore tokens. Two new tests confirm both network paths.
4. **Full-row drag** - `longPressDraggableHandle` is on the `Surface` modifier (full row). `NodeRow` has no `dragModifier` parameter. ExperimentalFoundationApi opt-in present at line 226.

All artifacts are substantive (real implementation, no stubs), all key links are wired, no anti-patterns found. 7 files modified as planned; SettingsViewModelTest has 11 tests total.

---
*Verified: 2026-03-03*
*Verifier: Claude (gsd-verifier)*
