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
  (`control-enabled` / `seed-enabled` / `login-code-peek-enabled` / `virtual-trips-enabled`).
  (`inbox-enabled` was retired in S46 when the inbox became a production feature — see below.)
- `DemoModeGuard` (startup) makes it impossible for any demo flag to be true outside the demo
  profile. `DemoEndpoints` (per-request) returns **404** when the flag is off, so demo endpoints are
  indistinguishable from non-existent ones in staging/production.
- **Demo virtual trips (S46)** are double-gated by `app.demo.virtual-trips-enabled`; generated rows
  are labelled `source=DEMO`, use synthetic `demo-driver-N` ids that can never authenticate (auth
  only mints `user-<phone>`), are priced by the same server-side `PricingPolicy` as real trips
  (formula-derived, zero variation), are rate-limited, replace-not-accumulate per route, and expire
  after 24h. The driver-capability bypass is confined to the generator and justified by the 404
  gate + synthetic drivers + labelled rows. `GET /internal/maps/demo-places` (unrouted) 404s unless
  the demo map provider is active, so it can never burn real provider quota.

## Authentication & authorization

- SMS login codes are generated with `SecureRandom`, stored **hashed** (single-use, TTL),
  constant-time compared, per-phone issuance rate-limited, and locked out after repeated failures.
  The plaintext is delivered out-of-band and never returned by the API.
- **Login codes are never inbox messages (S46).** In demo mode the plaintext lives only in a
  challenge-bound store inside auth-service, keyed `(phone, challengeId)`, written **only** by
  `DemoSmsProvider` (a bean that exists only under `providers.sms.type=demo`, so no plaintext is
  ever stored in staging/production — a structural guarantee, not just a flag). The login page
  peeks it via `POST /api/auth/sms-code/demo-peek` (POST body, no phone in URLs) presenting the
  `challengeId` returned by the matching `sms-code` request; a wrong/stale challenge is
  indistinguishable from "no code yet". Plaintext is deleted on successful `verify()`, on lockout,
  and by TTL, and is never logged/audited. Double-gated by `app.demo.login-code-peek-enabled` plus
  a per-phone peek rate limit. `NotificationService.notify` rejects category `AUTH_SMS_CODE`
  (`400 CATEGORY_NOT_INBOXABLE`); a migration purges any historical rows; inbox queries exclude the
  category defensively.
- **Message Center is a production feature (S46).** `/api/inbox` (successor of the demo-gated
  `/api/demo/inbox`, which was removed) is JWT-protected and strictly `X-User-Id`-scoped. Masked
  preview + explicit `reveal` (owner + TTL + audited, value never logged) is now a **production**
  invariant, not a demo affordance. Order/trip lifecycle notices are produced by a transactional
  outbox (same tx as the state change) relayed at-least-once with receiver-side dedupe by
  `event_id`; notice bodies carry only ids/seats/times — **never phones, names, or cancel reasons**.
- **Chat authz (S46).** A conversation is bound to a legitimate order (`order_id` unique).
  Participant identities are derived server-side from authoritative order/trip records — clients
  never pass a user id. Only the trip's driver and the order's rider may access it; **every
  endpoint returns 404 `CONVERSATION_NOT_FOUND` to non-participants** (discovery-proof; even an
  operator role gets 404 — there is no operator chat surface in v1). Sends are idempotent per
  `clientMsgId`, body-length-capped (1–500), control-char-rejected, and rate-limited (20/60s send,
  10/60s create); order status is re-checked on send (fail-closed). Responses expose only a role
  label for the counterpart, never their user id or phone.
- Roles are **server-authoritative** — resolved from the persisted user record, never from the
  client. `LoginRequest` carries only `phone` + `code` (a regression test guards this).
- Short-lived access token (30 min) + rotating refresh token with reuse detection and family
  revocation.
- All external business requests go through the Gateway. Protected `/api/**` requires a valid Bearer
  token; `/api/admin/**`, `/api/audits(/**)`, `/api/orders/admin(/**)`, `/api/demo/control/**`,
  driver-review (`GET /api/drivers/verification-cases`, `POST .../{caseId}/approve|reject`, S33),
  and the operator user directory / OCR task listing require OPERATOR/ADMIN. The Gateway injects
  `X-User-Id`/`X-User-Roles`/`X-Trace-Id` and **strips client-supplied spoofed** copies.
- **Internal-only endpoints are refused externally (S33):** some backend routes exist purely for
  in-mesh Feign calls (which reach the service directly, never via the Gateway) — user upsert
  (`POST /api/users`), the single-user lookup (`GET /api/users/{id}`), order settlement
  (`POST /api/orders/{id}/pay`), seat lock/release (`POST /api/trips/{id}/seat-locks…`), and the
  removed legacy `simulations`. The Gateway answers **404** for any external request to these, so a
  client cannot self-assign a role, mark an order paid outside the signed-callback pipeline, or
  tamper with another user's seat inventory. This complements the service-side ownership checks.
- Resource-ownership is enforced service-side (e.g. only the order's rider or an operator may read/pay
  a payment intent; only the trip driver or an operator may complete; file access is
  owner/operator/admin only).

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
- Fixed-window rate limiting (per-IP on `/api/auth/**` and payment callbacks, per-user elsewhere;
  map endpoints get their own tighter bucket). The per-IP key is the **real client IP**: behind a
  trusted proxy (loopback nginx by default, plus `security.rate-limit.trusted-proxies`) it is taken
  from `X-Real-IP`/`X-Forwarded-For`, so all traffic no longer collapses into one bucket keyed by the
  proxy address. 429 responses carry `Retry-After`. The limiter is in-memory (per-gateway-instance);
  a distributed (Redis) limiter is deferred until the gateway is scaled out — the gateway does not yet
  depend on spring-data-redis, so `RATE_LIMIT_BACKEND=redis` is currently a no-op there.

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
- **Redis must never evict security state.** Every key this app stores in Redis is
  security/correctness state with its own TTL — hashed SMS codes + attempt counters, refresh
  tokens, payment replay nonces, distributed rate-limit windows, and live driver presence — and
  there is no ordinary evictable cache in Redis (the map route cache lives in MySQL). The Redis
  `maxmemory-policy` must therefore be **`noeviction`** in every environment, so an over-budget
  write fails loudly rather than silently dropping a valid token or replay nonce (which would
  enable webhook replay or a forced mass logout). The base `docker-compose.yml` uses the Redis
  default (`noeviction`); the low-mem overlay pins it explicitly (`docker-compose.lowmem.yml`).
  Size `maxmemory` to the real working set per environment. A general `allkeys-lru`/`volatile-*`
  policy must never be applied to the instance holding this state without an explicit, documented
  risk decision and a separate cache instance for anything evictable.

## Location & tracking threat model (S37–S42)

Precise location is the most sensitive data this product handles. The controls, and their limits:

| Threat | Control | Residual risk |
|---|---|---|
| Client claims to be a driver | `POST /api/trips` binds the driver to the Gateway-verified `X-User-Id` and requires server-checked capability (identity APPROVED + documents APPROVED). Location reporting requires being *that trip's* driver. | None known. A `DRIVER` role claim in a token grants nothing on its own. |
| Harvesting driver positions | Nothing is exposed before booking. After booking, only a rider holding a `LOCKED` seat on that trip can read it; everyone else gets **404, not 403**, so the endpoint cannot be used to probe which trips are live. Releasing the seat ends access immediately. | None known for browsing. A rider could book a seat to observe one driver — inherent to the feature. |
| Spoofed / replayed positions | Coordinate range + datum validation; rejects timestamps >30s in the future or older than the presence TTL; rejects implied speeds >200 km/h; per-trip rate limit. | **Plausibility, not proof.** A spoofer moving at believable speed is not detected. Defeating that needs device attestation, which is out of scope. |
| Location retention | Positions live in Redis under a TTL (default 45s) and are **never written to MySQL**. The TTL is simultaneously the offline signal and the retention limit. | None: there is no location history to leak, subpoena, or forget to delete. |
| Precise location in logs | Coordinates are never logged. The report endpoint deliberately does not echo coordinates back, keeping them out of proxy and access logs. | Redis holds them for the TTL window, as required to serve the feature. |
| Rider location | The rider's detected position is **never persisted**. It is used in-browser to bias search and is sent only as a search parameter; only the *chosen* pickup point is stored, on the trip/order. | None known. |
| Location without consent | Precise location is requested only after an explicit user action, never on page load. Denial is a fully supported path (city picker + manual search), never re-prompted, and **no IP-based inference is performed after a denial**. `Permissions-Policy: geolocation=(self)`. | None known. |
| Browser map key extraction | The JSAPI key renders tiles only — every geocode, POI search and route quote goes through map-service, so a lifted key cannot be used to query place data. Restricted by domain whitelist. The JSAPI *security code* never reaches the browser: nginx proxies `/_AMapService/` and appends it server-side. | A lifted key could render tiles against your quota until the whitelist is enforced. |
| Map quota exhaustion | Map endpoints have their own Gateway rate-limit bucket (default 60/min/user), separate from the general API allowance, because autocomplete fires per debounced keystroke. | None known. |
| Demo data mistaken for real | Demo provider output is flagged `provider="demo"` / `providerTrace="amap-mock"` and carries a persistent UI badge. A configured real provider that fails **fails closed** — never a fabricated route or place. | None known. |

## Known gaps (intentional for this round)

- Internal service-to-service calls (e.g. auth→notification, driver→identity) are protected by not
  being routed through the Gateway, but have no mTLS/internal-token yet (revisit for real
  deployment).
- In-memory fallbacks for SMS/refresh/nonce stores are single-instance; Redis is used when present.
- Demo-data reset (vs. the operator seed) and observability/pen-testing are out of scope.
- **Read-side IDOR (low, unfixed):** `GET /api/orders/{id}`, `GET /api/orders/{id}/review`, and
  `GET /api/trips/{id}` return records to any authenticated caller without an ownership check (ids are
  random UUIDs, so not enumerable). Payment intents and files already scope reads to the
  owner/operator; the same scoping should be extended to single-order reads. Deferred because it
  changes a hot H5 read path and warrants its own test pass.
- ~~**Trip publish identity binding (low, unfixed)**~~ — **CLOSED in S37.** `POST /api/trips` now
  derives the driver from the Gateway-verified `X-User-Id` and ignores any body `driverId`, and
  requires server-checked driver capability (identity APPROVED **and** documents APPROVED, via
  driver-service's internal `/internal/drivers/{userId}/capability`) before publishing. Missing
  principal → `401 AUTH_REQUIRED`; missing capability → `403 DRIVER_NOT_APPROVED`. The capability
  check runs before the map provider call, so a rejected publish costs no provider quota. Covered by
  `TripControllerTest` (spoofed body `driverId` ignored; unauthenticated rejected) and
  `TripPublishServiceTest` (both capability halves, and that no quote is issued on rejection), plus a
  live negative case in `scripts/demo-smoke.sh` step 3b.
