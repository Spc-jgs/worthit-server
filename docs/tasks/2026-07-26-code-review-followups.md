# 2026-07-26 代码审查后续待办

## 状态说明

本轮只修复以下两个已确认的 Gateway P0：

- 未登录错误链路在可信 Header GlobalFilter 之前执行，TraceId 缺失或可能回显伪造值。
- 用户明确要求的账号密码登录没有加入 Gateway 匿名白名单。

本文登记其余问题，不代表已经实现或验证。

## 业务闭环待办

### TODO-BIZ-001：补齐 Item 更新、删除与短时恢复

**现状**

`ItemController` 只有创建、详情和分页列表，没有接口终稿中的 PATCH、DELETE 和
`POST /items/{id}/restore`。

**风险**

当前实现不能通过 M1.1/M1.4 中物品编辑、删除、恢复、版本冲突和越权恢复验收。

**修复边界**

- 按接口终稿实现完整请求和响应，不新增字段、状态或错误码。
- 更新、删除和恢复使用 `version` 乐观锁。
- 删除返回服务端生成的 `restoreToken` 与 `restoreDeadline`。
- 恢复校验窗口、Token、版本和用户归属；成功后不自动恢复旧 Reminder。
- 每个状态变化在同一 Tracking 本地事务中至多写一条对应 Outbox。

**完成标准**

- 覆盖 PATCH 成功及版本冲突。
- 覆盖删除后窗口内恢复、超时、重复恢复、错误 Token、版本冲突和跨用户访问。
- 通过接口终稿及 TC-ITEM-007、008、011、012、013 对应验收。

### TODO-BIZ-002：实现 Tracking Outbox 到 Reminder 的可靠投递

**现状**

Item 创建只向 `trk_outbox_event` 写入 `NEW`，没有 Relay 抢占、投递、重试、成功或
DEAD 状态推进；Reminder App 也尚未实现 reconcile 用例。

**风险**

保修提醒事件会永久停留在 Tracking 数据库，不能形成 Reminder PENDING 实例。

**修复边界**

- Tracking Relay 按 `NEW / PROCESSING / RETRY_WAIT / SUCCEEDED / DEAD` 状态机实现。
- 多实例通过租约抢占并支持 PROCESSING 超时回收。
- 使用原 `eventId` 调用 `ReminderCommandClient.reconcile`，不得重建事件编号绕过幂等。
- Reminder 严格按架构终稿 10.7 的 Binding 行锁、command_log 幂等、版本和
  payload digest 规则实现。

**完成标准**

- 正常投递生成唯一 PENDING 实例并将 Outbox 标记 SUCCEEDED。
- 覆盖重复投递、乱序、结果未知重试、租约回收、退避、DEAD 和人工重放。
- 覆盖同 Binding 并发 reconcile 及相同版本不同 payload 的契约冲突。

## P1

### TODO-P1-001：分类删除与业务对象创建并发不一致

**现状**

分类删除先查询是否被引用，再执行逻辑删除；分类行和引用创建之间没有串行化防线，
`trk_item` 等表也没有防止引用已删除分类的数据库约束。

**风险**

并发时可创建引用已删除分类的有效对象。Item 列表使用有效分类 JOIN，而 count
不 JOIN 分类，可能出现数据不可见及分页 total 不一致。

**建议边界**

先为删除与引用创建确定同一套锁顺序或等价 CAS 方案，再补并发集成测试；不得仅通过
调整查询 JOIN 掩盖孤儿引用。

**完成标准**

- 分类删除与 Item/Subscription/Wish 创建并发时只能有一个合法结果。
- 不产生引用已删除分类的有效对象。
- 列表结果数量与 total 一致。

### TODO-P1-002：超长 categoryId 通过校验后返回 500

**现状**

请求正则允许 19 位正整数，但部分合法 19 位文本大于 `Long.MAX_VALUE`；
Controller 中 `Long.valueOf` 抛出的 `NumberFormatException` 会进入通用 500。

**建议边界**

在 Web 参数绑定边界统一校验可表示的正 `long`，保持跨端 JSON ID 为字符串，不把
Snowflake ID 转成 JavaScript number。

**完成标准**

- `9223372036854775807` 按资源语义正常进入查询。
- `9223372036854775808` 和 `9999999999999999999` 返回 HTTP 400、
  `VAL_INVALID_ARGUMENT`，且不记录为未处理系统异常。
- 创建和列表 categoryId 使用相同规则。

### TODO-P1-003：Flyway 与 MySQL 8.4 兼容基线漂移

**现状**

当前依赖解析为 Flyway 11.7.2。MySQL 8.4 Testcontainers 迁移日志提示该版本只验证到
MySQL 8.1，MySQL 8.4 支持未经测试。

**建议边界**

只治理 Flyway 版本兼容，不顺带升级 Spring Boot、数据库驱动或开展其他依赖治理；
目标版本必须通过现有依赖门禁和四份权威迁移空库验证。

**完成标准**

- 依赖树显示明确受控的 Flyway 版本。
- MySQL 8.4 空库迁移不再输出“latest supported version is MySQL 8.1”警告。
- Auth、Tracking、Reminder 和 tracking_m2 权威迁移均通过约束验证。

## 文档债务

### TODO-DOC-001：同步账号密码登录权威基线

**现状**

账号密码登录是用户明确要求的功能，代码、V2 凭据表和本地账号初始化已经实现，但
上级 PRD、接口终稿和数据库定稿索引仍只登记微信登录及 V1 Auth 迁移。

**处理要求**

后续经用户授权修改上级 `worthit/docs` 时，同步产品适用端、匿名接口契约、错误语义、
密码凭据表和 Flyway 版本清单；在此之前不得反向删除已确认的密码登录功能。

