# OutlineGod — Pre-Implementation Review

## Summary

**12 files reviewed. 19 issues found — 6 critical, 5 important, 3 operational gaps, 5 residual errors. All fixed in the updated files.**

---

## Critical Issues Fixed (Would Block Claude Code)

### 1. P1-2 Smoke Test Sequencing — Tests Can't Pass at Task 2
**File**: PLAN_PHASE1.md  
**Problem**: P1-2 was the second task but contained `createTestDb()` (needs migration runner from P1-11) and smoke tests checking for 6 tables (which don't exist until P1-10). Rules say "tests must pass before next task" → deadlock.  
**Fix**: Split P1-2 into config-only (P1-2) + moved `createTestDb()` into P1-11 (migration runner) + added P1-16 as the smoke test exit gate. Phase 1 now has 16 tasks.

### 2. Systematic Off-by-One Dependency References (23 errors)
**Files**: PLAN_PHASE1.md, PLAN_PHASE2.md  
**Problem**: Every `Inputs required` field in Phase 1 (P1-5 through P1-15) and Phase 2 (P2-1, P2-3–P2-5, P2-9, P2-10, P2-15–P2-22, P2-26–P2-27) had wrong task ID references. For example, P1-5 cited "P1-3 (db connection module)" but db connection is P1-4. P2-1 cited "P1-13 (auth middleware)" but auth middleware is P1-14.  
**Fix**: All 23 cross-references corrected to point to the right task IDs.

### 3. Missing `files` Metadata Table
**Files**: All schema-related files  
**Problem**: API.md specifies `GET /api/files/:filename` returns 403 for files belonging to other users, and `DELETE /api/files/:filename` also checks ownership. But no `files` table existed in the schema to track which user owns which file. P2-18 acknowledged this: "since no files table exists, skip ownership check."  
**Fix**: Added `files` table to P1-10 (schema task), updated API.md common definitions, updated P2-17 to insert metadata on upload, updated P2-18 to check ownership via the table. Smoke test updated to check for 7 tables.

### 4. JWT Expiry Conflict
**File**: CLAUDE.md  
**Problem**: CLAUDE.md said "JWT access token (15 min)" but API.md and P2-1 both specify 1 hour.  
**Fix**: CLAUDE.md updated to "1 hour" to match API.md.

### 5. Missing Search API Endpoint — P5-1 Calls Non-Existent Route
**File**: PLAN_PHASE5.md  
**Problem**: P5-1 `SearchRepositoryImpl` called `GET /api/search?q={query}&type=node` but this endpoint doesn't exist in API.md. The architecture specifies search as client-side FTS5 in Room (ARCHITECTURE.md §3).  
**Fix**: P5-1 rewritten to query local Room FTS5 virtual table (`nodes_fts`) via `NodeDao` instead of a server endpoint.

### 6. CLAUDE.md Hilt Modules Don't Match Phase 3
**File**: CLAUDE.md  
**Problem**: Listed `AuthModule` and `SyncModule` but Phase 3 creates `ClockModule` and `RepositoryModule` instead.  
**Fix**: Module table updated to match actual Phase 3 implementation: `DatabaseModule`, `NetworkModule`, `RepositoryModule`, `ClockModule`.

---

## Important Issues Fixed

### 7. PLAN.md Status Table Outdated
**Problem**: Phases 4–6 showed "TBD" task counts and "⏳ Pending" status despite having fully planned files.  
**Fix**: Updated with actual task counts (14, 12, 12) and "✅ Planned" status.

### 8. ARCHITECTURE.md `children` TypeConverter Reference
**Problem**: Section 1 mentions "The `children` JSON array serializes through a simple `@TypeConverter`" which directly contradicts the core rule in CLAUDE.md/PRD that "There is no `children` array in the database."  
**Fix**: Sentence replaced with clarification that OutlineGod does NOT use a children array.

### 9. Deployment Details Not Captured
**Problem**: Docker server (`greg@192.168.1.50`), GitHub repo (`gmaingret/outlinergod`), and Google OAuth SHA-1 fingerprint were not documented in any project file.  
**Fix**: Added "Deployment & Repository" section to CLAUDE.md with all three details.

### 10. Search Architecture Clarification
**Problem**: No explicit statement anywhere that search is client-side only.  
**Fix**: Added "Search: Client-side FTS5 only (Room). No server search endpoint." to CLAUDE.md hard rules and PLAN.md key decisions.

### 11. PLAN.md Auth Token Duration
**Problem**: PLAN.md key decisions said nothing about token duration, creating ambiguity.  
**Fix**: Added "JWT access token (1 h)" explicitly.

---

## Files Changed

| File | Changes |
|------|---------|
| `CLAUDE.md` | JWT expiry fix, Hilt modules fix, deployment details added, search clarification, git workflow rules |
| `PLAN.md` | Task counts updated (P1:16, P2:28, P4:17, P5:15, P6:14), status table corrected, search + auth decisions added, git push in prompt template |
| `PLAN_PHASE1.md` | P1-2 split, all dependency refs fixed, files table added to P1-10, P1-11 migration test 6→7 tables, P1-16 smoke test added, git init in P1-1 |
| `PLAN_PHASE2.md` | All 14 Phase-1 cross-references corrected, P2-17/P2-18 updated for files table, goal paragraph P1-13→P1-14 and P1-2→P1-11, P2-28 deployment task added |
| `PLAN_PHASE4.md` | SHA-1 fingerprint added to P4-2 Google Cloud Console prerequisite |
| `PLAN_PHASE5.md` | P5-1 search rewritten for client-side FTS5 |
| `PLAN_PHASE6.md` | google-services.json removed, P6-145 header typo fixed, P6-14 self-reference fixed |
| `ARCHITECTURE.md` | Removed misleading `children` TypeConverter reference |
| `API.md` | Added File Ownership section to common definitions |

## Files Unchanged

| File | Status |
|------|--------|
| `PRD.md` | ✅ Clean — no issues found |
| `PLAN_PHASE0.md` | ✅ Clean |
| `PLAN_PHASE3.md` | ✅ Clean |

---

## Operational Gaps Fixed (Would Break Autonomous Workflow)

### 12. No Git Init or Push Cadence in Task Plans
**Files**: PLAN_PHASE1.md, PLAN.md  
**Problem**: CLAUDE.md documented the GitHub repo but no task actually ran `git init` or pushed code. Claude Code won't autonomously push unless a task tells it to.  
**Fix**: P1-1 now begins with `git init`, `.gitignore` creation, and `git remote add origin`. PLAN.md prompt template updated: step (e) is now `git add -A && git commit && git push origin main` after every green test run. Rule 6 added: never commit `.env`.

### 13. No Deployment Task in Phase 2
**Files**: PLAN_PHASE2.md, PLAN.md  
**Problem**: Phase 2 built the Docker backend but never deployed it to `greg@192.168.1.50`. The separate "Post-Phase 2" deployment prompt was outside the task system and could be missed.  
**Fix**: Added P2-28 as a proper task: creates `docker-compose.yml`, `Dockerfile`, `.env.example`, SSHes to server, runs `docker compose up -d --build`, verifies health check. Phase 2 task count updated to 28.

### 14. SHA-1 Fingerprint Not Referenced in Google Sign-In Task
**Files**: PLAN_PHASE4.md  
**Problem**: P4-2 (LoginScreen) used `GetGoogleIdOption` with `BuildConfig.GOOGLE_CLIENT_ID` but never mentioned the SHA-1 fingerprint needed to register the Android credential in Google Cloud Console. Claude Code wouldn't know to configure it.  
**Fix**: P4-2 now states the SHA-1 (`D8:B0:6D:19:92:71:D4:DA:60:EF:A7:0A:93:A4:5C:29:B7:A3:D5:C4`) and requires registering package `com.outlinegod.app` in Google Cloud Console. Notes that the web client ID (not Android client ID) goes in both `BuildConfig` and backend `.env`.

---

## Residual Errors Fixed (Final Pass)

### 15. P1-11 Migration Test Checks 6 Tables, Should Be 7
**File**: PLAN_PHASE1.md  
**Problem**: `migrate_createsAllSixTables` listed 6 tables (`bookmarks, documents, nodes, refresh_tokens, settings, users`) but the `files` table is defined in P1-10 and should be created by migration. P1-16 smoke test correctly checks 7 — the two tests contradicted each other.  
**Fix**: Renamed to `migrate_createsAllSevenTables`, added `files` to the expected list.

### 16. Phase 2 Goal Paragraph Has Wrong Task IDs
**File**: PLAN_PHASE2.md  
**Problem**: Intro says "requireAuth preHandler from P1-13" (P1-13 is LWW merge, not auth) and "createTestDb() from P1-2" (P1-2 is Vitest config, not migration runner).  
**Fix**: Corrected to P1-14 (auth middleware) and P1-11 (migration runner + createTestDb).

### 17. PLAN.md Task Counts Wrong for Phases 4, 5, 6
**File**: PLAN.md  
**Problem**: Phase 4 listed 14 tasks (actual: 17, P4-0 through P4-16). Phase 5 listed 12 (actual: 15, P5-1 through P5-15). Phase 6 listed 12 (actual: 14, P6-1 through P6-14).  
**Fix**: Updated to 17, 15, 14 respectively.

### 18. P6-145 Header Typo
**File**: PLAN_PHASE6.md  
**Problem**: Section header reads `### P6-145:` instead of `### P6-14:`. The Task ID field inside correctly says P6-14.  
**Fix**: Header corrected to `### P6-14:`.

### 19. P6-14 Self-Referencing Dependency
**File**: PLAN_PHASE6.md  
**Problem**: `Inputs required: All P6-1 through P6-14 tasks completed` — P6-14 can't depend on itself.  
**Fix**: Changed to "P6-1 through P6-13".

---

## Implementation Prompts

### Phase 0 — Prototypes
```
We are now implementing Phase 0. PLAN_PHASE0.md and API.md are the source of truth.
Rules for this session:
1. Work exactly one task at a time, in task ID order
2. Before starting each task, state: 'Starting [Task ID]: [Title]'
3. For each task: (a) write the feature code, (b) write the test file immediately after, (c) run the tests (pnpm test / ./gradlew test), (d) show me the test output — I must see green before proceeding, (e) git add -A && git commit -m 'P0-[N]: [Title]' && git push origin main, (f) state 'Task [ID] complete. Tests passing. Pushed.' then pause for confirmation
4. If a test fails, fix it before moving to the next task
5. Do not start the next phase without explicit instruction
6. Never commit .env or secrets — only .env.example
Start with P0-1.
```

### Phase 1 — Backend Foundation
```
We are now implementing Phase 1. PLAN_PHASE1.md and API.md are the source of truth.
Rules for this session:
1. Work exactly one task at a time, in task ID order
2. Before starting each task, state: 'Starting [Task ID]: [Title]'
3. For each task: (a) write the feature code, (b) write the test file immediately after, (c) run the tests (pnpm test / ./gradlew test), (d) show me the test output — I must see green before proceeding, (e) git add -A && git commit -m 'P1-[N]: [Title]' && git push origin main, (f) state 'Task [ID] complete. Tests passing. Pushed.' then pause for confirmation
4. If a test fails, fix it before moving to the next task
5. Do not start the next phase without explicit instruction
6. Never commit .env or secrets — only .env.example
Start with P1-1.
```

### Phase 2 — Backend API + Deployment
```
We are now implementing Phase 2. PLAN_PHASE2.md and API.md are the source of truth.
Rules for this session:
1. Work exactly one task at a time, in task ID order
2. Before starting each task, state: 'Starting [Task ID]: [Title]'
3. For each task: (a) write the feature code, (b) write the test file immediately after, (c) run the tests (pnpm test / ./gradlew test), (d) show me the test output — I must see green before proceeding, (e) git add -A && git commit -m 'P2-[N]: [Title]' && git push origin main, (f) state 'Task [ID] complete. Tests passing. Pushed.' then pause for confirmation
4. If a test fails, fix it before moving to the next task
5. Do not start the next phase without explicit instruction
6. Never commit .env or secrets — only .env.example
7. P2-28 deploys to greg@192.168.1.50 — see task for SSH + docker compose instructions
Start with P2-1.
```

### Phases 3–6 follow the same prompt pattern, substituting the phase number.
