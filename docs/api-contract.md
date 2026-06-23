# API Contract

所有外部接口通过 Gateway 暴露，统一前缀为 `/api/**`。当前 `0.3.0-SNAPSHOT` 已将 Users、Driver Verification、Trips、Orders、Payment Sim、Files、AI OCR Mock 任务落到 MySQL/Flyway；Admin 已接真实聚合读接口。Map、Audit 仍是 MVP 适配层占位。

## Auth

- `POST /api/auth/sms-code`
  - request: `{ "phone": "13800000000" }`
  - response: `{ "phone": "...", "mockCode": "MOCK-123456", "expiresAt": "..." }`

- `POST /api/auth/login`
  - request: `{ "phone": "13800000000", "code": "123456", "roles": ["RIDER", "DRIVER"] }`
  - response: `{ "accessToken": "...", "tokenType": "Bearer", "expiresAt": "...", "user": UserAccount }`
  - security: `accessToken` 是 HS512 签名 JWT，claims 包含 `sub`、`roles`、`jti`、`iat`、`exp`。
  - note: 登录验证码仍是 Mock；`roles` 为 MVP/本地测试专用可选字段，默认角色为 `RIDER`、`DRIVER`，生产不能允许客户端指定角色。

## Gateway Security

- `POST /api/auth/**`、`GET /actuator/health`、`GET /actuator/info` 放行。
- 其他 `/api/**` 必须提供 `Authorization: Bearer <jwt>`。
- `/api/admin/**`、`/api/audits/**`、`/api/orders/admin/**` 要求 `OPERATOR` 或 `ADMIN`。
- Gateway 验证通过后向下游注入 `X-User-Id`、`X-User-Roles`、`X-Trace-Id`，并移除客户端传入的同名伪造头。
- `401`、`403`、`429` 统一返回 `ApiError`，响应头带 `X-Trace-Id`。
- 默认限流：`/api/auth/**` 每 IP 每 60 秒 20 次；其他 `/api/**` 每 userId 每 60 秒 120 次。`security.rate-limit.backend=redis` 时可切换 Redis 固定窗口计数。
- Gateway 已配置本地 Vite CORS：`http://127.0.0.1:5173`、`http://127.0.0.1:5174` 和 localhost 等价地址；`OPTIONS` 预检不要求 Bearer token。

## Users

- `POST /api/users`
  - request: `{ "userId": "user-1", "phone": "13800000000", "roles": ["RIDER"] }`
  - response: `UserAccount`
  - persistence: MySQL `users`，手机号以字段加密后的 `VARBINARY` 存储。

- `GET /api/users/{userId}`
  - response: `UserAccount`

## Driver Verification

- `POST /api/drivers/verification-cases`
  - request: `{ "userId": "user-1", "drivingLicenseFileId": "file-1", "vehicleLicenseFileId": "file-2" }`
  - response: `VerificationCase`
  - persistence: MySQL `driver_verification_cases`，OCR Mock 的证件号字段落库前脱敏。

- `GET /api/drivers/verification-cases`
  - response: `VerificationCase[]`

- `POST /api/drivers/verification-cases/{caseId}/approve`
- `POST /api/drivers/verification-cases/{caseId}/reject`

## Trips

- `POST /api/trips`
  - request: `{ "driverId": "driver-1", "originText": "A", "destinationText": "B", "departureAt": "...", "distanceMeters": 12000, "durationSeconds": 1800, "totalSeats": 3 }`
  - response: `TripOffer`
  - persistence: MySQL `trips`，服务端生成 `tripId`、`routeId`，按 `PricingPolicy` 计价，初始 `lockedSeats=0`。

- `GET /api/trips?origin=A&destination=B`
  - response: `TripOffer[]`

- `GET /api/trips/{tripId}`
  - response: `TripOffer`

- `POST /api/trips/{tripId}/seat-locks`
  - request: `{ "orderId": "order-1", "seats": 1 }`
  - response: `TripOffer`
  - behavior: 由 Trip 服务按 `orderId` 幂等锁座，库存不足返回冲突类错误。

- `POST /api/trips/{tripId}/seat-locks/{orderId}/release`
  - response: `TripOffer`
  - behavior: 按 `orderId` 幂等释放座位。

## Orders

- `POST /api/orders`
  - request: `{ "tripId": "trip-1", "riderId": "user-1", "seats": 1, "idempotencyKey": "booking-001" }`
  - response: `OrderDetail`
  - behavior: Gateway 透传 `X-User-Id` 时以该用户为 rider；`riderId` 只作为本地 MVP 直连 fallback。金额从 Trip 服务读取，不信任前端传价。
  - persistence: MySQL `orders`，唯一键 `(rider_id, idempotency_key)`。

- `GET /api/orders/{orderId}`
  - response: `OrderDetail`

- `GET /api/orders?status=PENDING_PAYMENT`
  - response: `OrderDetail[]`
  - behavior: 有 `X-User-Id` 时默认返回当前用户订单。

- `POST /api/orders/{orderId}/pay`
  - response: `OrderDetail`

- `POST /api/orders/{orderId}/timeout`
  - response: `OrderDetail`
  - behavior: 对 `PENDING_PAYMENT` 订单幂等超时取消并释放 Trip 库存；已支付订单不能超时。

- `GET /api/orders/admin`
  - response: `OrderDetail[]`

- `GET /api/orders/admin/metrics`
  - response: `{ "todayOrders": 1, "lockedOrders": 1, "overduePendingPayments": 0 }`

## Payment Sim

- `POST /api/payments/simulations`
  - request: `{ "orderId": "order-1", "idempotencyKey": "pay-001" }`
  - response: `PaymentSimulation`
  - behavior: Payment Sim 从 Order 服务读取订单金额，写入 MySQL `payment_simulations`，再将订单标记为模拟支付成功。

- `POST /api/payments/simulate-success`
  - compatibility alias for old local clients; `amount` 字段会被忽略，仍以订单服务金额为准。

## Files

- `POST /api/files/presign-upload`
  - request: `{ "ownerId": "user-1", "objectName": "driver/license.png", "contentType": "image/png" }`
  - response: `FileObject`
  - note: 当前只创建私有文件元数据，不生成真实 MinIO 预签名 URL。

- `POST /api/files/mock-upload`
  - request: `{ "ownerId": "user-1", "objectName": "driver/license.png", "contentType": "image/png" }`
  - response: `FileObject`
  - persistence: MySQL `file_objects`。

## AI

- `POST /api/ai/ocr/mock`
  - request: `{ "fileObjectId": "file-driving-license-001" }`
  - response: `OcrResult`
  - persistence: MySQL `ocr_tasks`，Mock OCR 结果中的证件号字段脱敏保存并返回。

## Admin, Audit

- `GET /api/admin/dashboard`
  - response: `{ "pendingDriverReviews": 0, "todayOrders": 0, "lockedOrders": 0, "overduePendingPayments": 0, "riskAlerts": 0, "status": "live-mvp" }`
  - behavior: 聚合 Driver Verification 和 Order admin metrics。

- `POST /api/audits`
- `POST /api/audits/logs`
  - request: `{ "actorId": "user-1", "action": "ORDER_TIMEOUT", "targetType": "ORDER", "targetId": "order-1", "metadata": {} }`
  - response: `AuditLog`

Audit 当前仍为适配层占位，后续接入 MongoDB 审计。
