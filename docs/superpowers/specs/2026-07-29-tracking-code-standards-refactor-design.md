# Tracking 代码规范重构设计

日期：2026-07-29

## 1. 目标

在不改变 M1 业务语义、HTTP 契约、数据库结构和跨服务契约的前提下，治理
Tracking 模块中已经确认的字符串弱类型、重复协议字面量和常量所有权问题，使非法
状态尽可能在编译期被阻止，并让持久化 code 的转换集中在基础设施边界。

本轮只处理代码规范和类型表达，不拆分现有 Service，不新增模块、依赖、中间件或
全仓格式化工具。

根据当前任务追加要求，业务 App 的 Application Service 同步统一为
`*Service` 接口与 `*ServiceImpl` 实现。该结构治理覆盖 Auth、Tracking、Reminder
三个 Servlet App，避免只在 Tracking 建立一套局部约定。

## 2. 审查发现

### 2.1 有限状态以字符串穿透领域和应用边界

以下稳定有限集合仍使用 `String`：

- Item 生命周期状态；
- Subscription、Wish 业务状态；
- Category 系统分类编码；
- Tracking 幂等操作编码；
- 幂等记录内部状态；
- Tracking Outbox 状态和事件类型。

字符串允许拼写错误和未定义值通过编译，并使 Repository 接口无法表达允许的状态
集合。现有常量只降低了部分调用点的拼写风险，没有形成类型边界。

### 2.2 既有枚举仍依赖 `name()` / `valueOf()`

`AutoRenew` 与 `BillingCycleType` 直接用枚举名称作为数据库稳定 code，且枚举值缺少
语义注释。虽然当前值与契约一致，但持久化契约与 Java 标识符被隐式绑定，不符合
“稳定 code 显式表达”的规则。

### 2.3 稳定协议字面量重复

三个 Controller 和三个恢复请求重复声明 UUID 正则；分类 ID 正则在请求与查询参数
中重复；公网幂等头名称在所有写接口散落。它们属于同一 Tracking HTTP 协议语义，
应由接口层的专用类型拥有。

### 2.4 单类实现策略未具名

幂等处理中“一分钟占用期、一天保留期”和 SHA-256 算法仍以内联字面量表达。它们
不是跨模块契约，但应保留为所属类内的具名常量。

### 2.5 Application Service 缺少接口边界

Auth、Tracking、Reminder 的公开应用服务目前大多是直接由 Controller 注入的具体
类。接口层因而依赖实现类型，且不同 App 无法通过统一架构门禁识别 Application
Service 的公开用例边界。

## 3. 设计

### 3.1 显式 code 枚举

新增或完善以下职责单一类型：

- `ItemLifecycleStatus`
- `SubscriptionStatus`
- `WishStatus`
- `CategorySystemCode`
- `TrackingOperation`
- `IdempotencyRecordStatus`
- `OutboxStatus`
- `OutboxEventType`
- `AutoRenew`
- `BillingCycleType`

每个枚举显式保存稳定 `code`，提供 `code()`；需要从数据库读取的类型提供
`fromCode(String)`，未知值立即抛出包含类型与原始值的中文异常。禁止使用 ordinal，
也不依赖 `name()` 作为持久化契约。

Domain Model 和 Repository 端口使用枚举；DO、Mapper 参数和 HTTP Response 继续使用
权威文档冻结的字符串。转换只发生在 Infrastructure 或 Interfaces 边界，因此不会
改变表字段、SQL、JSON 或 OpenAPI 机器值。

幂等操作枚举属于 `idempotency.application`，因为它约束所有 Tracking 写用例与幂等
存储之间的应用协议。幂等记录状态只由两个 MyBatis Store 使用，归
`idempotency.infrastructure` 所有，不向业务用例暴露。

### 3.2 REST 协议常量

- `UuidFormat` 拥有 RFC 4122 兼容 UUID 正则和显式校验方法；
- `PositiveLongIdParser` 同时拥有正 long 文本正则和解析逻辑；
- `TrackingHeaderNames` 只拥有 Tracking 公网协议请求头名称。

不创建宽泛的 `Constants` 或 `Enums` 容器。注解继续引用编译期常量，Controller 的
显式缺失校验继续保留现有错误语义。

### 3.3 不纳入本轮的候选项

- `CNY` 不改成枚举：接口允许任意三位币种代码，枚举会错误收窄契约。仅在各自
  业务判断中保留具名常量，后续若需要统一币种校验，应单独设计值对象。
- 不为了减少行数拆分三个 Application Service：类体量问题需要按用例和事务边界
  单独设计，不能混入纯类型重构。
- 测试夹具中的数据库原始字符串保留：集成测试需要证明落库 code 未改变。

### 3.4 Application Service 接口与实现

三个业务 App 中位于 `application` 包、以 `Service` 结尾的公开用例类型统一调整为：

- `*Service`：只声明 Controller、Scheduler 或其他应用服务可调用的公开用例；
- `*ServiceImpl`：承载现有事务、幂等、领域协调和 Repository 调用，并使用
  `@Service` 注册；
- 调用方只依赖 `*Service`，单元测试直接构造 `*ServiceImpl` 验证编排；
- Service 接口和主要方法保留中文 Javadoc，明确用例、副作用和边界。

这项约定不增加一层空转委托，也不改变 Repository、Domain 或 Infrastructure
职责。通过 Common Test 中的共享 ArchUnit 规则，三个 App 都必须满足 Service 为
接口、ServiceImpl 实现同包同名 Service 的约束。

## 4. 兼容性与风险

- API、数据库和 Outbox payload 的 code 保持逐字一致；
- MyBatis DO 继续使用 `String`，不依赖全局 TypeHandler；
- 幂等历史响应的 Application Result 继续使用字符串，避免改变 Jackson 重放格式；
- 未知数据库 code 将从“在业务分支中静默不匹配”改为“边界处快速失败”，有利于
  暴露数据不变量破坏；
- 本轮不需要数据迁移和回滚脚本，回滚代码提交即可恢复旧类型表达。

## 5. TDD 与验证

先新增枚举 code、反向解析、未知 code 拒绝，以及共享格式常量测试，并确认目标类型
缺失时编译 RED。实现后执行：

1. 新增枚举与格式单元测试；
2. Application Service 接口/实现 ArchUnit RED 与三个 App 架构测试；
3. Item、Subscription、Wish、Category、Idempotency、Outbox 相关测试；
4. Auth、Tracking、Reminder 模块全量 `verify`；
5. 全仓 `clean verify`；
6. `git diff --check` 和 GitHub CI。

真实 MySQL 集成测试继续验证持久化 code、恢复、幂等、Outbox 和并发行为未发生变化。
