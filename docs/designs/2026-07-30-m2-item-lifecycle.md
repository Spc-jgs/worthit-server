# M2 物品生命周期实现设计

日期：2026-07-30
更新：2026-07-31
状态：M2 生命周期闭环已实现并通过本地质量门禁
关联：#10、#11、#12

## 1. 权威输入与范围

本实现遵循上级文档现行基线：

- PRD V0.15.6；
- 微服务技术方案 V0.3.19；
- 接口设计 V0.2.2；
- 数据库模型 V0.3.5 与 Tracking M2 V2；
- 技术测试与质量门禁 V0.3.2；
- 产品验收测试用例 V0.5.2。

本轮完成幂等执行器、生命周期状态机、Tracking V2 迁移、终态指标冻结、退货、
卖出、报废、替换关系和生命周期复盘闭环。Flyway/MySQL 8.4 版本兼容治理仍不在
本轮实现范围；MySQL 8.4 迁移和持久化行为由 Testcontainers 门禁验证。

## 2. 分层与所有权

- Interfaces：Controller、Request、Response 和 HTTP/Bean Validation，只做协议
  转换，不持有业务状态机。
- Application：`ItemLifecycleService` 接口与同包
  `ItemLifecycleServiceImpl`；编排幂等、事务、Repository 与 Outbox。
- Domain：`ItemLifecycleStateMachine`、`ItemLifecycleStatus`、
  `DisposalType`、`ItemDisposal` 和 `ItemCostCalculator`；持有状态、日期、金额
  与计算不变量。
- Infrastructure：DO、Mapper、Repository 实现、条件更新和 Flyway SQL。

稳定有限集合使用带显式 code 的枚举。Request、Command、Domain、DO 与 Response
不跨层复用。

## 3. 状态机

状态只允许单向转换：

| 当前状态 | 命令 | 目标状态 |
| --- | --- | --- |
| HOLDING | return | RETURNED |
| HOLDING | sell | SOLD |
| HOLDING | scrap | SCRAPPED |
| 任一终态 | return/sell/scrap | 拒绝，VAL_STATE_CONFLICT |

逻辑删除与生命周期状态独立。客户端不得通过普通 Item 更新直接提交
`lifecycleStatus`。

## 4. 幂等执行矩阵

幂等键由 `operationCode + Idempotency-Key` 唯一确定，请求摘要参与冲突判断。

| 已有记录 | 摘要 | 租约 | 结果 |
| --- | --- | --- | --- |
| PROCESSING | 相同 | 活动 | 409 IDEM_IN_PROGRESS |
| PROCESSING | 不同 | 活动或过期 | 409 IDEM_CONFLICT |
| PROCESSING | 相同 | 过期 | 重新 claim 后执行 |
| SUCCEEDED | 相同 | — | 重放首次成功结果 |
| FAILED | 相同 | — | 重放首次终结性业务失败 |
| SUCCEEDED/FAILED | 不同 | — | 409 IDEM_CONFLICT |

JSON、请求头格式和 Bean Validation 在 claim 前执行，不产生幂等记录。进入用例后
可确定的 400/404/409 在业务事务回滚后固化为 FAILED。技术性 5xx 不固化失败，
仅允许活动租约到期后重试。

## 5. 处置事务

一次处置在 Tracking 单一本地事务内完成：

1. 按 `user_id + id + version + lifecycle_status=HOLDING + del_flag=0` 条件更新
   Item，写入目标终态、`warranty_reminder_enabled=false`，版本加一。
2. 插入唯一 Disposal，固化 `purchase_price_snapshot`。
3. 写入新 `sourceVersion`、`DISPOSE_ITEM`、`reminderEnabled=false` 的 Outbox。
4. 在同一事务中把幂等记录更新为 SUCCEEDED。

任何一步失败均整体回滚。并发处置以条件更新恰好影响一行为主门禁，
`uk_disposal_item` 为数据库最终防线；普通 Item 更新不得重新开启终态保修提醒。

## 6. 终态读模型与计算

- HOLDING：`holdingDays` 与 `holdingDailyCost` 以服务端当前日期为截止日。
- RETURNED/SOLD/SCRAPPED：以唯一 Disposal 的 `disposalDate` 为截止日。
- 同日购买并处置按 1 天计算。
- SOLD 的 `netCost = purchasePriceSnapshot - saleAmount`，允许负数。
- 处置后的 Item 购买价变化不得改写历史快照与净成本。
- 终态退出 Dashboard 当前持有统计，详情返回与状态一致的 Disposal。

## 7. Reminder 收敛

三种处置统一写 `DISPOSE_ITEM`：

- 已到达的保修 PENDING 收敛为 PROCESSED；
- 尚未到达的保修 PENDING 收敛为 CANCELED；
- 不创建新的 PENDING；
- Item 与 Outbox 的提醒期望都为 false。

Tracking 本地事务只保证 Item、Disposal、Outbox 原子提交；提交后的跨服务投递由
Relay、重试、DEAD 和人工重放机制最终收敛。

## 8. TDD 与门禁

实现顺序遵循 RED → GREEN → REFACTOR：

1. #10 幂等租约与失败持久化矩阵；
2. 状态机领域模型；
3. M2 V2 迁移及 MySQL 8.4 Testcontainers 约束测试；
4. #11 终态持有指标冻结与详情读模型；
5. 退货；
6. 卖出；
7. 报废；
8. #12 并发、回滚、提醒真/假分支、OpenAPI 与三层门禁。

### 8.1 替换关系

- 公网写接口为 `POST /api/v1/items/{oldItemId}/replace`，请求体只包含字符串
  `newItemId`，响应返回字符串 `relationId` 和带名称的旧/新物品摘要。
- 两件物品必须属于当前用户、未逻辑删除且 ID 不同；生命周期状态不限制。
- 业务事务按物品 ID 升序执行 `SELECT ... FOR UPDATE`，消除替换与逻辑删除之间的
  锁顺序歧义；数据库 `uk_repl_old_item`、`uk_repl_new_item` 作为旧/新角色
  一对一的最终防线。
- 替换只追加不可变关系事实，不改变物品状态、版本、保修提醒，也不写 Reminder
  Outbox。
- `ITEM_REPLACE + Idempotency-Key` 保证成功重放与请求摘要冲突语义一致。

### 8.2 生命周期复盘

- 公网读接口为 `GET /api/v1/lifecycle/review?page=1&size=20`。
- 读模型使用 `DISPOSAL / REPLACEMENT` 显式判别联合；`disposal` 与
  `replacement` 两个分支恰好一个非空。
- Disposal 的 `eventDate` 使用业务处置日期；Replacement 使用
  `DATE(create_time)`；统一按
  `eventDate DESC, createTime DESC, id DESC` 稳定分页。
- 查询只连接 Tracking 本地事实和物品表，不依赖缓存、快照表或跨服务调用；连接
  物品历史时不以 `del_flag=0` 过滤，因此逻辑删除后仍可复盘。
- 卖出净成本只使用处置事实内的购买价快照，不受当前物品价格修改影响。
- Gateway Nacos 模板显式路由 `/api/v1/lifecycle/**` 到 Tracking；公网
  OpenAPI 包含新接口，内部 OpenAPI 不暴露它们。

### 8.3 验证证据

实现顺序遵循 RED → GREEN → REFACTOR。合码前已执行：

- Flyway 执行源一致性自测与正式校验；
- `./mvnw --batch-mode --no-transfer-progress clean verify`；
- 18 个 reactor 模块全部成功，Surefire 共 445 个测试，0 failure、
  0 error、0 skipped；
- MySQL 8.4、Redis 7.4、Nacos 3.0.3 本机容器的 Compose 渲染、镜像、
  health、端口及 Nacos readiness 只读探测；
- `git diff --check`、分层架构、并发、用户隔离、持久化、Gateway 路由和公网
  OpenAPI 契约检查。
