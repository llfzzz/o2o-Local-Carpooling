# 架构设计

## 技术基线

- JDK 21
- Spring Boot 3.5.15
- Spring Cloud 2025.0.3
- Spring Cloud Alibaba 2025.0.0.0
- React + TypeScript + Vite
- Ant Design / ProComponents / Ant Design Mobile
- MySQL、Redis、RabbitMQ、MongoDB、MinIO、Nacos

## 模块

```text
gateway-service       统一入口、路由、限流、鉴权入口
auth-service          手机号登录、Token、角色
user-service          用户资料、角色、账号状态
driver-service        司机资质、车辆、审核状态
trip-service          发车、路线快照、搜索、库存视图
order-service         订座、状态机、超时取消
payment-sim-service   模拟支付隔离层
map-service           高德地图适配层和路线快照
file-service          MinIO 文件对象和授权访问
ai-service            OCR Mock 和未来 OCR 供应商适配
admin-service         运营后台聚合接口
audit-service         审计日志和关键事件归档
common                领域模型、状态机、事件类型
```

## 关键链路

```mermaid
sequenceDiagram
  participant Rider as 乘客 H5
  participant Gateway as Gateway
  participant Trip as trip-service
  participant Order as order-service
  participant Pay as payment-sim-service
  participant MQ as RabbitMQ

  Rider->>Gateway: 搜索路线
  Gateway->>Trip: GET /api/trips
  Trip-->>Rider: 行程 + RouteSnapshot + SeatInventory
  Rider->>Gateway: 创建订单(幂等键)
  Gateway->>Order: POST /api/orders
  Order->>Order: 校验库存并锁座
  Order->>MQ: 投递支付超时延迟消息
  Rider->>Gateway: 模拟支付
  Gateway->>Pay: POST /api/payments/simulations
  Pay->>Order: 标记支付成功
  MQ-->>Order: 超时消息到达
  Order->>Order: 幂等检查，未支付则取消并释放库存
```

## 数据一致性

- 订单创建使用 `rider_id + idempotency_key` 防重复。
- 行程库存使用版本号或数据库行锁，后续落库时实现乐观锁。
- 订单事件使用 Outbox 表，消费者通过 `event_id` 幂等。
- 支付超时消息必须先读订单当前状态，不能直接取消。

## 可插拔适配

- 高德地图通过 `map-service` 统一封装，保存供应商响应快照。
- OCR 首期用 `MockOcrPolicy`，未来替换为外部 OCR Provider。
- 支付首期为 `payment-sim-service`，真实支付必须新建适配器并隔离回调签名校验。
