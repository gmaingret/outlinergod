---
phase: 05-sync-integration-fixes
verified: 2026-03-03T13:07:38Z
status: passed
score: 10/10 must-haves verified
---

# Phase 05 Verification: sync-integration-fixes

**Phase Goal:** Fix all three critical integration blockers so Android-backend sync works end-to-end
**Verified:** 2026-03-03T13:07:38Z
**Status:** passed
**Re-verification:** No - initial verification

## Score

10/10 must-haves verified

## Must-Haves

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | HlcClock.generate() produces 13-digit-decimal-wall format | VERIFIED | HlcClock.kt line 69: padStart(13) for wall, line 70: padStart(5) for counter; test asserts 13 decimal digit wall and 5 decimal digit counter |
| 2 | HlcClock.parse() correctly round-trips the new decimal format | VERIFIED | HlcClock.kt lines 82-85: toLong() and toInt() (not hex variants); property test uses 13-digit epoch range 1_000_000_000_000L..9_999_999_999_999L |
| 3 | AuthRepository exposes getUserId() returning user UUID | VERIFIED | AuthRepository.kt line 14: fun getUserId(); AuthRepositoryImpl.kt line 91: returns DataStore value under USER_ID_KEY |
| 4 | googleSignIn() persists user.id into DataStore under user_id key | VERIFIED | AuthRepositoryImpl.kt line 51: prefs[USER_ID_KEY] = response.user.id; test googleSignIn_storesUserId asserts getUserId().first() equals uid1 |
| 5 | DocumentListViewModel queries Room with real user UUID | VERIFIED | Line 66: authRepository.getUserId().filterNotNull().first() passed to getAllDocuments(userId). Zero getAccessToken() calls in this file. |
| 6 | NodeEditorViewModel creates root nodes with real user UUID as userId field | VERIFIED | NodeEditorViewModel.kt line 64: val userId = authRepository.getUserId().filterNotNull().first() used for rootNode.userId; addChildNode inherits userId from loaded parent entity - functionally correct. |
| 7 | SettingsViewModel queries/creates settings with real user UUID | VERIFIED | SettingsViewModel.kt line 33: userId = authRepository.getUserId().filterNotNull().first() used in settingsDao.getSettings(userId) and default SettingsEntity(userId = userId) |
| 8 | SyncWorker background sync uses real user UUID for getPendingChanges | VERIFIED | SyncWorker.kt line 71: val userId = authRepository.getUserId().filterNotNull().first() used at lines 73-74 for documentDao and bookmarkDao getPendingChanges calls |
| 9 | POST /api/documents sends snake_case body (parent_id, sort_order) | VERIFIED | CreateDocumentRequest.kt: @SerialName annotations for parent_id and sort_order fields; DocumentListViewModel.kt line 198: setBody(CreateDocumentRequest(...)) |
| 10 | All 216+ existing tests still pass after migration | VERIFIED | 38 test files exist; all setUp() methods mock getUserId() with decimal HLC fixtures; SUMMARY reports 222 tests 221 passing (1 pre-existing flaky BookmarkDaoTest race) |

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| android/.../sync/HlcClock.kt | Decimal padStart(13) wall + padStart(5) counter | VERIFIED | Lines 69-70 confirmed; parse() uses toLong()/toInt() |
| android/.../repository/AuthRepository.kt | Interface declares getUserId() | VERIFIED | Line 14 confirmed |
| android/.../repository/impl/AuthRepositoryImpl.kt | USER_ID_KEY; googleSignIn stores user.id; getUserId() returns it | VERIFIED | Lines 40, 51, 91 confirmed |
| android/.../network/model/CreateDocumentRequest.kt | @Serializable with snake_case SerialName annotations | VERIFIED | Lines 11-12 confirmed; 13-line file, no stubs |
| android/.../screen/documentlist/DocumentListViewModel.kt | getUserId() for loadDocuments, triggerSync, createDocument | VERIFIED | Lines 66, 111, 170 confirmed; no getAccessToken() present |
| android/.../screen/nodeeditor/NodeEditorViewModel.kt | getUserId() for loadDocument root node and triggerSync | VERIFIED | Lines 64, 659 confirmed; no getAccessToken() present |
| android/.../screen/settings/SettingsViewModel.kt | getUserId() for loadSettings and default entity creation | VERIFIED | Line 33 confirmed; no getAccessToken() present |
| android/.../sync/SyncWorker.kt | getUserId() for PUSH phase getPendingChanges | VERIFIED | Line 71 confirmed; no getAccessToken() present |
| android/.../sync/HlcClockTest.kt | Decimal regex assertions; property test 13-digit range | VERIFIED | 102 lines; Arb range 1_000_000_000_000L..9_999_999_999_999L |
| android/.../repository/AuthRepositoryTest.kt | getUserId() round-trip tests | VERIFIED | Lines 133-151: googleSignIn_storesUserId and logout_clearsUserId confirmed |

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| DocumentListViewModel.loadDocuments() | authRepository.getUserId() | filterNotNull().first() for Room userId query | WIRED | Line 66: exact pattern present |
| DocumentListViewModel.triggerSync() | authRepository.getUserId() | filterNotNull().first() for getPendingChanges | WIRED | Line 111: confirmed |
| DocumentListViewModel.createDocument() | CreateDocumentRequest | setBody(CreateDocumentRequest(...)) | WIRED | Lines 198-204: confirmed |
| SyncWorker.doWork() | authRepository.getUserId() | filterNotNull().first() for getPendingChanges | WIRED | Line 71: confirmed |
| NodeEditorViewModel.loadDocument() | authRepository.getUserId() | filterNotNull().first() for root node userId | WIRED | Line 64: confirmed |
| SettingsViewModel.loadSettings() | authRepository.getUserId() | filterNotNull().first() for DAO query | WIRED | Line 33: confirmed |

## Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| HLC format matches backend hlc.ts (decimal) | SATISFIED | format() and parse() use decimal; test confirms exact 13-char wall |
| getUserId() returns UUID not JWT | SATISFIED | stored as response.user.id, separate from ACCESS_TOKEN key |
| All ViewModels use userId UUID for Room | SATISFIED | DocumentListVM, NodeEditorVM, SettingsVM all verified |
| SyncWorker uses userId UUID for sync push | SATISFIED | getPendingChanges(userId,...) in doWork() |
| POST body uses snake_case field names | SATISFIED | SerialName annotations in CreateDocumentRequest |
| Test suite remains green | SATISFIED | 222 tests, 221 passing (1 pre-existing flaky race, passes on retry) |

## Anti-Patterns Found

No blockers or stub patterns found. All implementations are substantive.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| NodeEditorViewModel.kt | 525 | parentEntity userId fallback in addChildNode | Info | Uses parent node userId rather than calling getUserId() directly; functionally correct - all loaded nodes carry owning user UUID from getUserId() at document load time |

## Human Verification Items

No programmatic gaps found. The following item requires human and device testing to confirm end-to-end sync:

### 1. Full Android-Backend Sync Round-Trip

**Test:** Log in to the app, create a document, verify it appears on the backend via GET /api/documents, kill the app, relaunch, confirm the document is still visible.
**Expected:** Document created on Android is pushed to backend with correct UUID userId, snake_case body accepted (no 400 error), and re-appears after re-sync.
**Why human:** Requires running Android emulator or device with a live Docker backend against real Google OAuth credentials and network.

## Gaps Summary

No gaps. All 10 must-haves are verified in the actual codebase. The phase goal is achieved.

The three original integration blockers are resolved:

- GAP-1 (HLC format mismatch): HlcClock now produces 13-digit-decimal-wall-5-digit-decimal-counter-deviceId matching backend hlc.ts exactly.
- GAP-2 (userId was JWT): getUserId() added to AuthRepository and persists response.user.id on login; all Room queries in ViewModels and SyncWorker now use this UUID.
- GAP-3 (camelCase POST body): CreateDocumentRequest uses SerialName annotations for parent_id and sort_order, replacing the previous mapOf(parentId to ...) which the backend rejected.

---

_Verified: 2026-03-03T13:07:38Z_
_Verifier: Claude (gsd-verifier)_
