## WMPP (Web Message Push Platform)

一个可运行的 Web 消息推送平台，支持单体与单机微服务部署。

- 角色：`gateway / scheduler / pusher / registry`
- 通道：`WebSocket`（优先）+ `SSE`（降级）
- 鉴权：`appId + appSecretKey`
- 推送：广播 / 单用户 / 批量用户 / Topic
- 可靠性（基础版）：`msgId + ACK + 有限重试 + 客户端去重`

---

## 1. 功能清单（当前版本）

- App 级鉴权（`X-App-Id` + `X-App-Secret-Key`）
- 客户端连接分配：`GET /api/connect`
- WebSocket 控制通道：`/connect`（兼容 `/ws/push`）
- SSE 数据通道：`/stream`
- 推送接口：`/push/broadcast`、`/push/user`、`/push/users`、`/push/topic`
- 心跳与连接回收（超时清理）
- RR / LeastConnection 调度
- 健康检查与指标端点（Actuator）

---

## 2. 客户怎么接入（重点）

### 2.1 客户前端接入 SDK

在业务前端页面引入：

```html
<script src="http://<gateway-host>:8080/wmpp-sdk.js"></script>
<script>
  wmpp.connect("systemA", "1001", {
    transportPolicy: "ws_primary_sse_fallback",
    heartbeatMs: 15000,
    wsReconnectMinMs: 1000,
    wsReconnectMaxMs: 15000,
    dedupeWindowMs: 30000,
    onMessage: ({ id, body, raw }) => {
      const text = body?.payload ?? raw
      console.log("收到消息", id, text)
    }
  })
</script>
```

说明：
- SDK 先调用 `/api/connect` 自动拿目标 pusher 地址
- 默认 WS 优先，异常时自动降级 SSE
- 收到带 `msgId` 的消息会自动 ACK 并去重

### 2.2 客户后端调用推送接口

所有推送接口都需要 Header：

- `X-App-Id: <appId>`
- `X-App-Secret-Key: <appSecretKey>`

广播：

```bash
curl -i -H "X-App-Id: systemA" -H "X-App-Secret-Key: systemA-secret" \
  "http://<gateway-host>:8080/push/broadcast?message=hello"
```

单用户：

```bash
curl -i -X POST -H "X-App-Id: systemA" -H "X-App-Secret-Key: systemA-secret" \
  "http://<gateway-host>:8080/push/user?userId=1001&message=hello-1001"
```

批量用户：

```bash
curl -i -X POST -H "X-App-Id: systemA" -H "X-App-Secret-Key: systemA-secret" \
  -H "Content-Type: application/json" \
  -d '{"userIds":["1001","2002"],"message":"hello-users"}' \
  "http://<gateway-host>:8080/push/users"
```

Topic：

```bash
curl -i -X POST -H "X-App-Id: systemA" -H "X-App-Secret-Key: systemA-secret" \
  "http://<gateway-host>:8080/push/topic?topic=starA&message=hello-topic"
```

---

## 3. 本地开发（单体模式）

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=8080 --wmpp.role=mono" spring-boot:run
```

打开：`http://localhost:8080/sdk-test.html`

---

## 4. Docker 微服务部署（推荐演示）

```bash
docker compose up --build -d
docker compose ps
```

### 4.1 健康检查

```bash
curl -s http://127.0.0.1:8080/actuator/health
curl -s http://127.0.0.1:8080/actuator/health/readiness
```

### 4.2 关键端口

- gateway: `8080`
- scheduler: `8081`
- pusher1: `8084`
- pusher2: `8085`
- registry: `8090`

### 4.3 connect 检查

```bash
curl -i "http://127.0.0.1:8080/api/connect?appId=systemA&userId=1001"
```

应返回可访问的 `wsUrl/sseUrl`（不要是 `localhost`，应为你的服务器 IP/域名）。

---

## 5. 可靠性说明（当前实现）

- 服务端推送消息携带 `msgId`
- 客户端收到消息自动发送 ACK（WS）
- 未 ACK 消息进入重试扫描任务
- 超过最大次数标记失败
- 客户端按 `msgId` 去重，避免重复处理

相关配置（`application.properties`）：

- `wmpp.delivery.retry.max-attempts=3`
- `wmpp.delivery.retry.base-delay-ms=500`
- `wmpp.delivery.retry.scan-interval-ms=1000`

---

## 6. 给老师演示怎么走（3 分钟）

1) 服务健康：

```bash
docker compose ps
curl -s http://127.0.0.1:8080/actuator/health
```

2) 动态分配：

```bash
curl -i "http://127.0.0.1:8080/api/connect?appId=systemA&userId=1001"
```

3) 浏览器：
- 打开 `http://<server-ip>:8080/sdk-test.html`
- 点击 Connect（看到 `WMPP WS Connected`）
- 点击四个推送按钮，展示返回成功和消息接收

4) 资源回收：
- 连接后关闭页面
- 等待心跳超时，观察 pusher 日志中的下线/回收信息

---

## 7. 常用命令

查看日志：

```bash
docker compose logs -f gateway
docker compose logs -f scheduler
docker compose logs -f pusher1
docker compose logs -f pusher2
```

停止：

```bash
docker compose down
```
