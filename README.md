## WMPP Demo

本仓库是 **Web Message Push Platform (WMPP)** 的单机可演进实现。

支持两种运行方式：

- **单体模式（mono）**：所有模块在一个进程内（便于本地开发）
- **单机微服务模式（roles）**：Gateway / Scheduler / Pusher / Registry 多进程（或多容器）运行

---

## 1) 关键能力

- **App 级认证**：Push API 必须携带 `appId` + `appSecretKey`（Header：`X-App-Id`、`X-App-Secret-Key`；或 Query：`appId`、`appSecretKey`）
- **WebSocket 控制通道**：`/ws/push?appId=xxx&userId=xxx`（心跳 `ping/pong`）
- **SSE 数据通道**：`/stream?appId=xxx&userId=xxx`
- **推送模式**：广播 / 单用户 / 批量用户 / Topic
- **FR-001 管理端**：`/admin/apps`（文件持久化到 `data/apps.json`）
- **自动分配 Pusher**：`GET /api/connect?appId=xxx&userId=xxx`（返回 wsUrl + sseUrl；WebSocket 实际路径为 `/connect`，与详细设计一致）

---

## 2) 单体模式启动（mono）

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=8080 --wmpp.role=mono" spring-boot:run
```

打开：`/sdk-test.html`

---

## 3) 单机微服务模式启动（roles，论文演示推荐）

> 下面端口是建议值，你可以按需修改。

### 3.1 Registry

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9090 --wmpp.role=registry" spring-boot:run
```

### 3.2 Pusher

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9084 --wmpp.role=pusher --wmpp.pusher.id=Pusher-1 --wmpp.registry.baseUrl=http://localhost:9090" spring-boot:run
```

### 3.3 Scheduler

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9081 --wmpp.role=scheduler --wmpp.registry.baseUrl=http://localhost:9090 --wmpp.pusher.nodes=Pusher-1=http://localhost:9084" spring-boot:run
```

### 3.4 Gateway

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9080 --wmpp.role=gateway --wmpp.scheduler.baseUrl=http://localhost:9081" spring-boot:run
```

客户端（浏览器）访问 gateway：`http://localhost:9080/sdk-test.html`  
SDK 会先调用 `GET /api/connect` 自动拿到 pusher 的 `wsUrl/sseUrl` 再建立连接。

---

## 4) FR-001：App 管理端 API（示例）

默认管理员 Token 在 `application.properties`：

- `wmpp.admin.token=change-me`

列出 App：

```powershell
$h=@{'X-Admin-Token'='change-me'}
Invoke-WebRequest -UseBasicParsing -Headers $h "http://localhost:9080/admin/apps"
```

---

## 5) SDK

### 5.1 Server SDK

- Java：`sdk/server-java`
- Python：`sdk/server-python`

### 5.2 Client SDK

- Java：`sdk/client-java`
- Python：`sdk/client-python`（如果环境无法安装 `websocket-client`，会只启用 SSE）

---

## 6) Docker（可选）

仓库提供了 `Dockerfile` 与 `docker-compose.yml`。

> 说明：在 Docker 网络下，gateway 返回的 `/api/connect` 中的 ws/sse 地址需要配置“对外可访问的 pusher 地址”。  
可以通过 `wmpp.public.pusher.nodes` 配置 pusher 的 public baseUrl 映射（形如 `Pusher-1=http://localhost:9084`）。

### 6.1 一键启动

```bash
docker compose up --build -d
docker compose ps
```

### 6.2 查看日志

```bash
docker compose logs -f gateway
docker compose logs -f scheduler
docker compose logs -f pusher1
```

### 6.3 快速验收

```bash
curl -i -H "X-App-Id: systemA" -H "X-App-Secret-Key: systemA-secret" \
  "http://127.0.0.1:8080/push/broadcast?message=hello"
```

### 6.4 关闭/清理

```bash
docker compose down
```

---

## 7) 本地 connect 流程冒烟（roles）

启动 `registry/pusher/scheduler/gateway` 后，可直接检查：

```powershell
Invoke-WebRequest -UseBasicParsing "http://localhost:9080/api/connect?appId=systemA&userId=1001"
Invoke-WebRequest -UseBasicParsing "http://localhost:9081/scheduler/assign?appId=systemA&userId=1001"
```

预期返回 JSON，包含：

- `pusherId`
- `httpBaseUrl`
- `wsBaseUrl`

