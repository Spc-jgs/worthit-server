# WorthIt Backend Architecture Hardening Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复已确认的四个后端正确性缺陷，让 Same-Token 扩展点可独立覆盖、Gateway 一次请求只生成一个 TraceId、架构门禁扫描完整生产包，并使 Flyway 与 MySQL 8.4 测试基线处于同一受支持版本线。

**Architecture:** 保持现有 Maven 模块和业务契约不变；`common-security` 分离 Same-Token 的提供与校验适配器，`common-webmvc-starter` 按单一扩展点独立回退；Gateway 先重建可信头，再由认证错误响应复用请求中的可信 TraceId；`common-test` 集中声明运行栈规则，各 App 只负责扫描完整服务根包；根 POM 显式治理 Flyway 11.x 的补丁版本。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Cloud Gateway 4.x、Sa-Token 1.45.0、Flyway 11.20.3、MySQL 8.4 Testcontainers、ArchUnit 1.4.2、JUnit 5、AssertJ、Maven。

## Global Constraints

- 权威设计是 `docs/superpowers/specs/2026-07-26-backend-architecture-hardening-design.md`，本计划只实施其中第 11.1 节第一批正确性修复。
- 不修改业务 API、DTO、错误码、数据库迁移脚本、配置中心、前端仓库或 `dev-stack`。
- Gateway 保持纯 WebFlux；三个业务 App 保持 Servlet/WebMVC；`common-security` 不引入 Servlet 或 WebFlux。
- Same-Token 的 Provider 和 Verifier 是两个独立扩展点，不再以一个 Bean 同时承担两个职责。
- Gateway 外部请求携带的 TraceId 必须先被清除；后续成功链路和认证失败响应只能使用 Gateway 本次生成的可信 TraceId。
- 架构测试必须导入模块完整生产根包，并通过 `DoNotIncludeTests` 排除测试夹具。
- Flyway 保持在 Spring Boot 3.5 基线使用的 11.x 主版本内，只升级到官方 11.x 最后发布版 `11.20.3`；不得通过关闭日志或过滤告警掩盖兼容性问题。
- 所有行为变更执行 RED → GREEN → REFACTOR。仅 POM 版本治理没有适合的 Java 单元级 RED，用现有真实 MySQL 迁移测试的兼容性告警作为基线失败信号。
- 每个任务达到独立可验证里程碑后精确暂存并提交；禁止 `git add -A`、`git add .` 和 `git commit -am`。
- 不 push、merge、rebase、tag 或删除分支。发现任务外改动时停止暂存，先确认归属。
- Java 人类可读文本、公共 Javadoc、计划和提交说明使用中文；冻结的机器契约值保持不变。

---

### Task 1: 让 Same-Token Provider 与 Verifier 独立回退

**Files:**

- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SaTokenSameTokenProvider.java`
- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SaTokenSameTokenVerifier.java`
- Delete: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SaTokenSameTokenService.java`
- Modify: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfiguration.java`
- Modify: `worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfigurationTest.java`
- Modify: `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/WorthItGatewayApplication.java`
- Modify: `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/GatewaySaTokenRedisCompatibilityTest.java`

**Interfaces:**

- Consumes: `SameTokenProvider.currentToken()`
- Consumes: `SameTokenVerifier.verify(String token)`
- Produces: 可分别被应用覆盖的默认 `SaTokenSameTokenProvider` 与 `SaTokenSameTokenVerifier`
- Preserves: Sa-Token 的 `SaSameUtil.getToken()` 和 `SaSameUtil.checkToken(token)` 行为

- [ ] **Step 1: 为三个覆盖组合写自动配置测试**

在 `WorthItMvcSecurityAutoConfigurationTest` 增加三个测试配置：

```java
@TestConfiguration(proxyBeanMethods = false)
static class SameTokenProviderOverrideConfiguration {

    @Bean
    SameTokenProvider customSameTokenProvider() {
        return () -> "custom-provider-token";
    }
}

@TestConfiguration(proxyBeanMethods = false)
static class SameTokenVerifierOverrideConfiguration {

    @Bean
    SameTokenVerifier customSameTokenVerifier() {
        return token -> {
        };
    }
}

@TestConfiguration(proxyBeanMethods = false)
static class SameTokenOverridesConfiguration {

    @Bean
    SameTokenProvider customSameTokenProvider() {
        return () -> "custom-provider-token";
    }

    @Bean
    SameTokenVerifier customSameTokenVerifier() {
        return token -> {
        };
    }
}
```

三个测试分别断言：

```java
assertThat(context).hasSingleBean(SameTokenProvider.class);
assertThat(context).hasSingleBean(SameTokenVerifier.class);
assertThat(context).hasSingleBean(TrustedSourceFilter.class);
```

只覆盖 Provider 时，Provider 必须是 `customSameTokenProvider`，Verifier 必须仍有默认实现；只覆盖 Verifier 时反向成立；同时覆盖时两个自定义 Bean 都必须保留。

- [ ] **Step 2: 运行局部覆盖测试并确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am \
  -Dtest=WorthItMvcSecurityAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL。当前组合条件在只覆盖一个接口时同时禁用默认 Provider 和 Verifier，导致另一个接口缺失或 `TrustedSourceFilter` 无法装配；同时覆盖测试可以通过。

- [ ] **Step 3: 拆分 Sa-Token 适配器**

新增 Provider：

```java
package com.shaopc.worthit.common.security.sametoken;

import cn.dev33.satoken.same.SaSameUtil;

/**
 * 基于 Sa-Token 获取当前 Same-Token。
 */
public final class SaTokenSameTokenProvider implements SameTokenProvider {

    @Override
    public String currentToken() {
        return SaSameUtil.getToken();
    }
}
```

新增 Verifier：

```java
package com.shaopc.worthit.common.security.sametoken;

import cn.dev33.satoken.same.SaSameUtil;

/**
 * 基于 Sa-Token 校验 Same-Token。
 */
public final class SaTokenSameTokenVerifier implements SameTokenVerifier {

    @Override
    public void verify(String token) {
        SaSameUtil.checkToken(token);
    }
}
```

删除 `SaTokenSameTokenService`，不保留同时实现两个接口的兼容壳。

- [ ] **Step 4: 把自动配置改为按接口独立回退**

用两个 Bean 方法替换原组合条件：

```java
@Bean
@ConditionalOnMissingBean(SameTokenProvider.class)
SaTokenSameTokenProvider saTokenSameTokenProvider() {
    return new SaTokenSameTokenProvider();
}

@Bean
@ConditionalOnMissingBean(SameTokenVerifier.class)
SaTokenSameTokenVerifier saTokenSameTokenVerifier() {
    return new SaTokenSameTokenVerifier();
}
```

默认上下文测试改为分别断言两个具体适配器和两个接口各只有一个 Bean：

```java
assertThat(context).hasSingleBean(SaTokenSameTokenProvider.class);
assertThat(context).hasSingleBean(SaTokenSameTokenVerifier.class);
assertThat(context).hasSingleBean(SameTokenProvider.class);
assertThat(context).hasSingleBean(SameTokenVerifier.class);
```

- [ ] **Step 5: 更新 Gateway 的 Provider 使用点**

`WorthItGatewayApplication.sameTokenProvider()` 返回：

```java
return new SaTokenSameTokenProvider();
```

`GatewaySaTokenRedisCompatibilityTest` 仅验证取 Token，因此把局部变量类型改为：

```java
SaTokenSameTokenProvider sameTokenProvider =
        new SaTokenSameTokenProvider();
```

并调用 `sameTokenProvider.currentToken()`；不得为 Gateway 引入 Verifier。

- [ ] **Step 6: 验证 Same-Token GREEN**

Run:

```bash
mvn -pl \
  worthit-common/worthit-common-security,\
worthit-common/worthit-common-webmvc-starter,\
worthit-gateway \
  -am test
```

Expected: PASS；三个覆盖组合、Starter 默认装配和 Gateway Same-Token Redis 兼容测试全部通过。

- [ ] **Step 7: 检查旧组合实现已完全移除**

Run:

```bash
rg -n "SaTokenSameTokenService" .
```

Expected: 无输出，退出码为 1。

- [ ] **Step 8: 精确提交 Same-Token 修复**

Run:

```bash
git add -- \
  worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SaTokenSameTokenProvider.java \
  worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SaTokenSameTokenVerifier.java \
  worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SaTokenSameTokenService.java \
  worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfiguration.java \
  worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfigurationTest.java \
  worthit-gateway/src/main/java/com/shaopc/worthit/gateway/WorthItGatewayApplication.java \
  worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/GatewaySaTokenRedisCompatibilityTest.java
git diff --cached --check
git diff --cached --stat
git commit -m "fix(security): 修复 Same-Token 局部覆盖装配"
```

Expected: 提交只包含本任务列出的八个路径。

---

### Task 2: Gateway 认证失败复用可信 TraceId

**Files:**

- Modify: `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/security/GatewaySecurityErrorWriter.java`
- Modify: `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/GatewaySaTokenSecurityTest.java`

**Interfaces:**

- Consumes: `TrustedHeadersGlobalFilter` 写入请求的 `X-Trace-Id`
- Produces: 认证失败响应头和 `ApiResponse.traceId` 使用同一个可信 TraceId
- Removes: `GatewaySecurityErrorWriter` 对 `TraceIdGenerator` 的依赖

- [ ] **Step 1: 用生产过滤顺序重写未登录测试**

先保留当前 `GatewaySecurityErrorWriter(objectMapper, () -> "trace-error")` 构造方式，把 `rejectsMissingLoginWithUnifiedUnauthorizedResponse` 改为先执行可信头过滤器：

```java
AtomicBoolean reached = new AtomicBoolean();
AtomicInteger generated = new AtomicInteger();
TrustedHeadersGlobalFilter trustedHeaders = new TrustedHeadersGlobalFilter(
        () -> {
            generated.incrementAndGet();
            return "trace-trusted";
        },
        () -> "same-token-trusted");
MockServerWebExchange exchange = exchange("/api/v1/items");

withLoginState(false, () -> StepVerifier.create(trustedHeaders.filter(
                exchange,
                trustedExchange -> filter.filter(
                        trustedExchange,
                        ignored -> {
                            reached.set(true);
                            return Mono.empty();
                        })))
        .verifyComplete());
```

断言：

```java
assertThat(generated).hasValue(1);
assertThat(exchange.getResponse().getHeaders()
        .getFirst(SecurityHeaderNames.TRACE_ID))
        .isEqualTo("trace-trusted");
assertThat(body.path("traceId").textValue())
        .isEqualTo("trace-trusted");
```

同时把 `excludedLoginPathStillUsesTrustedHeaderRebuild` 改成相同生产顺序：`trustedHeaders.filter(...)` 外层，`filter.filter(...)` 内层。

- [ ] **Step 2: 运行 Gateway 安全测试并确认 RED**

Run:

```bash
mvn -pl worthit-gateway -am \
  -Dtest=GatewaySaTokenSecurityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；请求头是 `trace-trusted`，当前错误写入器仍生成并返回 `trace-error`。

- [ ] **Step 3: 让错误写入器读取可信请求头**

删除 `TraceIdGenerator` 字段、导入和构造参数，构造器只保留：

```java
public GatewaySecurityErrorWriter(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(
            objectMapper, "ObjectMapper不能为空");
}
```

`unauthorized` 从 Sa-Token 当前请求读取 TraceId：

```java
String traceId = requireTraceId(
        SaHolder.getRequest().getHeader(SecurityHeaderNames.TRACE_ID));
```

保留现有响应状态、Content-Type、响应头、统一错误码和序列化失败处理。可信头缺失时继续快速失败，不在错误写入器中生成第二个 TraceId。

- [ ] **Step 4: 更新测试构造器并验证 GREEN**

测试字段改为：

```java
private final GatewaySecurityErrorWriter errorWriter =
        new GatewaySecurityErrorWriter(objectMapper);
```

Run:

```bash
mvn -pl worthit-gateway -am \
  -Dtest=GatewaySaTokenSecurityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；可信 TraceId 只生成一次，未登录响应头与响应体均为 `trace-trusted`。

- [ ] **Step 5: 回归整个 Gateway**

Run:

```bash
mvn -pl worthit-gateway -am test
```

Expected: PASS；Gateway 响应式门禁、Sa-Token、Redis 兼容和 BlockHound 测试无回归。

- [ ] **Step 6: 精确提交 TraceId 修复**

Run:

```bash
git add -- \
  worthit-gateway/src/main/java/com/shaopc/worthit/gateway/security/GatewaySecurityErrorWriter.java \
  worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/GatewaySaTokenSecurityTest.java
git diff --cached --check
git diff --cached --stat
git commit -m "fix(gateway): 复用可信链路 TraceId"
```

Expected: 提交只包含错误写入器和对应安全测试。

---

### Task 3: 让架构门禁覆盖完整生产包

**Files:**

- Create: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/tracking/fixture/ServletAppDependsOnWebFluxFixture.java`
- Modify: `worthit-common/worthit-common-test/src/main/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRules.java`
- Modify: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRulesTest.java`
- Modify: `worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/architecture/AuthAppArchitectureTest.java`
- Modify: `worthit-tracking/worthit-tracking-app/src/test/java/com/shaopc/worthit/tracking/app/architecture/TrackingAppArchitectureTest.java`
- Modify: `worthit-reminder/worthit-reminder-app/src/test/java/com/shaopc/worthit/reminder/app/architecture/ReminderAppArchitectureTest.java`
- Modify: `worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/architecture/ReminderClientArchitectureTest.java`

**Interfaces:**

- Produces: `SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME`
- Consumes: Auth、Tracking、Reminder 的完整生产根包
- Preserves: Gateway WebFlux 与 Servlet App 的运行栈隔离
- Activates: 已存在但 Reminder Client 尚未执行的 `CLIENT_MUST_STAY_CONTRACT_ONLY`

- [ ] **Step 1: 为共享 Servlet 运行栈规则写失败测试**

新增违规夹具：

```java
package com.shaopc.worthit.tracking.fixture;

import org.springframework.web.reactive.DispatcherHandler;

public final class ServletAppDependsOnWebFluxFixture {

    private DispatcherHandler dispatcherHandler;
}
```

在 `WorthItArchitectureRulesTest` 增加：

```java
@Test
void servletAppRuntimeRuleAcceptsServletApplicationCode() {
    JavaClasses classes = importer.importClasses(TrackingFixture.class);

    assertThatCode(() ->
            WorthItArchitectureRules
                    .SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME
                    .check(classes))
            .doesNotThrowAnyException();
}

@Test
void servletAppRuntimeRuleRejectsWebFluxDependency() {
    JavaClasses classes = importer.importClasses(
            ServletAppDependsOnWebFluxFixture.class);

    assertThatThrownBy(() ->
            WorthItArchitectureRules
                    .SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME
                    .check(classes))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("org.springframework.web.reactive");
}
```

- [ ] **Step 2: 运行共享规则测试并确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-test -am \
  -Dtest=WorthItArchitectureRulesTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL 编译；共享规则常量尚不存在。

- [ ] **Step 3: 实现共享 Servlet App 规则**

在 `WorthItArchitectureRules` 增加：

```java
/**
 * Servlet 业务应用不得依赖响应式 Web 运行时。
 */
public static final ArchRule
        SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME =
        noClasses()
                .that().resideInAnyPackage(
                        "com.shaopc.worthit.auth..",
                        "com.shaopc.worthit.tracking..",
                        "com.shaopc.worthit.reminder..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.reactive..",
                        "org.springframework.web.server..")
                .allowEmptyShould(false)
                .as("Servlet 业务应用不得依赖响应式 Web 运行时");
```

Run:

```bash
mvn -pl worthit-common/worthit-common-test -am \
  -Dtest=WorthItArchitectureRulesTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；合法夹具通过，WebFlux 夹具被拒绝。

- [ ] **Step 4: 先用测试暴露 Tracking 漏扫 infrastructure**

把 `TrackingAppArchitectureTest` 改为 ArchUnit JUnit 形式，但先保留错误的扫描包：

```java
@AnalyzeClasses(
        packages = "com.shaopc.worthit.tracking.app",
        importOptions = ImportOption.DoNotIncludeTests.class)
class TrackingAppArchitectureTest {

    @ArchTest
    static void importsTrackingInfrastructure(JavaClasses classes) {
        assertThat(classes.stream().map(JavaClass::getName))
                .contains(ReminderClientConfiguration.class.getName());
    }
}
```

Run:

```bash
mvn -pl worthit-tracking/worthit-tracking-app -am \
  -Dtest=TrackingAppArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；当前只导入 `com.shaopc.worthit.tracking.app`，不包含真实生产类 `tracking.infrastructure.client.ReminderClientConfiguration`。

- [ ] **Step 5: 三个 App 改为扫描完整服务根包**

三个测试分别使用：

```java
@AnalyzeClasses(
        packages = "com.shaopc.worthit.auth",
        importOptions = ImportOption.DoNotIncludeTests.class)
```

```java
@AnalyzeClasses(
        packages = "com.shaopc.worthit.tracking",
        importOptions = ImportOption.DoNotIncludeTests.class)
```

```java
@AnalyzeClasses(
        packages = "com.shaopc.worthit.reminder",
        importOptions = ImportOption.DoNotIncludeTests.class)
```

每个测试执行共享规则：

```java
@ArchTest
static final ArchRule servletAppMustNotDependOnReactiveRuntime =
        WorthItArchitectureRules
                .SERVLET_APPS_MUST_NOT_DEPEND_ON_REACTIVE_RUNTIME;
```

并保留一个 `JavaClasses` 非空断言。Tracking 额外保留 `importsTrackingInfrastructure`，防止以后扫描范围再次退回 `.app`。

- [ ] **Step 6: 启用 Reminder Client 纯契约规则**

在 `ReminderClientArchitectureTest` 增加：

```java
@ArchTest
static final ArchRule clientMustStayContractOnly =
        WorthItArchitectureRules.CLIENT_MUST_STAY_CONTRACT_ONLY;
```

不修改规则内容；该规则已经由 `ClientDependsOnBootFixture` 覆盖其拒绝行为。

- [ ] **Step 7: 验证所有架构门禁 GREEN**

Run:

```bash
mvn -pl \
  worthit-common/worthit-common-test,\
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-client,\
worthit-reminder/worthit-reminder-app \
  -am \
  -Dtest='*ArchitectureTest,WorthItArchitectureRulesTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；共享规则夹具测试通过，三个 App 扫描完整生产根包，Reminder Client 同时执行实现层依赖和运行时依赖两条规则。

- [ ] **Step 8: 精确提交架构门禁修复**

Run:

```bash
git add -- \
  worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/tracking/fixture/ServletAppDependsOnWebFluxFixture.java \
  worthit-common/worthit-common-test/src/main/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRules.java \
  worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRulesTest.java \
  worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/architecture/AuthAppArchitectureTest.java \
  worthit-tracking/worthit-tracking-app/src/test/java/com/shaopc/worthit/tracking/app/architecture/TrackingAppArchitectureTest.java \
  worthit-reminder/worthit-reminder-app/src/test/java/com/shaopc/worthit/reminder/app/architecture/ReminderAppArchitectureTest.java \
  worthit-reminder/worthit-reminder-client/src/test/java/com/shaopc/worthit/reminder/client/architecture/ReminderClientArchitectureTest.java
git diff --cached --check
git diff --cached --stat
git commit -m "test(architecture): 覆盖完整生产包与 Client 边界"
```

Expected: 提交只包含共享规则、规则夹具和四个消费者测试。

---

### Task 4: 对齐 Flyway 11 与 MySQL 8.4 测试基线

**Files:**

- Modify: `pom.xml`

**Interfaces:**

- Consumes: Spring Boot 3.5.16 依赖管理与三个 App 的 `flyway-core`、`flyway-mysql`
- Produces: Reactor 全局一致的 Flyway `11.20.3`
- Preserves: 现有迁移脚本、MySQL 8.4 Testcontainers 镜像和迁移测试行为

- [ ] **Step 1: 记录当前依赖和兼容性告警基线**

Run:

```bash
mvn -pl worthit-auth/worthit-auth-app -am \
  dependency:tree -Dincludes=org.flywaydb
```

Expected: `flyway-core` 和 `flyway-mysql` 解析为 `11.7.2`。

Run:

```bash
mvn -pl \
  worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -am \
  -Dtest='*FlywayMigrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  --log-file /private/tmp/worthit-flyway-phase1-baseline.log test
```

Run:

```bash
rg -n "newer than this version of Flyway|latest supported version of MySQL" \
  /private/tmp/worthit-flyway-phase1-baseline.log
```

Expected: 三个迁移测试本身 PASS，但日志命中 MySQL 8.4 高于 Flyway 11.7.2 已测试上限的兼容性告警。该告警是本任务的 RED 信号。

- [ ] **Step 2: 在根 POM 显式治理 Flyway 版本**

在 `<properties>` 的框架版本区增加：

```xml
<flyway.version>11.20.3</flyway.version>
```

在三个 BOM import 之后、其他直接依赖版本之前增加：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>${flyway.version}</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
    <version>${flyway.version}</version>
</dependency>
```

必须同时显式管理两个 Artifact；不能只覆盖 `flyway-core`，也不能依赖 imported BOM 内部属性可被当前项目属性覆盖。

- [ ] **Step 3: 验证 Reactor 解析为同一 Flyway 版本**

Run:

```bash
mvn -pl worthit-auth/worthit-auth-app -am \
  dependency:tree -Dincludes=org.flywaydb
```

Expected: `flyway-core`、`flyway-mysql` 及其显示的 Flyway 模块均为 `11.20.3`，不存在 `11.7.2`。

- [ ] **Step 4: 重跑三套真实 MySQL 迁移测试**

Run:

```bash
mvn -pl \
  worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -am \
  -Dtest='*FlywayMigrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  --log-file /private/tmp/worthit-flyway-phase1-green.log test
```

Expected: Auth、Tracking、Reminder 三套空库迁移测试全部 PASS。

Run:

```bash
rg -n "newer than this version of Flyway|latest supported version of MySQL" \
  /private/tmp/worthit-flyway-phase1-green.log
```

Expected: 无输出，退出码为 1。若 `11.20.3` 仍产生兼容性告警，立即停止本任务并报告实际日志；不得抑制告警，也不得未经设计评审跳到 Flyway 12 或 13。

- [ ] **Step 5: 执行第一阶段全量验证**

Run:

```bash
mvn validate
```

Expected: 整个 Reactor BUILD SUCCESS，模块与 POM 结构有效。

Run:

```bash
mvn test
```

Expected: 整个 Reactor BUILD SUCCESS。

Run:

```bash
mvn package
```

Expected: 整个 Reactor BUILD SUCCESS，四个可运行应用均完成可执行 Jar 打包。

Run:

```bash
git diff --check
git status --short
```

Expected: 无空白错误；提交前三个任务的工作区干净，当前只剩 `pom.xml` 和本计划文档（如果计划尚未单独提交）。

- [ ] **Step 6: 精确提交 Flyway 版本治理**

Run:

```bash
git add -- pom.xml
git diff --cached --check
git diff --cached --stat
git commit -m "build(flyway): 对齐 MySQL 8.4 迁移基线"
```

Expected: 提交只包含根 POM 的 Flyway 版本属性和两个 dependencyManagement 条目。

- [ ] **Step 7: 做提交后最终核验**

Run:

```bash
git log -5 --oneline
git status --short --branch
```

Expected: 能看到本阶段三个实现提交和一个测试/架构提交；工作区仅保留明确未提交的计划文档，或在计划已单独提交时完全干净。不得把未验证状态描述为完成。

---

## Phase 1 Completion Criteria

- Same-Token 只覆盖 Provider、只覆盖 Verifier、同时覆盖两者三种上下文都能启动，且 `TrustedSourceFilter` 始终可装配。
- `SaTokenSameTokenService` 已移除，Provider 与 Verifier 的 Sa-Token 适配器职责单一。
- Gateway 未登录响应复用可信头过滤器生成的 TraceId，生成器每个请求只调用一次。
- Auth、Tracking、Reminder 架构测试扫描完整生产根包；Tracking 的 `infrastructure` 有显式防退化断言。
- Reminder Client 同时执行“不得依赖实现层”和“必须保持纯契约”两条规则。
- Flyway `core` 与 `mysql` 均解析为 `11.20.3`；三套 MySQL 8.4 迁移测试通过且不再出现版本支持告警。
- `mvn test` 与 `mvn package` 在同一最终代码状态下均为 BUILD SUCCESS。
- 每个提交边界精确、中文 Conventional Commit 合规，工作区没有混入任务外改动。

## Reference Decisions

- Flyway `11.20.3` 是 Flyway 11.x 官方发布线的最后版本，选择同主版本升级以限制 Spring Boot 3.5 集成风险：<https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine>
- MySQL 支持判断必须结合实际 Testcontainers 8.4 迁移测试，不以依赖解析成功代替运行验证：<https://documentation.red-gate.com/fd/mysql-277579322.html>
