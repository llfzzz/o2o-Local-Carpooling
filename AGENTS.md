# O2O Local Carpooling Agent Handoff

Last updated: 2026-06-28 CST
Workspace: `/Users/llfzzz/Desktop/o2o-Local-Carpooling`

## 项目定位

这是一个企业级 O2O 同城拼车系统。首期目标是做出可运行、可继续扩展的 MVP 基线，覆盖「手机号登录 -> 司机证件审核 -> 车主发布行程 -> 乘客订座 -> 模拟支付 -> 支付超时取消/库存释放 -> 后台复核审计」闭环。

当前版本是 `0.5.0-SNAPSHOT` 地图权威化基线，不是生产可上线版本。现阶段已让「发布行程 -> 服务端路线快照/计价 -> 搜索 -> 幂等下单锁座 -> 模拟支付 -> RabbitMQ 延迟超时取消/库存释放 -> MinIO 私有文件授权 -> MongoDB 审计落库 -> 后台复核」在本地服务形态上可恢复、可继续扩展。

## 总体项目需求

### 角色

- 乘客：手机号登录、路线搜索、查看行程、锁座下单、模拟支付、取消订单、查看我的行程。
- 司机：提交驾驶证/行驶证、等待审核、发布路线、管理座位、确认订单、取消发车。
- 运营：司机审核、订单监控、行程管理、用户管理、文件/OCR 复核、风控审计、系统配置。
- 系统：统一鉴权、限流、幂等、防重复提交、延迟取消、事件投递、状态机校验、日志追踪。

### MVP 范围

- 手机号验证码登录，当前为 Mock。
- RBAC 角色模型：`RIDER`、`DRIVER`、`OPERATOR`、`ADMIN`。
- 司机资质上传与 OCR Mock。
- 后台人工通过/驳回司机认证。
- 车主发布行程，保存路线快照、距离、时长、价格、座位库存。
- 乘客按起终点搜索行程。
- 订单创建锁座、模拟支付、支付超时取消、库存释放。
- MinIO 私有文件上传、完成确认和短时下载授权。
- RabbitMQ TTL/DLX 延迟取消与 Order Outbox。
- MongoDB 审计日志落库和运营检索。

### 非目标

- 首期不接真实支付。
- 首期不接真实实名、活体、人脸核验。
- 首期不做复杂动态拼单调度。
- 首期不做原生 App。
- 首期不把 Mock OCR、Mock 登录、Mock 支付包装成真实生产能力。

## 技术框架

### 后端

技术基线：

- JDK `21`
- Spring Boot `3.5.15`
- Spring Cloud `2025.0.3`
- Spring Cloud Alibaba `2025.0.0.0`
- OpenFeign、Nacos、Gateway、Sentinel
- MySQL、Redis、RabbitMQ、MongoDB、MinIO

模块：

```text
backend/common                领域模型、状态机、事件类型、后端 common foundation
backend/gateway-service       统一入口、路由、限流、鉴权入口
backend/auth-service          手机号登录、Token、角色
backend/user-service          用户资料、角色、账号状态
backend/driver-service        司机资质、车辆、审核状态
backend/trip-service          发车、路线快照、搜索、库存视图
backend/order-service         订座、订单状态机、超时取消
backend/payment-sim-service   模拟支付隔离层
backend/map-service           高德地图适配层和路线快照
backend/file-service          MinIO 文件对象和授权访问
backend/ai-service            OCR Mock 和未来 OCR Provider 适配
backend/admin-service         运营后台聚合接口
backend/audit-service         审计日志和关键事件归档
```

### 前端

技术基线：

- React + TypeScript + Vite
- 设计系统：Free Joy (FJ)，本地包 `packages/fj-ui`，经 `tokens/brand-carpool.css` 重定为同城拼车 teal 主题
- 用户 H5：Free Joy 全量组件
- 运营后台：Free Joy 外壳/卡片/统计/时间线 + 保留 FJ 主题化 Ant Design Table 数据网格
- 服务端请求状态：TanStack Query
- 轻量 UI 状态：Zustand

应用：

```text
apps/user-h5          用户端 H5，地图/路线搜索优先（Free Joy 全量组件）
apps/admin-console    运营后台，高密度表格/筛选/审核流优先（Free Joy 外壳 + FJ 主题化 Ant Table）
packages/fj-ui        Free Joy 设计系统本地副本（tokens + 精选组件），@fj 别名消费
```

### 基础设施

```text
docker-compose.yml          本地 MySQL、Redis、RabbitMQ、MongoDB、MinIO、Nacos
backend/*/db/migration      已落库服务的 Flyway migration
infra/mysql/001_init.sql    早期初始化 SQL 参考，不再由 Compose 自动挂载执行
.env.example                本地环境变量占位，不保存真实密钥
.github/workflows/ci.yml    GitHub Actions 校验
scripts/verify.sh           本地一键校验
docs/                       PRD、架构、API、运维、ADR、产品设计
```

## 已完成

- 已创建 Maven 多模块后端，包含计划中的核心服务模块和 `common` 领域模块。
- 已锁定 Spring 版本线：Boot `3.5.15`、Cloud `2025.0.3`、Alibaba `2025.0.0.0`，并用 ADR 记录。
- 已实现核心领域类型：用户、司机审核、车辆、行程、路线快照、座位库存、订单、支付模拟、文件对象、OCR、审计日志。
- 已实现核心事件类型：司机审核提交、OCR 完成、行程发布、订单创建、支付超时、订单取消、座位释放。
- 已用 TDD 覆盖关键领域规则：订单状态机、座位库存、价格/距离计算、OCR Mock 解析。
- 已提供 REST 控制器，覆盖 Auth、User、Driver、Trip、Order、Payment Sim、Map、File、AI、Admin、Audit 的 MVP API 入口。
- User、Driver Verification、Trip、Order、Payment Sim、File、AI OCR Mock、Map RouteSnapshot 已从内存实现推进到 Spring `JdbcClient` + MySQL/Flyway 持久化。
- 已为 `user-service`、`driver-service`、`trip-service`、`order-service`、`payment-sim-service`、`file-service`、`ai-service`、`map-service` 配置独立 Flyway history table，避免共享 schema 下 migration 版本冲突。
- Trip 服务已拥有 `trips` 和 `trip_seat_locks`，按 `orderId` 幂等锁座/释放座位，库存不足时拒绝锁座。
- Map 服务已拥有 `route_snapshots`，配置 `AMAP_API_KEY` 时调用高德 Web 服务地理编码 + 路径规划 2.0 驾车接口，未配置时明确返回 `amap-mock` fallback，并保存脱敏供应商响应快照。
- Trip 发布行程已改为调用 Map 服务获取服务端 `RouteSnapshot` 后计价，旧客户端传入的 `distanceMeters`、`durationSeconds` 兼容接收但不参与价格和路线快照。
- Order 服务已拥有 `orders` 和 `order_outbox_events`，按 `(rider_id, idempotency_key)` 防重复下单，从 Trip 服务读取服务端价格并锁座；支付超时主路径为 Outbox 发布 RabbitMQ TTL/DLX 延迟消息，定时扫描保留为兜底对账。
- Payment Sim 服务已拥有 `payment_simulations`，按 `(order_id, idempotency_key)` 防重复模拟支付，从 Order 服务读取金额，不信任前端传价。
- Admin 服务 `GET /api/admin/dashboard` 已从 Driver Verification 和 Order admin metrics 聚合真实 MVP 数据。
- 已实现手机号 AES-GCM 字段加密落库；Mock OCR 证件号字段落库前脱敏。
- File 服务已实现 MinIO 私有文件 presigned upload/download、上传完成 `statObject` 确认、owner/operator/admin 授权和 `mock-upload` 兼容入口。
- Audit 服务已使用 MongoDB `audit_logs` 落库，支持追加和按 target/action/actor 分页检索，`AuditLog` 带 traceId。
- Driver 审核通过/驳回、Order 支付成功/超时取消、File 上传完成/下载授权已通过 best-effort Feign 写入审计服务。
- 已配置各服务 `application.yml`，包括端口、Nacos discovery、Sentinel dashboard、Actuator 暴露项。
- 已实现 React H5 用户端，包含 Mock 登录、发布示例行程、城市提示 + 服务端路线快照计价、真实 API 路线搜索、行程卡片、订座/支付模拟、真实文件选择 + MinIO presigned upload + complete 后提交司机认证。
- 已实现 React 运营后台，包含 Operator Mock 登录、真实后台聚合指标、司机审核列表/证件短时下载链接/通过/驳回、订单监控。
- 已配置 pnpm workspace、Vite、TypeScript 严格类型检查和生产构建。
- 已引入 Free Joy (FJ) 设计系统到 `packages/fj-ui`（tokens + 精选组件 + `.d.ts`），通过 `@fj` 别名 + Vite `resolve.dedupe` 消费；用户 H5 全量改用 FJ 组件，运营后台改用 FJ 外壳/卡片/统计/时间线并保留 FJ 主题化 Ant Table（以 `DataTablePanel` 隔离，便于后续换 FJ DataGrid）；FJ accent 经 `tokens/brand-carpool.css` 重定为 `#137A63` teal。两端 `pnpm typecheck`/`pnpm build` 通过，本地 dev HTTP 200，并完成移动端(412px)/桌面端(1440px)浏览器截图回归。
- 已修正前端类型检查为 `tsc --noEmit`，避免 `.js` 和 `.d.ts` 产物污染源码目录。
- 已提供 Docker Compose 中间件骨架；MySQL 表结构由已落库服务的 Flyway migration 管理。
- 已提供项目文档：`docs/PRD.md`、`docs/product-design.md`、`docs/architecture.md`、`docs/api-contract.md`、`docs/operations.md`、`docs/adr/0001-spring-cloud-2025-boot-35.md`。
- 已从 `llfzzz/RTT` 复用 Backend Foundation 形态到 `backend/common`，新增 Boot 3 自动配置、`X-Trace-Id` 过滤器、结构化 `ApiError`、`BusinessException`、全局 MVC 异常处理和对应单元测试。
- 已完成 Slice 3 平台安全基线：`JwtTokenService` 使用 HS512 签名 JWT，`SecurityPrincipal` 统一 userId/角色表达，Gateway 实现 Bearer token 校验、`/api/admin/**`、`/api/audits/**`、`/api/orders/admin/**` RBAC、WebFlux `ApiError` 写出、`X-Trace-Id` 透传和客户端伪造头清理。
- 已实现 Gateway 固定窗口限流：默认 `/api/auth/**` 每 IP 每 60 秒 20 次，其他 `/api/**` 每 userId 每 60 秒 120 次；本地默认内存实现，配置 `security.rate-limit.backend=redis` 时切 Redis Lua 计数实现。
- Gateway 已补 `/api/payments/**` 路由、本地 Vite CORS 和 `OPTIONS` 预检放行；Map 同时兼容 `/api/map/**` 和 `/api/maps/**`。
- Auth 登录已从 `mock.jwt.*` 改成真实签名 JWT；Mock 登录仍明确是 Mock，默认角色为 `RIDER`、`DRIVER`，请求体可选 `roles` 仅用于 MVP/本地测试。
- 已提供 CI 配置和本地 `scripts/verify.sh`。
- 已验证 `./scripts/verify.sh` 通过：后端测试、前端类型检查、前端生产构建全部通过。
- 已验证 H5 和后台 Vite 服务可本地返回 HTTP 200：
  - H5: `http://127.0.0.1:5173/`
  - Admin: `http://127.0.0.1:5174/`
- 已初始化 Git 仓库并推送到 `https://github.com/llfzzz/o2o-Local-Carpooling.git` 的 `main` 分支，初始提交为 `318b282`。

## 未完成

- `infra/mysql/001_init.sql` 仍是早期参考 SQL，后续新增表应优先进入各服务 Flyway migration。
- Auth 当前仍是 Mock 短信验证码，尚未接短信服务、刷新 Token、会话失效或生产级用户登录校验；`roles` 请求字段只能用于 MVP/本地测试。
- Driver 文件上传已打通 MinIO presigned upload/download 和短时授权，但尚未做文件病毒扫描、内容类型深度校验、对象生命周期策略或文件访问审计强一致投递。
- OCR 当前是 Mock，已持久化 Mock OCR 任务，但尚未接真实 OCR Provider，也没有异步 OCR 任务队列。
- Map 当前已接高德 Web 服务基础适配和响应快照落库，但尚未做路线缓存、供应商限流/熔断、备用供应商、批量路线、途经点、车牌限行策略或 JS 地图 SDK 展示。
- Order 当前已接 RabbitMQ TTL/DLX 延迟队列和 Outbox；但尚未做 Outbox 分片、后台补偿控制台、死信告警或跨服务 Saga 编排。
- Payment Sim 当前仍是 Mock 支付，没有真实支付单生命周期、回调签名、退款或真实资金状态。
- Audit 当前已写入 MongoDB 并接入 MVP 关键事件；但业务审计为 best-effort Feign，尚未用服务本地 Outbox 保证审计投递。
- Admin 当前已有 dashboard 聚合、司机审核和订单监控，但用户管理、行程管理、审计检索页面、风控配置仍未落地。
- Testcontainers、契约测试、Playwright E2E、安全测试、性能压测尚未落地。
- 当前机器有 `docker` CLI，`docker compose config --quiet` 通过；但 Docker daemon 未运行，报 `unix:///Users/llfzzz/.docker/run/docker.sock` 不存在，因此本轮未实际启动 Compose 或做 Gateway curl smoke。
- 当前已有 GitHub 远端和 `main` 提交，但尚未建立长期分支策略或 PR 记录。

## 已优化

- 技术版本按官方兼容线保守选择，避免首期直接上 Boot 4。
- 领域规则先写测试再实现，订单状态机和库存规则有单元测试兜底。
- 路线距离、价格、座位库存、订单状态的权威性原则已写入 PRD 和领域代码，Trip 发布不再信任前端传入的距离/时长。
- 外部密钥通过环境变量读取，`.env.example` 只保存占位值。
- 数据库结构预留了幂等键、版本号、审计/Outbox、核心索引。
- 手机号、车牌等敏感字段在 SQL 中预留为加密存储类型。
- Users、司机审核、Trip/Order/Payment Sim、文件对象、OCR Mock 任务、Map RouteSnapshot 已进入服务自有 Flyway migration。
- 手机号字段已通过服务端 AES-GCM 加密后存储，Mock OCR 证件号已做落库脱敏。
- 前端分成 H5 和运营后台两个应用，避免用户端与运营端交互模型混杂。
- 前端使用 TanStack Query 管理服务端状态，Zustand 只承载轻量本地状态。
- 本地校验统一收口到 `scripts/verify.sh`，CI 与本地命令保持一致。
- 文档已经覆盖产品、PRD、架构、API、运维和版本 ADR。
- 后端 MVC 服务和 Gateway/WebFlux 已通过 common foundation 统一基础 traceId 响应头和结构化异常响应模型。
- Order 延迟取消主路径已从定时扫描推进到 Outbox + RabbitMQ TTL/DLX，超时消费只取消 `PENDING_PAYMENT`，已支付订单幂等忽略。
- File 私有对象已以服务端 object key、短时 URL 和 owner/operator/admin 授权为准，前端不再决定对象路径。
- Audit 已从占位 Controller 推进到 MongoDB 落库和查询，Gateway 精确 `/api/audits` 根路径也已纳入 OPERATOR/ADMIN RBAC。
- Map 已从文本长度 Mock 推进到高德 Web 服务适配；未配置 Key 时仍保留明确命名的 `amap-mock` fallback，避免把 Mock 包装成真实地图能力。
- 前端已统一到 Free Joy 设计系统 token（颜色/排版/间距/圆角/阴影/动效），由 `tokens/brand-carpool.css` 单点重定 teal 品牌色；运营后台保留的 Ant Table 经 antd `ConfigProvider` 主题对齐 FJ token，用户端与运营端视觉语言统一。

## 未优化

- 运营后台已做 vendor 级代码分包（app chunk ~18 KB，react/tanstack/vendor 分离，最大 vendor ~646 KB 含 Ant Table）；后续随 FJ DataGrid 替换 Ant Table 进一步收敛，并可做路由级懒加载。
- 前端已接 MVP Gateway API 和文件上传/下载流，但仍是手写 fetch 客户端，尚未接 OpenAPI 类型生成、统一重试策略和全局错误边界。
- H5 地图区域仍是产品占位，不是真实地图 SDK 或 WebGL/Canvas 地图组件；服务端路线已经可来自高德 Web 服务。
- UI 已完成 FJ 迁移后的移动端(412px)/桌面端(1440px)截图回归，但可访问性检查、Playwright 截图基线、FJ 字体/Lucide 图标自托管（去 CDN 运行时依赖）仍未落地。
- 后端各服务 `application.yml` 有重复配置，后续可抽到 Nacos shared config 或 Spring profile 模板。
- Trip/Order/Payment Sim 已拆出 Repository/Service，但 Driver/File/AI/Admin 等仍需继续清理 Controller、Application Service、Domain Service、Repository 边界。
- 前端尚未统一消费后端 `ApiError` 的企业级错误码、traceId、message、details 格式。
- 日志规范、脱敏日志、Metrics、Tracing 还没有系统化实现；审计已有 MVP 关键事件落库，但还不是强一致审计流水。
- 缓存策略、数据库读写隔离、限流降级、熔断 fallback 还没有真正落地。
- 安全基线已有 JWT/RBAC/限流和文件越权下载单元测试，但还没有系统覆盖所有资源归属越权、重复提交和敏感字段日志脱敏测试。

## 编写规范

### 通用规范

- 所有生产级代码必须以服务端为权威，前端不能决定价格、库存、订单状态、审核状态。
- 不要把 Mock 能力包装成真实能力；Mock 类、Mock 接口和 Mock 数据必须命名明确。
- 不要把真实 API Key、JWT Secret、短信/OCR/支付密钥写入 Git。
- 新增技术栈或替换核心框架前先写 ADR，说明原因、替代方案、风险和回滚方式。
- 改动必须尽量小而清晰，不做无关重构，不混入格式化全仓文件。
- 能用结构化模型和状态机表达的业务规则，不用散落的字符串判断。

### 后端规范

- Java 包名统一使用 `com.o2o.carpooling.<service>`。
- 共享领域类型放在 `backend/common`，但不要把具体服务的基础设施细节塞进 `common`。
- 订单、库存、审核状态迁移必须通过明确的领域方法或状态机，不允许接口直接覆盖状态。
- 创建订单、支付、审核、文件上传等写操作必须设计幂等键。
- 消费 RabbitMQ 事件必须按 `eventId` 幂等，不能假设消息只投递一次。
- 数据库写操作必须考虑事务边界、并发锁、版本号和重复提交。
- 外部供应商调用必须经过适配层：地图走 `map-service`，OCR 走 `ai-service`，支付走独立 payment adapter。
- Controller 只做协议转换和校验，业务规则放 Application Service 或 Domain Service。
- 新增接口要同步更新 `docs/api-contract.md`，后续接 OpenAPI 后以 OpenAPI 为准。
- 单元测试优先覆盖状态机、库存、价格、权限、幂等和异常状态。

### 前端规范

- 用户端优先保证路线搜索、行程信息、座位状态和支付状态清晰可见。
- 后台优先保证表格密度、筛选、审核动作、异常状态和审计入口。
- 服务端数据用 TanStack Query；纯 UI 状态或当前 tab/筛选草稿可用 Zustand。
- 不在前端硬编码权威价格、库存、审核结论；只能展示服务端返回值或本地 Mock 明确标注。
- UI 文案直接服务用户任务，不写介绍式、营销式、解释式空话。
- 按钮、状态标签、表格列和表单字段要稳定，避免动态内容导致布局跳动。
- 新增页面后至少运行 `pnpm typecheck` 和 `pnpm build`。
- 复杂页面后续要补 Playwright E2E 和移动端截图检查。

### 数据与安全规范

- 手机号、车牌、证件号、支付相关字段必须加密或脱敏存储。
- 文件对象默认私有，前端只拿文件 ID 或短时授权 URL。
- 后台审核、订单取消、支付状态变更、文件访问必须写审计日志。
- 所有外部请求最终必须经过 Gateway `/api/**`。
- 所有日志要带 traceId，禁止把密钥、证件号、完整手机号、完整车牌写入日志。

### 验证规范

每次提交前至少运行：

```bash
./scripts/verify.sh
```

或分开运行：

```bash
./mvnw test -DskipTests=false
pnpm install --frozen-lockfile
pnpm typecheck
pnpm build
```

当前本机没有全局 `node`、`npm`、`mvn`，但有 Codex bundled Node/pnpm；`scripts/verify.sh` 已兼容该路径。Maven 通过项目根目录 `./mvnw` 自动下载到 `.mvn-local/`。

## 推荐下一步

1. 前端收尾：FJ 字体/Lucide 图标自托管（去 CDN 运行时依赖），补可访问性与 Playwright 截图基线；按需从 DesignSync 拉取 FJ `DataGrid` 替换运营后台 Ant Table（已用 `DataTablePanel` 隔离），并做运营后台路由级懒加载/代码分包。
2. 为司机审核、文件上传、发布行程、乘客订座、RabbitMQ 超时取消补 Testcontainers/API E2E/Playwright。
3. 把业务审计从 best-effort Feign 升级为服务本地 Outbox + 审计投递重试/死信告警。
4. 继续补资源归属权限校验、重复提交和敏感字段日志脱敏测试。
5. 运营后台补审计检索页面、用户管理、行程管理和风控配置。
6. 继续增强地图能力：路线缓存、供应商熔断/限流、途经点、车牌限行策略、H5 地图 SDK 展示。
7. 引入真实支付适配设计、回调签名、退款/取消生命周期和对账任务。
