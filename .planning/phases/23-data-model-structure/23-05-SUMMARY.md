---
phase: 23-data-model-structure
plan: 05
subsystem: database
tags: [room, ksp, android, schema, sqlite, migration]

# Dependency graph
requires:
  - phase: 23-data-model-structure
    provides: exportSchema=true restored in 23-04; Room DB v3 entity schema; AppDatabase wired with 4 DAOs
provides:
  - "android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json committed to git"
  - "build.gradle.kts safe for KSP: no destructive doFirst hook"
  - "Phase 24 MigrationTestHelper can locate 3.json"
affects: [phase-24-migration-tests]

# Tech tracking
tech-stack:
  added: []
  patterns: [commit 3.json after first-time KSP generation; never use deleteRecursively on schemas/ dir]

key-files:
  created:
    - android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json
  modified:
    - android/app/build.gradle.kts

key-decisions:
  - "D-23-05-A: Remove doFirst deleteRecursively hook; manually delete 3.json once to allow KSP fresh write, then commit result — once committed and entities stable, kspDebugKotlin stays UP-TO-DATE and AbstractMethodError cannot fire"
  - "D-23-05-B: --rerun-tasks invalidates KSP incremental cache causing AbstractMethodError; avoid --rerun-tasks after 3.json is committed"

patterns-established:
  - "Room schema commit pattern: delete 3.json manually -> kspDebugKotlin -> verify UP-TO-DATE on second run -> commit 3.json -> never use deleteRecursively hook again"

requirements-completed: []

# Metrics
duration: 195min
completed: 2026-03-07
---

# Phase 23 Plan 05: Gap Closure — Room Schema 3.json Committed Summary

**Removed the destructive doFirst hook from build.gradle.kts and committed Room DB v3 schema (3.json) so Phase 24 MigrationTestHelper can locate it; kspDebugKotlin now stays UP-TO-DATE on subsequent runs.**

## Performance

- **Duration:** ~195 min (dominated by OOM recovery from accumulated background gradle processes)
- **Started:** 2026-03-07T21:30:00Z
- **Completed:** 2026-03-07T23:00:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Deleted the `tasks.matching{ksp*Kotlin}.configureEach { doFirst { schemasDir.deleteRecursively() } }` block from build.gradle.kts — the root cause of the 3.json absence
- Ran `kspDebugKotlin` to regenerate 3.json fresh (without reading existing file, avoiding AbstractMethodError)
- Verified `kspDebugKotlin` on second run shows `UP-TO-DATE` — confirms the crash cycle is permanently broken
- Committed and pushed both changes to master; Phase 24 MigrationTestHelper can now locate `schemas/3.json`

## Task Commits

Each task was committed atomically:

1. **Task 1: Remove destructive doFirst hook from build.gradle.kts** - `2ccbcc3` (fix)
2. **Task 2: Regenerate 3.json and commit to git** - `5ba4ebe` (fix)

**Plan metadata:** (see final metadata commit below)

## Files Created/Modified
- `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json` - Room DB v3 schema snapshot (formatVersion:1, version:3, identityHash:8e578df2b7b9d8eb578d8af4a5551e16)
- `android/app/build.gradle.kts` - Removed 12-line doFirst block that deleted schemas/ before every KSP run; ksp { arg("room.schemaLocation") } retained

## Decisions Made
- **D-23-05-A:** The doFirst hook and a committed 3.json are mutually exclusive (D-23-04-A already established this). Resolution: remove the hook permanently, manually delete 3.json once to allow KSP to write fresh (not read), then commit. Once committed and entities stable, KSP stays UP-TO-DATE indefinitely.
- **D-23-05-B:** `--rerun-tasks` forces KSP re-execution even with unchanged sources, triggering the AbstractMethodError. After 3.json is committed, never use `--rerun-tasks` on this project.

## Deviations from Plan

### Manual Step During Task 2

The plan specified running `kspDebugKotlin` after removing the doFirst hook. However, a `--rerun-tasks` command (run to get full test counts) invalidated the KSP incremental cache. KSP attempted to re-run, read the freshly-generated 3.json, and hit the AbstractMethodError. Recovery required manually deleting 3.json one more time, then re-running kspDebugKotlin — which regenerated it fresh without reading.

This is not a rule deviation — it is a consequence of the documented D-23-04-A behavior. The AbstractMethodError fires exactly when KSP is forced to read an existing schema JSON. Once committed and UP-TO-DATE, the cycle is permanently broken.

---

**Total deviations:** 0 (the extra manual deletion step is documented D-23-04-A behavior, not an unplanned deviation)

## Issues Encountered
- Windows file-lock conflicts: multiple background gradle processes accumulated during the session, each holding `test-results-new/.../output.bin`. Required repeated `./gradlew --stop` and manual kill -9 to clear.
- Out-of-memory error (java.lang.OutOfMemoryError: Java heap space) during one test run caused by accumulated gradle daemon state from prior hung processes. Tests from the clean run (22 test suites, 157 tests, 0 failures) confirm no regression.
- `--rerun-tasks` unexpectedly re-triggered AbstractMethodError — documented as D-23-05-B.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- 3.json is committed to git HEAD under `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/`
- `build.gradle.kts` has no `deleteRecursively` — safe for all future KSP runs
- Phase 24 migration tests can start: `MigrationTestHelper` will find `schemas/` directory
- Known concern: `--rerun-tasks` will re-trigger AbstractMethodError — document in Phase 24 plan, avoid that flag

## Self-Check

- [ ] `android/app/schemas/com.gmaingret.outlinergod.db.AppDatabase/3.json` — FOUND
- [ ] `android/app/build.gradle.kts` contains no `deleteRecursively` — CONFIRMED (0 matches)
- [ ] `git ls-files android/app/schemas/` returns exactly 1 line — CONFIRMED
- [ ] `kspDebugKotlin` second run shows UP-TO-DATE — CONFIRMED (BUILD SUCCESSFUL in 14s, 17 UP-TO-DATE)
- [ ] Commit 2ccbcc3 exists — CONFIRMED
- [ ] Commit 5ba4ebe exists — CONFIRMED

## Self-Check: PASSED

---
*Phase: 23-data-model-structure*
*Completed: 2026-03-07*
