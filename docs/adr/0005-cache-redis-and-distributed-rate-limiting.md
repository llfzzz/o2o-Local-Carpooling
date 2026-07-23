# ADR 0005 — Two Redis roles (state vs cache) and a distributed gateway rate limiter

Status: Accepted (2026-07-23, perf Round 2)

## Context

Round 1 (S49 / PR #11) established that **every key the app stored in Redis was
security/correctness state with a TTL** (SMS codes, refresh tokens, payment replay nonces,
rate-limit windows, driver presence), so the state Redis was pinned `noeviction`. There was still no
ordinary read cache in Redis: map route quotes were cached only in MySQL (`route_snapshots`, with a
fresh/stale policy) and coalesced only in-process (per-JVM `ConcurrentHashMap` single-flight).
Meanwhile the gateway rate limiter remained per-process and could never actually use Redis
(`RATE_LIMIT_BACKEND=redis` was a dead switch — the gateway had no Redis client).

We want a real Redis read cache and a genuinely distributed gateway limiter **without** letting an
evictable cache endanger security/correctness state, and without increasing the footprint of the
low-memory prod demo host.

## Decision

**Two distinct Redis roles, as two instances (never two logical DBs of one instance):**

- **State Redis** — the existing instance. Security/correctness TTL state only. Stays `noeviction`;
  AOF as configured. Unchanged by this round. The distributed **rate-limiter** counters live here
  (they are correctness state — an evicted window would silently reset a limit).
- **Cache Redis** — a new, optional, disposable instance (`docker-compose.cache.yml`): `allkeys-lfu`,
  persistence off, bounded `maxmemory`, separate port/container/failure domain. It holds only derived,
  reconstructable data: the **map route read-cache** and the **cache-fill leases**. It is off by
  default (`map.route-cache.redis.enabled=false`), so the low-mem prod host is unaffected; enable it
  via the overlay on hosts with headroom.

**Map route read-cache (map-service):** cache-aside in front of the authoritative MySQL snapshot.
Lookup order: validate → Redis fresh → MySQL fresh (populate Redis) → provider (through the existing
circuit breaker) → save MySQL → populate Redis. Versioned String values (`cache:map:route:v1:<sha256>`),
TTL = base + jitter **capped by the snapshot's remaining freshness**, oversize/decode protection, and
**every failure degrades to a cache miss** — Redis can never mask credential/city/coordinate/provider
errors or fabricate a route, and cache loss never corrupts authoritative state. Endpoints are never
cached (request-specific); they are re-applied per caller.

**Distributed cache-fill lease (cache Redis):** `SET key token NX PX` acquire, atomic
owner-compare-and-delete (Lua) release, bounded lease + bounded loser wait. It exists **only** to
avoid duplicate cache rebuild / provider calls across instances; a lost/expired lease at worst causes
one extra provider call. It is never used for business correctness.

**Distributed gateway limiter (state Redis, reactive):** `RATE_LIMIT_BACKEND=redis` now wires a
`ReactiveStringRedisTemplate` + atomic Lua limiter — no blocking Redis call on the Netty event loop.
Buckets, limits, trusted-proxy client-IP and per-user keying are preserved; `Retry-After` comes from
the real Redis window remainder. A misconfigured redis backend fails startup (the bean requires the
reactive template) rather than silently reverting to memory. On a Redis outage the limiter enters a
**metered degraded mode**: a bounded local in-memory emergency limiter, or (when configured)
fail-closed for sensitive buckets (auth / map / payment-callback / demo-control) — never silently
unlimited. The in-memory backend is retained for single-instance demo.

## Alternatives considered

- **One Redis with logical DBs** for cache vs state — rejected: a shared instance shares a failure
  domain and a `maxmemory`/eviction policy; an evictable cache must not be able to pressure or share
  fate with `noeviction` security state. Instance isolation is the point.
- **Redisson** for the lease/limiter — rejected for now: the lease scope is narrow and a small Spring
  Data Redis + Lua implementation covers it without the extra dependency, memory, and operational cost.
- **Blocking `StringRedisTemplate` in the gateway** — rejected: it would block the Netty event loop.

## Consequences

- Cache Redis is purely additive and reversible by config: disable the flag or drop the overlay and
  the system runs exactly as before (MySQL + provider + in-process single-flight). No DB rollback.
- Enabling the redis limiter backend makes gateway limits correct across instances; disabling it
  returns to the per-gateway in-memory limiter.
- New low-cardinality metrics: `map.route.redis.cache.{requests,populate,loads}`,
  `map.route.cache.lease`, and `gateway.ratelimit.{decisions{...,backend},degraded}`.
- The cache Redis must never hold security/correctness state, and the state Redis must stay
  `noeviction` — see `docs/security.md`.
