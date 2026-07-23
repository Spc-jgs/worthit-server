# WorthIt Reminder Client Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `worthit-reminder-client` 中实现 Tracking → Reminder reconcile 的稳定内部 HTTP 契约，并用 JSON、Bean Validation、反射和 ArchUnit 测试冻结终稿要求。

**Architecture:** Client 是 Reminder 所有的纯契约 JAR，只包含 HTTP Interface、Command、Response、契约枚举、协议常量和输入结构校验。字段级约束负责单字段边界，自定义类级约束负责已冻结的跨字段不变量；幂等、乱序收敛、状态迁移、实例协调和代理创建仍归调用方 Infrastructure 或 Reminder Application/Domain。

**Tech Stack:** Java 17、Maven、Spring Framework 6.2 HTTP Interface、Jakarta Validation 3.0.2、Hibernate Validator、Jackson、JUnit 5、AssertJ、ArchUnit 1.4.2

## Global Constraints

- 直接在当前 `main` 工作区实施，不创建分支或 worktree。
- 仅修改 `worthit-reminder/worthit-reminder-client`；不修改上级 `docs/`、其他 App、Common、数据库或前端。
- 不创建 HTTP 代理工厂、自动配置、Controller、Application Service、Domain、Repository、Mapper 或 Flyway。
- `X-Idempotency-Key` 的值是 Outbox `eventId`；Client 不生成、转换或持久化该值。
- `schemaVersion` 在 M1 精确等于 `ReminderClientContract.SCHEMA_VERSION`，当前为 `1`。
- 命令不得出现 `cause`、`resolutionCause`、`reconcileCause`、`correction` 或 `displayName`。
- 所有公共类型、主要公开方法、契约字段、常量、枚举类型和枚举值均写中文 Javadoc。
- Java 中面向人阅读的异常消息和校验消息使用中文；HTTP 头、路径、JSON 字段和枚举值保持冻结的英文机器契约。
- 每个行为按 RED → GREEN → REFACTOR 实施；必须先观察预期失败，才能添加对应生产实现。
- 当前执行不 commit、不 push；实现完成后保留可审阅工作区差异，等待用户明确 Git 指令。

---

### Task 1: 建立 Client 最小 Maven 依赖

**Files:**
- Modify: `worthit-reminder/worthit-reminder-client/pom.xml`

**Interfaces:**
- Consumes: Spring Boot BOM 管理的 Spring Web、Jakarta Validation、Jackson、Hibernate Validator、JUnit 5 和 AssertJ 版本。
- Consumes: 根 POM 管理的 ArchUnit `1.4.2` 与内部模块版本。
- Produces: 无 Starter、无 App、无数据库依赖的纯契约 JAR 依赖图。

- [x] **Step 1: 执行依赖 RED 检查**

Run:

```bash
if rg -n "spring-web|jakarta.validation-api" \
  worthit-reminder/worthit-reminder-client/pom.xml; then
  exit 1
else
  echo "RED confirmed: production dependencies are absent"
fi
```

Expected: 输出 RED 确认证据，证明目标生产依赖当前尚未声明。

- [x] **Step 2: 声明精确依赖**

将模块 POM 改为：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.shaopc.worthit</groupId>
        <artifactId>worthit-server</artifactId>
        <version>${revision}</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>worthit-reminder-client</artifactId>
    <name>WorthIt Reminder Client</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.hibernate.validator</groupId>
            <artifactId>hibernate-validator</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.shaopc.worthit</groupId>
            <artifactId>worthit-common-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

`archunit-junit5` 必须由消费模块显式声明；`common-test` 对它的 test-scope 依赖不会传递。

- [x] **Step 3: 验证配置 GREEN**

Run:

```bash
xmllint --noout worthit-reminder/worthit-reminder-client/pom.xml
mvn -pl worthit-reminder/worthit-reminder-client -am validate
mvn -pl worthit-reminder/worthit-reminder-client -am \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree \
  -Dscope=compile -Dincludes=com.shaopc.worthit
```

Expected: XML 有效、Reactor `BUILD SUCCESS`，生产 compile 依赖中没有任何 WorthIt App。

使用显式 Maven Dependency Plugin 3.8.1 是因为仓库默认解析到的 2.8 不能在报告目标中正确消费 Reactor 内尚未安装的快照模块。

---

### Task 2: 冻结协议常量和枚举全集

**Files:**
- Create: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/model/ReminderClientModelContractTest.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/contract/ReminderClientContract.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/model/ReminderBusinessType.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/model/ReminderType.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/model/ReminderOperationType.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/model/ReconcileResultCode.java`

**Interfaces:**
- Produces: `ReminderClientContract.BASE_PATH`
- Produces: `ReminderClientContract.RECONCILE_PATH`
- Produces: `ReminderClientContract.IDEMPOTENCY_HEADER`
- Produces: `ReminderClientContract.SCHEMA_VERSION`
- Produces: 四个稳定契约枚举及其精确全集。

- [x] **Step 1: 先写模型契约测试**

```java
package com.shaopc.worthit.reminder.client.model;

import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderClientModelContractTest {

    @Test
    void shouldExposeFrozenProtocolConstants() {
        assertThat(ReminderClientContract.BASE_PATH).isEqualTo("/internal/v1/reminders");
        assertThat(ReminderClientContract.RECONCILE_PATH).isEqualTo("/reconcile");
        assertThat(ReminderClientContract.IDEMPOTENCY_HEADER).isEqualTo("X-Idempotency-Key");
        assertThat(ReminderClientContract.SCHEMA_VERSION).isEqualTo(1);
    }

    @Test
    void shouldExposeFrozenEnumValuesOnly() {
        assertThat(ReminderBusinessType.values())
                .extracting(Enum::name)
                .containsExactly("ITEM", "SUBSCRIPTION", "WISH");
        assertThat(ReminderType.values())
                .extracting(Enum::name)
                .containsExactly("RENEWAL", "WARRANTY", "WATCH");
        assertThat(ReminderOperationType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "INITIAL_SYNC",
                        "ENABLE_REMINDER",
                        "DISABLE_REMINDER",
                        "UPDATE_BUSINESS_DATE",
                        "ADVANCE_NEXT_RENEWAL_DATE",
                        "CORRECT_BUSINESS_DATE",
                        "PAUSE_SUBSCRIPTION",
                        "END_SUBSCRIPTION",
                        "RESUME_SUBSCRIPTION",
                        "PURCHASE_WISH",
                        "ABANDON_WISH",
                        "CONTINUE_CONSIDERING",
                        "DISPOSE_ITEM",
                        "DELETE_OBJECT");
        assertThat(ReconcileResultCode.values())
                .extracting(Enum::name)
                .containsExactly("APPLIED", "IGNORED_OLD");
    }
}
```

- [x] **Step 2: 运行测试确认 RED**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReminderClientModelContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compile 因常量类和枚举不存在而失败。

- [x] **Step 3: 实现协议常量**

```java
package com.shaopc.worthit.reminder.client.contract;

/**
 * Reminder 内部 Client 的稳定协议常量。
 *
 * <p>常量由接口终稿 V0.1.2 冻结，调用方不得自行拼接路径或改写请求头。</p>
 */
public final class ReminderClientContract {

    /** Reminder 内部接口公共路径。 */
    public static final String BASE_PATH = "/internal/v1/reminders";

    /** Reminder reconcile 接口相对路径。 */
    public static final String RECONCILE_PATH = "/reconcile";

    /** Outbox 事件标识使用的内部幂等请求头。 */
    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

    /** M1 Reminder reconcile 契约版本。 */
    public static final int SCHEMA_VERSION = 1;

    private ReminderClientContract() {
    }
}
```

- [x] **Step 4: 实现枚举**

每个枚举按下列精确值实现，并为枚举类型和每个枚举值添加中文 Javadoc：

```java
public enum ReminderBusinessType {
    /** 物品。 */
    ITEM,
    /** 订阅。 */
    SUBSCRIPTION,
    /** 想买。 */
    WISH
}
```

```java
public enum ReminderType {
    /** 续费提醒。 */
    RENEWAL,
    /** 保修到期提醒。 */
    WARRANTY,
    /** 想买关注提醒。 */
    WATCH
}
```

```java
public enum ReminderOperationType {
    /** 首次同步完整期望状态。 */
    INITIAL_SYNC,
    /** 开启提醒。 */
    ENABLE_REMINDER,
    /** 关闭提醒。 */
    DISABLE_REMINDER,
    /** 更新业务日期。 */
    UPDATE_BUSINESS_DATE,
    /** 推进下一续费日期。 */
    ADVANCE_NEXT_RENEWAL_DATE,
    /** 修正业务日期。 */
    CORRECT_BUSINESS_DATE,
    /** 暂停订阅。 */
    PAUSE_SUBSCRIPTION,
    /** 结束订阅。 */
    END_SUBSCRIPTION,
    /** 恢复订阅。 */
    RESUME_SUBSCRIPTION,
    /** 购买想买对象。 */
    PURCHASE_WISH,
    /** 放弃想买对象。 */
    ABANDON_WISH,
    /** 继续考虑想买对象。 */
    CONTINUE_CONSIDERING,
    /** 处置物品。 */
    DISPOSE_ITEM,
    /** 删除业务对象。 */
    DELETE_OBJECT
}
```

```java
public enum ReconcileResultCode {
    /** 命令已应用。 */
    APPLIED,
    /** 命令来源版本过旧，已忽略。 */
    IGNORED_OLD
}
```

枚举文件分别声明对应 package；不得增加 code 字段、别名或额外枚举值。

- [x] **Step 5: 运行测试确认 GREEN**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReminderClientModelContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `ReminderClientModelContractTest` 全部通过。

---

### Task 3: 实现 Command JSON 与字段级校验

**Files:**
- Create: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/command/ReconcileReminderCommandJsonTest.java`
- Create: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/command/ReconcileReminderCommandFieldValidationTest.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/command/ReconcileReminderCommand.java`

**Interfaces:**
- Produces: `ReconcileReminderCommand` 十一个字段的稳定 Java 与 JSON 契约。
- Produces: ID、必填枚举、状态码与 `schemaVersion` 字段级 Bean Validation。

- [x] **Step 1: 先写 JSON 契约测试**

测试创建以下固定命令：

```java
private static ReconcileReminderCommand command() {
    return new ReconcileReminderCommand(
            1001L,
            ReminderBusinessType.SUBSCRIPTION,
            2001L,
            ReminderType.RENEWAL,
            3L,
            LocalDate.of(2026, 8, 1),
            LocalDateTime.of(2026, 7, 31, 0, 0),
            true,
            "ACTIVE",
            ReminderOperationType.UPDATE_BUSINESS_DATE,
            ReminderClientContract.SCHEMA_VERSION);
}
```

使用：

```java
ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
JsonNode json = objectMapper.valueToTree(command());
```

断言字段集合精确等于：

```text
userId
businessType
businessId
reminderType
sourceVersion
businessDate
remindAt
reminderEnabled
businessStatusCode
operationType
schemaVersion
```

并断言：

```java
assertThat(json.path("businessType").asText()).isEqualTo("SUBSCRIPTION");
assertThat(json.path("reminderType").asText()).isEqualTo("RENEWAL");
assertThat(json.path("operationType").asText()).isEqualTo("UPDATE_BUSINESS_DATE");
assertThat(json.path("businessDate").asText()).isEqualTo("2026-08-01");
assertThat(json.path("remindAt").asText()).isEqualTo("2026-07-31T00:00:00");
assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
assertThat(json.has("cause")).isFalse();
assertThat(json.has("resolutionCause")).isFalse();
assertThat(json.has("reconcileCause")).isFalse();
assertThat(json.has("correction")).isFalse();
assertThat(json.has("displayName")).isFalse();
```

- [x] **Step 2: 先写字段校验测试**

用 Hibernate Validator 创建不需要 EL 的校验器：

```java
ValidatorFactory factory = Validation.byDefaultProvider()
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .buildValidatorFactory();
Validator validator = factory.getValidator();
```

分别创建以下非法命令并断言唯一目标字段存在 violation：

| 修改 | violation 字段 |
| --- | --- |
| `userId=0` | `userId` |
| `businessType=null` | `businessType` |
| `businessId=0` | `businessId` |
| `reminderType=null` | `reminderType` |
| `sourceVersion=0` | `sourceVersion` |
| `businessStatusCode=" "` | `businessStatusCode` |
| `operationType=null` | `operationType` |
| `schemaVersion=0` | `schemaVersion` |
| `schemaVersion=2` | `schemaVersion` |

测试类使用 `@AfterAll` 关闭 `ValidatorFactory`，并断言合法固定命令没有 violation。

- [x] **Step 3: 运行测试确认 RED**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReconcileReminderCommandJsonTest,ReconcileReminderCommandFieldValidationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compile 因 `ReconcileReminderCommand` 不存在而失败。

- [x] **Step 4: 实现 Command**

```java
package com.shaopc.worthit.reminder.client.command;

import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracking 发往 Reminder 的完整期望状态命令。
 *
 * @param userId 用户标识
 * @param businessType 业务对象类型
 * @param businessId 业务对象标识
 * @param reminderType 提醒类型
 * @param sourceVersion Tracking 业务对象来源版本
 * @param businessDate 提醒计算使用的业务日期，可空
 * @param remindAt 计算后的无时区提醒时间，可空
 * @param reminderEnabled 是否期望启用提醒
 * @param businessStatusCode Tracking 业务状态码
 * @param operationType 服务端生成的业务操作类型
 * @param schemaVersion reconcile 契约版本，M1 固定为 1
 */
public record ReconcileReminderCommand(
        @Positive(message = "用户标识必须大于0")
        long userId,
        @NotNull(message = "业务类型不能为空")
        ReminderBusinessType businessType,
        @Positive(message = "业务对象标识必须大于0")
        long businessId,
        @NotNull(message = "提醒类型不能为空")
        ReminderType reminderType,
        @Positive(message = "来源版本必须大于0")
        long sourceVersion,
        LocalDate businessDate,
        LocalDateTime remindAt,
        boolean reminderEnabled,
        @NotBlank(message = "业务状态码不能为空")
        String businessStatusCode,
        @NotNull(message = "操作类型不能为空")
        ReminderOperationType operationType,
        @Min(value = ReminderClientContract.SCHEMA_VERSION, message = "契约版本必须为1")
        @Max(value = ReminderClientContract.SCHEMA_VERSION, message = "契约版本必须为1")
        int schemaVersion) {
}
```

- [x] **Step 5: 运行测试确认 GREEN**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReconcileReminderCommandJsonTest,ReconcileReminderCommandFieldValidationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: JSON 字段集合、禁止字段、Java Time 格式和全部字段级约束通过。

---

### Task 4: 实现跨字段契约校验

**Files:**
- Create: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/validation/ReconcileReminderCommandValidatorTest.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/validation/ValidReconcileReminderCommand.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/validation/ReconcileReminderCommandValidator.java`
- Modify: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/command/ReconcileReminderCommand.java`

**Interfaces:**
- Produces: `@ValidReconcileReminderCommand`
- Enforces: 业务类型/提醒类型、日期完整性、提醒时间偏移、操作类型适用范围。

- [x] **Step 1: 先写跨字段校验测试**

测试至少覆盖并断言 violation property path：

| 场景 | 预期 |
| --- | --- |
| `ITEM/WARRANTY`、`SUBSCRIPTION/RENEWAL`、`WISH/WATCH` | 合法 |
| 三种业务类型与另外两种提醒类型交叉 | `reminderType` 非法 |
| `reminderEnabled=true,businessDate=null` | `businessDate` 非法 |
| `reminderEnabled=true,remindAt=null` | `remindAt` 非法 |
| `businessDate=null,reminderEnabled=false,remindAt=null` | 合法 |
| `businessDate=null,reminderEnabled=false,remindAt!=null` | `remindAt` 非法 |
| 关闭提醒但业务日存在且 `remindAt=null` | 合法 |
| `RENEWAL=业务日前1日00:00` | 合法 |
| `WARRANTY=业务日前7日00:00` | 合法 |
| `WATCH=业务日00:00` | 合法 |
| 三种提醒时间偏移任意差一分钟 | `remindAt` 非法 |
| 六种通用操作用于任意业务类型 | 合法 |
| 四种 Subscription 专属操作用于非 Subscription | `operationType` 非法 |
| 三种 Wish 专属操作用于非 Wish | `operationType` 非法 |
| `DISPOSE_ITEM` 用于非 Item | `operationType` 非法 |

六种通用操作精确为：

```java
EnumSet.of(
        ReminderOperationType.INITIAL_SYNC,
        ReminderOperationType.ENABLE_REMINDER,
        ReminderOperationType.DISABLE_REMINDER,
        ReminderOperationType.UPDATE_BUSINESS_DATE,
        ReminderOperationType.CORRECT_BUSINESS_DATE,
        ReminderOperationType.DELETE_OBJECT)
```

不得在测试中复制生产校验器的 switch；使用固定输入/预期表驱动断言。

- [x] **Step 2: 运行测试确认 RED**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReconcileReminderCommandValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 当前 Command 不会产生预期的类级 violation，测试失败。

- [x] **Step 3: 实现类级约束注解**

```java
package com.shaopc.worthit.reminder.client.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验 Reminder reconcile 命令的跨字段契约不变量。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = ReconcileReminderCommandValidator.class)
public @interface ValidReconcileReminderCommand {

    /**
     * 默认校验消息。
     *
     * @return 默认校验消息
     */
    String message() default "提醒协调命令不符合契约";

    /**
     * Bean Validation 分组。
     *
     * @return 校验分组
     */
    Class<?>[] groups() default {};

    /**
     * Bean Validation 负载。
     *
     * @return 校验负载
     */
    Class<? extends Payload>[] payload() default {};
}
```

- [x] **Step 4: 实现校验器**

```java
package com.shaopc.worthit.reminder.client.validation;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Reminder reconcile 命令跨字段契约校验器。
 */
public final class ReconcileReminderCommandValidator
        implements ConstraintValidator<ValidReconcileReminderCommand, ReconcileReminderCommand> {

    private static final Set<ReminderOperationType> COMMON_OPERATIONS = EnumSet.of(
            ReminderOperationType.INITIAL_SYNC,
            ReminderOperationType.ENABLE_REMINDER,
            ReminderOperationType.DISABLE_REMINDER,
            ReminderOperationType.UPDATE_BUSINESS_DATE,
            ReminderOperationType.CORRECT_BUSINESS_DATE,
            ReminderOperationType.DELETE_OBJECT);

    /**
     * 校验业务类型、提醒类型、日期和操作类型是否形成合法契约。
     *
     * @param command 待校验命令
     * @param context 校验上下文
     * @return 命令满足跨字段契约时返回 {@code true}
     */
    @Override
    public boolean isValid(
            ReconcileReminderCommand command,
            ConstraintValidatorContext context) {
        if (command == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (command.businessType() != null
                && command.reminderType() != null
                && !isReminderTypeSupported(command.businessType(), command.reminderType())) {
            addViolation(context, "提醒类型与业务类型不匹配", "reminderType");
            valid = false;
        }

        if (command.reminderEnabled() && command.businessDate() == null) {
            addViolation(context, "提醒开启时业务日期不能为空", "businessDate");
            valid = false;
        }
        if (command.reminderEnabled() && command.remindAt() == null) {
            addViolation(context, "提醒开启时提醒时间不能为空", "remindAt");
            valid = false;
        }
        if (command.businessDate() == null
                && !command.reminderEnabled()
                && command.remindAt() != null) {
            addViolation(context, "业务日期为空时提醒时间必须为空", "remindAt");
            valid = false;
        }

        if (command.businessDate() != null
                && command.remindAt() != null
                && command.reminderType() != null
                && !expectedRemindAt(command).equals(command.remindAt())) {
            addViolation(context, "提醒时间不符合提醒类型规则", "remindAt");
            valid = false;
        }

        if (command.businessType() != null
                && command.operationType() != null
                && !isOperationSupported(command.businessType(), command.operationType())) {
            addViolation(context, "操作类型不适用于当前业务类型", "operationType");
            valid = false;
        }
        return valid;
    }

    private static boolean isReminderTypeSupported(
            ReminderBusinessType businessType,
            ReminderType reminderType) {
        return switch (businessType) {
            case ITEM -> reminderType == ReminderType.WARRANTY;
            case SUBSCRIPTION -> reminderType == ReminderType.RENEWAL;
            case WISH -> reminderType == ReminderType.WATCH;
        };
    }

    private static LocalDateTime expectedRemindAt(ReconcileReminderCommand command) {
        return switch (command.reminderType()) {
            case RENEWAL -> command.businessDate().minusDays(1).atStartOfDay();
            case WARRANTY -> command.businessDate().minusDays(7).atStartOfDay();
            case WATCH -> command.businessDate().atStartOfDay();
        };
    }

    private static boolean isOperationSupported(
            ReminderBusinessType businessType,
            ReminderOperationType operationType) {
        if (COMMON_OPERATIONS.contains(operationType)) {
            return true;
        }
        return switch (businessType) {
            case ITEM -> operationType == ReminderOperationType.DISPOSE_ITEM;
            case SUBSCRIPTION -> EnumSet.of(
                    ReminderOperationType.ADVANCE_NEXT_RENEWAL_DATE,
                    ReminderOperationType.PAUSE_SUBSCRIPTION,
                    ReminderOperationType.END_SUBSCRIPTION,
                    ReminderOperationType.RESUME_SUBSCRIPTION).contains(operationType);
            case WISH -> EnumSet.of(
                    ReminderOperationType.PURCHASE_WISH,
                    ReminderOperationType.ABANDON_WISH,
                    ReminderOperationType.CONTINUE_CONSIDERING).contains(operationType);
        };
    }

    private static void addViolation(
            ConstraintValidatorContext context,
            String message,
            String property) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
```

REFACTOR 时可将专属操作集提取为 `private static final` 集合，避免每次校验创建 `EnumSet`；这些集合只属于该校验器实现细节，按规则保留在类内。

- [x] **Step 5: 将类级约束添加到 Command**

在 record 声明前添加：

```java
@ValidReconcileReminderCommand
public record ReconcileReminderCommand(
```

并导入 `com.shaopc.worthit.reminder.client.validation.ValidReconcileReminderCommand`。

- [x] **Step 6: 运行测试确认 GREEN**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReconcileReminderCommandFieldValidationTest,ReconcileReminderCommandValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 字段级与跨字段校验全部通过，错误路径精确落到冻结字段。

---

### Task 5: 实现 Response 与 Spring HTTP Interface

**Files:**
- Create: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/response/ReconcileReminderResponseJsonTest.java`
- Create: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/api/ReminderCommandClientContractTest.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/response/ReconcileReminderResponse.java`
- Create: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/api/ReminderCommandClient.java`

**Interfaces:**
- Produces: `ReconcileReminderResponse`
- Produces: `POST /internal/v1/reminders/reconcile`
- Consumes: `X-Idempotency-Key` 请求头和 `ReconcileReminderCommand` 请求体。

- [x] **Step 1: 先写 Response JSON 测试**

```java
ReconcileReminderResponse response = new ReconcileReminderResponse(
        true,
        ReconcileResultCode.APPLIED,
        false,
        3001L,
        3L);
JsonNode json = new ObjectMapper().valueToTree(response);
```

断言字段集合精确为：

```text
applied
resultCode
idempotent
bindingId
lastSourceVersion
```

并断言 `resultCode` 序列化为 `APPLIED`。再构造 `IGNORED_OLD` 响应，断言不会产生 `IDEMPOTENT` 或 `CONFLICT` 值。

- [x] **Step 2: 先写 HTTP 注解反射测试**

对 `ReminderCommandClient.class` 断言：

```java
HttpExchange typeExchange = ReminderCommandClient.class.getAnnotation(HttpExchange.class);
assertThat(typeExchange.value()).isEqualTo(ReminderClientContract.BASE_PATH);

Method method = ReminderCommandClient.class.getDeclaredMethod(
        "reconcile", String.class, ReconcileReminderCommand.class);
PostExchange postExchange = method.getAnnotation(PostExchange.class);
assertThat(postExchange.value()).isEqualTo(ReminderClientContract.RECONCILE_PATH);
assertThat(method.getReturnType()).isEqualTo(ReconcileReminderResponse.class);
```

对第一个参数断言存在：

```java
@NotBlank(message = "幂等键不能为空")
@RequestHeader(ReminderClientContract.IDEMPOTENCY_HEADER)
```

对第二个参数断言存在 `@Valid` 与 `@RequestBody`。使用反射读取注解值，不只断言注解类型存在。

- [x] **Step 3: 运行测试确认 RED**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReconcileReminderResponseJsonTest,ReminderCommandClientContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compile 因 Response 和 Client 接口不存在而失败。

- [x] **Step 4: 实现 Response**

```java
package com.shaopc.worthit.reminder.client.response;

import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;

/**
 * Reminder reconcile 处理结果。
 *
 * @param applied 是否应用了命令期望状态
 * @param resultCode reconcile 稳定结果码
 * @param idempotent 是否为相同事件的幂等重放
 * @param bindingId Reminder Binding 标识
 * @param lastSourceVersion Binding 已接受的最新来源版本
 */
public record ReconcileReminderResponse(
        boolean applied,
        ReconcileResultCode resultCode,
        boolean idempotent,
        long bindingId,
        long lastSourceVersion) {
}
```

- [x] **Step 5: 实现 HTTP Interface**

```java
package com.shaopc.worthit.reminder.client.api;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Tracking 调用 Reminder 的内部命令契约。
 *
 * <p>本接口只声明 HTTP 协议，不负责代理创建、鉴权、服务发现或错误解码。</p>
 */
@HttpExchange(ReminderClientContract.BASE_PATH)
public interface ReminderCommandClient {

    /**
     * 按 Tracking 完整期望状态协调 Reminder Binding 与提醒实例。
     *
     * @param eventId Outbox 事件标识，用作幂等键
     * @param command 完整期望状态命令
     * @return Reminder reconcile 处理结果
     */
    @PostExchange(ReminderClientContract.RECONCILE_PATH)
    ReconcileReminderResponse reconcile(
            @NotBlank(message = "幂等键不能为空")
            @RequestHeader(ReminderClientContract.IDEMPOTENCY_HEADER)
            String eventId,
            @Valid @RequestBody ReconcileReminderCommand command);
}
```

- [x] **Step 6: 运行测试确认 GREEN**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReconcileReminderResponseJsonTest,ReminderCommandClientContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: Response JSON 和 HTTP Interface 反射契约全部通过。

---

### Task 6: 启用真实 Client 架构门禁并完成验收

**Files:**
- Create: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/architecture/ReminderClientArchitectureTest.java`
- Verify: `worthit-reminder/worthit-reminder-client/pom.xml`
- Verify: `worthit-reminder/worthit-reminder-client/src/main/java/com/shaopc/worthit/reminder/client/**`
- Verify: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/**`

**Interfaces:**
- Enforces: `TECH-ARCH-002` 扫描真实 Client 生产类且 Client 不依赖 App/Application/Domain/Infrastructure。
- Verifies: `TECH-CLI-001` 完整 DTO、`schemaVersion`、禁止字段和 HTTP 头。

- [x] **Step 1: 先写真实架构门禁**

```java
package com.shaopc.worthit.reminder.client.architecture;

import com.shaopc.worthit.common.test.architecture.WorthItArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.shaopc.worthit.reminder.client",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ReminderClientArchitectureTest {

    @ArchTest
    static final ArchRule clientMustNotDependOnImplementation =
            WorthItArchitectureRules.CLIENT_MUST_NOT_DEPEND_ON_IMPLEMENTATION;

    @ArchTest
    static void importsProductionClientClasses(JavaClasses classes) {
        assertThat(classes).isNotEmpty();
    }
}
```

- [x] **Step 2: 运行架构测试**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  -Dtest=ReminderClientArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 扫描结果非空，`CLIENT_MUST_NOT_DEPEND_ON_IMPLEMENTATION` 通过。

- [x] **Step 3: 运行模块完整测试**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am test
```

Expected: Reminder Client 契约测试和依赖模块测试全部通过。

- [x] **Step 4: 检查生产依赖边界**

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client -am \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree \
  -Dscope=compile
```

Expected:

- 生产依赖只包含 `spring-web`、`jakarta.validation-api` 及其必要传递依赖；
- 没有 `worthit-*-app`、MyBatis、数据库、Spring Boot Starter、`common-http` 或供应商 SDK；
- Reminder Client 节点不列出任何内部 WorthIt compile 依赖。

- [x] **Step 5: 运行全仓门禁**

Run:

```bash
mvn validate
mvn test
mvn package
```

Expected: 全部 16 个模块 `BUILD SUCCESS`。

- [x] **Step 6: 执行静态与范围检查**

Run:

```bash
rg -n \
  "cause|resolutionCause|ResolutionCause|reconcileCause|correction|displayName" \
  worthit-reminder/worthit-reminder-client/src/main \
  && exit 1 || true
rg -n \
  "worthit-.*-app|mybatis|spring-boot-starter|common-http" \
  worthit-reminder/worthit-reminder-client/pom.xml \
  && exit 1 || true
git diff --check
git status --short --untracked-files=all
```

Expected: 禁止字段与禁止依赖无匹配，diff 无空白错误；Git 状态只包含本计划授权的 Reminder Client 文件以及已确认的设计/计划文档差异。

- [x] **Step 7: 形成验证报告**

交付时逐项报告：

- 每条 Maven 命令的退出码；
- Reminder Client 测试数和全仓测试数；
- JSON、Bean Validation、HTTP 反射和 ArchUnit 分别验证了什么；
- dependency tree 中的生产边界；
- 本轮没有验证真实 HTTP、Same-Token、Nacos、LoadBalancer、超时、错误解码、Controller、数据库或 Reminder 业务执行；
- 当前分支、是否有提交、是否推送和工作区差异范围。
