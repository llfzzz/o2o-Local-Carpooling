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
#   2b. the structured map runtime contract exists and reverse geocoding works — this detects an
#      old map-service jar, a missing Web Service key, or an invalid real-provider configuration;
#   3. unmapped paths answer 404 NOT_FOUND (not 500 INTERNAL_ERROR) — pins the common
#      GlobalApiExceptionHandler fix;
#   4. a latency snapshot of the interactive endpoints, called twice: a large cold->warm gap
#      means service processes are being paged out (host memory pressure), not a code problem;
#   5-6. the S33 gateway hardening (internal-only 404s + driver-review RBAC) is still correctly
#      enforced — added 2026-07-09 alongside an order-service/payment-sim-service redeploy;
#   7. an INFO-only baseline of the two documented, deliberately-deferred security gaps
#      (docs/security.md Known gaps) — records current behavior, does not fail the run.
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
info(){ echo "  INFO: $1"; }

# $1=phone -> echoes "TOKEN|USERID|ROLES" (same shape as demo-smoke.sh's login())
login() {
  local phone=$1
  # challenge-bound demo peek (the code no longer passes through the notification inbox)
  local challenge; challenge=$(CURL -X POST "$BASE/api/auth/sms-code" -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\"}" | j "['challengeId']")
  local code; code=$(CURL -X POST "$BASE/api/auth/sms-code/demo-peek" -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\",\"challengeId\":\"$challenge\"}" | j "['code']")
  local resp; resp=$(CURL -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\",\"code\":\"$code\"}")
  echo "$(echo "$resp" | j "['accessToken']")|$(echo "$resp" | j "['user']['userId']")|$(echo "$resp" | j "['user']['roles']")"
}

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

echo "===== 2b. MAP RUNTIME CONTRACT (old jar / missing key / provider drift) ====="
MAP_CITIES_RESPONSE=$(CURL -w $'\n%{http_code}' -H "Authorization: Bearer $OTOK" "$BASE/api/maps/cities")
MAP_CITIES_STATUS=${MAP_CITIES_RESPONSE##*$'\n'}
MAP_CITIES_BODY=${MAP_CITIES_RESPONSE%$'\n'*}
if [ "$MAP_CITIES_STATUS" = "200" ]; then
  ok "GET /api/maps/cities -> 200"
else
  bad "GET /api/maps/cities -> $MAP_CITIES_STATUS (404 = stale map-service jar)"
fi

MAP_DEMO_PROVIDER=$(echo "$MAP_CITIES_BODY" | j "['demoProvider']")
case "${EXPECT_REAL_MAP_PROVIDER:-false}" in
  true|TRUE|1)
    if [ "$MAP_DEMO_PROVIDER" = "False" ]; then
      ok "active map provider is real (demoProvider=false)"
    else
      bad "active map provider is not real (demoProvider=$MAP_DEMO_PROVIDER)"
    fi
    ;;
  *)
    info "map provider mode: demoProvider=$MAP_DEMO_PROVIDER (set EXPECT_REAL_MAP_PROVIDER=true to require AMap)"
    ;;
esac

MAP_PROBE_LAT="${MAP_PROBE_LAT:-24.4879}"
MAP_PROBE_LNG="${MAP_PROBE_LNG:-118.1781}"
MAP_REVERSE_RESPONSE=$(CURL -w $'\n%{http_code}' -X POST \
  -H "Authorization: Bearer $OTOK" \
  -H 'Content-Type: application/json' \
  -d "{\"lat\":$MAP_PROBE_LAT,\"lng\":$MAP_PROBE_LNG,\"datum\":\"WGS84\"}" \
  "$BASE/api/maps/reverse-geocode")
MAP_REVERSE_STATUS=${MAP_REVERSE_RESPONSE##*$'\n'}
MAP_REVERSE_BODY=${MAP_REVERSE_RESPONSE%$'\n'*}
if [ "$MAP_REVERSE_STATUS" = "200" ]; then
  ok "POST /api/maps/reverse-geocode -> 200"
else
  MAP_REVERSE_ERROR=$(echo "$MAP_REVERSE_BODY" | j "['errorCode']")
  bad "POST /api/maps/reverse-geocode -> $MAP_REVERSE_STATUS ${MAP_REVERSE_ERROR:-UNKNOWN_ERROR}"
fi

echo "===== 3. UNMAPPED PATH MAPS TO 404 (not 500) ====="
code=$(CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $OTOK" "$BASE/api/payments/route-that-does-not-exist")
if [ "$code" = "404" ]; then ok "unmapped path -> 404"; else bad "unmapped path -> $code (500 = backend predates the NOT_FOUND handler fix)"; fi

echo "===== 4. LATENCY SNAPSHOT (cold vs warm; big gap = host paging) ====="
for path in "/api/trips/search?originLat=24.4879&originLng=118.1781&destinationLat=24.5751&destinationLng=118.0972&datum=GCJ02" "/api/orders"; do
  t1=$(CURL -o /dev/null -w '%{time_total}' -H "Authorization: Bearer $OTOK" "$BASE$path")
  t2=$(CURL -o /dev/null -w '%{time_total}' -H "Authorization: Bearer $OTOK" "$BASE$path")
  echo "  $path  cold=${t1}s warm=${t2}s"
done

echo "===== 5. S33 INTERNAL-ONLY PATHS (expect 404 — gateway blocks before routing) ====="
declare -A INTERNAL_ONLY_PATHS=(
  ["POST /api/users"]=""
  ["GET /api/users/probe-id"]=""
  ["POST /api/orders/probe-id/pay"]=""
  ["POST /api/orders/probe-id/timeout"]=""
  ["POST /api/trips/probe-id/seat-locks"]=""
  ["POST /api/payments/simulations"]=""
  ["POST /api/payments/simulate-success"]=""
)
for spec in "${!INTERNAL_ONLY_PATHS[@]}"; do
  method="${spec%% *}"; path="${spec#* }"
  code=$(CURL -o /dev/null -w '%{http_code}' -X "$method" -H 'Content-Type: application/json' -d '{}' "$BASE$path")
  if [ "$code" = "404" ]; then ok "$spec -> 404"; else bad "$spec -> $code (expected 404 — internal-only path externally reachable)"; fi
done

echo "===== 6. DRIVER-REVIEW RBAC (S33; operator 200 / rider 403) ====="
RP="${RP:-138$(date +%s | tail -c 9)}"
R=$(login "$RP"); RTOK=${R%%|*}
[ -n "$RTOK" ] && ok "rider token minted for RBAC checks" || bad "rider login (resp: $R)"
code=$(CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $OTOK" "$BASE/api/drivers/verification-cases")
[ "$code" = "200" ] && ok "GET verification-cases as operator -> 200" || bad "GET verification-cases as operator -> $code (expected 200)"
code=$(CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $RTOK" "$BASE/api/drivers/verification-cases")
[ "$code" = "403" ] && ok "GET verification-cases as rider -> 403" || bad "GET verification-cases as rider -> $code (expected 403)"
code=$(CURL -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d '{}' "$BASE/api/drivers/verification-cases/probe-id/approve")
[ "$code" = "403" ] && ok "POST approve as rider -> 403" || bad "POST approve as rider -> $code (expected 403)"

echo "===== 7. TRIP PUBLISH IDENTITY BINDING (S37 regression — this one FAILS the run) ====="
# Was a documented deferred gap; closed in S37. A rider with no driver capability must be refused,
# and the body driverId must never decide who a trip belongs to.
RP2="${RP2:-139$(date +%s | tail -c 9)}"   # different prefix than RP so the two never collide
RB=$(login "$RP2"); RTOK_B=${RB%%|*}
if [ -n "$RTOK" ] && [ -n "$RTOK_B" ]; then
  RID=${R#*|}; RID=${RID%%|*}
  DEP=$(python3 -c "import datetime; print((datetime.datetime.now(datetime.UTC)+datetime.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
  SPOOF=$(CURL -X POST "$BASE/api/trips" -H "Authorization: Bearer $RTOK_B" -H 'Content-Type: application/json' \
    -d "{\"driverId\":\"$RID\",\"originText\":\"probe2\",\"destinationText\":\"probe2\",\"city\":\"probe\",\"departureAt\":\"$DEP\",\"totalSeats\":1}")
  SPOOF_DRIVER=$(echo "$SPOOF" | j "['driverId']")
  if echo "$SPOOF" | grep -q DRIVER_NOT_APPROVED; then
    ok "publish refused for a rider without driver capability (DRIVER_NOT_APPROVED)"
  elif [ "$SPOOF_DRIVER" = "$RID" ]; then
    bad "REGRESSION: trip publish trusted body driverId — riderB published as riderA ($RID)"
  else
    bad "publish should have been refused with DRIVER_NOT_APPROVED (resp head: $(echo "$SPOOF" | head -c 200))"
  fi
else
  info "publish binding check skipped: could not mint both rider tokens"
fi

echo "===== 7b. KNOWN-GAP BASELINE (INFO only — docs/security.md deferred items, does not fail the run) ====="
# Read-side IDOR probing needs a real trip+order, which now requires a driver-capable account.
# This script does not provision one (scripts/demo-smoke.sh does); probe only if a trip id is given.
if [ -n "${PROBE_TRIP_ID:-}" ] && [ -n "${PROBE_ORDER_ID:-}" ] && [ -n "$RTOK_B" ]; then
  ordercode=$(CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $RTOK_B" "$BASE/api/orders/$PROBE_ORDER_ID")
  if [ "$ordercode" = "200" ]; then info "read-side IDOR still present: other rider got 200 on order $PROBE_ORDER_ID (documented, deferred)"; else info "GET /api/orders/{id} cross-user -> $ordercode (was documented as 200/deferred — behavior may have changed)"; fi
  tripcode=$(CURL -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $RTOK_B" "$BASE/api/trips/$PROBE_TRIP_ID")
  if [ "$tripcode" = "200" ]; then info "read-side IDOR still present: other rider got 200 on trip $PROBE_TRIP_ID (documented, deferred)"; else info "GET /api/trips/{id} cross-user -> $tripcode (was documented as 200/deferred — behavior may have changed)"; fi
else
  info "read-side IDOR baseline skipped: set PROBE_TRIP_ID and PROBE_ORDER_ID (from a demo-smoke run) to probe it"
fi

echo
if [ "$FAILS" -eq 0 ]; then
  echo "ALL CHECKS PASSED"
else
  echo "$FAILS CHECK(S) FAILED — if section 2/3 failed, redeploy backend jars built from the same commit as the frontend."
  exit 1
fi
