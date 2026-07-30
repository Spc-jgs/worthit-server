# M2 物品生命周期实现设计

日期：2026-07-30
状态：退货、卖出、报废已实现
关联：#10、#11、#12

## 1. 权威输入与范围

本实现遵循上级文档现行基线：

- PRD V0.15.6；
- 微服务技术方案 V0.3.18；
- 接口设计 V0.2.1；
- 数据库模型 V0.3.5 与 Tracking M2 V2；
- 技术测试与质量门禁 V0.3.1；
- 产品验收测试用例 V0.5.1。

本轮完成幂等执行器、生命周期状态机、Tracking V2 迁移、终态指标冻结以及退货、
卖出、报废闭环。替换关系、生命周期复盘和 Flyway/MySQL 8.4 兼容治理不在本轮
实现范围。

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

合码前执行 Tracking 与 Reminder reactor 测试、`git diff --check`、分层审查和
公网 OpenAPI 契约检查。
