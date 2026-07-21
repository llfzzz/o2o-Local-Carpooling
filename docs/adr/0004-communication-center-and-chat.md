# ADR 0004 — Communication center, chat, and login-code isolation

- Status: Accepted
- Date: 2026-07-21
- Supersedes: nothing. Revises part of the S4/S5 "Demo Delivery Center" decision (the demo inbox
  becomes a production Message Center). Extends ADR 0002 (Provider SPI + demo profiles).

## Context

Login SMS codes were delivered into the general notification inbox as `AUTH_SMS_CODE` rows, and the
login page peeked them via an **unauthenticated** `GET /api/auth/sms-code/demo-inbox?phone=` that
could read any phone's latest code. The "Message Center" was a demo-gated delivery mailbox
(`/api/demo/inbox`) with no unread count, pagination, categories, or links — not a real user
communication surface. There was no chat between passengers and drivers, and notifications were
produced only at three hardcoded sites by direct best-effort Feign calls.

## Decisions

### 1. Login codes are never inbox messages; the demo code is a challenge-bound login-page peek

Plaintext demo codes live only in a `DemoLoginCodeStore` inside auth-service, keyed
`(phone, challengeId)`, written **exclusively** by `DemoSmsProvider` — a bean that exists only when
`providers.sms.type=demo`, which the shared provider config permits only under the demo profile. So
outside demo, no plaintext code is stored anywhere: a structural guarantee, not merely a flag.
`POST /api/auth/sms-code` returns an opaque `challengeId` (never the code); the login page peeks via
`POST /api/auth/sms-code/demo-peek` presenting it. A wrong/stale challenge is indistinguishable from
"no code yet". Plaintext is deleted on login, lockout, and TTL, and never logged.

*Why:* a code delivered to a shared inbox, and a peek endpoint that reads any phone's code, are both
ways for a login secret to leak. Binding the peek to the issuing request's challenge and keeping the
plaintext out of the notification store and out of logs closes both.

### 2. The Message Center is a production feature (`/api/inbox`), not a demo affordance

The demo inbox is removed; `/api/inbox` is JWT-protected, `X-User-Id`-scoped, and adds keyset
pagination, category filters, unread counts, mark-one/all-read, and deep links
(`linkType`/`linkId`). Masked preview + explicit reveal (owner + TTL + audited) is kept as a
**production** invariant for sensitive categories. It lives on a new path — not `/api/notifications`
— because the gateway does not route the internal notify API and must not start.

### 3. Notifications are produced from domain events via a transactional outbox

Rather than scatter direct Feign calls, order-service writes an `order_notification_outbox` row in
the **same transaction** as each state transition; a scheduled publisher relays it at-least-once
with the outbox `event_id` as the receiver's dedupe key (exactly-once inbox). Driver verification
results use best-effort direct Feign (mirroring identity-service — a non-critical notice must never
fail the review decision). Departure reminders are a `@Scheduled` scan in trip-service, which owns
`departure_at` and the LOCKED-seat rider ids. A shared `NotificationCategory` enum in `common` kills
cross-service string drift. RabbitMQ was **not** used and notification-service gained no broker
dependency: the proven audit-outbox pattern already in order-service is the right precedent, and it
keeps the fan-out durable without new infrastructure.

### 4. Chat is hosted inside notification-service; membership is 404-on-miss

Standing up a 15th JVM was rejected: the production host is already memory-oversubscribed (AGENTS
S31/S34). notification-service is the communication center, so chat lives there. A conversation is
bound to a legitimate order; participants are resolved once at creation from authoritative
order/trip records (a new **unrouted** `GET /internal/orders/{orderId}` — not the
ownership-unchecked external order GET). Hot-path membership is a local column comparison, and
**every endpoint returns 404 to non-participants** (the driver-location `requireParticipant`
precedent), so a conversation's existence cannot be probed. There is no operator chat endpoint in v1
(the requirement allows it only via an explicit support/audit workflow — deferred).

### 5. Realtime is polling, not SSE/WebSocket

Chat and unread counts poll bearer-authenticated REST endpoints on a short interval, exactly like
the S42 driver-location client. `EventSource` cannot send an `Authorization` header, and putting a
token in the URL would log it; a WebSocket gateway is more infrastructure than this needs. The
message endpoints are transport-agnostic, so a future SSE/WebSocket upgrade needs no contract change.

## Consequences

- One fewer secret-leak path (login codes) and a real, production-grade Message Center + chat, with
  no new runtime infrastructure.
- notification-service gains chat tables and Feign clients to order/trip; its blast radius grows but
  its deployment shape does not.
- Operator chat access, and a durable broker-based event bus for cross-service fan-out, are
  deliberately deferred.
