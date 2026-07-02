# Security Baseline

The controls below hold in every phase and must not regress. "Demo" affordances are additive and
gated — they never weaken these.

## Secrets

- No real or usable secret/token/password literal may live in the repo. All sensitive config comes
  from environment variables.
- `SecretsValidator` (staging/production only) refuses to start if a sensitive property
  (`security.jwt.base64-secret`, `security.field-encryption-key-base64`,
  `providers.payment.webhook-secret`) is a known demo value (compared by **SHA-256 hash**, never a
  literal) or an obvious placeholder (`replace-with-*`, `changeme`, …). New sensitive keys must be
  added to `SecretsValidator.SECRET_KEYS`.
- `scripts/generate-local-env.sh` writes a mode-600, gitignored `.env` with fresh random secrets.

## Demo fencing (double-gate)

- Every demo-only endpoint/switch/dataset is fenced by `app.demo-mode` **and** a specific flag
  (`inbox-enabled` / `control-enabled` / `seed-enabled`).
- `DemoModeGuard` (startup) makes it impossible for any demo flag to be true outside the demo
  profile. `DemoEndpoints` (per-request) returns **404** when the flag is off, so demo endpoints are
  indistinguishable from non-existent ones in staging/production.

## Authentication & authorization

- SMS login codes are generated with `SecureRandom`, stored **hashed** (single-use, TTL),
  constant-time compared, per-phone issuance rate-limited, and locked out after repeated failures.
  The plaintext is delivered out-of-band (Demo Inbox) and never returned by the API.
- Roles are **server-authoritative** — resolved from the persisted user record, never from the
  client. `LoginRequest` carries only `phone` + `code` (a regression test guards this).
- Short-lived access token (30 min) + rotating refresh token with reuse detection and family
  revocation.
- All external business requests go through the Gateway. Protected `/api/**` requires a valid Bearer
  token; `/api/admin/**`, `/api/audits(/**)`, `/api/orders/admin(/**)` and `/api/demo/control/**`
  require OPERATOR/ADMIN. The Gateway injects `X-User-Id`/`X-User-Roles`/`X-Trace-Id` and **strips
  client-supplied spoofed** copies of those headers.
- Resource-ownership is enforced service-side (e.g. only the order's rider may pay/review; only the
  trip driver or an operator may complete; file access is owner/operator/admin only).

## Provider webhooks (payment)

- `POST /api/payments/callbacks/{provider}` is the only path that drives a payment intent to a
  terminal state — no front-end back door. It is authenticated by **HMAC-SHA256** over
  `timestamp.nonce.rawBody` (secret from `PAYMENT_WEBHOOK_SECRET`), a **timestamp freshness window**,
  and **single-use nonces** (replay protection). The signature is checked before the nonce is
  registered, so forged requests cannot poison the replay store.
- Callbacks are **idempotent per `event_id`**; state changes route through `PaymentIntentStateMachine`
  so a terminal intent is never resurrected by a late/out-of-order callback.

## Files

- Objects are private by default; clients only receive a file id or a short-lived presigned URL.
- Upload is restricted to a **MIME whitelist** and a **size limit**; the limit is re-checked against
  the **actual** stored object size at completion (a client lying at presign time is still caught).

## Gateway hardening

- Security response headers on every proxied response: `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `X-XSS-Protection: 0`,
  `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`.
- Per-environment CORS (demo = local Vite origins; staging/production = real origins via env).
- TLS-ready (`TLS_ENABLED` + keystore env); enable HSTS alongside TLS.
- Fixed-window rate limiting (per-IP on `/api/auth/**`, per-user elsewhere; Redis-backed optional).

## Data protection & audit

- Phone numbers are AES-GCM field-encrypted at rest; masked in directories and logs. OCR/identity
  document numbers are masked before storage.
- State changes (verification approve/reject, order pay/cancel/complete, payment status, file
  access, review submission) write audit logs to MongoDB with `traceId`/`correlationId`, delivered
  via a service-local outbox + retrying relay (at-least-once) so audits survive a transient outage.
- Logs must never contain secrets, full phone numbers, full document/card numbers, verification-code
  plaintext, or token plaintext.

## Docker

- Middleware ports bind to `127.0.0.1` only. `docker-compose.demo.yml` adds per-container
  memory/CPU limits, `no-new-privileges`, and a restart policy. Official images run as non-root.

## Known gaps (intentional for this round)

- Internal service-to-service calls (e.g. auth→notification, driver→identity) are protected by not
  being routed through the Gateway, but have no mTLS/internal-token yet (revisit for real
  deployment).
- In-memory fallbacks for SMS/refresh/nonce stores are single-instance; Redis is used when present.
- Demo-data reset (vs. the operator seed) and observability/pen-testing are out of scope.
