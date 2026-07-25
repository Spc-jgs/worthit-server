# WorthIt Phase 0 基础设施运行时接入设计

日期：2026-07-25

## 1. 目标

在 Phase 0 Common 能力已经实现的基础上，接入并验证四个运行模块所需的
Nacos、MySQL、Redis、Flyway、Sa-Token 与 Actuator，使项目从“模块和组件可
编译”推进到“本地基础设施链路可运行、可重复验证”。

本轮完成条件是：

- Gateway、Auth、Tracking、Reminder 使用冻结的服务名启动；
- 四个服务使用 Nacos 3.0.3 完成配置读取和服务注册；
- Tracking 的 `RestClient + LoadBalancer + HTTP Interface` 能通过 Nacos
  发现 Reminder；
- Auth、Tracking、Reminder 各自只连接自己的逻辑数据库，Flyway 能在
  MySQL 8.4 空库执行各自的 M1 `V1`；
- Sa-Token 1.45.0 使用 Redis 保存会话及 Same-Token 数据，JWT 只作为 Token
  风格，不绕开 Redis 会话治理；
- Gateway 清洗外部伪造的内部头，三个 MVC App 拒绝缺失或错误 Same-Token
  的直连请求；
- 四个服务提供 Actuator liveness/readiness，验证报告能区分进程启动、
  容器健康、配置加载、注册发现和协议可用；
- 常规 Maven 测试不依赖开发机残留中间件；真实本地联调使用独立
  `dev-stack` 仓库，并保留可复现的命令和证据。

本轮不实现微信登录、Dashboard、Outbox Relay、Reminder 领域处理或其他 M1
业务能力。因此 `TECH-SEC-001`、`TECH-SEC-002` 和包含真实 Dashboard
身份语义的 `TECH-SEC-006` 只完成底层运行条件，不宣称门禁已通过；它们随
第一条业务纵切补齐。

## 2. 权威基线与已核实依赖

本设计遵循 `../docs/README.md` 当前登记的架构、接口、数据库迁移和技术门禁
终稿。版本保持：

| 能力 | 版本/模块 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Nacos Client/Server | 3.0.3 |
| MySQL | 8.4 |
| Sa-Token | 1.45.0 |
| Flyway | 由 Boot 3.5.16 BOM 管理，当前解析为 11.7.2 |
| Testcontainers | 由 Boot 3.5.16 BOM 管理，当前解析为 1.21.4 |

依赖接入使用：

- Nacos：`spring-cloud-starter-alibaba-nacos-config` 与
  `spring-cloud-starter-alibaba-nacos-discovery`；
- Flyway：`flyway-core` 与 MySQL 专用的 `flyway-mysql`；
- Redis：`spring-boot-starter-data-redis`；
- Sa-Token Redis：`cn.dev33:sa-token-redis-template:1.45.0`；
- Sa-Token JWT：`cn.dev33:sa-token-jwt:1.45.0`；
- 健康探针：`spring-boot-starter-actuator`。

`cn.dev33:sa-token-redis:1.45.0` 不作为本轮依赖：该坐标未发布到当前可用的
Maven 仓库。Spring Boot 3.5.16 的 Redis 配置使用
`spring.data.redis.*`，不使用旧的 `spring.redis.*`。

## 3. 方案选择

### 3.1 采用：可重复测试 + 显式本地联调的双轨方案

- MySQL Flyway 空库门禁使用 Testcontainers MySQL 8.4，保证开发机和 CI
  都从隔离空库开始；
- Nacos、Redis 与四服务联合运行使用显式 `local-infra` Spring Profile 和
  `/Users/shaopc/Documents/Script/dev-stack`；
- 普通 `mvn test` 不连接外部 Nacos、Redis 或 MySQL；
- 本地联合验证脚本只消费环境变量，不读取或复制 `dev-stack/.env`。

该方案同时保证可重复门禁和真实版本联调，不把开发机残留状态冒充测试结果。

### 3.2 不采用：所有测试直接复用 dev-stack

该方式启动快，但测试结果会受已有数据、容器版本和服务状态影响，无法证明
Flyway 的空库迁移，也不适合作为 CI 阻塞门禁。

### 3.3 不采用：所有中间件都由 Testcontainers 启动

这适合隔离测试，但不足以覆盖团队实际使用的 Compose、Nacos 初始化和本地
操作文档。本轮保留 Testcontainers 作为可重复门禁，同时执行一次 dev-stack
真实联合验收。

## 4. 服务、端口和数据所有权

冻结本地默认值如下，均允许通过环境变量覆盖：

| 模块 | `spring.application.name` | 默认端口 | 数据库 |
| --- | --- | ---: | --- |
| Gateway | `worthit-gateway` | 18080 | 无 |
| Auth | `worthit-auth` | 18081 | `worthit_auth` |
| Tracking | `worthit-tracking` | 18082 | `worthit_tracking` |
| Reminder | `worthit-reminder` | 18083 | `worthit_reminder` |

约束：

- Gateway 不引入 JDBC、Flyway、MyBatis、Servlet、同步文件访问或阻塞式远程
  HTTP Client；Sa-Token 1.45.0 Redis DAO 的实际同步 I/O 行为按权威架构要求
  纳入 Phase 0 阻塞检测和并发验证；
- 三个业务 App 不跨库查询，也不共享 Flyway history 表；
- 每个 App 使用自己的数据库账号，账号只拥有对应逻辑库权限；
- 数据库名、用户名可以进入本地模板；密码不得提交；
- 当前不新增 `common-redis`。只有出现至少两个消费者共享的、与业务无关的
  Redis 技术实现后，才另行评审是否建模块。

## 5. 配置分层

### 5.1 仓库内安全默认值

各服务的 `application.yml` 只保存：

- 服务名、默认端口；
- Actuator 探针开关和最小暴露范围；
- springdoc 现有环境开关；
- 不包含密钥的连接参数变量名；
- Sa-Token 的非敏感行为配置；
- Nacos Data ID、group、namespace 的变量名和本地默认命名。

数据库密码、Redis 密码、Nacos 凭据、JWT Secret 不提供弱默认值；启用
`local-infra` 时缺失即启动失败。

### 5.2 Nacos 配置

`local-infra` Profile 使用 `spring.config.import` 导入两个配置：

1. `worthit-common.yaml`：非敏感公共超时、日志、探针和安全行为；
2. `${spring.application.name}.yaml`：服务自有路由、连接池和任务参数。

本地默认：

- namespace：`worthit-local`；
- group：`WORTHIT_LOCAL`；
- server address：`127.0.0.1:8848`；
- 配置格式：YAML；
- 公共与服务配置均开启刷新，但数据库连接凭据和 JWT Secret 不进入 Nacos。

仓库在 `deploy/nacos/local/` 保存与 Data ID 一一对应的非敏感模板及导入说明。
模板是可审阅基线，Nacos 是运行实例；联合验收前使用幂等脚本或明确命令同步，
禁止依赖控制台里不可追踪的手工配置。

### 5.3 Profile 隔离

- 默认 Profile：允许编译和纯单元测试，不自动连接外部中间件；
- `test`：只服务于测试上下文，使用测试提供的动态属性；
- `local-infra`：连接 dev-stack，并对 Nacos 配置缺失采取 fail-fast；
- 后续生产 Profile：只保留变量契约，不复用 `worthit-local` namespace/group。

`local`、`dev`、`test` 现有 OpenAPI 开关与 `local-infra` 的基础设施开关分开，
避免仅为了查看文档就意外连接外部服务。

## 6. Nacos 注册、发现与配置验证

四服务共同接入 Nacos Config 和 Discovery，服务名使用第 4 节冻结值。

实现约束：

- 不在代码里写死 Nacos 用户名和密码；
- namespace 和 group 必须显式配置，禁止落入 public namespace；
- Gateway 路由目标和 Tracking→Reminder 的 base URL 使用
  `lb://worthit-*` 或 `http://worthit-reminder` 虚拟服务名；
- 不写静态 localhost 下游地址作为成功路径；
- 配置刷新只验证一个无敏感、无副作用的测试属性，不用连接池或密钥轮换证明
  动态刷新。

联合验收顺序：

1. 确认 dev-stack 实际 Nacos 镜像为 3.0.3 且协议探测就绪；
2. 导入本地非敏感配置；
3. 启动四个 Java 服务；
4. 从 Nacos 查询四个注册实例和健康状态；
5. 修改专用测试属性并证明目标服务收到刷新；
6. 通过 Gateway 命中下游；
7. 通过 Tracking 的 LoadBalancer Client 命中 Reminder 测试端点。

用于发现链路的测试端点只在 `local-infra` 或测试配置中存在，不作为公网业务
接口发布。

## 7. MySQL 与 Flyway

### 7.1 执行源

只复制 M1 的三个 `V1`：

- Auth：`worthit-auth-app/src/main/resources/db/migration/V1__init_auth.sql`；
- Tracking：
  `worthit-tracking-app/src/main/resources/db/migration/V1__init_tracking.sql`；
- Reminder：
  `worthit-reminder-app/src/main/resources/db/migration/V1__init_reminder.sql`。

`tracking_m2/V2__add_item_lifecycle.sql` 不进入本轮。脚手架落地后，App
resources 是唯一执行源；任何 DDL 变化必须新增版本，禁止重写已执行的 `V1`。

新增 `scripts/verify-flyway-source-parity.sh`，逐文件比较执行源与
`../docs/数据库文档/flyway/` 登记脚本。脚本在找不到权威 docs 时失败，不跳过
也不使用缓存摘要；它作为仓库与文档同工作区的显式质量门禁运行。

### 7.2 空库测试

三个 App 分别建立 Flyway 集成测试：

- 使用 `mysql:8.4` Testcontainer；
- 每个测试从新的逻辑库和独立 Flyway history 开始；
- 仅执行该 App classpath 下的 migration；
- 断言 Flyway validate/migrate 成功和当前版本为 `1`；
- Tracking 额外断言 `source_wish_id` 列及其唯一约束存在；
- 测试结束由 Testcontainers 回收，不操作 dev-stack 数据卷。

MySQL 专用 Flyway 支持通过 `flyway-mysql` 显式提供，避免只添加
`flyway-core` 后在运行时失败。

### 7.3 本地数据库初始化

`deploy/mysql/local/` 提供：

- 三库三账号的权限模板；
- 仅接受环境变量的幂等初始化脚本；
- 运行、检查和回滚说明。

脚本只创建缺失的逻辑库、账号和授权，不删除库表，不执行 `DROP`，也不修改
dev-stack Compose。建库后由三个 App 自己执行 Flyway，不由共享 MySQL
初始化目录代替服务迁移。

## 8. Redis、Sa-Token 与内部信任

### 8.1 Redis 接入

四个服务可共享同一 Redis 实例，但使用清晰的 key prefix；连接属性使用
`spring.data.redis.*`。密码只由环境变量注入。

Sa-Token 依赖组合：

- Gateway：Reactor Boot 3 Starter；
- 三个 MVC App：Spring Boot 3 Starter；
- 四个服务共享 Redis `SaTokenDao`：`sa-token-redis-template`；
- JWT Simple：`sa-token-jwt` 并显式注册对应 `StpLogic`。

JWT 是登录 Token 风格，Redis 仍保存登录会话、Token-Session、账号状态和
Same-Token。不得切换为无状态 JWT 以规避 Redis 门禁。

Sa-Token 1.45.0 的 `SaSameTemplate` 通过同步 `SaTokenDao#get/set` 读写
Same-Token，`sa-token-redis-template` 的实现使用同步
`StringRedisTemplate`；`SaReactorFilter` 的认证回调也在当前 Reactor 调用线程
同步执行。这是本轮已核实的上游实现事实。Gateway 仍按权威架构使用官方
Reactor Starter 和 Redis DAO，不自研第二套 Same-Token 协议，但必须记录
Netty Event Loop、阻塞检测和受控并发结果。若 Redis I/O 已形成明显瓶颈，
按权威架构另行评审受控线程隔离或 Gateway MVC，不能在本轮静默改技术栈。

### 8.2 Same-Token

沿用 Common 已有的 `SameTokenProvider`、`SameTokenVerifier` 和
`SaTokenSameTokenService`：

- Gateway 无条件删除外部 Same-Token、`X-Caller-Service`、
  `X-User-Id`、`X-Session-Id` 和 `X-Trace-Id`；
- Gateway 生成可信 TraceId，并写入服务端取得的 Same-Token；
- 三个 MVC App 在进入 Application 前校验 Same-Token；
- Tracking 的内部 Client 注入当前 Same-Token、调用方服务名和 TraceId；
- 无/错 Same-Token 的直连请求返回统一 401 或 403 信封，不进入测试
  Application 处理器。

本轮验证 Same-Token 的 Redis 持久化、跨服务读取和拒绝/放行路径，并按权威
架构落实 Auth 唯一刷新责任：

- 只有 Auth 注册刷新调度；
- Auth 多实例使用 Redis `SET NX` + TTL 选出单一执行者；
- 释放锁必须比较 owner，不能直接删除其他实例持有的锁；
- 仅在剩余 TTL 低于阈值时调用 Sa-Token `refreshToken()`；
- 其他三个服务只获取和校验；
- 指标记录刷新成功、跳过、失败和剩余 TTL，不记录 Token 值。

Sa-Token 已提供 current/past 双值校验，不另造第二套灰度协议。调度间隔、刷新
阈值和锁 TTL 是 Nacos 非敏感参数；Redis 密码和 Token 值不进入 Nacos。

### 8.3 本轮安全门禁边界

本轮可以完整验证：

- `TECH-SEC-004`：直连业务端口无/错 Same-Token 被拒绝；
- `TECH-SEC-006` 的可信头清洗部分；
- Redis 中会话和 Same-Token 的真实读写；
- Gateway/WebFlux 与 App/Servlet 依赖隔离不回退。

本轮不能完整验证：

- `TECH-SEC-001` 的真实 Dashboard Token TTL；
- `TECH-SEC-002` 的真实 logout→Dashboard；
- `TECH-SEC-006` 中 Token 对应业务用户身份。

后面三项需要 Auth 登录和 Dashboard 第一条业务纵切，不使用测试假接口冒充
最终 API 门禁。

## 9. Actuator 与可观测性

四服务引入 Actuator：

- 暴露 `health`、`info` 和 Prometheus 是否启用由 Nacos 非敏感配置控制；
- 始终启用 liveness/readiness health group；
- 默认不暴露 `env`、`beans`、`configprops` 等可能泄露配置的端点；
- Gateway 下游不可用时返回统一技术错误，不暴露 Netty/Java 堆栈；
- 日志包含 traceId、service、path、costMs、resultCode，不打印密码、JWT、
  Same-Token 或完整 Authorization。
- Gateway 联合验收记录 Reactor Netty Event Loop 指标、请求延迟和阻塞检测
  结果，明确区分 Sa-Token Redis 同步调用与项目新增的长耗时阻塞操作。

readiness 的含义是服务当前可承接请求。Nacos、数据库或 Redis 是否纳入
readiness 由服务实际职责决定，并通过故障测试确认，不能只凭进程存活判定。

## 10. 测试与验证分层

### 10.1 常规门禁

每次提交前执行：

```bash
mvn test
mvn verify
mvn dependency:tree
./scripts/verify-flyway-source-parity.sh
git diff --check
```

实际实施计划会把大范围命令拆成模块级红绿重构步骤，最终再运行全量门禁。

### 10.2 隔离集成测试

- Testcontainers MySQL 8.4：三个 App 的 `V1` 空库迁移；
- Testcontainers Redis 7.4：SaTokenDao、JWT Simple、Same-Token 和 Auth
  单实例刷新锁；
- Spring 测试上下文：Profile 隔离、Nacos 导入声明、Sa-Token Redis Bean；
- Gateway WebTestClient：可信头清洗与重建；
- MVC MockMvc/真实端口：Same-Token 拒绝/放行；
- 依赖树和 ArchUnit：Servlet/WebFlux、Client/App、Common/业务隔离。

Nacos 3.0.3 的 Compose、配置中心和注册发现使用 dev-stack 显式联合验收；
普通单元测试不启动 Nacos，也不依赖开发机残留注册数据。

### 10.3 dev-stack 联合验收

启动前记录 `docker compose ps`，只启动 MySQL、Redis、Nacos。验证至少包含：

- `docker compose config` 成功；
- 三容器 health/状态和关键启动日志；
- MySQL 协议连接、三库存在、Flyway history 和 V1；
- Redis `PING`，以及 Sa-Token/Same-Token 的受控 key 证据；
- Nacos 3.0.3 版本、健康接口、四服务注册和配置刷新；
- 四服务 liveness/readiness；
- Gateway→下游发现；
- Tracking→Reminder LoadBalancer 调用；
- 缺失/错误 Same-Token 的直连拒绝；
- Gateway Sa-Token Redis 路径的受控并发、Netty Event Loop 指标与阻塞检测；
- 停止本次启动容器的精确命令和最终保留状态。

证据中只展示变量名、容器名、状态、版本、Data ID、服务名和脱敏 key 前缀，
不输出密码、JWT、Same-Token 或 Authorization 值。

## 11. 实施切片

设计确认后，编码计划按以下依赖顺序拆分为可独立提交的切片：

1. 根 POM 依赖管理、Profile 约束和依赖树门禁；
2. AGENTS/dev-stack 协作说明及本地运行文档；
3. 三 App Flyway 执行源、哈希门禁和 MySQL 8.4 空库测试；
4. 四服务配置骨架、Actuator 和安全的环境变量契约；
5. Nacos Config/Discovery 与非敏感模板；
6. Redis、Sa-Token JWT Simple 和 Same-Token 真实存储；
7. Gateway/三 App 安全运行接入；
8. Gateway 路由与 Tracking→Reminder 发现链路；
9. dev-stack 联合验收、证据归档和全量回归。

每个切片先写失败测试或可失败的验证，再实现、运行局部门禁并提交。提交格式
遵守 `<type>(<scope>): <中文描述>`。未经单独授权不推送 WorthIt 仓库。

## 12. 完成判定与剩余工作

本轮结束时必须分别报告：

- **已实现**：代码、配置、脚本和迁移执行源；
- **已自动验证**：单元测试、Testcontainers、ArchUnit、依赖树和哈希门禁；
- **已真实联调**：dev-stack 容器、Nacos 注册/配置、Redis、MySQL 与四服务
  协议链路；
- **未验证**：受业务接口或外部服务约束而未执行的门禁；
- **后续事项**：第一条 Auth→Gateway→Dashboard 业务纵切，以及由它补齐的
  `TECH-SEC-001/002/006`。

只有源代码、测试、真实运行证据和 Git 状态一致时，才声明本轮完成。容器
`Up`、单模块编译或测试假实现均不能替代上述完成条件。
