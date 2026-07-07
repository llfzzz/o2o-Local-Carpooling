# API Contract

所有外部接口通过 Gateway 暴露，统一前缀为 `/api/**`。当前 `0.5.0-SNAPSHOT` 已将 Users、Driver Verification、Trips、Orders、Payment Sim、Files、AI OCR Mock、Map RouteSnapshot 任务落到 MySQL/Flyway；Order 已接 Outbox + RabbitMQ TTL/DLX 延迟超时取消主路径；Files 已接 MinIO presigned upload/download；Audit 已接 MongoDB 落库与检索。Map 在配置 `AMAP_API_KEY` 时走高德 Web 服务地理编码 + 驾车路线规划，未配置时明确走 `amap-mock` 本地 fallback。

## Auth

- `POST /api/auth/sms-code`
  - request: `{ "phone": "13800000000" }`
  - response: `{ "phoneMasked": "138****0000", "expiresAt": "...", "message": "..." }`
  - S8 起：验证码**绝不在响应里返回**，投递到 Demo 收件箱；每手机号发送限流、验证失败锁定。

- `GET /api/auth/sms-code/demo-inbox?phone=13800000000`（仅 demo）
  - response: `{ "phoneMasked": "...", "maskedPreview": "...", "code": "401459", "expiresAt": "...", "message": "..." }` — 交互式登录用它取出最新验证码。

- `POST /api/auth/login`
  - request: `{ "phone": "13800000000", "code": "123456" }`（**无 `roles` 字段**——角色服务端权威，S8 修复权限提升漏洞）
  - response: `{ "accessToken": "...", "tokenType": "Bearer", "expiresAt": "...", "refreshToken": "...", "refreshExpiresAt": "...", "user": UserAccount }`
  - behavior: 先服务端校验验证码，再经 user-service 取得/创建用户（新手机号仅 `RIDER`）。`accessToken` 是 HS512 JWT（`sub`/`roles`/`jti`/`iat`/`exp`），30 分钟有效；配合 `POST /api/auth/refresh`（轮换 + 重放检测）、`POST /api/auth/logout`。

- `POST /api/auth/demo/operator-session`（S26，仅 demo）
  - request: `{}` 或 `{ "phone": "13900000000" }` · response: 同 login 的 `AuthToken`（角色 `OPERATOR` + `ADMIN`）
  - behavior: **双重闸门** `DemoEndpoints.requireSeed()`（demo profile + `app.demo.seed-enabled`），非 demo 返回 `404`。一次调用即开通并签发运营会话，供 admin-console 与运营 Demo 控制台使用（替代 S8 删掉客户端 `roles` 后失效的 mock 登录）。

## Gateway Security

- `POST /api/auth/**`、`GET /actuator/health`、`GET /actuator/info` 放行。
- 其他 `/api/**` 必须提供 `Authorization: Bearer <jwt>`。
- `/api/admin/**`、`/api/audits`、`/api/audits/**`、`/api/orders/admin/**`、`/api/demo/control/**`、`GET /api/users`、`GET /api/ai/ocr/tasks`（列表，S29）、`GET /api/drivers/verification-cases`（审核队列，S33）、`POST /api/drivers/verification-cases/{caseId}/approve|reject`（S33）要求 `OPERATOR` 或 `ADMIN`。
- **仅服务间（Feign）可用、Gateway 一律拒绝为 `404`（S33）**：`POST /api/users`、`GET /api/users/{id}`、`POST /api/orders/{id}/pay`、`POST /api/orders/{id}/timeout`、`POST /api/trips/{id}/seat-locks`、`POST /api/trips/{id}/seat-locks/{orderId}/release`、`POST /api/payments/simulations`、`POST /api/payments/simulate-success`。这些端点只由 in-mesh 服务用直连 URL 调用（不经 Gateway）；从外部到达即视为越权，返回 `404`（与「不存在」不可区分），杜绝「不走签名回调直接标记支付」「客户端自选角色提权」「篡改他人订单/库存」等旁路。
- Gateway 验证通过后向下游注入 `X-User-Id`、`X-User-Roles`、`X-Trace-Id`，并移除客户端传入的同名伪造头。
- `401`、`403`、`429` 统一返回 `ApiError`，响应头带 `X-Trace-Id`。
- 未映射路径 / 不支持的方法（S31 起）：服务对不存在的路径返回 `404 NOT_FOUND`、错误方法返回 `405 METHOD_NOT_ALLOWED`、无法解析的 JSON body 返回 `400 MALFORMED_REQUEST`（此前这三类都被兜底成 `500 INTERNAL_ERROR`，曾把「前后端版本不一致」伪装成服务器崩溃）。
- 默认限流：`/api/auth/**` 每 IP 每 60 秒 20 次；其他 `/api/**` 每 userId 每 60 秒 120 次。`security.rate-limit.backend=redis` 时可切换 Redis 固定窗口计数。
- CORS（S24 按环境）：demo 默认放行本地 Vite 源（`http://127.0.0.1:5173`/`5174` 及 localhost），每个源可用 `GATEWAY_CORS_ORIGIN_1..4` 覆盖，staging/prod 换真实源；`OPTIONS` 预检不要求 Bearer token。
- 安全响应头（S24，`default-filters`）：每个代理响应带 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`、`X-XSS-Protection: 0`、`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`。TLS-ready（`TLS_ENABLED` + keystore），开 TLS 时再加 HSTS。

## Users

- `POST /api/users` · `GET /api/users/{userId}` —— **仅服务间（Feign）调用，Gateway 拒绝外部访问（404，S33）**
  - request: `{ "userId": "user-1", "phone": "13800000000", "roles": ["RIDER"] }` · response: `UserAccount`
  - persistence: MySQL `users`，手机号以字段加密后的 `VARBINARY` 存储。
  - 说明：`upsert` 会写入 `roles`，若外部可达等于允许客户端自选角色（提权）。登录时的建号走 auth-service → user-service 内部 Feign（新号仅 `RIDER`），外部无需、也不允许调用该写接口。

- `GET /api/users`（列表）
  - response: `UserSummary[]`（手机号脱敏），要求 `OPERATOR`/`ADMIN`。

## Driver Verification

- `POST /api/drivers/verification-cases`
  - request: `{ "userId": "user-1", "drivingLicenseFileId": "file-1", "vehicleLicenseFileId": "file-2" }`
  - response: `VerificationCase`
  - **准入门禁（S17）**：以网关注入的 `X-User-Id` 为准（body `userId` 仅本地直连 fallback）；提交前 driver-service 通过 identity-service 内部接口校验该用户实名认证已 `APPROVED`，否则 `403 DRIVER_IDENTITY_NOT_VERIFIED`。这是叠加在既有运营人工复核之上的第一道闸门。
  - persistence: MySQL `driver_verification_cases`，OCR Mock 的证件号字段落库前脱敏。
  - 依赖内部接口 `GET /internal/identity/verifications/status?userId=X`（identity-service，**不经 Gateway 路由**，仅服务间调用；返回 `{ userId, approved }`）。

- `GET /api/drivers/verification-cases` —— **要求 `OPERATOR`/`ADMIN`（S33）**
  - response: `VerificationCase[]`（运营审核队列，跨所有司机）

- `POST /api/drivers/verification-cases/{caseId}/approve` —— **要求 `OPERATOR`/`ADMIN`（S33）**
- `POST /api/drivers/verification-cases/{caseId}/reject` —— **要求 `OPERATOR`/`ADMIN`（S33）**
  - 说明：通过/驳回是运营决定；此前网关未对 `/api/drivers/**` 做角色校验，任何登录用户都能审批，S33 已在网关补精确 method+path 门禁。自助提交 `POST /api/drivers/verification-cases` 仍开放给已实名的司机本人。

## Map

- `GET /api/maps/route?origin=A&destination=B&city=厦门`
- `GET /api/map/route?origin=A&destination=B&city=厦门`
  - response: `RouteSnapshot`
  - behavior: `origin`、`destination` 可传地址文本或 `lon,lat` 坐标；`city` 是可选地理编码提示。
  - provider（S22 起）：按 `providers.map.type` 显式选型（`demo` → `MockRouteProvider`，`providerTrace=amap-mock` 的本地 fallback；`amap` → `AmapRouteProvider`，调用高德 Web 服务地理编码 + 路径规划 2.0 驾车接口）；未配置 Provider 时 `MAP_PROVIDER_UNCONFIGURED` fail-closed。选中 `amap` 但缺 `AMAP_API_KEY` 时**直接失败**（`MAP_ROUTE_QUOTE_FAILED`），不静默降级到 mock。
  - persistence: MySQL `route_snapshots`，保存起终点文本、解析坐标、距离、时长、provider、providerTrace 和脱敏后的供应商响应快照；不会保存 API Key。
  - 推迟项（有意，不阻塞 Demo 验收）：路线缓存、供应商熔断/限流降级、备用供应商切换、途经点、车牌限行策略、H5 真实地图 SDK 展示。

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

- `POST /api/trips/{tripId}/seat-locks` · `POST /api/trips/{tripId}/seat-locks/{orderId}/release` —— **仅服务间（Feign）调用，Gateway 拒绝外部访问（404，S33）**
  - request: `{ "orderId": "order-1", "seats": 1 }` · response: `TripOffer`
  - behavior: 由 Trip 服务按 `orderId` 幂等锁座/释放，库存不足返回冲突类错误。只由 order-service 在下单/取消/超时流程内部调用；外部可达会允许篡改他人订单的座位库存，故不对外暴露。

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

- `POST /api/orders/{orderId}/pay` —— **仅服务间（Feign）调用，Gateway 拒绝外部访问（404，S33）**
  - response: `OrderDetail`
  - behavior: `PENDING_PAYMENT → SEAT_LOCKED`，只由 payment-sim-service 在**验证通过的签名回调**摄取到 `SUCCEEDED` 后调用（幂等）。外部可达等于绕过整条签名回调管道「免费标记已支付」，故不对外暴露。超时取消走 RabbitMQ TTL/DLX → 内部 `expireIfPaymentPending` + 定时扫描兜底（进程内，无对外 HTTP 入口；原 `POST /api/orders/{id}/timeout` 无消费者，S33 已删除）。

- `POST /api/orders/{orderId}/cancel`（S14 起；S29 起支持可选取消原因）
  - request（可选）: `{ "reason": "乘客投诉司机爽约" }`（≤200 字，超长 `400 ORDER_CANCEL_REASON_TOO_LONG`；空 body 兼容）
  - response: `OrderDetail`
  - **鉴权（服务端权威）**：从网关注入的 `X-User-Id` / `X-User-Roles` 解析发起人——订单本人 → `USER_CANCELLED`、行程司机 → `DRIVER_CANCELLED`、OPERATOR/ADMIN → `OPERATOR_CANCELLED`；三者都不是则 `403 ORDER_CANCEL_FORBIDDEN`。
  - behavior: 仅 `PENDING_PAYMENT` 或已支付的 `SEAT_LOCKED` 可取消，经 `OrderStateMachine` 迁移并释放 Trip 座位（按 `orderId` 幂等）；对同一发起人重复取消是幂等 no-op；写审计（`ORDER_CANCELLED_BY_{USER|DRIVER|OPERATOR}`，`reason` 存审计 metadata，不落 orders 行）。已支付订单的退款是真实供应商职责，Demo 阶段不涉及。

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

> S11 之前的旧入口 `POST /api/payments/simulations` / `simulate-success`（直接标记订单已支付）已于 **S33 删除**：H5 自 S15 起改用下面的 Payment Intent + 签名回调流程，旧入口无任何消费者，且绕过签名回调直接「标记已支付」本身是支付旁路。控制器/服务/仓库/`PaymentSimulation`/`PaymentStatus` 已一并移除；Flyway `V1__create_payment_simulations.sql` 保留（已在库中执行，删除会破坏迁移历史），对应 `payment_simulations` 表不再写入。

### Payment Intent（S11 起）

- `POST /api/payments/intents`
  - request: `{ "orderId": "order-1", "idempotencyKey": "intent-001" }`
  - response: `PaymentIntent`（`intentId`、`status`、`amount`、`provider`、`providerRef` 等）
  - behavior: 金额从 Order 服务读取（不信任客户端）；仅订单本人可发起（否则 `PAYMENT_FORBIDDEN`）；按 `(orderId, idempotencyKey)` 幂等；Provider 由 `providers.payment.type` 选型，找不到实现时 `PAYMENT_PROVIDER_UNCONFIGURED` fail-closed。Demo Provider 创建的 intent 初始为 `REQUIRES_PAYMENT`，最终结局只能由下方签名回调驱动。

- `GET /api/payments/intents/{intentId}`
  - response: `PaymentIntent`
  - behavior（S31 起）：仅付款人本人或 OPERATOR/ADMIN 可读（intent 含 orderId/riderId/金额）；其他用户 `403 PAYMENT_FORBIDDEN`。角色取 Gateway 注入的 `X-User-Roles`，头缺失（服务间/本地调用）时放行，与 `createIntent` 的既有契约一致。

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

- `GET /api/demo/control/payment/intents?orderId=&limit=20`（S29，控制台列表）
  - response: `PaymentIntent[]`（最新在前；`orderId` 可选过滤；`limit` 1..100）
  - behavior: 同一双闸门 + Gateway OPERATOR/ADMIN；只读，供运营台选定要驱动的 intent，不再需要 curl 查库。

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

- `GET /api/demo/control/identity/verifications?limit=20`（S29，控制台列表）
  - response: `IdentityVerification[]`（最新在前；`limit` 1..100）
  - behavior: 双闸门 + Gateway OPERATOR/ADMIN；只读，供运营台选定要驱动的认证会话。

- `POST /api/demo/control/identity/{verificationId}/liveness`
  - request: `{ "outcome": "PASSED|FAILED|TIMEOUT|RETRY_REQUIRED" }` · response: `IdentityVerification`
- `POST /api/demo/control/identity/{verificationId}/session`
  - request: `{ "outcome": "APPROVED|REJECTED|TIMEOUT|RETRY_REQUIRED" }` · response: `IdentityVerification`
  - behavior: `DemoEndpoints.requireControl()` 双闸门 + Gateway OPERATOR/ADMIN。经两层状态机（终态不可覆盖，非法迁移 `IDENTITY_ILLEGAL_TRANSITION`）；`APPROVED` 要求活体先 `PASSED`（否则 `IDENTITY_LIVENESS_REQUIRED`）；每个会话结局（非 `PENDING`）异步投递结果到用户收件箱（category `IDENTITY_VERIFICATION_RESULT`），不内联返回。

## Notification / Demo Delivery Center（S4/S5 起，仅 demo profile）

- `GET /api/demo/inbox?limit=50` · `POST /api/demo/inbox/{deliveryId}/reveal` · `POST /api/demo/inbox/{deliveryId}/read`
  - **用户收件箱**：严格按 Gateway 注入的 `X-User-Id` 归属——只能看/取自己的投递；列表只含脱敏预览，敏感值只能经显式 `reveal`（TTL 内）取出，reveal 动作留痕。双闸门 `app.demo.inbox-enabled`。

### Demo 通知控制台（仅 demo profile，Gateway OPERATOR/ADMIN）

- `GET /api/demo/control/notification/deliveries?limit=20`（S29，控制台列表）
  - response: `DeliveryRecord[]`（跨用户、最新在前；**只含脱敏预览，绝不含可 reveal 的敏感载荷**）。
- `POST /api/demo/control/notification/{deliveryId}/status`
  - request: `{ "status": "DELIVERED|FAILED|RETRYING|READ" }` · response: `{ "deliveryId": "...", "status": "..." }`
  - behavior: 模拟渠道侧投递结果（`RETRYING` 会累加 `retry_count`）；未知投递 `404 DELIVERY_NOT_FOUND`。

## Files

- `POST /api/files/presign-upload`
  - request: `{ "objectName": "license.png", "contentType": "image/png", "contentLength": 20480 }`
  - fallback request field for local direct tests: `ownerId`
  - response: `{ "fileObject": FileObject, "uploadUrl": "...", "method": "PUT", "requiredHeaders": { "Content-Type": "image/png" }, "expiresAt": "..." }`
  - behavior: Gateway 透传 `X-User-Id` 时以当前用户为 owner；服务端生成 MinIO object key，不信任前端传入路径。
  - **上传加固（S25）**：`contentType` 必须在白名单内（默认 `image/jpeg,image/jpg,image/png,image/webp,application/pdf`，`providers`... 实为 `minio.allowed-content-types` 可配），否则 `415 FILE_CONTENT_TYPE_NOT_ALLOWED`；`contentLength`（可选，前端传 `file.size`）超过 `minio.max-upload-bytes`（默认 10 MiB）则 `413 FILE_TOO_LARGE`。

- `POST /api/files/{fileId}/complete`
  - request: `{ "ownerId": "user-1" }` only for local direct tests
  - response: `FileObject`
  - behavior: owner/operator/admin 可完成；服务端通过 MinIO `statObject` 确认对象存在后标记 `AVAILABLE`。
  - **权威大小校验（S25）**：complete 时读 `statObject().size()`，实际对象超过 `max-upload-bytes` 则 `413 FILE_TOO_LARGE`（客户端在 presign 谎报大小也拦得住）。

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
- `GET /api/ai/ocr/tasks?limit=20`（S29，任务列表）
  - response: `OcrTask[]`（最新在前；`limit` 1..100）
  - **鉴权**：OCR 任务不归属单个用户，该列表在 Gateway 要求 OPERATOR/ADMIN（精确匹配 `GET /api/ai/ocr/tasks`；单任务 `GET /{taskId}` 与提交不受影响）。供运营台的「OCR 任务」页使用。

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
