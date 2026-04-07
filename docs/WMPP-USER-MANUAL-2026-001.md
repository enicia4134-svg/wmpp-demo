## 《Web消息推送平台（WMPP）使用说明书》

**Web Message Push Platform (WMPP) — User Manual**

- **文档编号**：WMPP-USER-MANUAL-2026-001  
- **版本**：V1.0  
- **适用范围**：本仓库 `wmpp-demo`（单体模式 + 单机微服务模式 + Docker 部署）  

---

## 1. 系统简介

WMPP 是一个基于 Web 的消息推送平台，为多个业务系统提供统一推送能力。平台由以下模块组成：

- **Push Gateway**：对外推送入口（App 认证、接收推送请求、转交调度）
- **Scheduler**：推送任务调度（选择 Pusher、路由推送）
- **Pusher Worker**：维护 WebSocket 长连接 + SSE 推送通道，执行实际推送
- **Session/Registry**：维护在线路由（`appId + userId -> pusherId`）
- **SDK**：Server SDK（业务系统调用推送）/ Client SDK（客户端接入接收）

---

## 2. 核心概念

### 2.1 App 级认证（业务系统身份）

业务系统接入平台需要获得：

- `AppID`
- `AppSecretKey`

业务系统调用推送接口必须携带密钥 **`AppSecretKey`**，实现上仅支持以下两种（二选一即可，不要混用多套别名）：

- Header：`X-App-Id`、`X-App-Secret-Key`
- 或 Query：`appId`、`appSecretKey`

### 2.2 客户端双通道（控制/数据）

- **WebSocket 控制通道**：用于在线状态、心跳（`ping/pong`）
- **SSE 数据通道**：用于下行推送消息（`/stream`）

### 2.3 自动分配 Pusher（推荐）

客户端先调用 gateway：

- `GET /api/connect?appId=xxx&userId=xxx`（返回 JSON；WebSocket 实际连接 `ws://.../connect?...`）

gateway 返回该用户应连接的 pusher 地址（包含 wsUrl + sseUrl），客户端按返回值建立连接。

---

## 3. 运行模式

### 3.1 单体模式（mono）

适用：本地开发、快速验证。

启动（PowerShell）：

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=8080 --wmpp.role=mono" spring-boot:run
```

访问测试页：

- `http://localhost:8080/sdk-test.html`

### 3.2 单机微服务模式（roles）

适用：论文演示、单机容器化部署前的联调。

建议端口（可自定义）：

- registry：`9090`
- scheduler：`9081`
- pusher：`9084`（可多个）
- gateway：`9080`

依次启动（PowerShell，多窗口）：

registry：

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9090 --wmpp.role=registry" spring-boot:run
```

pusher：

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9084 --wmpp.role=pusher --wmpp.pusher.id=Pusher-1 --wmpp.registry.baseUrl=http://localhost:9090" spring-boot:run
```

scheduler：

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9081 --wmpp.role=scheduler --wmpp.registry.baseUrl=http://localhost:9090 --wmpp.pusher.nodes=Pusher-1=http://localhost:9084 --wmpp.public.pusher.nodes=Pusher-1=http://localhost:9084" spring-boot:run
```

gateway：

```powershell
.\mvnw.cmd -q "-Dspring-boot.run.arguments=--server.port=9080 --wmpp.role=gateway --wmpp.scheduler.baseUrl=http://localhost:9081" spring-boot:run
```

浏览器从 gateway 访问测试页：

- `http://localhost:9080/sdk-test.html`

### 3.3 Docker Compose（推荐部署方式）

前置：服务器已安装 Docker + Compose。

在项目根目录执行：

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f gateway
```

关闭：

```bash
docker compose down
```

> 重要：如果客户端在容器外（浏览器/手机/外部网络），需要配置 `wmpp.public.pusher.nodes` 为“外部可访问的 pusher 地址”，否则 `/api/connect` 返回的地址可能无法直连。

---

## 4. App 管理（FR-001）

### 4.1 管理端鉴权

管理接口使用 Header：

- `X-Admin-Token`

默认值在 `application.properties`：

- `wmpp.admin.token=change-me`

### 4.2 管理接口

（1）列出所有 App

- `GET /admin/apps`

PowerShell 示例：

```powershell
$h=@{'X-Admin-Token'='change-me'}
Invoke-WebRequest -UseBasicParsing -Headers $h "http://localhost:9080/admin/apps"
```

（2）创建 App（自动生成 secret）

- `POST /admin/apps`
- Body：`{"appId":"systemC","allowPush":true}`

（3）启用/禁用推送

- `POST /admin/apps/{appId}/allowPush?allow=true|false`

（4）重置 secret

- `POST /admin/apps/{appId}/rotateSecret`

### 4.3 持久化文件

App 注册表会落盘到：

- `data/apps.json`

Docker 模式下会挂载为 volume（保证容器重启不丢）。

---

## 5. 客户端接入（Client）

### 5.1 推荐接入方式：先 /api/connect 再连 WS/SSE

（1）获取连接地址：

- `GET http://<gateway>/api/connect?appId=xxx&userId=xxx`

响应示例：

```json
{
  "pusherId": "Pusher-1",
  "wsUrl": "ws://host:port/ws/push?appId=systemA&userId=1001",
  "sseUrl": "http://host:port/stream?appId=systemA&userId=1001"
}
```

（2）建立连接：

- WebSocket：连接 `wsUrl`，并每 15s 发送 `"ping"`（服务端回 `"pong"`）
- SSE：打开 `sseUrl`，接收推送消息（`data:`）

### 5.2 前端 JS Demo

访问：

- `/sdk-test.html`

该页面会使用 `static/wmpp-sdk.js`：

- 先调用 `/api/connect`
- 再建立 WS + SSE

---

## 6. 业务系统推送接入（Server）

### 6.1 HTTP 推送接口（Gateway）

接口统一需要 Header：

- `X-App-Id: <appId>`
- `X-App-Secret-Key: <AppSecretKey>`

推送正文参数统一为 **`message`**（不再使用 `msg` 别名）。

（1）广播推送

- `POST /push/broadcast?message=...`

（2）单用户推送

- `POST /push/user?userId=...&message=...`

（3）批量用户推送

- `POST /push/users`（JSON）

```json
{ "userIds": ["1001","2002"], "message": "hello" }
```

或（便于测试）：

- `GET /push/users?userIds=1001,2002&message=...`

（4）Topic 推送

- `POST /push/topic?topic=...&message=...`

### 6.2 Server SDK

仓库自带：

- Java：`sdk/server-java`
- Python：`sdk/server-python`

Python 示例（本地）：

```bash
cd sdk/server-python
python -m pip install -r requirements.txt
WMPP_BASE_URL=http://localhost:9080 python example.py
```

Java 示例（本地，使用项目 mvnw）：

```powershell
$env:WMPP_BASE_URL="http://localhost:9080"
.\mvnw.cmd -q -f "sdk\server-java\pom.xml" -DskipTests package
.\mvnw.cmd -q -f "sdk\server-java\pom.xml" exec:java "-Dexec.mainClass=com.zqw.wmpp.sdk.server.Example"
```

---

## 7. 常用排查与 FAQ

### 7.1 推送成功但客户端没收到

检查顺序：

- 客户端是否先 `/api/connect` 并正确连接到返回的 `pusher`  
- SSE 是否连通（浏览器 Network 里是否有 `text/event-stream` 连接）  
- registry 是否有路由（registry `/registry/snapshot`）  
- scheduler 是否能访问 pusher internal（日志中是否有 5xx/Unknown pusherId）  

### 7.2 /api/connect 返回的地址客户端无法访问

Docker 网络或内网环境下常见。处理：

- 在 scheduler/gateway 配置 `wmpp.public.pusher.nodes` 为 **外部可访问地址**

示例：

- `Pusher-1=http://<公网IP>:8084`

### 7.3 端口占用

本地多进程启动时，确保端口不冲突；或换一组端口启动。

---

## 8. 配置项速查

| 配置项 | 说明 | 示例 |
|---|---|---|
| `wmpp.role` | 运行角色 | `mono/gateway/scheduler/pusher/registry` |
| `wmpp.admin.token` | 管理端 token | `change-me` |
| `wmpp.appRegistry.file` | App 注册表文件 | `data/apps.json` |
| `wmpp.registry.baseUrl` | registry 地址 | `http://localhost:9090` |
| `wmpp.scheduler.baseUrl` | scheduler 地址（gateway 用） | `http://localhost:9081` |
| `wmpp.pusher.nodes` | pusher internal 地址（scheduler 用） | `Pusher-1=http://localhost:9084` |
| `wmpp.public.pusher.nodes` | pusher public 地址（/api/connect 返回用） | `Pusher-1=http://公网IP:8084` |
| `wmpp.pusher.id` | pusher 自身 ID | `Pusher-1` |

