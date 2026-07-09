// Shared helpers for k6 scripts against the o2o-Local-Carpooling gateway API.
// Mirrors scripts/demo-smoke.sh's login() shape and flow so both stay in sync by construction.
import http from 'k6/http';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Hostnames this must never run against, checked by substring match against TARGET_BASE_URL.
const KNOWN_PROD_HOSTS = ['woxiangchuanaj.top'];

// Call from setup() in every script. Throws (aborting the run before any request is sent) unless
// TARGET_BASE_URL is set, doesn't match a known-production host, and the operator has explicitly
// confirmed the target isn't production. Belt and suspenders against an accidental prod run.
export function guardNotProd() {
  const target = __ENV.TARGET_BASE_URL || '';
  const confirm = __ENV.I_UNDERSTAND_THIS_IS_NOT_FOR_PROD || '';
  if (!target) {
    throw new Error('TARGET_BASE_URL is required, e.g. TARGET_BASE_URL=http://localhost:8120');
  }
  if (KNOWN_PROD_HOSTS.some((h) => target.includes(h))) {
    throw new Error(
      `REFUSING: TARGET_BASE_URL ("${target}") matches a known production host. ` +
      `Never run this against ${KNOWN_PROD_HOSTS.join(', ')}.`
    );
  }
  if (confirm !== 'yes') {
    throw new Error('REFUSING: set I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes to confirm this target is not production.');
  }
  return target;
}

// $phone -> { token, userId, roles, raw }. Same SMS-code-via-demo-inbox flow as demo-smoke.sh's
// login(): only works against a demo-profile target (DemoEndpoints 404s the demo-inbox route
// otherwise — see docs/demo-mode.md).
export function login(base, phone) {
  http.post(`${base}/api/auth/sms-code`, JSON.stringify({ phone }), { headers: JSON_HEADERS });
  const inboxRes = http.get(`${base}/api/auth/sms-code/demo-inbox?phone=${phone}`);
  const code = inboxRes.json('code');
  const loginRes = http.post(`${base}/api/auth/login`, JSON.stringify({ phone, code }), { headers: JSON_HEADERS });
  const body = loginRes.json() || {};
  return {
    token: body.accessToken,
    userId: body.user && body.user.userId,
    roles: body.user && body.user.roles,
    raw: loginRes,
  };
}

// Demo-only operator+admin bootstrap (POST /api/auth/demo/operator-session) — 404s outside the
// demo profile, same double-gate as everything else under docs/demo-mode.md.
export function operatorSession(base) {
  const res = http.post(`${base}/api/auth/demo/operator-session`, '{}', { headers: JSON_HEADERS });
  const body = res.json() || {};
  return {
    token: body.accessToken,
    userId: body.user && body.user.userId,
    roles: body.user && body.user.roles,
    raw: res,
  };
}

export function authHeaders(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}

// Unique-enough synthetic phone number per VU+iteration+call, independent of clock resolution
// (VU/ITER are prefixed ahead of the timestamp so same-millisecond collisions across VUs can't
// happen). Format is deliberately not a "real" phone shape — this system doesn't validate one
// (scripts/demo-smoke.sh and scripts/check-deployment.sh both already rely on that).
export function randomPhone(prefix = '138') {
  return `${prefix}${__VU}${__ITER}${Date.now()}`;
}

export function nowPlusHoursIso(hours) {
  return new Date(Date.now() + hours * 3600 * 1000).toISOString().replace(/\.\d+Z$/, 'Z');
}
