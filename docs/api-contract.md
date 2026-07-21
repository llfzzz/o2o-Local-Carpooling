# API Contract

所有外部接口通过 Gateway 暴露，统一前缀为 `/api/**`。当前 `0.5.0-SNAPSHOT` 已将 Users、Driver Verification、Trips、Orders、Payment Sim、Files、AI OCR Mock、Map RouteSnapshot 任务落到 MySQL/Flyway；Order 已接 Outbox + RabbitMQ TTL/DLX 延迟超时取消主路径；Files 已接 MinIO presigned upload/download；Audit 已接 MongoDB 落库与检索。Map 在配置 `AMAP_API_KEY` 时走高德 Web 服务地理编码 + 驾车路线规划，未配置时明确走 `amap-mock` 本地 fallback。

## Auth

- `POST /api/auth/sms-code`
  - request: `{ "phone": "13800000000" }`
  - response: `{ "phoneMasked": "138****0000", "challengeId": "chg-…", "expiresAt": "...", "message": "..." }`
  - 验证码**绝不在响应里返回**；每手机号发送限流、验证失败锁定。响应返回一个不透明的登录 `challengeId`（不是验证码本身）——demo 登录页凭它取码（见下）。
  - **登录验证码不再进入消息中心**（S46）：demo 模式下明文只存于 auth-service 内一个按 `(phone, challengeId)` 键控的临时 store（Redis/内存），登录成功、锁定或 TTL 到期即删除；生产/staging 下 `DemoSmsProvider` bean 不存在，任何地方都不会存明文。

- `POST /api/auth/sms-code/demo-peek`（仅 demo，S46 起取代 `GET …/demo-inbox`）
  - request: `{ "phone": "13800000000", "challengeId": "chg-…" }`（POST body，手机号不进 URL/访问日志）
  - response: `{ "phoneMasked": "...", "code": "401459", "expiresAt": "...", "message": "..." }`
  - behavior: 仅当 `challengeId` 与该手机号最近一次 `sms-code` 请求匹配时返回验证码；错误或过期的 challenge 与「尚未收到」不可区分（无 oracle）。双重闸门 `DemoEndpoints.requireLoginCodePeek()`（demo profile + `app.demo.login-code-peek-enabled`），非 demo 返回 `404`；每手机号额外限流（10 次 / 5 分钟）。**绝不创建通知/收件箱记录，绝不写日志/审计**。

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
- 普通登录用户（RIDER 起）可达、JWT 保护、非运营专属：`/api/inbox/**`（消息中心，S46）、`/api/conversations/**`（乘客-司机私信，S46）、`/api/demo/trips/**`（demo 虚拟行程生成，S46；服务端 `DemoEndpoints.requireVirtualTrips()` 二次 demo-gate，非 demo 返回 404）。这些不是运营专属，故不在上面的 `requiresOperator` 列表里。
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

### 坐标系约定（S37 起，强约束）

所有坐标都必须显式带 `datum`，不允许出现「裸经纬度对」。浏览器 Geolocation API 产出 **WGS84**，高德（JS API 与 Web 服务）收发 **GCJ02**，两者在国内相差约 500m。

- 存储与传输的规范坐标系是 **GCJ02**（与供应商一致）。
- WGS84 → GCJ02 的转换只发生在一个地方：`MapQueryService`（调用 `common` 的 `CoordinateTransform`）。Provider 内部一律假定 GCJ02。
- 领域类型 `GeoPoint(latitude, longitude, datum)` 在构造时校验经纬度范围并拒绝空 datum；没有任何构造函数接受不带 datum 的坐标对。

### 结构化位置 `LocationRef`

业务流程中的「地点」只有这一种形态；自由文本只能作为解析的**输入**，不能作为地点本身：

```json
{
  "point": { "latitude": 24.4879, "longitude": 118.1781, "datum": "GCJ02" },
  "provider": "amap",
  "providerPlaceId": "B0FFH5V8N9",
  "cityCode": "0592",
  "adcode": "350211",
  "displayName": "软件园三期",
  "formattedAddress": "福建省厦门市集美区软件园三期",
  "source": "AUTOCOMPLETE",
  "accuracyMeters": 35,
  "capturedAt": "2026-07-20T02:00:00Z"
}
```

`source` ∈ `GEOLOCATION | AUTOCOMPLETE | POI_SEARCH | MAP_PIN | MANUAL | DEMO_SEED`。`adcode` 是多城市的主键，城市比较一律按 `adcode` 而非展示文本。

### 端点

- `GET /api/maps/cities`
  - response: `{ "unrestricted": bool, "demoProvider": bool, "cities": [{ "adcodePrefix", "name", "cityCode" }] }`
  - behavior: 支持城市白名单（`map.cities.enabled`，按 adcode 前缀，`3502` 即开放整个厦门）。**空列表 = 不限制**。城市支持是配置，不是代码里的条件分支。`demoProvider` 让前端知道该不该打「演示数据」徽标。

- `POST /api/maps/reverse-geocode` — body `{ "lat", "lng", "datum" }`
  - response: `LocationRef`；`datum` **必填**（不填 400）。用于「使用我的位置」和地图拖拽落点。

- `GET /api/maps/place/suggest?keyword&cityCode&lat&lng&datum&size`
  - response: `LocationRef[]`；高德 input tips 输入联想。没有坐标的行政区联想会被过滤掉（无法作为路线端点）。
- `GET /api/maps/place/search?keyword&cityCode&lat&lng&datum&size`
  - response: `LocationRef[]`；高德 POI 搜索，结果比 suggest 更完整。
  - 两者都按白名单过滤（**过滤**而非报错：一个关键词可能命中多城市，只返回可服务的）。`lat/lng/datum` 是可选的排序偏置点。

- `POST /api/maps/route` — body `{ "origin": LocationRef, "destination": LocationRef }`
  - response: `RouteSnapshot`
  - behavior: 两端已解析，无需地理编码往返；起终点 `adcode` 都要通过白名单校验，否则 `MAP_CITY_NOT_SUPPORTED`。
  - cache: 按 Provider + 两端约 100m 网格读取 `route_snapshots`；默认 30 分钟内为新鲜命中，同 key 的并发 miss 在单实例内合并为一次供应商调用。只缓存结构化接口，旧文本接口不按模糊字符串复用。

- `GET /api/maps/route?origin=A&destination=B&city=厦门` · `GET /api/map/route?...`
  - **旧文本形态，保留兼容**，由 Provider 先做地理编码。将在结构化迁移收尾时移除。

- `GET /internal/maps/demo-places?cityCode=0592` —— **仅服务间调用，不经 Gateway 路由**（S46）
  - response: `LocationRef[]`（该城市的 fixture 地点）。**仅当 demo map provider 激活时存在**，否则 `404 MAP_DEMO_PLACES_UNAVAILABLE`——对真实供应商随机取两点没有意义且会烧配额。供 trip-service 的「随机路线」虚拟行程功能使用。

### `RouteSnapshot`

在原有 `routeId / distanceMeters / durationSeconds / providerTrace` 之上新增三个**可空**字段：`polyline`（供应商路线几何，供 H5 绘制）、`origin` / `destination`（`LocationRef`）。旧文本形态的报价这三项为 null。

### Provider 与失败语义

按 `providers.map.type` 显式选型（`demo` → `DemoMapProvider`，`providerTrace=amap-mock`；`amap` → `AmapMapProvider`，调用高德 Web 服务地理编码/逆地理编码/input tips/POI 搜索/路径规划 2.0）；未配置 Provider 时 `MAP_PROVIDER_UNCONFIGURED` fail-closed。选中 `amap` 但缺 `AMAP_API_KEY` 时**直接失败**（`MAP_ROUTE_QUOTE_FAILED`），不静默降级到 demo。

真实 Provider 受 Resilience4j 熔断保护：默认 20 次滑动窗口、至少 10 次调用、50% 失败率打开、30 秒后半开探测 3 次；连接/读取超时默认 2s/5s，不做自动重试。结构化路线在临时供应商故障或熔断打开时，可以使用 24 小时内的历史**真实**路线，`providerTrace` 追加 `-stale-cache`；没有合格缓存则 `503 MAP_PROVIDER_UNAVAILABLE`。缺 Key、Key/权限配置错误、非法参数和城市白名单拒绝都不允许被旧缓存掩盖，也不计入临时故障熔断；联想、POI、逆地理编码没有旧结果降级；任何环境都不会把 Demo 数据当真实 Provider 的 fallback。

`DemoMapProvider` 使用跨 4 个不相关城市（厦门/北京/成都/哈尔滨）的固定 fixture 集，距离为直线 × 1.3 道路系数——确定性、可复现，且每条结果都带 `provider="demo"`，前端据此打徽标。未命中 fixture 的文本返回 `MAP_DEMO_LOCATION_UNKNOWN`，不会凭空编造地点。

- persistence: MySQL `route_snapshots`，除原有字段外保存 `polyline`、`coordinate_datum`、起终点 `adcode`/`cityCode`/`placeId` 和 `cache_key`（坐标取 3 位小数的去重键）；不会保存 API Key（响应快照脱敏）。
- metrics: `map.route.cache.hits`（fresh/stale）、`map.provider.failures`、`map.provider.circuit.rejected`、`map.provider.circuit.state`。
- 推迟项（有意）：第二家真实地图供应商、途经点、车牌限行策略。当前备用策略是有界的历史真实路线，绝不使用 Demo Provider。

## Trips

- `POST /api/trips`
  - request: `{ "originText": "A", "destinationText": "B", "city": "厦门", "departureAt": "...", "totalSeats": 3, "idempotencyKey": "publish-001" }`
  - response: `TripOffer`
  - **身份绑定（S37）**：司机身份取自网关注入的 `X-User-Id`，**不再取自请求体**。请求体里的 `driverId` 兼容接收但**一律忽略**（旧行为允许任何登录用户以他人身份发布行程，属 `docs/security.md` 记录的缺口，现已关闭）。无 `X-User-Id` 时 `401 AUTH_REQUIRED`。
  - **司机准入（S37）**：发布前经 driver-service 内部接口 `GET /internal/drivers/{userId}/capability` 校验「实名 APPROVED **且** 证件审核 APPROVED」，否则 `403 DRIVER_NOT_APPROVED`。客户端持有 `DRIVER` 角色声明**不构成**准入证明。该校验在调用地图 Provider **之前**执行，被拒的发布不消耗供应商配额。
  - **幂等（S37）**：`idempotencyKey` 按 `(driver_id, idempotency_key)` 唯一；重复提交返回同一条 `TripOffer`，且不会重复请求路线报价。此前发布是唯一没有幂等键的创建类写操作。
  - behavior: Trip 服务调用 Map 服务获取服务端 `RouteSnapshot`，再按 `PricingPolicy` 计价；旧客户端传入的 `distanceMeters`、`durationSeconds` 兼容接收但会被忽略。
  - persistence: MySQL `trips`，服务端生成 `tripId`，保存 Map 服务返回的 `routeId`、距离、时长、providerTrace，初始 `lockedSeats=0`。

- `GET /internal/drivers/{userId}/capability` —— **仅服务间调用**，不在 `/api/**` 下，网关不路由
  - response: `{ "userId", "approved", "identityApproved", "documentsApproved" }`
  - behavior: `approved = identityApproved && documentsApproved`。与 `InternalIdentityController` 同构。

- `GET /api/trips/search?originLat&originLng&destinationLat&destinationLng&datum&departAt&minSeats`
  - response: `TripOffer[]`，按「绕路最少」排序
  - **地理匹配（S37）**：起点在乘客起点 `trip.matching.origin-radius-meters`（默认 3000m）内、终点在 `destination-radius-meters`（默认 5000m）内、发车时间在 `departure-window`（默认 ±2h）内、且剩余座位 ≥ `minSeats`。排序按「起点距离 + 终点距离 + 时间差惩罚」，距离占主导、时间差只做同距离时的次要排序。
  - 实现：SQL 里先按索引 `idx_trips_geo` 做外接矩形预筛（**只会多选、绝不少选**），再在 Java 里算精确大圆距离并排序。用普通 `DECIMAL(10,7)` 列而非 MySQL SPATIAL，因为仓库层测试跑在 H2 上，`ST_*` 不可用。
  - `datum` 可选，默认 `GCJ02`；传 `WGS84`（浏览器定位）会先转换再比较。
  - 配置项全部可按环境覆盖（`TRIP_MATCH_*`），**不是代码常量**——覆盖稀疏的城市需要更大的半径。
  - 未带结构化坐标的历史行程（迁移前发布的）不参与地理匹配，会被跳过而不是报错。

- `GET /api/trips?origin=A&destination=B` —— **旧文本搜索，保留兼容**
  - response: `TripOffer[]`
  - behavior: `origin_text like '%A%'`。无距离概念、无时间窗、无座位过滤，且前导通配符使索引失效。已被 `GET /api/trips/search` 取代。

- `GET /api/trips/{tripId}`
  - response: `TripOffer`

- `POST /api/trips/route-preview` — body `{ "origin": LocationRef, "destination": LocationRef }`（S46，路线确认）
  - response: `{ "route": RouteSnapshot, "pricing": PriceBreakdown }`
  - behavior: 一次权威 map-service 路线报价 + **与发布行程同一 `PricingPolicy`** 的每座价格明细。这是唯一的乘客侧计价权威——前端**照原样展示** `pricing`，绝不自己算价。因 trip→map Feign 绕过网关地图桶，此接口在服务内额外限流 30/min/用户。端点需已解析的 `LocationRef`，否则 `400 ROUTE_PREVIEW_LOCATION_REQUIRED`。
  - `PriceBreakdown` 字段：`distanceMeters`、`distanceKm`、`baseFare`、`includedKm`、`chargeableKm`、`extraCharge`、`total{amount,currency}`、`currency`（见下「计价公式」）。

- `TripOffer` 新增字段（S46）：`priceBreakdown`（每座明细，迁移前的行为 null）、`source`（`USER` 真实行程 / `DEMO` 演示生成，前端据此打「演示」徽标）。

### 计价公式（S46，服务端权威）

```
fare = max(minFare, baseFare + max(0, distanceKm − includedKm) × extraKilometerFare)
```

- 全程 `BigDecimal`，**money 绝不用浮点**。取整规则（已文档化并测试）：`distanceKm` = 米/1000，scale 3 HALF_UP；`chargeableKm` = max(0, km − includedKm)，scale 3；`extraCharge` = chargeableKm × perKm，scale 2 HALF_UP；`total` = max(minFare, base + extra)，由 `Money` 归一化到 scale 2。
- 配置 `trip.pricing.*`（env `TRIP_PRICING_*`）：`base-fare`（默认 6.00，**含前 `included-km` 公里**）、`included-km`（默认 3.0）、`per-km-fare`（默认 1.20）、`min-fare`（默认 6.00）、`currency`（默认 CNY）。
- 计价组件（base/includedKm/perKm/minFare）在发布时随行落 `trips` 行，因此展示的明细永远与已落库的座位价一致，即使之后改了配置。旧构造函数 `new PricingPolicy(base, perKm)` 保留（included=0、min=0、CNY）。

### 演示虚拟行程（S46，仅 demo profile）

- `POST /api/demo/trips/generate` — body `{ "origin": LocationRef, "destination": LocationRef, "seed": <long?> }`
- `POST /api/demo/trips/random` — body `{ "cityCode": "0592", "seed": <long?> }`（从 `GET /internal/maps/demo-places` 取两个不同 fixture 地点）
  - response: `TripOffer[]`（每条 `source=DEMO`）
  - behavior: 双闸门 `DemoEndpoints.requireVirtualTrips()`（demo profile + `app.demo.virtual-trips-enabled`），非 demo `404`。一次权威 map-service 报价 → **同一 `PricingPolicy` 计价，价格严格公式派生、零浮动**（「派生而非任意」）；每次生成 5 条，仅在发车时刻（+15m..+3h）、座位数（1–4）、合成司机 id `demo-driver-N`（永不可能通过鉴权——auth 只签发 `user-<phone>`）上变化，全部由 `seed`（或端点哈希）确定性驱动。限流 5/min/用户；同端点重复生成**替换**而非累积；小时级 `@Scheduled` 清理发车已过 24h 的演示行程。生成器**故意跳过司机准入校验**（限于该类内，注释说明：端点在非 demo 下 404、司机合成、行标记 DEMO）。
  - request: `{ "orderId": "order-1", "seats": 1 }` · response: `TripOffer`
  - behavior: 由 Trip 服务按 `orderId` 幂等锁座/释放，库存不足返回冲突类错误。只由 order-service 在下单/取消/超时流程内部调用；外部可达会允许篡改他人订单的座位库存，故不对外暴露。

### 接驾途中实时定位（S42）

司机位置**只在行程进行中存在**，且**只对已下单该行程的乘客可见**。位置仅存 Redis（TTL 默认 45s），**不落 MySQL、无轨迹历史**——TTL 同时是「离线信号」和「保留上限」。

- `POST /api/trips/{tripId}/driver-location` — body `{ lat, lng, datum, headingDegrees?, speedMetersPerSecond?, capturedAt? }`
  - **仅该行程的司机**（`X-User-Id` 必须等于 `trip.driverId`），否则 `403 TRIP_NOT_DRIVER`；行程非 `PUBLISHED` 时 `409 TRIP_NOT_TRACKABLE`。司机准入已在发布时校验过，故此热路径**不做跨服务调用**。
  - `datum` 必填；传 `WGS84`（浏览器定位）会转换为 GCJ02 后存储。
  - **合理性校验**：时间戳超前 >30s 或早于 TTL → `400 DRIVER_LOCATION_STALE`；隐含速度 >200km/h → `400 DRIVER_LOCATION_IMPLAUSIBLE`；超过 `trip.tracking.max-updates`/`update-window` → `429 DRIVER_LOCATION_RATE_LIMITED`。这是**合理性**而非证明——慢速伪造仍可绕过，已在 `docs/security.md` 记录。
  - 响应**不回显坐标**（司机本就知道自己在哪），避免精确位置进入代理/访问日志。
- `DELETE /api/trips/{tripId}/driver-location` —— 司机立即停止共享，不等 TTL。
- `GET /api/trips/{tripId}/driver-location` —— 单次读取，供轮询客户端使用。
- `GET /api/trips/{tripId}/driver-location/stream` —— SSE（`text/event-stream`），每 `stream-interval`（默认 3s）推送一次，上限 `stream-max-duration`（默认 30min）。
  - **观看权限**：该行程的司机，**或**持有该行程 `LOCKED` 座位锁的乘客。其余一律 **404（不是 403）**——403 会确认「该行程存在且正在被追踪」。座位释放后立即失去权限。
  - 响应：`{ sharing: bool, lat?, lng?, datum?, capturedAt?, ageSeconds? }`。**未共享或已过期时 `sharing:false` 且不带坐标**——客户端必须渲染为「未知」，绝不能把上一次已知位置当作实时位置展示。
  - nginx 需 `proxy_buffering off`（已在 `deploy/nginx/ai-managed-locations.conf` 配好），否则事件会被缓冲。
  - 注：H5 当前走轮询而非 `EventSource`——浏览器的 `EventSource` **无法携带 Authorization 头**，而该接口是 Bearer 鉴权；把 token 放进 URL 会被日志记录，故未采用。SSE 端点本身可用，供能设置请求头的客户端使用。

- `POST /api/trips/{tripId}/seat-locks` 的 body 新增 `riderId`（S42）：由 order-service 从认证主体解析后传入，落库到 `trip_seat_locks.rider_id`，作为上述观看权限的判定依据。该接口是**内部专用**（网关 404），故 `riderId` 不是客户端输入。

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

## Message Center / 消息中心（S46 起，**生产功能**，非 demo-gated）

用户消息中心，取代了原 demo-gated `/api/demo/inbox`（该端点已删除）。JWT 保护、严格按 Gateway 注入的 `X-User-Id` 归属——只能看/操作自己的消息。敏感载荷默认脱敏，只能经显式 `reveal`（owner + TTL + 审计）取出——脱敏 + 显式 reveal 现在是**生产不变量**。

- `GET /api/inbox?limit=20&cursor=<id>&category=<cat>`
  - response: `{ "items": DeliveryRecord[], "nextCursor": <id|null> }` —— 按数字 id 的 keyset 分页（最新在前），可按 category 过滤。`DeliveryRecord` 含 `linkType`/`linkId`（ORDER/TRIP/PAYMENT/CONVERSATION，驱动消息内的跳转）、`readAt`、`cursor`、`revealable`（是否有可取出的敏感值）。
- `GET /api/inbox/unread-count` → `{ "unread": <n> }`（tab 红点用；与列表独立轮询）
- `POST /api/inbox/{deliveryId}/read` → `{ "deliveryId": "...", "updated": <bool> }`
- `POST /api/inbox/read-all` → `{ "updated": <n> }`
- `POST /api/inbox/{deliveryId}/reveal` → `{ "deliveryId": "...", "value": "...", "revealedAt": "..." }`（owner + 未过期，reveal 留痕不记值；无可取出内容 `404 REVEAL_UNAVAILABLE`）
- **登录验证码永不入库为消息**：`NotificationService.notify` 拒绝 category `AUTH_SMS_CODE`（`400 CATEGORY_NOT_INBOXABLE`），历史行由迁移 `V2` 清除，列表/未读查询再加 `category <> 'AUTH_SMS_CODE'` 兜底。

### 领域事件驱动的通知产生（S46）

订单/行程生命周期的消息由**事务性 outbox**产生，而非到处直连 Feign：order-service 在每次状态迁移的同一事务里写 `order_notification_outbox`，`OrderNotificationOutboxPublisher`（@Scheduled 10s）以 outbox `event_id` 作为 `dedupeKey` 中继到 notification-service（至少一次 + 接收端按 dedupeKey 幂等去重）。覆盖：`ORDER_CREATED`、`ORDER_PAID`、`ORDER_PAYMENT_TIMEOUT`、`ORDER_CANCELLED_BY_{USER,DRIVER,OPERATOR}`、`ORDER_COMPLETED`、`ORDER_REVIEW_INVITATION`、`TRIP_SEAT_LOCKED`、`TRIP_SEAT_RELEASED`。driver-service 审核通过/驳回投递 `DRIVER_VERIFICATION_RESULT`（best-effort，绝不阻塞决定）；trip-service `@Scheduled` 扫描在发车前 `trip.reminder.lead`（默认 30 分钟）向司机与持 `LOCKED` 座位的乘客投递 `TRIP_DEPARTURE_REMINDER`（`departure_reminder_sent_at` 标记 + `tripId:userId:DEPARTURE` dedupeKey 双重防重）。消息体只含最小必要信息（订单/行程 id、座位数、时刻）——**绝不含手机号、姓名、取消原因等 PII**。共享 `NotificationCategory` 枚举（`backend/common`）消除跨服务字符串漂移。

- 内部 `POST /api/notifications`（服务间 Feign，不经 Gateway）新增字段：`linkType`、`linkId`、`dedupeKey`（重复 dedupeKey 是 no-op，返回原回执）。

## 乘客-司机私信 / Chat（S46 起，托管在 notification-service）

会话绑定到一个合法订单（`order_id` 唯一）；参与者身份在会话创建时从**权威订单/行程记录**服务端派生（订单 → riderId + tripId 校验；行程 → driverId），**客户端从不传 userId**。热路径成员校验是本地列长比较。**非参与者在每个端点都得 404 `CONVERSATION_NOT_FOUND`**（与「不存在」不可区分，杜绝会话存在性探测）；无运营端点（v1；仅在明确的支持/审计工作流下才允许，列为后续）。实时用 5s 轮询（同 driver-location 的理由：`EventSource` 无法带 Authorization 头）。

- `POST /api/conversations` `{ "orderId": "order-1" }` → `ConversationView`（create-or-return；已取消/超时的订单 `409 CONVERSATION_UNAVAILABLE`）
- `GET /api/conversations?limit=20` → `ConversationView[]`（按最近活跃排序）
- `GET /api/conversations/unread-count` → `{ "unread": <n> }`
- `GET /api/conversations/{id}/messages?beforeId=<id>&limit=30` → `MessageView[]`（keyset 分页，升序渲染）
- `POST /api/conversations/{id}/messages` `{ "clientMsgId": "...", "body": "..." }` → `MessageView`（`clientMsgId` 幂等：失败重试同 id 不重复；body 1–500 字、拒绝控制字符；发送限流 20/60s/用户，创建 10/60s/用户；发送前按 60s 缓存复核订单未取消）
- `POST /api/conversations/{id}/read` `{ "lastReadMessageId": <id?> }` → `{ "conversationId": "...", "lastReadMessageId": <id> }`
- 隐私：`ConversationView` 只暴露 `myRole` + `counterpartLabel`（司机/乘客），不含对方 userId/手机号；`MessageView` 用服务端算出的 `mine` 布尔而非 sender id。
- 依赖内部 `GET /internal/orders/{orderId}`（order-service，不经 Gateway；返回含 riderId 的完整订单）+ `GET /api/trips/{tripId}`。

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
