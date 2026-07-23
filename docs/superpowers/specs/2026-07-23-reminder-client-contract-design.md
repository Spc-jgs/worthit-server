# WorthIt Reminder Client 契约设计

**日期：** 2026-07-23
**状态：** 待项目负责人书面确认
**适用模块：** `worthit-reminder/worthit-reminder-client`

## 1. 目标

为 Tracking → Reminder 的内部 reconcile 调用建立纯契约 Client，冻结：

- Spring HTTP Interface 路径和幂等请求头；
- `ReconcileReminderCommand` 完整期望状态；
- `ReconcileReminderResponse` 结果结构；
- Reminder 业务类型、提醒类型、操作类型和处理结果枚举；
- 字段级与跨字段 Bean Validation；
- `TECH-CLI-001` 和真实 `TECH-ARCH-002` 门禁。

本轮不创建调用代理、自动配置、Controller、Application Service、数据库实现或运行时安全配置。

## 2. 权威输入

实现以以下终稿为输入：

- 架构：`../docs/后端架构文档/值不值小程序_微服务技术方案_V0.3.15.md`
- 接口：`../docs/接口文档/值不值小程序_接口设计_V0.1.2.md`
- 技术门禁：`../docs/测试文档/值不值小程序_技术测试与质量门禁_V0.2.2.md`

发生冲突时按仓库 `AGENTS.md` 的权威关系与架构变更流程处理，不静默发明字段或放宽契约。

## 3. 方案选择

### 3.1 方案 A：仅字段级约束

只使用 `@Positive`、`@NotNull`、`@NotBlank`、`@Min` 和 `@Max`。

优点是依赖和实现最少；缺点是业务类型、提醒类型、日期和操作类型可以形成互相矛盾的命令，不能充分承担跨服务契约职责。

### 3.2 方案 B：Record 构造器强校验

在 compact constructor 中直接抛出 `IllegalArgumentException`。

优点是无效对象无法创建；缺点是 Jackson 反序列化失败会变成构造异常，不利于统一生成字段级校验详情，也混淆对象构造和接口校验语义。

### 3.3 选定方案：字段约束 + 自定义类级 Bean Validation

字段约束处理单字段必填和数值边界；`@ValidReconcileReminderCommand` 处理跨字段契约不变量，并将错误挂到具体字段路径。

该方案让 Client 能拒绝已知无效的跨服务命令，同时不把 Reminder 的状态迁移、锁、幂等和实例协调算法放进 Client。

## 4. 代码结构

```text
com.shaopc.worthit.reminder.client
├── api
│   └── ReminderCommandClient
├── command
│   └── ReconcileReminderCommand
├── response
│   └── ReconcileReminderResponse
├── model
│   ├── ReminderBusinessType
│   ├── ReminderType
│   ├── ReminderOperationType
│   └── ReconcileResultCode
├── validation
│   ├── ValidReconcileReminderCommand
│   └── ReconcileReminderCommandValidator
└── contract
    └── ReminderClientContract
```

每个公开类型、公开方法、常量、枚举和枚举值均按 `rules/20-java-code-style.md` 编写中文 Javadoc。

## 5. HTTP Interface

`ReminderCommandClient` 使用 Spring Framework 6.2 HTTP Interface：

```java
@HttpExchange(ReminderClientContract.BASE_PATH)
public interface ReminderCommandClient {

    @PostExchange(ReminderClientContract.RECONCILE_PATH)
    ReconcileReminderResponse reconcile(
            @NotBlank(message = "幂等键不能为空")
            @RequestHeader(ReminderClientContract.IDEMPOTENCY_HEADER)
            String eventId,
            @Valid @RequestBody ReconcileReminderCommand command);
}
```

稳定常量：

| 常量 | 值 | 用途 |
| --- | --- | --- |
| `BASE_PATH` | `/internal/v1/reminders` | Client 公共内部路径 |
| `RECONCILE_PATH` | `/reconcile` | reconcile 路径 |
| `IDEMPOTENCY_HEADER` | `X-Idempotency-Key` | Outbox eventId 幂等头 |
| `SCHEMA_VERSION` | `1` | M1 契约版本 |

接口只声明 HTTP 契约，不创建代理。Tracking 后续在自身 Infrastructure 中通过 `RestClientAdapter` 与 `HttpServiceProxyFactory` 创建代理；Same-Token、TraceId、调用方标识、超时、LoadBalancer、错误解码和指标由 `common-http` 与调用方配置负责。

## 6. 命令契约

`ReconcileReminderCommand` 字段顺序和类型固定为：

```java
public record ReconcileReminderCommand(
        long userId,
        ReminderBusinessType businessType,
        long businessId,
        ReminderType reminderType,
        long sourceVersion,
        LocalDate businessDate,
        LocalDateTime remindAt,
        boolean reminderEnabled,
        String businessStatusCode,
        ReminderOperationType operationType,
        int schemaVersion) {
}
```

字段级约束：

| 字段 | 约束 |
| --- | --- |
| `userId` | `@Positive` |
| `businessType` | `@NotNull` |
| `businessId` | `@Positive` |
| `reminderType` | `@NotNull` |
| `sourceVersion` | `@Positive` |
| `businessDate` | 可空，由类级约束处理 |
| `remindAt` | 可空，由类级约束处理 |
| `reminderEnabled` | 必填 primitive |
| `businessStatusCode` | `@NotBlank` |
| `operationType` | `@NotNull` |
| `schemaVersion` | `@Min(1)` + `@Max(1)` |

所有校验消息使用中文。JSON 字段名和枚举值保持接口终稿冻结的英文机器契约。

命令不得出现：

- `cause`
- `resolutionCause` / `ResolutionCause`
- `reconcileCause`
- `correction`
- `displayName`

## 7. 枚举契约

### 7.1 `ReminderBusinessType`

- `ITEM`
- `SUBSCRIPTION`
- `WISH`

### 7.2 `ReminderType`

- `RENEWAL`
- `WARRANTY`
- `WATCH`

### 7.3 `ReminderOperationType`

- `INITIAL_SYNC`
- `ENABLE_REMINDER`
- `DISABLE_REMINDER`
- `UPDATE_BUSINESS_DATE`
- `ADVANCE_NEXT_RENEWAL_DATE`
- `CORRECT_BUSINESS_DATE`
- `PAUSE_SUBSCRIPTION`
- `END_SUBSCRIPTION`
- `RESUME_SUBSCRIPTION`
- `PURCHASE_WISH`
- `ABANDON_WISH`
- `CONTINUE_CONSIDERING`
- `DISPOSE_ITEM`
- `DELETE_OBJECT`

### 7.4 `ReconcileResultCode`

- `APPLIED`
- `IGNORED_OLD`

禁止增加 `IDEMPOTENT` 或 `CONFLICT` 成功结果码。幂等重放由响应中的 `idempotent=true` 表达；契约冲突使用统一错误响应。

## 8. 类级契约校验

`@ValidReconcileReminderCommand` 由 `ReconcileReminderCommandValidator` 实现。

### 8.1 业务类型与提醒类型

只允许：

| 业务类型 | 提醒类型 |
| --- | --- |
| `ITEM` | `WARRANTY` |
| `SUBSCRIPTION` | `RENEWAL` |
| `WISH` | `WATCH` |

不匹配时将错误挂到 `reminderType`。

### 8.2 日期完整性

- `reminderEnabled=true` 时，`businessDate` 和 `remindAt` 均不能为空。
- `businessDate=null` 时，必须同时满足 `reminderEnabled=false`、`remindAt=null`。
- `businessDate` 非空、`reminderEnabled=false` 时，允许 `remindAt` 为空；如果提供 `remindAt`，仍必须符合提醒时间规则。
- 类级校验跳过已由字段级约束报告的空业务类型或空提醒类型，避免重复错误。

### 8.3 提醒时间规则

提供 `businessDate` 和 `remindAt` 时必须满足：

| 提醒类型 | `remindAt` |
| --- | --- |
| `RENEWAL` | `businessDate.minusDays(1).atStartOfDay()` |
| `WARRANTY` | `businessDate.minusDays(7).atStartOfDay()` |
| `WATCH` | `businessDate.atStartOfDay()` |

不匹配时将错误挂到 `remindAt`。

### 8.4 操作类型适用范围

所有业务类型均允许：

- `INITIAL_SYNC`
- `ENABLE_REMINDER`
- `DISABLE_REMINDER`
- `UPDATE_BUSINESS_DATE`
- `CORRECT_BUSINESS_DATE`
- `DELETE_OBJECT`

仅 `SUBSCRIPTION`：

- `ADVANCE_NEXT_RENEWAL_DATE`
- `PAUSE_SUBSCRIPTION`
- `END_SUBSCRIPTION`
- `RESUME_SUBSCRIPTION`

仅 `WISH`：

- `PURCHASE_WISH`
- `ABANDON_WISH`
- `CONTINUE_CONSIDERING`

仅 `ITEM`：

- `DISPOSE_ITEM`

不匹配时将错误挂到 `operationType`。

## 9. 不进入 Validator 的业务逻辑

以下内容留在 Reminder Application/Domain：

- Binding upsert、`FOR UPDATE` 和事务顺序；
- eventId、sourceVersion、payload digest 幂等与冲突处理；
- 乱序命令的 `IGNORED_OLD` 收敛；
- 旧 PENDING 的 PROCESSED/CANCELED 归档；
- 新 PENDING 创建和唯一约束冲突处理；
- `operationType → ResolutionCause` 内部映射；
- 是否已到达、是否为解析型业务动作和最终状态判定；
- `businessStatusCode` 对应的 Tracking 领域状态机。

Client 只判断命令是否满足双方冻结的输入契约，不执行命令。

## 10. 响应契约

```java
public record ReconcileReminderResponse(
        boolean applied,
        ReconcileResultCode resultCode,
        boolean idempotent,
        long bindingId,
        long lastSourceVersion) {
}
```

本轮只冻结类型和 JSON 结构，不在响应 record 中复制 Reminder 的结果一致性判断。服务端产生响应时由 Application 测试验证 `applied`、`resultCode`、`idempotent` 和版本的组合语义。

## 11. Maven 依赖

生产依赖：

- `org.springframework:spring-web`
- `jakarta.validation:jakarta.validation-api`

测试依赖：

- `com.fasterxml.jackson.core:jackson-databind`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`
- `org.hibernate.validator:hibernate-validator`
- `org.junit.jupiter:junit-jupiter`
- `org.assertj:assertj-core`
- `com.shaopc.worthit:worthit-common-test`

测试中 Hibernate Validator 使用 `ParameterMessageInterpolator`，不为测试额外引入表达式语言运行时。

禁止依赖：

- `worthit-reminder-app`
- 任何其他 App
- MyBatis、数据库、Spring Boot Starter
- `common-http` 运行配置
- 自动配置与供应商 SDK

## 12. 测试与门禁

### 12.1 JSON 契约

- 序列化完整命令并按 JSON Tree 断言字段集合和枚举值；
- 断言 `schemaVersion=1`；
- 断言不存在禁止字段；
- 序列化响应并断言 `resultCode` 仅为 `APPLIED` 或 `IGNORED_OLD`；
- 使用 Java Time Module 验证日期和时间格式。

JSON 对象键顺序不作为摘要或兼容性依据，符合 ADR-043。

### 12.2 Bean Validation

- 合法完整命令无 violation；
- 单字段约束分别覆盖 ID、版本、状态码、必填枚举和 schemaVersion；
- 业务类型与提醒类型三组合法、交叉组合非法；
- 开启提醒时日期完整；
- 无业务日时关闭提醒且无提醒时间；
- 三类提醒时间偏移准确；
- operationType 适用范围完整覆盖。

### 12.3 HTTP Interface

通过反射断言：

- 类型级 `@HttpExchange` 路径；
- 方法级 `@PostExchange` 路径；
- `X-Idempotency-Key` 请求头；
- 方法参数包含 `@Valid` 和 `@RequestBody`。

### 12.4 架构门禁

新增 `ReminderClientArchitectureTest`，扫描真实 `com.shaopc.worthit.reminder.client..` 生产类并复用：

```java
WorthItArchitectureRules.CLIENT_MUST_NOT_DEPEND_ON_IMPLEMENTATION
```

同时断言扫描结果非空，使 `TECH-ARCH-002` 成为真实门禁。

### 12.5 全量验收

至少执行：

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am test
mvn validate
mvn test
mvn package
mvn -pl worthit-reminder/worthit-reminder-client -am package dependency:tree
git diff --check
```

## 13. 完成标准

- `TECH-CLI-001` 有真实序列化测试；
- `TECH-ARCH-002` 扫描真实 Client 生产类并通过；
- 枚举全集、字段集合和 schemaVersion 与终稿一致；
- 类级 Validator 拒绝冻结的跨字段非法组合；
- Client 无 App、数据库、运行配置或自动配置依赖；
- 所有新增公共契约均具有中文 Javadoc；
- 全仓 16 模块 Maven 门禁通过；
- 本轮实现未扩大到 Controller、代理工厂或业务执行。
