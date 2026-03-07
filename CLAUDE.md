# OutlinerGod — Claude Code Project Memory

## First Session — Planning Only

When opening this project for the first time (no `PLAN.md` exists yet):
1. Read `PRD.md` and `ARCHITECTURE.md` fully before doing anything
2. Define all REST endpoints and write them to `API.md`
3. Write the full phase/task breakdown to `PLAN.md`
4. Each task must specify: what to build, inputs required, output artifact, how to test
5. Flag the 3 HIGH-RISK tasks (drag-and-drop, WYSIWYG, sync) as Phase 0 prototypes
6. Do NOT write any application code during planning
7. When done, print: "Planning complete. Review PLAN.md and API.md before proceeding."

When `PLAN.md` already exists: skip planning and ask which phase to execute.

> **Note:** `PLAN.md` is a legacy file covering only phases 0–6. The project has progressed well beyond that. For current phase status, completed work, and next steps, read `.planning/ROADMAP.md` and `.planning/STATE.md` — these are the authoritative source of truth for all phases 7 and beyond.

---

## What This Project Is
Self-hosted Android outliner app. Two parts:
1. **Android app** (Kotlin + Jetpack Compose + Material Design 3)
2. **Backend** (Node.js/TypeScript + Fastify + SQLite, runs in Docker)

Always read both `PRD.md` and `ARCHITECTURE.md` before starting any task.
The API contract lives in `API.md` — create this file first in planning, before writing any code.

---

## Deployment & Repository

- **Git repository**: `https://github.com/gmaingret/outlinergod` — push and maintain code here autonomously
- **Docker server**: `root@192.168.1.50` — deploy Docker containers to `/root/outlinergod`
- **Google OAuth SHA-1 fingerprint**: `D8:B0:6D:19:92:71:D4:DA:60:EF:A7:0A:93:A4:5C:29:B7:A3:D5:C4` — register this in Google Cloud Console for the Android app credential (package `com.gmaingret.outlinergod`)

### Git workflow (applies to every phase)
- P1-1 must `git init` at the project root, create `.gitignore`, and `git remote add origin https://github.com/gmaingret/outlinergod.git`
- After each completed task: `git add -A && git commit -m "P[X]-[N]: [Title]" && git push origin main`
- Never commit secrets (`.env`, signing keys). The `.gitignore` must exclude `.env`, `node_modules/`, `dist/`, `.gradle/`, `build/`, `local.properties`

### Docker deployment (after Phase 2 backend is complete)
- SSH to `root@192.168.1.50`
- Copy `docker-compose.yml` and `.env` to `/root/outlinergod/`
- Run `docker compose up -d --build` from `/root/outlinergod/`
- Verify: `curl http://localhost:3000/health` returns `{"status":"ok",...}`

---

## Hard Rules (Never Violate)

### Data model
- `sort_order` is always TEXT (string fractional indexing). Never a float. Never a number.
- Tree structure is reconstructed from `parent_id` + `sort_order`. There is no `children` array in the database.
- Backlinks are computed client-side only, at document load time. The server never stores or computes backlinks.

### Sync
- Sync uses per-field Hybrid Logical Clock (HLC) + Last-Write-Wins. See `ARCHITECTURE.md` Section 4 for the exact schema and merge rules.
- No WebSockets. No SSE. No real-time push. Sync is pull-based only (Android polls on reconnect + timer).
- Deletion always wins over concurrent edits.

### Backend
- Framework: Fastify v5. No Express. No Hono.
- ORM: Drizzle + better-sqlite3. Enable WAL mode and foreign keys on startup.
- Database migrations run automatically on Docker container startup via `drizzle-kit migrate`. Never require manual migration commands.
- JWT: use `jose` library. Token expiry is **1 hour**. Google token verification: use `google-auth-library`.
- File serving: `@fastify/static` from the Docker volume mount. Validate UUID filenames before serving.
- File ownership is tracked via the `files` metadata table — every uploaded file is associated with a `user_id`.

### Android
- Architecture: Orbit MVI. One `UiState` data class per screen.
- DI: Hilt. Modules: `DatabaseModule`, `NetworkModule`, `RepositoryModule`, `ClockModule` (production modules created in Phase 3).
- Local DB: Room 2.8.4 with KSP. FTS5 virtual table and triggers created via raw SQL in a Room migration (see `ARCHITECTURE.md` Section 3 for exact SQL). Never use Room annotations for FTS5.
- HTTP client: Ktor Client 3.4.0 with OkHttp engine.
- Drag-and-drop: render tree as flat list with depth integers. Use `sh.calvin.reorderable` for reordering. Custom `pointerInput` for horizontal drag to determine reparenting.
- WYSIWYG: `BasicTextField` with `TextFieldState` + `VisualTransformation`. Phase 1 = visible markers. Marker-hiding deferred.
- Google Sign-In: Credential Manager API only (`androidx.credentials:credentials:1.3.0`). Never use the deprecated `GoogleSignInClient`.
- Image compression: Compressor library before upload. Target < 1 MB.
- Background sync: WorkManager with `NetworkType.CONNECTED`, exponential backoff, `ExistingWorkPolicy.REPLACE`.
- Search: FTS5 queries run locally via Room FTS5 virtual table. There is NO server-side search endpoint. All search is client-side.

### Enter key behavior
- In **content field**: Enter = new sibling node. Never a newline.
- In **note field**: Enter = newline within the note. Never creates a new node.
- Use `InputTransformation` to intercept Enter at the text buffer level (works for both hardware and software keyboards).

### Editing interactions
- Tap glyph = zoom in. Never collapse.
- Collapse/expand = directional arrow only.
- Long-press + move = drag. Long-press + release = context menu.
- `#` = tag. Never a heading.

---

## Project Structure

```
outlinergod/
├── android/          # Android app (Kotlin + Compose)
├── backend/          # Fastify backend (TypeScript)
├── PRD.md            # Product requirements (v4)
├── ARCHITECTURE.md   # Technical architecture decisions
├── API.md            # REST API contract (generated in planning — must exist before any code)
├── PLAN.md           # Phase/task plan (generated in planning — must exist before any code)
└── CLAUDE.md         # This file
```

---

## Hilt Module Responsibilities

| Module | Provides |
|--------|----------|
| `DatabaseModule` | Room database instance, all DAOs |
| `NetworkModule` | Ktor HTTP client, base URL config |
| `RepositoryModule` | All repository interface → implementation bindings (including AuthRepository, SyncRepository) |
| `ClockModule` | HLC clock singleton |

---

## Critical Risk Areas (Prototype First)

These three features are high-risk and must be prototyped in Phase 0 before the main build:

1. **Drag-and-drop** — flat list rendering + horizontal drag for reparenting
2. **WYSIWYG formatting** — live bold/italic with cursor stability
3. **Sync round-trip** — single node, single device, full HLC-LWW cycle

Do not proceed to Phase 1 until all three prototypes have passing acceptance tests.

---

## Testing Rules (Never Violate)

### Every task must ship with its tests — no exceptions
A task is NOT complete until its tests are written AND passing. Never mark a task done without runnable tests.

### Backend testing
- Framework: **Vitest** (`vitest@^2`) for all unit and integration tests
- HTTP integration tests: **`@fastify/inject`** (Fastify's built-in injection — no real HTTP port needed)
- Database: use an **in-memory SQLite** instance per test file (`new Database(':memory:')`) — never the real `/data` database
- Each route file gets a corresponding `*.test.ts` file in the same directory
- Test structure per route: one `describe` block per endpoint, one `it` per behavior (happy path, each error case, auth failure)
- Run after every task: `pnpm test` must pass with zero failures before moving to the next task

### Android testing
- Unit tests: **JUnit 4** + **Mockk** for ViewModel, Repository, and HLC logic
- Each ViewModel and Repository gets a corresponding `*Test.kt` file under `src/test/`
- HLC merge logic: must have property-based tests using **Kotest** verifying commutativity and idempotency (see ARCHITECTURE.md §5 Risk 5)
- FTS5 queries: integration test against a real in-memory Room database (not mocked)
- Run after every task: `./gradlew test` must pass before moving to the next task

### What counts as a passing task
1. Feature code written
2. Test file written covering: happy path + all documented error cases from API.md
3. `pnpm test` (backend) or `./gradlew test` (Android) exits 0
4. No test is skipped (`skip`, `xtest`, `@Ignore`) without a written explanation

### What NOT to test
- Do not test third-party library internals (Fastify routing, Room query compilation)
- Do not test the Android UI layer with unit tests — UI correctness is verified manually per PLAN.md acceptance criteria

---

## What NOT to Build

- No real-time push notifications
- No Firebase integration
- No "Move To" dialog (removed from scope)
- No "Export visible items only" toggle (removed from scope)
- No to-do checkboxes, numbered lists, headings, quote blocks, or code blocks
- No conflict archive — deletions are permanent, no recovery UI
- No overdue date highlighting
- No upload manager UI
- No sepia theme or custom CSS theme
- No WebSockets or SSE
- No server-side search endpoint — search is client-side FTS5 only
