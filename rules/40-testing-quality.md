# 测试与质量门禁

## 基本原则

- 新功能、缺陷修复和行为变更遵循 RED → GREEN → REFACTOR。
- 先写能因目标行为缺失而失败的测试，确认失败原因正确，再写最小实现。
- 测试必须证明业务行为、边界或集成结果，不只证明 Mock 被调用。
- 配置文件、生成文件等不适合测试先行的改动，需要在实施前说明原因，并提供静态校验或运行验证。
- 测试不能替代代码审查、数据库约束、日志证据和真实链路验证。

## 测试层次

| 层次 | 主要验证内容 | 不应承担 |
| --- | --- | --- |
| Unit | 值对象、状态机、计算、映射和纯业务规则 | Spring 容器和真实数据库 |
| Application | 用例编排、权限、幂等、事务边界、Outbox 写入 | Controller JSON 细节 |
| Integration | Repository、MyBatis、事务、锁、唯一约束和外部适配 | 只靠 Mock 推断真实行为 |
| API/Contract | 路径、请求头、序列化、校验、信封、错误码和 Client 一致性 | 内部实现细节 |
| Migration | MySQL 空库迁移、升级路径、索引和约束 | H2 兼容性猜测 |
| Architecture | 模块、包和层次依赖方向 | 业务结果正确性 |
| E2E | Gateway 到服务、数据库及必要外部依赖的关键闭环 | 穷举所有领域分支 |

## 单元与应用测试

- 一个测试表达一个主要行为，名称包含场景和预期结果。
- 使用 Arrange/Act/Assert 或 Given/When/Then 组织，不在测试中复制生产算法。
- 覆盖正常路径、边界、无权限、重复命令、版本冲突和失败恢复。
- 金额测试使用精确 `BigDecimal`，时间测试注入可控 `Clock`，不依赖当前系统时间和 `sleep`。
- 并发语义不能只用顺序调用模拟；必须有可重复的并发测试或数据库集成测试。
- Application 测试必须验证事务结果和 Outbox 副作用，而不只验证返回对象。

## 集成与数据库测试

- MySQL 字段、生成列、唯一索引、锁和隔离行为使用 MySQL 8.4/Testcontainers 或等价真实 MySQL 环境验证。
- 不使用 H2 作为 MySQL DDL、唯一约束、锁或并发语义的通过证据。
- Flyway 至少验证空库顺序迁移；已有版本变化时同时验证升级路径。
- 数据库集成测试独立准备和清理数据，不依赖开发者本机残留状态。
- 测试必须验证数据库最终行、版本、唯一性和状态，而不只验证 SQL 没有抛异常。
- 同一 Binding 多条历史终态、至多一个 PENDING、`source_wish_id` 唯一等约束必须保留真实数据库证据。

## API 与 Client 契约测试

- 公网 API 验证 HTTP 状态、业务 code、`data`、`traceId` 和防枚举语义。
- 写接口验证 `Idempotency-Key` 的首次、重复、摘要冲突和并发行为。
- 内部 reconcile 验证 `X-Idempotency-Key`、Same-Token、完整 DTO、错误解码和超时路径。
- `ReconcileReminderCommand` 必须包含 `operationType` 和 `schemaVersion`，不得包含 `cause`、`ResolutionCause`、`correction` 或 `displayName`。
- Client 序列化契约与服务端实现必须一致；不能仅分别测试两端能编译。
- Mock 下游成功只证明调用方逻辑，不等于真实 HTTP、服务发现、鉴权或序列化链路通过。

## 架构测试

Phase 0 至少使用 ArchUnit 或等价可执行检查覆盖：

- Domain 不依赖 Web、MyBatis、Controller 或 Infrastructure 实现；
- Client 不依赖 App；
- Common 不依赖业务 App、Client 或业务包；
- App 不直接依赖另一服务 App；
- Persistence DO 和 Mapper 不泄漏到 Domain、Client 或公网 Response。

依赖树审阅用于补充 ArchUnit，确认没有通过传递依赖绕过边界。

## 技术门禁

- 技术测试文档中的 P0 失败阻塞合码。
- P1 未通过时必须登记缺口、影响、负责人或后续处理方式，不能静默忽略。
- Phase 0 必须验证注册/配置、Gateway、LoadBalancer、HTTP Client、安全头、可信 TraceId 和数据库迁移。
- Reminder 必须覆盖幂等、乱序、并发首次创建、ignore/reconcile 竞争和 command log 摘要冲突。
- Outbox 必须覆盖重复投递、租约回收、重试退避、DEAD 和人工重放。
- Restore 必须覆盖窗口内恢复、超时、重复、错误 Token、版本冲突和越权。

具体阻塞用例以 [上级项目文档索引](../../docs/README.md) 登记的技术门禁终稿为准。

## Maven 基础门禁

完成实现前至少执行：

```bash
mvn validate
mvn test
mvn package
```

根据改动范围追加：

- `mvn -pl <module> -am test`；
- ArchitectureTest；
- Client/API 契约测试；
- Flyway/MySQL 集成测试；
- Gateway 和真实 HTTP 链路；
- 产品验收用例。

不得以根 Reactor 成功替代尚未执行的外部集成验证，也不得以某个模块通过推断全仓通过。

## 测试稳定性

- 测试不得依赖执行顺序、共享可变单例、固定端口或开发者本机数据。
- 随机数据需要记录 seed 或使用能定位失败的固定样本。
- 异步测试使用有界等待和状态条件，不使用任意长时间 `sleep`。
- 不通过增加重试次数掩盖竞态、时间或资源清理问题。
- 容器、线程、连接、临时目录和测试服务在结束后必须释放。

## 验证报告

交付报告至少包含：

- 执行的准确命令；
- 退出码和测试数量/模块范围；
- 关键数据库、HTTP、日志或产物证据；
- 哪些是 Mock/静态验证，哪些是真实集成；
- 未执行或未通过的门禁及原因；
- 是否存在与当前任务无关的工作区改动。

禁止只写“已测试”“应该没问题”或引用其他 Agent 的结论代替证据。
