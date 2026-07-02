# ADR 0002: Provider SPI Adapters + Demo/Staging/Production Profiles

## Status

Accepted

## Context

系统依赖多个外部**业务**供应商（短信、支付、实名/活体、OCR、地图、通知）。首期要做「可点击端到端 Demo」，但不能接真实供应商，也不能把 Mock 能力伪装成真实能力，更不能让 Demo 专属能力泄漏到 staging/production。基础设施（MySQL/Redis/RabbitMQ/MongoDB/MinIO/Nacos）必须始终是真实的。

## Decision

- **每个外部业务能力先定义 Provider SPI，再实现 `Demo*` 版本**（`SmsProvider`/`PaymentProvider`/`IdentityVerificationProvider`/`OcrProvider`/`MapRouteProvider`/`NotificationChannelAdapter`）。`map-service` 的 `MapRouteProvider`/`MockRouteProvider`/`AmapRouteProvider` 是最早的范式，其它均照此建立。
- **按 `providers.<capability>.type` 选型**，共享在 `backend/common/src/main/resources/carpooling-providers.yml`，各服务通过 `spring.config.import` 引入。类型为空 = 未配置：Demo bean 不创建、真实调用在调用点 fail-closed。
- **三套 profile**：`demo`（全部 `demo` + 收件箱/控制台/seed 开）、`staging`/`production`（provider type 交给环境变量、全部 Demo 开关关）。
- **Demo 专属能力双重闸门**：`app.demo-mode` + 具体开关（`inbox/control/seed-enabled`）。`DemoModeGuard` 启动期强校验，`DemoEndpoints` 每请求校验并对关闭态返回 404（与「不存在」不可区分）。
- **状态迁移显式状态机**：支付/订单/实名/活体等关键状态用显式合法迁移表（`PaymentIntentStateMachine`、`OrderStateMachine`、`IdentityVerificationStateMachine`、`LivenessCheckStateMachine`），非法迁移抛异常。
- **敏感产出物走收件箱**：验证码、实名结果、（可选）支付结果默认遮罩投递到 Demo 收件箱，显式 reveal 才取明文；支付终态只能由签名回调驱动。

## Consequences

- 接入真实供应商 = 实现对应 SPI + 提供环境变量凭据 + 把 `providers.<cap>.type` 改成供应商名，**核心业务流程与契约不变**（验收标准之一）。
- staging/production 下任何 Demo 开关为真都无法启动；Demo 端点在非 demo 下返回 404，不暴露存在性。
- 新增外部业务能力必须先定义 SPI 再实现 Demo；新增 Demo 端点必须走双重闸门；新增关键状态迁移必须走状态机；新增一次性凭证必须哈希存储 + TTL + 限流 + 锁定（参考 `SmsCodeService`）。
- 代价：每个能力多一层 SPI + 选型样板；`common` 承载了 Provider/Profile 配置基座（`AppProperties`、`ProviderProperties`、`carpooling-providers.yml`），这是 `common` 的合理职责例外。
