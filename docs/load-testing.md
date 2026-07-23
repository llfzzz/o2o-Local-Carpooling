# Load Testing: Capacity Analysis & Deployment Verification (2026-07-09)

## Context

This project's own `docs/demo-mode.md` states that "full observability and load/pen testing are
intentionally out of scope for this demo round." This document does **not** contradict that — it
records a static, config-derived capacity analysis plus a deployment-drift fix, produced without
generating synthetic concurrent load against the live host, because the host (`/var/www/o2o-Local-Carpooling`
is itself the production box for `woxiangchuanaj.top`) demonstrably cannot absorb it: on 2026-07-08 a
single service restart (rebuilding+restarting `gateway-service` for the S33 commit) triggered a
~20-minute crash/restart loop. Reusable k6 scripts for exercising this system properly are included
(`scripts/loadtest/`), but are explicitly guarded against ever running here — they're meant for a
laptop, staging environment, or CI.

## What triggered this

A routine "make sure the project is running correctly" pass found the project was not actually in the
state its own S33 security audit (`26bba6b`) believed it was in:

- `order-service` and `payment-sim-service` were running jars built *before* `26bba6b`. Confirmed via
  `jar tf` — `payment-sim-service`'s jar still contained the deleted
  `PaymentSimulationController/Service/Repository` classes, and `order-service` still had the removed
  `POST /api/orders/{id}/timeout` method. Root cause: the Maven pitfall already documented in
  `AGENTS.md` (~line 517) — a jar isn't repackaged if held open by its own running JVM.
- gateway-service's Sentinel transport port **8719 was bound to `0.0.0.0`**, not loopback. Bytecode
  inspection confirmed Sentinel is 100% unused (zero `@SentinelResource`/flow-rule usage anywhere) and
  that the same dormant exposure is baked into all 14 services' jars — gateway had simply served enough
  traffic to be the first to lazily trigger the listener.

Both were fixed this session (see `AGENTS.md` S34 entry for the full changelog) and verified two ways:
static jar-content inspection (the only reliable proof — `GatewaySecurityFilter` already 404s the
affected paths regardless of the backend jar's actual content, so an HTTP check alone cannot prove a
redeploy landed) and a full pass of `scripts/check-deployment.sh` (extended with 3 new sections) +
`scripts/demo-smoke.sh`, both green, `FAILS=0`.

## Static capacity analysis

All figures below come directly from live, already-deployed configuration — no traffic was generated to
produce them.

### Memory is the single load-bearing bottleneck

| | Per unit | × count | Total |
|---|---|---|---|
| Backend JVM `MemoryMax` (systemd hard ceiling) | 260M | × 14 services | 3640 MiB |
| Middleware container limits (mysql/redis/rabbitmq/mongodb/minio) | — | 5 containers | 1152 MiB |
| **Combined hard-ceiling sum** | | | **4792 MiB** |
| Total physical RAM | | | 3482 MiB (3.4 GiB) |

**The configured hard ceilings alone already exceed total physical RAM by ~38%, before any request
traffic exists.** Even the soft `MemoryHigh` sum (14×180M + 1152M = 3672 MiB) is ~5.5% over budget. This
is why the host runs in permanent memory oversubscription *by design*, even fully idle (observed
baseline: ~115-150MB free, ~2.0-2.3GB already in swap). It directly explains why a *restart* — a
transient JIT/classloading/connection-pool-warmup spike landing on a baseline with ~0 headroom — is what
previously cascaded into a crash loop, not sustained request concurrency. This matches
`docs/operations.md`'s own stated requirement almost exactly: ≥8GB *available* gives roughly 3.4GB of
genuine headroom above the 4792MB hard-ceiling sum, a sane margin for OS/cache/burst.

**This session's redeploy validated the mitigation**: stopping each target service *before* rebuilding
(rather than rebuilding a jar held open by its own running JVM) both sidesteps the Maven repackaging bug
and frees ~180-260MB of headroom before the build runs. Result: `order-service` reached healthy in ~25s
and `payment-sim-service` in ~24s — compare to the 205-267s (and an outright crash loop) seen on
2026-07-08 doing it the other way.

### Per-service concurrency ceiling: Hikari, not Tomcat

- `spring.datasource.hikari.maximum-pool-size=2`, uniform across all 14 services (no per-service
  override raises it — `deploy/systemd/o2o@.service`).
- A 3rd simultaneous DB-touching request to the *same* service queues for up to the fixed
  `connection-timeout=120000` (2 minutes) before failing — a tight ceiling reached at just 3-4
  simultaneous requests to one service, and the long queue tolerance means overload shows up as severe
  latency, not fast, cheap failure.
- Tomcat's default 200-thread cap is never the real constraint: `-Xmx96m -XX:MaxMetaspaceSize=96m
  -XX:ReservedCodeCacheSize=24m` already claims 216 of the 260MB hard `MemoryMax`, leaving ~44MB for
  thread stacks/buffers/native overhead — a cgroup OOM-kill (`SIGKILL`) is the realistic failure mode
  long before thread count matters.

### MySQL connection headroom: comfortable, not the bottleneck

14 services × 2 max Hikari connections = 28 possible concurrent app connections, vs.
`--max-connections=40` → 12 (30%) of headroom in the worst case. Actual idle usage is much lower since
only `order-service`/`trip-service` set `minimum-idle=1`; the other 12 default to `minimum-idle=0`.

### Gateway rate limits (a deliberate control, not a bug — but a real throughput ceiling regardless)

- `/api/auth/**`: **20 requests / 60s**, keyed by client IP.
- All other `/api/**`: **120 requests / 60s**, keyed by authenticated user id.
- Fixed, wall-clock-epoch-aligned windows (not sliding, not token-bucket) —
  `backend/gateway-service/src/main/resources/application.yml`.
- Realistic max sustained per-identity throughput: ~2 req/s general API, ~0.33 req/s on auth endpoints,
  regardless of backend capacity.

### CPU: secondary constraint

2 total vCPUs; each JVM pinned `-XX:ActiveProcessorCount=1`; middleware reserves ~1.2 cores of soft quota
between the 5 containers, leaving under 1 core of headroom for 14 JVMs to contend over. Real, but this
throttles rather than kills — memory is the primary risk on this host, not CPU.

### Bottom line

This architecture's constraints (Hikari pool of 2, rate limits) are tight but *graceful* — they queue or
429. The actual observed failure mode is *not* "too many concurrent requests," it's "a process lifecycle
event is a transient memory spike landing on a baseline with zero headroom." That is exactly why no
synthetic concurrent load was generated against this host as part of this work, and why the redeploy
runbook used here (stop-before-rebuild, one service at a time, health-gated, with explicit abort
criteria) is the correct shape of mitigation for this specific risk.

## Verified this session (not a load test — correctness + security-boundary checks only)

`scripts/check-deployment.sh` (extended with 3 new sections) and `scripts/demo-smoke.sh`, both run
against the live host post-redeploy, both fully green:

- Admin listing endpoints (S29+), unmapped-path 404 semantics, cold/warm latency snapshot — all as
  before, all passing (cold/warm gap for `/api/trips` was 0.85s → 0.04s post-redeploy, well below the
  multi-second gaps documented during the 2026-07-07 host-paging incident).
- **New — S33 re-verification**: all 7 internal-only paths (`POST /api/users`, `GET /api/users/{id}`,
  order pay/timeout, trip seat-locks, legacy payment simulations) still correctly 404 externally;
  driver-review RBAC still correctly gated (operator 200 / rider 403 on both the list and approve
  actions).
- **Known-gap baseline** (INFO-only): `GET /api/orders/{id}` and `GET /api/trips/{id}` remain
  readable cross-user as documented in `docs/security.md`. The former body-supplied trip `driverId`
  gap is closed: publishing now binds to the authenticated principal and requires driver capability.
- Full `demo-smoke.sh` 13-step flow (login → publish → search → book → payment intent → signed
  callback → SEAT_LOCKED → identity verification → complete → review + duplicate-409 → cancel →
  negative-authz 403): `FAILS=0`.

## Reusable load-test scripts (`scripts/loadtest/`) — not run against this host

- `lib/api.js` — shared login/demo-inbox/operator-session helpers (JS port of `demo-smoke.sh`'s
  `login()`).
- `booking-flow.js` — the full `demo-smoke.sh` flow as k6 checks, parameterized
  (`VUS`/`DURATION`), conservative defaults. Each VU earns identity+liveness and driver-document
  approval before publishing, matching the real server-side gate.
- `rate-limit-boundary.js` — single-VU, single-iteration boundary probe for the 20/60s and 120/60s
  limits, window-aligned so a burst can't straddle two fixed windows.
- `sse-concurrency.mjs` — dependency-free Node load probe for authenticated driver-location SSE.
  It opens 10 → 25 → 50 → 100 connections for 2 minutes per stage by default and fails unless
  connection success is ≥99%, first-event p95 <5s, event-gap p95 <6s, abnormal disconnects <1%,
  and 5xx count is zero. Required environment:
  ```bash
  TARGET_BASE_URL=https://staging.example.test/o2o-api \
  SSE_TRIP_ID=trip-test \
  SSE_VIEWER_TOKEN='test-token' \
  I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes \
    node scripts/loadtest/sse-concurrency.mjs
  ```
- Every script refuses to run (in `setup()`) unless `TARGET_BASE_URL` avoids a known-production-hostname
  denylist **and** `I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes` is explicitly set. See the header comment in
  each file for prerequisites. The SSE script additionally requires an authorized locked rider or the
  trip's driver token and hard-refuses the production domain.

## Recommendations (not executed this session)

- **Permanent Sentinel fix**: exclude `sentinel-transport-simple-http` in root `backend/pom.xml`'s
  `<dependencyManagement>` for `spring-cloud-starter-alibaba-sentinel` (covers all 14 services in one
  diff, since none pin their own version). Requires rebuilding all 14 — deferred because the config-only
  fix already applied (`SPRING_CLOUD_SENTINEL_ENABLED=false`) closes the actual exposure; the 11 services
  that don't currently have 8719 open pick up the same config fix at their next natural restart.
- Actual SSE concurrency execution still needs a non-production target, an active trip and a valid
  participant token. Never point the script at `woxiangchuanaj.top`.
- The read-side IDOR items remain intentionally deferred per `docs/security.md`; trip publishing no
  longer trusts a body `driverId` and the load flow now exercises the real driver-capability gate.

## Round 1 performance fixes (2026-07-23)

A first, deliberately bounded optimization pass landed — safe, unit-testable, config-reversible, with
**zero** new Redis/JVM footprint (see `docs/operations.md` → "性能与可扩展性基线 (Round 1)" and the
`AGENTS.md` S49 entry for the full changelog). Relative to the bottleneck ranking above:

- **Now measurable.** `micrometer-registry-prometheus` is on the classpath, so `/actuator/prometheus`
  serves on every service (it used to 404 despite being "exposed"). That endpoint now exposes JVM/GC,
  thread, HikariCP pool (active/idle/**pending**/acquisition-time) and HTTP-server timing meters, plus
  custom `gateway.ratelimit.decisions` and `order.seatlock.reconciliation` counters. **This is the
  hook to capture the still-missing p50/p95/p99, GC and Hikari-pending baselines** on a suitable
  non-prod host — point Prometheus at it and re-run `scripts/loadtest/*` there.
- **Hikari sizing** is no longer implicit in launch args (it silently defaulted to 10 on a bare
  `java -jar`); it is declared per service and env-overridable.
- **Feign read timeout** cut 90s → 30s, so a stalled downstream can no longer pin a caller thread for
  90s against a pool of 2.
- **Gateway rate limits** are now keyed by the real client IP behind the proxy (they had collapsed to
  one bucket = the nginx IP) and carry `Retry-After`; the in-memory limiter no longer leaks keys.
- **SQL** hot paths got bounded/indexed (admin order list, dashboard driver count, today-count, geo
  seek, inbox keyset). **before/after `EXPLAIN ANALYZE` was NOT captured this session** — the docker
  demo MySQL (3307) was down and the host's native MySQL credentials are unknown (not guessed, not
  touched). This is a reported evidence gap, not a completed measurement; the exact `EXPLAIN` commands
  are in `docs/operations.md`. Correctness of the changed queries is covered by the H2 repository tests.
- Still deferred (need a real environment / measured evidence): JVM/GC re-tuning, a distributed
  gateway limiter, a new Redis read cache, and the SSE concurrency run.

## Round 2 — Redis read cache + distributed limiter (2026-07-23)

Two of the Round-1 "deferred" items are now **implemented and proven against real Redis** (see
`AGENTS.md` S50 and `docs/adr/0005`): a map route Redis read-cache with avalanche/penetration/hot-key
protection + a distributed cache-fill lease, and a genuinely distributed (reactive Lua) gateway
limiter. Evidence status, clearly separated:

- **Code + unit/integration tests: done.** Testcontainers integration tests actually run against a
  real Redis and prove the *distributed* behaviors that mocks can't: two limiter instances enforce
  one shared quota, atomic under concurrency, window reset, `Retry-After` = window remainder; and two
  map-service instances with a concurrent cache miss make exactly **one** provider call (the lease
  works). `./scripts/verify.sh` green. `@Testcontainers(disabledWithoutDocker=true)` skips them where
  no Docker daemon exists, so `mvn test` stays green there.
- **Runtime config verified.** `docker compose -f docker-compose.yml -f docker-compose.cache.yml
  config` validates; the cache Redis is a separate `allkeys-lfu` instance on 6380, off by default.
- **Load evidence still missing (blocked, not faked).** The comparative run the DoD asks for — ≥2
  gateway instances on one state Redis, ≥2 map-service instances on one cache Redis, before/after
  provider-call and `route_snapshots`-read counts, cache hit rate, p50/p95/p99, Redis memory, and 429
  behavior under k6/SSE — needs a non-production staging target with adequate hardware, which this
  session did not have. The scripts (`scripts/loadtest/*`) are ready and production-denylisted. **Do
  not** point them at `woxiangchuanaj.top`.
