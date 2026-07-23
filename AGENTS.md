# O2O Local Carpooling Agent Handoff

Last updated: 2026-07-23 CST
Workspace: `/Users/llfzzz/Desktop/o2o-Local-Carpooling`

**本文件是本项目实施状态的唯一权威来源（single source of truth）。** 任何新会话/新 agent 接手前，应先完整阅读本文件，尤其是「Demo Mode 实施路线图」「已完成 — Demo Mode 阶段详情」「下一步精确行动」三节，再决定下一步做什么。每完成一个有意义的实施步骤（每个 commit 级别的 Step），必须回来更新本文件。

## 项目定位

这是一个企业级 O2O 同城拼车系统。首期目标是做出可运行、可继续扩展的 MVP 基线，覆盖「手机号登录 -> 司机证件审核 -> 车主发布行程 -> 乘客订座 -> 模拟支付 -> 支付超时取消/库存释放 -> 后台复核审计」闭环。

当前版本是 `0.5.0-SNAPSHOT`。项目正处于**第二条主线任务**中：把项目从「可本地运行的 MVP 基线」推进为「可完整点击走通的端到端 Demo 版本」，同时保持生产级安全标准不降级。这条主线任务有独立的详细执行计划，本文件是其执行状态的实时记录。

## 当前主线任务：可点击端到端 Demo 版本

### 目标

在 `docker compose up`（真实 MySQL/Redis/RabbitMQ/MongoDB/MinIO/Nacos + Gateway + 全部微服务）之上，让用户可以在浏览器里完整点击走通整个业务闭环，并可以**主动触发**成功/失败/超时/取消/重试/回调等各种路径。所有外部**业务**供应商（短信、OCR、支付、实名认证、地图、通知）都通过可替换的 Provider 适配器实现，Demo Provider 只是其中一种实现；基础设施（MySQL/Redis/RabbitMQ/MongoDB/MinIO/Nacos）永远是真实的，不做任何 Mock。**安全能力任何时候都不允许被 Mock 或削弱**——本轮工作实际上是在补齐/修复原有安全缺口，而不是妥协它们。

### 验收标准（Acceptance Criteria，全部满足才算完成）

1. **真实拓扑**：`docker compose up` 拉起全部中间件且健康；全部服务注册到 Nacos；所有请求必须经过 Gateway `/api/**`，没有绕过网关的业务路径。
2. **可交互登录**：H5 输入手机号 -> 发送验证码；Demo 明文只进入 auth-service 的短期 `DemoLoginCodeStore`，用户只能在当前登录页持匹配 `challengeId` 显式「查看」，绝不进入消息中心、普通通知 API、日志或审计，也绝不自动填充；登录成功、锁定或 TTL 到期立即删除。错误/过期/重放的验证码必须被拒绝；发送要限流；连续验证失败要锁定。
3. **服务端权威鉴权**：客户端不能自行指定角色；角色来自持久化的用户记录；access token 短期有效，配合可用的 refresh token 轮换机制。
4. **发布 -> 搜索 -> 下单**：车主发布行程（服务端路线快照 + 计价）；乘客搜索并订座 -> 订单进入 `PENDING_PAYMENT` 且锁座。价格/库存/状态全部服务端权威。
5. **Demo 实名认证**：乘客/司机可以走真实名 + 活体检测流程；运营/用户可以从 Demo 控制台主动触发 APPROVED / REJECTED / TIMEOUT / RETRY_REQUIRED，以及活体的 PASS/FAIL/TIMEOUT/RETRY；结果通过收件箱送达，不是内联返回。司机能力只有在认证通过后才授予。
6. **Demo 支付走签名 Webhook**：支付会创建一个 payment intent；完成动作通过**模拟供应商回调**触发，带 HMAC 签名校验、重放保护和幂等处理；运营可主动触发 succeeded/failed/canceled/expired，以及延迟/重复/乱序回调。订单状态只能由「验证通过的回调」驱动更新。
7. **超时与取消路径**：未支付订单通过既有的 RabbitMQ TTL/DLX 路径自动取消并释放座位；乘客/司机/运营都可以主动取消；所有迁移都必须经过订单状态机。
8. **评价**：订单变为 COMPLETED 后，收件箱出现评价邀请；只有该订单的合法已完成参与者可以提交且只能提交一次（防重复、鉴权、校验、审计齐全）。
9. **Demo 环境下也要 Security-by-default**：仓库中不能有任何密钥；Demo 专属端点（收件箱、Demo 控制台、seed/reset）在 staging/production 下必须不可能被启用；RBAC、限流、幂等、重放保护、审计日志、输入校验、上传类型/大小限制、安全的错误响应（不泄露内部信息）、按环境的 CORS 白名单、TLS-ready 网关、非 root 容器、内部中间件端口不对外暴露。
10. **可替换性证明**：把 `providers.*.type` 从 `demo` 切换为真实供应商名 + 提供环境变量凭据，应该是接入真实供应商唯一需要做的事；核心业务流程和契约不变。
11. **质量门禁**：`./scripts/verify.sh`（mvn 测试 + pnpm typecheck + pnpm build）必须全绿；新增状态机、验证码校验、回调签名/重放/幂等、评价资格、RBAC 都要有单元测试；Phase 9 还要补 E2E smoke 脚本和 Playwright 流程。

### 锁定的设计决策（已与用户确认，不要重新讨论）

1. **模块划分（Hybrid）**：新增独立的 `notification-service`（通知是真正跨领域的能力：短信/推送/站内信）；实名认证放在一个聚焦的独立模块里，本阶段只实现 `DemoIdentityProvider`；评价（review）功能放进 `order-service`，严格绑定到已完成订单。
2. **运行时**：真实 Docker 中间件，只 Mock 业务供应商；三套 profile：`demo` / `staging` / `production`。
3. **消息中心与一次性登录凭证严格分离**：`/api/inbox` 只承载用户消息和业务/系统通知；登录验证码是唯一例外，只允许在当前登录页通过 challenge-bound demo-peek 临时查看，绝不写入消息中心。其它允许 reveal 的敏感通知默认遮罩、由消息中心显式「查看」。支付完成必须通过模拟的签名 Webhook 回调触发，不能是纯前端状态切换。

### 计划文件

详细的分阶段实施计划（含每一步的交付物与验证方式）位于本机 `~/.claude/plans/first-check-the-status-purrfect-token.md`（该文件**不在仓库内**，是 Claude Code 会话产物，仅供本机会话参考；本 AGENTS.md 才是仓库内、随代码一起版本化的权威记录）。

## 总体项目需求

### 角色

- 乘客：手机号登录、路线搜索、查看行程、锁座下单、模拟支付、取消订单、查看我的行程、订单完成后评价。
- 司机：提交驾驶证/行驶证、实名+活体认证、等待审核、发布路线、管理座位、确认订单、取消发车。
- 运营：司机审核、订单监控、行程管理、用户管理、文件/OCR 复核、风控审计、系统配置、Demo 控制台（驱动各类 Mock 供应商结果）。
- 系统：统一鉴权、限流、幂等、防重复提交、延迟取消、事件投递、状态机校验、日志追踪、审计留痕。

### MVP 范围（含 Demo Mode 扩展）

- 手机号验证码登录：**服务端已校验验证码**（S8 起）；Demo 验证码仅在登录页通过 `(phone, challengeId)` 临时取出，登录成功/锁定/TTL 即删，绝不进入消息中心。
- RBAC 角色模型：`RIDER`、`DRIVER`、`OPERATOR`、`ADMIN`，角色服务端权威，客户端不可自选。
- 短期 access token + 可轮换 refresh token（含重放检测与吊销）。
- 司机资质上传与 OCR（Provider 化，当前 Demo 实现）。
- 实名认证 + 活体检测（Provider 化，当前只有 `DemoIdentityProvider`，Phase 4 已落地：`identity-service` + 两层状态机 + 司机准入门禁 + H5 界面）。
- 后台人工通过/驳回司机认证。
- 车主发布行程，保存路线快照、距离、时长、价格、座位库存。
- 乘客按起终点搜索行程。
- 订单创建锁座、Payment Intent 支付（Provider 化，Demo 支付走签名 Webhook，Phase 3 ✅）、支付超时取消、库存释放、取消（用户/司机/运营，S14 ✅）、完成（S14 ✅）。
- 评价功能（Phase 6 待建）。
- Demo Delivery Center：跨供应商的用户收件箱 + 运营 Demo 控制台（S4/S5 已建）。
- MinIO 私有文件上传、完成确认和短时下载授权。
- RabbitMQ TTL/DLX 延迟取消与 Order Outbox。
- MongoDB 审计日志落库和运营检索。

### 非目标

- 首期不接真实支付、真实短信网关、真实 OCR/实名/活体供应商——但必须留好 Provider 适配层，接入时不改核心业务流程。
- 首期不做复杂动态拼单调度。
- 首期不做原生 App。
- 不允许把 Mock 能力包装成真实生产能力；不允许 Demo 专属端点/数据泄漏到 staging/production。

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
backend/common                领域模型、状态机、事件类型、后端 common foundation、Provider/Profile 配置基座
backend/gateway-service       统一入口、路由、限流、鉴权入口
backend/auth-service          手机号登录（服务端验证码校验）、JWT + Refresh Token、角色
backend/user-service          用户资料、角色、账号状态
backend/driver-service        司机资质、车辆、审核状态
backend/trip-service          发车、路线快照、搜索、库存视图
backend/order-service         订座、订单状态机、超时取消（评价功能待补，Phase 6）
backend/payment-sim-service   支付隔离层：PaymentProvider SPI + PaymentIntent 状态机 + 签名回调摄取 + Demo 支付控制台（Phase 3 ✅）
backend/map-service           高德地图适配层和路线快照
backend/file-service          MinIO 文件对象和授权访问
backend/ai-service            OCR Provider 适配（S19：OcrProvider SPI + DemoOcrProvider 异步任务生命周期，按 providers.ocr.type 选型）
backend/admin-service         运营后台聚合接口
backend/audit-service         审计日志和关键事件归档
backend/notification-service  【新增】跨服务通知 Provider SPI + Demo Delivery Center（用户收件箱 + 运营 Demo 控制台）
backend/identity-service      【新增，S16】实名认证 + 活体 Provider SPI + DemoIdentityProvider + 两层状态机 + Demo 实名控制台（结果异步投递收件箱）
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
apps/user-h5          用户端双外壳（S36）：matchMedia 1024px 运行时切换——窄视口保持 H5「行程流 Trip Flow」（S35：底部四标签、地图前置首页 + 路线轨母题、订座确认页、订单状态时间线、成为车主四步 Stepper、演示收件箱）；宽视口为桌面「乘客控制台」（Dispatch 同款侧栏 + 面包屑外壳，FJ 卡片/主-从停靠面板/时间线，非 Ant 密表）。业务逻辑收敛于 src/lib（api/session/types/labels/format/queries），两壳共用
apps/admin-console    运营后台「调度台 Dispatch」（S35 重构）：⌘K 侧栏 + 面包屑顶栏 + KPI 总览 + 密集 FJ 主题化 Ant Table + 司机审核详情抽屉 + 订单已保存视图；含 Demo 控制台（支付回调/实名活体/通知投递模拟，S29）与 OCR 任务、订单完成/取消操作
packages/fj-ui        Free Joy 设计系统本地副本（tokens + 精选组件），@fj 别名消费
```

### 基础设施

```text
docker-compose.yml           本地 MySQL、Redis、RabbitMQ、MongoDB、MinIO、Nacos
backend/*/db/migration       已落库服务的 Flyway migration（notification-service 已加入）
backend/common/src/main/resources/carpooling-providers.yml
                              共享的 demo/staging/production Provider 选型配置，各服务通过 spring.config.import 引入
infra/mysql/001_init.sql     早期初始化 SQL 参考，不再由 Compose 自动挂载执行
.env.example                  本地环境变量占位模板，只含占位符，不含任何可用密钥
.env.demo.example             Demo profile 环境变量模板（含约定的本地中间件账号密码占位、密钥占位）
scripts/generate-local-env.sh 生成带随机密钥的本地 .env（gitignored），供 Demo profile 使用
.github/workflows/ci.yml     GitHub Actions 校验
scripts/verify.sh            本地一键校验
docs/                        PRD、架构、API、运维、ADR、产品设计
```

## Demo Mode 实施路线图

状态图例：✅ 已完成并已推送 main　🔶 进行中　⬜ 未开始

| Phase | 内容 | 状态 | Steps | 依赖 |
|---|---|---|---|---|
| 0 | Profile、Demo Mode 开关、密钥硬化 | ✅ | S1 Provider/Profile 配置基座、S2 密钥硬化/fail-closed、S3 DemoModeGuard | 无，是一切的地基 |
| 1 | notification-service + Demo Delivery Center | ✅ | S4 新建 notification-service、S5 Demo 收件箱/控制台 API（S6 前端并入 Phase 2/S10） | 依赖 Phase 0 |
| 2 | Auth/SMS 安全加固 + 交互式登录 | ✅ | S7 SmsProvider + 服务端验证码存储、S8 修复登录漏洞、S9 Refresh Token、S10 H5 交互式登录+收件箱 | 依赖 Phase 1（收件箱） |
| 3 | 支付 Provider + Intent 状态机 + 签名 Webhook | ✅ | S11 ✅ PaymentProvider SPI + Intent 状态机；S12 ✅ 签名 Webhook 摄取；S13 ✅ Demo 支付控制台；S14 ✅ 订单取消/完成状态迁移；S15 ✅ H5 订座流程改造 | 依赖 Phase 1（回调触发通知）+ Phase 2（鉴权） |
| 4 | 实名认证（含活体）Demo Provider | ✅ | S16 ✅ 身份模块 + DemoIdentityProvider、S17 ✅ 准入门禁（司机能力需认证通过）、S18 ✅ H5 认证界面 | 依赖 Phase 1（结果异步投递到收件箱） |
| 5 | OCR Provider 适配 | ✅ | S19 ✅ OcrProvider SPI + DemoOcrProvider（异步任务生命周期） | 依赖 Phase 0 |
| 6 | 订单评价（order-service 内） | ✅ | S20 ✅ 评价领域+接口（资格/防重复/鉴权/校验/审计）、S21 ✅ H5 评价界面 | 依赖 Phase 3（订单需要 COMPLETED 状态，即 S14） |
| 7 | 地图 Provider 配置对齐 | ✅ | S22 ✅ 统一到 providers.map.type，保留失败不静默降级模型 | 依赖 Phase 0 |
| 8 | 部署与安全加固 | ✅ | S23 ✅ Docker 加固（localhost-only 端口/资源限制/no-new-privileges/`docker-compose.demo.yml`）、S24 ✅ Gateway TLS-ready+安全头+按环境 CORS、S25 ✅ 文件上传类型/大小限制、S26 ✅ Demo seed 双重闸门 + operator 开通 | 依赖 Phase 0-7 大部分完成 |
| 9 | 端到端测试与文档 | ✅ | S27 ✅ curl 全栈 E2E（真机 FAILS=0）+ 支付回调契约测试 + Playwright 登录冒烟；S28 ✅ demo-mode/security 文档 + ADR-0002 + 刷新本文件 | 依赖前面所有 Phase |

**当前所在位置：🎉 Phase 0–9 全部完成（S1–S28）。159 个单元/切片测试全绿；S27 全栈 E2E 于真实 Docker 栈跑通（14 服务、全部 Nacos 注册、13 步 curl 冒烟 FAILS=0 + 深度持久化核对）；安全加固（S23-S26）、契约测试、Playwright 登录冒烟、文档（demo-mode/security/ADR-0002）均已落地并推送 main。唯一有意保留的技术债：payment-sim 旧 `/api/payments/simulations` 入口（无消费者，可删）、Demo 数据 reset（只做了 operator seed）、内部服务间调用 mTLS/token、Playwright 仅登录冒烟（完整业务流由 curl smoke 覆盖）、真实供应商对接（推迟项）。E2E 过程中发现并修复了 3 个单测漏掉的集成缺陷（loadbalancer、Flyway baseline、user-service 404），见下文「S27 全栈 E2E 结果」。2026-07-07 补充：新增 `docs/demo-user-guide.md`，作为项目已启动后的手动 Demo 操作手册（网页入口、登录/注册、下单锁座、支付回调、订单完成、司机认证、运营台/API 操作）。**

**S29（2026-07-07，已实现+真机验证，已提交 `c6c0c5b`）：运营台 Demo 控制台 + 订单操作 UI**——补齐 S13 起遗留的「Demo 控制台前端 UI」缺口，原 curl-only 的运营流程全部可以在浏览器点击完成：

- **admin-console 新增「Demo 控制台」视图**（明确标注演示专用，与生产动作分离）：① 支付回调模拟——选 intent → 结局（成功/失败/取消/过期）×投递模式（正常/重复/乱序）×时间回拨秒数，经 S12 签名管道投递，每条 emission 的接受/拒绝码 + 最终状态用时间线可视化；② 实名/活体驱动——列出认证会话，按钮驱动活体（PASSED/FAILED/TIMEOUT/RETRY）与会话（APPROVED/REJECTED/TIMEOUT/RETRY），非法迁移（如活体未过先批会话）以错误 toast 呈现服务端 409；③ 通知投递模拟——跨用户查看投递记录（永远脱敏）+ 驱动 DELIVERED/FAILED/RETRYING/READ。
- **「OCR 任务」视图**（生产 API，非演示）：提交 fileObjectId → 列表 → 「查询进度」轮询推进到 COMPLETED（脱敏字段+置信度展示）。
- **订单监控加操作列**（生产 API）：「完成订单」（SEAT_LOCKED，Popconfirm）；「取消订单」（Modal 必填原因 ≤200 字 → `POST /{id}/cancel` body `{reason}` → 审计 metadata，已验证落 Mongo）。
- **支撑后端**：demo-control 只读列表 ×3（`GET /api/demo/control/payment/intents`、`.../identity/verifications`、`.../notification/deliveries`，双闸门+网关 OPERATOR）；`GET /api/ai/ocr/tasks`（网关对该精确路径要求 OPERATOR）；order cancel 可选 reason。
- **顺带修复真机发现的竞态缺陷**：支付成功回调打到已 TIMEOUT_CANCELLED 的订单时，`markPaid` 的 Feign 409 曾把 Webhook 摄取炸成 500（真实 PSP 会无限重试）。现 `FeignOrderClient` 把 409 转译为 `OrderPayConflictException`，`PaymentCallbackService` 接受回调（intent 照常 SUCCEEDED）并记 WARN 供对账/退款（退款属真实供应商范畴）。
- **前端工程**：两个 app 的 Vite dev server 加 `/api` 代理（strip Origin，绕开 dev 端口不在 CORS 白名单的问题）+ `PORT` 环境变量支持 + launch.json autoPort；`.env.local`（gitignored）置空 `VITE_API_BASE_URL` 走相对路径。
- **验证**：后端 171 测试全绿（新增 12：cancel reason ×2、listing ×4、gateway 门 ×2、回调竞态 ×1 等）；两 app typecheck/build 全绿；真机浏览器全流程点击验证（回调→SEAT_LOCKED→完成→评价邀请进收件箱、取消原因落审计、活体门禁反例、OCR 91% 脱敏结果、投递重试计数 +1）。`docs/api-contract.md` 已标注全部 S29 端点（含补写的 Notification/Demo Delivery Center 一节）。

**S30（2026-07-07）：Demo 控制台拆分为独立模块 + 手动刷新**——原合并的「Demo 控制台」拆成三个独立导航页：支付回调 / 实名认证 / 通知投递（侧边栏加「演示模拟」分组分隔线，与生产运营页明确区隔；每页有自己的 Demo-only Alert + 演示模拟徽标）；OCR 任务保留在生产组。**四个模块全部去除自动轮询**（`refetchInterval` 删除 + `refetchOnWindowFocus: false`），改为每页右上角手动「刷新」按钮，仅用户点击或用户动作完成后（mutation invalidate）才更新；订单监控页保留仅在本页可见时的 5s 轮询（不在本次范围）。抽出复用组件：`RefreshButton`（统一手动刷新）、`ModuleHeader`（图标+标题+演示/生产徽标+右侧动作位）、`StatusBadge`（label/tone 映射驱动的状态徽章）、`DataTablePanel` 增加 `extra` 动作位。移除顶部「MVP 0.5.0」版本徽标。API 契约/权限/加载/错误/空态全部保留。浏览器实测：支付意图列表 12s 零轮询、点「刷新」恰好 +1 请求，OCR 列表 10s 零轮询；typecheck/build 全绿（后端零改动）。

**S31（2026-07-07）：线上站（woxiangchuanaj.top）双问题排查 + 修复：交互延迟 & 用户请求不进运营台**——针对用户报告的「所有可点击操作都极慢」与「用户端 POST（支付/实名/OCR 等）在运营台看不到」两问题做了全链路取证（浏览器计时探针 + 本地全栈对照复现），结论与修复：

- **延迟根因（部署层，非代码）**：线上主机同时跑 14 个 JVM + 6 个中间件容器，内存吃紧导致冷服务进程被换页。证据：同一接口冷/暖差 7–75 倍（发布行程 14.5s/7.3s/7.5s 冷 → 194–234ms 暖；实名发起 8.4s 冷 → 103ms 暖；payment-sim 纯 DB 单读 6.3s 冷 → 82ms 暖）；热路径也随机卡 0.6–2.1s（15 连发 GET 中出现 2094ms 尖峰）；Gateway 401 拒绝恒 ~40ms（网关常驻）；同一代码本地全栈全部 5–100ms。**代码侧收敛**：`scripts/start-services.sh` 每 JVM 加 `-Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=96m` + `-Dserver.tomcat.threads.max=24`（原默认 200×14）+ Hikari `maximum-pool-size=5/minimum-idle=1/keepalive-time=300000`（原默认 10×14≈140 连接）。**部署侧要求**（写入 `docs/operations.md` 新「服务器部署与升级」节）：≥8GB 可用内存、`vmstat` 查换页、`scripts/check-deployment.sh` 验收。
- **运营台看不到用户请求的三层根因**：① **线上后端 jar 落后于前端**（前端已是 S30，后端缺 S29 的四个列表端点：`/api/demo/control/{payment/intents,identity/verifications,notification/deliveries}`、`GET /api/ai/ocr/tasks`）——数据一直有落库（订单/意图/认证创建全 200，`/api/orders/admin` 能看到），只是运营台模块调的接口在旧后端不存在；② **`GlobalApiExceptionHandler` 的 `Throwable` 兜底把未映射路径（`NoResourceFoundException`）伪装成 `500 INTERNAL_ERROR`**，让版本不一致看起来像服务器崩溃（本地 HEAD 同样复现 500，四服务同因）；③ **运营台 access token（30 分钟）过期后无续期**，全部查询静默失败、仪表盘用 `?? 0` 兜底显示零值（「今日订单 0」实为加载中/失败）。
- **后端修复**：`GlobalApiExceptionHandler` 用 spring-web 的 `ErrorResponse` 接口把框架 4xx 语义透传（未映射路径→`404 NOT_FOUND`、错误方法→`405 METHOD_NOT_ALLOWED`、坏 JSON→`400 MALFORMED_REQUEST`），+3 单测与 payment-sim 契约测试锁定「未知路径必须 404」；顺带修掉排查中发现的**越权读**：`GET /api/payments/intents/{id}` 原来任何登录用户可读任意人的支付意图，现仅付款人本人或 OPERATOR/ADMIN（`PAYMENT_FORBIDDEN`），+1 测试（`docs/api-contract.md` 已更新两处契约）。
- **admin-console 修复**：运营会话弹性——`api()` 遇 401 自动重铸 operator session 并重放一次（module 级 in-flight 去重），`sessionQuery` 每 20 分钟主动续期（`refetchIntervalInBackground`），新 token 经 `setQueryData` 广播给所有按 token 键控的查询；仪表盘去掉零值兜底（加载显示 `—`、失败显示带 describeError 的 Alert + 重试）；全部 9 个列表加 `tableEmptyText`（失败必须显示错误码/trace，不再伪装成空数据）；司机审核/订单监控/行程/用户页补手动「刷新」按钮。
- **两端 UX**：新增 `GlobalActivityBar`（H5 + admin）——mutation 或首载在途时顶部 3px 进度条（排除后台轮询避免常亮），慢请求不再像卡死。
- **新工具**：`scripts/check-deployment.sh [api-base]`——对本地或线上验收：operator 会话、S29 列表端点 200（404/500=后端 jar 落后）、未映射路径 404 语义、冷/暖延迟快照（差距大=主机换页）。本地全栈实测 ALL CHECKS PASSED；越权反例（陌生 rider 读他人 intent 403 / operator 200）真机验证通过。
- **验证**：`./mvnw test` 15 模块 **173 测试全绿**（+4）；两 app `typecheck`/`build` 全绿；admin-console 浏览器实测（仪表盘真值「今日订单 3」、支付回调模块列出用户创建的 intent 并可选中驱动）；H5 浏览器实测（活动条挂载、无 console 错误）。**线上站还需按 `docs/operations.md` 运行手册重新部署后端 jar（与前端同 commit）并跑 `scripts/check-deployment.sh https://woxiangchuanaj.top/o2o-api` 验收**——这是把两问题在线上彻底关闭的最后一步，代码侧已全部就绪。

**S32（2026-07-07，已提交 `3c6a065`）：去 Nacos 直连路由 + 去 CDN 图标 + 低内存 systemd 部署**——网关路由与全部 13 个 Feign 客户端从 `lb://<service>` 改为 `${O2O_<SERVICE>_SERVICE_URL:http://127.0.0.1:<port>}` 直连（Nacos 5 天前在本机被 OOM kill 后未重启，本次直接移除运行期依赖而非维持它存活）；`fj-ui` 的 `Icon` 从运行时 unpkg.com 拉取改为本地内联 SVG mask 表（`iconMask.js`），去掉渲染路径上的外部 CDN 依赖；补入与线上一致的 systemd 部署布局（`deploy/systemd`、`deploy/nginx`）与 `docker-compose.lowmem.yml`/`scripts/*-lowmem.sh`，让仓库与实际部署对齐；vite 增加 `VITE_BASE_PATH` 支持 nginx 子路径（`/o2o/`、`/o2o-admin/`）。

**S33（2026-07-08，最终审计——安全加固 + 死代码清理，本机改动，尚未 commit/push）：全项目终审发现并关闭一组「内部专用写接口经网关 `/api/**` catch-all 对外暴露」的越权面**——这些接口只应由服务间 Feign（直连 URL、不走网关）调用，但因与对外接口共享 catch-all 路由被任意登录用户可达：

- **网关单点加固（`GatewaySecurityFilter`）**：新增 `isInternalOnlyPath`，对以下路径**一律返回 404**（与「不存在」不可区分）：`POST /api/users`（upsert 含 roles → 客户端自选角色**提权到 ADMIN** 的严重漏洞）、`GET /api/users/{id}`（返回单条未脱敏用户）、`POST /api/orders/{id}/pay`（**绕过签名回调管道免费标记已支付**）、`POST /api/orders/{id}/timeout`、`POST /api/trips/{id}/seat-locks[/{orderId}/release]`（篡改他人订单座位库存）、旧 `POST /api/payments/simulations|simulate-success`（同样是直连 markPaid 的支付旁路）。这些内部调用走 `O2O_*_SERVICE_URL` 直连，不受网关拒绝影响。
- **补齐司机审核 RBAC**：`GET /api/drivers/verification-cases`（审核队列，跨所有司机）与 `POST /api/drivers/verification-cases/{caseId}/approve|reject` 此前网关未对 `/api/drivers/**` 做角色校验，任何登录用户可审批/驳回司机证件、可看全部证件——`requiresOperator` 已补精确 method+path 门禁（OPERATOR/ADMIN）；自助提交 `POST /api/drivers/verification-cases`（已实名司机本人）仍开放。
- **死代码清理**：删除 S11 前旧支付入口（`PaymentSimulationController/Service/Repository`、`SimulatePaymentCommand`、`PaymentSimulationServiceTest`）与仅其使用的 `common` 领域类型 `PaymentSimulation`/`PaymentStatus`（H5 自 S15 起走 Payment Intent，旧入口无消费者且本身是支付旁路）；保留 Flyway `V1__create_payment_simulations.sql`（已在库中执行，删除会破坏迁移历史，表停用）。删除无任何消费者的 `POST /api/orders/{id}/timeout` 控制器方法（超时全程走 RabbitMQ 消费者 + 定时扫描，进程内，无对外 HTTP 入口）。清掉 `OrderService.markPaid` 里 `updated ? get : get` 的恒等三元。
- **测试**：`GatewaySecurityFilterTest` 重写 `POST /api/users` 用例（原断言「转发」= 漏洞行为，改为断言 404）+ 新增 users/{id}、order pay/timeout、trip seat-locks、legacy simulations 拒绝 404、order cancel 仍转发（防过度拦截）、司机审核 rider 403 / 提交仍开放 / operator 可审批等用例。
- **文档**：`docs/api-contract.md`（Gateway Security/Users/Driver/Trips/Orders/Payment Sim 各节标注内部专用+404 语义与运营门禁，删旧 simulations 节）、`docs/security.md`（鉴权节 + Known gaps 记录读侧 IDOR 与 trip publish 身份绑定两条 low 未修建议）、`docs/architecture.md`（时序图改为 Payment Intent + 签名回调）、`README.md`（去 Nacos/去 Mac codex PATH + 内部接口说明）、`.env.example`（Nacos 默认 off + `O2O_*_SERVICE_URL` 说明）。
- **部署**：**本次安全加固上线只需重建并重启 `gateway-service` 单个服务**（内部 Feign 走直连不受影响，无需动其余 13 个服务）；死代码清理与恒等三元属源码整洁，随下次 order/payment-sim 全量重部署生效，其间网关 404 已覆盖线上风险。**尚未 `git push`（本机无 push 凭据）**，需用户从有凭据的机器推送。
- **未修（有意，已在 `docs/security.md` Known gaps 记录为建议）**：`GET /api/orders/{id}`、`GET /api/orders/{id}/review`、`GET /api/trips/{id}` 的读侧 IDOR（随机 UUID 不可枚举，改动触及 H5 热路径，建议单独一轮加 owner/operator scoping 与测试）；`POST /api/trips` 的 `driverId` 取自 body 而非绑定登录主体、且未在发布时要求 driver 能力（建议改为从 `X-User-Id` 解析 + 发布时校验 driver 准入）。

**S34（2026-07-09，部署漂移修复 + Sentinel 端口加固 + 压测评估，本机改动，尚未 commit/push）：** 补齐 S33 遗留的部署漂移，新增一处此前未发现的端口暴露修复，并交付静态容量评估与可复用压测脚本——

- **部署漂移修复**：确认 `order-service`/`payment-sim-service` 的线上 jar 早于 `26bba6b`（`jar tf` 证实 payment-sim-service 仍打包已删除的 `PaymentSimulationController/Service/Repository`，order-service 仍含已删除的 `/timeout` 方法），根因是本文件已记录的「Maven 增量坑」。修复：先 `systemctl stop` 目标服务再 `./mvnw -pl <service> -am clean package -DskipTests`（避免 jar 被运行中 JVM 占用），逐个重启 + `/actuator/health` 健康门禁，静态解包校验死代码已清除（不能靠网关 curl 验证——网关层已统一 404 掉这些内部路径，不管后端 jar 是否过期）。两次重建 + 重启均在 25 秒内健康完成（对比 2026-07-08 网关单独重启触发的 ~20 分钟崩溃重启循环），因为「先停服务再构建」同时释放了该服务的 cgroup 内存额度、也从根上避开了 jar 被占用的问题。
- **新发现并修复：Sentinel 端口 `0.0.0.0` 暴露**：gateway-service 的 Sentinel 传输端口 `8719` 监听在所有网卡而非仅 `127.0.0.1`（该版本 Sentinel 的 `ServerSocket(port, backlog)` 无绑定地址可配）。字节码级确认 Sentinel 在本项目零功能使用（无 `@SentinelResource`/规则数据源），且该暴露不是网关独有——14 个服务的 jar 里都潜伏同样的能力，只是网关最先因为流量触发了它的懒加载 command center。修复：在共享的 `deploy/systemd/o2o@.service` 模板加一行 `Environment="SPRING_CLOUD_SENTINEL_ENABLED=false"`（配置级生效，无需重新编译），随本次 order/payment-sim 重启顺带下发，另重启 gateway-service 专门关闭当前已开的 8719（`ss -tlnp` 确认关闭）。其余 11 个服务的配置已就位但要等各自下次自然重启才生效（本次未强制重启，因为它们目前都没有实际打开该端口）；永久修复（`backend/pom.xml` 排除 `sentinel-transport-simple-http`，覆盖全部 14 个服务）留作后续、需要全量重建，本次的配置级修复已关闭实际风险。
- **`scripts/check-deployment.sh` 新增 3 节**：内部专用路径 404 复核（S33 网关拦截仍生效）、司机审核 RBAC 复核（operator 200 / rider 403）、已知安全缺口基线（`docs/security.md` Known gaps 里读侧 IDOR 与 trip publish 身份绑定两条，用新增的 `info()` 桶记录当前行为、不计入 `FAILS`——确认两条均未变化，仍是有意保留）。
- **压测评估（未对本机生成任何并发流量）**：主机在空闲基线下已处于内存超卖状态（14 服务 `MemoryMax` 硬上限总和 3640MB + 5 个中间件容器 1152MB = 4792MB，超过 3.4GB 物理内存约 38%），这解释了为什么「重启」而非「持续并发」才是本机真正的风险来源。新增 `docs/load-testing.md` 记录完整静态容量分析（Hikari 每服务 2 连接的排队上限、MySQL 40 连接对 28 个 Hikari 连接的余量、网关限流 20/60s 与 120/60s 的吞吐天花板）。新增 `scripts/loadtest/`（`lib/api.js` + `booking-flow.js` + `rate-limit-boundary.js`，k6 脚本）——每个脚本 `setup()` 里都有双重防呆（生产域名黑名单 + 需要显式 `I_UNDERSTAND_THIS_IS_NOT_FOR_PROD=yes`），本次会话未在本机执行，供未来指向 staging/本地栈使用。
- **验证**：`scripts/check-deployment.sh` 与 `scripts/demo-smoke.sh` 均在重新部署后针对线上跑通，`ALL CHECKS PASSED` / `FAILS=0`；全程 `free`/`vmstat`/`journalctl` 监控，无一次崩溃或换页恶化。
- **尚未 `git commit`**（改动：`deploy/systemd/o2o@.service`、`scripts/check-deployment.sh`，新增 `docs/load-testing.md` 与 `scripts/loadtest/*`），留给用户决定是否提交。（后已随 PR #2 合入 main。）

**S35（2026-07-11，分支 `feat/s35-trip-flow-dispatch-redesign`）：全站 UI/UX 重构——H5「行程流 Trip Flow」+ 运营台「调度台 Dispatch」**——按 Claude Design 项目「UI/UX redesign directions」的 `Carpool Redesign.dc.html`（采纳画布内推荐组合：H5 走方向 1a 行程流，桌面运营台吸收方向 1b 调度台的密集表格 + 详情抽屉）重建两个前端应用的表现层。**API 契约、数据流、鉴权/脱敏语义零改动**，青绿主色与 Free Joy tokens 不变：

- **user-h5**：信息架构从页内 Tabs 改为底部四标签（首页/行程/消息/我的）+ 独立订座页。A1 登录（品牌 hero「顺路的人，一起走。」+ 行内获取验证码；演示收件箱显式取码流程保留）；A2 首页 = 地图 hero（路线母题 + 玻璃 live pill 显示实时车主数）+ 路线轨卡片（出发/到达内联可编辑，驱动既有 trips 搜索）+ 顺路车主卡片（Bricolage 大号价格数字）+「发布示例行程」；A3 订座确认页（路线时刻/司机行 + 座位步进器 + 价格明细 + 大号合计 + 粘底「下单锁座」，支付仍在订单卡上显式发起、由签名回调驱动）；A5 我的行程 = 进行中/历史分段 + 每单状态时间线（已下单→支付回调→待出发→完成，取消态红色终止节点）+ 发起支付/取消/评价内嵌，订单卡新增用既有生产端点 `GET /api/trips/{id}` 读路线文案；A6 消息 = 类别着色图标 + 未读红点 + 全部已读 + 显式「查看」reveal；我的 = 资料卡 + 成为车主四步 Stepper（实名→活体→证件→审核，1:1 映射既有 identity 会话轮询 + driver-case 提交）+ 退出登录。Playwright 登录冒烟断言更新为新文案。
- **admin-console**：Dispatch 控制台外壳——侧栏（Carpool Ops 品牌、⌘K 快速导航过滤（真实过滤 nav 项）、运营/演示分组、司机审核待办徽标、运营员身份角标）；面包屑顶栏（在线/离线状态点 + 每页唯一手动「刷新」按钮，按 view 映射失效对应 query key 前缀，S30「无自动轮询、手动刷新」原则保持，订单监控页内 5s 轮询保留）；运营总览 = 6 KPI 大数字卡（超时>0 走 warn 色）+ 最近订单密表 +「查看全部」跳转 + 审计时间线（新增只读 `GET /api/audits?page=0&size=5`，复用生产审计接口）；司机审核 = 密表（行点击）+ 右侧停靠详情抽屉（证件卡片点击走 presign 下载、OCR 置信度、通过/驳回）；订单监控 = 已保存视图 Tabs（全部/待支付/已锁座/已完成/已取消，客户端过滤）+ 完成（Popconfirm）/取消（Modal 必填原因）动作不变；行程/用户/审计/OCR 与三个演示模块内容逻辑保留、只换外壳；顶层持久 info Alert 移除（demo 语义降噪），每个演示模块自身的 Demo-only Alert 保留。
- **验证**：`pnpm typecheck`/`pnpm build` 双 app 全绿；本地真实全栈（Gateway 8080）浏览器点击走通完整闭环——登录取码→订座锁座（PENDING_PAYMENT）→发起支付（intent）→运营台「支付回调」投递签名 SUCCEEDED（管道接受）→订单 SEAT_LOCKED（H5 时间线推进）→运营台「订单监控·已锁座」视图完成订单→H5 历史行程出现评价块并提交 5★；司机审核空态+抽屉占位、审计 12 行、用户列表、通知/OCR 列表全部正常渲染；无新增 console 错误（antd React19 兼容警告为既有）。

**S36（2026-07-18，worktree 分支 `claude/mobile-desktop-interface-redesign-bb70d1`）：user-h5 双外壳——移动 H5 + 桌面乘客控制台（视口切换）**——一个应用、一个 bundle，按视口宽度运行时切换两套用户端体验；**API 契约、查询键、轮询间隔、幂等键、鉴权/脱敏语义零改动**，无新增 npm 依赖，`styles.css`（移动样式）零改动：

- **共享层重构**：原 1317 行 `App.tsx` 拆为 `src/lib/{types,labels,session,api,format}.ts` + `src/lib/queries.ts`（全部 TanStack Query hooks 收敛于此：键/轮询/失效/幂等键由 hook 拥有，toast/导航经回调留在组件；orders 与 demo-inbox 5s、payment-intent 与 identity-verification 4s 轮询保持）；移动屏幕纯搬移到 `src/mobile/*`（MobileApp/Login/Home/Booking/Trips/Inbox/Profile），DOM 与视觉不变。
- **视口闸门** `src/lib/useViewport.ts`：`(min-width: 1024px)` matchMedia + change 监听，另加 500ms 定时兜底同步（CDP 视口仿真——Playwright/内置浏览器预览——改视口时不派发 resize/matchMedia change 事件，布尔不变时 React bail-out，零渲染开销）；两壳共用 zustand session 与 Query 缓存，跨断点热切换不丢登录、命中温缓存。
- **桌面壳** `src/desktop/`（DesktopApp/DesktopLogin + views/{Home,Trips,Inbox,Driver,Profile}）+ `desktop.css`（全部 `dsk-` 前缀，与移动样式表类名严格不相交；admin Dispatch 外壳 CSS 适配移植）：216px 侧栏（品牌/出行/账户分组、行程与未读计数 chip、用户脚标）+ 56px 面包屑顶栏（在线点 + 按 view 失效键的手动刷新）；找车 = 搜索卡 + 行程主列表 + **订座停靠详情面板**（选中行程 → 座位步进 + 价格明细 + 下单锁座，成功跳「我的行程」）；我的行程 = 4 Stat KPI + 进行中/历史 Tabs + 订单主列表 + 订单停靠面板（FJ Timeline 状态线 + 发起支付/intent 轮询 + 取消 + 评价）；消息 = FJ List，**只渲染 maskedPreview，值仅经显式「查看」reveal**（收件箱安全不变量在桌面端保持）；成为车主 = 独立导航项，四步 Stepper 卡 + 右侧认证进度 Timeline（同一 identity/driver-case/presign hooks）；个人中心 = 账户卡 + facts + 去认证/退出。图标名受限于 fj-ui iconMask 内置集（非全量 lucide），Timeline 用无图标彩点。
- **验证**：`pnpm -C apps/user-h5 typecheck`/`build` 全绿；Playwright 3 specs 全过——`login.spec.ts` 钉住 390×844 移动视口（原断言不动）、新增 `desktop-login.spec.ts`（1280 桌面登录卡渲染 + 登录门控 + 无 `.mobile-shell`；1023↔1024 热切换断言）；内置浏览器实测：390px 移动全屏幕与重构前一致，1280px 五个桌面视图 + 登录全部渲染，登录态跨断点拖拽热切换正常，Gateway 未起时空态/离线态优雅降级（真实全栈闭环点击留待下次起 Docker 栈时回归）。

**S37（2026-07-20，worktree 分支 `claude/trip-location-planning-fb71f8`）：真实位置能力第 1 阶段——坐标系边界 + 地图 Provider SPI 扩容**——这是「把硬编码/纯展示的行程位置体验换成真实的、带权限的、供应商支撑的多城市位置系统」这条新主线的第一片，后端专属、纯增量，前端零改动。

已确认的产品决策（本轮与用户逐条敲定，后续阶段不要重开）：① **定时拼车 + 接驾途中实时定位**（司机位置只在行程进行中存在，且只对已下单该行程的乘客可见）——AGENTS.md 的「不做复杂动态拼单调度」非目标保持不变；② 乘客自选已发布行程，无司机接单步骤、无自动派单；③ 下单前不暴露任何司机位置，下单后精确可见；④ 全国范围 + 按 adcode 的配置化城市白名单，允许跨城；⑤ 前端用高德 JS API 2.0（与后端同为 GCJ02，不跨坐标系混用瓦片）；⑥ 已有 Web 服务 key，JSAPI key 待注册（两套受限凭据分离）；⑦ demo/真实双支持 + 显眼的模式徽标；⑧ 司机每 ~10s HTTP 上报、乘客经 SSE 接收，Redis GEO + ~45s 存活 TTL；⑨ **只存当前位置（Redis），不落 MySQL、无轨迹历史**；⑩ 输入方式 = 联想 + 当前定位 + 地图拖拽落点；⑪ **发布行程的身份绑定缺口纳入本轮范围**（它正是司机上报位置所需的同一个能力校验）；⑫ 契约增量迁移，收尾阶段再删旧文本通路；⑬ 端点邻近 + 时间窗匹配（起点 3km / 终点 5km / 发车 ±2h，全部可配）；⑭ 拒绝定位后不做 IP 兜底，改用记住上次选择的城市选择器；⑮ POI/逆地理编码经 map-service 服务端代理，浏览器 JSAPI key 只用于瓦片渲染。

本阶段（S37）落地内容：

- **`backend/common` 新增坐标与位置领域模型**：`GeoPoint(latitude, longitude, datum)`（构造时校验经纬度范围、拒绝空 datum、归一化到 7 位小数以稳定往返 `DECIMAL(10,7)`；**没有任何构造函数接受不带 datum 的坐标对**）、`CoordinateDatum{WGS84,GCJ02}`、`CoordinateTransform`（**全仓唯一的 WGS84↔GCJ02 边界**，正向为公开近似算法、反向为 4 次迭代收敛到亚米级，国境外恒等）、`Haversine`、`LocationRef`（point/provider/providerPlaceId/cityCode/**adcode**/displayName/formattedAddress/source/accuracyMeters/capturedAt）、`LocationSource`。此前全仓**零坐标系处理**——高德收发 GCJ02、浏览器 Geolocation 产出 WGS84，二者在国内差约 500m，原先只有一个不做范围校验的正则把坐标当不透明字符串传递。
- **`RouteSnapshot` 增量扩展**：新增可空的 `polyline` / `origin` / `destination`（`LocationRef`），保留 4 参构造函数，因此 `TripRepository.mapTrip`、`PricingPolicy` 及全部既有调用点零改动。此前坐标在 `RouteQuoteService` 里被解析后**直接丢弃**，前端即使接了地图 SDK 也画不出路线。
- **Provider SPI 从 2 个方法扩到 5 个**：`MapRouteProvider` → `MapProvider`，新增 `reverseGeocode` / `suggest` / `searchPoi`；`AmapRouteProvider` → `AmapMapProvider`（新接 `/v3/geocode/regeo`、`/v3/assistant/inputtips`、`/v5/place/text`，驾车接口补 `show_fields=cost,polyline` 并拼接 steps 几何，解析 adcode/citycode/POI id）；`MockRouteProvider` → `DemoMapProvider`（原本距离是 `(起点字数+终点字数)×1200` 的字符串长度算术，现改为**跨厦门/北京/成都/哈尔滨 4 个不相关城市的 14 条固定 fixture** + 直线 × 1.3 道路系数，确定性可复现；未命中 fixture 返回 `MAP_DEMO_LOCATION_UNKNOWN`，不凭空编造地点）。
- **新增 `MapProviderSelector`（选型 + fail-closed 收口）、`MapQueryService`（**坐标系边界所在**：WGS84 入参在此转换，Provider 内部一律 GCJ02）、`MapCityRegistry`（`map.cities.enabled` 按 adcode 前缀的城市白名单，空列表=不限制；逆地理编码越界直接 `MAP_CITY_NOT_SUPPORTED`，联想/搜索则是**过滤**而非报错，因为一个关键词可能命中多城市）。
- **新增端点**：`GET /api/maps/cities`、`POST /api/maps/reverse-geocode`（`datum` 必填）、`GET /api/maps/place/suggest`、`GET /api/maps/place/search`、`POST /api/maps/route`（结构化）；旧 `GET /api/maps/route` 文本形态保留兼容。
- **Flyway `V2__route_snapshots_geometry.sql`**：`route_snapshots` 增 `polyline`/`coordinate_datum`/起终点 `adcode`/`cityCode`/`placeId`/`cache_key`（坐标取 3 位小数的去重键），`origin_text`/`destination_text` 从 `VARCHAR(120)` 放宽到 200（POI 全名放不下）。全部可空，可回滚。
- **验证**：`./mvnw test` **15 模块 218 测试全绿**（较 S31 基线 173 新增 45：`GeoPointTest` 7、`CoordinateTransformTest` 7（含 5 个不相关城市的往返误差 <1m）、`LocationRefTest` 5、`AmapMapProviderTest` 8（含配额超限/缺 Key/缺 adcode 的 fail-closed 反例）、`DemoMapProviderTest` 10（含多城市、确定性、拒绝编造地点）、`MapCityRegistryTest` 7、`MapQueryServiceTest` 7（含「WGS84 入参必须被转换约 500m」这条核心断言）、`RouteQuoteServiceTest` +2、`RouteSnapshotRepositoryTest` +3）。`docs/api-contract.md` 的 Map 一节已按新契约重写（坐标系约定、`LocationRef`、全部端点、失败语义）。
- **本阶段当时有意未做**：路线缓存读取与熔断已在 S44 补齐；地图配额限流已在 S40 落地。`map-service` 仍不新增 Redis 依赖，缓存继续复用 MySQL 的真实路线快照。

**S38（2026-07-20）：发布行程身份绑定 + 司机准入门禁**——关闭 `docs/security.md` Known gaps 里记录已久的缺口。

- **身份绑定**：`TripController.publish` 现在读网关注入的 `X-User-Id` 作为 `driverId`，请求体里的 `driverId` 兼容接收但**一律忽略**（此前任何登录用户都能以他人身份发布行程；`TripController` 曾是唯一不读取网关主体的业务控制器）。无主体时 `401 AUTH_REQUIRED`。
- **司机准入**：新增 driver-service 内部端点 `GET /internal/drivers/{userId}/capability`（不在 `/api/**` 下，网关不路由，与 `InternalIdentityController` 同构），返回「实名 APPROVED **且** 证件审核 APPROVED」的合取。`TripPublishService` 在**调用地图 Provider 之前**校验，被拒的发布不消耗供应商配额。客户端持有 `DRIVER` 角色声明不构成准入证明。
- **幂等**：`POST /api/trips` 补上 `idempotencyKey`（此前是唯一没有幂等键的创建类写操作），Flyway `V2__trip_publish_idempotency.sql` 建 `(driver_id, idempotency_key)` 唯一索引；重复提交返回同一条行程且不重复请求路线报价。
- **连带修复 `scripts/demo-smoke.sh`**：原脚本第 3 步用乘客身份发布行程，加门禁后必然 403。已重排为「登录 → 运营会话 → **成为已审核司机（实名+证件）** → 非司机发布反例（断言 `DRIVER_NOT_APPROVED`）→ 发布（故意传伪造 `driverId`，断言服务端绑定到真实主体）→ …」，原第 9 步的实名段落上移，第 9 步改为发布幂等复验。
- **`scripts/check-deployment.sh`**：原「已知缺口基线」里 trip publish 那条从 INFO 升级为**真实 CHECK**（回归会计入 FAILS）；读侧 IDOR 基线因为现在需要有司机资格的账号才能造行程，改为按 `PROBE_TRIP_ID`/`PROBE_ORDER_ID` 环境变量可选探测，并明确说明跳过原因。
- **验证**：`./mvnw test` **240 测试全绿**（新增 `TripControllerTest` 3 个：伪造 body driverId 被忽略、未认证被拒、幂等键透传且 `PublishTripCommand` 不含距离/时长分量；`TripPublishServiceTest` 扩到 7 个：两个准入半边、被拒时零配额消耗、主体一致性、幂等复用、跨司机同键互不影响）。`docs/security.md` 该条已标记 CLOSED。

**S39（2026-07-20）：结构化位置 + 地理匹配**——搜索从 `LIKE '%文本%'` 换成真正的地理邻近匹配。

- **`backend/common` 新增 `GeoMatchingPolicy`**（纯领域、无 I/O）：起点半径 / 终点半径 / 发车时间窗 / 结果上限，外加外接矩形换算与排序打分。**测试先抓到一个真 bug**：外接矩形原按 111320 m/度换算，而 `Haversine` 用的地球半径对应 111195 m/度，矩形比半径**小约 6m**，会在边界上悄悄漏掉本应匹配的行程。已改为共用同一地球模型 + 1% 安全余量，并留下断言钉死「矩形必须只多选不少选」。
- **Flyway `V3__trips_structured_location.sql`**：`trips` 增起终点 `lat/lng`（`DECIMAL(10,7)`）、`coordinate_datum`、`adcode`/`cityCode`/`placeId`、`route_polyline`，文本列放宽到 200，并建 `idx_trips_geo (status, departure_at, origin_lat, origin_lng)`。**刻意不用 MySQL SPATIAL**：仓库层测试跑 H2，`ST_*` 不可用，而这个量级下复合 B-tree 预筛同样够用。
- **`TripRepository.searchByProximity`**：SQL 外接矩形预筛（走索引）→ Java 精确大圆距离 + 排序。同时补上此前完全没有的**剩余座位过滤**与**发车时间窗**。旧 `search(origin, destination)` 保留兼容。
- **新端点 `GET /api/trips/search`**，`datum` 可选（传 WGS84 会先转换）。发布路径支持结构化 `LocationRef`（走 `POST /api/maps/route`，无需地理编码往返），旧文本形态经 `PublishTripCommand.ofText` 保留。
- **计价从硬编码字段初始化器移到配置**：`TripRepository` 里的 `new PricingPolicy(6.00, 1.20)` 改由 `TripMatchingProperties` 注入（`trip.pricing.*`），匹配参数同样全部可按环境覆盖（`trip.matching.*` / `TRIP_MATCH_*`）。
- **验证**：`./mvnw test` **263 测试全绿**（新增 `GeoMatchingPolicyTest` 11、`TripProximitySearchTest` 12）。地理测试**刻意覆盖厦门/北京/成都/哈尔滨四个不相关城市**（含高纬度的哈尔滨，验证经度矩形随纬度变宽），并覆盖：跨城路线、邻近起点匹配、超半径排除、时间窗、座位不足、排序顺序、非 PUBLISHED 排除、以及**迁移前无坐标的历史行程被跳过而不是报错**。

**S40–S43（2026-07-20）：前端定位/地图/实时接驾 + 旧通路下线**——真实位置能力主线收尾。

- **S40 前端定位输入**：新增 `useGeolocation`（显式状态机：idle/locating/granted/denied/unavailable/timedout/error，安全上下文检查、10s 超时、单次自动重试、**拒绝后不再自动追问**）、`useCityPreference`（记住上次城市，**不做 IP 兜底**）、`location.ts`（`LocationRef` 类型 + `isResolved` 守卫，未解析的值无法发起搜索）、`LocationSearchSheet`（联想 + 当前定位 + 演示徽标）、`CityPicker`、`DemoProviderBadge`。**删除两个首页里 6 个硬编码的厦门 `useState` 种子**，起终点改为「用户选择的已解析地点」。移动壳与桌面壳共用 `src/lib` 与 `src/components`，CSS 仍按 `dsk-` 前缀严格不相交。
  - **Playwright 新增 6 个用例**（granted/denied/timedout/unsupported/演示徽标/无硬编码起终点），mock 网关、无需后端。**这批测试抓到一个真 bug**：`useGeolocation` 的 `mountedRef` 只在 cleanup 里置 false、从不在挂载时置回 true，React StrictMode 双调用 effect 后该 ref 永久为 false，所有定位回调静默 return，状态永远卡在 `locating`。已修为「挂载置 true / 卸载置 false」。
  - **补上 Phase 1 有意推迟的地图配额限流**：网关新增独立的 `map` 限流桶（默认 60/min/user，`SecurityProperties.RateLimit.map`），与通用 API 桶分离——联想是逐键（防抖后）请求，共用通用额度会让单个账号烧光供应商配额。+2 个网关测试。
- **S41 真实地图**：`amapLoader.ts`（JS API 2.0 幂等加载，**仅用于渲染**；`securityJsCode` 不内联，改用 `window._AMapSecurityConfig.serviceHost` 指向自家 `/_AMapService/`，由 nginx 附加 jscode）、`TripMap.tsx`（起终点 marker + 服务端 polyline + 相机自适应 + 可选拖拽落点；**六种显式状态**：loading/unconfigured/offline/error/idle/ready）。移动首页 `hero-band` 的四个空 `<span>` 与 `styles.css` 里那条旋转 24° 的虚线彻底删除，桌面壳新增此前完全没有的地图面板。`index.html` 加 `Permissions-Policy: geolocation=(self)`。**供应商/SDK 失败一律显示明确错误，绝不显示"看起来正常"的假地图**（+2 个 Playwright 用例钉死）。
- **S42 接驾途中实时定位**：`DriverLocation`/`DriverLocationPolicy`/`DriverPresenceStore`（Redis + 内存双实现，与 SMS/refresh/nonce 同构选型）/`DriverLocationService`/`DriverLocationController`（含 SSE）。**两种权限严格分离**：上报＝该行程的司机本人；观看＝该行程司机**或**持 `LOCKED` 座位锁的乘客，其余**一律 404 而非 403**（403 会确认「该行程存在且正在被追踪」）。为此 `trip_seat_locks` 新增 `rider_id`（V4，由 order-service 从认证主体解析后传入，该接口内部专用故非客户端输入），避免在 10s 热路径上做跨服务调用。合理性校验：时间戳窗口、>200km/h 的瞬移、每行程限流。**位置只存 Redis、TTL 45s，不落 MySQL、无轨迹历史**——TTL 同时是离线信号与保留上限。+17 个测试（几乎全是鉴权反例）。
  - H5 侧：`useDriverLocationBroadcast`（司机每 10s 上报，标签页隐藏时暂停）+ `DriverLiveStatus`（仅在 `SEAT_LOCKED` 时渲染；过期显示「位置已过期」，**绝不把上次已知位置当实时位置展示**）。**注意**：浏览器端目前走轮询而非 `EventSource`——`EventSource` 无法携带 Authorization 头，而把 token 放进 URL 会被日志记录。SSE 端点本身可用，留给能设请求头的客户端。
- **S43 旧通路下线**：删除 `GET /api/trips?origin=&destination=`（纯 `LIKE`、无任何解析）、`TripRepository.search` 及 `like()`/`normalized()` 死代码、前端已无消费者的 `useTripsQuery`、`PublishTripRequest` 的 `driverId`/`distanceMeters`/`durationSeconds` 三个死字段，以及 Flyway `V5` 删掉只服务旧 LIKE 查询的 `idx_trips_search`（前导通配符本就让它用不上，只在白白拖慢写入）。`demo-smoke.sh`/`check-deployment.sh`/`loadtest` 全部改用 `GET /api/trips/search`，并新增「邻近 1.2km 仍命中」「北京搜索不返回厦门行程」两条断言。
  - **有意保留**：文本发布通路（`originText`/`destinationText` + `GET /api/maps/route`）。它**不违反**「地点必须解析后才能进入行程」——只是解析发生在服务端（Provider 地理编码）而非客户端，落库的仍是完整 `LocationRef`。删掉它只会逼 shell 脚本手写整段 JSON，没有实际收益。
- **验收核对**：`grep -rn "厦门|软件园|集美|Xiamen|Jimei"` 在**生产 Java 逻辑、全部 Flyway 迁移、两个前端 `src/`** 中均无命中；仅剩 `DemoMapProvider` 的固定 fixture（明确标注演示数据）与 `MapCityRegistry`/`application.yml` 里的配置示例注释。
- **验证**：`./scripts/verify.sh` 全绿（**282 后端测试**，较 S31 基线 173 新增 109）；Playwright **11 passed**（原 3 + 定位权限 6 + 地图状态 2）；内置浏览器实测移动 390px 与桌面 1280px 两壳渲染正常、无 console 错误、无后端时优雅降级。新增 `docs/adr/0003-location-and-map-architecture.md`；`docs/security.md` 新增「位置与追踪威胁模型」表（含**残余风险**：合理性校验不是防伪证明，慢速伪造仍可绕过）。
**S44（2026-07-20，worktree 分支 `claude/trip-location-planning-fb71f8`，未 commit/push）：高德双 Key、本地安全代理、路线缓存/熔断与 SSE 压测脚本**——只在指定 worktree 与仓库外本地 secret 工作，未部署或修改 `woxiangchuanaj.top`，H5 源码零改动。

- **高德凭据与本地验证**：保留既有 Web JS Key，另建独立 Web 服务 Key；两者只写入 gitignored、权限 `600` 的 `.env` / `apps/user-h5/.env.local`，JSAPI `securityJsCode` 只写入仓库外权限 `600` 的 nginx include。Web 服务 Key 已用高德真实地理编码请求验证返回成功；当前账号没有技术服务许可证，因此只允许本地/测试使用，生产仍不部署。
- **nginx fail-closed**：`/_AMapService/` 从 `/etc/nginx/secrets/o2o-amap-jscode*.conf` 读取 `$amap_jscode`；未读到时明确返回 503。仓库只新增无真实值的 `amap-jscode.conf.example`。本机没有 nginx 且 Docker daemon 未启动，所以本轮只完成静态配置与构建产物检查，`nginx -t`、未配置 503 和真实代理运行态验证待 Docker/nginx 可用后执行。
- **结构化路线缓存**：仅 `POST /api/maps/route` 读取 MySQL `route_snapshots.cache_key`；Provider + 两端约 100m 网格，默认 30 分钟新鲜期，命中不请求供应商也不重复写快照；同 key 进程内 single-flight 防并发穿透。共享的只是距离/时长/polyline，每个调用仍绑定自己请求中的 `LocationRef`，不会串掉地点名称和来源。旧文本 GET 路线继续不读缓存。
- **熔断与受限降级**：真实地图 Provider 统一经过 Resilience4j，默认连接/读取超时 2s/5s、无自动重试，20 次窗口/至少 10 次/50% 打开/30s 后半开/3 次探测。临时故障或熔断打开时只允许使用 24 小时内同 key 的历史真实路线，`providerTrace` 加 `-stale-cache`；缺 Key、Key/权限配置错误、非法参数、城市白名单拒绝都不能被旧缓存掩盖；POI/联想/逆地理编码无缓存降级；永远不回退到 `DemoMapProvider`。新增 fresh/stale cache hit、provider failure、circuit reject/state 指标。
- **压测脚本**：新增无第三方依赖的 `scripts/loadtest/sse-concurrency.mjs`，支持 Bearer Header、SSE 事件解析、首事件/事件间隔 p95、连接成功率、异常断线与 5xx 统计；默认 10→25→50→100、每档 2 分钟，硬拒绝生产域名并要求显式非生产确认。`booking-flow.js` 已先完成实名/活体/司机证件审批再发布，匹配真实准入门禁。
- **验证**：`./scripts/verify.sh` 全绿（**292 后端测试**；map-service 51，含新鲜/过期/24h 旧缓存、缺 Key、非法参数、single-flight、每请求 LocationRef、熔断打开/恢复）；H5 typecheck/build 全绿；Playwright 用空 Key 覆盖未配置状态 **11 passed**；构建产物含允许公开的 Web JS Key且不含 `securityJsCode`；Node/k6 脚本语法检查通过。Docker daemon 未启动、k6 未安装、没有 staging URL/行程/Token，因此 `demo-smoke.sh`、nginx 运行态和 SSE 真实并发结果没有伪造为已完成。
- **下一步只剩运行态事项**：① 启动本地 Docker/nginx 后跑 `nginx -t`、`/_AMapService/` 503/代理正反例与 `scripts/demo-smoke.sh`（要求 `FAILS=0`）；② 提供 staging URL、已激活行程与合法参与者 Token 后执行 SSE 阶梯压测；③ 取得技术服务许可证后，再单独规划生产 Key、secret、H5 构建和部署。

**S44（2026-07-20）：真实高德凭据接入 + 真机验证，抓到两个「看起来正常但是假的」缺陷**——用户配好真实 `AMAP_API_KEY` 与 JSAPI key 后，首次用真实供应商跑通并验证。本轮价值几乎全在「只有拿真 key 跑才会暴露」的两个问题上。

- **缺陷 1（配置，严重）：demo profile 把 `providers.map.type` 写死为 `demo`**。用户已设 `MAP_PROVIDER=amap` + 真实 key，但 `carpooling-providers.yml` 的 demo 文档是字面量 `type: demo`，**env 完全不生效**——跑的仍是固定 fixture，而界面一切正常。这正是本主线一直在防的「demo 被误认为真实」失败模式的反向版本。已改为 `${MAP_PROVIDER:demo}`（默认仍 demo，安全默认不变），并加测试钉死：其余五个 provider 仍必须写死 demo，只有 map 允许指向真实供应商（它只烧配额，不涉及真实资金/用户影响）。
- **缺陷 2（前端，严重）：高德 key 域名未授权时不会以任何可见方式失败**。真机实测：脚本正常加载、`new AMap.Map()` 正常返回、高德 logo 正常渲染、**连 `complete` 事件都照常触发**，但一个瓦片都没有；错误只以异步 `console.error: INVALID_USER_DOMAIN` 出现。原实现据此判定为 `ready`，于是**呈现一个看起来正常的空白地图**。已在 `amapLoader.ts` 增加针对高德鉴权错误码（`INVALID_USER_DOMAIN`/`INVALID_USER_KEY`/`USER_KEY_RECYCLED`/`DAILY_QUERY_OVER_LIMIT` 等）的检测：高德没有提供任何回调/Promise，console 是唯一渠道，故窄范围包一层 `console.error`（**只观测、永远转发原始调用、不吞任何日志**），新增 `rejected` 状态明确显示「地图密钥未授权当前域名」。注意先试过「`complete` 事件 + 超时」方案，真机证明不可行（鉴权失败时 `complete` 照样触发），已废弃。
- **真机实测基线**（MySQL 3307 + map-service 单服务，未起全部 14 服务——本机空闲内存仅 191M，AGENTS.md S31/S34 已记录过内存压力导致 7–75× 延迟劣化与 20 分钟崩溃重启循环）：输入提示返回 3 条真实 POI（`provider=amap`、真实 POI id）；**逆地理编码 WGS84 入参自动转 GCJ02、偏移 576m**（两个结果分别落在观日路 30 号与 10 号，正是不转换就会差半公里的实证）；驾车路线 17972m/1735s/**334 个折线点**；**路线缓存命中 514ms → 19ms**；无效 key 返回 502 `MAP_ROUTE_QUOTE_FAILED (INVALID_USER_KEY)` 且 `demoProvider` 仍为 false（不静默降级）；`route_snapshots` 与服务日志中 **API Key 泄漏 0 条**。
- **顺带修正一个环境相关的脆弱测试**：`map-states` 原本断言「未配置 key → 显示地图未配置」，但本机现在有 key，断言变成环境依赖。改为断言**契约**——地图不可用时必须显示某个明确原因（未配置/未授权/加载失败/离线之一），且页面其余部分仍可用——CI（无 key）与开发机（有 key 但域名未授权）都成立。
- **验证**：`./scripts/verify.sh` 全绿（**293 后端测试**）；Playwright **13 passed**（新增 key 拒绝检测 2 条）。`docs/operations.md` 新增「地图供应商配置」一节（两套凭据用途、`demoProvider` 自检命令、**域名白名单必须含 localhost 否则本地地图全空**、实测基线）。
- **缺陷 3（配置文档，用户实际踩到）：根目录 `.env.example` 列了 `VITE_AMAP_JS_KEY`，但 Vite 根本不读仓库根目录的 env 文件**（`vite.config.ts` 是 `loadEnv(mode, appRoot)`，只读 `apps/user-h5/`）。用户把 key 按惯例写进根 `.env`、拉到服务器后地图仍是「未配置」。叠加第二个坑：`VITE_*` 是**构建期**注入，`git pull` + 重启服务不可能生效，必须重新 `pnpm build` 并重新部署 `dist/`。已修：根 `.env.example` 改为显式警告「这里不是它该待的地方」并给出两种正确位置；`vite.config.ts` 在 `command === 'build'` 且缺 key 时打印醒目警告（把「静默产出一个永远显示未配置的 bundle」变成构建时就能看见的错误），并额外接受 `process.env.VITE_AMAP_JS_KEY` 以支持 `VITE_AMAP_JS_KEY=... pnpm build`；`docs/operations.md` 新增「服务器上『拉代码看不到地图』怎么修」runbook（含构建后 `grep dist/assets/*.js` 自检）。双向实测：无 key 构建 → 警告触发且产物 0 命中；带 key 构建 → 产物 1 命中。
- **仍需人工**：① 在高德控制台把 `localhost` 加进该 JSAPI key 的域名白名单（现在前端会明确提示这一点，不再是空白地图）；② `AMAP_JS_SECURITY_CODE` 尚未配置到 nginx `$amap_jscode`（本地开发不需要，上线需要）；③ 完整 14 服务 `demo-smoke.sh` 真机回归仍未做（内存不足，需在内存充足的机器或服务器上跑）；④ SSE 并发压测（**只能 staging**）。

**S45（2026-07-20，分支 `claude/amap-real-provider-verification`）：线上地图定位故障闭环 + 部署漂移门禁**——用户在 `woxiangchuanaj.top/o2o/` 实测发现地图瓦片能显示，但“使用我的当前位置”总提示无法解析。Chrome 复现证明浏览器定位权限已经授予；故障发生在坐标交给后端逆地理编码之后。

- **根因不是浏览器定位**：服务器直连运行中的 `map-service :8107`，`GET /api/maps/cities` 返回 `404 NOT_FOUND`；服务器源码 HEAD 已包含 `/api/maps/cities` 与 `/api/maps/reverse-geocode`，因此确定是**运行 JAR 落后于源码**。同时服务器秘密配置检查显示根 `.env` 的 `MAP_PROVIDER` 为空、`AMAP_API_KEY` 未配置；即使只重建 JAR，也只会回到 Demo Provider，不能解析真实位置。
- **服务器修复**：使用本地 gitignored Web 服务 Key 创建 `deploy/systemd/env/map-service.env`（仅 `MAP_PROVIDER=amap`、`AMAP_API_KEY`，权限 `600`，Key 全程无终端回显），由既有 systemd 模板只注入 map-service。先停服务，再执行 `./mvnw -f backend/pom.xml -pl map-service -am clean package -DskipTests`，构建 45.576s `BUILD SUCCESS`；重启后 `systemctl is-active` 为 `active`，直连 `/api/maps/cities` 从 404 变为 200，且 `demoProvider=false`。真实 Key 未写入 Git、日志或本文件。
- **Chrome 真机回归**：沿用同一已登录模拟用户，再次点击“使用我的当前位置”，地点选择弹窗成功关闭，出发地从空值变为高德逆地理编码返回的真实地点名称，地图同步移动标记；页面无定位错误。该结果同时证明：浏览器 Geolocation → WGS84 → Gateway → map-service → 高德逆地理编码 → `LocationRef` → 地图更新整条链路已打通。出于隐私原因，本文件不记录用户的具体定位结果。
- **防复发代码**：`.gitignore` 明确忽略 `deploy/systemd/env/map-service.env`，新增无真实值的 `.example`；`scripts/check-deployment.sh` 新增地图运行态契约，验证 `/api/maps/cities`、`/api/maps/reverse-geocode`，并支持 `EXPECT_REAL_MAP_PROVIDER=true` 强制 `demoProvider=false`；`docs/operations.md` 增加“瓦片正常但当前位置失败”的诊断表和正确 Maven 聚合器构建命令。这样旧 JAR、缺 Web 服务 Key、Provider 漂移都会在部署验收中直接失败，不再留给用户点击时才发现。
- **验证**：线上接口 200 + `demoProvider=false`；线上 Chrome 定位与地图标记成功；`bash -n scripts/check-deployment.sh`、`git diff --check` 与真实 Key 泄漏扫描（拟提交文件 0 命中）通过；`./scripts/verify.sh` 全绿（**293 后端测试**，两端 typecheck/build）。仍未执行的只有原有 staging SSE 并发压测与完整 14 服务 `demo-smoke.sh`。

**S46（2026-07-21，worktree 分支 `claude/message-center-login-codes-a0ffd5`，未 commit/push）：登录验证码隔离 + 真实消息中心 + 乘客-司机私信 + 交互式地图 + 定位打通 + 距离计价 + 演示虚拟行程**——一整轮产品化改造，八个工作包（WP1–WP8），API 契约/鉴权/脱敏/demo 隔离语义全程不降级。后端测试从 293 增至 **355**，Playwright 从 13 增至 **15**，`./scripts/verify.sh` 全绿。

- **WP1 登录码隔离**：验证码**彻底移出消息中心**。demo 明文只存 auth-service 内 `DemoLoginCodeStore`（Redis/内存），键 `(phone, challengeId)`，只由 `DemoSmsProvider` 写（该 bean 仅在 `providers.sms.type=demo` 存在→非 demo 结构性无明文）。`POST /api/auth/sms-code` 返回不透明 `challengeId`（非验证码）；`GET …/demo-inbox` → `POST /api/auth/sms-code/demo-peek {phone, challengeId}`（POST body 不泄露手机号，错误/过期 challenge 与「未收到」不可区分，双闸门 `app.demo.login-code-peek-enabled` + 每手机号限流）；登录成功/锁定/TTL 即删明文，绝不入日志/审计。notification-service `V2` 清除历史 `AUTH_SMS_CODE` 行、`notify` 拒绝该 category（`CATEGORY_NOT_INBOXABLE`）、列表兜底排除；删除 auth 的 `NotificationFeignClient` 与 notification 的 `GET /internal/latest`。前端登录页只在组件态持 challengeId/code、不落 localStorage。`demo-smoke.sh`/`check-deployment.sh` 改用 challenge 取码。
- **WP2 生产消息中心**：demo `/api/demo/inbox` 删除，升级为生产 `/api/inbox`（JWT + `X-User-Id` 归属）：keyset 分页 + category 过滤 + `unread-count` + `read`/`read-all` + `reveal`（owner+TTL+审计，脱敏+显式 reveal 成为生产不变量）。notification `V3` 加 `link_type`/`link_id`/`dedupe_key` + 索引；`DeliveryRecord` 加 `linkType`/`linkId`/`cursor`/`revealable`。网关路由 `notification-demo-inbox` → `notification-inbox`（`/api/inbox/**`）。前端两壳消息页重构（分类 chip、未读、加载更多、深链）。`inbox-enabled` demo 开关退役。
- **WP3 领域事件驱动通知**：`backend/common` 新增 `NotificationCategory` 枚举消除字符串漂移。order-service `V5` `order_notification_outbox` + `OrderNotificationOutboxPublisher`（@Scheduled，`dedupeKey=event_id` 至少一次 + 接收端去重），在 create/markPaid/timeout/cancel/complete 各迁移的同事务写 outbox（乘客+司机双向、最小 PII）；driver-service 审核结果 best-effort 直连 Feign；trip-service `V6` `departure_reminder_sent_at` + `TripDepartureReminderService`（@Scheduled 扫描，发车前 30min 提醒司机+LOCKED 乘客，marker + dedupeKey 双重防重）。
- **WP4 乘客-司机私信**：托管在 notification-service（避免第 15 个 JVM，主机内存超卖）。`V4` `chat_conversations`（`order_id` 唯一）+ `chat_messages`（`(conversation_id,sender_id,client_msg_id)` 唯一）。`/api/conversations` 全套；参与者从权威订单/行程记录服务端派生（新增**内部** `GET /internal/orders/{orderId}`），**非参与者每个端点 404**（含 operator，v1 无运营端点）；`clientMsgId` 幂等发送、body 1–500 + 拒控制字符、限流、发送前复核订单未取消（60s 缓存 fail-closed）；隐私上只暴露对方角色标签 + 每消息 `mine` 布尔。5s 轮询（同 driver-location，EventSource 不能带 Authorization）。网关加 `/api/conversations/**` 路由。前端 `ChatWindow`/`ConversationList`（失败重试同 clientMsgId），行程订单卡「联系司机/乘客」，消息页「通知/私信」分段，tab 红点合并未读。
- **WP5 距离计价**：`PricingPolicy` = `max(minFare, base + max(0, km − includedKm) × perKm)`，全 `BigDecimal`，取整规则文档化 + 测试矩阵（零/低于/等于/略高于 included、长途、小数、取整边界、负配置、minFare 下限）。新 `PriceBreakdown` 记录；`TripOffer` 加 `priceBreakdown`；trip `V7` 存计价组件（base/includedKm/perKm/minFare）保证展示与落库价一致。新 `POST /api/trips/route-preview`（唯一乘客侧计价权威，限流 30/min）。默认：base ¥6.00 含 3km，¥1.20/km 超程，min ¥6.00，CNY（env `TRIP_PRICING_*`）。前端 `PriceBreakdownRows` 只渲染服务端值。
- **WP6 交互式地图 + 定位**：新 zustand `routeSelection` store（origin/destination/preview）两壳 + 缩略图 + 展开地图共享；`ExpandedMapModal`（全屏交互地图：缩放控件、拖拽/点选落点→服务端逆地理、定位按钮 + `AMap.Circle` 精度圈、POI 搜索复用 `LocationSearchSheet`、交换/清除、名称/地址/距离/时长/价格卡、确认路线→store）。`TripMap` 加 opt-in `interactive`；`amapLoader` 加插件加载。`useGeolocation` 加 secure-context + `navigator.permissions` 预检 + poor-accuracy（>100m）警告 + 手动校正流；无 IP 兜底。AMap 仍仅瓦片/手势，所有解析走 map-service。Playwright 新增 `expanded-map.spec.ts` + poor-accuracy 用例。
- **WP7 演示虚拟行程**：新 demo flag `app.demo.virtual-trips-enabled`。trip `V8` `source` 列 + `TripSource` 枚举 + `TripOffer.source`。trip-service `DemoTripController`（`POST /api/demo/trips/generate|random`）+ `DemoTripGenerator`（一次权威报价 → 同 `PricingPolicy` 计价、**价格零浮动**、seed 确定性、5 条/路线、合成 `demo-driver-N` 不可鉴权、replace-not-accumulate、限流 5/min、24h 清理、司机准入 bypass 限于该类）。map-service 内部 `GET /internal/maps/demo-places`（404 除非 demo provider 激活，绝不烧配额）。网关 `/api/demo/trips/**` 路由（JWT，非 operator）。前端 demo 模式下「生成演示行程」+「随机路线」，`source=DEMO` 打「演示」徽标。
- **WP8 文档 + 验证**：新增 `docs/adr/0004-communication-center-and-chat.md`；`docs/api-contract.md`（Auth demo-peek、Message Center、Chat、route-preview + 计价公式、演示虚拟行程、demo-places 全部改写）、`docs/security.md`（登录码生命周期、chat authz、生产收件箱、演示虚拟行程）、`docs/demo-mode.md`（demo-peek、虚拟行程、移除 demo inbox）刷新。
- **新增/变更路由**：`+/api/inbox/**`、`+/api/conversations/**`、`+/api/demo/trips/**`；`-/api/demo/inbox/**`（删）；`GET/api/auth/sms-code/demo-inbox`（删）→ `POST /api/auth/sms-code/demo-peek`。新增内部（不经网关）：`GET /internal/orders/{orderId}`、`GET /internal/maps/demo-places`。
- **验证**：`./mvnw test` **15 模块 355 测试全绿**（较 S45 的 293 新增 62）；`pnpm -C apps/user-h5 typecheck`/`build` 全绿；Playwright **15 passed**（原 13 + expanded-map + poor-accuracy）。**尚未做**：完整 Docker 全栈 `demo-smoke.sh`（本机内存/Docker 状态见既往记录）、staging SSE 压测、`git commit/push`（等用户指示）。

**S47（2026-07-21，同分支，未 commit/push）：用户复测报两个残留问题的根因闭环**——(1) 登录码仍出现在消息中心；(2) 随机路线不生成可用拼车行程。定位后修复真实后端生命周期与生成逻辑，而非只改前端展示。

- **问题 1 根因**：写路径其实已闭合（`DemoSmsProvider` 只写 `DemoLoginCodeStore`；`notify` 拒 `AUTH_SMS_CODE`；`/api/inbox` 已排除），残留在于**清理只覆盖精确 `AUTH_SMS_CODE`、一次性 DELETE**，且**跨用户运营台 `findRecent` 未排除**登录码——历史行/等价拼写（`SMS_CODE` 等）会在库里留存或从运营台泄露。**修复**：新增 `LoginCodeCategories`（`AUTH_SMS_CODE`/`SMS_CODE`/`LOGIN_CODE`/`VERIFICATION_CODE` 单一真源），`notify` 拒绝整集、**三条读路径（用户收件箱 + 未读计数 + 运营台）全部 `category not in (…)` 排除**、新增**幂等可重复**迁移 `V5__purge_all_login_code_deliveries.sql` 广清整集。新增 `DemoSmsProviderTest`（结构性断言唯一协作者是 store，绝不触达通知）。
- **问题 2 根因**：`demoPlaces(null)` 返回 4 城全部 14 个 fixture，`generateRandom` 随机取两点→**可能跨城（厦门→哈尔滨 ~2000km）**；**完全没有距离区间校验/重试/最大尝试**；发车 15m..3h 超出 ±2h 搜索窗；**前端 `useGenerateRandomDemoTrips` 生成后不设置路线选择**，`useTripSearchQuery` 从不为生成端点搜索→行程虽落库但从不经真实搜索流展示。**修复**：新增 `trip.demo.*` 配置（min/max/preferred 距离、maxAttempts、offers、发车 lead/spread、retention）；重写 `generateRandom`——**同城**取点（`cityCode` 省略时按 seed 定城）→ 权威 map-service 报价 → 读权威距离 → 仅落 `[min,max]` 接受（优先 preferred band）→ 否则换对**重试**至 `maxAttempts` → 无合格返回 `422 DEMO_NO_VALID_ROUTE`；发车落在 `[lead, lead+spread] ≤ 匹配窗`；端点响应改为**信封** `{origin,destination,route,offers}`；前端生成后**采纳该路线为当前选择**，普通 `GET /api/trips/search` 返回持久化的演示行程（真实搜索流，非伪造卡片）。新增 `DemoTripGeneratorTest` 覆盖同城/区间重试/最大尝试报错/经真实搜索命中/发车在窗内。
- **验证**：`./scripts/verify.sh` 全绿（后端 15 模块 **363 测试全绿**、两端 typecheck/build）；Playwright **17 passed**（新增 `demo-flow.spec.ts`：消息中心无登录码 + 随机路线生成在区间内且经搜索出现）。仍未做：Docker 全栈 `demo-smoke.sh`、staging SSE 压测。

**S48（2026-07-22，分支 `llfzzz/fix-server-deploy-login-code-runbook`）：登录码线上回归 + 服务器启动方式纠偏**——S46/S47 已随 PR #9 合并到 `main`（`260ffd6`），本轮没有重复修改已经正确的验证码业务代码；线上故障来自旧运行手册绕过 systemd，而不是验证码隔离失效。

- **线上回归**：服务器源码与本地均为 `260ffd6`；真实登录页完成「发送 -> 显式查看 -> 手工输入 -> 登录」，登录成功后页面不再保留验证码；随后进入 `/api/inbox`，通知页返回「暂无消息」，刚生成的登录码没有进入消息中心。源码同时由 `SmsCodeService.verify` 保证登录成功/锁定即清理 hash 与 demo 明文，notification-service 的写入口、用户列表/未读数、运营列表均拒绝或排除全部登录码分类，V2/V5 迁移负责历史清理。
- **部署事故根因**：旧 `docs/operations.md` 仍写 Gateway `:8080` 并推荐 `pkill ...; scripts/start-services.sh`。线上 Nginx 实际代理 `127.0.0.1:8120`，且 `deploy/systemd/env/gateway-service.env` 也固定 `SERVER_PORT=8120`；旧命令杀掉 systemd 管理的 14 个 JVM 后，把 Gateway 以开发默认端口 8080 拉起，导致 `/o2o-api/**` 全部 502。已用 `systemctl start o2o@gateway-service` 恢复 8120，13.134s 启动完成并由浏览器通过真实登录验证。
- **防复发**：`docs/operations.md` 的线上升级流程改为 systemd 管理、Gateway 8120；明确 `scripts/start-services.sh` 只用于本地/临时开发栈，线上禁止使用，避免端口、资源限制、环境文件和进程监督同时漂移。
- **验证**：`./scripts/verify.sh` 全绿（后端 **366 tests**、0 failures/0 errors；两个前端 typecheck/build 均通过）；另单独运行 auth-service + notification-service 切片测试，两模块各 26 tests 全绿。

**S49（2026-07-23，已随 PR #11 合并 main，merge commit `6376e30`）：性能与可扩展性优化 Round 1——安全、可单测、可配置回滚的一批修复**——先做了仓库落地的性能审计（JVM/Redis/MySQL/限流/锁/可观测全量取证，含三个只读 Explore agent 交叉核对），产出计划并经用户逐条确认后实施「仅安全修复」这一轮；不改 API 契约、不改安全语义、不动 outbox/RabbitMQ 架构、零新增 Redis/JVM 占用。`./scripts/verify.sh` 全绿（后端 15 模块全部通过、两前端 typecheck/build 通过；较基线新增约 10 个单测）。八个切片：

- **Redis 淘汰安全**：本项目 Redis 里每个 key 都是带 TTL 的安全/正确性状态（短信码+尝试计数、refresh token、支付 nonce、限流窗口、司机在线位置），没有可淘汰缓存（地图路线缓存在 MySQL）。`docker-compose.lowmem.yml` 的 `allkeys-lru` 属纯风险（可能在 TTL 前淘汰掉有效 token/nonce → 重放或强制登出），改为 `noeviction`；基础 compose 本就是默认 noeviction。`docs/security.md` Docker 节新增该不变量。
- **MySQL 索引/查询**：① admin 仪表盘「待审司机数」从「Feign 拉全部 case（含 OCR/文件 JSON）再 Java 过滤计数」改为 driver-service 新内部端点 `GET /internal/drivers/verification-cases/pending-review-count`（走 `idx_driver_verification_status` 的 `count(*)`）；② `OrderRepository.list` 加按 rider 拆分+强制 `limit`（默认 200，可选 `limit` 参数向后兼容），admin 全 null 路径不再全表扫+filesort；③ 新增 Flyway 索引 `idx_orders_created`（今日计数）、`idx_trips_geo_seek (status,origin_lat,origin_lng,departure_at)`（邻近搜索纬度可 seek，保留旧 `idx_trips_geo` 待 EXPLAIN 验证后再定夺）、`idx_notif_user_id_seq (user_id,id)`（收件箱 keyset 去 filesort），均为可回滚增量、纯 B-tree（H2 兼容）；④ 五个 outbox 扫描 `order by id` → `order by next_attempt_at, id` 对齐 `(status,next_attempt_at,id)` 索引去 filesort；⑤ `UserRepository.upsert` 从「读后写」改为幂等的「先 update 命中即返回，否则 insert，撞唯一键回退 update」（H2/MySQL 双兼容，避免并发首次 upsert 撞唯一键）。
- **Hikari/Feign 显式化**：10 个落库服务 `application.yml` 显式声明 `spring.datasource.hikari.*`（`HIKARI_*` 可覆盖），不再依赖启动参数否则回落 10 连接默认；systemd Feign 读超时 90s→30s（下游卡死更快释放线程，30s 仍是实测最差冷启动 ~14.5s 的约 2 倍）。
- **单键原子操作**：`RedisSmsCodeStore.incrementAttempts` 的 `INCR`+分离 `EXPIRE`（崩溃会留无 TTL 计数键→永久锁死）改为单条 Lua（复用限流器范式）；`RefreshTokenStore` 新增原子 `rotate`（Lua CAS，两端幂等），refresh 轮换从「读-比较-写」三次往返改为一步 CAS，杜绝并发刷新双双成功/误判。新增并发轮换「恰好一个胜出」测试 + 两个 Redis store 的原子性单测（Mockito）。
- **可观测性**：`micrometer-registry-prometheus` 加入根 `backend/pom.xml`（覆盖 14 服务），`/actuator/prometheus` 从 404 变为可抓取（附带 JVM/GC/线程/HikariCP pending/HTTP 计时内建指标）；自定义低基数指标 `gateway.ratelimit.decisions{bucket,outcome}` 与 `order.seatlock.reconciliation{outcome}`（标签绝无用户/IP/手机号/token）。
- **网关限流正确性**：`clientIp` 此前只读 socket peer，nginx 后所有匿名流量共用「代理 IP」一个桶（把 auth 20/60s 压成全局）。现受信代理（默认 loopback 上的 nginx + 可配 `security.rate-limit.trusted-proxies`）读 `X-Real-IP`/`X-Forwarded-For` 取真实客户端 IP；429 加 `Retry-After`；内存限流器超阈值清理过期窗口不再无界增长。仍是内存态（单网关够用），分布式限流+网关加 spring-data-redis 留待横向扩展（当前 `RATE_LIMIT_BACKEND=redis` 在网关是空开关，已在文档注明）。
- **座位库存对账兜底**：order-service 新增 `@Scheduled reconcileCancelledSeatLocks`——对最近取消（默认 30min lookback，走 `idx_orders_created`）的订单重放幂等 `releaseSeats`，修复「订单已取消但 releaseSeats Feign 失败导致座位漏放」的跨服务漂移；`releaseSeats` 幂等（已 RELEASED 直接返回），只放真漏掉的锁，绝不超卖。反向漂移（已锁座订单丢锁）不自动修复（重锁可能超卖），仅留待后续+检测指标。
- **文档**：`docs/operations.md`（新「性能与可扩展性基线 Round 1」节 + EXPLAIN 取证命令）、`docs/load-testing.md`（Round 1 小节 + 可观测已就绪，指出 p95/GC/Hikari-pending 基线现可抓取）、`docs/security.md`（Redis 不淘汰不变量 + 限流真实 IP）、`.env.example`（HIKARI_*/RATE_LIMIT_MAP_*/受信代理/Feign 超时/对账 knobs）。
- **有意未做 / 缺口（如实记录）**：① SQL 改动的 before/after `EXPLAIN ANALYZE` 未取证——本机 Docker demo MySQL（3307）未起、原生 MySQL（3306）凭据未知（不猜测、不改动用户库），命令已写入 `docs/operations.md` 待有已灌数非生产库时补齐；正确性由各仓库 H2 切片测试覆盖。② JVM/GC 按环境重调（需压测硬件）、分布式网关限流、新增 Redis 读缓存、地图 regeo/suggest/POI 的 stale 降级、订单↔座位 outbox/saga、无用索引清理、读侧 IDOR 收口——均记录为后续测量轮。③ 完整 Docker 全栈 `demo-smoke.sh`（FAILS=0）与 staging SSE 阶梯压测仍需运行态环境，本轮未跑。

**S50（2026-07-23，分支 `llfzzz/perf-redis-cache-distributed-limiter`，从合并后的 `origin/main` `6376e30` 切出，未 commit/push）：性能与可扩展性 Round 2——真实 Redis 读缓存 + 缓存雪崩/穿透/热点防护 + 分布式缓存填充租约 + 真正分布式的网关限流**——保持全部业务/安全/幂等/状态机不变，架构决策见 `docs/adr/0005`。`./scripts/verify.sh` 全绿（后端 15 模块，含真实 Redis 的 Testcontainers 集成测试；两前端 typecheck/build 通过）。六个切片：

- **两套 Redis 角色（实例级隔离，非逻辑 DB）**：① **State Redis**＝既有实例，只放带 TTL 的安全/正确性状态，保持 `noeviction`（本轮不动）；分布式**限流计数**放这里（属正确性状态）。② **Cache Redis**＝新增、可选、可丢弃实例（`docker-compose.cache.yml`：`allkeys-lfu`、关持久化、限 `maxmemory`、独立端口 6380/容器/失败域），只放**派生可重建**数据（地图路线读缓存 + 缓存填充租约）。默认关闭（`map.route-cache.redis.enabled=false`），**低内存生产 Demo 主机零新增占用**；有内存余量的机器用 overlay 开启。缓存丢失绝不污染权威状态。
- **地图路线 Redis 读缓存（map-service）**：MySQL 快照之上的 cache-aside。查找序：校验 → Redis 新鲜 → MySQL 新鲜（回填 Redis）→ Provider（走既有熔断）→ 存 MySQL → 回填 Redis。**版本化 String** 键 `cache:map:route:v1:<sha256(归一化键)>`（键无坐标/凭据/用户标识、有界长度、schema 版本）；值为确定性 JSON。**任何 Redis 失败一律降级为 miss**，绝不掩盖凭据/城市/坐标/Provider 错误、绝不伪造路线；端点（LocationRef）永不入缓存，按请求逐调用重贴。**TTL＝base+抖动，且被源快照剩余新鲜度上限封顶**（老快照不会拿到全新完整 TTL）；超大 payload 拒缓存、坏值忽略并删除修复、短值防穿透由既有校验前置完成。加 `map.route.redis.cache.{requests,populate,loads}` 指标。
- **分布式缓存填充租约（Cache Redis）**：在既有进程内 single-flight 之上加跨实例去重。`SET key token NX PX` 获取、强随机 owner token、Lua 原子 compare-and-delete 释放（绝不裸 `DEL`）、有界租期 + 有界等待；输家有界退避轮询缓存，命中即返回，超时则安全走 MySQL/Provider（重复 Provider 调用可接受，错误数据不可接受）。**只用于缓存填充，绝不触碰座位/订单/支付/身份/鉴权正确性**。加 `map.route.cache.lease{outcome}` 指标。放在 map-service，不入 `common`。
- **真正分布式的网关限流**：`RATE_LIMIT_BACKEND=redis` 在 gateway 真正生效——加 `spring-boot-starter-data-redis-reactive`，用 `ReactiveStringRedisTemplate` + 原子 Lua（`INCR`+`PEXPIRE`+`PTTL`），**绝不在 Netty 事件循环上做阻塞 Redis 调用**。保留桶定义/限额/受信代理真实客户端 IP/按用户键控/独立地图桶；`Retry-After` 取自 Redis 真实窗口剩余。限流键放 **State Redis**。选 redis 后端但缺反应式模板＝**启动失败**（不静默回退内存）。Redis 故障进入**有度量的降级模式**：有界本地内存应急限流，或（可配 `fail-closed-when-degraded`）对敏感桶（auth/map/payment-callback/demo-control）fail-closed，绝不「静默无限」。单机 Demo 仍用内存后端。加 `gateway.ratelimit.decisions{...,backend}` 与 `gateway.ratelimit.degraded{bucket}` 指标。
- **测试（真机 Redis 已跑通）**：`RedisRouteCacheTest`（TTL 抖动上下界 + 老快照封顶，纯单测）；`RouteCacheRedisIntegrationTest`（Testcontainers：put/get 往返、坏值忽略+修复、超大拒缓存、租约独占+仅 owner 释放+过期可重获、**两实例并发 miss 只调一次 Provider**）；`RedisReactiveRateLimiterIntegrationTest`（Testcontainers：**两实例共享一份配额**、并发不超限、窗口过期重置、Retry-After=窗口剩余、不同键独立）。`@Testcontainers(disabledWithoutDocker=true)` 保证无 Docker 主机上 `mvn test` 仍绿。全部既有测试保留通过。
- **配置/回滚/文档**：新增 `docs/adr/0005`；`docs/architecture.md`/`operations.md`/`load-testing.md`/`security.md` 与 `.env.example`、`docker-compose.cache.yml` 更新。回滚全走配置：关 `map.route-cache.redis.enabled` 回到 MySQL/Provider；`RATE_LIMIT_BACKEND=memory` 回到单网关内存限流；移除 cache overlay 不影响 Demo 栈启动；无需数据库回滚。
- **运行态/压测证据（如实分级）**：① **代码完成**＝全部六片；② **单测/集成完成**＝上述真机 Redis Testcontainers 全绿 + `verify.sh` 全绿；③ **运行态验证**＝`docker compose -f docker-compose.yml -f docker-compose.cache.yml config` 通过、compose overlay 校验 allkeys-lfu/6380；④ **仍缺的负载证据**＝多网关/多 map-service 实例对共享 Redis 的阶梯压测、缓存命中率与 Provider 调用/`route_snapshots` 读次数的 before/after、p50/p95/p99、Redis 内存与 429 曲线——需要非生产 staging + 足够硬件，本轮未跑（严禁指向生产域名）。

## 已完成 — Demo Mode 阶段详情

以下每一项都已经过对应模块的单元测试验证、`git commit` 到 `main` 并 `git push` 完成，可用 `git log --oneline` 核对提交哈希。

### Phase 0 — Profile、Demo Mode 开关、密钥硬化（3 commits）

- **S1** `3c536eb` `feat(config): provider/profile scaffolding for demo mode`
  - `backend/common`：新增 `AppProperties`（`app.demo-mode` + `app.demo.{inbox,control,seed}-enabled`，全部默认 `false`，fail-closed）与 `ProviderProperties`（`providers.{sms,ocr,payment,identity,map,notification}.type`，默认空字符串表示未配置）。
  - 新增共享配置 `backend/common/src/main/resources/carpooling-providers.yml`：`demo`/`staging`/`production` 三个 Spring profile 文档，`demo` 打开全部 Demo Provider 和收件箱/控制台，`staging`/`production` 一律关闭并把 provider type 交给环境变量（无默认值）。
  - 全部 12 个既有服务的 `application.yml` 加上 `spring.config.import: "optional:classpath:carpooling-providers.yml"`。
  - 测试：`CarpoolingProvidersYamlTest`、扩展 `BackendFoundationAutoConfigurationTest`，验证 demo 文档打开一切、staging 文档 fail-closed。
- **S2** `83ed09c` `feat(security): fail-closed secrets, no committed secret material`
  - 移除 `SecurityProperties.Jwt.base64Secret` 里硬编码的示例 HS512 密钥默认值，以及 gateway/auth/user 三个 `application.yml` 里内联的相同占位密钥。
  - `JwtTokenService` 的 Bean 改为 `@Lazy`：只有真正注入它的服务（gateway、auth）会构造它，空密钥只在真正用到的地方 fail-closed，不连累其它服务启动。
  - 新增 `SecretsValidator`（仅 `staging`/`production` profile 生效）：以 **SHA-256 哈希比对**（仓库内不出现任何密钥明文）拒绝已知 Demo 密钥，同时拒绝形如 `replace-with-*`、`changeme` 等占位符特征。
  - 重写 `.env.example` 为纯占位符模板；新增 `.env.demo.example`（Demo 专用、非敏感的本地中间件账号密码 + 密钥占位符）；新增 `scripts/generate-local-env.sh`，一键生成带随机密钥、权限 600、gitignored 的本地 `.env`。
  - 测试：`SecretsValidatorTest`（占位符拒绝、强随机密钥通过、空值跳过）。
- **S3** `576c412` `feat(security): DemoModeGuard fail-closed profile invariants`
  - 新增 `DemoModeGuard`：启动期强校验——`staging`/`production` 下 `app.demo-mode=true` 直接拒绝启动；`staging`/`production` 下任何 Demo 收件箱/控制台/seed 开关为真也拒绝启动；任何 Demo 开关为真但 `app.demo-mode=false` 同样拒绝启动（双重闸门）。
  - 测试：`DemoModeGuardTest`（5 个场景全覆盖）。

**验证**：`./mvnw -pl common test` 全绿（18 tests after S2, 23 after S3）。

### Phase 1 — notification-service + Demo Delivery Center（2 commits）

- **S4** `12f546f` `feat(notification): new notification-service with channel SPI + delivery outbox`
  - 新建 Maven 模块 `backend/notification-service`（已注册进 `backend/pom.xml` 的 `<modules>`）。
  - `NotificationChannelAdapter` SPI（`supports(ChannelType)` / `send(NotificationMessage)`），`DemoNotificationChannelAdapter` 在 `providers.notification.type=demo` 时生效，直接把消息写进 Demo 收件箱表。
  - `NotificationService`：路由到对应 Provider、把敏感 payload 做遮罩（用 `•` 替换，截断到 255 字符）后落库为 `DeliveryRecord`；无可用 Provider 时 fail-closed（`NOTIFICATION_PROVIDER_UNCONFIGURED`）。
  - Flyway `V1__create_notification_deliveries.sql`：`notification_deliveries` 表，含 `user_id` 索引、`revealable_payload` + `reveal_expires_at`（TTL 过期后不可再取出）、`status`、`correlation_id`、`retry_count`。
  - 内部 API `POST /api/notifications`（服务间调用，Feign，**不经过 Gateway 对外暴露**）。
  - 测试：`NotificationServiceTest`（5 个用例：遮罩保存但 TTL 内可取出、TTL 后拒绝取出、收件箱按用户隔离、无 Provider fail-closed、拒绝空收件人）。
- **S5** `c4f52ab` `feat(notification): Demo Inbox + Demo Control APIs, user-scoped & gated`
  - `DemoInboxController`（`/api/demo/inbox`）：`GET` 列表、`POST /{id}/reveal`（显式取出，TTL 内、且必须是本人）、`POST /{id}/read`。全部严格按 Gateway 注入的 `X-User-Id` 隔离，一个用户绝不可能看到另一个用户的记录。
  - `DemoNotificationControlController`（`/api/demo/control/notification`）：运营驱动 delivered/failed/retried/read 状态模拟。
  - `DemoEndpoints` 网关：任一 Demo 开关关闭时返回 `404 DEMO_ENDPOINT_DISABLED`（而不是 403，让 Demo 端点在非 Demo 环境下与「不存在」不可区分）。
  - Gateway：新增路由 `/api/demo/inbox/**`、`/api/demo/control/notification/**`；`/api/demo/control/**` 整体纳入 OPERATOR/ADMIN RBAC。
  - 测试：新增 4 个 service 用例（reveal 仅限本人、mark-read、运营驱动状态、未知 delivery fail-closed）+ 2 个 Gateway RBAC 用例（rider 禁止访问 Demo 控制台、rider 可访问自己的收件箱）。

**验证**：`./mvnw -pl notification-service,gateway-service -am test` 全绿（notification-service 9 tests，gateway-service 11 tests）。

### Phase 2 — Auth/SMS 安全加固 + 交互式登录（4 commits）—— 本轮最关键的安全修复

> ⚠️ 修复前的状态：`AuthController.login` **从不校验验证码**，且请求体接受客户端自选的 `roles` 字段——任何客户端可以直接请求 `ADMIN` 角色，属于严重的权限提升漏洞。本 Phase 已彻底关闭这两个漏洞。

- **S7+S8（合并一次提交）** `d69b822` `feat(auth): server-side SMS code + close privilege escalation`
  - 新增 `SmsProvider` SPI（`send(SmsSendCommand)`），`DemoSmsProvider` 在 `providers.sms.type=demo` 时把验证码投递到通知服务的 Demo 收件箱（**验证码绝不在 `POST /api/auth/sms-code` 的响应体里返回**）。
  - 新增 `SmsCodeService`：验证码用 SecureRandom 生成、SHA-256 哈希后单次使用存储（存的是 hash 不是明文）、常数时间比较防时序攻击、TTL 过期、每手机号发送限流（`FixedWindowRateLimiter`，在 Gateway 每 IP 限流之上再加一层每手机号限流）、连续验证失败自动锁定。
  - `SmsCodeStore` 有 Redis 实现（跨实例）和内存实现（单机/测试）两种，按是否有 `StringRedisTemplate` 自动选择。
  - **重写 `AuthController.login`**：先调用 `smsCodeService.verify()` 校验验证码，通过后经由新的 `UserAccounts`（Feign 调用 user-service）取得或创建用户——新手机号只授予 `RIDER`，角色完全服务端权威。`LoginRequest` 记录类型**已删除 `roles` 字段**，加了一个回归测试断言该记录只剩 `phone`、`code` 两个组件，防止未来被悄悄加回去。
  - 新增 `GET /api/auth/sms-code/demo-inbox`（仅 Demo 环境）：交互式登录用来「查看」最新投递的验证码。
  - notification-service 新增内部端点 `GET /api/notifications/internal/latest`，给 auth-service 查询某用户某分类下最新一条（未过期）投递记录。
  - 测试：`SmsCodeServiceTest`（7 个用例：颁发+验证+单次消费、错误验证码、连续失败锁定、过期拒绝、发送限流、无 Provider fail-closed、非 Demo 环境下收件箱查看被拒）+ 重写的 `AuthControllerTest`（3 个用例）。
- **S9** `1e4e23c` `feat(auth): refresh tokens with rotation, reuse detection & revocation`
  - access token 有效期从 2 小时缩短为 **30 分钟**；新增 `POST /api/auth/refresh`、`POST /api/auth/logout`。
  - `RefreshTokenService`：签发不透明 refresh token（只存 SHA-256 哈希），按「会话族（family）」分组；轮换时推进该族的「当前 token」；如果检测到**已被轮换掉的旧 token 被重放**，视为泄露，直接吊销整个会话族，强制重新登录（`REFRESH_TOKEN_REUSE`）。
  - `RefreshTokenStore` 同样有 Redis / 内存两种实现。
  - 测试：`RefreshTokenServiceTest`（5 个用例：正常轮换、重放检测吊销、显式登出、过期拒绝、未知 token 拒绝）。
- **S10（含 S6 前端部分）** `a0b1e1e` `feat(frontend): interactive SMS login + Demo Inbox in H5`
  - H5 `apps/user-h5/src/App.tsx` 重写：去掉硬编码手机号+验证码+`roles` 的自动登录；新增登录页——输入手机号 -> 发送验证码 -> 打开 Demo 收件箱手动取出 -> 输入验证码 -> 登录。
  - session（access + refresh token）持久化在 `localStorage`；`api()` 请求封装遇到 401 会自动尝试用 refresh token 换新 access token 一次，换不到就清空 session 回到登录页。
  - 新增「收件箱」Tab：列出当前用户的 Demo 投递记录（遮罩预览），点击「查看」显式取出内容。
  - 退出登录会调用 `/api/auth/logout` 吊销 refresh token。
  - 验证：`pnpm typecheck` + `pnpm build` 全绿；真实浏览器端到端验证放在 Phase 9（需要完整 Docker 栈）。

**验证**：`./mvnw -pl notification-service,auth-service -am test` 全绿（auth-service 15 tests，notification-service 9 tests）；前端 `pnpm typecheck`/`pnpm build` 全绿。

### Phase 3 — 支付 Provider + Intent 状态机 + 签名 Webhook（✅ 已完成，5/5 commits）

- **S11（已完成）** `58fcb36` `feat(payment): PaymentProvider SPI + payment-intent state machine`
  - `backend/common` 新增 `PaymentIntentStatus`（`REQUIRES_PAYMENT → AUTHORIZED → SUCCEEDED|FAILED|CANCELED|EXPIRED`）与 `PaymentIntentStateMachine`（显式合法迁移表，终态不可再迁移，非法迁移抛 `IllegalStateException`）。
  - `payment-sim-service` 新增：
    - `PaymentProvider` SPI（`name()` + `createIntent(CreateIntentCommand)`）。
    - `DemoPaymentProvider`（`providers.payment.type=demo`）：创建的 intent 初始状态是 `REQUIRES_PAYMENT`，真正的结果由后续（S13）Demo 控制台驱动的签名回调决定，不在创建时就决定成败。
    - `PaymentIntent` 实体 + Flyway `V2__create_payment_intents.sql`（新增 `payment_intents` 表，`(order_id, idempotency_key)` 唯一键；以及 `payment_callback_events` 表，`event_id` 唯一键，为 S12 的回调幂等/去重做好准备）。
    - `PaymentIntentRepository`：`JdbcClient` 实现，含乐观状态迁移（按预期的 from 状态做条件更新）和回调事件去重记录。
    - `PaymentIntentService`：金额永远从 order-service 读取（不信任客户端）；只有订单本人可以发起支付（`PAYMENT_FORBIDDEN`）；按 `(orderId, idempotencyKey)` 幂等；配置的 Provider 类型找不到对应实现时 fail-closed（`PAYMENT_PROVIDER_UNCONFIGURED`）。
    - 新增 `POST /api/payments/intents`、`GET /api/payments/intents/{id}`。**旧的 `POST /api/payments/simulations` / `simulate-success` 暂时保留**，等 S15 H5 切换到新流程后再考虑下线。
  - 测试：`PaymentIntentStateMachineTest`（3 用例）+ `PaymentIntentServiceTest`（4 用例：创建/幂等/越权拒绝/未配置 Provider fail-closed）。

- **S12（已完成）** `feat(payment): signed payment webhook ingestion (S12)`
  - `payment-sim-service` 新增 `POST /api/payments/callbacks/{provider}` 签名回调摄取端点——payment intent 走向终态的**唯一**路径（无前端后门）。
    - `PaymentCallbackVerifier`：HMAC-SHA256 签名校验（签名串 `"{timestamp}.{nonce}.{rawBody}"`，密钥读 `providers.payment.webhook-secret` / 环境变量 `PAYMENT_WEBHOOK_SECRET`）、时间戳新鲜度窗口（默认 `PT5M`）、nonce 单次使用防重放。校验顺序是**先验签再登记 nonce**，避免未授权请求污染重放库；密钥为空时 `PAYMENT_WEBHOOK_UNCONFIGURED` fail-closed；所有拒绝信息不回显密钥/签名。
    - `SeenNonceStore` SPI + `RedisSeenNonceStore`（`SETNX`，跨实例）/ `InMemorySeenNonceStore`（单机/测试），按是否有 `StringRedisTemplate` 自动选择（`PaymentConfig`，与 auth-service 的 store 选型同构）。为此给 `payment-sim-service` 加了 `spring-boot-starter-data-redis` 依赖和 `spring.data.redis` 配置。
    - `PaymentCallbackService`：按 `event_id` 幂等落库（复用 S11 建的 `payment_callback_events` 表，重复事件为 no-op）；经 `PaymentIntentStateMachine` 迁移，**终态不可被后续/乱序回调覆盖**；仅真正迁移到 `SUCCEEDED` 时才调用 order-service `markPaid`（本身幂等）。回调体 `{eventId,intentId,outcome}`，outcome 必须是四个终态之一。
    - `PaymentCallbackController`：`@RequestBody String rawBody` 读原始字节，先验签再解析 JSON，保证验签对象与调用方签名的字节一致。
  - Gateway：`GatewaySecurityFilter` 把 `/api/payments/callbacks/**` 纳入 public path（PSP 无 JWT，靠 HMAC 鉴权），仍按 IP 限流、仍剥离伪造的 `X-User-Id`/`X-User-Roles`。`/api/payments/**` 路由已存在，无需新增。
  - 测试：`PaymentCallbackServiceTest`（7 用例：成功回调→SUCCEEDED+markPaid 一次、伪造签名拒绝、nonce 重放拒绝、同 event_id 幂等、终态后乱序 FAILED 被忽略、过期时间戳拒绝、无密钥 fail-closed）+ `GatewaySecurityFilterTest` 新增回调 public path 用例。
  - 契约：`docs/api-contract.md` 补齐 Payment Intent（S11）与签名回调（S12）两节。

- **S13（已完成）** `feat(payment): Demo payment control console driving the signed pipeline (S13)`
  - `payment-sim-service` 新增 `POST /api/demo/control/payment/{intentId}/callbacks`（运营触发支付结局）。
    - `DemoPaymentControlService`：运营选 outcome（`SUCCEEDED/FAILED/CANCELED/EXPIRED`）+ mode（`NORMAL/DUPLICATE/OUT_OF_ORDER`）+ `delaySeconds`；服务端**自签合法回调并真正走 S12 的 `PaymentCallbackVerifier` + `PaymentCallbackService` 摄取管道**，绝不后门改状态。`DUPLICATE` 复用同一 `eventId`（演示幂等）、`OUT_OF_ORDER` 追加冲突终态（演示终态不可覆盖）、`delaySeconds` 回拨签名时间戳（超窗被管道以 `PAYMENT_CALLBACK_TIMESTAMP` 拒绝）。每次投递回报 accepted/结果状态/拒绝码，管道保护可观测。
    - `PaymentCallbackSignature`：抽出**唯一**的签名规范（`hex(HMAC-SHA256(secret,"{ts}.{nonce}.{body}"))`），`PaymentCallbackVerifier`（验签方）与新增的 `PaymentCallbackSigner`（demo 自签方）共用，杜绝两边漂移。`PaymentCallbackSigner` 是 demo-only bean（真实 PSP 在自己那边签名）。
    - `DemoEndpoints`（payment-sim-service 本地副本，与 notification-service 同构）：`requireControl()` 双重闸门，非 demo 返回 404。
  - Gateway：新增路由 `payment-demo-control`（`/api/demo/control/payment/**` → payment-sim-service）；RBAC 复用既有 `/api/demo/control/**` 的 OPERATOR/ADMIN 前缀规则（无需改 filter）。
  - 测试：`DemoPaymentControlServiceTest`（6 用例：NORMAL 成功走签名管道、DUPLICATE 幂等、OUT_OF_ORDER 终态不可覆盖、超窗 delay 被管道拒绝、非终态 outcome 拒绝、未知 intent 拒绝）。
  - 契约：`docs/api-contract.md` 补 Demo 支付控制台一节。

**验证**：`./mvnw -pl payment-sim-service,gateway-service -am test` 全绿（payment-sim-service 19 tests：2 旧 simulation + 4 intent + 7 callback + 6 demo control；gateway-service 12 tests）。

- **S14（已完成）** `feat(order): cancel/complete order state transitions with server-side authz (S14)`
  - `backend/common`：`OrderStatus` 新增 `OPERATOR_CANCELLED`；`OrderStateMachine` 新增 `cancelByUser`/`cancelByDriver`/`cancelByOperator`（合法源状态：`PENDING_PAYMENT` 或已支付的 `SEAT_LOCKED`）与 `complete`（`SEAT_LOCKED → COMPLETED`），非法迁移抛 `IllegalStateException`，既有 `pay`/`timeout` 不变。
  - `order-service`：
    - `POST /api/orders/{orderId}/cancel`：从网关注入的 `X-User-Id`/`X-User-Roles` **服务端权威**解析发起人——本人→`USER_CANCELLED`、行程司机（查 `tripClient.findTrip().driverId()`）→`DRIVER_CANCELLED`、OPERATOR/ADMIN→`OPERATOR_CANCELLED`，都不是则 `403 ORDER_CANCEL_FORBIDDEN`。取消经状态机迁移 + `tripClient.releaseSeats`（按 orderId 幂等）+ 审计 `ORDER_CANCELLED_BY_*`；对同一发起人重复取消是幂等 no-op。
    - `POST /api/orders/{orderId}/complete`：仅司机或 OPERATOR/ADMIN 可完成（乘客不能自完成，`403 ORDER_COMPLETE_FORBIDDEN`），`SEAT_LOCKED → COMPLETED`，不释放座位，审计 `ORDER_COMPLETED`，供 Phase 6 评价资格用。
    - `OrderRepository.transition` 的 `cancelled_at` case 增加 `OPERATOR_CANCELLED`；角色头解析复用 file-service 的 `X-User-Roles` 逗号分隔模式。
  - 前端：`apps/user-h5` 与 `apps/admin-console` 的 `OrderStatus` 联合类型都加上 `OPERATOR_CANCELLED`，admin 的 `ORDER_STATUS_TONE` 补一项（`danger`）。Gateway：`/api/orders/{id}/cancel|complete` 已在 `/api/orders/**` 路由内，属受 JWT 保护但非 admin 的普通订单路由，由 order-service 自行按角色鉴权。
  - 测试：`OrderStateMachineTest` +3（各发起人取消、终态不可取消、仅已支付可完成）；`OrderServiceTest` +7（本人取消释放座位且幂等、司机取消、运营按角色取消、陌生人 403、司机完成不释放座位、乘客不可完成、未支付不可完成）。
  - 契约：`docs/api-contract.md` 补 cancel/complete 两节。

**验证**：`./mvnw -pl order-service -am test` 全绿（common 29 tests、order-service 16 tests）；前端两个应用 `pnpm typecheck`/`build` 全绿。

- **S15（已完成）** `feat(frontend): H5 booking via payment intent + cancel + live status (S15)`
  - H5 `apps/user-h5/src/App.tsx` 订座流程从「一键下单 + 自动调 `/api/payments/simulations` 模拟成功」改为真实的 Intent + 回调驱动模型：
    - 「下单锁座」只创建订单（`POST /api/orders` → `PENDING_PAYMENT`），不再自动支付。
    - 新增 `CurrentOrderCard`：展示当前订单的服务端权威状态（`ORDER_STATUS_LABEL`/`ORDER_STATUS_TONE` 覆盖全部 7 个状态含 `OPERATOR_CANCELLED`）；`PENDING_PAYMENT` 时可「发起支付」（`POST /api/payments/intents`），轮询 `GET /api/payments/intents/{id}` 展示支付意图状态；明确提示「支付结果由已签名回调驱动，前端不自行改支付状态，演示中由运营在后台控制台触发」。
    - 「取消订单」按钮（`POST /api/orders/{id}/cancel`，`PENDING_PAYMENT`/`SEAT_LOCKED` 可用）；超时/完成有对应文案。
    - 订单列表查询加 `refetchInterval: 5000`，intent 查询加 `refetchInterval: 4000`，运营/PSP 驱动的回调或支付超时会自动刷新到界面。
  - 说明：H5 已不再调用旧的 `/api/payments/simulations`；乘客端只创建订单/意图并观察权威状态，真正驱动支付结局仍是 S13 的运营 Demo 控制台（其 admin-console 前端 UI 已于 S29 落地，可全程浏览器操作）。
  - 验证：`pnpm -C apps/user-h5 typecheck`/`build` 全绿；真实浏览器端到端（需完整 Docker 栈）仍排在 Phase 9。

### Phase 4 — 实名认证（含活体）Demo Provider（✅ 已完成，3/3 commits）

- **S16（已完成）** `feat(identity): identity-service with DemoIdentityProvider + two-layer state machines (S16)`
  - `backend/common`：新增 `IdentityVerificationStatus`（`PENDING → APPROVED|REJECTED|TIMEOUT|RETRY_REQUIRED`，`RETRY_REQUIRED → PENDING`）与 `LivenessCheckStatus`（`PENDING → PASSED|FAILED|TIMEOUT|RETRY_REQUIRED`）两个枚举，及对应的 `IdentityVerificationStateMachine`/`LivenessCheckStateMachine`（显式合法迁移表，终态不可迁移，照 `PaymentIntentStateMachine` 范式）。
  - 新建 Maven 模块 `backend/identity-service`（端口 8113，已注册进 `backend/pom.xml`）：
    - `IdentityVerificationProvider` SPI（`name()` + `start(StartVerificationCommand)`）；`DemoIdentityProvider`（`providers.identity.type=demo`）创建的会话初始 `PENDING`/`PENDING`，结局不在创建时决定。
    - `IdentityVerification` 实体 + Flyway `V1__create_identity_verifications.sql`（`(user_id, idempotency_key)` 唯一键）；`IdentityVerificationRepository`（JdbcClient，会话/活体各自的乐观迁移）。
    - `IdentityVerificationService`：`start`（证件号服务端脱敏，不落库/不进日志；按 `(userId, idempotencyKey)` 幂等；Provider fail-closed `IDENTITY_PROVIDER_UNCONFIGURED`）、`get`（owner-scoped，`IDENTITY_FORBIDDEN`）、`applyLivenessOutcome`/`applySessionOutcome`（**先经状态机判合法性**再判业务规则；`APPROVED` 要求活体先 `PASSED`，否则 `IDENTITY_LIVENESS_REQUIRED`；非法迁移 `IDENTITY_ILLEGAL_TRANSITION`）。每个会话结局（非 PENDING）**异步投递结果到用户 Demo 收件箱**（Feign 调 notification-service，category `IDENTITY_VERIFICATION_RESULT`），不内联返回。
    - `IdentityVerificationController`（`POST /api/identity/verifications`、`GET /api/identity/verifications/{id}`）；`IdentityDemoControlController`（`/api/demo/control/identity/{id}/liveness|session`，`DemoEndpoints.requireControl()` 双闸门）；本地 `DemoEndpoints` + `NotificationFeignClient`（与既有服务同构）。
  - Gateway：新增路由 `identity-service`（`/api/identity/**`，JWT 保护）与 `identity-demo-control`（`/api/demo/control/identity/**`，复用 `/api/demo/control/**` 的 OPERATOR/ADMIN 前缀规则）。`providers.identity.type` 已在 `carpooling-providers.yml` 三 profile 就绪（demo=demo，staging/prod=`${IDENTITY_PROVIDER:}`），无需改配置。
  - 测试：`IdentityVerificationStateMachineTest` +3、`LivenessCheckStateMachineTest` +3；`IdentityVerificationServiceTest` 8（创建 PENDING、幂等、Provider fail-closed、owner-scoped get、approve 需活体、活体通过后 approve 并投递收件箱、reject 投递、终态后非法迁移拒绝）。
  - 契约：`docs/api-contract.md` 新增 Identity 与 Demo 实名控制台两节。

**验证**：`./mvnw -pl identity-service -am test` 全绿（common 35 tests、identity-service 8 tests）。

- **S17（已完成）** `feat(driver): gate driver capability on APPROVED identity verification (S17)`
  - identity-service 新增 internal 接口 `GET /internal/identity/verifications/status?userId=X`（`InternalIdentityController`，返回 `{ userId, approved }`）+ `IdentityVerificationRepository.existsApprovedByUserId` + `IdentityVerificationService.isUserApproved`。该路径**故意不在 `/api/**` 下**，Gateway 不路由，仅服务间 Feign 可达（与 notification 内部接口同思路）。
  - driver-service 新增 `IdentityClient`（Feign，`identity-service`，`contextId=driverIdentityClient`）；`DriverVerificationController.submit` 改为：以 `X-User-Id` 为准解析用户（body `userId` 仅本地 fallback），**提交司机证件前先校验该用户 identity `APPROVED`**，否则 `403 DRIVER_IDENTITY_NOT_VERIFIED`。这是叠加在既有运营人工复核之上的第一道门禁；顺带收敛了「submit 之前信任 body userId」的历史小问题。
  - 测试：identity-service +1（`isUserApproved` 反映 APPROVED 会话）；driver-service `DriverVerificationControllerTest` +2（未认证提交被拒且不落库、认证通过后可提交）。
  - 契约：`docs/api-contract.md` 的 Driver Verification 一节补门禁说明与内部接口。

**验证**：`./mvnw -pl identity-service,driver-service -am test` 全绿（common 35、identity-service 9、driver-service 6）。

- **S18（已完成）** `feat(frontend): H5 identity verification gate before driver docs (S18)`
  - H5 `apps/user-h5` 「认证」Tab 新增 `IdentityVerifyCard`：输入姓名/证件号 → 「发起实名认证」（`POST /api/identity/verifications`）→ 轮询 `GET /api/identity/verifications/{id}`（`refetchInterval` 4s）展示会话状态 + 活体状态；`APPROVED` 前提示「结果由供应商回调驱动（演示中由运营在后台控制台触发活体 PASS 与会话 APPROVED），异步投递收件箱」。
  - 司机证件提交（既有卡片）改为**只有实名 `APPROVED` 后才可提交**（按钮 `disabled` 叠加 `!identityApproved`，未通过时展示提示 Alert）；与 S17 的服务端门禁双保险。`IdentityVerifyCard` 通过 `onApprovedChange` 回调把审批态提给父组件。
  - 验证：`pnpm -C apps/user-h5 typecheck`/`build` 全绿；浏览器端到端（需完整 Docker 栈）仍排在 Phase 9。

### Phase 5 — OCR Provider 适配（✅ 已完成，1/1 commit）

- **S19（已完成）** `feat(ai): OcrProvider SPI + DemoOcrProvider async task lifecycle (S19)`
  - `backend/common`：新增 `OcrTaskStatus`（`SUBMITTED/PROCESSING → COMPLETED|FAILED`，终态 COMPLETED/FAILED）；`OcrTask` 记录扩展为 `(taskId, fileObjectId, status, providerRef, result?, submittedAt, completedAt?)`（result/completedAt 在完成前可空）。
  - `ai-service`：抽出 `OcrProvider` SPI（`name()` + `submit(OcrSubmitCommand)` + `poll(providerRef)`）；`DemoOcrProvider`（`providers.ocr.type=demo`）包装既有 `MockOcrPolicy` + `OcrResultMasker`，submit→PROCESSING、首次 poll→COMPLETED（脱敏结果），in-flight 状态在进程内（单实例，真实供应商在其侧持有）。`OcrService` 改为持有异步任务生命周期（submit 落 PROCESSING、get 轮询 provider 完成后落库 COMPLETED），Provider 按 `providers.ocr.type` 选型、未配置 `OCR_PROVIDER_UNCONFIGURED` fail-closed，替换掉原来的 `new MockOcrPolicy()` 直连。
  - 新增 `POST /api/ai/ocr/tasks`（异步 submit）、`GET /api/ai/ocr/tasks/{taskId}`（轮询）；保留 `POST /api/ai/ocr/mock`（兼容，同步 submit+驱动完成）。Flyway `V2__ocr_tasks_async_lifecycle.sql` 加 `status`/`provider_ref`/`submitted_at` 列并放开 `result_json`/`completed_at` 可空。移除 ai-service `application.yml` 里已废弃的 `ocr.provider` 配置（统一到 `providers.ocr.type`）。
  - 测试：`OcrServiceTest` 重构为手动构造 service（与 `PaymentIntentServiceTest` 同范式），3 用例（兼容入口完成并脱敏落库、submit→PROCESSING→get→COMPLETED 异步生命周期、Provider fail-closed）。
  - 契约：`docs/api-contract.md` 的 AI 一节改写为 Provider 化 + 异步任务。

**验证**：`./mvnw -pl ai-service -am test` 全绿（common 35、ai-service 3）。

### Phase 6 — 订单评价（order-service 内）（✅ 已完成，2/2 commits）

- **S20（已完成）** `feat(order): order reviews with eligibility, dedup, authz, audit + completion invite (S20)`
  - `order-service` 新增评价领域：`OrderReview` 记录 + Flyway `V4__create_order_reviews.sql`（`order_id` 唯一 → 每订单一条）+ `OrderReviewRepository`（JdbcClient）。
  - `OrderReviewService.submit`：**服务端权威资格校验**——订单必须 `COMPLETED`（`REVIEW_ORDER_NOT_COMPLETED`）、只有该订单乘客可评（`REVIEW_FORBIDDEN`）、rating 1-5（`REVIEW_RATING_INVALID`）、comment ≤500（`REVIEW_COMMENT_TOO_LONG`）、每订单一次（预检 + `order_id` 唯一键兜底 `DuplicateKeyException`→`REVIEW_ALREADY_SUBMITTED`）、写审计 `ORDER_REVIEW_SUBMITTED`；`get` 读评价（`REVIEW_NOT_FOUND`）。
  - `OrderController` 新增 `POST /api/orders/{id}/review`（`X-User-Id` 为评价人）、`GET /api/orders/{id}/review`。
  - 订单完成投递评价邀请：新增 `NotificationClient` + `FeignNotificationClient`（**best-effort**，通知失败不回滚完成），`OrderService.complete` 成功后向乘客收件箱投递 `ORDER_REVIEW_INVITATION`。
  - 测试：`OrderReviewServiceTest` 6（完成订单本人评价+审计、未完成拒绝、非本人拒绝、非法评分拒绝、重复拒绝、无评价 get 404）；`OrderServiceTest` 完成用例补断言「完成时投递一条 `ORDER_REVIEW_INVITATION`」。
  - 契约：`docs/api-contract.md` Orders 一节补 review 两个接口 + 完成邀请说明。

**验证**：`./mvnw -pl order-service -am test` 全绿（common 35、order-service 22）。

- **S21（已完成）** `feat(frontend): H5 order review UI on completed orders (S21)`
  - H5 `apps/user-h5` 新增 `OrderReviewSection`：订单 `COMPLETED` 时在「当前订单」卡片里显示——`GET /api/orders/{id}/review`（404 视为「未评价」而非报错）已评价则展示 `{rating}★` + 文字；未评价则给评分（1-5 `NumberInput`）+ 文字输入 + 「提交评价」（`POST /api/orders/{id}/review`），提交后失效重查。`REVIEW_*` 错误码走既有 `describeError` 提示。
  - 验证：`pnpm -C apps/user-h5 typecheck`/`build` 全绿；浏览器端到端（需完整 Docker 栈）仍排在 Phase 9。

### Phase 7 — 地图 Provider 配置对齐（✅ 已完成，1/1 commit）

- **S22（已完成）** `feat(map): select route provider by providers.map.type, fail closed (S22)`
  - `map-service` 的路线 Provider 选型从「配了 `AMAP_API_KEY` 就用高德、否则 mock」的隐式判断，改为**显式读 `providers.map.type`**（与 sms/payment/ocr/identity 选型同构）：`MapRouteProvider` SPI 加 `name()`（去掉不再使用的 `@FunctionalInterface`/`supports()`）；`MockRouteProvider.name()="demo"`、`AmapRouteProvider.name()="amap"`；`RouteQuoteService` 注入 `List<MapRouteProvider>` + `ProviderProperties`，按 type 选，未配置 `MAP_PROVIDER_UNCONFIGURED` fail-closed。
  - 保留「配置了 amap 但缺 `AMAP_API_KEY` 时直接 `MAP_ROUTE_QUOTE_FAILED`，不静默降级到 mock」这条已做对的规则（现在在 `AmapRouteProvider.quote` 内联判断）。`carpooling-providers.yml` 三 profile 已就绪（demo=demo、staging/prod=`${MAP_PROVIDER:amap}`），无需改配置。
  - 文档明确记录仍有意推迟：路线缓存、供应商熔断/限流降级、备用供应商切换、途经点、车牌限行、H5 真实地图 SDK。
  - 测试：`RouteQuoteServiceTest` 重写为按 type 选型（3 用例：按 type 选 demo 并落库、选中的 amap 失败不降级、type 空 fail-closed）。

**验证**：`./mvnw -pl map-service -am test` 全绿（common 35、map-service 5）。

### Phase 8 — 部署与安全加固（✅ 已完成）

- **S23（已完成）** `chore(docker): harden middleware — localhost ports, limits, no-new-privileges, demo overlay (S23)`
  - `docker-compose.yml`：6 个中间件的发布端口全部绑到 `127.0.0.1`（localhost-only，不再暴露到网络）；既有 healthcheck 保留。
  - 新增 `docker-compose.demo.yml` 覆盖层：每容器 `mem_limit`/`cpus` 资源限制 + `security_opt: no-new-privileges:true` + `restart: unless-stopped`。用法 `MYSQL_HOST_PORT=3307 docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d`。官方镜像默认非 root 用户运行，no-new-privileges 再挡提权。
  - **真机验证**：叠加覆盖层重建后 6/6 healthy；`docker port o2o-mysql` = `127.0.0.1:3307`；`docker inspect o2o-nacos` 内存上限 768m + `no-new-privileges:true`。

- **S24（已完成）** `feat(gateway): security headers + per-env CORS + TLS-ready + fail-fast (S24)`
  - **安全响应头**：用 Spring Cloud Gateway 支持的 `default-filters`（`AddResponseHeader`）给所有代理响应加 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`、`X-XSS-Protection: 0`、`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`。**真机验证**：`/api/auth/sms-code` 200 响应上五个头都在。（起初写了自定义 `GlobalFilter`，但代理响应头会被网关的写出阶段覆盖，改用官方 `default-filters` 机制。）
  - **按环境 CORS**：`globalcors` 的 allowedOrigins 每个都可用 `GATEWAY_CORS_ORIGIN_1..4` env 覆盖，demo 默认本地 Vite 源，staging/prod 换真实源。真机验证：OPTIONS 预检返回 `Access-Control-Allow-Origin`。
  - **TLS-ready**：`server.ssl.*` 由 `TLS_ENABLED`（默认 false）+ keystore env 控制，staging/prod 开 TLS 时网关终止 HTTPS（此时 HSTS 才有意义）。
  - **`fail-fast: false`（14 服务）**：Nacos 注册瞬时失败不再 abort 启动（E2E 里发现网关重启时常因 `NacosServiceRegistry.register` 超时 + `failFast=true` 而启动失败）。加 `${NACOS_FAIL_FAST:false}`。
  - ⚠️ **内存教训**：14 个 JVM（各 ~320MB）+ 6 中间件容器 + 构建同时跑会把 Docker Desktop VM 撑爆，中间件被 OOM kill（Exit 137）连累服务全挂。做针对性验证时起最小子集（如本次 gateway+auth+notification 3 个）即可；跑全栈要确保内存足够。

- **S26（已完成）** `feat(auth): demo operator-session (seed double-gate) fixing the operator gap (S26)`
  - **修复 E2E 发现的 operator 开通缺口**：新增 `POST /api/auth/demo/operator-session`（auth-service），**双重闸门** `DemoEndpoints.requireSeed()`（demo profile + `app.demo.seed-enabled`），一次调用即开通并签发 `OPERATOR`+`ADMIN` 会话。`UserAccounts.seedOperator` 经 user-service upsert 运营用户。`carpooling-providers.yml` demo profile `seed-enabled: true`（staging/prod 仍 false，`DemoModeGuard` 保证非 demo 不可为真）。**真机验证**：经 Gateway 调用返回 `roles=['ADMIN','OPERATOR']` + token。
  - `apps/admin-console` 的失效 mock 登录（S8 后 `code:'MOCK-123456'+roles` 已不通）改调 `/api/auth/demo/operator-session`；`scripts/demo-smoke.sh` 也从 DB 直改 workaround 换成该端点。
  - 测试：`AuthControllerTest` +2（seed 开通并签发运营 token、seed 关闭时端点 404）；`CarpoolingProvidersYamlTest` 改断言 demo `seed-enabled=true`。
  - 说明：S26 的「reset（清 demo 数据）」暂未做（跨服务数据清理是更大的活）；本次交付的是「seed 双重闸门 + operator 开通」这一高价值部分，双闸门安全模式已落地并测试。

- **S25（已完成）** `feat(file): upload MIME whitelist + size limit (S25)`
  - `file-service` 上传加固：`FileStorageProperties` 加 `allowedContentTypes`（默认 jpeg/jpg/png/webp/pdf）+ `maxUploadBytes`（默认 10 MiB），均可经 `minio.allowed-content-types`/`minio.max-upload-bytes`（env `FILE_ALLOWED_CONTENT_TYPES`/`FILE_MAX_UPLOAD_BYTES`）配置。
  - `presignUpload`：content-type 不在白名单 `415 FILE_CONTENT_TYPE_NOT_ALLOWED`；请求带 `contentLength`（H5 传 `file.size`）超上限 `413 FILE_TOO_LARGE`。`completeUpload`：`ObjectStorageClient` 新增 `objectSize`（MinIO `statObject().size()`），**权威**校验实际对象大小超限则 `413`（客户端谎报大小也拦得住）。mock 直连入口也过白名单。
  - 测试：`FileObjectServiceTest` +3（拒绝非白名单类型、拒绝超大声明、complete 时拒绝超大实际对象）。H5 `uploadDriverDocument` 传 `contentLength`。

### Phase 9 — 端到端测试与文档（✅ 已完成）

- **S27（已完成）** — 三层 E2E 覆盖：
  - **curl 全栈冒烟** `scripts/demo-smoke.sh`（真实 Docker 栈跑通 FAILS=0，见下节「S27 全栈 E2E 结果」）。
  - **支付回调契约测试** `PaymentCallbackContractTest`（`@WebMvcTest`）：锁定 `POST /api/payments/callbacks/{provider}` 的 HTTP 契约——三个签名头 + 原始 body 透传、成功回 `{intentId,status}` 200、坏签名 401 `PAYMENT_CALLBACK_SIGNATURE_INVALID`（`@Import(GlobalApiExceptionHandler)` 让错误映射在 web 切片生效）。
  - **Playwright** `apps/user-h5`（`@playwright/test` + chromium）：`e2e/login.spec.ts` 登录界面冒烟（`webServer` 自起 Vite，无需后端）；`pnpm -C apps/user-h5 test:e2e` 1 passed。完整业务流的浏览器 spec 是后续项，当前由 curl smoke 覆盖。
- **S28（已完成）** — 文档最终化：新增 `docs/demo-mode.md`（profile/Provider/双闸门/运行步骤/端到端流程）、`docs/security.md`（密钥/鉴权/回调/文件/网关/审计/PII 安全基线 + 已知缺口）、`docs/adr/0002-provider-spi-and-demo-profiles.md`（Provider SPI + Profile 模型 ADR）；`docs/api-contract.md` 的 Auth/Files/Payment/Identity/Order/Map/AI 各节刷新到 S8–S26 现状；本 `AGENTS.md` 刷新到最终状态。

## 全量验证结果（截至本文档更新时点）

在仓库根目录执行的最近一次全量验证：

```text
./mvnw test          → BUILD SUCCESS，15/15 模块通过，173 个测试全部通过、0 失败、0 错误（common 38、gateway 14、auth 17、order 24、payment-sim 25 等，S31 后）
apps/user-h5 Playwright  → `pnpm -C apps/user-h5 test:e2e` 3 passed（移动登录冒烟 + 桌面登录卡 + 1023↔1024 外壳热切换，webServer 自起 Vite，S36）
scripts/demo-smoke.sh    → 真实 Docker 栈 13 步业务闭环 FAILS=0（需先起中间件 + 服务；S36 后未重跑，纯前端表现层改动）
pnpm -C apps/user-h5 typecheck / build       → 通过（S36 双外壳后经 ./scripts/verify.sh 全量复验）
pnpm -C apps/admin-console typecheck / build → 通过
git status --short   → S36 改动位于 worktree 分支 claude/mobile-desktop-interface-redesign-bb70d1
```

按模块测试数：common 35、gateway-service 12、auth-service 15、user-service 3、driver-service 6、trip-service 5、order-service 22、payment-sim-service 19、map-service 5、file-service 6、ai-service 3、admin-service 1、audit-service 2、notification-service 9、identity-service 9。

**尚未做、且必须在真实 Docker 栈上验证的**（计划放在 Phase 9 / S27）：`docker compose up` 起完整拓扑后，通过浏览器和/或 curl 走一遍「登录 -> 发布 -> 搜索 -> 下单 -> 认证 -> 支付 -> 超时/取消 -> 评价」的完整闭环。目前本机 Docker daemon 状态未在本轮重新确认，之前记录过 `unix:///Users/llfzzz/.docker/run/docker.sock` 不存在的情况，执行 Phase 9 前需要重新检查。

## 未完成 / 计划中任务（按 Phase 顺序）

### Phase 3 剩余（S12–S15）

✅ **已全部完成（S12–S15）**，详见上文「已完成 — Demo Mode 阶段详情 / Phase 3」。支付全流程（成功/失败/取消/过期 + 延迟/重复/乱序回调）可经运营 Demo 控制台（S13）触发、走真实签名回调管道（S12）驱动，订单取消/完成迁移（S14）到位，H5 已切换到 Intent + 回调驱动 + 可取消的订座流程（S15）。原遗留的「运营 Demo 控制台的 admin-console 前端 UI」已于 S29 落地（见上文 S29 段），支付/实名/通知的演示驱动与订单完成/取消均可在浏览器完成。

### Phase 4 — 实名认证（含活体检测）Demo Provider（S16–S18）

✅ **已全部完成（S16–S18）**，详见上文「已完成 — Demo Mode 阶段详情 / Phase 4」。`backend/identity-service`（两层状态机 + `DemoIdentityProvider` + Demo 实名控制台 + 结果异步投递收件箱）、司机资质提交的 identity `APPROVED` 准入门禁（S17）、H5 认证界面（S18）均已落地并测试。

### Phase 5 — OCR Provider 适配（S19）

✅ **已完成（S19）**，详见上文「已完成 — Demo Mode 阶段详情 / Phase 5」。`ai-service` 的 OCR 已从 `new MockOcrPolicy()` 直连改为 `OcrProvider` SPI + `DemoOcrProvider` 异步任务生命周期，按 `providers.ocr.type` 选型 fail-closed，证件号脱敏保留。

### Phase 6 — 订单评价（order-service 内，S20–S21）

- **S20 ✅ 已完成**：评价领域 + `POST/GET /api/orders/{id}/review`（资格/防重复/鉴权/校验/审计 + 完成时投递评价邀请）已上线，详见上文「已完成 … Phase 6 … S20」。
- **S21 ✅ 已完成**：H5 评价界面（`OrderReviewSection`，订单 `COMPLETED` 时提交/展示评价）已上线，详见上文「已完成 … Phase 6 … S21」。

### Phase 7 — 地图 Provider 配置对齐（S22）

✅ **已完成（S22）**，详见上文「已完成 … Phase 7 … S22」。`map-service` 选型已统一到 `providers.map.type`（demo→mock、amap→高德），保留「高德失败不静默降级」，路线缓存/熔断限流/备用供应商/H5 地图 SDK 等仍是有意推迟项。

### Phase 8 — 部署与安全加固（S23–S26）

- **S23 Docker 加固**：容器非 root 用户运行、内部中间件（MySQL/Redis/RabbitMQ/MongoDB/MinIO 管理端口等）不必要不对宿主机公开端口、既有健康检查复核、资源限制；新增 `docker-compose.demo.yml` 覆盖文件。
- **S24 Gateway TLS-ready + 安全头 + 按环境 CORS**：TLS-ready 的监听配置、标准安全响应头、按 `demo`/`staging`/`production` 区分的 CORS 白名单（当前只有本地 Vite 的硬编码白名单）。
- **S25 文件上传加固**：`FileObjectService.validateObjectRequest` 目前只校验 `contentType` 非空，没有类型白名单和大小上限。S25 要补上允许的 MIME 类型白名单、文件大小上限、可选的文件头（magic bytes）嗅探。
- **S26 Demo seed/reset 双重闸门**：Demo 专属的数据 seed/reset 端点，要求同时满足「`demo` profile」且「显式打开 `app.demo.seed-enabled`」（当前 `AppProperties` 已经预留了这个字段但还没有对应功能实现），必须在 staging/production 下不可能被触发；reset 只能清 Demo 数据。

依赖：前面各 Phase 大部分完成后再统一收口更实际，但 S23-S25 中的具体检查项也可以提前单独推进。

### Phase 9 — 端到端测试与文档（S27–S28）

- **S27**：`scripts/demo-smoke.sh`（curl/HTTP，通过 Gateway）驱动完整的「登录 -> 发布 -> 搜索 -> 下单 -> Demo 认证 -> Demo 支付 -> 超时/取消 -> 评价」闭环；Playwright 覆盖 H5 的登录/下单/认证/支付/取消/评价关键路径；补充支付回调的契约测试。这一步需要先确认本机 Docker daemon 可用。
- **S28**：更新 `docs/api-contract.md`、`docs/architecture.md`；新增 `docs/demo-mode.md`、`docs/security.md`；补充 Provider 适配器模式和 Profile 模型相关的 ADR；刷新本 `AGENTS.md` 到最终状态。

依赖：前面全部 Phase。这是收尾阶段。

## 安全要求（必须始终保持，不允许在后续任何 Phase 中回退）

以下是本轮工作已经建立、且在后续所有 Phase 的实现中都必须继续遵守的安全基线：

- **密钥**：仓库中不允许出现任何真实或可用的密钥/Token/密码字面量；所有敏感配置必须来自环境变量；`staging`/`production` 下 `SecretsValidator` 必须能拒绝已知弱密钥（新增敏感配置项时要把对应的 property key 加进 `SecretsValidator.SECRET_KEYS`）。
- **Demo 专属能力**：任何新的 Demo 专属端点/开关/数据，必须遵守「demo profile + 显式功能开关」双重闸门（参考 `DemoModeGuard`、`DemoEndpoints` 的既有模式），且必须验证在 `staging`/`production` 下不可访问（返回 404 而不是 403，避免暴露端点存在性）。
- **鉴权**：所有对外业务请求必须经过 Gateway；新增受保护路由要在 `GatewaySecurityFilter.requiresOperator` 里补充精确的 method+path 判断；角色/权限判定永远以服务端持久化数据为准，任何接口都不能信任客户端传入的角色/权限字段。
- **验证码/Token**：任何新增的一次性凭证（验证码、邀请码等）都要参考 `SmsCodeService` 的模式：哈希存储、单次使用、TTL、发送限流、验证失败次数限制。
- **Payment/Webhook**：任何供应商回调摄取端点都必须做签名校验、重放保护（时间戳+nonce+已见事件存储）、按事件 ID 幂等处理，绝不能有「前端直接调用改状态」的旁路。
- **收件箱/敏感数据展示**：默认遮罩，需要显式「查看/reveal」动作才能取出明文，且要有有效期；reveal 动作要审计（谁在什么时候查看了哪条记录，但不记录被查看的具体内容）。
- **幂等**：所有创建类写操作（订单、支付、认证提交、文件上传、评价……）必须设计幂等键。
- **审计**：状态变更类操作（审核通过/驳回、订单取消、支付状态变更、文件访问、评价提交……）必须写审计日志，带 `traceId`、`correlationId`。
- **日志**：任何日志都不能出现密钥、完整手机号、完整证件号、完整银行卡/支付信息、Token 明文。
- **输入校验/上传限制**：所有外部输入都要校验；文件上传要有类型白名单和大小上限（S25 待补）。
- **状态机权威性**：订单、支付、认证等关键业务状态的迁移必须通过显式状态机方法，不允许接口直接覆盖状态字段。

## 已知阻塞与风险

- **✅ S27 全栈 E2E 已在真实 Docker 栈跑通（2026-07-01）**，见下节「S27 全栈 E2E 结果」。曾被 Docker 国内镜像源（`hub-mirror.c.163.com` 挂掉=000）阻塞，用户移除该 mirror 后镜像可拉取，遂完成。
- ~~operator 开通缺口~~ **（S26 已解决）**：新增 `POST /api/auth/demo/operator-session`（双重闸门 demo+seed-enabled），一次调用签发 `OPERATOR`+`ADMIN` 会话；admin-console 与 `scripts/demo-smoke.sh` 都已改用它，不再需要 DB 直改 workaround。
- ~~中间件 S23 加固未做~~ **（S23 已完成）**：6 个中间件端口已 localhost-only 绑定，`docker-compose.demo.yml` 覆盖层加了资源限制 + no-new-privileges + restart，真机重建后 6/6 healthy。宿主机原生 MySQL 占 3306，compose mysql 发布端口用 `${MYSQL_HOST_PORT:-3306}`（demo 用 3307）。

## S27 全栈 E2E 结果（2026-07-01，真实 Docker 栈）

**启动命令（宿主机跑服务 + Docker 跑中间件）：**
```bash
scripts/generate-local-env.sh                 # .env（demo profile，随机密钥）
MYSQL_HOST_PORT=3307 docker compose up -d      # 6 中间件（mysql 映射到 3307，避开原生 MySQL 占用的 3306）
./mvnw package -DskipTests                     # 14 个 fat jar
bash scratchpad/start-services.sh              # 起全部 14 服务（脚本内 export MYSQL_JDBC_URL 指向 3307 + SPRING_PROFILES_ACTIVE=demo）
```

**健康 / 注册结果：** 6 中间件全 healthy；14 服务 `/actuator/health` 全 200；Nacos `catalog/services` 显示 14 个服务各 1 实例全部注册。

**冒烟（`scratchpad/smoke.sh`，全程经 Gateway :8080，`FAILS=0`）通过的 13 步：** 乘客短信登录（验证码从 Demo 收件箱取）→ operator 登录（DB 提权 workaround）→ 发布行程（route=amap-mock，走 demo map provider）→ 搜索 → 订座（`PENDING_PAYMENT`）→ 创建 Payment Intent（`REQUIRES_PAYMENT`）→ **运营触发签名支付回调 → intent `SUCCEEDED`** → 订单 `SEAT_LOCKED` → 实名认证（发起 `PENDING` → 运营驱动活体 `PASSED` + 会话 `APPROVED` → 结果异步投递收件箱）→ 运营完成订单 `COMPLETED` + 评价邀请进收件箱 → 提交评价（rating 5）+ 重复评价被拒 409 → 取消路径（`USER_CANCELLED` + 座位释放）→ 鉴权反例（乘客打运营 Demo 控制台被 403）。

**深度持久化核对：** MySQL `payment_callback_events` 落 1 条 SUCCEEDED（签名管道确实落库 + 幂等表就位）；`orders` 1 COMPLETED + 1 USER_CANCELLED；`trip_seat_locks` 取消的那条为 `RELEASED`、trip `locked_seats` 回到 1（座位释放正确）；`identity_verifications` 1 条 `APPROVED`/`PASSED`（两层状态机）；MongoDB `audit_logs` 4 条（`ORDER_PAID`/`ORDER_COMPLETED`/`ORDER_REVIEW_SUBMITTED`/`ORDER_CANCELLED_BY_USER`——证明「服务本地审计 Outbox → 定时 relay → audit-service → Mongo」异步链路打通）。

**E2E 发现并修复的 3 个集成缺陷（单测/切片测不到，真机才暴露）：**
1. **缺 `spring-cloud-starter-loadbalancer`**：所有用 Feign（服务名调用）的服务 + Gateway 的 `lb://` 路由都需要它，否则 `No Feign Client for loadBalancing defined` / Gateway 503。→ 加到 `backend/pom.xml` 的 `<dependencies>`（对所有模块生效）。
2. **Flyway 共享库 baseline**：多个服务共用一个 `o2o_carpooling` 库（各自独立 history 表），后启动的服务报 `Found non-empty schema(s) but no schema history table`。→ 给 10 个有 Flyway 的服务 yml 加 `baseline-on-migrate: true` + `baseline-version: 0`（baseline 在 0，保证各服务自己的 V1+ 迁移仍全部执行）。
3. **user-service 缺用户返回 400 而非 404**：`UserController.get` 用 `IllegalArgumentException`（→400），而 auth 的 `UserAccounts.getOrCreate` 只 `catch(FeignException.NotFound)`（404）来自动建号 → 登录 500、永远建不了用户。→ 改成 `BusinessException(NOT_FOUND, "USER_NOT_FOUND")`。
   - 另外的构建/环境修复：`spring-boot-maven-plugin` 打 fat jar（`common` `<skip>`）；`docker-compose.yml` 的 mysql 端口改可配 `${MYSQL_HOST_PORT:-3306}`（避开原生 MySQL）。
   - ⚠️ **Maven 增量坑**：`./mvnw package` 有时不会重新 repackage 某些 jar（尤其被运行中的 JVM 占用过的 gateway/auth），导致 fat jar 里缺新加的依赖。现象是 jar mtime 不更新、`unzip -l | grep loadbalancer` 为 0。**排查/修复**：`rm backend/<svc>/target/<svc>-*.jar*` 后 `./mvnw -pl <svc> -am package -DskipTests` 强制重打。重跑 E2E 前建议 `./mvnw clean package -DskipTests` 干净重建以绝后患。
- **payment-sim-service 新旧两套支付入口并存**：`POST /api/payments/simulations`（旧，S11 之前的实现，直接标记成功）与 `POST /api/payments/intents` + `POST /api/payments/callbacks/{provider}`（新，S11/S12，结局靠签名回调驱动）目前同时存在。**S15 起 H5 已完全切换到新入口，不再调用旧的 `simulations`**。旧入口现在是可删除的技术债（无前端消费者），但删除属于独立清理项，本轮先保留（仍有对应的 `PaymentSimulationServiceTest` 2 个用例）；若要删除，记得一并移除控制器/服务/仓库/迁移表引用与测试，并在 api-contract 里删除对应两节。
- ~~**`OrderStateMachine` 状态迁移不完整**~~（S14 已解决）：现已有 `pay`/`timeout`/`cancelByUser`/`cancelByDriver`/`cancelByOperator`/`complete`。评价功能（Phase 6/S20）依赖的 `COMPLETED` 终态已可达（`SEAT_LOCKED → COMPLETED`，经 `POST /api/orders/{id}/complete`）。
- **通知内部 API 未加认证**：`notification-service` 的 `POST /api/notifications` 和 `GET /api/notifications/internal/latest` 目前只靠「不通过 Gateway 对外路由」这一层来保护，服务网格内部没有做服务间调用鉴权（mTLS 或内部 token）。当前风险可接受（因为 Gateway 没有为它们开路由，外部直接打服务端口在生产部署里应该被网络策略挡住），但 Phase 8 做部署加固时应该重新评估是否需要加一层内部调用鉴权。
- **`RefreshTokenStore`/`SmsCodeStore` 的内存实现只适合单实例**：Redis 不可用时会自动降级为内存态实现，这在多实例部署下会导致会话/验证码状态不一致（用户可能被路由到不同实例）。Demo 环境下 Redis 是通过 Docker Compose 提供的真实中间件，这不是一个当前会触发的问题，但如果未来评估「不依赖 Redis 的极简部署」，要重新考虑这个限制。

## 推迟的真实供应商对接（Deferred real-provider integrations）

以下明确推迟到本轮 Demo Mode 工作完成之后，接入方式是「实现对应 Provider 接口 + 提供环境变量凭据 + 把 `providers.*.type` 从 `demo` 改成供应商名」，不需要改动核心业务流程：

- 真实短信网关（阿里云/腾讯云短信等）—— 对应 `SmsProvider`。
- 真实 OCR 供应商 —— 对应 Phase 5 将建立的 `OcrProvider`。
- 真实实名认证/活体检测供应商 —— 对应 Phase 4 将建立的 `IdentityVerificationProvider`。
- 真实支付供应商（支付宝/微信支付/Stripe 等）及其真正的资金结算、退款、对账 —— 对应 `PaymentProvider`；当前 Demo 阶段完全不涉及真实资金状态。
- 真实推送/站内信供应商（APNs/FCM 等）—— 对应 `NotificationChannelAdapter` 的 PUSH 通道，当前只有 Demo 通道。
- 地图：高德本地/测试接入、H5 SDK、路线缓存、限流与熔断已完成（S40/S41/S44）；仍推迟第二家真实供应商、途经点、车牌限行和取得许可证后的生产部署。
- 全链路可观测（统一 Metrics/Tracing 平台接入）、性能压测、正式的安全渗透测试 —— 超出本轮 Demo Mode 范围。

## 下一步精确行动（Next Actions）

当前代码实现已到 S47 并随 PR #9 合并；S48 已完成登录码线上回归与服务器运行手册纠偏。接下来只做剩余运行态验收与集成，不要重新实现已完成阶段：

1. **完整全栈回归（含 S46 新面）**：在内存充足的主机启动完整 Demo 栈，运行 `scripts/demo-smoke.sh`（要求 `FAILS=0`）——注意登录已改为 challenge 取码，脚本已更新。手动/浏览器再验：消息中心分页+分类+未读+deep link；乘客-司机私信收发+失败重试+非参与者 404；`POST /api/trips/route-preview` 价格明细与落库价一致；`POST /api/demo/trips/generate|random` 生成的 `source=DEMO` 行带徽标、价格公式派生；`AUTH_SMS_CODE` 不出现在任何 `/api/inbox` 响应。地图部署验收仍带 `EXPECT_REAL_MAP_PROVIDER=true`。
2. **staging SSE 压测**：同前，取得非生产 `TARGET_BASE_URL`/`SSE_TRIP_ID`/`SSE_VIEWER_TOKEN` 后跑阶梯并保存结果；生产域名严禁测试。
3. **正式生产合规（许可证之后）**：`woxiangchuanaj.top` 仍是 Demo 站验证；许可证取得后再做正式 Key/配额/告警/合规复核。
4. **线上升级边界**：服务器必须使用 `/etc/systemd/system/o2o@.service` 管理进程，Gateway 由 `deploy/systemd/env/gateway-service.env` 绑定 8120；禁止再用 `pkill ...; scripts/start-services.sh` 替代 systemd。每次升级后运行 `scripts/check-deployment.sh <api-base>`，并做一次登录页验证码与 `/api/inbox` 隔离回归。
5. **有意推迟**：私信的运营/审计端点（v1 无）；跨服务 broker 事件总线（现用事务性 outbox）；PUSH 通道真实供应商。

## 历史已完成（Demo Mode 主线任务之前的 MVP 基线）

以下是 Demo Mode 主线任务启动之前就已经完成的工作，构成了当前的技术基线：

- 已创建 Maven 多模块后端，包含核心服务模块和 `common` 领域模块；技术版本线：Boot `3.5.15`、Cloud `2025.0.3`、Alibaba `2025.0.0.0`（有 ADR 记录）。
- 核心领域类型：用户、司机审核、车辆、行程、路线快照、座位库存、订单、支付模拟、文件对象、OCR、审计日志；核心事件类型：司机审核提交、OCR 完成、行程发布、订单创建、支付超时、订单取消、座位释放。
- User、Driver Verification、Trip、Order、Payment Sim、File、AI OCR Mock、Map RouteSnapshot 均已从内存实现推进到 `JdbcClient` + MySQL/Flyway 持久化，且每个服务有独立 Flyway history table。
- Trip 服务：`trips` + `trip_seat_locks`，按 `orderId` 幂等锁座/释放，库存不足拒绝。
- Map 服务：`route_snapshots`，配置 `AMAP_API_KEY` 时走高德 Web 服务地理编码 + 路径规划 2.0，未配置时返回明确命名的 `amap-mock` fallback（不会把 Mock 包装成真结果），保存脱敏后的供应商响应快照。Trip 发布调用 Map 服务取权威路线快照后计价，不信任客户端传入的距离/时长。
- Order 服务：`orders` + `order_outbox_events`，按 `(rider_id, idempotency_key)` 防重复下单；支付超时主路径是 Outbox 发布 RabbitMQ TTL/DLX 延迟消息，定时扫描作为兜底对账。
- Payment Sim 服务：`payment_simulations`，按 `(order_id, idempotency_key)` 防重复，金额从 Order 服务读取，不信任前端传价（Phase 3 起正在演进为 Payment Intent 模型，见上文）。
- Admin 服务 `GET /api/admin/dashboard` 聚合 Driver Verification 和 Order admin metrics。
- 手机号 AES-GCM 字段加密落库；Mock OCR 证件号字段落库前脱敏。
- File 服务：MinIO 私有文件 presigned upload/download、上传完成 `statObject` 确认、owner/operator/admin 授权、`mock-upload` 兼容入口。
- Audit 服务：MongoDB `audit_logs` 落库，支持追加和按 target/action/actor 分页检索，带 traceId。
- driver/file/order 三服务的业务审计已从「best-effort Feign」升级为「服务本地 Outbox + 定时 relay 重试」（at-least-once），状态变更与审计事件同事务入库。
- Gateway 平台安全基线：`JwtTokenService`（HS512）、`SecurityPrincipal`、Bearer token 校验、`/api/admin/**`/`/api/audits/**`/`/api/orders/admin/**` RBAC、WebFlux `ApiError` 写出、`X-Trace-Id` 透传、客户端伪造头清理、固定窗口限流（内存/Redis 可切换）。
- 运营后台：Mock 登录、真实聚合指标、司机审核列表/证件短时下载/通过驳回、订单监控、审计检索页、行程总览页、用户管理页（脱敏手机号目录）。
- 前端技术栈：pnpm workspace、Vite、TypeScript 严格类型检查；Free Joy (FJ) 设计系统（`packages/fj-ui`，`@fj` 别名消费，teal 品牌色）；用户 H5 全量 FJ 组件，运营后台 FJ 外壳 + FJ 主题化 Ant Table；FJ 字体已用 `@fontsource` 自托管去 Google Fonts CDN；已做移动端(412px)/桌面端(1440px)截图回归。
- 项目文档：`docs/PRD.md`、`docs/product-design.md`、`docs/architecture.md`、`docs/api-contract.md`、`docs/operations.md`、`docs/adr/0001-spring-cloud-2025-boot-35.md`。
- CI 配置和本地 `scripts/verify.sh`；Git 仓库已推送到 `https://github.com/llfzzz/o2o-Local-Carpooling.git` 的 `main` 分支。

### 遗留的未完成项（MVP 基线阶段遗留，部分已被 Demo Mode 任务覆盖或将被覆盖）

- `infra/mysql/001_init.sql` 仍是早期参考 SQL，新表应优先进各服务 Flyway migration。
- Driver 文件上传尚未做病毒扫描、内容类型深度校验、对象生命周期策略（S25 会补内容类型/大小校验，病毒扫描等仍在范围外）。
- Map 已完成高德本地/测试接入、JS 地图 SDK、结构化路线缓存、供应商限流与熔断；仍未做第二家真实供应商、批量路线、途经点和车牌限行策略。
- Order 尚未做 Outbox 分片、后台补偿控制台、死信告警、跨服务 Saga 编排。
- Audit Outbox 死信告警、积压监控、统一可观测尚未落地。
- Admin 后台的行程写操作（取消/全状态管理）、风控配置尚未落地。~~Demo 控制台前端 UI 也尚未落地~~（S29 已落地：支付回调/实名活体/通知投递的 Demo 控制台 + OCR 任务 + 订单完成/取消操作列）。
- Testcontainers、契约测试、Playwright E2E、安全测试、性能压测尚未落地（Playwright/E2E/契约测试排进了 Phase 9 的 S27）。
- 长期分支策略或 PR 记录尚未建立（当前仍是直接提交到 `main` 的工作方式，与项目既有约定一致）。

## 已优化

- 技术版本按官方兼容线保守选择；领域规则先写测试再实现；路线距离/价格/座位库存/订单状态的权威性原则已写入 PRD 和领域代码。
- 外部密钥统一通过环境变量读取；Demo Mode 任务进一步把「密钥硬编码默认值」这个历史遗留问题连根拔除（S2），并加了 fail-closed 校验（`SecretsValidator`）和双重闸门守卫（`DemoModeGuard`）。
- 数据库结构预留幂等键、版本号、审计/Outbox、核心索引；手机号等敏感字段加密/脱敏存储。
- 前端分 H5 和运营后台两个应用；TanStack Query 管理服务端状态，Zustand 只承载轻量本地状态。
- 本地校验统一收口到 `scripts/verify.sh`。
- 后端 MVC 服务和 Gateway/WebFlux 通过 common foundation 统一基础 traceId 响应头和结构化异常响应模型；`BusinessException` + `ApiError` 的错误模型在 Demo Mode 任务中被复用扩展（新增 `SMS_CODE_*`、`REFRESH_TOKEN_*`、`PAYMENT_*`、`DEMO_ENDPOINT_DISABLED` 等错误码，均遵循「不泄露内部信息」的既有规范）。
- Order 延迟取消主路径是 Outbox + RabbitMQ TTL/DLX；File 私有对象以服务端 object key + 短时 URL + owner/operator/admin 授权为准；Audit 已是 MongoDB 落库 + Gateway RBAC；driver/file/order 三服务审计已统一为服务本地 Outbox。
- Map 已接高德 Web 服务适配，保留明确命名的 `amap-mock` fallback。
- 前端已统一 Free Joy 设计系统 token。
- **Demo Mode 任务新增的架构范式**：Provider SPI + Demo 实现的模式（`MapRouteProvider` 是最早的范例，`SmsProvider`/`PaymentProvider`/`NotificationChannelAdapter` 均照此模式建立）；状态机权威化模式（`OrderStateMachine`、`PaymentIntentStateMachine` 均是显式合法迁移表 + 非法迁移抛异常）；Demo 专属能力的双重闸门模式（profile + 显式开关）；一次性凭证的哈希存储+TTL+限流+锁定模式（`SmsCodeService`，后续可复用于其它验证码/邀请码场景）。

## 未优化

- 运营后台已做 vendor 级代码分包；后续随 FJ DataGrid 替换 Ant Table 可进一步收敛，并可做路由级懒加载。
- 前端仍是手写 fetch 客户端（Demo Mode 任务给 H5 加了 401 自动 refresh 的封装，但尚未接 OpenAPI 类型生成、统一重试策略和全局错误边界）。
- H5 地图已接高德 JS API 2.0；生产启用仍以取得技术服务许可证和完成独立部署验收为前提。
- 可访问性检查、Playwright 截图基线、Lucide 图标自托管仍未落地。
- 后端各服务 `application.yml` 有重复配置，后续可抽到 Nacos shared config（Demo Mode 任务新增的 `carpooling-providers.yml` 是朝这个方向迈出的第一步，但目前只覆盖 Provider/Profile 相关配置，端口、数据源等仍是各服务分别配置）。
- Driver/File/AI/Admin 等仍需继续清理 Controller/Application Service/Domain Service/Repository 边界（payment-sim-service 在 S11 引入 Payment Intent 后，边界更清晰了一些，但新旧两套支付逻辑并存期间还谈不上完全干净）。
- 日志规范、脱敏日志、Metrics、Tracing 还没有系统化实现。
- 地图路线缓存、地图限流和 Provider 熔断已落地；全系统统一缓存策略、数据库读写隔离及其它供应商的降级治理仍未统一。
- 安全基线已有 JWT/RBAC/限流/验证码/Refresh Token 等大量新增单元测试，但还没有系统覆盖所有资源归属越权、重复提交和敏感字段日志脱敏测试；文件上传类型/大小限制（S25）也还没做。

## 编写规范

### 通用规范

- 所有生产级代码必须以服务端为权威，前端不能决定价格、库存、订单状态、审核状态、认证结论。
- 不要把 Mock 能力包装成真实能力；Mock/Demo 类、接口和数据必须命名明确（如 `DemoXxxProvider`、`Demo` 前缀的端点路径）。
- 不要把真实 API Key、JWT Secret、短信/OCR/支付密钥写入 Git；新增任何敏感配置项都要同步加进 `SecretsValidator.SECRET_KEYS`（如适用）。
- 新增技术栈或替换核心框架前先写 ADR，说明原因、替代方案、风险和回滚方式。
- 改动必须尽量小而清晰，不做无关重构，不混入格式化全仓文件。
- 能用结构化模型和状态机表达的业务规则，不用散落的字符串判断。
- **新增外部业务能力（供应商依赖）必须先定义 Provider 接口，再实现 Demo 版本**；参考 `backend/map-service` 的 `MapRouteProvider`/`MockRouteProvider`/`AmapRouteProvider` 范式，或本轮新建的 `SmsProvider`/`PaymentProvider`/`NotificationChannelAdapter`。

### 后端规范

- Java 包名统一使用 `com.o2o.carpooling.<service>`。
- 共享领域类型放在 `backend/common`，但不要把具体服务的基础设施细节塞进 `common`；跨服务共享的 Provider/Profile 配置基座（`AppProperties`、`ProviderProperties`、`carpooling-providers.yml`）例外，属于 `common` 的合理职责。
- 订单、库存、审核、支付、认证等状态迁移必须通过明确的领域方法或状态机，不允许接口直接覆盖状态。
- 创建订单、支付、审核、文件上传、评价等写操作必须设计幂等键。
- 消费 RabbitMQ 事件必须按 `eventId` 幂等；消费供应商 Webhook 回调必须按 `event_id` 幂等 + 签名校验 + 重放保护。
- 数据库写操作必须考虑事务边界、并发锁、版本号和重复提交。
- 外部供应商调用必须经过适配层：地图走 `map-service`、短信走 `auth-service` 内的 `SmsProvider`、支付走 `payment-sim-service` 内的 `PaymentProvider`、通知走 `notification-service` 的 `NotificationChannelAdapter`、OCR/实名认证的 Provider 化正在 Phase 4/5 推进中。
- Controller 只做协议转换和校验，业务规则放 Application Service 或 Domain Service。
- 新增接口要同步更新 `docs/api-contract.md`。
- 单元测试优先覆盖状态机、库存、价格、权限、幂等和异常状态；Demo Mode 相关新增代码还要覆盖：Provider fail-closed 场景、Demo 端点在非 demo profile 下不可达、一次性凭证的 TTL/锁定/重放场景。

### 前端规范

- 用户端优先保证路线搜索、行程信息、座位状态和支付状态清晰可见。
- 后台优先保证表格密度、筛选、审核动作、异常状态和审计入口。
- 服务端数据用 TanStack Query；纯 UI 状态或当前 tab/筛选草稿可用 Zustand。
- 不在前端硬编码权威价格、库存、审核结论、认证结论；只能展示服务端返回值。
- **敏感内容不允许自动填充或无条件内联展示**。登录验证码只允许在当前登录页通过 challenge-bound demo-peek 临时查看，登录成功/锁定/TTL 即删，绝不进入消息中心；其它允许 reveal 的敏感通知在 `/api/inbox` 默认遮罩并要求显式「查看」。
- UI 文案直接服务用户任务，不写介绍式、营销式、解释式空话。
- 按钮、状态标签、表格列和表单字段要稳定，避免动态内容导致布局跳动。
- 新增页面后至少运行 `pnpm typecheck` 和 `pnpm build`。
- 复杂页面后续要补 Playwright E2E 和移动端截图检查（排进 Phase 9）。

### 数据与安全规范

- 手机号、车牌、证件号、支付相关字段必须加密或脱敏存储。
- 文件对象默认私有，前端只拿文件 ID 或短时授权 URL。
- 后台审核、订单取消、支付状态变更、文件访问、Demo 收件箱 reveal 动作必须写审计日志。
- 所有外部请求最终必须经过 Gateway `/api/**`；服务间内部调用（如 auth→notification、payment→order）不经过 Gateway，但要明确注释说明这一点。
- 所有日志要带 traceId，禁止把密钥、证件号、完整手机号、完整车牌、验证码明文、Token 明文写入日志。
- 任何 Demo 专属能力必须遵守 `app.demo-mode` + 对应功能开关的双重闸门，且在 `staging`/`production` profile 下要有测试验证其不可达。

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

当前本机没有全局 `node`、`npm`、`mvn`，但有 Codex bundled Node/pnpm；`scripts/verify.sh` 已兼容该路径：

```bash
PATH="/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:/Users/llfzzz/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin:$PATH" pnpm <command>
```

Maven 通过项目根目录 `./mvnw` 自动下载到 `.mvn-local/`。

针对单个模块的快速迭代验证（Demo Mode 任务期间常用）：

```bash
./mvnw -pl <module> -am test          # 只测某模块及其依赖（common 等）
./mvnw -pl <module1>,<module2> -am test  # 同时测多个改动模块
```

启动本地 Demo 环境（Docker daemon 需可用；当前镜像拉取被国内 mirror 阻塞，见「已知阻塞与风险」首条）：

```bash
scripts/generate-local-env.sh          # 生成带随机密钥的 .env（gitignored；已存在则用 --force 重生成）
MYSQL_HOST_PORT=3307 docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d  # 6 中间件（localhost-only 端口 + 资源限制 + no-new-privileges；mysql 映射 3307 避开原生 MySQL）
./mvnw package -DskipTests             # 产出 14 个可运行 fat jar（S27 加的 spring-boot-maven-plugin）
# 起全部服务（安全加载 .env：值含 & ? @，不能 source，用逐行 export）：
#   见 scratchpad/start-services.sh（java -Xmx320m -jar，日志写 scratchpad/logs/<svc>.log）
# 起前端：PATH 加 Codex node -> pnpm -C apps/user-h5 dev / pnpm -C apps/admin-console dev
```

> ⚠️ 服务在**宿主机**上跑（连 Docker 中间件的 published 端口 127.0.0.1:3306/6379/...），必须带 `SPRING_PROFILES_ACTIVE=demo`（否则 `carpooling-providers.yml` 的 provider type 全为空、一切 fail-closed）。fat jar 用 `java -jar` 直接运行；`common` 是库、已在其 pom 里 `<skip>` 掉 repackage。
