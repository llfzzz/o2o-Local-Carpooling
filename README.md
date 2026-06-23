# O2O Local Carpooling

Enterprise-grade O2O local carpooling MVP. The first release focuses on a complete driver-published trip flow: phone login, driver document review, trip publishing, route search, seat locking, simulated payment, delayed cancellation, and operations audit.

## Stack

- Backend: JDK 21, Spring Boot 3.5.15, Spring Cloud 2025.0.3, Spring Cloud Alibaba 2025.0.0.0
- Middleware: MySQL, Redis, RabbitMQ, MongoDB, MinIO, Nacos
- Frontend: React, TypeScript, Vite, Ant Design, Ant Design Mobile, TanStack Query, Zustand
- Quality: JUnit 5, AssertJ, Playwright-ready frontend structure, GitHub Actions workflow

## Layout

```text
backend/               Maven multi-module backend and service-owned Flyway migrations
apps/user-h5/          Mobile-first rider/driver H5 app
apps/admin-console/    Operations console
infra/                 Legacy local database bootstrap reference
docs/                  PRD, architecture, API and operations notes
scripts/               Local verification helpers
```

## Local Development

Start middleware:

```bash
docker compose up -d
```

Run backend tests:

```bash
./mvnw test
```

Install frontend dependencies and verify:

```bash
PATH="/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin:$PATH" pnpm install
PATH="/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin:$PATH" pnpm typecheck
PATH="/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin:$PATH" pnpm build
```

## Security Defaults

- External traffic must enter through Gateway paths under `/api/**`.
- Auth login returns an HS512 signed JWT; mock SMS and optional mock roles remain local MVP-only behavior.
- Gateway enforces Bearer JWT authentication for protected APIs, RBAC for admin/audit routes, fixed-window rate limits, and unified `ApiError` responses with `X-Trace-Id`.
- File objects are private by default; frontend only receives scoped URLs or opaque file ids.
- External keys are read from environment variables and must never be committed.
- Simulated payment is intentionally isolated from real payment provider code.
