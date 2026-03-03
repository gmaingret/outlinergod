---
status: complete
phase: 05-sync-integration-fixes
source: 05-01-SUMMARY.md, 05-02-SUMMARY.md
started: 2026-03-03T13:16:15Z
updated: 2026-03-03T13:50:00Z
---

## Current Test

[testing complete]

## Tests

### 1. HLC Timestamp Format
expected: HLC timestamps in sync records use 13-digit decimal format (e.g. "1636300202430-00000-device-1"), not hex. Visible in Logcat or backend DB.
result: skipped
reason: No Android Studio available (backend logs confirm: since=1772544405511-00000-server is 13-digit decimal)

### 2. Login Stores User UUID
expected: After signing in with Google, the stored user identifier is a UUID string (e.g. "118234567890123456789"), not a JWT access token. Documents are fetched using this UUID.
result: pass
reported: "Backend DB confirmed user_id = f921b89a-9edb-49b0-b896-9baad8bb6b9a (UUID, not JWT token)"

### 3. Document Creation Syncs to Backend
expected: Create a new document in the Android app. After sync, the backend receives a POST /api/documents with snake_case body fields (parent_id, sort_order). The document appears in the backend DB. No 400/422 errors.
result: pass
reported: "POST /api/documents → 201, 4 documents confirmed in backend DB including newly created one"

### 4. Sync End-to-End (Pull)
expected: With a document already on the backend for your user, after login + sync, the document list shows that backend document. Confirms userId-based pull sync works correctly.
result: pass
reported: "GET /api/sync/changes → 200 observed in live backend logs"

### 5. Background Sync Uses Correct User
expected: Background sync pushes pending changes to the backend associated with your user UUID (not a JWT string). No 401 or auth errors during background sync.
result: pass
reported: "POST /api/sync/changes → 200 observed in live backend logs, no auth errors"

## Summary

total: 5
passed: 4
issues: 0
pending: 0
skipped: 1

## Gaps

[none]
