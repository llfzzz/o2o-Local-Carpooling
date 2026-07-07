# Demo 使用手册

本文档面向“项目已经启动后，怎么实际操作 Demo”。它按当前代码与页面能力整理：哪些在网页里点，哪些需要用运营 Demo API 驱动，哪些只是 API 能力但还没有前端按钮。

## 入口

默认本地入口如下，实际端口以你启动 Vite 时终端输出为准：

| 入口 | 默认地址 | 用途 |
|---|---|---|
| 用户 H5 | `http://127.0.0.1:5173` | 登录/注册、找车、发布示例行程、下单锁座、发起支付、取消订单、实名认证、司机证件提交、收件箱、评价 |
| 运营后台 | `http://127.0.0.1:5174` | 运营总览、司机证件审核、订单监控、行程总览、用户管理、审计检索 |
| Gateway API | `http://127.0.0.1:8080` | 所有外部 API 都经这里访问；运营 Demo 控制接口也走这里 |

运营后台没有单独登录页。它会在 Demo profile 下自动调用 `/api/auth/demo/operator-session` 获取 `OPERATOR + ADMIN` 会话。普通用户端则通过手机号验证码登录，首次登录即完成注册。

## 当前可演示能力总览

用户 H5 已有页面入口：

| 功能 | 页面位置 | 能做什么 |
|---|---|---|
| 手机号注册/登录 | 打开用户 H5 后默认登录页 | 发送验证码、查看演示验证码、输入验证码登录；新手机号首次登录会创建 `RIDER` 用户 |
| 找车/发布示例行程 | H5 `找车` Tab | 输入起点/终点/城市，发布一条示例行程，搜索行程，下单锁座 |
| 支付意图 | H5 `找车` Tab 的“当前订单”卡片 | 对待支付订单点击“发起支付”，生成 Payment Intent；最终支付结果需要运营 Demo API 触发 |
| 取消订单 | H5 当前订单卡片 | `PENDING_PAYMENT` 或 `SEAT_LOCKED` 订单可由乘客取消并释放座位 |
| 实名认证 | H5 `认证` Tab | 输入真实姓名/证件号发起实名认证；最终结论需要运营 Demo API 触发 |
| 司机证件提交 | H5 `认证` Tab | 实名通过后上传驾驶证/行驶证并提交司机证件审核 |
| 收件箱 | H5 `收件箱` Tab | 查看验证码、实名认证结果、评价邀请等消息；敏感内容需要点击“查看”显式取出 |
| 订单评价 | H5 当前订单卡片 | 订单变成 `COMPLETED` 后提交 1-5 分评价，每单只能评价一次 |

运营后台已有页面入口：

| 功能 | 页面位置 | 能做什么 |
|---|---|---|
| 运营总览 | admin-console `运营总览` | 看待审核司机、今日订单、锁座订单、超时待处理等指标 |
| 司机审核 | admin-console `司机审核` | 查看司机证件审核 case、下载短时文件链接、点击“通过”或“驳回” |
| 订单监控 | admin-console `订单监控` | 查看订单列表和状态 |
| 行程总览 | admin-console `行程总览` | 按起点/终点查询已发布行程、查看库存/价格/路线源 |
| 用户管理 | admin-console `用户管理` | 查看用户列表、脱敏手机号、角色 |
| 审计检索 | admin-console `审计检索` | 按对象类型、操作、操作者检索审计日志 |

当前还没有前端按钮、需要用运营 API 驱动的能力：

| 能力 | API |
|---|---|
| 支付成功/失败/取消/过期回调 | `POST /api/demo/control/payment/{intentId}/callbacks` |
| 实名认证活体结果 | `POST /api/demo/control/identity/{verificationId}/liveness` |
| 实名认证会话结果 | `POST /api/demo/control/identity/{verificationId}/session` |
| 运营完成订单 | `POST /api/orders/{orderId}/complete` |
| 手动支付超时取消 | `POST /api/orders/{orderId}/timeout` |
| 模拟收件箱投递状态 | `POST /api/demo/control/notification/{deliveryId}/status` |

## 账号与角色

### 普通用户注册/登录

1. 打开用户 H5：`http://127.0.0.1:5173`。
2. 在“手机号登录”页输入手机号，例如 `13800000000`。
3. 点击“发送验证码”。
4. 页面提示验证码已写入演示收件箱后，点击“查看演示验证码”。
5. 把显示出来的 6 位验证码填入“验证码”输入框。
6. 点击“登录”。

首次使用某个手机号登录时，后端会自动创建用户，相当于注册。新用户默认只有 `RIDER` 角色；客户端不能自己选择 `DRIVER`、`OPERATOR` 或 `ADMIN`。

### 运营账号

运营账号不通过 H5 注册。运营后台打开后会自动获取 Demo 运营会话。

如果你需要在终端里调用运营 Demo API，可以先拿运营 token：

```bash
export GW=http://127.0.0.1:8080
export OP_TOKEN="$(
  curl -s -X POST "$GW/api/auth/demo/operator-session" \
    -H 'Content-Type: application/json' \
    -d '{}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])'
)"
```

这个接口只在 Demo profile 且 seed 开关打开时存在；staging/production 下会不可用。

## 推荐完整闭环：登录 -> 发布 -> 下单 -> 支付 -> 完成 -> 评价

这条路径最快验证主业务闭环。当前 H5 的“发布示例行程”会直接用当前登录用户的 `userId` 作为 `driverId`，方便 Demo 点击走通；司机证件审核链路见后文“司机认证与证件审核”。

### 1. 登录用户 H5

按“普通用户注册/登录”的步骤登录。示例手机号可以用 `13800000000`。

### 2. 发布一条示例行程

1. 进入 H5 `找车` Tab。
2. 填写：
   - 出发：`软件园三期`
   - 到达：`集美大学`
   - 城市：`厦门`
3. 点击“发布示例行程”。
4. 成功后页面会刷新行程列表，地图区域会显示“本地 Mock 路线快照”或“高德路线快照”，价格和距离由服务端返回。

说明：Demo profile 默认使用 `providers.map.type=demo`，因此路线源通常是 `amap-mock`，不是 H5 真实地图 SDK。

### 3. 搜索并选择行程

1. 保持 `找车` Tab。
2. 行程列表出现后，点击你刚发布的行程。
3. 在“订座确认”卡片里选择座位数，默认 1 座。

### 4. 下单锁座

1. 点击“下单锁座 ¥xx.xx”。
2. 下方会出现“当前订单”卡片。
3. 订单状态应为“待支付”，服务端状态是 `PENDING_PAYMENT`。
4. 订单卡片会显示 `order-...` 形式的订单 ID，后续完成订单时会用到。

下单后 Trip 库存会被锁定；如果取消或支付超时，座位会释放。

### 5. 发起支付

1. 在“当前订单”卡片点击“发起支付”。
2. 页面会显示“支付意图”，格式类似：

```text
pi-xxxxxxxx · 待支付
```

3. 记下页面显示的 `intentId`，也就是 `pi-...`。

注意：这里还没有真正支付成功。Demo 支付必须由“签名支付回调”驱动，前端不会自己把订单改成已支付。

### 6. 运营触发支付成功

在终端里先确保有运营 token：

```bash
export GW=http://127.0.0.1:8080
export OP_TOKEN="$(
  curl -s -X POST "$GW/api/auth/demo/operator-session" \
    -H 'Content-Type: application/json' \
    -d '{}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])'
)"
```

然后把 `INTENT_ID` 替换成页面上看到的 `pi-...`：

```bash
export INTENT_ID=pi-替换成页面上的支付意图ID

curl -s -X POST "$GW/api/demo/control/payment/$INTENT_ID/callbacks" \
  -H "Authorization: Bearer $OP_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"SUCCEEDED","mode":"NORMAL","delaySeconds":0}' \
| python3 -m json.tool
```

成功后返回里的 `finalStatus` 应为 `SUCCEEDED`。H5 会每 4-5 秒轮询支付意图和订单；稍等后当前订单状态会从“待支付”变成“已支付 · 座位锁定”，服务端状态为 `SEAT_LOCKED`。

可演示的支付结局：

| outcome | 含义 |
|---|---|
| `SUCCEEDED` | 支付成功；会把订单推进到 `SEAT_LOCKED` |
| `FAILED` | 支付失败；Payment Intent 终态失败，订单不会变成已支付 |
| `CANCELED` | 支付被取消；订单不会变成已支付 |
| `EXPIRED` | 支付意图过期；订单不会变成已支付 |

可演示的投递模式：

| mode | 含义 |
|---|---|
| `NORMAL` | 正常回调 |
| `DUPLICATE` | 重复同一个事件，演示幂等 |
| `OUT_OF_ORDER` | 追加冲突终态，演示终态不可被乱序回调覆盖 |

如果选择 `FAILED` / `CANCELED` / `EXPIRED`，订单一般仍处于 `PENDING_PAYMENT`，可以手动取消或等待支付超时取消。

### 7. 完成订单

当前 admin-console 只能查看订单，没有“完成订单”按钮。完成订单需要用运营 token 调接口。

把 `ORDER_ID` 替换成 H5 当前订单卡片里的 `order-...`：

```bash
export ORDER_ID=order-替换成页面上的订单ID

curl -s -X POST "$GW/api/orders/$ORDER_ID/complete" \
  -H "Authorization: Bearer $OP_TOKEN" \
| python3 -m json.tool
```

成功后订单状态变为 `COMPLETED`。系统会向乘客 Demo 收件箱投递一条 `ORDER_REVIEW_INVITATION` 评价邀请。

### 8. 提交评价

1. 回到 H5 `找车` Tab。
2. 等当前订单卡片刷新到“已完成”。
3. 卡片里会出现评价区域。
4. 选择评分 1-5。
5. 可选填写评价文字。
6. 点击“提交评价”。

限制：

- 只有该订单乘客能评价。
- 订单必须是 `COMPLETED`。
- 每个订单只能评价一次。
- 重复提交会被拒绝。

## 取消与超时路径

### 乘客主动取消

在 H5 当前订单卡片点击“取消订单”。

可取消状态：

- `PENDING_PAYMENT`
- `SEAT_LOCKED`

取消后状态通常是 `USER_CANCELLED`，座位会释放。

### 运营主动取消

当前 admin-console 没有取消按钮，可以用运营 token：

```bash
curl -s -X POST "$GW/api/orders/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $OP_TOKEN" \
| python3 -m json.tool
```

运营取消后的状态是 `OPERATOR_CANCELLED`。

### 支付超时取消

正常路径是 RabbitMQ TTL/DLX + order-service 定时兜底扫描，默认支付期限约 15 分钟。为了手动演示，可以调用保留的 smoke/admin 测试入口：

```bash
curl -s -X POST "$GW/api/orders/$ORDER_ID/timeout" \
  -H "Authorization: Bearer $OP_TOKEN" \
| python3 -m json.tool
```

仅 `PENDING_PAYMENT` 订单可以超时取消。成功后状态是 `TIMEOUT_CANCELLED`，座位释放。

## 司机认证与证件审核

司机相关能力分两段：

1. 用户实名认证 + 活体检测：H5 发起，运营 Demo API 决定结果。
2. 驾驶证/行驶证证件审核：H5 上传提交，admin-console 通过或驳回。

当前注意点：

- 司机证件提交前，服务端会检查该用户实名认证是否已经 `APPROVED`。
- 证件审核通过/驳回会写审计。
- 当前 H5 “发布示例行程”是 Demo 快速入口，并未被前端司机证件审核结果拦截；不要把它理解成完整生产级司机准入 UI。
- 当前没有“授予 DRIVER 角色”的前端入口；用户角色仍以服务端用户记录为准。

### 方案 A：页面发起实名认证，终端驱动结果

1. 用需要成为司机的手机号登录 H5。
2. 进入 `认证` Tab。
3. 在“实名认证 + 活体检测”卡片输入：
   - 真实姓名：例如 `张三`
   - 证件号：例如 `350211199001011234`
4. 点击“发起实名认证”。
5. 页面会显示“认证中 / 待检测”。

要把它推进到通过状态，运营 API 需要 `verificationId`。当前 H5 页面没有直接展示这个 ID；可以从浏览器开发者工具 Network 面板里查看 `POST /api/identity/verifications` 的响应，或者使用下方“方案 B”直接用 API 发起并保存 ID。

拿到 `verificationId` 后：

```bash
export VERIFICATION_ID=idv-替换成实名认证会话ID

curl -s -X POST "$GW/api/demo/control/identity/$VERIFICATION_ID/liveness" \
  -H "Authorization: Bearer $OP_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"PASSED"}' \
| python3 -m json.tool

curl -s -X POST "$GW/api/demo/control/identity/$VERIFICATION_ID/session" \
  -H "Authorization: Bearer $OP_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"outcome":"APPROVED"}' \
| python3 -m json.tool
```

H5 会轮询状态，稍等后会变成“认证通过 / 活体通过”。同时用户收件箱会出现 `IDENTITY_VERIFICATION_RESULT`。

### 方案 B：用 API 发起实名认证并保存 ID

如果你不想从浏览器 Network 面板找 `verificationId`，可以直接用 API 登录并发起认证。

登录普通用户并得到 token：

```bash
export GW=http://127.0.0.1:8080
export PHONE=13800138000

curl -s -X POST "$GW/api/auth/sms-code" \
  -H 'Content-Type: application/json' \
  -d "{\"phone\":\"$PHONE\"}" >/dev/null

export CODE="$(
  curl -s "$GW/api/auth/sms-code/demo-inbox?phone=$PHONE" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["code"])'
)"

export RIDER_AUTH="$(
  curl -s -X POST "$GW/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}"
)"

export RIDER_TOKEN="$(echo "$RIDER_AUTH" | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')"
export RIDER_ID="$(echo "$RIDER_AUTH" | python3 -c 'import sys,json; print(json.load(sys.stdin)["user"]["userId"])')"
```

发起实名认证：

```bash
export IDV_RESP="$(
  curl -s -X POST "$GW/api/identity/verifications" \
    -H "Authorization: Bearer $RIDER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"realName":"张三","idNumber":"350211199001011234"}'
)"

export VERIFICATION_ID="$(echo "$IDV_RESP" | python3 -c 'import sys,json; print(json.load(sys.stdin)["verificationId"])')"
echo "$VERIFICATION_ID"
```

再用“方案 A”里的两个运营 API 把活体置为 `PASSED`、会话置为 `APPROVED`。

### 实名结果的其他可演示状态

活体检测可选：

| outcome | 含义 |
|---|---|
| `PASSED` | 活体通过 |
| `FAILED` | 活体失败 |
| `TIMEOUT` | 活体超时 |
| `RETRY_REQUIRED` | 需要重试 |

实名认证会话可选：

| outcome | 含义 |
|---|---|
| `APPROVED` | 认证通过；要求活体已 `PASSED` |
| `REJECTED` | 认证驳回 |
| `TIMEOUT` | 认证超时 |
| `RETRY_REQUIRED` | 需要重试 |

### 提交司机证件

实名认证通过后：

1. 回到 H5 `认证` Tab。
2. 在“司机证件审核”卡片选择“驾驶证”文件。
3. 选择“行驶证”文件。
4. 文件类型建议使用 `png`、`jpg`、`jpeg`、`webp` 或 `pdf`，大小不超过默认 10 MiB。
5. 点击“提交证件审核”。
6. 成功后页面提示“OCR Mock 已识别，等待后台复核”，状态进入 `OCR_REVIEWABLE`。

证件上传走 MinIO 私有对象：

- 前端先请求 presigned upload。
- 浏览器把文件 PUT 到 MinIO。
- 前端再调用 complete。
- 服务端校验文件存在、类型、大小。

### 运营审核司机证件

1. 打开运营后台：`http://127.0.0.1:5174`。
2. 进入 `司机审核`。
3. 找到对应用户的审核记录。
4. 可点击资料按钮生成短时下载链接查看证件。
5. 点击“通过”或“驳回”。

审核状态：

| 状态 | 含义 |
|---|---|
| `OCR_REVIEWABLE` | OCR 已识别，等待人工复核 |
| `APPROVED` | 运营通过 |
| `REJECTED` | 运营驳回 |

## 收件箱怎么用

H5 `收件箱` Tab 会列出当前用户自己的 Demo 投递记录。常见 category：

| category | 来源 |
|---|---|
| `AUTH_SMS_CODE` | 手机号验证码 |
| `IDENTITY_VERIFICATION_RESULT` | 实名认证结果 |
| `ORDER_REVIEW_INVITATION` | 订单完成后的评价邀请 |

操作方式：

1. 进入 H5 `收件箱` Tab。
2. 列表里默认只显示遮罩预览。
3. 点击某条消息右侧“查看”。
4. 明文内容会以 toast 形式显示，同时该消息会被标记为已读。

收件箱按用户隔离：A 用户看不到 B 用户的消息。敏感内容有有效期，过期后不能 reveal。

运营也可以模拟投递状态变化。把 `DELIVERY_ID` 换成收件箱列表里的 `ntf-...`：

```bash
curl -s -X POST "$GW/api/demo/control/notification/$DELIVERY_ID/status" \
  -H "Authorization: Bearer $OP_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"FAILED"}' \
| python3 -m json.tool
```

可选状态：`QUEUED`、`DELIVERED`、`FAILED`、`RETRYING`、`READ`。

## 运营后台怎么用

### 运营总览

打开 admin-console 后默认进入 `运营总览`。

这里可以看：

- 待审核司机数量。
- 今日订单。
- 锁座订单。
- 支付超时待处理。
- 简要审计时间线。

如果顶部提示“Gateway 未连接”，说明前端访问不到 Gateway 或后端服务未完全就绪。

### 司机审核

进入 `司机审核`：

- 资料列：点击文件按钮可以生成短时下载链接。
- 状态列：查看 `OCR_REVIEWABLE` / `APPROVED` / `REJECTED`。
- 操作列：只有 `OCR_REVIEWABLE` 状态可点击“通过”或“驳回”。

### 订单监控

进入 `订单监控`：

- 查看订单 ID、行程 ID、乘客 ID、座位、金额、状态。
- 目前只读，没有取消/完成按钮。
- 要完成或运营取消订单，用上文的运营 API。

订单状态含义：

| 状态 | 含义 |
|---|---|
| `PENDING_PAYMENT` | 已下单锁座，等待支付 |
| `SEAT_LOCKED` | 支付成功，座位锁定 |
| `TIMEOUT_CANCELLED` | 支付超时取消 |
| `USER_CANCELLED` | 乘客取消 |
| `DRIVER_CANCELLED` | 司机取消 |
| `OPERATOR_CANCELLED` | 运营取消 |
| `COMPLETED` | 行程完成，可评价 |

### 行程总览

进入 `行程总览`：

- 可按起点/终点查询。
- 可查看司机 ID、路线、出发时间、距离、库存、单价、路线源、状态。
- 路线源 `amap-mock` 表示 Demo map provider，不是真实地图线路。

### 用户管理

进入 `用户管理`：

- 查看用户 ID。
- 查看脱敏手机号。
- 查看服务端持久化角色。
- 普通手机号登录创建的用户一般是 `RIDER`。

### 审计检索

进入 `审计检索`：

- 对象类型：例如 `ORDER`、`DRIVER_VERIFICATION`。
- 操作：例如 `ORDER_COMPLETED`、`ORDER_REVIEW_SUBMITTED`、`DRIVER_VERIFICATION_APPROVED`。
- 操作者：填 userId 或 operator userId。

状态变更、文件访问、评价提交等关键动作会写审计。

## API-only 能力

### 独立 OCR 任务

H5 的司机证件提交会触发 driver-service 内部 OCR Mock 流程。除此之外，ai-service 也提供独立 OCR 任务 API，主要用于验证 Provider 化后的 OCR 生命周期。

```bash
curl -s -X POST "$GW/api/ai/ocr/tasks" \
  -H "Authorization: Bearer $OP_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"fileObjectId":"file-替换成文件ID"}' \
| python3 -m json.tool
```

返回 `taskId` 后轮询：

```bash
curl -s "$GW/api/ai/ocr/tasks/$TASK_ID" \
  -H "Authorization: Bearer $OP_TOKEN" \
| python3 -m json.tool
```

Demo OCR 会从 `PROCESSING` 推进到 `COMPLETED`，并返回脱敏结果。

### 地图路线报价

H5 发布行程时会自动调用 Map 服务。如果想单独验证路线报价：

```bash
curl -s "$GW/api/maps/route?origin=软件园三期&destination=集美大学&city=厦门" \
  -H "Authorization: Bearer $OP_TOKEN" \
| python3 -m json.tool
```

Demo profile 下 provider 通常是 `demo`，响应里的 provider trace 通常是 `amap-mock`。

## 两个用户分开演示乘客和司机

如果你想更接近真实业务，可以使用两个手机号：

1. 用手机号 A 登录 H5，完成“司机认证与证件审核”，并在 `找车` Tab 发布示例行程。
2. 点击右上角“退出”。
3. 用手机号 B 登录 H5。
4. 搜索手机号 A 发布的路线并下单。
5. 手机号 B 发起支付，运营 API 触发支付成功。
6. 运营 API 完成订单，手机号 B 提交评价。

注意：当前 H5 没有多账号同时在线能力。要同时观察两个账号，可以使用两个浏览器 profile、无痕窗口，或登录/退出切换。

## 常见问题

### 为什么“发送验证码”后响应里没有验证码？

这是安全设计。验证码不会从普通 API 响应直接返回，必须通过 Demo 收件箱显式查看。H5 登录页的“查看演示验证码”本质上是 Demo-only 查询入口。

### 为什么支付点了“发起支付”后没有成功？

“发起支付”只创建 Payment Intent。支付成功/失败必须由运营 Demo API 触发签名回调。用 `POST /api/demo/control/payment/{intentId}/callbacks` 传 `SUCCEEDED` 后，订单才会变成 `SEAT_LOCKED`。

### 为什么实名认证发起后一直是“认证中”？

实名认证结果也是供应商回调驱动。Demo 中需要运营 API 先把活体置为 `PASSED`，再把会话置为 `APPROVED`。如果直接审批 `APPROVED` 但活体还没通过，会被服务端拒绝。

### 为什么司机证件提交按钮是灰的？

必须先让实名认证状态变为 `APPROVED`。H5 和服务端都有门禁，未通过时无法提交驾驶证/行驶证。

### 为什么 admin-console 找不到支付控制台按钮？

当前后端 Demo Control API 已有，但 admin-console 的支付/实名控制台前端按钮尚未实现。请用本文档里的 `curl` 命令驱动支付和实名结果。

### 为什么 admin-console 订单页不能完成订单？

当前订单监控页是只读表格。订单完成需要调用 `POST /api/orders/{orderId}/complete`，可以用运营 token 或该行程司机 token。

### 为什么证件审核通过后用户角色还是 RIDER？

当前 Demo 可完成“实名通过 + 证件审核通过”的审核链路，但还没有前端入口把普通用户角色变更为 `DRIVER`。角色仍来自服务端用户记录，不能由客户端自选。

### 怎么确认操作真的生效？

优先看三个地方：

1. H5 当前订单卡片：订单状态、支付意图状态、评价区域。
2. admin-console：订单监控、司机审核、用户管理。
3. admin-console 审计检索：搜索 `ORDER`、`DRIVER_VERIFICATION` 等对象类型。
