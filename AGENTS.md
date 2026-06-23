# O2O Local Carpooling Agent Handoff

Last updated: 2026-06-23 12:25 CST
Workspace: `/Users/llfzzz/Desktop/o2o-Local-Carpooling`

## 项目定位

这是一个企业级 O2O 同城拼车系统。首期目标是做出可运行、可继续扩展的 MVP 基线，覆盖「手机号登录 -> 司机证件审核 -> 车主发布行程 -> 乘客订座 -> 模拟支付 -> 支付超时取消/库存释放 -> 后台复核审计」闭环。

当前版本是 `0.2.0-SNAPSHOT` MySQL 持久化基线，不是生产可上线版本。现阶段重点是架构、模块边界、核心领域规则、部分服务落库和交付规范。

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
- MinIO 私有文件存储设计。
- RabbitMQ 延迟取消设计。
- MongoDB 审计日志设计。

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
- 用户 H5：Ant Design Mobile
- 运营后台：Ant Design + ProComponents
- 服务端请求状态：TanStack Query
- 轻量 UI 状态：Zustand

应用：

```text
apps/user-h5          用户端 H5，地图/路线搜索优先
apps/admin-console    运营后台，高密度表格/筛选/审核流优先
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
- User、Driver Verification、File、AI OCR Mock 已从内存实现推进到 Spring `JdbcClient` + MySQL/Flyway 持久化。
- 已为 `user-service`、`driver-service`、`file-service`、`ai-service` 配置独立 Flyway history table，避免共享 schema 下 migration 版本冲突。
- 已实现手机号 AES-GCM 字段加密落库；Mock OCR 证件号字段落库前脱敏。
- 已新增 `POST /api/files/presign-upload` 兼容入口，当前只创建私有文件元数据，不生成真实 MinIO 预签名 URL。
- 已配置各服务 `application.yml`，包括端口、Nacos discovery、Sentinel dashboard、Actuator 暴露项。
- 已实现 React H5 用户端，包含路线搜索、行程卡片、订座/支付模拟、司机认证状态展示。
- 已实现 React 运营后台，包含业务概览、司机审核、订单监控、运营导航。
- 已配置 pnpm workspace、Vite、TypeScript 严格类型检查和生产构建。
- 已修正前端类型检查为 `tsc --noEmit`，避免 `.js` 和 `.d.ts` 产物污染源码目录。
- 已提供 Docker Compose 中间件骨架；MySQL 表结构由已落库服务的 Flyway migration 管理。
- 已提供项目文档：`docs/PRD.md`、`docs/product-design.md`、`docs/architecture.md`、`docs/api-contract.md`、`docs/operations.md`、`docs/adr/0001-spring-cloud-2025-boot-35.md`。
- 已从 `llfzzz/RTT` 复用 Backend Foundation 形态到 `backend/common`，新增 Boot 3 自动配置、`X-Trace-Id` 过滤器、结构化 `ApiError`、`BusinessException`、全局 MVC 异常处理和对应单元测试。
- 已提供 CI 配置和本地 `scripts/verify.sh`。
- 已验证 `./scripts/verify.sh` 通过：后端测试、前端类型检查、前端生产构建全部通过。
- 已验证 H5 和后台 Vite 服务可本地返回 HTTP 200：
  - H5: `http://127.0.0.1:5173/`
  - Admin: `http://127.0.0.1:5174/`
- 已初始化 Git 仓库并推送到 `https://github.com/llfzzz/o2o-Local-Carpooling.git` 的 `main` 分支，初始提交为 `318b282`。

## 未完成

- Trip、Order、Payment Sim、Map、Admin、Audit 等业务接口仍主要是内存/占位实现，尚未接入 MySQL Repository、事务、数据库锁或乐观锁。
- `infra/mysql/001_init.sql` 仍是早期参考 SQL，后续新增表应优先进入各服务 Flyway migration。
- Gateway 尚未实现真实 JWT 校验、RBAC、限流规则、鉴权失败统一响应。
- Auth 当前是 Mock 短信和 Mock Token，尚未接短信服务、JWT 签名、刷新 Token、会话失效。
- Driver 文件上传当前已持久化文件元数据，但尚未真正打通 MinIO 上传、私有桶、短时授权 URL。
- OCR 当前是 Mock，已持久化 Mock OCR 任务，但尚未接真实 OCR Provider，也没有异步 OCR 任务队列。
- Map 当前是适配层占位，尚未接真实高德地图 API，也没有供应商响应快照落库。
- Order 当前未真正接 RabbitMQ 延迟队列，支付超时取消是接口占位。
- Payment Sim 当前没有完整支付单生命周期、回调签名、幂等回调处理。
- Audit 当前没有真正写入 MongoDB；traceId 只在 MVC 服务 common foundation 中生成/透传，尚未贯通 Gateway、日志和 MongoDB 审计。
- Admin 当前是前端静态/本地状态为主，尚未接真实后台聚合接口。
- Testcontainers、契约测试、Playwright E2E、安全测试、性能压测尚未落地。
- 当前机器 shell 没有 `docker` 命令，因此只验证了 `docker-compose.yml` 的 YAML 语法和部分镜像 tag，未实际启动 Compose。
- 当前已有 GitHub 远端和 `main` 提交，但尚未建立长期分支策略或 PR 记录。

## 已优化

- 技术版本按官方兼容线保守选择，避免首期直接上 Boot 4。
- 领域规则先写测试再实现，订单状态机和库存规则有单元测试兜底。
- 路线距离、价格、座位库存、订单状态的权威性原则已写入 PRD 和领域代码。
- 外部密钥通过环境变量读取，`.env.example` 只保存占位值。
- 数据库结构预留了幂等键、版本号、审计/Outbox、核心索引。
- 手机号、车牌等敏感字段在 SQL 中预留为加密存储类型。
- Users、司机审核、文件对象、OCR Mock 任务已进入服务自有 Flyway migration。
- 手机号字段已通过服务端 AES-GCM 加密后存储，Mock OCR 证件号已做落库脱敏。
- 前端分成 H5 和运营后台两个应用，避免用户端与运营端交互模型混杂。
- 前端使用 TanStack Query 管理服务端状态，Zustand 只承载轻量本地状态。
- 本地校验统一收口到 `scripts/verify.sh`，CI 与本地命令保持一致。
- 文档已经覆盖产品、PRD、架构、API、运维和版本 ADR。
- 后端 MVC 服务通过 common foundation 统一了基础 traceId 响应头和结构化异常响应模型，后续 Gateway/WebFlux 与审计链路需要继续补齐。

## 未优化

- 运营后台生产构建单 JS chunk 约 996 KB，后续应做路由级懒加载和更细粒度代码分包。
- 前端尚未接真实 API 客户端和统一错误处理，也没有从 OpenAPI 生成类型。
- H5 地图区域仍是产品占位，不是真实地图 SDK 或 WebGL/Canvas 地图组件。
- UI 尚未做浏览器截图回归、移动端多尺寸验证和可访问性检查。
- 后端各服务 `application.yml` 有重复配置，后续可抽到 Nacos shared config 或 Spring profile 模板。
- 业务服务 Controller 仍偏演示，后续要拆成 Controller、Application Service、Domain Service、Repository。
- Gateway 鉴权失败、WebFlux 错误响应和前端错误处理尚未接入 common foundation 的企业级错误码、traceId、message、details 格式。
- 日志规范、脱敏日志、审计埋点、Metrics、Tracing 还没有系统化实现。
- 缓存策略、数据库读写隔离、限流降级、熔断 fallback 还没有真正落地。
- 安全基线还没有覆盖越权访问、文件越权下载、重复提交、接口限流和敏感字段脱敏测试。

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

1. 进入 Slice 3：实现真实 JWT/RBAC，Gateway 加鉴权、限流，并让 Gateway/WebFlux 错误响应对齐 common foundation。
2. 打通 MinIO 私有文件上传、短时授权 URL 和司机证件审核闭环。
3. 将 Trip/Order 从内存推进到 MySQL，补事务边界、库存锁、幂等键和 Outbox。
4. 接 RabbitMQ 延迟消息，实现订单支付超时自动取消和库存释放。
5. 接真实高德地图适配层，保存 `RouteSnapshot` 和供应商响应快照。
6. 为司机审核、发布行程、乘客订座、支付超时取消补 E2E 测试。
