# OutlinerGod

Self-hosted Android outliner app with sync. Android client (Kotlin + Compose) + Node.js backend (Fastify + SQLite), deployed via Docker.

## Project Structure

- `android/` — Kotlin/Compose Android app
- `backend/` — Fastify/TypeScript REST API

## Backend — Development

**Prerequisites:** Node.js 20+, pnpm

```bash
cd backend
pnpm install
pnpm dev
```

Dev server runs at http://localhost:3000.

## Backend — Production (Docker)

**This is the only supported production deployment path.**

**Prerequisites:** Docker, Docker Compose

```bash
docker compose up -d --build
```

Run from the project root. This builds and starts the backend container.

**Health check:** `curl http://localhost:3000/health` returns `{"status":"ok","db":"ok",...}`

**Why `pnpm build` fails on the Docker host:**
The Dockerfile uses a multi-stage build. The `builder` stage installs pnpm and compiles TypeScript. The compiled `dist/` is copied into the lean `runner` stage and is never committed to git. The Docker host needs neither pnpm nor Node.js — the full build pipeline runs inside Docker during `docker compose up -d --build`.

**Environment:** Requires a `.env` file at the project root. See `ARCHITECTURE.md` for required variables.

## Android — Build

**Prerequisites:** Android Studio, JDK 21

```bash
cd android
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

## Android — Tests

```bash
cd android
./gradlew test
```
