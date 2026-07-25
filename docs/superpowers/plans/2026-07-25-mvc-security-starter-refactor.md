# WorthIt MVC Security Starter Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Auth、Tracking、Reminder 重复的 Servlet 安全运行时下沉到 `worthit-common-webmvc-starter`，仅保留服务差异策略并完成真实环境回归。

**Architecture:** `common-security` 保持框架中立，`common-webmvc-starter` 通过可覆盖的 Spring Boot 自动配置提供 Same-Token、登录校验、TraceId 和通用 Filter。Auth 提供精确匿名登录策略；Auth 的 Same-Token 轮换、Tracking 的 Reminder Client 与 `ServletTraceIdProvider` 继续归属各自 App。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring MVC 6.2.x、Sa-Token 1.45.0、JUnit 5、AssertJ、Spring Boot `ApplicationContextRunner`、Maven。

## Global Constraints

- 当前工作目录是隔离 worktree，分支为 `feature/phase0-runtime-services`。
- 不新增 Common 模块，不修改 API、请求头、错误码、状态码或 Same-Token 语义。
- Gateway 保持 WebFlux，不依赖 `common-webmvc-starter`、Servlet 或 Tomcat。
- `common-security` 保持无 Servlet、无 Spring Boot 自动配置。
- Auth 的 Same-Token 轮换与 Tracking 的内部 Client/TraceId Provider 留在 App。
- 新行为必须执行 RED → GREEN → REFACTOR。
- 达到独立可验证里程碑后精确暂存并提交；不使用 `git add -A`、`git add .` 或 `git commit -am`。
- 不 push、merge、rebase、tag 或删除分支。
- Java 人类可读文本和公共 Javadoc 使用中文；冻结的机器契约保持英文值。

---

### Task 1: 固化设计与规则边界

**Files:**

- Create: `docs/superpowers/specs/2026-07-25-mvc-security-starter-refactor-design.md`
- Create: `docs/superpowers/plans/2026-07-25-mvc-security-starter-refactor.md`
- Modify: `rules/10-architecture.md`
- Modify: `rules/30-spring-maven.md`

**Interfaces:**

- Produces: Starter 可以拥有通用 Servlet 安全运行时、App 只拥有服务差异策略的工程规则。

- [ ] **Step 1: 提交设计与执行计划**

Run:

```bash
git add -- \
  docs/superpowers/specs/2026-07-25-mvc-security-starter-refactor-design.md \
  docs/superpowers/plans/2026-07-25-mvc-security-starter-refactor.md
git diff --cached --check
git commit -m "docs(security): 设计 MVC 安全运行时下沉"
```

Expected: 提交成功，工作区仅保留后续实现改动。

- [ ] **Step 2: 在实现提交中同步规则**

将 `rules/10-architecture.md` 和 `rules/30-spring-maven.md` 的边界改为：

```text
common-webmvc-starter 可以装配三区 Servlet App 共同使用的技术性安全 Filter、
Sa-Token 运行时和默认策略；业务 Controller、业务错误码与服务专属放行策略
仍留在 App。具体 App 不复制相同运行时装配。
```

Expected: 规则明确 Gateway 与 `common-security` 的隔离边界不变。

---

### Task 2: 测试先行定义公共安全运行时

**Files:**

- Create: `worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/security/TrustedSourceFilterTest.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfigurationTest.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/security/UserLoginVerifier.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/security/PublicRequestAuthorizationPolicy.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/security/SaTokenUserLoginVerifier.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/security/TrustedSourceFilter.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfiguration.java`
- Modify: `worthit-common/worthit-common-webmvc-starter/pom.xml`
- Modify: `worthit-common/worthit-common-webmvc-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**

- Produces: `void UserLoginVerifier.verify()`
- Produces: `boolean PublicRequestAuthorizationPolicy.requiresLogin(String requestPath)`
- Produces: `TrustedSourceFilter(SameTokenVerifier, TraceIdGenerator, UserLoginVerifier, PublicRequestAuthorizationPolicy, ObjectMapper)`
- Produces: 可覆盖默认 Bean 的 `WorthItMvcSecurityAutoConfiguration`

- [ ] **Step 1: 写公共 Filter 失败测试**

测试必须覆盖：

```java
// /api 与 /internal 缺失或错误 Same-Token -> 403 AUTH_FORBIDDEN
// 需要登录的 /api -> 调用 UserLoginVerifier
// 匿名策略返回 false 的 /api -> 不调用 UserLoginVerifier
// /internal -> 不调用 UserLoginVerifier
// 非 /api、/internal -> Filter 不处理
// 合法 TraceId -> request attribute 与响应头均保留
// 非法或缺失 TraceId -> 生成新值并同时写入 request attribute 与响应头
// 登录失败 -> 401 AUTH_UNAUTHORIZED
```

- [ ] **Step 2: 确认 Filter RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am \
  -Dtest=TrustedSourceFilterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为公共安全类型尚不存在。

- [ ] **Step 3: 写自动配置失败测试**

用 `WebApplicationContextRunner` 断言默认上下文具有且各只有一个：

```text
StpLogic
SaTokenSameTokenService
SameTokenProvider
SameTokenVerifier
TraceIdGenerator
UserLoginVerifier
PublicRequestAuthorizationPolicy
TrustedSourceFilter
```

再通过 `@TestConfiguration` 注册自定义
`PublicRequestAuthorizationPolicy` 和 `UserLoginVerifier`，断言默认 Bean 回退且
Filter 使用覆盖实现。

- [ ] **Step 4: 确认自动配置 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am \
  -Dtest=WorthItMvcSecurityAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为自动配置尚不存在。

- [ ] **Step 5: 增加最小依赖和实现**

Starter 增加：

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-security</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-jwt</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-template</artifactId>
</dependency>
```

实现公共 Filter 和 `WorthItMvcSecurityAutoConfiguration`，所有默认扩展点使用
`@ConditionalOnMissingBean`，配置类使用：

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WorthItMvcSecurityAutoConfiguration {
}
```

并在 `AutoConfiguration.imports` 逐行登记新配置。

- [ ] **Step 6: 验证 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am test
```

Expected: PASS。

---

### Task 3: 迁移三个 App 并删除复制实现

**Files:**

- Create: `worthit-auth/worthit-auth-app/src/main/java/com/shaopc/worthit/auth/app/security/AuthPublicRequestAuthorizationPolicy.java`
- Create: `worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/security/AuthPublicRequestAuthorizationPolicyTest.java`
- Modify: `worthit-auth/worthit-auth-app/src/main/java/com/shaopc/worthit/auth/app/WorthItAuthApplication.java`
- Modify: `worthit-tracking/worthit-tracking-app/src/main/java/com/shaopc/worthit/tracking/app/WorthItTrackingApplication.java`
- Modify: `worthit-reminder/worthit-reminder-app/src/main/java/com/shaopc/worthit/reminder/app/WorthItReminderApplication.java`
- Modify: `worthit-auth/worthit-auth-app/pom.xml`
- Modify: `worthit-tracking/worthit-tracking-app/pom.xml`
- Modify: `worthit-reminder/worthit-reminder-app/pom.xml`
- Delete: three App copies of `SaTokenRuntimeConfiguration.java`
- Delete: three App copies of `UserLoginVerifier.java`
- Delete: three App copies of `SaTokenUserLoginVerifier.java`
- Delete: three App copies of `TrustedSourceFilter.java`
- Delete: three App copies of `TrustedSourceFilterTest.java`

**Interfaces:**

- Consumes: `PublicRequestAuthorizationPolicy`
- Produces: Auth 精确登录路径匿名策略
- Preserves: Auth Same-Token 轮换、Tracking `TraceIdProvider`

- [ ] **Step 1: 写 Auth 策略失败测试**

```java
@Test
void onlyWechatLoginPathIsAnonymous() {
    PublicRequestAuthorizationPolicy policy =
            new AuthPublicRequestAuthorizationPolicy();

    assertThat(policy.requiresLogin("/api/v1/auth/wechat/login")).isFalse();
    assertThat(policy.requiresLogin("/api/v1/auth/wechat/login/extra")).isTrue();
    assertThat(policy.requiresLogin("/api/v1/auth/profile")).isTrue();
}
```

- [ ] **Step 2: 确认策略 RED**

Run:

```bash
mvn -pl worthit-auth/worthit-auth-app -am \
  -Dtest=AuthPublicRequestAuthorizationPolicyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为 Auth 策略不存在。

- [ ] **Step 3: 实现策略并迁移 App**

实现：

```java
@Component
public final class AuthPublicRequestAuthorizationPolicy
        implements PublicRequestAuthorizationPolicy {

    private static final String WECHAT_LOGIN_PATH =
            "/api/v1/auth/wechat/login";

    @Override
    public boolean requiresLogin(String requestPath) {
        return !WECHAT_LOGIN_PATH.equals(requestPath);
    }
}
```

删除三个 App 的重复类和测试；启动类删除公共 Bean，Tracking 仅保留：

```java
@Bean
TraceIdProvider traceIdProvider() {
    return new ServletTraceIdProvider();
}
```

App POM 移除已由 Starter 拥有且源码未直接使用的 Sa-Token 依赖，并保留源码直接
引用的最小 Common 依赖。

- [ ] **Step 4: 同步规则**

按 Task 1 Step 2 修改 `rules/10-architecture.md` 与
`rules/30-spring-maven.md`。

- [ ] **Step 5: 验证三个 App**

Run:

```bash
mvn -pl \
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
-am test
```

Expected: PASS，且：

```bash
rg -n \
  "class TrustedSourceFilter|class SaTokenRuntimeConfiguration|interface UserLoginVerifier|class SaTokenUserLoginVerifier" \
  worthit-auth worthit-tracking worthit-reminder
```

Expected: 无输出，退出码 1。

- [ ] **Step 6: 精确提交实现**

Run:

```bash
git add -- \
  rules/10-architecture.md \
  rules/30-spring-maven.md \
  worthit-common/worthit-common-webmvc-starter \
  worthit-auth/worthit-auth-app \
  worthit-tracking/worthit-tracking-app \
  worthit-reminder/worthit-reminder-app
git diff --cached --check
git commit -m "refactor(security): 下沉 MVC 安全运行时"
```

Expected: 提交成功。

---

### Task 4: 依赖边界、全量构建与真实环境验收

**Files:**

- Modify only if a test exposes a defect in Task 2–3 scope.

**Interfaces:**

- Consumes: 已迁移的 Starter 与三个 App。
- Produces: 可检查的构建、依赖隔离和真实链路证据。

- [ ] **Step 1: 验证依赖边界**

Run:

```bash
mvn validate
mvn -pl worthit-common/worthit-common-security dependency:tree \
  -Dincludes=org.springframework.boot:*,org.springframework:spring-webmvc,jakarta.servlet:*
mvn -pl worthit-gateway/worthit-gateway-app dependency:tree \
  -Dincludes=com.shaopc.worthit:worthit-common-webmvc-starter,org.springframework:spring-webmvc,jakarta.servlet:*
```

Expected: `validate` PASS；后两条树均不出现被禁止依赖。

- [ ] **Step 2: 全量验证**

Run:

```bash
mvn test
mvn package
git diff --check
```

Expected: 全部 PASS。

- [ ] **Step 3: 重启三个 MVC 服务**

使用 `/Users/shaopc/Documents/Script/dev-stack/README.md` 登记的 MySQL、Redis、
Nacos，只停止并重启本任务已有的 Auth、Reminder、Tracking 进程，不停止
dev-stack 中间件。

- [ ] **Step 4: 执行真实链路门禁**

Run:

```bash
scripts/local-infra/verify.sh
```

Expected: Nacos 注册、MySQL/Flyway、Redis Same-Token、Gateway 路由、
Tracking → Reminder 调用及安全拒绝场景全部 PASS。

- [ ] **Step 5: 收尾提交**

仅当验证阶段产生本任务内修复时：

```bash
git add -- <验证阶段修复文件>
git diff --cached --check
git commit -m "fix(security): 修复 MVC 安全运行时回归"
```

Expected: 工作区干净；不得 push。
