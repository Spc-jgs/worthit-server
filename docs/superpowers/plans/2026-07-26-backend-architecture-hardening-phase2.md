# WorthIt Backend Architecture Hardening Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Servlet/MVC 实现从 Starter 下沉到独立自动配置模块，补齐可关闭、可覆盖、可测试的 OpenAPI、安全、TraceId 与统一异常处理，同时保持三个业务 App 的 Starter 接入方式和既有 API 契约不变。

**Architecture:** 新增 `worthit-common-webmvc-autoconfigure` 作为所有 Servlet/MVC 运行实现和配置属性的唯一所有者，`worthit-common-webmvc-starter` 只聚合运行依赖与该模块。可信来源、TraceId、用户登录按固定过滤顺序执行；统一异常处理复用请求 TraceId，并使用现行接口文档冻结的机器错误码与 HTTP 状态。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring MVC 6.2.19、Sa-Token 1.45.0、springdoc 2.8.17、JUnit 5、AssertJ、MockMvc、ApplicationContextRunner、Maven。

## Global Constraints

- 权威设计为 `docs/superpowers/specs/2026-07-26-backend-architecture-hardening-design.md`，本计划只实施第 15.2 节“批次二：Common 装配重构”。
- 保持 `worthit-gateway`、`worthit-auth`、`worthit-tracking`、`worthit-reminder` 四个运行服务及所有公网、内部 API、DTO、错误码、请求头和数据库契约不变。
- 三个业务 App 继续只通过 `worthit-common-webmvc-starter` 接入标准 WebMVC 运行时；不得直接依赖 autoconfigure 模块。
- Starter 不保留 Java 实现、自动配置声明或测试；autoconfigure 模块不聚合嵌入式服务器、Swagger UI、Redis 等完整运行时。
- Gateway、Client、`common-core`、`common-web` 不得依赖 Starter 或 autoconfigure；Gateway 保持纯 WebFlux。
- 所有 WorthIt 自有开关使用 `worthit.web.*` 或 `worthit.security.*`；默认安全、Trace 与异常处理开启，OpenAPI 分组默认关闭。
- 自动配置必须同时具备 Servlet 条件、必要 classpath 条件、属性开关、默认 Bean 回退、用户覆盖和缺失类路径测试。
- 外部或未验证来源不能控制可信 TraceId。只有 Same-Token 已验证的请求才可复用传入的 `X-Trace-Id`。
- 统一异常响应使用既有 `ApiResponse`；参数错误为 `VAL_INVALID_ARGUMENT/400`，未登录为 `AUTH_UNAUTHORIZED/401`，权限不足为 `AUTH_FORBIDDEN/403`，资源不存在为 `RES_NOT_FOUND/404`，冲突为现有冲突码/409，下游失败为 `SYS_UPSTREAM/502`，未知异常为 `SYS_ERROR/500`。
- 未知异常必须记录服务端堆栈，但响应不得包含堆栈、SQL、类名、内部异常消息或 Secret。
- 所有行为变更遵循 RED → GREEN → REFACTOR；每个任务完成后精确暂存并使用中文 Conventional Commit。
- 本批不修改业务 DDL、Flyway SQL、前端仓库、`dev-stack` 或上级 WorthIt 文档仓库。

---

### Task 1: 建立 Autoconfigure 模块并迁移现有装配

**Files:**

- Create: `worthit-common/worthit-common-webmvc-autoconfigure/pom.xml`
- Modify: `pom.xml`
- Modify: `worthit-common/pom.xml`
- Modify: `worthit-common/worthit-common-webmvc-starter/pom.xml`
- Move: `worthit-common/worthit-common-webmvc-starter/src/main/**`
- Move: `worthit-common/worthit-common-webmvc-starter/src/test/**`

**Interfaces:**

- Produces: `com.shaopc.worthit:worthit-common-webmvc-autoconfigure`
- Preserves: `com.shaopc.worthit.common.webmvc.*` 包名和现有公开类型
- Preserves: `worthit-common-webmvc-starter` 作为三个 App 的唯一依赖入口

- [ ] **Step 1: 记录结构性 RED 基线**

Run:

```bash
find worthit-common/worthit-common-webmvc-starter/src -type f | sort
```

Expected: 输出两个自动配置、Servlet Filter、OpenAPI 常量、自动配置 imports 和全部相关测试，证明 Starter 仍混合“依赖入口”和“运行实现”。

- [ ] **Step 2: 创建模块 POM 并登记 Reactor/版本治理**

`worthit-common-webmvc-autoconfigure/pom.xml` 声明：

```xml
<artifactId>worthit-common-webmvc-autoconfigure</artifactId>
<name>WorthIt Common WebMVC Autoconfigure</name>
```

编译依赖为 `worthit-common-web`、`worthit-common-security`；Spring Boot
autoconfigure、Servlet、Spring MVC、Jackson、springdoc 和 Sa-Token 相关依赖
使用 `optional=true`，只用于编译条件化实现；测试使用
`spring-boot-starter-test` 和 springdoc WebMVC API。

根 `dependencyManagement` 增加：

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-webmvc-autoconfigure</artifactId>
    <version>${revision}</version>
</dependency>
```

`worthit-common/pom.xml` 在 Starter 之前登记新模块。

- [ ] **Step 3: 原样迁移现有源码、资源和测试**

使用 `apply_patch` 的 Move 操作将 Starter 的 `src/main` 和 `src/test` 文件逐个
迁入 autoconfigure 模块，不改变 `com.shaopc.worthit.common.webmvc.*` 包名。
迁移后 Starter 目录只保留 `pom.xml`。

- [ ] **Step 4: 将 Starter 收敛为依赖聚合**

Starter POM 直接依赖：

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-webmvc-autoconfigure</artifactId>
</dependency>
```

并继续聚合 `spring-boot-starter-web`、Validation、springdoc WebMVC UI、
Sa-Token MVC/JWT/Redis。删除 Starter 对 `common-web`、`common-security`、
Lombok 和测试框架的重复声明。

- [ ] **Step 5: 验证迁移没有改变行为**

Run:

```bash
mvn -pl \
  worthit-common/worthit-common-webmvc-autoconfigure,\
worthit-common/worthit-common-webmvc-starter,\
worthit-gateway,\
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-client,\
worthit-reminder/worthit-reminder-app \
  -am -DskipTests install
mvn -pl \
  worthit-common/worthit-common-webmvc-autoconfigure,\
worthit-common/worthit-common-webmvc-starter,\
worthit-auth/worthit-auth-app,\
  worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -am test
test -z "$(find worthit-common/worthit-common-webmvc-starter/src \
  -type f -print -quit 2>/dev/null)"
```

Expected: PASS；原 22 个 Starter 测试在 autoconfigure 模块中通过，三个 App
继续编译测试，Starter 的 `src` 下没有文件。

- [ ] **Step 6: 精确提交模块边界**

```bash
git add -- pom.xml worthit-common/pom.xml \
  worthit-common/worthit-common-webmvc-autoconfigure \
  worthit-common/worthit-common-webmvc-starter
git diff --cached --check
git diff --cached --name-status
git commit -m "refactor(webmvc): 拆分自动配置与 Starter"
```

---

### Task 2: 建立类型安全属性与独立自动配置开关

**Files:**

- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/config/WorthItWebProperties.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/config/WorthItSecurityProperties.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItTraceAutoConfiguration.java`
- Modify: `WorthItOpenApiGroupsAutoConfiguration.java`
- Modify: `WorthItMvcSecurityAutoConfiguration.java`
- Modify: corresponding auto-configuration tests and imports
- Test generated: `target/classes/META-INF/spring-configuration-metadata.json`

**Interfaces:**

- Produces: `worthit.web.trace.enabled` default `true`
- Produces: `worthit.web.error-handling.enabled` default `true`
- Produces: `worthit.web.openapi.enabled` default `false`
- Produces: `worthit.security.mvc.enabled` default `true`

- [ ] **Step 1: 写属性开关 RED 测试**

在自动配置测试中新增断言：

```java
webContextRunner
        .withPropertyValues("worthit.security.mvc.enabled=false")
        .run(context -> assertThat(context)
                .doesNotHaveBean(TrustedSourceFilter.class));
```

```java
webContextRunner
        .withPropertyValues(
                "worthit.web.openapi.enabled=true",
                "springdoc.api-docs.enabled=true")
        .run(context -> assertThat(context)
                .hasBean(OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME));
```

并验证只开启 springdoc、但未开启 `worthit.web.openapi.enabled` 时不创建分组。

- [ ] **Step 2: 运行并确认 RED**

```bash
mvn -pl worthit-common/worthit-common-webmvc-autoconfigure -am \
  -Dtest='WorthIt*AutoConfigurationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；当前自动配置尚不认识 WorthIt 自有开关。

- [ ] **Step 3: 实现配置属性与元数据处理**

两个属性类均使用 `@ConfigurationProperties`、明确默认值和字段 Javadoc。
autoconfigure POM 增加可选注解处理器：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

新增 `WorthItTraceAutoConfiguration`，独立提供可覆盖的
`TraceIdGenerator`；从安全自动配置移除该 Bean，并通过
`@AutoConfiguration(after = WorthItTraceAutoConfiguration.class)` 声明顺序。

- [ ] **Step 4: 为每项自动配置添加属性与 classpath 条件**

- OpenAPI：Servlet + `GroupedOpenApi` + 两个 enabled 开关。
- Trace：Servlet + `OncePerRequestFilter` + `worthit.web.trace.enabled`。
- Security：Servlet + Sa-Token 类 + `worthit.security.mvc.enabled`。
- 每个默认 Bean继续使用 `@ConditionalOnMissingBean`。

自动配置 imports 顺序固定为 Trace、Security、OpenAPI，后续异常处理在 Trace
之后登记。

- [ ] **Step 5: 验证属性、覆盖、非 Servlet 和缺失 classpath**

使用 `FilteredClassLoader` 分别排除 `GroupedOpenApi`、Sa-Token 核心类，
断言对应自动配置无 Bean 且上下文可启动。运行：

```bash
mvn -pl worthit-common/worthit-common-webmvc-autoconfigure -am test
mvn -pl worthit-common/worthit-common-webmvc-autoconfigure -am package
rg -n \
  '"name":\s*"worthit\.(web\.(trace|error-handling|openapi)|security\.mvc)\.enabled"' \
  worthit-common/worthit-common-webmvc-autoconfigure/target/classes/META-INF/spring-configuration-metadata.json
```

Expected: 测试 PASS，元数据包含四个开关及默认值。

- [ ] **Step 6: 精确提交属性与条件**

```bash
git add -- \
  worthit-common/worthit-common-webmvc-autoconfigure/pom.xml \
  worthit-common/worthit-common-webmvc-autoconfigure/src
git diff --cached --check
git commit -m "feat(webmvc): 增加类型安全自动配置开关"
```

---

### Task 3: 固定可信来源、TraceId 与登录校验顺序

**Files:**

- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/security/TrustedRequestAttributes.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/security/ServletApiErrorWriter.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/trace/TrustedTraceIdFilter.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/security/PublicAuthenticationFilter.java`
- Modify: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/security/TrustedSourceFilter.java`
- Modify: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItTraceAutoConfiguration.java`
- Modify: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfiguration.java`
- Modify: `worthit-common/worthit-common-webmvc-autoconfigure/src/test/java/com/shaopc/worthit/common/webmvc/security/TrustedSourceFilterTest.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/test/java/com/shaopc/worthit/common/webmvc/security/TrustedSecurityFilterChainTest.java`
- Modify: `worthit-common/worthit-common-webmvc-autoconfigure/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItMvcSecurityAutoConfigurationTest.java`

**Interfaces:**

- Produces: 请求属性 `com.shaopc.worthit.trusted-source=true`
- Produces: 请求属性及响应头 `X-Trace-Id`
- Order: Same-Token source check → trusted TraceId → public login check
- Preserves: `/internal/**` 不检查用户登录，`/api/**` 默认检查登录

- [ ] **Step 1: 写过滤顺序和伪造 TraceId RED 测试**

以实际三个过滤器顺序执行：

```java
assertThat(invalidSameTokenResponseTraceId).isNotEqualTo("trace-forged");
assertThat(validSameTokenDownstreamTraceId).isEqualTo("trace-gateway");
assertThat(loginFailureBodyTraceId).isEqualTo("trace-gateway");
```

再验证关闭 security、保留 trace 时，外部传入 `trace-forged` 被替换为生成值。

- [ ] **Step 2: 运行并确认 RED**

```bash
mvn -pl worthit-common/worthit-common-webmvc-autoconfigure -am \
  -Dtest='*FilterTest,*AutoConfigurationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；目标过滤器、可信标记和顺序尚不存在。

- [ ] **Step 3: 拆分单一职责过滤器**

- `TrustedSourceFilter` 只校验 Same-Token，成功后设置可信来源属性。
- `TrustedTraceIdFilter` 仅在可信来源属性为真时复用请求头，否则生成新 TraceId。
- `PublicAuthenticationFilter` 只处理 `/api/**` 的登录策略。
- `ServletApiErrorWriter` 统一安全错误 JSON，并优先复用请求属性中的 TraceId。

所有 Filter 只匹配 `/api`、`/api/**`、`/internal`、`/internal/**`。

- [ ] **Step 4: 用 FilterRegistrationBean 固定顺序**

两个自动配置显式注册：

```java
TrustedSourceFilter       Ordered.HIGHEST_PRECEDENCE + 10
TrustedTraceIdFilter      Ordered.HIGHEST_PRECEDENCE + 20
PublicAuthenticationFilter Ordered.HIGHEST_PRECEDENCE + 30
```

不得依赖 Bean 名称、组件扫描或未声明的默认排序。

- [ ] **Step 5: 验证安全链路 GREEN**

```bash
mvn -pl \
  worthit-common/worthit-common-webmvc-autoconfigure,\
worthit-common/worthit-common-webmvc-starter,\
worthit-auth/worthit-auth-app \
  -am test
```

Expected: PASS；现有 Auth 匿名登录策略继续覆盖默认策略，内部路径不检查用户
登录，非法 Same-Token 不复用伪造 TraceId。

- [ ] **Step 6: 精确提交过滤链**

```bash
git add -- worthit-common/worthit-common-webmvc-autoconfigure/src
git diff --cached --check
git commit -m "refactor(security): 固定可信请求过滤顺序"
```

---

### Task 4: 增加统一 WebMVC 异常处理

**Files:**

- Create: `worthit-common/worthit-common-web/src/main/java/com/shaopc/worthit/common/web/error/CommonWebErrorCode.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/error/ErrorHttpStatusResolver.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/error/DefaultErrorHttpStatusResolver.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/error/WorthItRestExceptionHandler.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/error/RemoteServiceExceptionHandler.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItErrorHandlingAutoConfiguration.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/test/java/com/shaopc/worthit/common/webmvc/error/DefaultErrorHttpStatusResolverTest.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/test/java/com/shaopc/worthit/common/webmvc/error/WorthItRestExceptionHandlerTest.java`
- Create: `worthit-common/worthit-common-webmvc-autoconfigure/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItErrorHandlingAutoConfigurationTest.java`
- Modify: `worthit-common/worthit-common-webmvc-autoconfigure/pom.xml`
- Modify: `worthit-common/worthit-common-webmvc-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**

- Consumes: `BusinessException`, `RemoteServiceException`, Sa-Token exceptions,
  Spring MVC binding/conversion exceptions and unknown `Exception`
- Produces: `ResponseEntity<ApiResponse<Void>>`
- Produces: current request TraceId in body and `X-Trace-Id` response header

- [ ] **Step 1: 写异常契约 RED 集成测试**

测试 Controller 分别触发：

```java
throw new BusinessException(TEST_CONFLICT);
throw new RemoteServiceException(
        "worthit-reminder",
        503,
        "REMOTE_UNAVAILABLE",
        "trace-remote",
        "下游服务暂时不可用");
throw new IllegalStateException("sensitive-internal-message");
```

并用 Bean Validation 触发字段错误。断言 HTTP、`code`、`details`、`traceId`
以及未知异常响应不包含内部消息。

- [ ] **Step 2: 运行并确认 RED**

```bash
mvn -pl worthit-common/worthit-common-webmvc-autoconfigure -am \
  -Dtest='*ExceptionHandlerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；当前没有全局 Advice，异常不会转换为统一信封。

- [ ] **Step 3: 定义 Common Web 错误码**

`CommonWebErrorCode` 只包含现行接口文档已有的跨服务错误码：

```java
VAL_INVALID_ARGUMENT
RES_NOT_FOUND
SYS_ERROR
SYS_UPSTREAM
```

不得增加服务领域错误码。

- [ ] **Step 4: 实现可覆盖状态解析与 Advice**

默认解析器按现有稳定 code 映射 HTTP 状态；未知 `BusinessException` 默认 409，
应用可提供自己的 `ErrorHttpStatusResolver` 覆盖。Advice 至少处理：

- `MethodArgumentNotValidException`、`BindException`、
  `ConstraintViolationException`、JSON/类型转换异常；
- `BusinessException`；
- `NotLoginException`、权限/角色异常；
- `RemoteServiceException`；
- `NoResourceFoundException`；
- 未知 `Exception`。

`common-http` 在 autoconfigure POM 中使用 `optional=true`。远端异常处理放入独立
`RemoteServiceExceptionHandler`，并由
`@ConditionalOnClass(RemoteServiceException.class)` 的嵌套自动配置创建，
确保没有直接使用 `common-http` 的 Auth/Reminder 不被 Starter 强制引入该模块。

未知异常使用参数化 ERROR 日志记录完整 cause，客户端只返回
`SYS_ERROR` 的稳定消息。

- [ ] **Step 5: 自动配置并验证关闭/覆盖行为**

`WorthItErrorHandlingAutoConfiguration` 使用 Servlet、Advice classpath、
`worthit.web.error-handling.enabled` 条件；默认 resolver 和 Advice 均
`@ConditionalOnMissingBean`。测试属性关闭和用户 resolver 覆盖。

- [ ] **Step 6: 运行异常与模块验证**

```bash
mvn -pl \
  worthit-common/worthit-common-web,\
worthit-common/worthit-common-webmvc-autoconfigure,\
worthit-common/worthit-common-webmvc-starter \
  -am test
```

Expected: PASS；响应不泄露内部异常文本，TraceId 头与 body 一致。

- [ ] **Step 7: 精确提交统一异常**

```bash
git add -- \
  worthit-common/worthit-common-web/src \
  worthit-common/worthit-common-webmvc-autoconfigure/src
git diff --cached --check
git commit -m "feat(webmvc): 提供统一异常响应装配"
```

---

### Task 5: 迁移消费者配置并固化架构门禁

**Files:**

- Modify: `worthit-auth/worthit-auth-app/src/main/resources/application.yml`
- Modify: `worthit-tracking/worthit-tracking-app/src/main/resources/application.yml`
- Modify: `worthit-reminder/worthit-reminder-app/src/main/resources/application.yml`
- Modify: `worthit-auth/worthit-auth-app/pom.xml`
- Modify: `worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/architecture/AuthAppArchitectureTest.java`
- Modify: `worthit-tracking/worthit-tracking-app/src/test/java/com/shaopc/worthit/tracking/app/architecture/TrackingAppArchitectureTest.java`
- Modify: `worthit-reminder/worthit-reminder-app/src/test/java/com/shaopc/worthit/reminder/app/architecture/ReminderAppArchitectureTest.java`
- Modify: `worthit-common/worthit-common-test/src/main/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRules.java`
- Modify: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRulesTest.java`
- Modify: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/CommonArchitectureTest.java`
- Modify: `rules/10-architecture.md`
- Modify: `rules/30-spring-maven.md`
- Modify: `rules/40-testing-quality.md`

**Interfaces:**

- Preserves: 三个 App POM 只直接依赖 `worthit-common-webmvc-starter`
- Enables: local/dev/test 的 `worthit.web.openapi.enabled=true`
- Enforces: Starter 无实现；autoconfigure 不进入 Gateway/Client/中立 Common

- [ ] **Step 1: 写消费者与模块边界 RED 门禁**

新增架构/静态测试断言：

- autoconfigure 生产类只能位于 `com.shaopc.worthit.common.webmvc..`；
- Starter Jar 不包含 `com/shaopc/worthit/**/*.class` 或自动配置 imports；
- Gateway、Reminder Client 和 `common-web` 的 compile tree 不含 Starter 或
  autoconfigure；
- 三个 App 的 compile tree 均通过 Starter 获得 autoconfigure。
- Tracking 保留生产源码实际使用的 `common-security` 直接依赖；
  Auth 删除生产源码未直接使用的 `common-security`，Reminder 不额外增加
  `common-security` 或 `common-http` 直接依赖。

- [ ] **Step 2: 迁移环境配置**

三个 App 的默认配置保持 OpenAPI 关闭；`local | dev | test` 文档中增加：

```yaml
worthit:
  web:
    openapi:
      enabled: true
```

保留 springdoc 自身 `api-docs`、`swagger-ui` 与默认全量文档关闭配置。

- [ ] **Step 3: 更新后端仓库规则**

将旧规则中“业务异常适配留在各 App”“Starter 包含实现”更新为：

- autoconfigure 拥有 Servlet 技术实现和统一异常映射；
- Starter 只聚合依赖；
- 服务只拥有领域错误码、服务专属匿名路径和异常状态映射覆盖；
- 所有自动配置必须有开关、classpath、默认、覆盖和非 Servlet 测试。

不修改上级 WorthIt 文档仓库；其版本化同步留在全设计最终文档批次处理。

- [ ] **Step 4: 执行依赖隔离门禁**

```bash
mvn -pl \
  worthit-common/worthit-common-webmvc-autoconfigure,\
worthit-common/worthit-common-webmvc-starter,\
worthit-gateway,\
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
  worthit-reminder/worthit-reminder-client,\
worthit-reminder/worthit-reminder-app \
  -am dependency:tree
mvn -pl \
  worthit-common/worthit-common-test,\
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-client,\
worthit-reminder/worthit-reminder-app \
  -am -Dtest='*ArchitectureTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；WebFlux/Servlet、Client、Common 与新模块边界均未被绕过。
先执行 install 是因为 `dependency:tree` 不会自行打包 Reactor 中尚未安装的新模块；
树分析仍必须从根 Reactor 执行，不能从子模块目录单独解析 `${revision}` 父版本。

- [ ] **Step 5: 执行批次二总门禁**

```bash
mvn validate
mvn test
mvn package
git diff --check
git status --short --untracked-files=all
```

Expected: 18 个 Reactor 模块全部成功，工作区只含本任务预期文件。

- [ ] **Step 6: 精确提交配置、规则与门禁**

```bash
git add -- \
  worthit-auth/worthit-auth-app/pom.xml \
  worthit-auth/worthit-auth-app/src/main/resources/application.yml \
  worthit-tracking/worthit-tracking-app/src/main/resources/application.yml \
  worthit-reminder/worthit-reminder-app/src/main/resources/application.yml \
  worthit-common/worthit-common-test \
  rules/10-architecture.md \
  rules/30-spring-maven.md \
  rules/40-testing-quality.md
git diff --cached --check
git diff --cached --name-only
git commit -m "test(architecture): 固化 WebMVC 装配边界"
```

---

## Batch Completion Evidence

批次二完成后必须报告：

- 新模块、Starter 和三个 App 的准确依赖关系；
- 自动配置 imports、四个属性元数据和缺失 classpath 测试；
- Same-Token → TraceId → 登录过滤顺序的行为证据；
- 参数、业务、安全、下游与未知异常的 HTTP/code/TraceId 测试；
- Gateway、Client、`common-web` 的运行栈隔离依赖树；
- `mvn validate`、`mvn test`、`mvn package` 的模块数、退出码和测试结果；
- 未修改的上级版本化权威文档同步项，留给最终文档批次处理。
