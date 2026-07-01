#!/usr/bin/env bash
# Full-stack end-to-end smoke through the Gateway (127.0.0.1:8080), demo profile.
# Prereqs: docker compose middleware healthy + all 14 services running (scripts/start-services.sh).
# NOTE: operator role is currently obtained via a DB workaround (no operator-provisioning flow yet;
# see AGENTS.md "operator 开通仍是缺口"). S26 should add a proper demo operator seed.
import sys,json
try:
    print(json.load(sys.stdin)$1)
except Exception:
    print('')"; }
ok(){ echo "  PASS: $1"; }
bad(){ echo "  FAIL: $1"; FAILS=$((FAILS+1)); }

login() { # $1=phone -> echoes "TOKEN|USERID|ROLES"
  local phone=$1
  curl -s -X POST $GW/api/auth/sms-code -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\"}" >/dev/null
  local code; code=$(curl -s "$GW/api/auth/sms-code/demo-inbox?phone=$phone" | j "['code']")
  local resp; resp=$(curl -s -X POST $GW/api/auth/login -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\",\"code\":\"$code\"}")
  echo "$(echo "$resp" | j "['accessToken']")|$(echo "$resp" | j "['user']['userId']")|$(echo "$resp" | j "['user']['roles']")"
}

echo "===== 1. RIDER LOGIN (SMS via demo inbox) ====="
RP=13800138000
R=$(login $RP); RTOK=${R%%|*}; rest=${R#*|}; RID=${rest%%|*}; RROLES=${rest#*|}
[ -n "$RTOK" ] && ok "rider token ($RID roles=$RROLES)" || bad "rider login (resp: $R)"

echo "===== 2. OPERATOR SESSION (demo seed endpoint, S26) ====="
OPRESP=$(curl -s -X POST $GW/api/auth/demo/operator-session -H 'Content-Type: application/json' -d '{}')
OTOK=$(echo "$OPRESP" | j "['accessToken']"); OID=$(echo "$OPRESP" | j "['user']['userId']"); OROLES=$(echo "$OPRESP" | j "['user']['roles']")
echo "  operator roles: $OROLES"
[ -n "$OTOK" ] && echo "$OROLES" | grep -q OPERATOR && ok "operator session ($OID)" || bad "operator session (resp: $OPRESP)"

echo "===== 3. PUBLISH TRIP (rider as driver) ====="
DEP=$(python3 -c "import datetime; print((datetime.datetime.now(datetime.UTC)+datetime.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
TRIP=$(curl -s -X POST $GW/api/trips -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"driverId\":\"$RID\",\"originText\":\"软件园三期\",\"destinationText\":\"集美大学\",\"city\":\"厦门\",\"departureAt\":\"$DEP\",\"totalSeats\":3}")
TID=$(echo "$TRIP" | j "['tripId']"); PTRACE=$(echo "$TRIP" | j "['route']['providerTrace']")
[ -n "$TID" ] && ok "trip $TID (route=$PTRACE)" || bad "publish trip (resp: $TRIP)"

echo "===== 4. SEARCH TRIPS ====="
SEARCH=$(curl -s "$GW/api/trips?origin=%E8%BD%AF%E4%BB%B6%E5%9B%AD%E4%B8%89%E6%9C%9F&destination=%E9%9B%86%E7%BE%8E%E5%A4%A7%E5%AD%A6" -H "Authorization: Bearer $RTOK")
CNT=$(echo "$SEARCH" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
[ "${CNT:-0}" -ge 1 ] && ok "search returned $CNT trip(s)" || bad "search (resp head: $(echo "$SEARCH" | head -c 200))"

echo "===== 5. BOOK SEAT ====="
ORD=$(curl -s -X POST $GW/api/orders -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"tripId\":\"$TID\",\"seats\":1,\"idempotencyKey\":\"smoke-$(date +%s)\"}")
OIDR=$(echo "$ORD" | j "['orderId']"); OSTAT=$(echo "$ORD" | j "['status']")
[ "$OSTAT" = "PENDING_PAYMENT" ] && ok "order $OIDR ($OSTAT)" || bad "book (resp: $ORD)"

echo "===== 6. CREATE PAYMENT INTENT ====="
PI=$(curl -s -X POST $GW/api/payments/intents -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"$OIDR\",\"idempotencyKey\":\"pi-$OIDR\"}")
IID=$(echo "$PI" | j "['intentId']"); ISTAT=$(echo "$PI" | j "['status']")
[ "$ISTAT" = "REQUIRES_PAYMENT" ] && ok "intent $IID ($ISTAT)" || bad "create intent (resp: $PI)"

echo "===== 7. OPERATOR DRIVES SIGNED PAYMENT SUCCESS ====="
CB=$(curl -s -X POST "$GW/api/demo/control/payment/$IID/callbacks" -H "Authorization: Bearer $OTOK" -H 'Content-Type: application/json' \
  -d "{\"outcome\":\"SUCCEEDED\",\"mode\":\"NORMAL\"}")
FSTAT=$(echo "$CB" | j "['finalStatus']")
[ "$FSTAT" = "SUCCEEDED" ] && ok "payment callback -> intent $FSTAT" || bad "payment callback (resp: $CB)"

echo "===== 8. VERIFY ORDER PAID (SEAT_LOCKED) ====="
sleep 1
O8=$(curl -s "$GW/api/orders/$OIDR" -H "Authorization: Bearer $RTOK" | j "['status']")
[ "$O8" = "SEAT_LOCKED" ] && ok "order now $O8" || bad "order status after pay: $O8"

echo "===== 9. IDENTITY VERIFICATION (rider start, operator drives) ====="
IV=$(curl -s -X POST $GW/api/identity/verifications -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"realName\":\"张三\",\"idNumber\":\"350211199001011234\"}")
VID=$(echo "$IV" | j "['verificationId']"); VSTAT=$(echo "$IV" | j "['status']")
[ "$VSTAT" = "PENDING" ] && ok "identity session $VID ($VSTAT)" || bad "start identity (resp: $IV)"
curl -s -X POST "$GW/api/demo/control/identity/$VID/liveness" -H "Authorization: Bearer $OTOK" -H 'Content-Type: application/json' -d '{"outcome":"PASSED"}' >/dev/null
IVS=$(curl -s -X POST "$GW/api/demo/control/identity/$VID/session" -H "Authorization: Bearer $OTOK" -H 'Content-Type: application/json' -d '{"outcome":"APPROVED"}' | j "['status']")
[ "$IVS" = "APPROVED" ] && ok "identity -> $IVS" || bad "identity approve: $IVS"
# result delivered to rider inbox (not inline)
INBOX=$(curl -s "$GW/api/demo/inbox" -H "Authorization: Bearer $RTOK")
echo "$INBOX" | grep -q IDENTITY_VERIFICATION_RESULT && ok "identity result delivered to inbox" || bad "identity inbox delivery"

echo "===== 10. COMPLETE ORDER (operator) + review invite ====="
CMP=$(curl -s -X POST "$GW/api/orders/$OIDR/complete" -H "Authorization: Bearer $OTOK" | j "['status']")
[ "$CMP" = "COMPLETED" ] && ok "order -> $CMP" || bad "complete: $CMP"
curl -s "$GW/api/demo/inbox" -H "Authorization: Bearer $RTOK" | grep -q ORDER_REVIEW_INVITATION && ok "review invitation in rider inbox" || bad "review invite delivery"

echo "===== 11. SUBMIT REVIEW (rider) ====="
RV=$(curl -s -X POST "$GW/api/orders/$OIDR/review" -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d '{"rating":5,"comment":"准时"}')
RATED=$(echo "$RV" | j "['rating']")
[ "$RATED" = "5" ] && ok "review submitted (rating $RATED)" || bad "review (resp: $RV)"
DUP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GW/api/orders/$OIDR/review" -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d '{"rating":1,"comment":"x"}')
[ "$DUP" = "409" ] && ok "duplicate review rejected (409)" || bad "dup review code: $DUP"

echo "===== 12. CANCEL PATH (new order -> user cancel, seats released) ====="
ORD2=$(curl -s -X POST $GW/api/orders -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' \
  -d "{\"tripId\":\"$TID\",\"seats\":1,\"idempotencyKey\":\"smoke2-$(date +%s)\"}")
O2ID=$(echo "$ORD2" | j "['orderId']")
CAN=$(curl -s -X POST "$GW/api/orders/$O2ID/cancel" -H "Authorization: Bearer $RTOK" | j "['status']")
[ "$CAN" = "USER_CANCELLED" ] && ok "order2 $O2ID -> $CAN" || bad "cancel: $CAN"

echo "===== 13. AUTHZ NEGATIVE: rider cannot hit operator demo control ====="
NEG=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GW/api/demo/control/payment/$IID/callbacks" -H "Authorization: Bearer $RTOK" -H 'Content-Type: application/json' -d '{"outcome":"FAILED","mode":"NORMAL"}')
[ "$NEG" = "403" ] && ok "rider blocked from demo control (403)" || bad "rider->control expected 403 got $NEG"

echo ""
echo "================= SMOKE RESULT: FAILS=$FAILS ================="
