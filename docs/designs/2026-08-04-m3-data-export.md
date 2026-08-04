# M3 基础数据导出闭环设计

## 1. 目标与边界

本轮交付 M3 基础数据导出闭环：当前登录用户经 Auth 发起一次同步导出，Auth 只读取
自己的用户库，并通过正式内部 Client 向 Tracking、Reminder 请求各自拥有的数据分片，
最后返回一个完整 ZIP。任一分片失败时都返回统一 JSON 错误，不发送半个归档。

本轮明确不做：

- 不新增或修改 Flyway、表、索引、数据和中间件；不引入导出任务表、对象存储、MQ 或
  分布式事务。
- 不实现账号注销、备份恢复或导入；不把导出扩大为异步任务中心。
- 不修改前端页面、样式、导航、交互或工作台；前端只允许补充二进制 HTTP
  Service/Adapter 与对应测试。
- 不跨库查询：Auth 禁止连接 Tracking/Reminder 数据库，Tracking/Reminder 也不读取
  其他服务的数据。

## 2. 候选方案与取舍

### 2.1 采用：同步、全有或全无的内存 ZIP

基础导出属于低频、用户数据量受控的公开试用前能力。各拥有方在自己的只读事务内生成
结构化分片，Auth 顺序获取 Tracking、Reminder 分片，完成大小校验后一次性组装 ZIP。
该方案没有任务状态、文件生命周期、过期清理和下载凭证等额外闭环，在当前个人/小团队
规模下总成本最低。

Auth 在真正写响应前已持有完整归档，因此下游超时、容量超限或序列化失败不会产生
`200` 加损坏 ZIP。Auth 默认每实例最多同时执行 2 个导出，同一用户同时最多 1 个；
各服务最多导出 10,000 条业务记录、单分片 JSON 最多 8 MiB，最终 ZIP 最多 20 MiB。
全部上限配置化，但调大前必须按部署内存重新验证。

### 2.2 未采用：边查边向公网流式写 ZIP

直接向响应流写入虽然降低 Auth 峰值内存，但 HTTP 状态和部分字节一旦提交，下游后续
失败只能得到截断归档，无法继续返回稳定错误信封。当前需求更重视结果完整性，且硬上限
已经把内存风险限定在可验证范围内。

### 2.3 未采用：异步任务与对象存储

异步方案适合大文件、长耗时和可恢复下载，但会立即要求任务表、跨服务步骤状态、文件
存储、过期清理、下载授权与重试协议；这既触碰本轮“无 Flyway”边界，也没有当前数据
规模证据。达到同步上限的用户收到明确的 `413`，后续有真实规模证据时再独立立项。

### 2.4 未采用：Auth 直连业务库或跨服务统一事务

直连会破坏数据所有权，分布式事务会把低频只读能力变成高耦合协调。本方案只保证每个
服务内部的 REPEATABLE READ 只读快照，不声称三个服务共享同一时点；Manifest 记录各
分片 `capturedAt`，使这一事实显式可见。

## 3. 公网与内部契约

### 3.1 公网入口

`GET /api/v1/auth/data-export`

- 只接受当前有效登录会话，不接收 `userId`，不需要 `Idempotency-Key`。
- 成功：`200 application/zip`，文件名
  `worthit-data-export-<yyyyMMdd'T'HHmmss'Z'>.zip`。
- 响应头：`Content-Disposition: attachment`、`Cache-Control: no-store`、准确
  `Content-Length`、`X-Trace-Id`。
- 成功响应是二进制，不使用 `ApiResponse`；失败发生在响应提交前，继续使用统一 JSON
  错误信封。

### 3.2 内部读取

- `GET /internal/v1/tracking/users/{userId}/data-export`
- `GET /internal/v1/reminders/users/{userId}/data-export`

内部接口只由 Auth 使用，必须通过 Same-Token，并要求审计头
`X-Caller-Service=worthit-auth`；路径 `userId` 必须是正 Long。新增
`worthit-tracking-client`，并在既有 `worthit-reminder-client` 增加导出 HTTP Interface。
Client 只承载协议模型，不依赖 App、Mapper、Entity 或 Repository。Auth 使用现有
`HttpServiceClientFactory + RestClient + LoadBalancer` 创建代理，连接超时 2 秒、读取
超时 15 秒，调用顺序固定为 Tracking 后 Reminder，不为低频导出引入额外线程池。

### 3.3 稳定错误

- 未登录：`401 AUTH_UNAUTHORIZED`。
- 非 Auth 内部调用方：`403 AUTH_FORBIDDEN`。
- 用户不存在或不可见：`404 RES_NOT_FOUND`。
- 任一服务记录数、分片字节或最终归档超过上限：
  `413 DATA_EXPORT_LIMIT_EXCEEDED`。
- 同一用户已有导出或实例并发槽已满：`429 DATA_EXPORT_BUSY`。
- 下游超时、不可用或非容量类错误：`502/503 SYS_UPSTREAM`，不得把远端内部信息透传
  给公网。
- 归档序列化等未预期失败：`500 SYS_ERROR`，不得生成部分下载。

Auth 只把远端同名 `DATA_EXPORT_LIMIT_EXCEEDED` 映射为自己的 `413`；其他远端错误继续
交给统一 `RemoteServiceExceptionHandler` 屏蔽。

## 4. 归档格式

ZIP 条目及顺序固定为：

1. `manifest.json`
2. `auth/account.json`
3. `tracking/data.json`
4. `reminder/data.json`

所有 JSON 使用 UTF-8，`schemaVersion=1`。Snowflake 标识一律为字符串，金额和年限等
Decimal 一律为无科学计数法字符串，日期为 ISO-8601 日期；数据库本地时间使用
ISO-8601 local datetime，并由分片 `timeZone=Asia/Shanghai` 明确解释。数组均按 ID
升序，ZIP Entry 时间固定，避免同一快照因遍历顺序产生漂移。

`manifest.json` 包含 `schemaVersion`、`exportedAt`（UTC Instant）、字符串 `userId`
和三个数据文件描述；每个描述包含 `path`、`sha256`、`sizeBytes`、`recordCount`、
`capturedAt`。哈希针对 ZIP 内对应 UTF-8 JSON 原始字节。

### 4.1 Auth 分片

只导出 `auth_user` 的 `id`、`nickname`、`avatarFileId`、`status`、`createTime`、
`updateTime`。明确排除密码摘要、外部身份 subject/openid/unionid、登录审计、会话、
Token、Secret 和注销任务内部信息。

### 4.2 Tracking 分片

导出当前用户的全部分类、物品、订阅、想买、处置和替换关系，包括逻辑删除业务行及其
`deleted/deleteTime`，保留用户输入字段、业务状态、关联 ID、版本和创建更新时间。
处置保留购买价快照和卖出金额。明确排除 `createBy/updateBy`、Wish 内部
`conversionKey`、Outbox、幂等记录、锁、重试和错误信息。

所有查询都带 `user_id = :userId`，按 ID 升序并使用 `LIMIT remaining + 1`；应用层按
六类资源累计 10,000 条，命中第 10,001 条即失败，不能先全表加载再判断。

### 4.3 Reminder 分片

导出当前用户的全部 Binding 与 Reminder Instance，包括 PENDING 和所有终态历史。
Binding 保留业务类型/业务 ID/提醒类型、启用状态和创建更新时间；Instance 保留业务
日期、提醒时间、时区、状态、解决时间/原因和创建更新时间。明确排除
`lastSourceVersion`、source event ID、`pendingMarker`、command log、digest、冲突与
重试信息。

查询同样始终包含 `user_id = :userId`、按 ID 升序、累计最多 10,000 条。

## 5. 安全、事务与资源

- 公网 `userId` 只取自 `UserSession`；Controller、Client 和 Repository 都没有把调用方
  传入任意用户 ID 的入口。
- 内部接口处于 Same-Token 可信边界并校验 Caller-Service；服务间 TraceId 原样传播。
- Tracking/Reminder 导出服务使用 `@Transactional(readOnly=true,
  isolation=REPEATABLE_READ)`，每个分片只反映其本地一致快照。
- 分片先序列化后计算实际字节上限；Auth 在 ZIP 组装过程中使用有上限的输出流，超过
  20 MiB 立即失败。所有并发许可和用户占用都在 `finally` 中释放。
- 文件名不包含昵称、用户 ID 或其他个人信息；日志只记录 trace、服务、结果、计数和
  字节数，不记录导出正文、敏感字段或完整远端响应。
- 响应禁止缓存；不在磁盘、数据库、Redis、Nacos 或对象存储中保存归档。

## 6. 依赖与配置变化

- 新增 `worthit-tracking-client`，由 Auth 和 Tracking App 依赖；Tracking App 实现自己
  的内部协议。
- 扩展 `worthit-reminder-client`；Reminder App 实现导出协议，既有 reconcile 契约不变。
- Auth App 新增两个 Client、`worthit-common-http`、`worthit-common-security` 与
  Spring Cloud LoadBalancer 依赖；仍是 Servlet/MVC，不允许引入 WebFlux。
- Nacos 本地模板新增 Auth 到 Tracking/Reminder 的导出 Client 超时，以及三服务导出
  上限；不写 Secret，不改变数据库、Redis 或 Nacos 拓扑。
- Gateway 的既有 `/api/v1/auth/**` 路由已覆盖公网入口，不新增路由或前端导航。

## 7. RED→GREEN 验证与交付

- Client 契约红测冻结路径、GET、字符串 ID 模型和运行时中立依赖。
- Controller/应用红测冻结二进制响应头、本人身份、并发拒绝、容量映射、无部分响应、
  文件顺序、哈希与敏感字段排除。
- MySQL Testcontainers 覆盖逻辑删除行、终态提醒、两用户隔离、稳定排序、10,001 条边界
  与本地只读快照；测试不依赖开发机数据库。
- 安全/OpenAPI/架构测试确认公网入口只进入 public 分组、内部接口只进入 internal
  分组、Same-Token 与调用方校验生效、Servlet/WebFlux 依赖隔离不回退。
- 执行 Auth、Tracking、Reminder 定向测试，相关 Reactor，全仓 `./mvnw verify` 与
  `./mvnw package`，并证明全部 Flyway 文件哈希和数量不变。
- 复用用户已经运行且健康的 MySQL 8.4、Redis 7.4、Nacos 3.0.3 做四服务真实联调；
  不停止、不重建、不读取 `dev-stack/.env`。验证两用户隔离、ZIP 内容/哈希/响应头和
  下游失败 JSON 语义。
- DOCX 先编辑、逐页渲染检查后再同步 Markdown 镜像和定稿索引。
- 后端通过 `feature/m3-data-export` → PR → CI/mergeability → merge commit → 同步
  `main` → push 交付；上级权威文档是独立工作区，不混入后端 PR。

回滚代码时移除公网入口、内部 Client/接口、导出用例和配置即可；没有数据库迁移、数据
回填或持久化导出文件需要回滚。已经下载到用户设备的归档不属于服务端可回收资产。
