---
phase: 07-settings-display-and-techdebt
verified: 2026-03-03T19:30:40Z
status: passed
score: 6/6 must-haves verified
---

# Phase 07: Settings Display and Tech-Debt Verification Report

**Phase Goal:** Fix MainActivity JWT-as-userId bug so settings visually apply; remove orphaned SyncStatus enum; fix WorkManager policy; extract LAST_SYNC_HLC_KEY to shared constant
**Verified:** 2026-03-03T19:30:40Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | MainActivity calls `getUserId()` (not `getAccessToken()`) in `settingsFlow flatMapLatest` | VERIFIED | `MainActivity.kt:31` — `authRepository.getUserId().flatMapLatest { userId ->` |
| 2 | Orphaned `db/entity/SyncStatus.kt` does not exist | VERIFIED | File absent at `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/SyncStatus.kt`; no stale imports found |
| 3 | `SyncScheduler.kt` uses `ExistingPeriodicWorkPolicy.UPDATE` | VERIFIED | `SyncScheduler.kt:29` — `ExistingPeriodicWorkPolicy.UPDATE` |
| 4 | `SyncConstants.kt` exists with single `LAST_SYNC_HLC_KEY` definition | VERIFIED | `sync/SyncConstants.kt` — object `SyncConstants { val LAST_SYNC_HLC_KEY = stringPreferencesKey("last_sync_hlc") }` |
| 5 | No standalone `LAST_SYNC_HLC_KEY` definition in SyncWorker, DocumentListViewModel, or NodeEditorViewModel | VERIFIED | All three files reference `SyncConstants.LAST_SYNC_HLC_KEY`; no companion-object definition found |
| 6 | Tests reference `SyncConstants.LAST_SYNC_HLC_KEY` | VERIFIED | `SyncWorkerTest.kt:198` and `DocumentListSyncTest.kt:172` both use `SyncConstants.LAST_SYNC_HLC_KEY` |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android/app/src/main/java/com/gmaingret/outlinergod/MainActivity.kt` | `getUserId()` in `settingsFlow` | VERIFIED | Line 31 confirmed |
| `android/app/src/main/java/com/gmaingret/outlinergod/db/entity/SyncStatus.kt` | ABSENT (orphaned file removed) | VERIFIED | File does not exist |
| `android/app/src/main/java/com/gmaingret/outlinergod/ui/common/SyncStatus.kt` | PRESENT (active enum retained) | VERIFIED | File exists at ui/common/SyncStatus.kt |
| `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncScheduler.kt` | `ExistingPeriodicWorkPolicy.UPDATE` | VERIFIED | Line 29 confirmed |
| `android/app/src/main/java/com/gmaingret/outlinergod/sync/SyncConstants.kt` | Single `LAST_SYNC_HLC_KEY` constant | VERIFIED | Single definition in SyncConstants object |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MainActivity.kt` | `SettingsDao.getSettings(userId)` | `authRepository.getUserId().flatMapLatest` | WIRED | Lines 31-33: userId passed correctly; null check present |
| `SyncWorker.kt` | `SyncConstants.LAST_SYNC_HLC_KEY` | direct reference | WIRED | Lines 48, 101 |
| `DocumentListViewModel.kt` | `SyncConstants.LAST_SYNC_HLC_KEY` | direct reference | WIRED | Lines 79, 131 |
| `NodeEditorViewModel.kt` | `SyncConstants.LAST_SYNC_HLC_KEY` | direct reference | WIRED | Lines 638, 687 |
| Tests | `SyncConstants.LAST_SYNC_HLC_KEY` | direct reference | WIRED | SyncWorkerTest:198, DocumentListSyncTest:172 |

### Requirements Coverage

All phase requirements satisfied:

| Requirement | Status | Notes |
|-------------|--------|-------|
| Fix JWT-as-userId bug in MainActivity | SATISFIED | `getUserId()` confirmed at line 31 |
| Remove orphaned `db/entity/SyncStatus.kt` | SATISFIED | File absent; no dangling imports |
| Fix WorkManager policy to UPDATE | SATISFIED | `ExistingPeriodicWorkPolicy.UPDATE` at line 29 |
| Extract `LAST_SYNC_HLC_KEY` to `SyncConstants` | SATISFIED | Single authoritative constant; all call-sites updated; tests updated |

### Anti-Patterns Found

None. No TODO/FIXME/placeholder patterns detected in changed files.

### Human Verification Required

None. All phase changes are structural/behavioral code changes that are fully verifiable by static analysis.

## Summary

All 6 must-haves verified against the actual codebase. The phase goal is fully achieved:

- **Settings bug fixed:** `MainActivity.kt` now derives the settings userId from `authRepository.getUserId()` (which reads the stored userId string from DataStore), not from `getAccessToken()` (which returns a JWT token). This means `settingsDao.getSettings(userId)` receives the correct user identifier, so theme and density settings visually apply on launch.
- **Orphaned enum removed:** `db/entity/SyncStatus.kt` is gone; `ui/common/SyncStatus.kt` (the active version used by ViewModels) is intact.
- **WorkManager policy corrected:** `ExistingPeriodicWorkPolicy.UPDATE` replaces the prior policy, preventing stale periodic work from running alongside newly-enqueued work.
- **Constant extracted:** `SyncConstants.LAST_SYNC_HLC_KEY` is the single authoritative definition; `SyncWorker`, `DocumentListViewModel`, `NodeEditorViewModel`, and their corresponding tests all reference `SyncConstants.LAST_SYNC_HLC_KEY` with no standalone shadow definitions.

---

_Verified: 2026-03-03T19:30:40Z_
_Verifier: Claude (gsd-verifier)_
