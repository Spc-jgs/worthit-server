# Dashboard M1.4 实时汇总设计

## 1. 目标

在 `worthit-tracking` 内实现冻结契约
`GET /api/v1/dashboard`，形成 M1.4 首页成本汇总能力：

- 汇总当前用户持有中、未删除物品的精确计划日均，并统计未填写残值的件数。
- 汇总当前用户有效、未删除订阅的人民币参考月成本；未折算外币只计数量，不混入金额。
- 汇总当前用户考虑中、未删除想买的数量和预计金额。
- 返回接口 V0.1.2 冻结的十个字段，并保持金额字符串和展示文案契约。

本功能遵循 TDD，按 RED → GREEN → REFACTOR 推进。设计、查询闭环、API
契约和验收测试分别形成可审阅提交。

## 2. 权威输入

- PRD V0.15.6：5.1 首页、6 成本与多币种规则。
- 后端架构 V0.3.16：6.3 模块边界、8.3 派生值策略、9 成本规则、
  16.1 查询策略、ADR-021、ADR-034、附录 A.1。
- 接口设计 V0.1.2：第 7 节 Dashboard。
- 数据库模型 V0.3.4 与 Tracking V1 Flyway：三类事实表、状态、逻辑删除和索引。
- 产品验收 V0.4.2：G-SUM-01、G-HOME-01、TC-WISH-008、
  TC-HOME-001～003、E2E-M1-B/C。

上述基线之间未发现需要新增字段、状态、错误码、接口或 DDL 的冲突。

## 3. 范围与非目标

### 3.1 本次实现

- Tracking 内部只读 Dashboard 查询模型。
- `GET /api/v1/dashboard` 公网接口及 OpenAPI 描述。
- 将 Gateway 的 Phase 0 占位路由收敛到冻结的 `/api/v1/**` 业务路径，
  接通 Auth→Gateway→Dashboard 纵切。
- 用户隔离、状态和逻辑删除过滤。
- 高精度汇总、最终统一舍入和展示文案。
- 单元、API 契约、MySQL 8.4 Testcontainers 集成和架构测试。
- 本地真实链路中的 Dashboard 验收。

### 3.2 非目标

- Dashboard 不包含 `pendingCount` 或“最近处理”。
- Tracking 不调用 Reminder；小程序仍并行请求 Reminder
  `pending-count`。
- 不新增缓存、快照表、定时任务、Client、Maven 模块或数据库迁移。
- 不修改 Item、Subscription、Wish 的写模型和业务状态机。
- 不进入前端 HTTP Adapter、M2 生命周期复盘或 M3 数据权利范围。

## 4. 契约与汇总口径

接口返回统一响应信封，`data` 固定包含：

| 字段 | 口径 |
| --- | --- |
| `itemPlanDailyTotal` | HOLDING 且未删除物品的精确计划日均先求和，最后 HALF_UP 两位 |
| `itemPlanDailyTotalDisplay` | `¥x.xx/天`；正数舍入后为 0 时显示 `<¥0.01/天` |
| `itemResidualUnsetCount` | 上述物品中 `residual_value IS NULL` 的数量 |
| `subscriptionMonthlyCnyTotal` | ACTIVE 且未删除订阅中，CNY 金额和有人民币参考的外币金额按周期标准化后求和 |
| `subscriptionMonthlyCnyTotalDisplay` | 有外币人民币参考参与时加 `约 `，否则为 `¥x.xx/月` |
| `subscriptionMonthlyCnyApproximate` | 至少一条有人民币参考的非 CNY 订阅参与合计时为 `true` |
| `subscriptionUnconvertedForeignCount` | 非 CNY 且人民币参考为空的有效订阅数量 |
| `wishConsideringCount` | CONSIDERING 且未删除想买数量 |
| `wishConsideringAmountTotal` | 上述想买 `expected_price` 精确求和后 HALF_UP 两位 |
| `wishConsideringAmountTotalDisplay` | `¥x.xx` |

空账号返回数值零、计数零和对应零值展示，不返回 `null`。

G-SUM-01 的两条 `1 / 365` 应先求精确和再舍入为 `0.01`，展示
`¥0.01/天`，不能先把单项舍入为 `0.00`。正数汇总只有在最终舍入仍为
`0.00` 时才使用 `<¥0.01/天`。

## 5. 分层设计

```text
DashboardController
        │
        ▼
DashboardService ── CurrentUserProvider
        │
        ▼
DashboardFactsQuery（应用层只读端口）
        │
        ▼
MybatisDashboardFactsQuery ── DashboardMapper
        │
        ├── trk_item
        ├── trk_subscription
        └── trk_wish
```

### 5.1 Interfaces

`DashboardController` 只负责：

- 接收 `GET /api/v1/dashboard`；
- 调用 `DashboardService`；
- 将应用结果转换为 `DashboardResponse`；
- 返回统一 `ApiResponse`。

Response 使用 `@Schema` 说明金额字符串、单位和近似语义。Controller 不计算金额，
也不访问 Mapper。

### 5.2 Application

`DashboardService`：

- 通过 `CurrentUserProvider` 获取可信当前用户；
- 在 `@Transactional(readOnly = true)` 中读取三类事实，保证三个查询处于同一
  MySQL 一致性快照；
- 使用现有 `ItemCostCalculator` 和
  `SubscriptionCostCalculator.normalizeMonthly` 计算精确值；
- 先累加精确值，再进行最终两位 HALF_UP 和展示转换；
- 返回不可变 `DashboardResult`。

`DashboardFactsQuery` 是 Dashboard 自己拥有的只读查询端口，只暴露计算所需的最小
事实投影。Application 不依赖 MyBatis、DO 或其他业务域 Mapper。

### 5.3 Infrastructure

`MybatisDashboardFactsQuery` 实现只读端口，`DashboardMapper` 执行三条按
`user_id`、状态和 `del_flag` 过滤的查询：

1. 物品只取 `purchase_price / expected_years / residual_value`；
2. 订阅只取 `amount / currency / billing_cycle_type /
   billing_cycle_value / cny_reference_amount`；
3. 想买在数据库中使用 DECIMAL `COUNT + SUM(expected_price)`。

Dashboard 不复用或直接调用 Item、Subscription、Wish 的 Mapper/DO，也不复制聚合
写模型。查询只读取冻结事实字段，计算公式继续复用领域 Calculator。

M1 为个人数据规模，按架构基线实时查询，不引入缓存或快照。现有索引已以
`user_id` 和 `del_flag` 为前缀；状态过滤在当前用户数据集内完成，本次不新增 DDL。

## 6. 精度和边界

- 全程使用 `BigDecimal`，禁止 `double/float`。
- Item 精确计划日均复用现有领域公式；需要把精确计划日均计算提取为可复用公开方法，
  原 `calculate` 委托该方法，避免 Dashboard 复制公式。
- Subscription 使用现有 24 位计算上下文标准化月成本。
- Wish 金额是数据库 DECIMAL 的精确加法。
- 任何单项均不提前舍入；最终汇总统一 `setScale(2, HALF_UP)`。
- 非 CNY 且无人民币参考的订阅绝不进入人民币金额。
- 已删除或非目标业务状态的数据不参与任何金额和计数。
- 每个查询必须显式携带当前 `user_id`，禁止跨用户汇总。

## 7. TDD 与测试矩阵

### 7.1 RED：领域精度和应用汇总

先增加失败测试：

- Item 精确计划日均公开计算仍满足 G-PLAN 金标。
- 空账号返回全零。
- G-SUM-01 证明先汇总后舍入。
- G-HOME-01 证明物品、订阅和想买的完整字段。
- 外币无参考只增加未折算数；有参考参与时近似标志和 `约` 生效。
- 非目标状态、删除和跨用户过滤由集成测试证明。

确认失败来自目标类型/行为缺失，再写最小实现。

### 7.2 RED：API 契约

先增加 `DashboardControllerTest`，验证：

- 路径与 HTTP 200；
- 十个字段、金额字符串、展示文案和统一 `traceId`；
- 响应中不存在 `pendingCount` 和最近处理字段。

确认 404/编译缺失后再实现 Controller 与 Response。

### 7.3 集成与门禁

`DashboardPersistenceIntegrationTest` 使用 MySQL 8.4 Testcontainers，至少覆盖：

- G-HOME-01；
- G-SUM-01；
- PAUSED/ENDED、PURCHASED/ABANDONED、非 HOLDING、逻辑删除不计入；
- CNY、已折算外币、未折算外币；
- 用户 A/B 数据隔离；
- 空账号。

同时执行：

- Dashboard 定向单元/API/集成测试；
- Gateway Nacos 模板契约测试，锁定 Auth、Tracking、Reminder 的公网业务路径；
- Tracking ArchUnit 与 OpenAPI 双组门禁；
- Tracking Reactor 测试和全仓 `mvn clean test`；
- `mvn -DskipTests package`；
- 本地真实中间件链路的 Dashboard 验收。

## 8. 提交与交付

计划提交边界：

1. `docs(tracking): 设计Dashboard实时汇总`
2. `feat(tracking): 实现Dashboard只读汇总`
3. `feat(tracking): 提供Dashboard公网接口`
4. `test(tracking): 补充Dashboard验收门禁`
5. `fix(gateway): 接通M1公网业务路由`

每次提交只暂存当前步骤文件，提交前执行对应测试和 `git diff --check`。完成后推送
`feature/dashboard-m1`，创建正式 PR，检查可合并状态和 CI，再使用 merge commit
合并；最后同步并验证本地 `main` 与 `origin/main`。
