## WMPP (Web Message Push Platform)

一个面向业务系统后端的多租户消息推送平台 Demo，支持单体与单机微服务部署。

- 角色：`gateway / scheduler / pusher / registry`
- 通道：`WebSocket`（优先）+ `SSE`（降级）
- 鉴权：`appId + appSecretKey`
- 推送：广播 / 单用户 / 批量用户 / Topic
- 可靠性（基础版）：`msgId + ACK + 有限重试 + 客户端去重`
- 平台视角：租户隔离、客户后台、Admin 总后台、节点监控

---

## 1. 当前演示定位

WMPP 不是直接面向最终用户，而是面向 **业务系统后端** 提供实时消息推送能力：

- 你的客户：业务系统 Server
- 终端接收方：业务系统自己的网页端 / App / 小程序 / 设备端 Client
- 平台管理员：WMPP Admin

因此当前版本的演示被拆成 4 个视角：

1. 业务系统 Server 接入页
2. 业务系统终端 Client 接收页
3. 租户控制台（客户后台）
4. Admin 控制台（平台总后台）

---

## 2. 页面入口导航

启动后优先打开：

```text
http://localhost:8080/index.html
```

该入口页会导航到以下页面：

- `http://localhost:8080/biz-server-demo.html`
- `http://localhost:8080/biz-client-demo.html?appId=systemA&userId=1001`
- `http://localhost:8080/biz-client-demo.html?appId=systemA&userId=2002`
- `http://localhost:8080/biz-client-demo.html?appId=systemB&userId=3003`
- `http://localhost:8080/tenant-console.html`
- `http://localhost:8080/admin-console.html`

---

## 3. 各页面说明

### 3.1 业务系统 Server 接入页

页面：`/biz-server-demo.html`

用于模拟“客户后端项目如何接入你的平台”。

展示内容：
- 项目中引用 `server sdk`
- 配置 `baseUrl / appId / appSecret`
- 选择推送类型：广播 / 指定用户 / 批量用户 / Topic
- 发送推送

这对应老师说的：
- 业务系统 Server 页面
- 项目引用 server sdk
- 配置推送

### 3.2 业务系统终端 Client 接收页

页面：`/biz-client-demo.html`

用于模拟“业务系统最终终端用户”。

展示内容：
- 引用 client sdk 思维
- `login/connect`
- 在线状态
- 消息接收日志

这对应老师说的：
- Client 页面只需要 client sdk
- Client 页面只需要 `login/connect`
- 看是否收到消息即可

### 3.3 租户控制台（客户后台）

页面：`/tenant-console.html`

这是客户看到的后台页面，只能查看自己的数据。

展示内容：
- 自己的 `appId / secretKey`
- 自己的在线终端数
- 自己的节点分布
- 自己的路由数据
- 自己的 SDK 接入配置

说明：
- 使用 `X-App-Id + X-App-Secret-Key` 鉴权
- 只能看当前租户自己的数据
- 看不到其他客户的数据

### 3.4 Admin 控制台（平台总后台）

页面：`/admin-console.html`

这是平台管理员看到的页面。

展示内容：
- 全部租户数量
- 总在线终端数
- 活跃租户数
- 节点承载情况
- 全量路由快照

说明：
- 使用 `X-Admin-Token` 鉴权
- 可以查看全平台数据

---

## 4. 本地启动

### 4.1 单体模式

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=8080 --wmpp.role=mono" spring-boot:run
```

启动后打开：

```text
http://localhost:8080/index.html
```

### 4.2 Docker 微服务模式

```bash
docker compose up --build -d
docker compose ps
```

关键端口：

- gateway: `8080`
- scheduler: `8081`
- pusher1: `8084`
- pusher2: `8085`
- registry: `8090`

健康检查：

```bash
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8080/actuator/health/readiness
```

---

## 5. SDK 接入说明

### 5.1 Client SDK 接入思想

业务系统终端客户端只需要：

- 引入 client sdk
- 配置 `appId / userId`
- 调用 `connect/login`
- 接收消息

示例：

```html
<script src="http://<gateway-host>:8080/wmpp-sdk.js"></script>
<script>
  wmpp.connect("systemA", "1001", {
    transportPolicy: "ws_primary_sse_fallback",
    onMessage: ({ body, raw }) => {
      const text = body?.payload ?? raw
      console.log("收到消息", text)
    }
  })
</script>
```

### 5.2 Server SDK 接入思想

业务系统后端只需要：

- 引入 `WmppServerClient`
- 配置 `baseUrl / appId / appSecret`
- 调用推送方法

Java 示例：

```java
WmppServerClient client = new WmppServerClient(
    "http://localhost:8080",
    "systemA",
    "systemA-secret"
);

client.broadcast("hello all");
client.pushUser("1001", "hello 1001");
client.pushUsers(List.of("1001", "2002"), "hello batch");
client.pushTopic("starA", "hello topic");
```

---

## 6. 租户隔离与权限说明

### 客户（Tenant）
客户后台只能看：
- 自己的租户资料
- 自己的在线数
- 自己的路由和节点分布
- 自己的 SDK 配置

### 管理员（Admin）
管理员后台可以看：
- 全部客户
- 全平台在线数据
- 全局节点监控
- 全量路由快照

这对应老师强调的：
- 客户之间互相隔离
- 客户不能看别人的资料和数据
- Admin 可以看全局

---

## 7. 高并发与负载均衡设计

当前版本已经具备可用于答辩讲解的高并发与负载均衡基础能力。

### 7.1 高并发架构基础

系统采用分层微服务结构：

- `gateway`：统一入口
- `scheduler`：调度决策
- `pusher`：长连接承载与消息投递
- `registry`：用户路由注册与查询

这样做的好处是：
- 接入层与推送层解耦
- 长连接承载压力可以分摊到多个 pusher 节点
- 广播与单播可以分别采用不同的投递策略
- 后续可通过增加 pusher 副本实现横向扩展

### 7.2 负载均衡策略

当前系统已支持两种调度策略：

- `rr` / `roundrobin`：轮询分配
- `least` / `leastconn` / `least_connection`：最小连接数分配

新客户端接入时，由 scheduler 为其分配目标 pusher；
若用户已有历史路由，则优先保持路由稳定，减少跨节点漂移。

### 7.3 广播与单播的并发处理思路

- **广播**：调度层将消息 fanout 到全部 pusher 节点，再由各节点向本地连接广播
- **单播 / 批量 / Topic**：先通过 registry 查询用户所在 pusher，再只向目标节点投递

这样可以避免所有消息都走全节点广播，从而减少系统资源浪费。

### 7.4 如何现场演示负载均衡

答辩时可以这样展示：

1. 打开多个终端 Client 页面
2. 刷新 `admin-console.html`
3. 观察“高并发 / 负载均衡演示区”中的：
   - 当前策略
   - 各 pusher 当前连接数
   - 路由快照
4. 说明连接已被分配到不同推送节点

如果切换到 `leastconn` 策略，还可以继续演示最小连接数调度效果。

### 7.5 后续可扩展方向

当前版本重点完成了架构基础与演示能力；如果继续向生产级高并发演进，可接入：

- MySQL：持久化租户与推送记录
- Redis：缓存路由、在线状态、限流信息
- Kafka / RabbitMQ：异步削峰、批量消息分发
- MinIO / OSS：日志与报表归档
- 更完整的监控与告警体系

---

## 8. 答辩演示脚本（推荐 3~5 分钟）

### 第一步：说明系统定位
可以这样讲：

> WMPP 是一个面向业务系统后端的多租户消息推送平台。业务系统后端接入 platform SDK 并调用推送接口，消息最终送达到业务系统自己的终端客户端。平台同时提供客户后台与管理员后台，实现租户隔离与全局监控。

### 第二步：打开入口导航页
打开：

```text
http://localhost:8080/index.html
```

简单说明 4 个视角：
- 业务系统 Server
- 业务系统 Client
- 租户后台
- Admin 后台

### 第三步：展示业务系统 Server 接入
打开：

```text
http://localhost:8080/biz-server-demo.html
```

讲解重点：
- 项目中引用 `server sdk`
- 配置 `baseUrl / appId / appSecret`
- 调用广播、单播、批量、Topic 推送

### 第四步：展示终端 Client 接收
打开三个终端页：

```text
http://localhost:8080/biz-client-demo.html?appId=systemA&userId=1001
http://localhost:8080/biz-client-demo.html?appId=systemA&userId=2002
http://localhost:8080/biz-client-demo.html?appId=systemB&userId=3003
```

讲解重点：
- Client 端只需要 `connect/login`
- 只负责接收消息
- 不承担平台管理能力

### 第五步：演示同业务系统多终端推送
在 Server 页里选择：
- `systemA`
- `broadcast`

发送后预期：
- `systemA / 1001` 收到
- `systemA / 2002` 收到
- `systemB / 3003` 不收到

### 第六步：演示不同业务系统隔离
在 Server 页里切换：
- `systemB`
- `broadcast`

发送后预期：
- 只有 `systemB / 3003` 收到
- `systemA / 1001` 与 `systemA / 2002` 不收到

### 第七步：展示客户后台
打开：

```text
http://localhost:8080/tenant-console.html
```

输入：
- `systemA`
- `systemA-secret`

讲解重点：
- 只能看到自己的租户数据
- 看不到其他客户数据
- 体现多租户隔离

### 第八步：展示 Admin 后台
打开：

```text
http://localhost:8080/admin-console.html
```

输入：
- `change-me`

讲解重点：
- Admin 可以看全部客户
- 可以看全局在线数、节点承载和全量路由
- 体现平台管理能力

---

## 8. 常用命令

查看日志：

```bash
docker compose logs -f gateway
docker compose logs -f scheduler
docker compose logs -f pusher1
docker compose logs -f pusher2
docker compose logs -f registry
```

停止服务：

```bash
docker compose down
```

---

## 10. 当前实现与扩展方向

### 当前已实现
- 多角色微服务结构：`gateway / scheduler / pusher / registry`
- `WebSocket + SSE`
- 广播 / 单用户 / 批量用户 / Topic
- 心跳检测与资源回收
- RR / LeastConnection 调度
- 业务系统 Server / Client 演示页
- 租户后台 / Admin 后台
- 多租户数据隔离

### 可继续扩展
- 登录系统与正式账号体系
- 推送记录持久化
- 套餐 / 配额管理
- 更完整的图表 Dashboard
- MySQL / Redis / MQ / OSS 扩展
