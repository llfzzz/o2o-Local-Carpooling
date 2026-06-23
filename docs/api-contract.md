# API Contract

所有外部接口通过 Gateway 暴露，统一前缀为 `/api/**`。当前 `0.2.0-SNAPSHOT` 已将 Users、Driver Verification、Files、AI OCR Mock 任务落到 MySQL；Trips、Orders、Payment Sim、Admin、Audit 仍以 MVP 内存/占位实现为主。

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
- `/api/admin/**`、`/api/audits/**` 要求 `OPERATOR` 或 `ADMIN`。
- Gateway 验证通过后向下游注入 `X-User-Id`、`X-User-Roles`、`X-Trace-Id`，并移除客户端传入的同名伪造头。
- `401`、`403`、`429` 统一返回 `ApiError`，响应头带 `X-Trace-Id`。
- 默认限流：`/api/auth/**` 每 IP 每 60 秒 20 次；其他 `/api/**` 每 userId 每 60 秒 120 次。`security.rate-limit.backend=redis` 时可切换 Redis 固定窗口计数。

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

- `GET /api/trips?origin=A&destination=B`
  - response: `TripOffer[]`

## Orders

- `POST /api/orders`
  - request: `{ "tripId": "trip-1", "riderId": "user-1", "seats": 1, "unitPriceCents": 2000 }`
  - response: `OrderDetail`

- `POST /api/orders/{orderId}/pay`
  - response: `OrderDetail`

- `POST /api/orders/{orderId}/timeout`
  - response: `OrderDetail`

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

- `GET /api/admin/overview`
- `POST /api/audits`

Admin、Audit 当前仍为适配层占位，后续接入 MongoDB 审计和后台聚合查询。
