# Demo Mode

This document describes the "clickable end-to-end demo" design: how the system runs on real
middleware while every external **business** provider is a swappable adapter, and how the
demo-only affordances are fenced off so they can never leak into staging/production.

## Principles

- **Infrastructure is always real.** MySQL, Redis, RabbitMQ, MongoDB, MinIO and Nacos run in Docker
  and are never mocked.
- **External business providers are adapters.** SMS, payment, identity/liveness, OCR, map and
  notification are each behind a Provider SPI. The demo ships a `Demo*` implementation; a real
  vendor is dropped in by implementing the same SPI and setting `providers.<cap>.type`.
- **Security is never weakened for the demo.** The demo affordances are additive and gated; the
  auth, RBAC, signing, replay-protection, idempotency, audit and masking rules always apply. See
  [security.md](security.md).
- **Sensitive outputs are pulled, not pushed.** Verification codes, identity results and payment
  outcomes land in the Demo Inbox (masked) and require an explicit reveal; they are never returned
  inline or auto-filled.

## Profiles

Three Spring profiles select provider types and demo flags (see
`backend/common/src/main/resources/carpooling-providers.yml`):

| Profile | `app.demo-mode` | inbox/control/seed | provider types |
|---|---|---|---|
| `demo` | true | enabled | all `demo` |
| `staging` | false | disabled | from env (`SMS_PROVIDER`, `PAYMENT_PROVIDER`, …), empty ⇒ fail-closed |
| `production` | false | disabled | from env, empty ⇒ fail-closed |

`DemoModeGuard` refuses to start if any demo flag is true outside the demo profile, or if
`app.demo-mode=false` while a demo flag is true (double-fence). An empty provider type means "not
configured": the demo bean is absent and any real call fails closed at invocation time.

## Provider adapters (SPI + demo implementation)

| Capability | SPI | Demo impl | Selected by |
|---|---|---|---|
| SMS | `SmsProvider` (auth-service) | `DemoSmsProvider` → Demo Inbox | `providers.sms.type` |
| Payment | `PaymentProvider` (payment-sim) | `DemoPaymentProvider` + signed callbacks | `providers.payment.type` |
| Identity + liveness | `IdentityVerificationProvider` (identity-service) | `DemoIdentityProvider` | `providers.identity.type` |
| OCR | `OcrProvider` (ai-service) | `DemoOcrProvider` (async task) | `providers.ocr.type` |
| Map/route | `MapRouteProvider` (map-service) | `MockRouteProvider` (`amap` = real) | `providers.map.type` |
| Notification | `NotificationChannelAdapter` (notification-service) | `DemoNotificationChannelAdapter` | `providers.notification.type` |

## Demo-only endpoints (double-gated)

Every demo endpoint is fenced by a per-request `DemoEndpoints` gate on top of `DemoModeGuard`. When
the relevant flag is off it returns **404** (not 403), so the endpoint is indistinguishable from a
non-existent one outside the demo profile. The Gateway additionally RBACs `/api/demo/control/**` to
OPERATOR/ADMIN.

- **Demo Inbox** (`app.demo.inbox-enabled`): `GET /api/demo/inbox`, `POST /api/demo/inbox/{id}/reveal|read`.
  User-scoped; a user can only see and reveal their own deliveries within the TTL.
- **Demo Control** (`app.demo.control-enabled`): operator-driven outcome simulation.
  - `POST /api/demo/control/payment/{intentId}/callbacks` — succeeded/failed/canceled/expired with
    delayed/duplicate/out-of-order delivery, all pushed through the **real signed webhook pipeline**.
  - `POST /api/demo/control/identity/{id}/liveness|session` — drive liveness and the overall session
    to any outcome; results are delivered asynchronously to the inbox.
  - `POST /api/demo/control/notification/{id}/status` — simulate delivery status.
- **Demo Seed** (`app.demo.seed-enabled`): `POST /api/auth/demo/operator-session` mints an
  OPERATOR + ADMIN session in one call (there is no real operator-provisioning flow yet), so the
  admin console and the Demo Control endpoints are usable.

## Running the demo

```bash
scripts/generate-local-env.sh                                                     # random secrets, gitignored .env
MYSQL_HOST_PORT=3307 docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d
./mvnw package -DskipTests                                                         # 14 runnable fat jars
bash scripts/start-services.sh                                                     # all services (demo profile)
pnpm -C apps/user-h5 dev                                                           # H5 at :5173
```

Services run on the host and connect to the Docker middleware on `127.0.0.1`. `MYSQL_HOST_PORT=3307`
avoids clashing with a native MySQL on 3306. `scripts/demo-smoke.sh` drives the full
login → publish → search → book → pay → verify → complete → review → cancel loop through the Gateway.

For manual browser/API operation after the stack is already running, see
[demo-user-guide.md](demo-user-guide.md).

## The end-to-end flow

1. Rider requests an SMS code → reveals it from the Demo Inbox → logs in (RIDER).
2. An operator session is minted (`/api/auth/demo/operator-session`).
3. A trip is published (server-side route snapshot + pricing) and searched.
4. Rider books a seat (`PENDING_PAYMENT`) and creates a Payment Intent.
5. The operator triggers a **signed** payment callback → the intent reaches `SUCCEEDED` and the
   order becomes `SEAT_LOCKED` (paid). No front-end state flip; the signed webhook is the only path.
6. Identity verification: rider starts it (`PENDING`); the operator drives liveness `PASSED` and the
   session `APPROVED`; the result is delivered to the inbox. Driver capability requires `APPROVED`.
7. The operator completes the order (`COMPLETED`); a review invitation lands in the inbox.
8. The rider submits a review; unpaid-timeout and cancellation paths release the seat.

## Deferred real-provider integrations

Swapping in a real provider is: implement the SPI, provide credentials via secure env, and set
`providers.<cap>.type` to the vendor name — no change to the core flow. Real SMS/OCR/identity/
payment/push vendors, map route caching / circuit-breaking, full observability and load/pen testing
are intentionally out of scope for this demo round.
