# Technology Stack

**Analysis Date:** 2026-03-06

## Languages

**Primary:**
- Kotlin 2.1.0 — Android app (all application logic, UI, data layer)
- TypeScript 5.x — Backend API server (`backend/tsconfig.json` targets ES2022, NodeNext modules)
- TypeScript 5.9.3 — Web SPA (`web/tsconfig.json`)

**Secondary:**
- SQL — SQLite schemas via Drizzle ORM (`backend/src/db/schema/`) and Room raw SQL (`android/app/src/main/java/.../db/`)

## Runtime

**Backend Environment:**
- Node.js 24 (v24.13.0 installed locally; Dockerfile pins `node:20-alpine` — version mismatch between dev host and Docker image)
- Package Manager: pnpm 10.28 (via corepack in Docker; `backend/pnpm-lock.yaml` present)

**Android Environment:**
- JVM target: Java 17 (`compileOptions` + `kotlinOptions.jvmTarget = "17"`)
- Min SDK: 26 (Android 8.0); Target SDK: 36; Compile SDK: 36
- Gradle 8.13 + AGP 8.13.0
- KSP 2.1.0-1.0.29 (annotation processing for Hilt + Room)

**Web SPA Environment:**
- Vite 7.x build tool (`web/vite.config.ts`)
- Node.js (no explicit version pinned — no `.nvmrc`)

## Frameworks

**Backend:**
- Fastify 5.7.4 — HTTP server framework (ESM-only, TypeBox type provider)
- Drizzle ORM 0.45.1 — Schema definition and migration runner
- drizzle-kit 0.31.0 — Migration generation CLI

**Android:**
- Jetpack Compose (BOM 2025.04.01) — UI framework
- Orbit MVI 10.0.0 — State management (`orbit-core`, `orbit-viewmodel`)
- Hilt 2.56.1 — Dependency injection
- Room 2.8.4 (KSP) — Local SQLite ORM
- Ktor Client 3.1.3 (OkHttp engine) — HTTP client
- WorkManager 2.10.0 — Background sync scheduling
- Navigation Compose 2.8.5 — In-app navigation

**Web SPA:**
- React 19.2.0 + React DOM — UI framework
- React Router DOM 7.13.1 — Client-side routing
- Tailwind CSS 4.2.1 (via `@tailwindcss/vite`) — Styling

**Testing (Backend):**
- Vitest 2.x — Test runner and assertions
- `pool: 'forks'` required for `better-sqlite3` native module (threads mode causes segfaults)
- Config: `backend/vitest.config.ts`

**Testing (Android):**
- JUnit 4.13.2 — Unit test runner
- Mockk 1.14.0 — Mocking
- Kotest 5.9.1 — Property-based tests (commutativity/idempotency of HLC)
- Robolectric 4.13 — JVM-side Android unit tests (max SDK 34 — all tests require `@Config(sdk = [34])`)
- Room Testing 2.8.4 — In-memory Room DB for DAO tests
- Orbit Test 10.0.0 — ViewModel orbit-test DSL
- WorkManager Testing 2.10.0

**Testing (Web SPA):**
- Vitest (via `web/vitest.config.ts`)
- Testing Library (React + user-event + jest-dom)

**Build/Dev:**
- `tsx` 4.21.0 — TypeScript execution for backend dev (`tsx watch src/server.ts`)
- Vite 7.x — Web SPA bundler with API proxy to backend at `localhost:3000`

## Key Dependencies

**Critical:**
- `better-sqlite3` 12.6.2 — Synchronous SQLite driver for backend; requires native build (python3/make/g++ in Docker build stage)
- `google-auth-library` 10.6.1 — Google ID token verification on backend
- `jose` 6.1.3 — JWT signing (`HS256`) and verification on backend
- `sh.calvin.reorderable` 2.5.1 — Drag-and-drop reordering in Android `LazyColumn` (v2.x API — not v3)
- `kotlinx-serialization-json` 1.7.3 — JSON serialization (pinned below 1.8.0 for Kotlin 2.1.0 compat)
- `androidx.credentials:credentials` 1.3.0 — Google Sign-In (Credential Manager API)
- `com.google.android.libraries.identity.googleid:googleid` 1.1.1

**Infrastructure:**
- `@fastify/multipart` 9.4.0 — File upload handling
- `@fastify/rate-limit` 10.3.0 — Rate limiting (scoped to auth routes: 10 req/min)
- `@fastify/static` 8.0.4 — Serves React SPA `web/dist` files
- `@fastify/type-provider-typebox` 4.0.0 — Runtime schema validation
- `jszip` 3.10.1 — ZIP archive creation for data export endpoint
- `coil3` 3.1.0 (compose + network-okhttp) — Image loading in Android
- `androidx.datastore:datastore-preferences` 1.1.1 — Token and settings persistence on Android

## Configuration

**Backend Environment:**
- `.env` file present at project root — loaded via `env_file: .env` in `docker-compose.yml`
- Key env vars required: `JWT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_WEB_CLIENT_ID`, `DATABASE_PATH` (defaults `/data/outlinergod.db`), `UPLOADS_PATH` (defaults `/data/uploads`), `MAX_UPLOAD_BYTES` (defaults 50 MB), `PORT` (defaults 3000), `WEB_DIST_PATH`
- Build arg: `VITE_GOOGLE_CLIENT_ID` passed to Docker for web SPA at build time

**Android Build:**
- `android/local.properties` — holds `GOOGLE_CLIENT_ID` (web client ID) and `BASE_URL`; injected as `BuildConfig` fields at compile time
- `android/gradle.properties` — sets `ksp.incremental=false` and `ksp.incremental.intermodule=false` (workaround for KSP incremental build corruption)

**Backend Build:**
- `backend/tsconfig.json` — strict mode, ES2022 target, NodeNext modules
- `backend/drizzle.config.ts` — points to `../data/outlinergod.db` for local dev migration generation

## Platform Requirements

**Development:**
- Android Studio JBR (JDK 21) for Android compilation
- `ANDROID_HOME` pointing to Android SDK
- Node.js 24 + pnpm for backend dev
- python3, make, g++ for `better-sqlite3` native build (automated in Dockerfile)

**Production:**
- Docker (multi-stage build: `web-builder` → `ts-builder` → `runner`)
- Docker volume `outlinergod-data` mounted at `/data` for SQLite DB + file uploads
- Exposed port: 3000
- Health check: `GET /health` returns `{"status":"ok",...}`
- Target host: `root@192.168.1.50`, path `/root/outlinergod/`
- `docker compose up -d --build` — migrations run automatically on startup before routes are registered

**Notable Constraints:**
- Ktor 3.4.0 is not compatible with Kotlin 2.1.0 — pinned to 3.1.3
- `kotlinx-serialization` 1.8.0 incompatible with Kotlin 2.1.0 — pinned to 1.7.3
- Robolectric 4.13 max supported SDK = 34 (not 35 or 36)
- `better-sqlite3` requires `pool: 'forks'` in Vitest — thread pool causes native module segfaults
- Node.js version mismatch: dev host runs 24, Docker image uses `node:20-alpine`

---

*Stack analysis: 2026-03-06*
