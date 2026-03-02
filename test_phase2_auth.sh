#!/bin/bash
BASE="http://localhost:3000"

# JWT must be passed as env var: JWT=<token> bash test_phase2_auth.sh
if [ -z "$JWT" ]; then
  echo "ERROR: JWT env var not set. Run: JWT=<token> bash $0"
  exit 1
fi

AUTH="Authorization: Bearer $JWT"
PASS=0
FAIL=0

check() {
  local desc="$1"
  local expected="$2"
  local actual="$3"
  local body="$4"
  if [ "$actual" = "$expected" ]; then
    echo "  PASS: $desc"
    PASS=$((PASS+1))
  else
    echo "  FAIL: $desc  (expected $expected, got $actual)  body=$body"
    FAIL=$((FAIL+1))
  fi
}

echo "=== Auth ==="
R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/auth/me)
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "GET /api/auth/me → 200" 200 "$CODE" "$BODY"

echo ""
echo "=== Documents ==="
R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/documents)
CODE=$(echo "$R" | tail -1)
check "GET /api/documents → 200" 200 "$CODE"

R=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"Smoke Doc","type":"document","sort_order":"a0"}' $BASE/api/documents)
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "POST /api/documents → 201" 201 "$CODE" "$BODY"
DOC_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "$DOC_ID" ]; then
  R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/documents/$DOC_ID)
  CODE=$(echo "$R" | tail -1)
  check "GET /api/documents/:id → 200" 200 "$CODE"

  R=$(curl -s -w "\n%{http_code}" -X PATCH -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"title":"Updated"}' $BASE/api/documents/$DOC_ID)
  CODE=$(echo "$R" | tail -1)
  check "PATCH /api/documents/:id → 200" 200 "$CODE"
fi

echo ""
echo "=== Nodes ==="
if [ -n "$DOC_ID" ]; then
  R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/documents/$DOC_ID/nodes)
  CODE=$(echo "$R" | tail -1)
  check "GET /api/documents/:id/nodes → 200" 200 "$CODE"

  NODE_ID=$(cat /proc/sys/kernel/random/uuid)
  R=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"nodes\":[{\"id\":\"$NODE_ID\",\"content\":\"test\",\"note\":\"\",\"parent_id\":null,\"sort_order\":\"a0\",\"completed\":false,\"color\":0,\"collapsed\":false}]}" \
    $BASE/api/documents/$DOC_ID/nodes/batch)
  CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
  check "POST /api/documents/:id/nodes/batch → 200" 200 "$CODE" "$BODY"

  R=$(curl -s -w "\n%{http_code}" -X DELETE -H "$AUTH" $BASE/api/nodes/$NODE_ID)
  CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
  check "DELETE /api/nodes/:id → 200" 200 "$CODE" "$BODY"
fi

echo ""
echo "=== Sync ==="
R=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE/api/sync/changes?since=0&device_id=smoke-device")
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "GET /api/sync/changes → 200" 200 "$CODE" "$BODY"

R=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"device_id":"smoke-device","nodes":[],"documents":[],"bookmarks":[]}' \
  $BASE/api/sync/changes)
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "POST /api/sync/changes → 200" 200 "$CODE" "$BODY"

R=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"device_id":"smoke-device","nodes":[],"documents":[],"bookmarks":[]}' \
  $BASE/api/sync/push)
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "POST /api/sync/push → 200" 200 "$CODE" "$BODY"

echo ""
echo "=== Files ==="
R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/files)
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "GET /api/files → 200" 200 "$CODE" "$BODY"

R=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" \
  -F "file=@/etc/hostname;type=text/plain" \
  $BASE/api/files)
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "POST /api/files → 201" 201 "$CODE" "$BODY"
FNAME=$(echo "$BODY" | grep -o '"filename":"[^"]*"' | cut -d'"' -f4)

if [ -n "$FNAME" ]; then
  R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/files/$FNAME)
  CODE=$(echo "$R" | tail -1)
  check "GET /api/files/:filename → 200" 200 "$CODE"

  R=$(curl -s -w "\n%{http_code}" -X DELETE -H "$AUTH" $BASE/api/files/$FNAME)
  CODE=$(echo "$R" | tail -1)
  check "DELETE /api/files/:filename → 200" 200 "$CODE"
fi

echo ""
echo "=== Settings ==="
R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/settings)
CODE=$(echo "$R" | tail -1)
check "GET /api/settings → 200" 200 "$CODE"

R=$(curl -s -w "\n%{http_code}" -X PUT -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"theme":"dark","density":"cozy","show_guide_lines":true,"show_backlink_badge":true}' \
  $BASE/api/settings)
CODE=$(echo "$R" | tail -1)
check "PUT /api/settings → 200" 200 "$CODE"

echo ""
echo "=== Bookmarks ==="
R=$(curl -s -w "\n%{http_code}" -H "$AUTH" $BASE/api/bookmarks)
CODE=$(echo "$R" | tail -1)
check "GET /api/bookmarks → 200" 200 "$CODE"

R=$(curl -s -w "\n%{http_code}" -X POST -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"Smoke BM","target_type":"search","query":"test","sort_order":"a0"}' \
  $BASE/api/bookmarks)
CODE=$(echo "$R" | tail -1); BODY=$(echo "$R" | head -1)
check "POST /api/bookmarks → 201" 201 "$CODE" "$BODY"
BM_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -n "$BM_ID" ]; then
  R=$(curl -s -w "\n%{http_code}" -X PATCH -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"title":"Updated BM"}' $BASE/api/bookmarks/$BM_ID)
  CODE=$(echo "$R" | tail -1)
  check "PATCH /api/bookmarks/:id → 200" 200 "$CODE"

  R=$(curl -s -w "\n%{http_code}" -X DELETE -H "$AUTH" $BASE/api/bookmarks/$BM_ID)
  CODE=$(echo "$R" | tail -1)
  check "DELETE /api/bookmarks/:id → 200" 200 "$CODE"
fi

echo ""
echo "=== Export ==="
R=$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH" $BASE/api/export)
check "GET /api/export → 200" 200 "$R"

echo ""
echo "=== Document cleanup ==="
if [ -n "$DOC_ID" ]; then
  R=$(curl -s -w "\n%{http_code}" -X DELETE -H "$AUTH" $BASE/api/documents/$DOC_ID)
  CODE=$(echo "$R" | tail -1)
  check "DELETE /api/documents/:id → 200" 200 "$CODE"
fi

echo ""
echo "=============================="
echo "PASSED: $PASS  FAILED: $FAIL"
echo "=============================="
