# 加密货币套利监控系统（MVP）

对应《加密货币套利监控系统 - 开发方案》的可运行实现：**同秒对齐、价差与总价颜色、过滤与黑名单、5 分钟滚动涨跌幅与网页蜂鸣、登录与配置**。

## 后端（Java + Spring Boot）

业务与 REST/SSE 实现在 `backend/`，默认端口 **8787**，与前端 Vite 代理一致。

```bash
cd backend
mvn spring-boot:run
```

- 健康检查：<http://localhost:8787/api/health>
- 交易所公共 REST 延时（需登录）：`GET /api/latency?rounds=3`，前端菜单「API 延时」
- 数据：`backend/data/` 下 **H2 文件库**（首次启动创建默认账号）
- 配置：`backend/src/main/resources/application.yml`（`arb.spread-push-ms`、`arb.volatility-push-ms`、交易所列表、`arb.mq.*` 等）

### RabbitMQ（可选，削峰）

**方式 A — 使用上述「单镜像」部署时**：RabbitMQ 已在容器内随应用启动，管理台 <http://localhost:15672>（`guest`/`guest`），一般无需再单独起 Broker。

**方式 B — 本机只起 Rabbit、应用仍用 `mvn spring-boot:run`**：安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/) 后执行 `docker compose up -d`（若 Compose 里仍有独立 `rabbitmq` 服务）或 `.\scripts\start-rabbitmq.ps1`，再在 `application.yml` 中设 `arb.mq.enabled: true` 与 `host: localhost`。

- `enabled: false`（默认）：采集端通过 `TickGateway` **直接**调用 `MarketDataService`，不连 Rabbit。
- `enabled: true`：`MockCollectorService` / `LiveFeedsService` 将 `DepthTick` **发布到队列** `arb.mq.queue`，由 `DepthTickConsumer` 异步消费并写入内存；突发流量积压在队列中，可通过 **`prefetch`**（默认 200）与 **`consumers`** 调节消费速率。

## 前端

```bash
cd 加密
npm install
npm run dev
```

- 界面：<http://localhost:5173>（`/api` 代理到 8787）

默认账号：`admin` / `admin123`。

## Docker 单镜像（推荐部署）

前后端打成一个镜像：Vite 产物在 `classpath:/static`，**同一镜像内还包含 RabbitMQ**（`supervisord` 先起 Broker，再起 Spring Boot，应用默认连 `127.0.0.1:5672`）。

在仓库根目录：

```bash
docker build -t arb-monitor:1.0.0 .
docker run --rm -p 8787:8787 -p 5672:5672 -p 15672:15672 -v arb-data:/app/data -v arb-mq:/var/lib/rabbitmq arb-monitor:1.0.0
```

或使用 Compose（另含可选 Redis / MySQL）：

```bash
docker compose up -d --build
```

- 界面与接口：<http://localhost:8787>（`/api/*` 同源）
- RabbitMQ 管理台：<http://localhost:15672>（`guest` / `guest`）
- 健康检查：<http://localhost:8787/api/health>
- 卷：`arb_h2`（H2）、`rabbitmq_data`（RabbitMQ 数据）

默认 **`ARB_MQ_ENABLED=true`**（与内置 Broker 一致）。若只想内存模拟、不走路由队列，可在环境变量中设 **`ARB_MQ_ENABLED=false`** 后重启容器。

## 架构说明

| 方案组件 | 本仓库 |
|----------|--------|
| WebSocket 采集 | `MockCollectorService`（定时模拟多所深度）；真实所可新增 `@Scheduled` 或独立进程写入同一套 `MarketDataService` |
| MQ 削峰 | `arb.mq.enabled=true` 时经 RabbitMQ 队列 `arb.depth.ticks` 再进入 `MarketDataService`；默认关闭为直连 |
| 同秒对齐 | `AlignStore`（内存） |
| 5 分钟涨跌 | `VolatilityEngine`（内存滑动窗口） |
| 用户与黑名单 | JPA + H2（可改 `application.yml` 换 MySQL） |
| 推送 | SSE `/api/stream/*`（支持 `?token=`） |

## 紧急重启

与方案一致：进程僵死时通过云主机控制台重启；生产可用 `systemd`、Docker `restart: always` 或 K8s 探针。

# shujufenxi
