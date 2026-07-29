# M2 物品生命周期 TDD 实施计划

日期：2026-07-29

## 1. 目标与输入

按以下冻结基线实现退货、卖出、报废、替换关系和生命周期复盘：

- PRD V0.15.6；
- 架构 V0.3.17；
- 接口 V0.2；
- 数据库 V0.3.5 与 Tracking M2 V2 SQL；
- 技术门禁 V0.3；
- 产品验收 V0.5；
- `2026-07-29-m2-item-lifecycle-contract-design.md`。

每个任务都在工作区内先确认 RED，再完成 GREEN 和必要 REFACTOR；一个提交只交付
一个完整、可验证的功能或基础能力，不提交只有失败测试的共享分支状态。

## 2. 实施顺序

### Task 1：领域状态机

先新增失败单元测试，覆盖：

- `HOLDING` 到 `RETURNED / SOLD / SCRAPPED`；
- 终态再次处置与终态互转拒绝；
- 日期边界、卖出金额、类型与金额组合；
- 卖出基础净成本精度和负值。

实现要求：

- 扩展 `ItemLifecycleStatus` 显式 code 枚举；
- 新增 `DisposalType` 显式 code 枚举；
- Item 聚合提供具名状态转换，不暴露通用状态 setter；
- 时间使用注入的 `Clock`，金额使用 `BigDecimal`。

提交：

`feat(tracking): 建立物品处置状态机`

### Task 2：M2 数据库迁移与持久化边界

先新增 MySQL 8.4 Testcontainers 失败测试：

- Tracking V1→V2 顺序迁移；
- Disposal 购买价快照、CHECK/UNIQUE；
- Replacement 两个 UNIQUE 和 old≠new CHECK；
- 条件更新只接受 `user_id + del_flag=0 + version + HOLDING`。

实现要求：

- 将冻结的 V2 SQL 放入 Tracking App 迁移执行源；
- 文档 SQL 与执行源做 SHA/内容一致性检查；
- `purchase_price_snapshot` 必填非负，处置持久化不得回查可编辑 Item 价格计算历史；
- Repository 端口不泄漏 DO/Mapper；
- MyBatis 实现翻译唯一约束和条件更新结果，不用异常消息承担公网机器契约。

提交：

`feat(tracking): 启用M2生命周期迁移`

### Task 3：退货闭环

先写 Application 和 API RED：

- 成功事务更新 Item、写 Disposal、写 Outbox；
- `ITEM_RETURN` 幂等首次、成功/进入用例后的终结性失败重放、摘要冲突及技术失败
  lease 重试；claim 前的 HTTP/Bean Validation 失败不持久化；
- 日期/版本/状态/越权错误；
- Reminder 完整期望使用 `DISPOSE_ITEM`；
- Controller 只依赖 `ItemLifecycleService`。

实现要求：

- `ItemLifecycleService` 为接口；
- `ItemLifecycleServiceImpl` 承载事务与用例编排；
- `IdempotencyExecutionCoordinator` 以独立短事务提交 claim；成功与业务事务原子
  完成，终结性失败在回滚后独立固化，技术性失败不固化；
- `ReturnItemRequest → ReturnItemCommand → Domain → DO` 显式转换；
- Response 与 OpenAPI 使用冻结字段和中文描述。

提交：

`feat(tracking): 实现物品退货闭环`

### Task 4：卖出闭环

先写 RED：

- 必填非负十进制金额及 `DECIMAL(18,6)` precision/scale 边界；
- `ITEM_SELL` 幂等与并发；
- 净成本使用处置时购买价快照减卖出金额，允许负数；
- 卖出后修改 Item 购买价，详情与复盘历史净成本保持不变；
- 详情和统一处置响应字段。

复用共享编排的前提是语义、所有权和变化原因一致；不得把 return/sell/scrap 做成
字符串分支或通用 Map。

提交：

`feat(tracking): 实现物品卖出闭环`

### Task 5：报废闭环

先写 RED：

- `ITEM_SCRAP` 幂等与并发；
- 报废日期边界；
- 禁止卖出金额；
- Reminder、Dashboard 和统一响应。

提交：

`feat(tracking): 实现物品报废闭环`

### Task 6：处置并发与跨服务收敛门禁

补充真实并发和故障注入：

- 同版本、不同幂等键、不同处置只有一个成功；
- Disposal 或 Outbox 失败全部回滚；
- Relay 重试不重复处置；
- 已到达/未到达保修提醒分别收敛为 PROCESSED/CANCELED；
- 终态物品退出 Dashboard。

提交：

`test(tracking): 补充物品处置并发与提醒门禁`

### Task 7：替换关系

先写 RED：

- 两个物品归属、未删除、不同 ID；
- 按 ID 升序加锁并发删除；
- old/new 唯一关系；
- `ITEM_REPLACE` 幂等；
- 不改变 Item 状态、版本或 Reminder。

提交：

`feat(tracking): 实现物品替换关系`

### Task 8：生命周期复盘

先写 RED：

- DISPOSAL/REPLACEMENT 联合条目；
- `eventDate DESC, createTime DESC, id DESC` 稳定分页；
- 卖出金额和净成本；
- 已删除物品历史保留；
- 用户隔离与 OpenAPI Schema。

实现只读端口和 MyBatis 联合查询，不引入缓存、快照表或跨服务查询。
同时增加 Gateway `/api/v1/lifecycle/** → worthit-tracking` 路由及 Nacos 模板契约
测试，证明公网可达且 `/internal/**` 仍不暴露。

提交：

`feat(tracking): 实现生命周期复盘`

### Task 9：M2 端到端验收

执行并固化：

- TECH-LIFE-001~010；
- TC-LIFE-001~014；
- E2E-M2-A；
- Tracking 模块 `verify`；
- 全仓 `clean verify` 与 `package`；
- GitHub CI。

只补测试和交付证据，不在本任务顺带重构业务代码。

提交：

`test(tracking): 固化M2生命周期验收`

## 3. 每个功能的完成条件

每个功能提交前必须同时满足：

1. RED 的失败原因确实是目标行为缺失；
2. 定向 Unit/Application/API/IT 全绿；
3. Service 为接口、Impl 实现，入口不依赖 Impl；
4. 稳定有限集合已枚举化，稳定字面量已归属到专用常量；
5. Controller 无业务规则，Domain 无 Web/MyBatis 依赖；
6. 幂等、版本、用户隔离、事务和 Outbox 副作用有断言；
7. `git diff --check` 通过，只暂存本功能文件；
8. Conventional Commit 使用中文描述。

## 4. 不进入本计划

- Flyway/MySQL 8.4 版本兼容治理；
- 处置撤销、处置事实编辑或终态互转；
- 完整 TCO、维修、配件、退款；
- 新中间件、缓存、快照、Client/Common 模块；
- 前端 HTTP Adapter。
