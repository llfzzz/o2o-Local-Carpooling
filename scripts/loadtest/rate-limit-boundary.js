// Verifies the gateway's fixed-window rate limits are enforced exactly at their configured
// boundaries: 20 req/60s per client IP on /api/auth/**, 120 req/60s per authenticated user on
// other /api/**  (backend/gateway-service/src/main/resources/application.yml). Single VU, single
// iteration, ~150 fast sequential requests total for the whole run — this is a boundary probe, NOT
// sustained concurrent load.
//
// The limiter (backend/common's FixedWindowRateLimiter) uses wall-clock-epoch-aligned fixed
// windows, not a sliding window or token bucket, so a burst that straddles two windows would give
// a nondeterministic pass/fail count. This script waits for a fresh window before each burst.
//
// NEVER run this against the woxiangchuanaj.top production host — see guardNotProd() in lib/api.js.
// Requires a demo-profile target for the rider login helper (see docs/demo-mode.md); the rate
// limiter itself is not demo-gated, but this script's login() call is.
// Portability note: the in-memory rate limiter is single-instance-consistent only
// (RATE_LIMIT_BACKEND=memory, the default). If your target runs multiple gateway replicas behind a
// load balancer, this script's boundary assumptions only hold if RATE_LIMIT_BACKEND=redis there too.
//
// Usage:
//   TARGET_BASE_URL=http://localhost:8120 I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes \
//     k6 run scripts/loadtest/rate-limit-boundary.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { login, authHeaders, randomPhone, guardNotProd } from './lib/api.js';

const boundaryCorrect = new Rate('rate_limit_boundary_correct');

export const options = {
  scenarios: {
    rate_limit_boundary: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
    },
  },
  thresholds: {
    rate_limit_boundary_correct: ['rate>0.99'],
  },
};

export function setup() {
  const base = guardNotProd();
  return { base };
}

// Sleeps past the current fixed window's boundary if we're within `marginSeconds` of a rollover,
// so a burst can never straddle two windows.
function alignToFreshWindow(windowSeconds, marginSeconds = 10) {
  const nowSec = Date.now() / 1000;
  const intoWindow = nowSec % windowSeconds;
  const remaining = windowSeconds - intoWindow;
  if (remaining < marginSeconds) {
    sleep(remaining + 0.5);
  }
}

function authBoundaryCheck(base) {
  alignToFreshWindow(60);
  const statuses = [];
  for (let i = 0; i < 25; i++) {
    const phone = `${randomPhone('137')}${i}`;
    const res = http.post(`${base}/api/auth/sms-code`, JSON.stringify({ phone }), { headers: { 'Content-Type': 'application/json' } });
    statuses.push(res.status);
  }
  const firstTwenty = statuses.slice(0, 20);
  const rest = statuses.slice(20);
  const ok = firstTwenty.every((s) => s !== 429) && rest.length > 0 && rest.every((s) => s === 429);
  check(ok, { 'auth rate limit: first 20 allowed, 21st+ get 429': (v) => v === true });
  if (!ok) {
    console.warn(`auth boundary statuses: ${JSON.stringify(statuses)}`);
  }
  boundaryCorrect.add(ok);
}

function apiBoundaryCheck(base) {
  const rider = login(base, randomPhone('136'));
  if (!rider.token) {
    check(false, { 'api rate limit: rider login succeeded (prerequisite)': (v) => v === true });
    boundaryCorrect.add(false);
    return;
  }
  alignToFreshWindow(60);
  const auth = authHeaders(rider.token);
  const statuses = [];
  for (let i = 0; i < 125; i++) {
    const res = http.get(`${base}/api/orders`, auth);
    statuses.push(res.status);
  }
  const first120 = statuses.slice(0, 120);
  const rest = statuses.slice(120);
  const ok = first120.every((s) => s !== 429) && rest.length > 0 && rest.every((s) => s === 429);
  check(ok, { 'api rate limit: first 120 allowed, 121st+ get 429': (v) => v === true });
  if (!ok) {
    console.warn(`api boundary statuses: ${JSON.stringify(statuses)}`);
  }
  boundaryCorrect.add(ok);
}

export default function (data) {
  const { base } = data;
  authBoundaryCheck(base);
  apiBoundaryCheck(base);
}
