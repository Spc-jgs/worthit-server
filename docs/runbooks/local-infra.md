# 本地四服务联合验收

本文说明如何使用独立的 `dev-stack` 启动 WorthIt Phase 0 本地运行时，并完成
MySQL、Redis、Nacos、服务发现、Same-Token 和动态配置刷新验收。

## 权威环境与安全边界

本地中间件权威目录是：

```text
/Users/shaopc/Documents/Script/dev-stack
```

操作前依次阅读：

1. `dev-stack/README.md`
2. `dev-stack/conf/mysql/README.md`
3. `dev-stack/conf/redis/README.md`
4. `dev-stack/conf/nacos/README.md`

不得把 `dev-stack/.env`、密码、JWT Secret、访问 Token、Same-Token 或应用日志
提交到 WorthIt。禁止 `docker compose down -v`、prune、删除卷和清空持久化目录。
先记录 `docker compose ps`；任务开始前已经运行的容器不属于本任务，不能停止。

2026-07-25 本轮验收开始时 `dev-stack-mysql`、`dev-stack-redis` 和
`dev-stack-nacos` 已经运行，因此本轮未启动、重建或停止这些容器。

## 必需环境变量

所有真实值只注入当前 shell 或本机 Secret 工具。验收脚本不会读取
`dev-stack/.env`。

| 变量 | 用途 |
| --- | --- |
| `DEV_STACK_DIR` | dev-stack 绝对路径 |
| `NACOS_SERVER_ADDR` | 应用使用的 Nacos 服务端地址 |
| `NACOS_SERVER_BASE_URL` | Nacos v3 Admin API 基地址 |
| `NACOS_CONSOLE_BASE_URL` | Nacos 控制台基地址 |
| `NACOS_NAMESPACE` | 本地命名空间，默认 `worthit-local` |
| `NACOS_GROUP` | 本地分组，默认 `WORTHIT_LOCAL` |
| `NACOS_USERNAME`、`NACOS_PASSWORD` | Nacos 认证，必须同时设置或同时不设置 |
| `WORTHIT_MYSQL_HOST`、`WORTHIT_MYSQL_PORT` | MySQL 地址 |
| `WORTHIT_AUTH_DB_USERNAME`、`WORTHIT_AUTH_DB_PASSWORD` | Auth 数据库账号 |
| `WORTHIT_TRACKING_DB_USERNAME`、`WORTHIT_TRACKING_DB_PASSWORD` | Tracking 数据库账号 |
| `WORTHIT_REMINDER_DB_USERNAME`、`WORTHIT_REMINDER_DB_PASSWORD` | Reminder 数据库账号 |
| `WORTHIT_REDIS_HOST`、`WORTHIT_REDIS_PORT`、`WORTHIT_REDIS_PASSWORD` | Redis 连接参数 |
| `WORTHIT_SA_TOKEN_JWT_SECRET` | 四服务一致的 Sa-Token JWT Secret |
| `WORTHIT_LOG_DIR` | 本次任务专用临时日志目录 |

首次建库还需要单独注入 `WORTHIT_MYSQL_ADMIN_USERNAME` 和
`WORTHIT_MYSQL_ADMIN_PASSWORD`。管理员账号只用于幂等创建三库、应用账号和授权，
不得传给应用进程。

JWT Secret 和三个应用数据库密码应使用密码生成器产生，不能使用仓库默认值或
在命令行中直接写明文。四个应用必须从同一个 shell 启动，以继承同一组运行变量。

## 启动顺序

### 1. 检查并启动最小中间件

```bash
cd "$DEV_STACK_DIR"
git status --short --branch
docker compose ps
docker compose --profile nacos config --images
docker compose --profile nacos up -d mysql redis nacos
docker compose --profile nacos ps
docker compose --profile nacos logs --tail=200 nacos
curl --fail-with-body \
  http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness
curl --fail-with-body \
  http://127.0.0.1:8080/v3/console/health/readiness
```

渲染镜像必须是 `nacos/nacos-server:v3.0.3`。只记录本次由
`docker compose ... up -d` 新启动的服务名，后续只能停止这些服务。

### 2. 幂等初始化三库

使用管理员连接执行以下语义：

- 创建 `worthit_auth`、`worthit_tracking`、`worthit_reminder`；
- 为每个库创建独立应用账号；
- 每个账号只授权对应库；
- 已存在账号时更新为本次注入的应用密码。

不要把包含密码的 SQL 写入仓库、日志或 shell 历史。完成后立即清除管理员变量：

```bash
unset WORTHIT_MYSQL_ADMIN_PASSWORD
```

数据库表不由管理员脚本创建。三个 App 启动时分别由 Flyway 执行 V1。

### 3. 同步 Nacos 配置

回到 `worthit-server` 工作树：

```bash
scripts/local-infra/nacos-config.sh check
scripts/local-infra/nacos-config.sh sync
scripts/local-infra/nacos-config.sh verify
```

`sync` 只创建命名空间并发布五个 YAML，不删除远端配置。

### 4. 构建并启动四服务

```bash
mvn -DskipTests package
export WORTHIT_LOG_DIR="$(mktemp -d /tmp/worthit-phase0.XXXXXX)"

nohup java -jar \
  worthit-auth/worthit-auth-app/target/worthit-auth-app-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local-infra \
  >"$WORTHIT_LOG_DIR/auth.log" 2>&1 &
AUTH_PID=$!

nohup java -jar \
  worthit-reminder/worthit-reminder-app/target/worthit-reminder-app-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local-infra \
  >"$WORTHIT_LOG_DIR/reminder.log" 2>&1 &
REMINDER_PID=$!

nohup java -jar \
  worthit-tracking/worthit-tracking-app/target/worthit-tracking-app-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local-infra \
  >"$WORTHIT_LOG_DIR/tracking.log" 2>&1 &
TRACKING_PID=$!

nohup java -jar \
  worthit-gateway/target/worthit-gateway-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local-infra \
  >"$WORTHIT_LOG_DIR/gateway.log" 2>&1 &
GATEWAY_PID=$!
```

严格按 Auth → Reminder → Tracking → Gateway 启动。每启动一个服务，先等待其
readiness 为 `UP` 再启动下一个：

```bash
curl --fail-with-body \
  http://127.0.0.1:PORT/actuator/health/readiness
```

| 服务 | 端口 |
| --- | ---: |
| Gateway | 18080 |
| Auth | 18081 |
| Tracking | 18082 |
| Reminder | 18083 |

## 验收

```bash
scripts/local-infra/nacos-config.sh services
scripts/local-infra/verify.sh
bash scripts/verify-flyway-source-parity.sh
```

`verify.sh` 必须完整通过，不能通过缺省变量或空成功跳过。它验证：

- Nacos 服务端与控制台 readiness；
- 三库 Flyway V1；
- Redis PING 及 WorthIt Sa-Token key 存在，始终隐藏 value；
- 四服务 liveness/readiness；
- Nacos 中四个健康实例和端口；
- Gateway→Auth、Gateway→Reminder、Gateway→Tracking→Reminder；
- Reminder 直连缺失/错误 Same-Token 均返回 403；
- 更新 Nacos `probe-message` 后 Tracking 刷新，并恢复原配置；
- 响应和 `WORTHIT_LOG_DIR` 中没有配置的数据库密码、Redis 密码、JWT Secret
  或 Same-Token 值。

### 受控并发

先列出实际可用指标名：

```bash
curl --fail-with-body http://127.0.0.1:18080/actuator/metrics |
  jq -r '.names[]' | sort
```

再执行：

```bash
seq 1 200 | xargs -P 20 -I{} \
  curl --silent --output /dev/null \
  --write-out '%{http_code} %{time_total}\n' \
  http://127.0.0.1:18080/__infra/reminder/ping
```

必须记录成功率、p50、p95、max，并检查 Gateway 日志是否出现 starvation、超时、
拒绝执行或线程堆积。本轮实际暴露了 `http.server.requests`，未暴露 Reactor
Netty Event Loop pending tasks 或 Redis Micrometer latency，因此不能声称这两项
已由 Micrometer 观测。Redis 侧可只读 `INFO commandstats` 的 calls、
`usec_per_call`、rejected/failed calls，禁止读取 Same-Token value。

Sa-Token Redis DAO 包含已知同步 I/O；本轮结论只能是受控并发下未观察到明显
starvation，不能写成“完全非阻塞”。

## 停止与清理

只停止本次 shell 记录的 Java PID：

```bash
kill "$GATEWAY_PID" "$TRACKING_PID" "$REMINDER_PID" "$AUTH_PID"
wait "$GATEWAY_PID" "$TRACKING_PID" "$REMINDER_PID" "$AUTH_PID" 2>/dev/null || true
```

不得使用 `pkill java`。确认端口释放后，临时日志可以移到废纸篓；不要提交。

只有当某个容器是本次任务新启动时，才在 dev-stack 中精确停止该服务，例如：

```bash
cd "$DEV_STACK_DIR"
docker compose --profile nacos stop nacos
docker compose stop redis mysql
```

若 MySQL、Redis、Nacos 在任务开始前已经运行，则上述容器停止命令不能执行。

## 诊断顺序

出现失败时按以下顺序定位，不跳层：

1. `docker compose ps`：容器是否存在且 healthy；
2. `docker compose logs --tail=200 <service>`：中间件是否完成启动；
3. `curl`、MySQL 客户端、Redis PING：协议是否真正可用；
4. `nacos-config.sh services`：服务名、分组、命名空间、健康实例和端口；
5. 各应用 `/actuator/health/readiness`；
6. Gateway 探针与 Tracking→Reminder 探针；
7. 任务专用应用日志。

`Up`、端口可连和应用 readiness 是不同证据，不能互相替代。
