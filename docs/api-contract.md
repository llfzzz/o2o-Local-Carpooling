# API Contract

所有外部接口通过 Gateway 暴露，统一前缀为 `/api/**`。当前 `0.5.0-SNAPSHOT` 已将 Users、Driver Verification、Trips、Orders、Payment Sim、Files、AI OCR Mock、Map RouteSnapshot 任务落到 MySQL/Flyway；Order 已接 Outbox + RabbitMQ TTL/DLX 延迟超时取消主路径；Files 已接 MinIO presigned upload/download；Audit 已接 MongoDB 落库与检索。Map 在配置 `AMAP_API_KEY` 时走高德 Web 服务地理编码 + 驾车路线规划，未配置时明确走 `amap-mock` 本地 fallback。

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
  - **准入门禁（S17）**：以网关注入的 `X-User-Id` 为准（body `userId` 仅本地直连 fallback）；提交前 driver-service 通过 identity-service 内部接口校验该用户实名认证已 `APPROVED`，否则 `403 DRIVER_IDENTITY_NOT_VERIFIED`。这是叠加在既有运营人工复核之上的第一道闸门。
  - persistence: MySQL `driver_verification_cases`，OCR Mock 的证件号字段落库前脱敏。
  - 依赖内部接口 `GET /internal/identity/verifications/status?userId=X`（identity-service，**不经 Gateway 路由**，仅服务间调用；返回 `{ userId, approved }`）。

- `GET /api/drivers/verification-cases`
  - response: `VerificationCase[]`

- `POST /api/drivers/verification-cases/{caseId}/approve`
- `POST /api/drivers/verification-cases/{caseId}/reject`

## Map

- `GET /api/maps/route?origin=A&destination=B&city=厦门`
- `GET /api/map/route?origin=A&destination=B&city=厦门`
  - response: `RouteSnapshot`
  - behavior: `origin`、`destination` 可传地址文本或 `lon,lat` 坐标；`city` 是可选地理编码提示。
  - provider: 配置 `AMAP_API_KEY` 时调用高德 Web 服务地理编码和路径规划 2.0 驾车接口；未配置时返回 `providerTrace=amap-mock` 的本地 fallback。
  - persistence: MySQL `route_snapshots`，保存起终点文本、解析坐标、距离、时长、provider、providerTrace 和脱敏后的供应商响应快照；不会保存 API Key。
  - failure: 配置高德 Key 后供应商失败返回结构化错误 `MAP_ROUTE_QUOTE_FAILED`，不自动降级到 Mock。

## Trips

- `POST /api/trips`
  - request: `{ "driverId": "driver-1", "originText": "A", "destinationText": "B", "city": "厦门", "departureAt": "...", "totalSeats": 3 }`
  - response: `TripOffer`
  - behavior: Trip 服务调用 Map 服务获取服务端 `RouteSnapshot`，再按 `PricingPolicy` 计价；旧客户端传入的 `distanceMeters`、`durationSeconds` 兼容接收但会被忽略。
  - persistence: MySQL `trips`，服务端生成 `tripId`，保存 Map 服务返回的 `routeId`、距离、时长、providerTrace，初始 `lockedSeats=0`。

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

- `POST /api/orders/{orderId}/cancel`（S14 起）
  - response: `OrderDetail`
  - **鉴权（服务端权威）**：从网关注入的 `X-User-Id` / `X-User-Roles` 解析发起人——订单本人 → `USER_CANCELLED`、行程司机 → `DRIVER_CANCELLED`、OPERATOR/ADMIN → `OPERATOR_CANCELLED`；三者都不是则 `403 ORDER_CANCEL_FORBIDDEN`。
  - behavior: 仅 `PENDING_PAYMENT` 或已支付的 `SEAT_LOCKED` 可取消，经 `OrderStateMachine` 迁移并释放 Trip 座位（按 `orderId` 幂等）；对同一发起人重复取消是幂等 no-op；写审计（`ORDER_CANCELLED_BY_{USER|DRIVER|OPERATOR}`）。已支付订单的退款是真实供应商职责，Demo 阶段不涉及。

- `POST /api/orders/{orderId}/complete`（S14 起）
  - response: `OrderDetail`
  - **鉴权**：仅行程司机或 OPERATOR/ADMIN 可完成（乘客不能自完成，避免伪造评价前置条件）；否则 `403 ORDER_COMPLETE_FORBIDDEN`。
  - behavior: 仅 `SEAT_LOCKED`（已支付）可 `complete` 到 `COMPLETED`，不释放座位（行程已消费）；幂等；写审计（`ORDER_COMPLETED`）；完成时向乘客收件箱投递一条评价邀请（category `ORDER_REVIEW_INVITATION`，best-effort，不阻塞完成）。

- `POST /api/orders/{orderId}/review`（S20 起）
  - request: `{ "rating": 5, "comment": "很准时" }`
  - response: `OrderReview`
  - **资格/鉴权（服务端权威）**：订单必须 `COMPLETED`（否则 `409 REVIEW_ORDER_NOT_COMPLETED`）；只有该订单乘客（`X-User-Id` == riderId）可评价（否则 `403 REVIEW_FORBIDDEN`）；`rating` 必须 1-5（`400 REVIEW_RATING_INVALID`）、`comment` ≤ 500 字（`400 REVIEW_COMMENT_TOO_LONG`）；**每订单只能评价一次**（DB `order_id` 唯一键 + 预检，重复 `409 REVIEW_ALREADY_SUBMITTED`）；写审计（`ORDER_REVIEW_SUBMITTED`）。
  - persistence: MySQL `order_reviews`，`order_id` 唯一。

- `GET /api/orders/{orderId}/review`
  - response: `OrderReview`；无评价时 `404 REVIEW_NOT_FOUND`。

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

> 说明：`simulations` / `simulate-success` 为 S11 之前的旧入口（直接标记成功），在 S15 H5 切换到 Payment Intent 之前保留，不要删除。新流程见下。

### Payment Intent（S11 起）

- `POST /api/payments/intents`
  - request: `{ "orderId": "order-1", "idempotencyKey": "intent-001" }`
  - response: `PaymentIntent`（`intentId`、`status`、`amount`、`provider`、`providerRef` 等）
  - behavior: 金额从 Order 服务读取（不信任客户端）；仅订单本人可发起（否则 `PAYMENT_FORBIDDEN`）；按 `(orderId, idempotencyKey)` 幂等；Provider 由 `providers.payment.type` 选型，找不到实现时 `PAYMENT_PROVIDER_UNCONFIGURED` fail-closed。Demo Provider 创建的 intent 初始为 `REQUIRES_PAYMENT`，最终结局只能由下方签名回调驱动。

- `GET /api/payments/intents/{intentId}`
  - response: `PaymentIntent`

### 签名回调摄取（S12 起）

- `POST /api/payments/callbacks/{provider}`
  - 面向支付供应商（PSP）的 Webhook 入口，也是 payment intent 走向终态的**唯一**路径（没有前端后门）。
  - **鉴权**：Gateway 对该路径放行（不需要 JWT，PSP 没有 token），真实性完全由 HMAC 签名保证；仍按客户端 IP 限流、仍会剥离伪造的 `X-User-Id`/`X-User-Roles`。
  - headers: `X-Payment-Timestamp`（epoch 秒）、`X-Payment-Nonce`（一次性）、`X-Payment-Signature`（hex）。
  - 签名算法：`HMAC-SHA256(secret, "{timestamp}.{nonce}.{rawBody}")`，密钥来自环境变量 `PAYMENT_WEBHOOK_SECRET`（`providers.payment.webhook-secret`）。
  - request body: `{ "eventId": "evt-1", "intentId": "pi-1", "outcome": "SUCCEEDED|FAILED|CANCELED|EXPIRED" }`
  - response: `{ "intentId": "pi-1", "status": "SUCCEEDED" }`
  - behavior: 签名校验 -> 时间戳新鲜度窗口（默认 5 分钟）-> nonce 去重（Redis/内存，跨请求防重放）-> 按 `event_id` 幂等落库 -> 通过 `PaymentIntentStateMachine` 迁移（终态不可被后续/乱序回调覆盖）-> 迁移到 `SUCCEEDED` 时调用 order-service `markPaid`（本身幂等）。
  - 错误码：`PAYMENT_WEBHOOK_UNCONFIGURED`（未配置密钥，503）、`PAYMENT_CALLBACK_SIGNATURE_INVALID`（401）、`PAYMENT_CALLBACK_TIMESTAMP`（超出窗口，401）、`PAYMENT_CALLBACK_REPLAY`（nonce 重放，409）、`PAYMENT_CALLBACK_MALFORMED`（400）、`PAYMENT_INTENT_NOT_FOUND`（404）。

### Demo 支付控制台（S13 起，仅 demo profile）

- `POST /api/demo/control/payment/{intentId}/callbacks`
  - **仅 demo profile**：`DemoEndpoints.requireControl()` 双重闸门（`app.demo-mode` + `app.demo.control-enabled`），非 demo 环境返回 `404 DEMO_ENDPOINT_DISABLED`；Gateway 额外要求 OPERATOR/ADMIN（`/api/demo/control/**`）。
  - request: `{ "outcome": "SUCCEEDED|FAILED|CANCELED|EXPIRED", "mode": "NORMAL|DUPLICATE|OUT_OF_ORDER", "delaySeconds": 0 }`（`mode` 默认 `NORMAL`；`delaySeconds` 把签名时间戳回拨，用来演示新鲜度窗口，超窗会被摄取管道拒绝）。
  - response: `{ "intentId": "pi-1", "finalStatus": "SUCCEEDED", "emissions": [ { "eventId": "...", "outcome": "SUCCEEDED", "accepted": true, "resultStatus": "SUCCEEDED", "rejectionCode": null } ] }`
  - behavior: 运营选定结局后，服务端**自签一个合法回调并真正走 S12 的验签/防重放/状态机摄取管道**（不是后门直接改状态）。`DUPLICATE` 重复同一 `eventId`（演示幂等）、`OUT_OF_ORDER` 追加一个冲突终态（演示终态不可覆盖）；每次投递的 accepted/结果状态/拒绝码都回报给运营，管道的各类保护是可观测的。
  - 错误码：`PAYMENT_CALLBACK_OUTCOME_INVALID`（outcome 非终态，400）、`PAYMENT_INTENT_NOT_FOUND`（404）；单次投递被管道拒绝时不抛错，而是在对应 emission 的 `rejectionCode` 里体现（如 `PAYMENT_CALLBACK_TIMESTAMP`）。

## Identity（实名认证 + 活体，S16 起，仅 Demo Provider）

- `POST /api/identity/verifications`
  - request: `{ "realName": "张三", "idNumber": "1101...", "idempotencyKey": "idv-001" }`
  - response: `IdentityVerification`（`verificationId`、`status`、`livenessStatus`、`provider`、`providerRef` 等）
  - behavior: 以网关注入的 `X-User-Id` 为认证人；证件号服务端脱敏后**不落库、不进日志**；按 `(userId, idempotencyKey)` 幂等（默认 key=`idv-<userId>`）。Demo Provider 创建的会话初始 `status=PENDING`/`livenessStatus=PENDING`，**结局不在创建时决定**，由运营 Demo 控制台驱动，结果异步投递到收件箱。Provider 由 `providers.identity.type` 选型，未配置时 `IDENTITY_PROVIDER_UNCONFIGURED` fail-closed。

- `GET /api/identity/verifications/{verificationId}`
  - response: `IdentityVerification`
  - behavior: 仅本人可查看（否则 `IDENTITY_FORBIDDEN`）；供 H5 轮询会话/活体状态。

### Demo 实名控制台（S16 起，仅 demo profile）

- `POST /api/demo/control/identity/{verificationId}/liveness`
  - request: `{ "outcome": "PASSED|FAILED|TIMEOUT|RETRY_REQUIRED" }` · response: `IdentityVerification`
- `POST /api/demo/control/identity/{verificationId}/session`
  - request: `{ "outcome": "APPROVED|REJECTED|TIMEOUT|RETRY_REQUIRED" }` · response: `IdentityVerification`
  - behavior: `DemoEndpoints.requireControl()` 双闸门 + Gateway OPERATOR/ADMIN。经两层状态机（终态不可覆盖，非法迁移 `IDENTITY_ILLEGAL_TRANSITION`）；`APPROVED` 要求活体先 `PASSED`（否则 `IDENTITY_LIVENESS_REQUIRED`）；每个会话结局（非 `PENDING`）异步投递结果到用户收件箱（category `IDENTITY_VERIFICATION_RESULT`），不内联返回。

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

## AI（OCR，S19 起 Provider 化）

OCR 现在经 `OcrProvider` SPI 选型（`providers.ocr.type`，当前 `DemoOcrProvider` 包装 `MockOcrPolicy`），走异步任务生命周期 `SUBMITTED/PROCESSING → COMPLETED`；未配置 Provider 时 `OCR_PROVIDER_UNCONFIGURED` fail-closed。证件号等敏感字段落库/返回前脱敏。

- `POST /api/ai/ocr/mock`（兼容入口，同步）
  - request: `{ "fileObjectId": "file-driving-license-001" }` · response: `OcrResult`
  - behavior: submit + 轮询驱动到完成后返回结果（对旧调用方保持同步语义）。

- `POST /api/ai/ocr/tasks`（S19，异步）
  - request: `{ "fileObjectId": "file-1" }` · response: `OcrTask`（初始 `status=PROCESSING`，`result` 为空）
- `GET /api/ai/ocr/tasks/{taskId}`（S19，轮询）
  - response: `OcrTask`；供应商完成后落库脱敏 `result` 并置 `status=COMPLETED`；未找到任务 `OCR_TASK_NOT_FOUND`。
  - persistence: MySQL `ocr_tasks`（新增 `status`/`provider_ref`/`submitted_at` 列，`result_json`/`completed_at` 在完成前可空）。

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
