# API Contract

所有外部接口通过 Gateway 暴露，统一前缀为 `/api/**`。当前 `0.2.0-SNAPSHOT` 已将 Users、Driver Verification、Files、AI OCR Mock 任务落到 MySQL；Trips、Orders、Payment Sim、Admin、Audit 仍以 MVP 内存/占位实现为主。

## Auth

- `POST /api/auth/sms-code`
  - request: `{ "phone": "13800000000" }`
  - response: `{ "phone": "...", "mockCode": "MOCK-123456", "expiresAt": "..." }`

- `POST /api/auth/login`
  - request: `{ "phone": "13800000000", "code": "123456" }`
  - response: `{ "accessToken": "...", "tokenType": "Bearer", "expiresAt": "...", "user": UserAccount }`

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
