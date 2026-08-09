# M3 账号注销闭环设计提案

> 状态：`PROPOSED`，数据库边界已确认，完整方案待确认。本文是跨模块实施前的设计输入，
> 不是现行权威终稿；确认前不得据此修改业务代码或宣称账号注销已经进入现行接口、架构
> 与验收基线。

## 1. 背景与已确认边界

现行终稿已经完成 M3 Tracking 完整恢复与基础数据导出，但仍把账号注销和备份恢复标记为
“另行立项”。接口终稿只保留申请、撤销和查询三个路径，尚未冻结字段、错误、内部协议、
并发、失败恢复与验收用例。账号注销同时影响 Auth、Gateway、Tracking 和 Reminder，必须
先确认设计并同步上级权威文档，再按依赖顺序实施。

2026-08-09 用户已确认：可以为账号注销新增必要的业务表。该授权允许新增正常的 Flyway
业务迁移，但不包含 `TODO-P1-003` 登记的 Flyway 兼容治理；本轮不升级或压制 Flyway、
MySQL、Spring Boot、Spring Cloud、Spring Cloud Alibaba 的版本/兼容问题。

本提案范围：

- 完成账号注销的公网申请、七天冷静期、撤销、到期执行和跨服务数据清理闭环。
- 新增正确性所需的 Auth 幂等表，以及 Tracking、Reminder 用户写入围栏表。
- 复用现有 Client 模块、Same-Token、Sa-Token、Redis Leader Lock 与错误处理，不引入
  MQ、分布式事务、工作流引擎或新中间件。
- 数据导入属于 M4，不纳入本轮。隐私说明和备份恢复检查是公开试用准备的相邻交付，
  其中“恢复后不得复活已注销数据”必须进入备份恢复验收。

## 2. 根因与必须满足的不变量

仅依靠 Auth 注销会话和延迟几分钟再删数据不能严格闭环：已经通过 Gateway 的业务请求
可能在注销后提交；Tracking 已被删除的在途 Outbox 也可能迟到调用 Reminder，重新创建
提醒。响应超时不能证明服务端事务一定停止，多删一次也没有迟到写入的严格上界。

方案必须同时证明：

1. 注销申请与撤销具有持久化 `Idempotency-Key` 语义，服务重启后仍可重放或识别冲突。
2. 冷静期内撤销与到期执行只能有一个胜者。
3. 到期执行建立围栏后，旧请求必须先完成，后续写入必须被拒绝，之后才能删除数据。
4. Tracking、Reminder 各自只清理本地拥有的数据，任一步失败可独立回滚并安全重试。
5. 结果未知、进程崩溃和部分下游成功都不能使账号重新可用或跳过未完成服务。
6. 完成后不保留身份、凭证、登录审计和业务正文；只保留支撑重试、防复活与短期审计的
   最小技术数据。

## 3. 候选方案与取舍

### 3.1 采用：数据库任务状态 + 服务本地持久写围栏 + 幂等全量重试

Auth 以 `PENDING / EXECUTING / COMPLETED / REVOKED` 保存注销状态，并使用新的通用幂等
表冻结申请和撤销的请求摘要与响应。到期后 Auth 把用户和注销记录原子推进到执行态，撤销
全部会话，再调用 Tracking、Reminder 的清理接口。

Tracking、Reminder 各新增一张用户写围栏表。所有会改变用户数据的本地事务都先创建/锁定
围栏行并确认 `ACTIVE`；注销清理锁定同一行并改为 `CANCELLING` 后再删除数据。于是：

- 已经开始的写事务先持有行锁，注销清理必须等待它提交或回滚；
- 注销清理取得行锁后，新写事务只能等待并最终看到非 `ACTIVE`，不能写回；
- Reminder reconcile 也遵守围栏，Tracking 的迟到 Outbox 无法在清理后重建提醒。

下游清理天然幂等。Auth 不保存两个服务的独立步骤状态：任一失败或结果未知时，后续轮次
重新调用两端；已成功的一端看到 `CANCELLED` 围栏后直接返回成功。低频注销场景下，这比
额外步骤表更少状态、更易恢复，且不牺牲正确性。

### 3.2 未采用：只注销 Token 后按固定时间排空

Gateway 响应超时不能中止所有已经进入服务端的事务，Outbox Relay 也不受用户 Token
约束。固定等待只能降低概率，不能证明无迟到数据。

### 3.3 未采用：为每个下游新增 Auth 步骤表

步骤表可以减少重复远端调用，但会引入步骤租约、结果未知、人工重放和状态修复。当前只有
两个天然幂等下游，整组重试成本固定且低，步骤表不降低正确性风险，反而增加恢复分支。

### 3.4 未采用：Redis 作为任务、围栏或幂等权威

Redis 继续承担 Leader Lock 和会话，但不能成为注销事实、用户写权限或请求重放的唯一
来源。缓存丢失、过期或切换不能使注销申请消失，也不能重新放开已围栏用户。

### 3.5 未采用：Auth 直连业务库或分布式事务

跨库删除破坏数据所有权；分布式事务会把低频隐私用例与所有服务可用性强耦合。每个服务
只在自己的数据库中提交围栏与删除，Auth 通过可重试编排最终收敛。

## 4. 提议冻结的公网契约

### 4.1 申请注销

`POST /api/v1/auth/cancellation`

- 必须登录；用户标识只取自会话，不接受客户端 `userId`。
- 要求 UUID `Idempotency-Key`，无请求体。
- 首次申请创建 `PENDING`，`applyAt=now`，`effectiveAt=applyAt+7d`。
- 同 Key 重放返回冻结响应；不同 Key 且已有开放申请时返回同一 `PENDING`，不延后时间。
- 最近记录为 `REVOKED` 时，不同 Key 创建新的申请；旧 Key 仍重放旧响应，不会创建新申请。
- 返回 `id`、`status`、`applyAt`、`effectiveAt`、`revokedAt`、`completedAt`、`version`；
  Long 标识以字符串输出，时间使用 ISO-8601，并由契约明确 `Asia/Shanghai`。

冷静期内账号保持正常可用，不自动登出。这样用户仍能核对或导出数据，并可显式撤销误触
申请；七天是可逆决定期，不是停用期。

### 4.2 查询注销状态

`GET /api/v1/auth/cancellation`

- 必须登录，不接收 `userId`，不要求 `Idempotency-Key`。
- 返回当前用户最新一条记录；从未申请时 `data.cancellation=null`，不发明数据库 `NONE`。
- 执行开始后全部会话失效，公网不再提供执行中或完成后的本人查询；前端只处理冷静期内的
  `PENDING / REVOKED`。

### 4.3 撤销注销

`POST /api/v1/auth/cancellation/revoke`

- 必须登录并要求 UUID `Idempotency-Key`。
- 请求体为 `{ "cancellationId": "...", "version": 1 }`，防止旧请求误伤后续新申请。
- 仅允许本人 `PENDING` 且 `auth_user.status=ACTIVE` 的目标转为 `REVOKED`；成功后
  `version+1` 并写 `revokedAt`。
- 同 Key/同请求重放冻结响应；同 Key/不同目标或版本返回 `409 IDEM_CONFLICT`。
- 目标不存在、非本人或已经 `COMPLETED` 对公网统一为 `404 RES_NOT_FOUND`；不同 Key
  请求已撤销的同一目标返回当前终态；版本变化或已进入执行态返回
  `409 VAL_STATE_CONFLICT`。

### 4.4 稳定错误

- 未登录或 Token 已撤销：`401 AUTH_UNAUTHORIZED`。
- `Idempotency-Key` 缺失、非 UUID、字段非法：`400 VAL_INVALID_ARGUMENT`。
- 目标不存在/非本人/已完成：`404 RES_NOT_FOUND`。
- 同 Key 不同请求：`409 IDEM_CONFLICT`；同 Key 正在处理：`409 IDEM_IN_PROGRESS`。
- 版本、撤销与执行竞争失败：`409 VAL_STATE_CONFLICT`。
- 下游失败只发生在异步执行，不向原公网申请请求透传；进入指标和脱敏日志并持续重试。

## 5. 状态、并发与认证围栏

### 5.1 状态机

```text
无申请 --apply--> PENDING --revoke--> REVOKED
                      |
                      | effectiveAt 到期且 claim 成功
                      v
                  EXECUTING
                      |
                      | Tracking + Reminder 清理均成功
                      v
                  COMPLETED

auth_user: ACTIVE --claim--> CANCELLATION_EXECUTING --finalize--> 删除
```

`PENDING` 和 `EXECUTING` 都属于开放注销，必须由同一生成列唯一键保证每个用户至多一条。
撤销事务锁定用户与注销行，并同时校验用户仍为 `ACTIVE`；claim 使用相同锁顺序并条件更新，
两者只能有一个提交。进入 `EXECUTING` 后不支持撤销或业务回滚，恢复动作只有继续清理。

### 5.2 登录与会话竞态

微信和密码登录都必须在签发 Token 的事务内锁定 `auth_user` 并确认 `ACTIVE`。外部微信
code 交换发生在事务外，内部注册/加载、用户行锁和 Sa-Token 签发发生在短事务内。claim
使用同一用户行锁；若登录先完成，claim 随后会按用户撤销包括新 Token 在内的全部会话；
若 claim 先完成，登录看到执行态并返回 `AUTH_FORBIDDEN`。

当前 `UserSession` 端口需增加按用户撤销全部会话的能力，不能只注销调度线程不存在的
“当前 Token”。Auth 身份清理前再次执行一次全用户会话撤销，作为防御性收口。

### 5.3 多实例调度

- Auth 使用独立 Redis Leader Lock，每 30 秒扫描到期 `PENDING` 和未完成 `EXECUTING`，
  每批最多 20 条。
- Redis 锁只减少重复工作；数据库行锁/条件更新、状态机和下游幂等才是正确性边界。
- 进程在 claim、远端调用或本地 finalize 前后崩溃，都留下可扫描的 `EXECUTING`。
- 不把远端调用放在 Auth 数据库事务中；每一步事务短小，不能持锁跨网络等待。

## 6. 数据库迁移设计

### 6.1 Auth V3

调整 `auth_account_cancellation`：

- 状态约束扩展为 `PENDING / EXECUTING / COMPLETED / REVOKED`。
- `pending_user_id` 重命名为 `open_user_id`，生成表达式覆盖 `PENDING / EXECUTING`；
  唯一索引重命名为 `uk_cancellation_open_user`。
- 时间约束要求开放状态没有完成/撤销时间，完成和撤销终态分别只有对应时间。

新增 `auth_idempotency_record`，字段与现行 Tracking 幂等协议对齐：

- `id`、`user_id`、`operation_code`、`idempotency_key`、`request_hash`；
- `response_json`、`status`、`error_code/error_message`；
- `processing_expire_at`、`expires_at`、创建/更新时间；
- 唯一键 `(user_id, operation_code, idempotency_key)`，处理/到期扫描索引。

只复用共同语义和状态机，不让 Auth 依赖 Tracking App。若实现证明确有共享的运行时中立
幂等协调语义，再把协议下沉到 Common；不复制后立即为“Common 纯洁”跨模块重构。

### 6.2 Tracking V3：`trk_user_write_fence`

字段：`user_id` 主键、`status`、`cancellation_id`、`create_time/update_time/completed_at`。
状态为 `ACTIVE / CANCELLING / CANCELLED`，检查约束保证：

- `ACTIVE` 没有 cancellation/completed；
- `CANCELLING` 有 cancellationId、没有 completed；
- `CANCELLED` 同时具有 cancellationId 和 completedAt。

普通写事务使用 `INSERT ... ON DUPLICATE KEY UPDATE user_id=user_id` 懒建行，再
`SELECT ... FOR UPDATE` 并要求 `ACTIVE`。注销事务在同一行锁下设置 `CANCELLING`、删除
本人数据，最后设置 `CANCELLED`。围栏行不随业务数据删除。

### 6.3 Reminder V2：`rem_user_write_fence`

字段、状态和约束与 Tracking 相同，但由 Reminder 自己拥有。公网处理/忽略写用例和内部
reconcile 都必须先锁定并确认 `ACTIVE`；注销清理使用同一行锁。这样迟到 Outbox 会稳定
失败，不能在清理后重建 Binding/Instance。

### 6.4 迁移与回滚

迁移只增加/调整账号注销所需业务结构，不升级 Flyway。上线顺序必须为：

1. 先部署包含围栏表但尚未开放注销入口的 Tracking、Reminder；
2. 再部署所有写路径围栏和内部清理接口；
3. 最后部署 Auth 状态/幂等/调度并开放 Gateway 公网能力。

禁止在存在 `EXECUTING` 或任一下游 `CANCELLING` 用户时回滚到不认识围栏的版本。回滚先
关闭新申请和新的 claim，仅允许兼容版本完成在途任务；已物理删除的数据不能反向重建。

## 7. 内部清理契约与数据所有权

Auth 扩展现有运行时中立 Client：

- `POST /internal/v1/tracking/users/{userId}/account-cancellation`
- `POST /internal/v1/reminders/users/{userId}/account-cancellation`

两者要求 Same-Token、`X-Caller-Service=worthit-auth`、
`X-Idempotency-Key=<cancellationId>`；请求体也包含字符串 `cancellationId`。路径 userId
必须是正 Long。响应固定 `{ "completed": true }`，不把重放时会变化的删除计数作为协议
结果。非 Auth 调用方返回 `403 AUTH_FORBIDDEN`，非法 ID 返回
`400 VAL_INVALID_ARGUMENT`；没有数据或围栏已 `CANCELLED` 仍返回成功。

若同一用户围栏已绑定另一 cancellationId，返回内部稳定契约冲突，Auth 不跳过该服务并
持续告警；不得覆盖现有围栏或把用户重新设为 `ACTIVE`。

### 7.1 Tracking 本地事务

取得写围栏并改为 `CANCELLING` 后，按 `user_id` 物理删除：

1. `trk_item_replacement`、`trk_item_disposal`；
2. `trk_outbox_event`、`trk_idempotency_record`；
3. `trk_item`、`trk_subscription`、`trk_wish`；
4. `trk_category`；
5. 把围栏改为 `CANCELLED`，写 `completedAt`。

所有可过滤 SQL 都直接包含 `user_id=:userId`。同一事务任一步失败整体回滚；删除数量只进
指标和脱敏日志，不进入响应或新审计表。

所有 Tracking 写入口都必须先取得 `ACTIVE` 围栏：Category、Item、Subscription、Wish、
Lifecycle、短时恢复、M3 完整恢复，以及会写业务/幂等/Outbox 的后续入口。Data Export、
Dashboard 和纯查询不加写锁。

### 7.2 Reminder 本地事务

取得围栏并改为 `CANCELLING` 后依次删除：

1. `rem_command_log`：按本人 Binding 子查询过滤；
2. `rem_instance`；
3. `rem_binding`；
4. 把围栏改为 `CANCELLED`，写 `completedAt`。

公网处理/忽略和内部 reconcile 都先取得 `ACTIVE` 围栏。重复清理删除零行仍成功。

### 7.3 Auth 最终本地事务

两个下游都成功后，Auth 锁定用户和注销记录，确认
`EXECUTING + CANCELLATION_EXECUTING`，再次撤销该用户全部会话，然后在本地事务中：

1. 删除 `auth_password_credential`；
2. 删除 `auth_external_identity`；
3. 删除该用户 `auth_login_audit` 和 `auth_idempotency_record`；
4. 把注销记录改为 `COMPLETED`，写 `completedAt` 和 `version+1`；
5. 删除 `auth_user`。

数据库没有跨服务外键，顺序仍是稳定所有权边界。完成记录只保留注销 ID、原用户 ID、
申请/生效/完成时间、状态和版本，不保留用户名、外部身份、IP、设备、昵称、头像或正文。

## 8. 失败恢复、审计与备份约束

- 下游连接失败、超时、`5xx` 或结果未知：保持 `EXECUTING`，下一轮重试两端。
- 参数/鉴权/契约错误：记录失败指标与脱敏错误，不能跳过服务或删除 Auth 身份。
- 一个下游成功、另一个失败：成功方保留 `CANCELLED` 围栏并自然重放，失败方回滚重试。
- 执行开始后没有业务撤销；修复方式只有恢复依赖并继续清理。代码回滚不能把用户改回
  `ACTIVE`。
- 指标至少包含 pending/executing 数、最老任务时长、claim/success/retry/failure、各
  下游耗时、围栏冲突和清理结果；日志不记录业务正文、身份 subject、IP、设备或 Secret。

推荐 `COMPLETED / REVOKED` 注销记录保留 90 天后小批量物理删除；Tracking、Reminder 的
`CANCELLED` 围栏仅保留不可反查身份的 userId、cancellationId 和完成时间，长期保留以阻止
旧请求、旧事件或错误 ID 复用复活数据。90 天属于隐私策略，必须同步隐私说明。

备份恢复验收必须证明：恢复旧快照后，会重新应用备份时间点之后的已完成注销清单，再开放
公网流量；否则旧备份会复活已经删除的身份和业务数据。注销清单如何独立、加密、最小化
保存以及恢复演练的 RPO/RTO，需在备份恢复子设计中冻结，不能把“数据库能 restore”当作
隐私闭环完成。

## 9. 验证矩阵

### 9.1 数据迁移

- 三个服务分别用 Testcontainers 从空库迁移并核对表、列、生成列、索引和 CHECK。
- Auth 构造既有 `PENDING / COMPLETED / REVOKED` 数据后升级 V3，证明数据兼容和开放记录
  唯一性；Tracking/Reminder 在无围栏历史数据下可懒建 `ACTIVE`。
- 更新数据库权威 SQL 镜像和 Flyway parity fixture；只验证新增业务迁移，不处理既有
  Flyway/MySQL 兼容警告。

### 9.2 Auth

- Controller：登录身份、UUID Header、字符串 ID、空状态、撤销字段、错误和 public
  OpenAPI 分组。
- 幂等：同 Key 重放、不同请求冲突、PROCESSING 租约回收、技术失败重试、重启后重放。
- 应用：首次/重复/撤销后再申请、旧 Key 不创建新申请、旧撤销不影响新申请、版本冲突、
  到期边界、撤销与 claim 单胜者、登录与 claim 单胜者、执行态拒绝登录。
- Redis/Sa-Token：按用户撤销全部会话、Leader Lock 多实例竞争和锁丢失后的数据库正确性。

### 9.3 Tracking 与 Reminder

- Client 契约：固定路径/POST/Header/Caller/字符串 ID，Client 不依赖 App、Entity、Mapper。
- Controller：Same-Token、Auth Caller、正用户 ID、public/internal OpenAPI 隔离。
- 每个既有写用例都有 `ACTIVE` 围栏测试；`CANCELLING / CANCELLED` 返回稳定状态冲突。
- MySQL：两用户隔离、活跃/逻辑删除/Outbox/幂等/Binding/Instance/Command Log 全覆盖、
  中途失败整体回滚、重复清理成功、不同 cancellationId 冲突。
- 并发：业务写持锁时注销等待；注销取得锁后新写等待并失败；迟到 reconcile 不重建提醒。

### 9.4 端到端和全仓门禁

- 真实四服务经 Gateway：申请、状态、撤销、再次申请、到期执行、全会话失效、登录拒绝、
  三库本人数据清空、他人数据保持、下游故障后恢复重试。
- 定向模块测试、相关 Reactor、`./mvnw clean verify`、`./mvnw package`、全部
  `scripts/tests/*.sh`；测试不得依赖开发机残留容器。
- 公网/内部 OpenAPI、Servlet/WebFlux 隔离、Client 依赖方向、Nacos 模板和错误映射门禁
  不能回退。
- 现有 MySQL 8.4/Flyway 警告只如实记录，不在本轮升级、压制或宣称解决。

## 10. 文档、实施与交付顺序

完整设计确认后，先在上级独立文档工作区建立新的 PRD/架构/接口/数据库/技术门禁/产品
验收版本并更新定稿索引，再开始业务实现。上级文档当前整体未纳入其 Git 跟踪，写入前仍
需确认该工作区的所有权和交付方式；不得把它混进后端仓库提交。

实施顺序：

1. 数据库迁移与三个围栏/幂等持久化组件；
2. Tracking/Reminder Client 契约、写围栏接入和本地清理；
3. Auth 公网申请/查询/撤销、登录竞态修复和全会话撤销；
4. Auth 到期调度、失败恢复、指标和配置；
5. 跨服务并发、真实 E2E、全仓质量门禁和文档新鲜度复核。

后端使用 feature branch、正式 PR、CI/mergeability 检查、merge commit、同步 `main` 和
最终 HEAD 核验。设计确认前只提交本提案，不合并业务实现。

## 11. 待确认决策

数据库表授权已经确认；仍建议一次确认以下默认值：

1. 冷静期七天内账号保持可用；进入 `EXECUTING` 后不可撤销。
2. 采用 Auth 通用幂等表，以及 Tracking/Reminder 本地持久写围栏，不使用等待排空方案。
3. `COMPLETED / REVOKED` 注销记录保留 90 天；已取消用户的下游技术围栏长期保留。
4. 本轮业务代码范围是账号注销；同步隐私说明，并单独建立含“注销数据不复活”的备份恢复
   子设计与可执行验收。
5. 允许修改当前未跟踪的上级权威文档，并由用户确认其后续纳入版本控制的方式。
