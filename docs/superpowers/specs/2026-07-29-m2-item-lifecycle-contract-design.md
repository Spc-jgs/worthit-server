# M2 物品生命周期契约冻结设计

日期：2026-07-29

## 1. 目标

冻结 M2 物品生命周期的公网接口、数据库、状态机、提醒联动和验收契约，为退货、
卖出、报废的 TDD 实现建立可执行基线，并为替换关系和生命周期复盘保留完整链路。

本轮交付是契约与设计基线，不实现业务代码。后续实现必须遵循
RED → GREEN → REFACTOR，并按“状态机、应用编排、持久化、API、提醒联动、验收”
的依赖顺序分功能提交。

## 2. 权威输入

- PRD V0.15.6：4.4 生命周期处理、6.4 成本与复盘、7.3 状态联动、
  8.1 数据校验、REQ-ITEM-003、REQ-REPORT-001。
- 后端架构 V0.3.16：Tracking 生命周期模块、Item 不变量、业务状态与逻辑删除、
  Outbox/Reminder reconcile。
- 接口设计 V0.1.2：M1 公网契约及 M2 路由占位。
- 数据库模型 V0.3.4 与
  `flyway/tracking_m2/V2__add_item_lifecycle.sql`：处置事实、替换关系、
  唯一约束和卖出金额约束。
- 技术门禁 V0.2.3、产品验收 V0.4.2：现行 M1 门禁及尚未展开的 M2 追溯项。

现行需求、架构和数据库对四种生命周期状态及三种处置事实没有冲突。接口、状态
转换矩阵、并发语义和 M2 验收仍未展开，因此必须先冻结后再写实现。

## 3. 范围与非目标

### 3.1 本轮冻结

- 退货、卖出、报废三个公网命令及统一响应。
- 替换关系命令和生命周期复盘查询。
- `HOLDING / RETURNED / SOLD / SCRAPPED` 状态机。
- `trk_item`、`trk_item_disposal`、`trk_item_replacement` 的一致性边界。
- 处置引发的 Dashboard 退出和保修 Reminder 归档。
- 幂等、乐观锁、并发、权限、日期、金额和数据库约束。
- 后续 TDD 的单元、应用、API、MySQL 集成和端到端验收矩阵。

### 3.2 非目标

- 不实现退货、卖出、报废、替换或复盘业务代码。
- 不新增撤销处置、修改处置事实或终态互转接口；如未来需要须重新立项。
- 不引入维修、配件、保养、税费、退款或完整 TCO。
- 不修改 M1 删除/短时恢复语义；逻辑删除与生命周期状态继续独立。
- 不新增服务、Client、Common 模块、中间件、缓存或快照表。
- 不升级 Flyway，不开展 Flyway/MySQL 8.4 兼容治理，不修改已发布迁移。

## 4. 状态机

```text
                     return
              ┌────────────────► RETURNED
              │
HOLDING ──────┼────── sell ────► SOLD
              │
              └────── scrap ───► SCRAPPED
```

| 当前状态 | 允许命令 | 目标状态 | 结果 |
| --- | --- | --- | --- |
| `HOLDING` | `return` | `RETURNED` | 写入一条 `RETURNED` 处置事实 |
| `HOLDING` | `sell` | `SOLD` | 写入一条含卖出金额的 `SOLD` 处置事实 |
| `HOLDING` | `scrap` | `SCRAPPED` | 写入一条 `SCRAPPED` 处置事实 |
| 任一终态 | 任一处置命令 | 不变 | `VAL_STATE_CONFLICT` |
| 任一状态 | `replace` | 不变 | 只建立替换关系，不暗改生命周期状态 |

状态转换只允许单向执行。处置命令不复用通用 PATCH，不接受客户端传入目标状态；
Domain 由具名行为完成转换。逻辑删除 `del_flag` 与生命周期状态正交：已删除或不属于
当前用户的物品统一按 `RES_NOT_FOUND` 处理，恢复也不改变删除前的生命周期状态。

## 5. 公网接口契约

所有 M2 写接口：

- 路径位于 `/api/v1/**`，必须登录；
- 强制 UUID 格式 `Idempotency-Key`；
- Snowflake ID 在 JSON 中使用字符串；
- 金额以十进制字符串传输；
- 同键同摘要重放原结果，同键不同摘要返回 `IDEM_CONFLICT`；
- 幂等操作码按路由冻结为 `ITEM_RETURN / ITEM_SELL / ITEM_SCRAP /
  ITEM_REPLACE`，客户端不得传入或覆盖；
- 已删除、不存在或不属于当前用户统一返回 `404 RES_NOT_FOUND`；
- 版本过期、非法状态或唯一关系冲突返回 `409 VAL_STATE_CONFLICT`；
- 参数格式、未来日期、早于购买日期或负金额返回
  `400 VAL_INVALID_ARGUMENT`。

### 5.1 退货

`POST /api/v1/items/{id}/return`

```json
{
  "version": 1,
  "returnDate": "2026-07-29",
  "remark": "尺寸不合适"
}
```

`returnDate` 必填；`remark` 可空，最长 512 字符。成功后状态为 `RETURNED`，
`saleAmount` 必须为空。

### 5.2 卖出

`POST /api/v1/items/{id}/sell`

```json
{
  "version": 1,
  "saleDate": "2026-07-29",
  "saleAmount": "800.00"
}
```

`saleDate`、`saleAmount` 必填，金额必须大于等于 0。成功后状态为 `SOLD`；
基础净成本为 `purchasePrice - saleAmount`，允许为负，不因盈利截断为 0。

### 5.3 报废

`POST /api/v1/items/{id}/scrap`

```json
{
  "version": 1,
  "scrapDate": "2026-07-29"
}
```

`scrapDate` 必填。成功后状态为 `SCRAPPED`，`saleAmount` 必须为空。

### 5.4 处置响应与物品详情

三个处置接口统一返回 `ItemLifecycleResponse`：

```json
{
  "itemId": "1983456789012345678",
  "lifecycleStatus": "SOLD",
  "disposal": {
    "type": "SOLD",
    "date": "2026-07-29",
    "saleAmount": "800.00",
    "remark": null,
    "netCost": "200.00"
  },
  "version": 2,
  "updateTime": "2026-07-29T15:00:00"
}
```

`netCost` 仅 `SOLD` 返回；其他类型为 `null`。`GET /api/v1/items/{id}` 在 M2
增加同结构的 nullable `disposal`；`HOLDING` 时为 `null`，终态时必须存在且类型与
状态一致。列表继续返回 `lifecycleStatus`，不携带完整处置事实。

### 5.5 替换关系

`POST /api/v1/items/{oldItemId}/replace`

```json
{
  "newItemId": "1983456789012345688"
}
```

该命令只建立一条旧物品到新物品的关系，不改变任一物品状态，也不产生 Reminder
Outbox。两个物品必须属于当前用户、未删除且不能相同；一个旧物品最多关联一个新
物品，一个新物品最多被一个旧物品关联。响应返回关系 ID、旧/新物品最小摘要和
`createTime`。

### 5.6 生命周期复盘

`GET /api/v1/lifecycle/review?page=1&size=20`

返回 `PageResponse<LifecycleReviewEntryResponse>`，按
`eventDate DESC, createTime DESC, id DESC` 稳定排序。条目使用显式有限枚举
`entryType=DISPOSAL|REPLACEMENT`：

- `DISPOSAL`：包含处置类型、日期、物品摘要、卖出金额和基础净成本；
- `REPLACEMENT`：包含旧/新物品摘要，处置字段为 `null`；
- `eventDate`：处置使用 `disposal_date`，替换使用 `DATE(create_time)`；
- 已删除物品的历史仍保留在复盘中，不因当前删除状态丢失。

Gateway 必须新增 `/api/v1/lifecycle/** → worthit-tracking` 公网路由，并由路由
模板/集成测试证明 `/internal/**` 仍不暴露；否则复盘 Controller 不构成可访问链路。

## 6. 数据库与事务边界

沿用数据库模型 V0.3.4 和现行 M2 `V2__add_item_lifecycle.sql`，本轮无 DDL 变化：

- `trk_item.lifecycle_status` 保存当前状态，`version` 承担乐观锁；
- `trk_item_disposal` 保存不可变处置事实，`UNIQUE(item_id)` 保证一件物品最多
  一条处置；
- `disposal_type` 仅允许 `RETURNED / SOLD / SCRAPPED`；
- `SOLD` 必须有非负 `sale_amount`，其他类型必须为空；
- `trk_item_replacement` 的 `UNIQUE(old_item_id)` 和 `UNIQUE(new_item_id)`
  冻结一对一关系；
- 不新增跨库外键；Application 必须验证 `user_id`，数据库唯一键和 CHECK 是最终
  防线。

一次处置在同一本地事务中完成：

1. 按 `id + user_id + del_flag=0 + version + lifecycle_status=HOLDING` 条件更新
   `trk_item`，状态进入目标终态且版本加一；
2. 写入 `trk_item_disposal`；
3. 以新版本写入 Reminder 期望 Outbox，`operationType=DISPOSE_ITEM`；
4. 任一步失败则全部回滚。

并发处置不能只靠“先查后写”。条件更新必须恰好影响一行；失败后区分资源不存在与
状态/版本冲突。数据库 `uk_disposal_item` 继续防御遗漏或竞态。替换关系使用本地
 事务和唯一约束收敛并发。事务内按 ID 升序锁定两个物品并重新校验归属与未删除
 状态，避免与逻辑删除并发产生悬空关系；不更新 Item 版本。

## 7. 提醒、统计与安全

- 处置后物品立即退出 Dashboard 当前持有物品统计。
- 保修 Reminder 的完整期望状态由 Tracking 写入 Outbox：
  `businessStatusCode` 为终态，`reminderEnabled=false`，
  `operationType=DISPOSE_ITEM`。
- 已到达的 PENDING 进入 `PROCESSED`，尚未到达的 PENDING 进入 `CANCELED`；
  不创建新的 PENDING。
- 本地 Outbox 写入失败时 Item、Disposal、Outbox 整体回滚；只有事务提交后的
  Relay 投递失败才不回滚已提交的处置事实，并按现行重试、DEAD 和人工重放机制
  最终收敛。
- 客户端不能传 `operationType`、`businessStatusCode`、`userId` 或 Reminder
  归档原因；这些值全部由服务端根据可信身份和用例生成。
- Repository 查询和条件更新始终携带 `user_id`；越权与不存在统一 404，避免资源
  枚举。

## 8. 后续实现分层与代码规范

```text
ItemLifecycleController
        │
        ▼
ItemLifecycleService（接口）
        │
        ▼
ItemLifecycleServiceImpl
        │
        ├── Item 聚合状态机
        ├── ItemLifecycleRepository（端口）
        ├── IdempotencyStore
        └── OutboxWriter
                │
                ▼
MybatisItemLifecycleRepository
```

- Interfaces 只做 HTTP、Bean Validation、身份与 DTO 转换。
- Application 使用 `ItemLifecycleService` 接口和同包
  `ItemLifecycleServiceImpl`；Controller 只依赖接口。
- Domain 拥有 `ItemLifecycleStatus`、`DisposalType`、日期/金额不变量和单向转换，
  不依赖 Spring Web 或 MyBatis。
- Infrastructure 拥有 DO、Mapper、条件更新、唯一约束异常翻译和读模型查询。
- 稳定有限集合使用显式 code 枚举，不使用 `String`、`name()` 或 ordinal。
- 公共请求头、长度、分页上限和时间/金额精度使用职责明确的常量，不创建全局
  `Constants` 大杂烩。
- 公网 Request/Response、Application Command/Result、Domain 和 DO 不跨层复用。
- 金额使用 `BigDecimal`，日期使用 `LocalDate`，时钟通过 `Clock` 注入。

## 9. TDD 与验收矩阵

### 9.1 RED：Domain

- `HOLDING` 分别转 `RETURNED / SOLD / SCRAPPED`。
- 任一终态拒绝再次处置或终态互转。
- 未来日期、早于购买日期、负卖出金额被拒绝。
- `SOLD` 必须有金额，其他处置类型禁止金额。
- 基础净成本保留精度并允许负数。

### 9.2 RED：Application

- 成功处置原子更新 Item、Disposal 和 Outbox。
- Item 条件更新失败时不写 Disposal/Outbox。
- 同一幂等键同请求重放原结果，不重复写库；不同摘要冲突。
- 相同版本并发不同处置仅一个成功，失败方得到 `VAL_STATE_CONFLICT`。
- Outbox 使用新 `sourceVersion`、服务端 `DISPOSE_ITEM` 和完整终态期望。
- 替换关系不改变 Item 状态和版本。

### 9.3 RED：API/Contract

- 三条路径、强制幂等头、请求字段、金额字符串、日期格式和统一信封。
- 404 防枚举、400 参数错误、409 状态/版本/幂等冲突。
- Request 中额外伪造的 `operationType/userId/businessStatusCode` 不被采纳。
- Item 详情和生命周期复盘的联合类型序列化/OpenAPI Schema。

### 9.4 MySQL 8.4 / Testcontainers

- 空库顺序执行 Tracking V1 + M2 V2。
- `uk_disposal_item`、三项 CHECK、两个 Replacement UNIQUE 全部生效。
- 条件更新与并发处置只产生一个终态、一条 Disposal、一个 Outbox。
- 事务回滚不留下半成品。
- Dashboard 排除终态；复盘保留已删除物品历史。

### 9.5 E2E

- 创建持有物品 → 卖出 → Dashboard 金额减少 → 详情/复盘显示卖出事实与净成本。
- 退货/报废分别验证状态、日期、无卖出金额。
- 未来/过早日期、并发处置、越权、幂等冲突全部返回冻结错误。
- 保修提醒已到达时处置为 `PROCESSED`，未到达时为 `CANCELED`。

M2 合码要求所有新增 P0 通过；P1/P2 若暂未处理必须建立 GitHub Issue，说明影响、
证据和后续计划，不得静默忽略。

## 10. Flyway/MySQL 8.4 兼容治理说明

Flyway 已经是当前项目的数据库迁移工具，不是本轮建议引入的新中间件。Spring Boot
启动时由 Flyway 按版本执行 `V1/V2...` SQL，并在 schema history 中记录已执行迁移，
用于保证空库和升级库到达同一结构。

当前依赖解析为 Flyway 11.7.2；它对 MySQL 8.4 给出“数据库版本高于已测试支持上限”
警告。现有测试成功说明当前 SQL 没有暴露不兼容，但不能证明所有迁移、元数据查询和
未来升级路径都在该组合的官方测试范围内。因此这是兼容证据缺口，不是当前已发生的
数据错误，也不是 M2 契约冻结的 P0。

若后续单独治理，应：

1. 升级现有 Flyway 到明确支持 MySQL 8.4 的版本，不并行引入第二套迁移工具；
2. 在 MySQL 8.4 Testcontainers 上验证空库迁移和已有 V1 → V2 升级路径；
3. 校验 schema history、重复启动、失败恢复、CHECK/生成列/索引；
4. 通过依赖树、全仓测试和 CI 后再合并，并保留版本回退方案。

本目标不修改 Flyway 版本、POM、迁移位置或 CI。

## 11. 文档版本与交付

本轮语义变更按文档维护规则创建新版本：

- 架构：V0.3.17；
- 接口：V0.2；
- 技术门禁：V0.3；
- 产品验收：V0.5；
- 数据库：继续使用 V0.3.4 和现行 M2 V2 SQL，无 DDL 变化。

DOCX 先修改并逐页渲染；同版本 Markdown 后同步。`../docs` 不属于后端 Git
仓库，权威文档本地定稿与后端 PR 分开交付；后端 PR 提交本设计和可检查的追溯
基线，不把上级未跟踪文档伪装成后端提交内容。
