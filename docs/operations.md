# 运维与维护

## 本地启动

```bash
docker compose up -d
./mvnw test
pnpm install --frozen-lockfile
pnpm typecheck
pnpm build
```

## 环境变量

- `.env.example` 只保存占位值。
- `MYSQL_JDBC_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 控制已落库服务的 MySQL 连接。
- `FLYWAY_ENABLED` 控制服务启动时是否执行 Flyway migration。
- `FIELD_ENCRYPTION_KEY_BASE64` 控制手机号等服务端字段加密；生产必须替换本地示例值。
- `AMAP_API_KEY`、MinIO Secret、JWT 签名密钥、RabbitMQ/MongoDB 凭据、短信/OCR/支付密钥必须来自环境变量或密钥管理系统。
- `AMAP_BASE_URL` 默认 `https://restapi.amap.com`，仅用于测试或代理环境覆盖。
- 高德需要两套独立凭据：后端 Web 服务 Key 写入 `AMAP_API_KEY`；浏览器 Web Key 仅在构建 H5 时通过 `VITE_AMAP_JS_KEY` 注入。
- JSAPI `securityJsCode` 不能写进 `.env` 或前端 bundle。复制 `deploy/nginx/amap-jscode.conf.example` 到仓库外的 `/etc/nginx/secrets/o2o-amap-jscode.conf`，替换占位符后设为 root 所有、权限 `600`；nginx 未读到安全码时 `/_AMapService/` 返回 503。
- 路线缓存默认新鲜 30 分钟、供应商故障时最多使用 24 小时内历史真实路线；熔断和超时均可用 `.env.example` 中的 `MAP_ROUTE_CACHE_*`、`MAP_PROVIDER_CB_*`、`AMAP_*_TIMEOUT` 覆盖。
- 生产环境禁止使用 Compose 中的本地默认密码。

## 数据库

- `0.5.0-SNAPSHOT` 起 Users、Driver Verification、Trips、Orders、Order Outbox、Payment Sim、Files、AI OCR Mock、Map RouteSnapshot 任务由各服务自己的 Flyway migration 建表。
- `docker-compose.yml` 只启动 MySQL，不再把 `infra/mysql/001_init.sql` 挂载为初始化脚本；该 SQL 仅保留为早期结构参考。
- 每个落库服务使用独立 Flyway history table，例如 `flyway_schema_history_user_service`，避免共享 schema 下 migration 版本互相冲突。
- 手机号通过服务端 AES-GCM 字段加密后写入 `users.phone`；Mock OCR 证件号字段落库前脱敏。
- Trip 库存由 `trips.locked_seats` 和 `trip_seat_locks.order_id` 共同维护；Trip 发布使用 Map 服务返回的 `RouteSnapshot` 计价；Map 供应商路线快照写入 `route_snapshots`，响应快照需脱敏 API Key；Order 使用 `(rider_id, idempotency_key)` 防重复下单；Payment Sim 使用 `(order_id, idempotency_key)` 防重复支付模拟；文件上传状态由 `file_objects.upload_status` 标识。

## 日志与审计

- 所有服务必须输出 traceId。
- 后台审核、订单取消、支付状态变更、文件访问生成审计日志并写入 MongoDB `audit_logs`。
- 订单支付超时主路径为 Order Outbox 发布 RabbitMQ TTL/DLX 延迟消息；order-service 定时扫描保留为兜底对账。

## 地图供应商配置（S44 实测）

### 两套凭据，用途不同

| 变量 | 用途 | 放在哪 |
|---|---|---|
| `AMAP_API_KEY` | 服务端 Web 服务：地理编码/逆地理编码/输入提示/POI/路径规划 | `.env`，**永不下发浏览器** |
| `VITE_AMAP_JS_KEY` | 浏览器 JS API：**只渲染瓦片/标记/路线** | `apps/user-h5/.env.local`（会进 bundle，靠域名白名单约束） |
| `AMAP_JS_SECURITY_CODE` | JSAPI 安全密钥 | **只放 nginx**（`$amap_jscode`），浏览器永不可见 |

### 必做：`providers.map.type` 要真的生效

`carpooling-providers.yml` 的 demo profile 原本把 `map.type` **写死为 `demo`**，导致即使配了 `MAP_PROVIDER=amap` + 真实 `AMAP_API_KEY`，跑的仍然是固定 fixture——**界面看起来一切正常，但数据是假的**。S44 已改为 `${MAP_PROVIDER:demo}`：默认仍是 demo（安全默认），设了 `MAP_PROVIDER=amap` 才切真实供应商。

自检（最快的一条）：

```bash
curl -s http://127.0.0.1:8107/api/maps/cities | grep demoProvider
# "demoProvider": false  -> 真实高德
# "demoProvider": true   -> demo fixture（前端会打「演示地图数据」徽标）
```

### 必做：JSAPI key 的域名白名单

高德控制台 → 该 JSAPI key → 安全设置 → 域名白名单，**必须同时加入**：

- 线上域名（如 `woxiangchuanaj.top`）
- 本地开发用的 `localhost`（**否则本机开发时地图是空的**）

**为什么这条容易踩坑**：域名没授权时，高德**不会以任何调用方可见的方式失败**——脚本正常加载、`new AMap.Map()` 正常返回、右下角高德 logo 正常渲染、连 `complete` 事件都会照常触发，**只是没有任何瓦片**，错误仅以异步 `console.error: INVALID_USER_DOMAIN` 出现。S44 已在 `amapLoader.ts` 里针对这一组错误码做检测，前端会明确显示「地图密钥未授权当前域名」，而不是给一个看起来正常的空白地图。

### 实测基线（2026-07-20，本机 + 真实 key）

```text
输入提示  软件园/厦门     -> 3 条真实 POI（B0FFF307N4 等），provider=amap
逆地理编码 WGS84 入参      -> 自动转 GCJ02，偏移 576m（不转就会差半公里）
驾车路线  软件园2期->集美大学 -> 17972m / 1735s / 334 个折线点
路线缓存  同一请求二次调用   -> 命中缓存，514ms -> 19ms
无效 key                  -> 502 MAP_ROUTE_QUOTE_FAILED (INVALID_USER_KEY)，
                             且 demoProvider 仍为 false（不会静默降级成 demo）
API Key 泄漏检查          -> route_snapshots 与服务日志中均为 0 条命中
```

## 服务器部署与升级（demo 站，2026-07-07 起）

线上 demo 站（nginx 反代：`/o2o/` H5、`/o2o-admin/` 运营台、`/o2o-api/` → Gateway :8080）曾出现两类事故，部署时必须遵守以下规则：

1. **前后端必须同 commit 一起部署。** 运营台前端（S29/S30）调用的列表接口在旧后端 jar 上不存在；在 S31 修复前，未映射路径还会被兜底成 `500 INTERNAL_ERROR`，让「版本不一致」看起来像服务器崩溃。升级步骤：
   ```bash
   git pull
   ./mvnw clean package -DskipTests        # 干净重打，避免 Maven 增量漏 repackage（见 AGENTS.md 的坑）
   pkill -f '0.5.0-SNAPSHOT.jar'; scripts/start-services.sh
   pnpm install --frozen-lockfile && pnpm build   # 两个 app 的 dist 同步发到 nginx 目录
   scripts/check-deployment.sh https://<domain>/o2o-api   # 全绿才算完成
   ```
2. **主机内存必须给足：14 个 JVM + 6 个中间件容器需要 ≥ 8 GB 可用内存。** 2026-07-07 的线上排查证明：内存吃紧时冷服务进程被换页，任何「一段时间没人点的按钮」首次请求要 1.5–14.5 秒（页换入），热路径也会随机卡顿 0.6–2 秒；同一代码在余量充足的机器上全部 20–100ms。判断方法：`free -h` 看 swap 使用量、`vmstat 1` 看 si/so 是否非零、或跑 `scripts/check-deployment.sh` 看 cold/warm 差距。`scripts/start-services.sh` 已内置每 JVM 的 SerialGC/线程栈/元空间/Tomcat 线程/Hikari 连接收敛，进一步不够就必须加内存或减服务数，不要关 Demo 收件箱轮询（轮询是目前唯一让服务保持常驻内存的东西）。
3. **部署后验收**：`scripts/check-deployment.sh <api-base>` 必须 ALL CHECKS PASSED——它会验证运营台会话、S29 列表接口、404 语义与冷/暖延迟快照。

## 发布策略

- `0.1.0`：可运行 MVP，内存服务 + 前端闭环 + 中间件骨架。
- `0.2.0`：MySQL/Flyway 持久化基线，覆盖用户、司机审核、文件对象、OCR Mock 任务；真实地图、订单延迟消息继续后移。
- `0.3.0`：核心可用闭环，覆盖 Trip/Order/Payment Sim MySQL 持久化、库存幂等锁座/释放、支付超时定时取消、后台真实聚合读、前端接 Gateway API。
- `0.4.0`：可靠性与安全底座，覆盖 Order Outbox + RabbitMQ TTL/DLX 延迟取消、MinIO 私有上传/下载授权、MongoDB 审计落库、文件/审计 RBAC 和前端文件流。
- `0.5.0`：地图权威化，覆盖高德 Web 服务地理编码 + 驾车路线规划适配、Map RouteSnapshot 持久化、Trip 发布服务端计价和 H5 城市提示。
- `1.0.0`：真实支付/实名/隐私合规/监控告警/部署流程稳定后定义。

## 升级策略

- Spring 依赖通过 BOM 锁定版本。
- Boot 4 / Spring Cloud 2025.1 作为后续 ADR，不进入首期。
- 前端依赖升级必须通过 typecheck、build 和主要页面 E2E。
