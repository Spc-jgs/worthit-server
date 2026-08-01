# M1/M2 发布候选黑盒门禁设计

## 1. 目标与边界

本轮在现有模块测试、Testcontainers 集成测试和
`verify-public-api.sh` 认证/分类探针之上，增加真实四服务发布候选门禁：

- 所有业务请求只经 Gateway，公网凭证只使用单一 `Authorization: Bearer`。
- 覆盖 Item、Subscription、Wish、Dashboard、Reminder 和 M2 生命周期主闭环。
- 覆盖公网可稳定构造的幂等重放/冲突、用户隔离、删除恢复和并发收敛。
- 使用两个独立本地测试账号验证资源归属，不通过数据库伪造业务结果。
- 以精确 PID 按 Gateway → Tracking → Reminder → Auth 顺序停止本轮 Java 进程，
  检查端口释放、Nacos 实例退出和服务日志。
- 显式运行前端真实 HTTP Adapter 集成测试；只允许修复 Adapter 与测试，不修改
  工作台、页面、样式、导航或其他前端能力。

本轮不修改 Flyway 版本、依赖、迁移、数据库结构或兼容策略；不建设 M3；不把本机
真实中间件检查并入普通 Maven/CI；不替代产品人员的视觉和人工验收。

## 2. 验证分层

| 层级 | 责任 | 本轮动作 |
| --- | --- | --- |
| 现有 UT/IT | 事务回滚、唯一键、租约、同版本并发、Reminder 乱序等不可稳定由公网制造的 P0 | 运行模块与全仓 Maven 门禁，不重复发明数据库注入接口 |
| 轻量公网探针 | Bearer、密码登录、me、分类 | 保留 `verify-public-api.sh`，作为快速诊断 |
| 发布候选公网链路 | M1/M2 真实业务闭环、安全、幂等、恢复、Reminder 最终一致 | 新增 `verify-release-candidate.sh`；到期 Reminder 使用显式本地测试夹具建立前置条件 |
| 环境与退出 | Nacos/Redis/MySQL、服务发现、动态配置、有序停止 | 复用 `verify.sh`，新增精确 PID 停机门禁 |
| 前端 Adapter | 客户端请求/响应映射与字符串 ID | 显式启用现有 HTTP 集成测试，不做 UI 验收 |

公网黑盒不宣称替代 `TECH-LIFE-005/006/009`、`TECH-REM-*`、`TECH-OUT-*` 等
需要事务故障注入或数据库级观测的既有 Testcontainers 门禁。

当前 dev-stack 按 Nacos 3 推荐边界关闭客户端鉴权、保留 Admin/Console 鉴权。环境验证
只需要客户端配置与服务发现能力，因此 `verify.sh` 在没有显式 Admin 凭据时使用 Nacos
v1 Client API 查询实例并做受控配置刷新；显式提供 Admin 凭据时继续使用 v3 Admin API。
`nacos-config.sh sync` 的 namespace 创建与全量发布职责不变，不从容器或 `.env` 偷取
管理员密码。

## 3. 发布候选矩阵

### 3.1 认证与安全

1. 伪造内部 `worthit-token`、缺失 Bearer、畸形 Bearer 均为
   `401 AUTH_UNAUTHORIZED`。
2. 两个本地账号分别登录，只保存在权限为 0600 的临时 Header 文件中。
3. 用户 B 查询、更新、删除、恢复或处置用户 A 的资源统一
   `404 RES_NOT_FOUND`，不泄露存在性。
4. 响应失败只打印 HTTP、稳定业务码与 traceId，不输出 Token 或密码。

### 3.2 Item 与 Dashboard

1. 创建 G-PLAN-01 物品，校验 `¥2.74/天`、`residualUnset=true`、字符串 ID；
   同 key 同 body 重放返回同一 ID，同 key 不同 body 返回 `409 IDEM_CONFLICT`。
2. 详情、名称搜索和分类筛选均命中；Dashboard 当前持有合计按精确规则变化。
3. 删除后使用响应中的 version/restoreToken 窗口内恢复；重复恢复幂等；恢复后不自动
   复活历史 Reminder；再次删除完成清理。
4. 创建三件独立物品分别执行退货、卖出、报废，校验单向终态、处置快照、字符串金额、
   Dashboard 退出统计和重复处置 `VAL_STATE_CONFLICT`。
5. 创建 old/new 两件物品建立替换关系；同 key 重放返回同 relationId；复盘返回
   显式判别联合且 ID 均为字符串。

### 3.3 Subscription

1. 创建 20 USD 月付并提供 140 CNY 参考，校验约人民币月成本进入 Dashboard。
2. pause 使合计减少，resume 使合计恢复，end 再次退出；状态命令均携带 UUID
   `Idempotency-Key` 与 version。
3. pause 状态命令抽样同 key 重放；创建、更新、删除及其他状态命令的摘要冲突继续由
   既有模块幂等门禁覆盖。
4. 删除、窗口内恢复、重复恢复和越权恢复按冻结语义验证；恢复后不自动复活旧提醒。

### 3.4 Wish 与购买并发

1. 创建考虑中 Wish，校验计划日均及 Dashboard 数量/金额；abandon → reconsider 保留
   最近放弃事实并恢复考虑中。
2. 同一 Wish 使用两个不同 UUID key 并发 purchase；两个成功响应必须返回同一 itemId，
   Wish 为 PURCHASED，Dashboard 当前想买摘要减少，数据库唯一性由既有 IT 继续兜底。
3. 独立 Wish 执行删除、恢复、重复恢复和越权恢复。

### 3.5 Reminder 最终一致

1. 通过公网创建明日到期的 WATCH Reminder，先轮询 Reminder 库确认 Outbox 已收敛出
   唯一 PENDING；随后仅在显式本地发布候选环境中，把这条精确实例的 `remind_at`
   调整为当前时刻之前，建立产品用例“已有到期 PENDING”的前置夹具。生产代码、普通
   Maven 门禁和公网 API 不提供改时钟/改提醒时间入口。
2. 轮询公网 pending-count/list，避免把 Outbox 最终一致误判为同步。
3. ignore 后 pending-count 下降，DONE 列表包含 IGNORED；重复 ignore 返回状态冲突或
   not-found，不能回到 PENDING。
4. 删除/恢复对象后轮询确认旧 Reminder 不复活；CANCELED 不出现在 DONE。

## 4. 数据隔离与清理

- 脚本要求调用方提供主、次两个本地账号，不创建账号、不清库。唯一数据库写入是按
  本轮 businessId 精确定位的 Reminder 到期前置夹具；脚本要求独立测试账号和独立
  Reminder 应用数据库凭据，且只把一条新建 PENDING 的 `remind_at` 调整到过去。
- 每次运行使用唯一名称和 UUID 幂等键；跟踪本轮创建的资源 ID。
- 正常路径通过公网 API 精确逻辑删除本轮资源；失败时 trap 尽力清理仍可删除的资源。
- Reminder 历史按产品契约保留，测试账号的物理清理由环境编排在服务停止后按精确
  userId 执行，不属于业务脚本。
- MySQL、Redis、Nacos 容器由用户预先启动；脚本不启动、停止、重建或读取
  `dev-stack/.env`。

## 5. 有序停机

停机脚本只接受 `WORTHIT_GATEWAY_PID`、`WORTHIT_TRACKING_PID`、
`WORTHIT_REMINDER_PID`、`WORTHIT_AUTH_PID`。每个 PID 必须存活且命令行包含对应
应用 JAR，防止误杀其他 Java 进程。逐个发送 SIGTERM 并在超时内等待自然退出；不发送
SIGKILL，不使用 `pkill`。

退出后验证四个端口均不可连接，并通过 Nacos Client API 检查四个实例消失。当前锁定的
Nacos Client 3.0.3 在关闭期会稳定产生 `NotifyCenter` 的 `InterruptedException` 与
`NacosGracefulShutdownDelegate` 重复关闭空指针；脚本只对这组已确认第三方噪声做显式
WARN 分类。macOS 上 Gateway 若缺少 Netty 原生 DNS provider，会明确记录“退回系统 DNS”
WARN；该平台回退也单独计数。任何其他 ERROR 仍阻塞。只有服务进程按期退出、端口释放、
实例注销且无其他 ERROR 才能判为有序停机成功。依赖升级属于版本基线变更，不在本轮
静默处理。

## 6. 交付与回滚

本轮只新增/修改发布候选脚本、脚本契约测试、运行手册和安全退出工具。回滚可删除
新增脚本并恢复运行手册，不影响数据库事实或接口契约。后端走 feature branch → PR →
CI/mergeability → merge commit → main 同步与 push。上级权威文档仅在发现现行基线缺少
新的稳定门禁事实时增量同步；若无业务语义变化，不人为升版。
