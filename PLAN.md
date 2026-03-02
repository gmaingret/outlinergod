# OutlinerGod — Implementation Plan

## How to Use This Plan

> To start an implementation session, paste this prompt:
>
> "We are now implementing Phase [X]. PLAN_PHASE[X].md and API.md are the source of truth.
> Rules for this session:
> 1. Work exactly one task at a time, in task ID order
> 2. Before starting each task, state: 'Starting [Task ID]: [Title]'
> 3. For each task: (a) write the feature code, (b) write the test file immediately after, (c) run the tests (pnpm test / ./gradlew test), (d) show me the test output — I must see green before proceeding, (e) git add -A && git commit -m 'P[X]-[N]: [Title]' && git push origin main, (f) state 'Task [ID] complete. Tests passing. Pushed.' then pause for confirmation
> 4. If a test fails, fix it before moving to the next task
> 5. Do not start the next phase without explicit instruction
> 6. Never commit .env or secrets — only .env.example
> Start with P[X]-1."

---

## Phases

| Phase | File | Focus | Tasks | Status |
|---|---|---|---|---|
| 0 | PLAN_PHASE0.md | Prototypes — DnD, WYSIWYG, Sync round-trip | 3 | ✅ Planned |
| 1 | PLAN_PHASE1.md | Backend Foundation — scaffold, Docker, DB, HLC | 16 | ✅ Planned |
| 2 | PLAN_PHASE2.md | Backend API — all routes + deployment | 28 | ✅ Planned |
| 3 | PLAN_PHASE3.md | Android Setup — Gradle, Hilt, Room, Ktor, Nav | 15 | ✅ Planned |
| 4 | PLAN_PHASE4.md | Android Core — editor, tree, DnD, sync, auth | 17 | ✅ Planned |
| 5 | PLAN_PHASE5.md | Android Advanced — search, export, bookmarks | 15 | ✅ Planned |
| 6 | PLAN_PHASE6.md | Integration, polish, end-to-end testing | 14 | ✅ Planned |

---

## Constraints (apply to all phases)

- One concern = one task (~1–2 hours per task)
- Every task has: Task ID, Title, What to build, Inputs required, Output artifact, How to test, Risk
- Tests must be green before moving to the next task — never skip, never todo
- Backend tests: Vitest, `@fastify/inject`, in-memory SQLite via `createTestDb()`
- Android tests: `./gradlew test`, Room in-memory DB, Kotest for property-based tests
- Do not start the next phase without explicit instruction

---

## Key Technical Decisions

- **Sync**: HLC (Hybrid Logical Clock) + per-field LWW (Last Write Wins)
- **Auth**: Google Sign-In → JWT access token (1 h) + opaque refresh token (30 days)
- **Database**: SQLite via better-sqlite3 (backend), Room (Android)
- **API**: Fastify v5, TypeBox validation, Drizzle ORM
- **Android**: Kotlin 2.x, Jetpack Compose, Hilt, Ktor Client 3.4.0
- **sort_order**: Fractional indexing (TEXT, never REAL)
- **Soft delete**: `deleted_at` + `deleted_hlc` on all syncable entities
- **Search**: Client-side FTS5 only (Room). No server search endpoint.
- **File ownership**: Tracked via `files` metadata table in backend DB
