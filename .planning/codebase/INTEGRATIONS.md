# External Integrations

**Analysis Date:** 2026-03-06

## APIs & External Services

**Google Identity:**
- Google OAuth 2.0 — Authenticates Android users via the Credential Manager API; authenticates web SPA users via `@react-oauth/google`
  - Android SDK: `androidx.credentials:credentials:1.3.0` + `com.google.android.libraries.identity.googleid:googleid:1.1.1`
  - Backend verification SDK: `google-auth-library` 10.6.1 (`OAuth2Client.verifyIdToken`)
  - Web SDK: `@react-oauth/google` 0.13.4
  - Auth env var: `GOOGLE_CLIENT_ID` (Android client ID), `GOOGLE_WEB_CLIENT_ID` (web client ID) — both verified as valid audience in `backend/src/routes/auth.ts`
  - Flow: Android/Web sends Google `id_token` → `POST /api/auth/google` → backend verifies with Google JWKS → returns internal JWT + refresh token

**No other third-party external APIs are integrated.** All functionality is self-hosted.

## Data Storage

**Databases:**
- SQLite (backend) — Primary persistent store for users, nodes, documents, bookmarks, settings, files metadata, refresh tokens
  - Driver: `better-sqlite3` 12.6.2 (synchronous, native module)
  - ORM/Schema: Drizzle ORM 0.45.1 (`backend/src/db/schema/index.ts`)
  - File path: `/data/outlinergod.db` (Docker volume `outlinergod-data`)
  - Pragmas: WAL journal mode, foreign keys ON (set in `backend/src/db/connection.ts`)
  - Migrations: Auto-run on startup via `backend/src/db/migrate.ts`; SQL files in `backend/drizzle/`

- SQLite (Android/Room) — Local replica of server data for offline use
  - ORM: Room 2.8.4 with KSP code generation
  - Database class: `android/app/src/main/java/com/gmaingret/outlinergod/db/AppDatabase.kt`
  - Entities: `NodeEntity`, `DocumentEntity`, `BookmarkEntity`, `SettingsEntity` (all with HLC columns for per-field sync)
  - FTS: FTS4 virtual table for full-text search (NOT FTS5 — device SQLite may lack it); created via raw SQL in Room migration

**File Storage:**
- Local filesystem only — uploaded files stored in Docker volume at `/data/uploads/` (env var `UPLOADS_PATH`)
- Filenames are UUID + extension, validated with regex `/^[0-9a-f-]{36}\.[a-z0-9]+$/` before serving
- File metadata tracked in `files` table (`backend/src/db/schema/files.ts`) with `user_id` ownership
- Max upload size: `MAX_UPLOAD_BYTES` env var (default 50 MB / 52428800 bytes)
- No cloud object storage (no S3, GCS, Cloudinary, etc.)

**Caching:**
- None — no Redis, Memcached, or in-memory caching layer

## Authentication & Identity

**Auth Provider:** Google OAuth 2.0 (external identity provider only — no Supabase Auth, Auth0, Firebase Auth)

**Backend Token Strategy:**
- JWT: `jose` 6.1.3, HS256 algorithm, 1-hour expiry, signed with `JWT_SECRET` env var
- Refresh Tokens: 30-day expiry, stored in `refresh_tokens` table, rotated on each use (old token revoked, new token issued)
- Token rotation: `POST /api/auth/refresh` — implemented in `backend/src/routes/auth.ts`
- Rate limiting: auth routes rate-limited to 10 req/min per IP via `@fastify/rate-limit` scoped to auth plugin only

**Android Token Management:**
- Access token and refresh token stored in `androidx.datastore:datastore-preferences` 1.1.1
- `AuthRepository` interface: `android/app/src/main/java/com/gmaingret/outlinergod/repository/AuthRepository.kt`
- Implementation in `android/app/src/main/java/com/gmaingret/outlinergod/repository/impl/`
- Mutex-guarded refresh in `AuthRepository` implementation — prevents concurrent refresh races
- `KtorClientFactory` (`android/.../network/KtorClientFactory.kt`) intercepts 401 responses and calls `tokenRefresher` before retrying; uses `dagger.Lazy<AuthRepository>` to break circular DI dependency

**Web SPA Token Strategy:**
- Tokens managed via `AuthContext` (`web/src/auth/AuthContext.tsx`)
- API calls via `web/src/auth/api.ts`

## Monitoring & Observability

**Error Tracking:**
- None — no Sentry, Datadog, Rollbar, or similar

**Logs:**
- Backend: Fastify built-in logger (`Fastify({ logger: true })` in `backend/src/index.ts`) — outputs JSON to stdout; visible via `docker logs`
- Android: `SyncLogger` utility (`android/app/src/main/java/com/gmaingret/outlinergod/util/SyncLogger.kt`) — logs HTTP requests/responses from Ktor interceptor; tag "Ktor"
- No structured log aggregation (no ELK, Loki, CloudWatch)

## CI/CD & Deployment

**Hosting:**
- Self-hosted Docker on `root@192.168.1.50` at `/root/outlinergod/`
- Single-container deployment (`docker-compose.yml`) — no orchestration (no Kubernetes, Swarm)
- Docker image: multi-stage build (`backend/Dockerfile`)
  - Stage 1 `web-builder`: builds React SPA with `node:20-alpine`
  - Stage 2 `ts-builder`: compiles TypeScript backend with native `better-sqlite3` build tools
  - Stage 3 `runner`: lean production image, runs as non-root `node` user

**CI Pipeline:**
- None — no GitHub Actions, CircleCI, or other CI; deployments are manual SSH + docker compose

**Git Remote:**
- `https://github.com/gmaingret/outlinergod.git` (branch: master)

## Environment Configuration

**Required env vars (backend):**
- `JWT_SECRET` — signs and verifies all backend JWTs (no default — must be set)
- `GOOGLE_CLIENT_ID` — Android OAuth client ID for Google token verification
- `GOOGLE_WEB_CLIENT_ID` — Web OAuth client ID (also accepted as token audience)
- `DATABASE_PATH` — SQLite file location (defaults to `/data/outlinergod.db`)
- `UPLOADS_PATH` — File storage directory (defaults to `/data/uploads`)
- `PORT` — Server listen port (defaults to `3000`)
- `WEB_DIST_PATH` — Path to built React SPA (defaults to relative `../../web/dist`)
- `MAX_UPLOAD_BYTES` — Upload size limit in bytes (defaults to `52428800`)
- `VITE_GOOGLE_CLIENT_ID` — Build-time arg for web SPA Google login

**Android build-time config (`android/local.properties`):**
- `GOOGLE_CLIENT_ID` — Must be the **web** client ID (not Android client ID) for `serverClientId`
- `BASE_URL` — Backend base URL (defaults to `http://10.0.2.2:3000` for emulator)

**Secrets location:**
- Backend secrets in `.env` file at project root (excluded from git via `.gitignore`)
- Android secrets in `android/local.properties` (excluded from git)
- No secrets manager (no Vault, AWS Secrets Manager, etc.)

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None

## Sync Architecture

**Sync Protocol:** Pull-based polling only — no WebSockets, SSE, or real-time push
- Android pulls from `GET /api/sync/pull?since=<hlc>&deviceId=<id>` and pushes to `POST /api/sync/push`
- Background sync: WorkManager `SyncWorker` (`android/.../sync/SyncWorker.kt`) runs every 15 minutes when `NetworkType.CONNECTED`, with exponential backoff starting at 30s, `ExistingPeriodicWorkPolicy.UPDATE`
- Foreground sync: triggered on screen resume and after 30s inactivity timer in `NodeEditorViewModel`
- Conflict resolution: per-field Hybrid Logical Clock (HLC) Last-Write-Wins; HLC format `<13-digit-unix-ms>-<5-digit-counter>-<deviceId>`
- Tombstones: soft deletes with `deleted_at` / `deleted_hlc`; purged after 90 days on server startup

**HLC Implementation:**
- Backend: `backend/src/hlc.ts`
- Android: `android/.../sync/HlcClock.kt`
- Merge logic: `android/.../sync/NodeMerge.kt`, `DocumentMerge.kt`, `BookmarkMerge.kt`

---

*Integration audit: 2026-03-06*
