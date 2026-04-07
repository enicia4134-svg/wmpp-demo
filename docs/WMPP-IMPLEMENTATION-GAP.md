## WMPP 需求/设计 与 当前实现差距清单（V1.0）

本文档用于论文撰写与迭代计划：列出 SRS/DDD 中的关键需求点，并标注当前仓库实现状态。

> 统计口径：以当前工程 `wmpp-demo`（单体 Spring Boot Demo）为准。

---

## 1 已实现（可演示）

- **App 级认证（Push Gateway）**  
  - `/push/**` 接口要求携带 `appId + AppSecretKey`（Header：`X-App-Secret-Key` 或 Query：`appSecretKey`），认证失败返回 401。
- **WebSocket 握手校验（最小校验）**  
  - 连接要求携带 `appId + userId`；校验 appId 存在。
- **三种推送模式（WebSocket 下行）**  
  - 广播推送（按 appId 隔离）
  - 指定用户推送（按 appId + userId）
  - Topic/集合推送（按 appId + topic 获取订阅用户集合并逐个推送）
- **Scheduler（RR）最小实现**  
  - 按 appId 维度轮询选择 PusherNode（演示用）

---

## 2 未实现（已在需求/设计中出现，后续迭代）

- **SSE 数据通道**（G-003 / DDD 2.3.3）
  - 当前仅有 WebSocket 下行推送，未提供 `/stream` SSE 接口。
- **心跳检测与超时回收**（FR-004 / DDD 第4章）
  - 当前未实现心跳消息协议、最后活跃时间、超时断开与注册表更新。
- **同一用户新连接覆盖旧连接**（DDD 2.4.2）
  - 当前会覆盖路由映射，但未显式关闭旧连接（可补强为：新连接建立时 close 旧 session）。
- **Least Connection 调度**（DDD 2.2.3）
  - 当前仅 RR，连接数统计也未与真实 session 数绑定（可在 Session Registry 或 handler 中维护）。
- **业务系统注册管理端/接口**（FR-001）
  - 当前 App 注册表为内存预置数据；未提供管理 API/后台页面。
- **Server SDK（Java/Python）**（SRS 1.3 / DDD 2.6）
  - 当前仅有简易前端 `wmpp-sdk.js` demo；缺少后端 SDK 封装与示例工程。
- **Client SDK 自动重连（WebSocket + SSE）**（DDD 2.5）
  - 目前仅 demo 连接与日志输出，缺少重连与心跳封装。
- **容器化与多容器单机微服务部署**（非功能需求）
  - 当前为单体应用；未提供 Dockerfile / docker-compose 拆分。

---

## 3 建议的 V1.1/V1.2 迭代顺序（最贴合论文）

- **V1.1**：补齐 SSE + 心跳/回收 + 连接覆盖关闭旧连接  
- **V1.2**：引入 Session Registry 抽象 + LeastConn 策略 + Server SDK（Java）最小可用  
- **V1.3**：拆分为单机多容器（gateway/scheduler/pusher/registry/topic）+ docker-compose 演示

