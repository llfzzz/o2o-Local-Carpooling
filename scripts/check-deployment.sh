#!/usr/bin/env bash
# Deployment health & version-skew check for an o2o stack, runnable against local or live.
#
#   scripts/check-deployment.sh                                  # local gateway (127.0.0.1:8080)
#   scripts/check-deployment.sh https://woxiangchuanaj.top/o2o-api   # live station
#
# Verifies the things that broke on the live station on 2026-07-07:
#   1. demo operator session can be minted (auth + demo seed gates);
#   2. the S29 admin listing endpoints exist and answer 200 — a 404/500 here means the deployed
#      backend jars are OLDER than the deployed admin-console frontend (version skew): rebuild
#      and restart the backend from the same commit as the frontend;
#   3. unmapped paths answer 404 NOT_FOUND (not 500 INTERNAL_ERROR) — pins the common
#      GlobalApiExceptionHandler fix;
#   4. a latency snapshot of the interactive endpoints, called twice: a large cold->warm gap
#      means service processes are being paged out (host memory pressure), not a code problem.
set -u
BASE="${1:-http://127.0.0.1:8080}"
FAILS=0
# Function (not a variable) so the --noproxy glob never word-splits/expands.
CURL() { curl -s --noproxy '*' --max-time 30 "$@"; }

j() { python3 -c "
import sys,json
try:
    print(json.load(sys.stdin)$1)
except Exception:
    print('')"; }
ok(){ echo "  PASS: $1"; }
bad(){ echo "  FAIL: $1"; FAILS=$((FAILS+1)); }

echo "===== 1. OPERATOR SESSION (demo seed endpoint) ====="
OPRESP=$(CURL -X POST "$BASE/api/auth/demo/operator-session" -H 'Content-Type: application/json' -d '{}')
OTOK=$(echo "$OPRESP" | j "['accessToken']")
[ -n "$OTOK" ] && ok "operator session minted" || bad "operator session (resp: $OPRESP)"

echo "===== 2. ADMIN LISTING ENDPOINTS (S29+; 404/500 = stale backend jars) ====="
for path in \
  "/api/demo/control/payment/intents?limit=1" \
  "/api/demo/control/identity/verifications?limit=1" \
  "/api/demo/control/notification/deliveries?limit=1" \
  "/api/ai/ocr/tasks?limit=1" \
  "/api/admin/dashboard" \
  "/api/orders/admin"; do
  code=$(CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $OTOK" "$BASE$path")
  if [ "$code" = "200" ]; then ok "$path -> 200"; else bad "$path -> $code (expected 200)"; fi
done

echo "===== 3. UNMAPPED PATH MAPS TO 404 (not 500) ====="
code=$(CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $OTOK" "$BASE/api/payments/route-that-does-not-exist")
if [ "$code" = "404" ]; then ok "unmapped path -> 404"; else bad "unmapped path -> $code (500 = backend predates the NOT_FOUND handler fix)"; fi

echo "===== 4. LATENCY SNAPSHOT (cold vs warm; big gap = host paging) ====="
for path in "/api/trips?origin=probe&destination=probe" "/api/orders"; do
  t1=$(CURL -o /dev/null -w '%{time_total}' -H "Authorization: Bearer $OTOK" "$BASE$path")
  t2=$(CURL -o /dev/null -w '%{time_total}' -H "Authorization: Bearer $OTOK" "$BASE$path")
  echo "  $path  cold=${t1}s warm=${t2}s"
done

echo
if [ "$FAILS" -eq 0 ]; then
  echo "ALL CHECKS PASSED"
else
  echo "$FAILS CHECK(S) FAILED — if section 2/3 failed, redeploy backend jars built from the same commit as the frontend."
  exit 1
fi
