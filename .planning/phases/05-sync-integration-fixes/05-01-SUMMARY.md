# 05-01 Summary: HLC Decimal Format + AuthRepository getUserId()

## Status

Complete

## One-liner

Converted HlcClock from hex to 13-digit decimal format matching backend hlc.ts, and added getUserId() to AuthRepository persisting the real user UUID (not JWT) in DataStore.

## Deliverables

- `android/app/src/main/java/com/gmaingret/outlinergod/sync/HlcClock.kt`: decimal format (13-digit decimal wall, 5-digit decimal counter). `format()` uses `padStart(13,'0')` and `padStart(5,'0')`. `parse()` uses `toLong()` and `toInt()` (not hex variants).
- `android/app/src/test/java/com/gmaingret/outlinergod/sync/HlcClockTest.kt`: all test assertions updated for decimal format; property test renamed to `wallDecimal_isExactlyThirteenChars` with range clamped to 13-digit epoch window.
- `android/app/src/test/java/com/gmaingret/outlinergod/prototype/LwwMergeTest.kt`: hex HLC literals replaced with decimal equivalents (0x17b05a3a1be = 1636300202430) preserving correct ordering.
- `android/app/src/main/java/com/gmaingret/outlinergod/repository/AuthRepository.kt`: `getUserId(): Flow<String?>` added to interface.
- `android/app/src/main/java/com/gmaingret/outlinergod/repository/impl/AuthRepositoryImpl.kt`: `USER_ID_KEY` DataStore key, `googleSignIn()` persists `response.user.id`, `logout()` clears it, `getUserId()` returns it.
- `android/app/src/test/java/com/gmaingret/outlinergod/repository/AuthRepositoryTest.kt`: 2 new tests — `googleSignIn_storesUserId` and `logout_clearsUserId`.

## Commits

- `097376f` — fix(05-01): convert HlcClock to decimal format matching backend
- `7fe3830` — feat(05-01): add getUserId() to AuthRepository and persist user UUID on login

## Deviations

### Auto-fixed Issues

**1. [Rule 1 - Bug] Property test range too wide for 13-digit wall assertion**

- **Found during:** Task 1 verification
- **Issue:** `HlcClockTest.wallDecimal_isExactlyThirteenChars` used `Arb.long(0L..Long.MAX_VALUE / 2)` which generates values up to 19 digits; `padStart(13,'0')` does not truncate, so wall parts could be >13 chars.
- **Fix:** Clamped Arb range to `1_000_000_000_000L..9_999_999_999_999L` (13-digit epoch window, covers 2001-2286).
- **Files modified:** `android/app/src/test/java/com/gmaingret/outlinergod/sync/HlcClockTest.kt`
- **Commit:** 097376f

## Test Results

All 222 tests passed (including 2 new AuthRepository tests). One flaky Robolectric/Room threading issue (`BookmarkDaoTest.observeAllActive_excludesOtherUsers`) was observed on one run but is pre-existing — it consistently passes on retry and is caused by Room's Invalidation Tracker background thread outliving the previous test's `db.close()`.

Target tests verified green:
- `com.gmaingret.outlinergod.sync.HlcClockTest` — 8 tests (7 original updated + 1 renamed)
- `com.gmaingret.outlinergod.sync.LwwMergeTest` — 9 tests (unchanged, format-agnostic)
- `com.gmaingret.outlinergod.prototype.HlcClockTest` — 3 tests (prototype class unchanged, still hex)
- `com.gmaingret.outlinergod.prototype.LwwMergeTest` — 6 tests (literal strings updated to decimal)
- `com.gmaingret.outlinergod.repository.AuthRepositoryTest` — 8 tests (6 original + 2 new)

## Verification Checklist

- [x] `HlcClock.format(1772370135754L, 3, "device-1")` returns `"1772370135754-00003-device-1"`
- [x] `HlcClock.parse("1772370135754-00003-device-1")` returns `Triple(1772370135754L, 3, "device-1")`
- [x] `AuthRepository.getUserId()` emits `"uid1"` after successful googleSignIn (not `"access-token-123"`)
- [x] `AuthRepository.getAccessToken()` still emits JWT token (unchanged for Ktor interceptor)
- [x] All tests in sync, prototype, and repository packages pass green
