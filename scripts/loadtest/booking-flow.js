// Models scripts/demo-smoke.sh's full booking/payment/identity/review/cancel flow as a k6 load test.
//
// NEVER run this against the woxiangchuanaj.top production host — see guardNotProd() in lib/api.js,
// which refuses to run unless TARGET_BASE_URL is set, doesn't match a known-prod host, and
// I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes is explicitly passed. This project's production host is a
// 3.4GiB/2-vCPU box already running at the edge of its memory budget (see docs/load-testing.md) —
// point this at a local Docker Compose stack, staging, or CI instead.
//
// Requires a demo-profile target (app.demo-mode=true): the SMS demo-inbox, operator-session, and
// demo-control endpoints this script depends on 404 outside that profile (docs/demo-mode.md).
//
// Usage:
//   TARGET_BASE_URL=http://localhost:8120 I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes \
//     k6 run -e VUS=5 -e DURATION=1m scripts/loadtest/booking-flow.js
//
// Env vars (all optional except TARGET_BASE_URL / I_UNDERSTAND_THIS_IS_NOT_FOR_PROD):
//   VUS            virtual users (default 5 — deliberately conservative starting point; this is a
//                   demo-scale system: Hikari pool=2/service, 96MB JVM heaps when run under the
//                   systemd lowmem profile — raise gradually against a target sized for it)
//   DURATION       run duration (default 1m)
//   ACCOUNT_SEED   salt mixed into trip origin/destination text, default 'lt'
//
// Thresholds below are generous starting points for an unknown target (laptop/CI/staging), not
// tuned to any specific host's capacity — tighten them once you know what "healthy" looks like
// for your target.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { login, operatorSession, authHeaders, randomPhone, nowPlusHoursIso, guardNotProd } from './lib/api.js';

const flowCompleted = new Rate('booking_flow_completed');

export const options = {
  scenarios: {
    booking_flow: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
    booking_flow_completed: ['rate>0.95'],
  },
};

export function setup() {
  const base = guardNotProd();
  const operator = operatorSession(base);
  check(operator.raw, { 'operator session minted': () => !!operator.token });
  if (!operator.token) {
    throw new Error(`Could not mint operator session against ${base} — is this a demo-profile target? resp: ${operator.raw.body}`);
  }
  return { base, operatorToken: operator.token };
}

export default function (data) {
  const { base, operatorToken } = data;
  const seed = __ENV.ACCOUNT_SEED || 'lt';
  const operatorAuth = authHeaders(operatorToken);

  // 1. rider login (SMS via demo inbox)
  const rider = login(base, randomPhone('138'));
  const riderOk = check(rider.raw, { 'rider login ok': () => !!rider.token });
  if (!riderOk) { flowCompleted.add(false); return; }
  const riderAuth = authHeaders(rider.token);

  // 2. publish trip (rider acts as driver, same pattern as demo-smoke.sh)
  const dep = nowPlusHoursIso(1);
  const tripRes = http.post(`${base}/api/trips`, JSON.stringify({
    driverId: rider.userId,
    originText: `probe-origin-${seed}`,
    destinationText: `probe-dest-${seed}`,
    city: 'probe',
    departureAt: dep,
    totalSeats: 3,
  }), riderAuth);
  const trip = tripRes.json() || {};
  const tripOk = check(tripRes, { 'trip published': () => tripRes.status === 200 && !!trip.tripId });
  if (!tripOk) { flowCompleted.add(false); return; }

  // 3. search
  const searchRes = http.get(
    `${base}/api/trips?origin=${encodeURIComponent('probe-origin-' + seed)}&destination=${encodeURIComponent('probe-dest-' + seed)}`,
    riderAuth
  );
  check(searchRes, { 'search returned results': () => searchRes.status === 200 && Array.isArray(searchRes.json()) && searchRes.json().length >= 1 });

  // 4. book seat
  const orderRes = http.post(`${base}/api/orders`, JSON.stringify({
    tripId: trip.tripId, seats: 1, idempotencyKey: `lt-${rider.userId}-${Date.now()}`,
  }), riderAuth);
  const order = orderRes.json() || {};
  const orderOk = check(orderRes, { 'order PENDING_PAYMENT': () => order.status === 'PENDING_PAYMENT' });
  if (!orderOk) { flowCompleted.add(false); return; }

  // 5. create payment intent
  const intentRes = http.post(`${base}/api/payments/intents`, JSON.stringify({
    orderId: order.orderId, idempotencyKey: `pi-${order.orderId}`,
  }), riderAuth);
  const intent = intentRes.json() || {};
  const intentOk = check(intentRes, { 'intent REQUIRES_PAYMENT': () => intent.status === 'REQUIRES_PAYMENT' });
  if (!intentOk) { flowCompleted.add(false); return; }

  // 6. operator drives signed payment success (goes through the real HMAC-signed callback
  //    pipeline in payment-sim-service, not a shortcut — see docs/architecture.md)
  const cbRes = http.post(`${base}/api/demo/control/payment/${intent.intentId}/callbacks`, JSON.stringify({
    outcome: 'SUCCEEDED', mode: 'NORMAL',
  }), operatorAuth);
  check(cbRes, { 'payment callback SUCCEEDED': () => cbRes.status === 200 && cbRes.json('finalStatus') === 'SUCCEEDED' });

  sleep(1); // let the internal markPaid Feign call land, same beat demo-smoke.sh gives it

  // 7. verify SEAT_LOCKED
  const orderAfterPay = http.get(`${base}/api/orders/${order.orderId}`, riderAuth);
  const seatLocked = check(orderAfterPay, { 'order SEAT_LOCKED': () => orderAfterPay.json('status') === 'SEAT_LOCKED' });

  // 8. identity verification (rider starts, operator drives liveness then session)
  const idRes = http.post(`${base}/api/identity/verifications`, JSON.stringify({
    realName: 'k6 probe', idNumber: '350211199001011234',
  }), riderAuth);
  const identity = idRes.json() || {};
  const idStarted = check(idRes, { 'identity session PENDING': () => identity.status === 'PENDING' });
  let identityApproved = false;
  if (identity.verificationId) {
    http.post(`${base}/api/demo/control/identity/${identity.verificationId}/liveness`, JSON.stringify({ outcome: 'PASSED' }), operatorAuth);
    const sessRes = http.post(`${base}/api/demo/control/identity/${identity.verificationId}/session`, JSON.stringify({ outcome: 'APPROVED' }), operatorAuth);
    identityApproved = check(sessRes, { 'identity APPROVED': () => sessRes.json('status') === 'APPROVED' });
  }

  // 9. complete order (operator)
  const completeRes = http.post(`${base}/api/orders/${order.orderId}/complete`, null, operatorAuth);
  const completeOk = check(completeRes, { 'order COMPLETED': () => completeRes.json('status') === 'COMPLETED' });

  // 10. review + duplicate-review rejection
  const reviewRes = http.post(`${base}/api/orders/${order.orderId}/review`, JSON.stringify({ rating: 5, comment: 'k6 probe' }), riderAuth);
  check(reviewRes, { 'review submitted': () => reviewRes.json('rating') === 5 });
  const dupRes = http.post(`${base}/api/orders/${order.orderId}/review`, JSON.stringify({ rating: 1, comment: 'dup' }), riderAuth);
  check(dupRes, { 'duplicate review rejected 409': () => dupRes.status === 409 });

  // 11. second order -> cancel path
  const order2Res = http.post(`${base}/api/orders`, JSON.stringify({
    tripId: trip.tripId, seats: 1, idempotencyKey: `lt2-${rider.userId}-${Date.now()}`,
  }), riderAuth);
  const order2 = order2Res.json() || {};
  if (order2.orderId) {
    const cancelRes = http.post(`${base}/api/orders/${order2.orderId}/cancel`, null, riderAuth);
    check(cancelRes, { 'order2 USER_CANCELLED': () => cancelRes.json('status') === 'USER_CANCELLED' });
  }

  // 12. negative authz: rider cannot hit operator demo control
  const negRes = http.post(`${base}/api/demo/control/payment/${intent.intentId}/callbacks`, JSON.stringify({
    outcome: 'FAILED', mode: 'NORMAL',
  }), riderAuth);
  check(negRes, { 'rider blocked from demo control 403': () => negRes.status === 403 });

  flowCompleted.add(riderOk && tripOk && orderOk && intentOk && seatLocked && idStarted && identityApproved && completeOk);
  sleep(1);
}
