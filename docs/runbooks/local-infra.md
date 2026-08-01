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
| `WORTHIT_AUTH_LOCAL_ACCOUNT_ENABLED` | 设为 `true` 时幂等初始化本地账号 |
| `WORTHIT_AUTH_LOCAL_USERNAME`、`WORTHIT_AUTH_LOCAL_PASSWORD` | 本地 App/H5 联调账号，密码不得写入命令历史 |
| `WORTHIT_AUTH_LOCAL_NICKNAME` | 可选的本地账号昵称 |
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

数据库表不由管理员脚本创建。Auth 启动时由 Flyway 执行 V1、V2，Tracking 和
Reminder 分别执行 V1。

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
./mvnw -DskipTests package
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
scripts/local-infra/verify-public-api.sh
scripts/local-infra/verify-release-candidate.sh
bash scripts/verify-flyway-source-parity.sh
```

`verify.sh` 必须完整通过，不能通过缺省变量或空成功跳过。它验证：

- Nacos 服务端与控制台 readiness；
- Auth Flyway V1/V2、Tracking 和 Reminder Flyway V1；
- Redis PING 及 WorthIt Sa-Token key 存在，始终隐藏 value；
- 四服务 liveness/readiness；
- Nacos 中四个健康实例和端口；
- Gateway→Auth、Gateway→Reminder、Gateway→Tracking→Reminder；
- Reminder 直连缺失/错误 Same-Token 均返回 403；
- 更新 Nacos `probe-message` 后 Tracking 刷新，并恢复原配置；
- 响应和 `WORTHIT_LOG_DIR` 中没有配置的数据库密码、Redis 密码、JWT Secret
  或 Same-Token 值。

`verify-public-api.sh` 是显式真实业务链路门禁，要求四服务已经就绪，并要求当前
shell 提供 `WORTHIT_AUTH_LOCAL_USERNAME`、`WORTHIT_AUTH_LOCAL_PASSWORD`。它不会
启动或停止容器，不会初始化或清空数据库；脚本只经 Gateway 使用
`Authorization: Bearer`，验证伪造内部 Token 被拒绝、密码登录、当前用户、分类创建、
重命名、查询和删除。脚本生成唯一分类名，并在失败退出时尽力通过公网接口清理本次
创建且未被引用的分类；Token 和密码不会写入输出。

`verify-release-candidate.sh` 是发布候选公网链路门禁，除主账号变量外还要求
`WORTHIT_AUTH_SECONDARY_USERNAME`、`WORTHIT_AUTH_SECONDARY_PASSWORD` 以及当前
Reminder 应用数据库账号。脚本覆盖 Item/Subscription/Wish、Dashboard、Reminder、
M2 处置/替换/复盘、幂等冲突与重放、两个用户的 404 隔离、三对象窗口内恢复和 Wish
并发购买收敛。产品用例“已有到期 PENDING”无法在一天任意时刻只靠业务日期稳定构造，
因此脚本先经公网创建明日 Reminder，等待 Outbox 收敛，再把本轮唯一实例的
`remind_at` 精确调整到当前时刻之前；该数据库写入只允许独立本地测试账号，不进入普通
Maven/CI，也不提供生产或公网改时钟入口。脚本退出时通过公网精确逻辑删除本轮业务对象，
不清库、不操作容器。

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

只停止本次 shell 记录的 Java PID。推荐使用有命令行归属校验的有序停机门禁：

```bash
export WORTHIT_GATEWAY_PID="$GATEWAY_PID"
export WORTHIT_TRACKING_PID="$TRACKING_PID"
export WORTHIT_REMINDER_PID="$REMINDER_PID"
export WORTHIT_AUTH_PID="$AUTH_PID"
scripts/local-infra/stop-apps-ordered.sh
```

脚本按 Gateway → Tracking → Reminder → Auth 逐个 SIGTERM，验证四端口释放、四个 Nacos
实例注销并扫描本轮四份日志。Nacos Client 3.0.3 已知关闭期会记录 `NotifyCenter`
`InterruptedException` 和 `NacosGracefulShutdownDelegate` 重复关闭空指针；脚本对此明确
输出 WARN。macOS 缺少 Netty 原生 DNS provider 时的系统 DNS fallback 也单独输出 WARN；
任何其他 ERROR 均阻塞。这些 WARN 不等于进程或注册失败，依赖升级需另行评审。

也可以手工执行同一顺序：

```bash
kill "$GATEWAY_PID"
wait "$GATEWAY_PID" 2>/dev/null || true
kill "$TRACKING_PID"
wait "$TRACKING_PID" 2>/dev/null || true
kill "$REMINDER_PID"
wait "$REMINDER_PID" 2>/dev/null || true
kill "$AUTH_PID"
wait "$AUTH_PID" 2>/dev/null || true
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
