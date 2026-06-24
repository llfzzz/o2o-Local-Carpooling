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
