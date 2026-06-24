# API Contract

所有外部接口通过 Gateway 暴露，统一前缀为 `/api/**`。当前 `0.4.0-SNAPSHOT` 已将 Users、Driver Verification、Trips、Orders、Payment Sim、Files、AI OCR Mock 任务落到 MySQL/Flyway；Order 已接 Outbox + RabbitMQ TTL/DLX 延迟超时取消主路径；Files 已接 MinIO presigned upload/download；Audit 已接 MongoDB 落库与检索。Map 仍是 MVP 适配层占位。

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
- `/api/admin/**`、`/api/audits`、`/api/audits/**`、`/api/orders/admin/**` 要求 `OPERATOR` 或 `ADMIN`。
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
  - persistence: MySQL `orders`，唯一键 `(rider_id, idempotency_key)`；创建成功后写 `order_outbox_events`，由 Outbox publisher 投递 RabbitMQ 延迟超时消息。

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
  - note: 手工 smoke/admin 测试入口保留；生产主路径是 RabbitMQ TTL/DLX 延迟消息触发内部 `expireIfPaymentPending`，定时扫描仅作为兜底对账。

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
  - request: `{ "objectName": "license.png", "contentType": "image/png" }`
  - fallback request field for local direct tests: `ownerId`
  - response: `{ "fileObject": FileObject, "uploadUrl": "...", "method": "PUT", "requiredHeaders": { "Content-Type": "image/png" }, "expiresAt": "..." }`
  - behavior: Gateway 透传 `X-User-Id` 时以当前用户为 owner；服务端生成 MinIO object key，不信任前端传入路径。

- `POST /api/files/{fileId}/complete`
  - request: `{ "ownerId": "user-1" }` only for local direct tests
  - response: `FileObject`
  - behavior: owner/operator/admin 可完成；服务端通过 MinIO `statObject` 确认对象存在后标记 `AVAILABLE`。

- `GET /api/files/{fileId}/presign-download`
  - response: `{ "fileObject": FileObject, "downloadUrl": "...", "expiresAt": "..." }`
  - behavior: owner/operator/admin 可生成短时下载 URL；未完成上传的文件不能下载。

- `POST /api/files/mock-upload`
  - request: `{ "ownerId": "user-1", "objectName": "driver/license.png", "contentType": "image/png" }`
  - response: `FileObject`
  - persistence: MySQL `file_objects`。
  - note: 兼容旧本地客户端，只创建 `AVAILABLE` 私有文件元数据，不上传 MinIO。

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
  - behavior: 有 `X-User-Id` 时以 Gateway principal 为 actor；`actorId` 只作为本地直连 fallback。响应包含 `traceId`。

- `GET /api/audits?targetType=&targetId=&action=&actorId=&page=&size=`
  - response: `{ "items": AuditLog[], "page": 0, "size": 50, "total": 123 }`
  - behavior: operator/admin only through Gateway；默认 `page=0,size=50`，最大 `size=100`。

Audit 当前写入 MongoDB `audit_logs`，已接入司机审核、订单支付成功、订单超时取消、文件上传完成和文件下载授权发放等 MVP 关键事件。
