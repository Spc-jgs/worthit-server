# M3 Tracking 完整恢复入口设计

## 1. 目标与范围

本轮把 M3 收敛为 Tracking 单服务可独立交付的完整恢复闭环：

- 统一分页查询当前用户已逻辑删除的 Item、Subscription、Wish。
- 删除短时恢复窗口过期后，仍可按删除后版本执行长期恢复。
- 恢复保持原业务状态和版本单调递增，不复活任何历史 Reminder 实例。
- 原分类仍有效时恢复原分类；原自定义分类已删除时回落到当前用户系统“未分类”。
- 同步 PRD、架构、接口、数据库无变更说明、技术门禁和产品验收终稿。

本轮不实现数据导出、账号注销或备份恢复；不新增或修改 Flyway、表、索引和数据；
不修改前端页面、工作台、样式、导航或交互。真实联调只验证后端公网契约，不替代产品
人员的视觉与人工验收。

## 2. 公网契约

### 2.1 已删除数据列表

`GET /api/v1/recovery/resources`

查询参数：

- `resourceType`：可选，`ITEM`、`SUBSCRIPTION`、`WISH`；省略时合并查询三类资源。
- `page`：从 1 开始，默认 1。
- `size`：默认 20，最大 50。

返回按 `deletedAt DESC, resourceType ASC, id DESC` 稳定排序的分页结果。每项冻结字段为：

- `id`：字符串形式的资源标识。
- `resourceType`、`name`、`status`。
- `categoryId`：字符串形式的删除前分类标识。
- `categoryName`：分类行仍存在时返回其名称。
- `categoryAvailable`：原分类当前是否有效。
- `version`：删除后的当前版本。
- `deletedAt`：逻辑删除时间。

列表只返回当前认证用户且 `del_flag = 1` 的数据；其他用户数据不可见。

### 2.2 长期恢复

`POST /api/v1/recovery/resources/{resourceType}/{id}/restore`

请求要求 UUID `Idempotency-Key`，请求体为：

```json
{ "version": 2 }
```

成功响应字段为 `id`、`resourceType`、`name`、`categoryId`、`categoryName`、`status`、
`version` 和 `categoryFallbackApplied`。所有标识仍以字符串返回。

- 资源不存在或不属于当前用户：`404 RES_NOT_FOUND`。
- 资源已恢复或版本变化：`409 VAL_STATE_CONFLICT`。
- 同一幂等键用于不同资源、类型或版本：`409 IDEM_CONFLICT`。
- 同 key、同请求成功重放同一响应。

短时恢复接口及其 60 秒令牌语义保持不变。

## 3. 数据与事务设计

删除列表使用只读投影对三张现有业务表执行 `UNION ALL`。过滤始终包含 `user_id` 与
`del_flag = 1`，单页最多 50 条；当前个人/小团队数据规模下，复用现有
`(user_id, del_flag, create_time)` 索引完成过滤，排序允许用户内小结果集 filesort，
没有证据支持为本轮新增索引或 Flyway。

长期恢复事务顺序：

1. 以 `userId + operation + Idempotency-Key` 占用幂等记录，请求摘要包含类型、资源 ID
   与删除后版本。
2. 按 `id + user_id` 读取包含逻辑删除行的删除状态。
3. 原分类有效时，沿用分类引用协议锁定可删除自定义分类；若观察或加锁时发现原分类已
   删除，则获取/创建系统“未分类”。
4. 以 `id + user_id + version + del_flag = 1` 条件更新 `category_id`、`del_flag`、
   `delete_time`、`version` 与审计字段。
5. 读取恢复后的投影并完成幂等响应。

分类锁先于业务对象条件更新，与分类删除/业务写入的既有锁顺序一致。两个不同幂等键
并发恢复同一删除版本时，仅一个条件更新成功；另一请求返回状态冲突。用户归属同时
进入查询与更新条件，不以接口层检查代替数据边界。

## 4. Reminder 语义

删除时已经写入关闭提醒的 Tracking Outbox。完整恢复只恢复业务事实，不写
`REMINDER_RECONCILE` Outbox，不把历史 CANCELED、IGNORED 或 DONE Reminder 改回
PENDING。用户若需要新的提醒，必须在恢复后通过既有业务更新接口显式启用或调整日期，
由该业务命令产生新的期望状态。

## 5. 候选方案与取舍

未选择三组独立列表/恢复 Controller：它会复制分页、鉴权、错误与幂等契约，并让后续
恢复入口继续按业务类型扩散。统一 Recovery 用例只编排 Tracking 内已经存在的三个聚合，
不跨服务，不下沉 Common。

未选择“原分类删除后拒绝恢复”：分类在 60 秒窗口后允许删除，拒绝会把业务数据永久
困在回收站且当前阶段没有分类恢复入口。自动回落“未分类”保留业务事实与引用完整性，
并通过 `categoryFallbackApplied` 对调用方显式披露。

未选择恢复 Reminder：历史提醒实例承载已经发生的投递与用户处理事实，复活会破坏审计
语义并可能重复触达；这也与现行 M3 架构边界冲突。

## 6. 验证与交付

- Controller 红测冻结字符串 ID、分页、枚举、UUID 幂等键和参数错误。
- 应用测试覆盖幂等重放/冲突、版本冲突、越权不可见、分类保留与回落。
- MySQL Testcontainers 覆盖三类列表、稳定排序、条件恢复、并发单胜者、版本递增和
  Outbox 数量不变。
- OpenAPI 分组测试确认新路径只进入 public 分组；Gateway 配置契约确认路由到 Tracking。
- 运行 Tracking 定向测试、Tracking reactor、Gateway 测试、全仓 Maven 门禁和 package。
- 复用用户已启动的 MySQL 8.4；因本次不读取 `dev-stack/.env` 密钥，Redis 7.4 与
  Nacos 3.0.3 使用任务自有的隔离临时容器。四服务经 Gateway 完成两用户真实公网
  联调后有序停机，临时容器和临时数据库账号精确回收，用户原中间件容器不变。
- DOCX 先编辑后渲染为逐页 PNG 检查，再同步 Markdown 镜像和定稿索引。
- 后端按 feature branch → PR → CI/mergeability → merge commit → main 同步 → push。
  上级权威文档是独立工作区交付，不混入后端 PR。

回滚代码时移除 Recovery 路由、用例、投影与新增稳定操作码即可；没有数据库迁移或数据
回填需要回滚。已经完成的长期恢复是正常业务事实，不做逆向批量删除。
