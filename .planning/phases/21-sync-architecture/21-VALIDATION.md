---
phase: 21
slug: sync-architecture
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-06
---

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + MockK + Kotest (Android unit tests) |
| **Config file** | `android/app/build.gradle.kts` |
| **Quick run command** | `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test --tests "*.SyncOrchestratorTest" --tests "*.SyncWorkerTest" --tests "*.DocumentListSyncTest"` |
| **Full suite command** | `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && export ANDROID_HOME="/c/Users/gmain/AppData/Local/Android/Sdk" && cd android && ./gradlew test` |
| **Estimated runtime** | ~60 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run quick run command
- **After every plan wave:** Run full suite
- **Before `/gsd:verify-work`:** Full suite must be green (222+ tests)
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 21-01-W0 | 01 | 0 | SyncOrchestrator unit tests | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ W0 | ⬜ pending |
| 21-01-W0b | 01 | 0 | SyncOrchestrator integration test | integration | `./gradlew test --tests "*.SyncOrchestratorIntegrationTest"` | ❌ W0 | ⬜ pending |
| 21-01-01 | 01 | 1 | SyncOrchestratorImpl — happy path | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ W0 | ⬜ pending |
| 21-01-02 | 01 | 1 | SyncOrchestratorImpl — auth failure | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ W0 | ⬜ pending |
| 21-01-03 | 01 | 1 | SyncOrchestratorImpl — pull failure | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ W0 | ⬜ pending |
| 21-01-04 | 01 | 1 | SyncOrchestratorImpl — push failure | unit | `./gradlew test --tests "*.SyncOrchestratorTest"` | ❌ W0 | ⬜ pending |
| 21-02-01 | 01 | 1 | SyncWorker delegates to orchestrator | unit | `./gradlew test --tests "*.SyncWorkerTest"` | ✅ (must update) | ⬜ pending |
| 21-02-02 | 01 | 1 | DocumentListViewModel delegates | unit | `./gradlew test --tests "*.DocumentListSyncTest"` | ✅ (must update) | ⬜ pending |
| 21-02-03 | 01 | 1 | NodeEditorViewModel delegates | unit | `./gradlew test --tests "*.NodeEditorSyncTest"` | ✅ (must update) | ⬜ pending |
| 21-03-01 | 01 | 1 | Full regression — 222+ tests | regression | `./gradlew test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorTest.kt` — 4 unit test stubs (happy path, auth failure, pull failure, push failure)
- [ ] `android/app/src/test/java/com/gmaingret/outlinergod/sync/SyncOrchestratorIntegrationTest.kt` — integration test with real in-memory Room (mirrors `SyncWorkerIntegrationTest` pattern)

*No new framework installs required — all test infrastructure already present.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| None | — | — | — |

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
