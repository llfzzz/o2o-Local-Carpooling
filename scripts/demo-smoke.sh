#!/usr/bin/env bash
# Full-stack end-to-end smoke through the Gateway (127.0.0.1:8120), demo profile.
# Prereqs: docker compose middleware healthy + all 14 services running (scripts/start-services.sh).
# Override GW to test through nginx, e.g. GW=https://woxiangchuanaj.top/o2o-api
set -u

GW="${GW:-http://127.0.0.1:8120}"
CURL_MAX_TIME="${CURL_MAX_TIME:-180}"
CURL_NO_PROXY="${CURL_NO_PROXY:-*}"
FAILS=0

j(){
  local expr="$1"
  python3 -c "import sys,json
try:
    data=json.load(sys.stdin)
    print(eval('data' + sys.argv[1]))
except Exception:
    print('')" "$expr"
}
ok(){ echo "  PASS: $1"; }
bad(){ echo "  FAIL: $1"; FAILS=$((FAILS+1)); }
curlq(){ curl --noproxy "$CURL_NO_PROXY" -sS --max-time "$CURL_MAX_TIME" "$@"; }

login() { # $1=phone -> echoes "TOKEN|USERID|ROLES"
  local phone=$1
  # The sms-code response returns a challengeId; the demo-peek endpoint requires it (the code
  # no longer passes through the notification inbox).
  local challenge; challenge=$(curlq -X POST $GW/api/auth/sms-code -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\"}" | j "['challengeId']")
  local code; code=$(curlq -X POST "$GW/api/auth/sms-code/demo-peek" -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\",\"challengeId\":\"$challenge\"}" | j "['code']")
  local resp; resp=$(curlq -X POST $GW/api/auth/login -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\",\"code\":\"$code\"}")
  echo "$(echo "$resp" | j "['accessToken']")|$(echo "$resp" | j "['user']['userId']")|$(echo "$resp" | j "['user']['roles']")"
}

echo "===== 1. RIDER LOGIN (SMS via demo inbox) ====="
RP="${RP:-138$(date +%s | tail -c 9)}"
echo "  rider phone: $RP"
R=$(login $RP); RTOK=${R%%|*}; rest=${R#*|}; RID=${rest%%|*}; RROLES=${rest#*|}
[ -n "$RTOK" ] && ok "rider token ($RID roles=$RROLES)" || bad "rider login (resp: $R)"

echo "===== 2. OPERATOR SESSION (demo seed endpoint, S26) ====="
OPRESP=$(curlq -X POST $GW/api/auth/demo/operator-session -H 'Content-Type: application/json' -d '{}')
OTOK=$(echo "$OPRESP" | j "['accessToken']"); OID=$(echo "$OPRESP" | j "['user']['userId']"); OROLES=$(echo "$OPRESP" | j "['user']['roles']")
echo "  operator roles: $OROLES"
[ -n "$OTOK" ] && echo "$OROLES" | grep -q OPERATOR && ok "operator session ($OID)" || bad "operator session (resp: $OPRESP)"

echo "===== 3. BECOME AN APPROVED DRIVER (identity + documents) ====="
# S37: publishing requires server-verified driver capability, so the account must earn it first.
# Publishing used to accept any body driverId from any logged-in user — that hole is closed.
IV=$(curlq -X POST $GW/api/identity/verifications -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"realName\":\"张三\",\"idNumber\":\"350211199001011234\"}")
VID=$(echo "$IV" | j "['verificationId']"); VSTAT=$(echo "$IV" | j "['status']")
[ "$VSTAT" = "PENDING" ] && ok "identity session $VID ($VSTAT)" || bad "start identity (resp: $IV)"
curlq -X POST "$GW/api/demo/control/identity/$VID/liveness" -H "Authorization: Bearer $OTOK" -H 'Content-Type: application/json' -d '{"outcome":"PASSED"}' >/dev/null
IVS=$(curlq -X POST "$GW/api/demo/control/identity/$VID/session" -H "Authorization: Bearer $OTOK" -H 'Content-Type: application/json' -d '{"outcome":"APPROVED"}' | j "['status']")
[ "$IVS" = "APPROVED" ] && ok "identity -> $IVS" || bad "identity approve: $IVS"
# result delivered to rider inbox (not inline)
INBOX=$(curlq "$GW/api/inbox" -H "Authorization: Bearer $RTOK")
echo "$INBOX" | grep -q IDENTITY_VERIFICATION_RESULT && ok "identity result delivered to inbox" || bad "identity inbox delivery"
# documents: self-service submit, then operator approval
CASE=$(curlq -X POST $GW/api/drivers/verification-cases -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d '{"drivingLicenseFileId":"file-driving-001","vehicleLicenseFileId":"file-vehicle-001"}')
CID=$(echo "$CASE" | j "['caseId']")
[ -n "$CID" ] && ok "driver case $CID submitted" || bad "submit driver case (resp: $CASE)"
CSTAT=$(curlq -X POST "$GW/api/drivers/verification-cases/$CID/approve" -H "Authorization: Bearer $OTOK" | j "['status']")
[ "$CSTAT" = "APPROVED" ] && ok "driver documents -> $CSTAT" || bad "approve driver case: $CSTAT"

echo "===== 3b. PUBLISH GATE REJECTS A NON-DRIVER ====="
# Negative case: a fresh rider with no driver capability must be refused.
NR=$(login "139$(date +%s | tail -c 9)"); NRTOK=${NR%%|*}
NRESP=$(curlq -X POST $GW/api/trips -H "Authorization: Bearer $NRTOK" -H 'Content-Type: application/json' \
  -d "{\"originText\":\"软件园三期\",\"destinationText\":\"集美大学\",\"city\":\"厦门\",\"departureAt\":\"2026-12-01T10:00:00Z\",\"totalSeats\":3}")
echo "$NRESP" | grep -q DRIVER_NOT_APPROVED && ok "non-driver publish rejected (DRIVER_NOT_APPROVED)" || bad "non-driver publish should be rejected (resp: $NRESP)"

echo "===== 3c. PUBLISH TRIP (approved driver; server binds driverId to the token) ====="
DEP=$(python3 -c "import datetime; print((datetime.datetime.now(datetime.UTC)+datetime.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
# Deliberately send a spoofed driverId: the server must ignore it and use the authenticated user.
TRIP=$(curlq -X POST $GW/api/trips -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"driverId\":\"user-someone-else\",\"originText\":\"软件园三期\",\"destinationText\":\"集美大学\",\"city\":\"厦门\",\"departureAt\":\"$DEP\",\"totalSeats\":3,\"idempotencyKey\":\"smoke-publish-$(date +%s)\"}")
TID=$(echo "$TRIP" | j "['tripId']"); PTRACE=$(echo "$TRIP" | j "['route']['providerTrace']")
TDRV=$(echo "$TRIP" | j "['driverId']")
[ -n "$TID" ] && ok "trip $TID (route=$PTRACE)" || bad "publish trip (resp: $TRIP)"
[ "$TDRV" = "$RID" ] && ok "driverId bound to authenticated principal (spoofed body value ignored)" || bad "driverId should be $RID but was $TDRV"

echo "===== 4. SEARCH TRIPS (geographic proximity, S39) ====="
# Coordinates of 软件园三期 / 集美大学 as the demo provider resolves them. Matching is by distance
# and departure window now, not by substring — searching near the origin must find the trip.
SEARCH=$(curlq "$GW/api/trips/search?originLat=24.4879&originLng=118.1781&destinationLat=24.5751&destinationLng=118.0972&datum=GCJ02" -H "Authorization: Bearer $RTOK")
CNT=$(echo "$SEARCH" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
[ "${CNT:-0}" -ge 1 ] && ok "geographic search returned $CNT trip(s)" || bad "search (resp head: $(echo "$SEARCH" | head -c 200))"

# A start ~1.2km away must still match (3km pickup radius) — the substring search never could.
NEAR=$(curlq "$GW/api/trips/search?originLat=24.4975&originLng=118.1800&destinationLat=24.5751&destinationLng=118.0972&datum=GCJ02" -H "Authorization: Bearer $RTOK")
NCNT=$(echo "$NEAR" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
[ "${NCNT:-0}" -ge 1 ] && ok "nearby origin (~1.2km away) still matches" || bad "proximity match failed (resp head: $(echo "$NEAR" | head -c 200))"

# A different city must NOT match — proves matching is geographic, not text.
FAR=$(curlq "$GW/api/trips/search?originLat=39.9847&originLng=116.3070&destinationLat=39.8654&destinationLng=116.3786&datum=GCJ02" -H "Authorization: Bearer $RTOK")
FCNT=$(echo "$FAR" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
[ "${FCNT:-0}" -eq 0 ] && ok "Beijing search correctly returns no Xiamen trips" || bad "cross-city leak: $FCNT trip(s)"

echo "===== 5. BOOK SEAT ====="
ORD=$(curlq -X POST $GW/api/orders -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"tripId\":\"$TID\",\"seats\":1,\"idempotencyKey\":\"smoke-$(date +%s)\"}")
OIDR=$(echo "$ORD" | j "['orderId']"); OSTAT=$(echo "$ORD" | j "['status']")
[ "$OSTAT" = "PENDING_PAYMENT" ] && ok "order $OIDR ($OSTAT)" || bad "book (resp: $ORD)"

echo "===== 6. CREATE PAYMENT INTENT ====="
PI=$(curlq -X POST $GW/api/payments/intents -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"$OIDR\",\"idempotencyKey\":\"pi-$OIDR\"}")
IID=$(echo "$PI" | j "['intentId']"); ISTAT=$(echo "$PI" | j "['status']")
[ "$ISTAT" = "REQUIRES_PAYMENT" ] && ok "intent $IID ($ISTAT)" || bad "create intent (resp: $PI)"

echo "===== 7. OPERATOR DRIVES SIGNED PAYMENT SUCCESS ====="
CB=$(curlq -X POST "$GW/api/demo/control/payment/$IID/callbacks" -H "Authorization: Bearer $OTOK" -H 'Content-Type: application/json' \
  -d "{\"outcome\":\"SUCCEEDED\",\"mode\":\"NORMAL\"}")
FSTAT=$(echo "$CB" | j "['finalStatus']")
[ "$FSTAT" = "SUCCEEDED" ] && ok "payment callback -> intent $FSTAT" || bad "payment callback (resp: $CB)"

echo "===== 8. VERIFY ORDER PAID (SEAT_LOCKED) ====="
sleep 1
O8=$(curlq "$GW/api/orders/$OIDR" -H "Authorization: Bearer $RTOK" | j "['status']")
[ "$O8" = "SEAT_LOCKED" ] && ok "order now $O8" || bad "order status after pay: $O8"

echo "===== 9. PUBLISH IDEMPOTENCY (retry must not duplicate) ====="
IKEY="smoke-idem-$(date +%s)"
DEP2=$(python3 -c "import datetime; print((datetime.datetime.now(datetime.UTC)+datetime.timedelta(hours=3)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
BODY2="{\"originText\":\"厦门北站\",\"destinationText\":\"中山路步行街\",\"city\":\"厦门\",\"departureAt\":\"$DEP2\",\"totalSeats\":2,\"idempotencyKey\":\"$IKEY\"}"
P1=$(curlq -X POST $GW/api/trips -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d "$BODY2" | j "['tripId']")
P2=$(curlq -X POST $GW/api/trips -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d "$BODY2" | j "['tripId']")
[ -n "$P1" ] && [ "$P1" = "$P2" ] && ok "repeated publish returned the same trip $P1" || bad "publish idempotency: $P1 vs $P2"

echo "===== 10. COMPLETE ORDER (operator) + review invite ====="
CMP=$(curlq -X POST "$GW/api/orders/$OIDR/complete" -H "Authorization: Bearer $OTOK" | j "['status']")
[ "$CMP" = "COMPLETED" ] && ok "order -> $CMP" || bad "complete: $CMP"
curlq "$GW/api/inbox" -H "Authorization: Bearer $RTOK" | grep -q ORDER_REVIEW_INVITATION && ok "review invitation in rider inbox" || bad "review invite delivery"

echo "===== 11. SUBMIT REVIEW (rider) ====="
RV=$(curlq -X POST "$GW/api/orders/$OIDR/review" -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d '{"rating":5,"comment":"准时"}')
RATED=$(echo "$RV" | j "['rating']")
[ "$RATED" = "5" ] && ok "review submitted (rating $RATED)" || bad "review (resp: $RV)"
DUP=$(curlq -o /dev/null -w "%{http_code}" -X POST "$GW/api/orders/$OIDR/review" -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d '{"rating":1,"comment":"x"}')
[ "$DUP" = "409" ] && ok "duplicate review rejected (409)" || bad "dup review code: $DUP"

echo "===== 12. CANCEL PATH (new order -> user cancel, seats released) ====="
ORD2=$(curlq -X POST $GW/api/orders -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"tripId\":\"$TID\",\"seats\":1,\"idempotencyKey\":\"smoke2-$(date +%s)\"}")
O2ID=$(echo "$ORD2" | j "['orderId']")
CAN=$(curlq -X POST "$GW/api/orders/$O2ID/cancel" -H "Authorization: Bearer $RTOK" | j "['status']")
[ "$CAN" = "USER_CANCELLED" ] && ok "order2 $O2ID -> $CAN" || bad "cancel: $CAN"

echo "===== 13. AUTHZ NEGATIVE: rider cannot hit operator demo control ====="
NEG=$(curlq -o /dev/null -w "%{http_code}" -X POST "$GW/api/demo/control/payment/$IID/callbacks" -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d '{"outcome":"FAILED","mode":"NORMAL"}')
[ "$NEG" = "403" ] && ok "rider blocked from demo control (403)" || bad "rider->control expected 403 got $NEG"

echo ""
echo "================= SMOKE RESULT: FAILS=$FAILS ================="
exit "$FAILS"
