# WorthIt Phase 0 Common Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐七个 Phase 0 Common 模块的真实能力，并完成 Gateway、Auth、Tracking、Reminder 的必要运行接入与可验证门禁。

**Architecture:** 使用“薄 Common 能力层 + App 自有运行适配器”。Common 只提供技术契约、可组合实现和测试规则；Gateway 的 WebFlux Filter、三个业务 App 的 Servlet Filter、Tracking 的 Reminder Client Bean 仍由各运行模块拥有。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Framework 6.2.19、Spring Cloud 2025.0.3、Spring Cloud Gateway/Commons 4.3.x、Sa-Token 1.45.0、MyBatis-Plus 3.5.17、springdoc-openapi 2.8.17、JUnit 5、AssertJ、MockMvc、WebTestClient、ArchUnit 1.4.2、Maven。

## Global Constraints

- 当前分支为 `main`，不创建分支或 worktree。
- 保留任务开始前的 OpenAPI 自动配置重命名和 handoff 改动。
- 每个独立、已验证里程碑允许精确暂存并提交；不得使用 `git add -A`、`git add .` 或 `git commit -am`。
- 不执行 push、merge、rebase、tag 或删除分支。
- Common 不依赖业务 App、Client、业务 DTO、DO、Mapper 或 Repository。
- Gateway 保持 WebFlux，不依赖 WebMVC Starter、Servlet、Tomcat、JDBC 或阻塞式 `RestClient`。
- Reminder Client 保持纯契约，不依赖 Boot Starter、springdoc、Servlet 或 Tomcat。
- `common-web` 保持 Servlet/WebFlux 中立。
- `common-security` 不注册 MVC/Reactor 自动配置或 Filter。
- `common-data` 不提供跨服务共享 BaseDO。
- `common-http` 不包含 Reminder/Tracking 业务契约，也不自动重试。
- 新生产行为按 RED → GREEN → REFACTOR 实施；每个测试必须先因目标能力缺失而失败。
- Java 人类可读文本使用中文；机器契约保持权威文档冻结的英文值。
- 公共类、主要方法、常量、错误码和枚举值使用中文 Javadoc。

---

### Task 0: 验收并提交现有 OpenAPI 重命名现场

**Files:**

- Modify: `docs/superpowers/specs/2026-07-23-openapi-webmvc-starter-design.md`
- Modify: `docs/superpowers/plans/2026-07-23-openapi-webmvc-starter.md`
- Create: `docs/handoffs/2026-07-25-openapi-webmvc-starter-handoff.md`
- Rename: `WorthItOpenApiAutoConfiguration.java` → `WorthItOpenApiGroupsAutoConfiguration.java`
- Rename: `WorthItOpenApiAutoConfigurationTest.java` → `WorthItOpenApiGroupsAutoConfigurationTest.java`
- Modify: `worthit-common/worthit-common-webmvc-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**

- Produces: `WorthItOpenApiGroupsAutoConfiguration`
- Preserves: `public=/api/**`, `internal=/internal/**`

- [ ] **Step 1: 验证模块回归**

Run:

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am test
```

Expected: PASS，Core 12、Web 8、Starter 9，共 29 个测试。

- [ ] **Step 2: 验证旧类名无残留**

Run:

```bash
rg -n "WorthItOpenApiAutoConfiguration" \
  docs/superpowers \
  worthit-common/worthit-common-webmvc-starter
```

Expected: 无输出，退出码 1。

- [ ] **Step 3: 精确提交**

```bash
git add -- \
  docs/handoffs/2026-07-25-openapi-webmvc-starter-handoff.md \
  docs/superpowers/plans/2026-07-23-openapi-webmvc-starter.md \
  docs/superpowers/specs/2026-07-23-openapi-webmvc-starter-design.md \
  worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiAutoConfiguration.java \
  worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiGroupsAutoConfiguration.java \
  worthit-common/worthit-common-webmvc-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiAutoConfigurationTest.java \
  worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiGroupsAutoConfigurationTest.java
git diff --cached --check
git commit -m "refactor(common-webmvc-starter): 明确OpenAPI分组配置职责"
```

---

### Task 1: 测试先行增加 TraceId 基础能力

**Files:**

- Create: `worthit-common/worthit-common-core/src/test/java/com/shaopc/worthit/common/core/trace/UuidTraceIdGeneratorTest.java`
- Create: `worthit-common/worthit-common-core/src/main/java/com/shaopc/worthit/common/core/trace/TraceIdGenerator.java`
- Create: `worthit-common/worthit-common-core/src/main/java/com/shaopc/worthit/common/core/trace/UuidTraceIdGenerator.java`

**Interfaces:**

- Produces: `String TraceIdGenerator.generate()`
- Produces: 32 位小写十六进制、不含连字符的 TraceId

- [ ] **Step 1: 写失败测试**

```java
class UuidTraceIdGeneratorTest {

    private final TraceIdGenerator generator = new UuidTraceIdGenerator();

    @Test
    void generatesOpaqueLowercaseHexTraceId() {
        String traceId = generator.generate();

        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(traceId).doesNotContain("-");
    }

    @Test
    void generatesDifferentTraceIds() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
```

- [ ] **Step 2: 确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-core -am \
  -Dtest=UuidTraceIdGeneratorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，`TraceIdGenerator` 和 `UuidTraceIdGenerator` 不存在。

- [ ] **Step 3: 最小实现**

```java
@FunctionalInterface
public interface TraceIdGenerator {

    String generate();
}
```

```java
public final class UuidTraceIdGenerator implements TraceIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
```

- [ ] **Step 4: 验证 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-core -am test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add -- worthit-common/worthit-common-core
git diff --cached --check
git commit -m "feat(common-core): 增加可信链路标识生成能力"
```

---

### Task 2: 测试先行实现 Common Security

**Files:**

- Modify: `pom.xml`
- Modify: `worthit-common/worthit-common-security/pom.xml`
- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/header/SecurityHeaderNames.java`
- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/error/SecurityErrorCode.java`
- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/context/UserContext.java`
- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SameTokenProvider.java`
- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SameTokenVerifier.java`
- Create: `worthit-common/worthit-common-security/src/main/java/com/shaopc/worthit/common/security/sametoken/SaTokenSameTokenService.java`
- Create: `worthit-common/worthit-common-security/src/test/java/com/shaopc/worthit/common/security/header/SecurityHeaderNamesTest.java`
- Create: `worthit-common/worthit-common-security/src/test/java/com/shaopc/worthit/common/security/error/SecurityErrorCodeTest.java`
- Create: `worthit-common/worthit-common-security/src/test/java/com/shaopc/worthit/common/security/context/UserContextTest.java`

**Interfaces:**

- Produces: frozen header names
- Produces: `AUTH_UNAUTHORIZED`, `AUTH_FORBIDDEN`
- Produces: `String SameTokenProvider.currentToken()`
- Produces: `void SameTokenVerifier.verify(String token)`

- [ ] **Step 1: 写失败契约测试**

```java
@Test
void exposesFrozenTrustedHeaderNames() {
    assertThat(SecurityHeaderNames.SAME_TOKEN).isEqualTo(SaSameUtil.SAME_TOKEN);
    assertThat(SecurityHeaderNames.CALLER_SERVICE).isEqualTo("X-Caller-Service");
    assertThat(SecurityHeaderNames.USER_ID).isEqualTo("X-User-Id");
    assertThat(SecurityHeaderNames.SESSION_ID).isEqualTo("X-Session-Id");
    assertThat(SecurityHeaderNames.TRACE_ID).isEqualTo("X-Trace-Id");
}
```

```java
@Test
void exposesOnlyFrozenSecurityErrors() {
    assertThat(SecurityErrorCode.values())
            .extracting(SecurityErrorCode::code)
            .containsExactly("AUTH_UNAUTHORIZED", "AUTH_FORBIDDEN");
}
```

```java
@Test
void rejectsNonPositiveUserId() {
    assertThatIllegalArgumentException()
            .isThrownBy(() -> new UserContext(0L))
            .withMessage("用户标识必须大于0");
}
```

- [ ] **Step 2: 确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-security -am test
```

Expected: FAIL，因为模块尚无源码和依赖。

- [ ] **Step 3: 增加受管依赖**

根 POM：

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-core</artifactId>
    <version>${sa-token.version}</version>
</dependency>
```

模块 POM：

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-core</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-core</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

并增加 JUnit 5 与 AssertJ test scope。

- [ ] **Step 4: 实现稳定契约**

`SecurityErrorCode` 完整行为：

```java
public enum SecurityErrorCode implements ErrorCode {
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未登录或登录已失效"),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "没有权限访问该资源");

    private final String code;
    private final String defaultMessage;

    SecurityErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
```

`UserContext`：

```java
public record UserContext(long userId) {
    public UserContext {
        if (userId <= 0) {
            throw new IllegalArgumentException("用户标识必须大于0");
        }
    }
}
```

Same-Token 端口：

```java
@FunctionalInterface
public interface SameTokenProvider {
    String currentToken();
}
```

```java
@FunctionalInterface
public interface SameTokenVerifier {
    void verify(String token);
}
```

Sa-Token 适配：

```java
public final class SaTokenSameTokenService
        implements SameTokenProvider, SameTokenVerifier {

    @Override
    public String currentToken() {
        return SaSameUtil.getToken();
    }

    @Override
    public void verify(String token) {
        SaSameUtil.checkToken(token);
    }
}
```

- [ ] **Step 5: 验证 GREEN 与依赖隔离**

Run:

```bash
mvn -pl worthit-common/worthit-common-security -am test
mvn -pl worthit-common/worthit-common-security dependency:tree -Dscope=compile
```

Expected: PASS；依赖树不含 MVC、WebFlux、Servlet 或 Tomcat。

- [ ] **Step 6: 提交**

```bash
git add -- pom.xml worthit-common/worthit-common-security
git diff --cached --check
git commit -m "feat(common-security): 建立可信来源安全契约"
```

---

### Task 3: 测试先行实现 Common Data

**Files:**

- Modify: `pom.xml`
- Modify: `worthit-common/worthit-common-data/pom.xml`
- Create: `worthit-common/worthit-common-data/src/main/java/com/shaopc/worthit/common/data/config/WorthItMybatisPlusConfiguration.java`
- Create: `worthit-common/worthit-common-data/src/main/java/com/shaopc/worthit/common/data/audit/CurrentAuditor.java`
- Create: `worthit-common/worthit-common-data/src/main/java/com/shaopc/worthit/common/data/audit/WorthItMetaObjectHandler.java`
- Create: `worthit-common/worthit-common-data/src/main/java/com/shaopc/worthit/common/data/logic/LogicalDeleteConstants.java`
- Create: `worthit-common/worthit-common-data/src/test/java/com/shaopc/worthit/common/data/config/WorthItMybatisPlusConfigurationTest.java`
- Create: `worthit-common/worthit-common-data/src/test/java/com/shaopc/worthit/common/data/audit/WorthItMetaObjectHandlerTest.java`

**Interfaces:**

- Produces: MySQL pagination then optimistic-lock interceptor order
- Produces: audit fill using `Clock` and `CurrentAuditor`
- Produces: active `0`, deleted `1`

- [ ] **Step 1: 写失败配置测试**

```java
@Test
void registersMysqlPaginationBeforeOptimisticLocking() {
    MybatisPlusInterceptor interceptor =
            new WorthItMybatisPlusConfiguration().mybatisPlusInterceptor();

    assertThat(interceptor.getInterceptors())
            .hasExactlyElementsOfTypes(
                    PaginationInnerInterceptor.class,
                    OptimisticLockerInnerInterceptor.class);
    assertThat((PaginationInnerInterceptor) interceptor.getInterceptors().get(0))
            .extracting(PaginationInnerInterceptor::getDbType)
            .isEqualTo(DbType.MYSQL);
}
```

- [ ] **Step 2: 写失败审计测试**

测试实体声明 `createTime/updateTime/createBy/updateBy` 并通过 MyBatis
`SystemMetaObject.forObject` 创建 MetaObject。固定 `Clock` 为
`2026-07-25T08:00:00Z`，当前用户为 `1001`，断言 insert/update 填充值；
当前用户为空时 actor 字段保持 null。

- [ ] **Step 3: 确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-data -am test
```

Expected: FAIL，因为配置、审计类型和插件依赖不存在。

- [ ] **Step 4: 增加依赖**

根 POM 管理：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
```

模块依赖上述两个 Artifact、Lombok、JUnit 5、AssertJ。

- [ ] **Step 5: 最小实现**

```java
@Configuration(proxyBeanMethods = false)
public class WorthItMybatisPlusConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

```java
@FunctionalInterface
public interface CurrentAuditor {
    OptionalLong currentUserId();
}
```

`WorthItMetaObjectHandler` 使用以下字段和类型：

```java
strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
```

当前用户存在时对 `createBy/updateBy` 使用 `Long.class` 填充；不存在时不填充。

- [ ] **Step 6: 验证 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-data -am test
mvn -pl worthit-common/worthit-common-data dependency:tree -Dscope=compile
```

Expected: PASS；分页类来自 `mybatis-plus-jsqlparser`，无共享 DO/Mapper。

- [ ] **Step 7: 提交**

```bash
git add -- pom.xml worthit-common/worthit-common-data
git diff --cached --check
git commit -m "feat(common-data): 增加MyBatis基础配置与审计填充"
```

---

### Task 4: 测试先行实现内部 HTTP 请求头与错误解码

**Files:**

- Modify: `worthit-common/worthit-common-http/pom.xml`
- Create: `worthit-common/worthit-common-http/src/main/java/com/shaopc/worthit/common/http/config/HttpClientTimeouts.java`
- Create: `worthit-common/worthit-common-http/src/main/java/com/shaopc/worthit/common/http/trace/TraceIdProvider.java`
- Create: `worthit-common/worthit-common-http/src/main/java/com/shaopc/worthit/common/http/context/InternalRequestContext.java`
- Create: `worthit-common/worthit-common-http/src/main/java/com/shaopc/worthit/common/http/interceptor/InternalRequestHeadersInterceptor.java`
- Create: `worthit-common/worthit-common-http/src/main/java/com/shaopc/worthit/common/http/error/RemoteServiceException.java`
- Create: `worthit-common/worthit-common-http/src/main/java/com/shaopc/worthit/common/http/error/ApiResponseErrorHandler.java`
- Create corresponding tests under `src/test/java`

**Interfaces:**

- Consumes: `SameTokenProvider`, `TraceIdProvider`
- Produces: three trusted headers
- Produces: bounded unified-envelope error decoding

- [ ] **Step 1: 写失败值对象测试**

```java
@Test
void rejectsZeroOrNegativeTimeouts() {
    assertThatIllegalArgumentException()
            .isThrownBy(() -> new HttpClientTimeouts(Duration.ZERO, Duration.ofSeconds(1)));
    assertThatIllegalArgumentException()
            .isThrownBy(() -> new HttpClientTimeouts(Duration.ofSeconds(1), Duration.ZERO));
}
```

- [ ] **Step 2: 写失败拦截器测试**

使用 `MockClientHttpRequest` 和捕获型 `ClientHttpRequestExecution`，断言旧值被覆盖：

```java
assertThat(headers.getFirst(SecurityHeaderNames.SAME_TOKEN)).isEqualTo("same-token-test");
assertThat(headers.getFirst(SecurityHeaderNames.CALLER_SERVICE)).isEqualTo("worthit-tracking");
assertThat(headers.getFirst(SecurityHeaderNames.TRACE_ID)).isEqualTo("trace-test");
```

- [ ] **Step 3: 写失败错误解码测试**

构造 409 响应：

```json
{
  "success": false,
  "code": "IDEM_CONFLICT",
  "message": "幂等键与请求内容冲突",
  "data": null,
  "traceId": "trace-remote"
}
```

断言 `RemoteServiceException` 保存 `worthit-reminder`、409、
`IDEM_CONFLICT`、`trace-remote`；HTML、空 body、超过 64 KiB 的 body
均不进入异常消息。

- [ ] **Step 4: 确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-http -am test
```

Expected: FAIL，因为所有目标类型不存在。

- [ ] **Step 5: 增加模块依赖**

生产依赖：

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-core</artifactId>
</dependency>
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-web</artifactId>
</dependency>
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

测试依赖 JUnit 5、AssertJ、Spring Test。

- [ ] **Step 6: 实现值对象和拦截器**

`InternalRequestContext` 校验 caller service 非空；拦截器使用 `headers.set`
覆盖三类可信头，禁止保留调用方旧值。

- [ ] **Step 7: 实现安全错误解码**

`ApiResponseErrorHandler`：

- 最多读取 65,537 字节；
- 超过 65,536 字节直接生成通用中文消息；
- 只读取 `code/message/traceId` 文本字段；
- 缺失字段使用 `REMOTE_HTTP_ERROR` 和“远端服务请求失败”；
- 抛出 `RemoteServiceException` 并保留 HTTP 状态。

- [ ] **Step 8: 验证 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-http -am test
```

Expected: PASS。

---

### Task 5: 测试先行实现 HTTP Interface 代理工厂

**Files:**

- Create: `worthit-common/worthit-common-http/src/main/java/com/shaopc/worthit/common/http/client/HttpServiceClientFactory.java`
- Create: `worthit-common/worthit-common-http/src/test/java/com/shaopc/worthit/common/http/client/HttpServiceClientFactoryTest.java`

**Interfaces:**

- Produces:

```java
<T> T create(
        Class<T> clientType,
        String targetService,
        URI baseUrl,
        RestClient.Builder builder,
        HttpClientTimeouts timeouts,
        InternalRequestContext requestContext)
```

- [ ] **Step 1: 写失败真实 HTTP 测试**

使用 JDK `HttpServer` 绑定随机本机端口，定义测试 `@HttpExchange` 接口并验证：

- 请求到达正确路径；
- JSON 可序列化；
- 三个可信头存在；
- 成功响应反序列化；
- 409 响应转为 `RemoteServiceException`；
- 100ms 读取超时面对 500ms 响应时失败并保留 cause。

- [ ] **Step 2: 确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-http -am \
  -Dtest=HttpServiceClientFactoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，代理工厂不存在。

- [ ] **Step 3: 最小实现**

工厂必须：

```java
HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(timeouts.connectTimeout())
        .build();
JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(httpClient);
requestFactory.setReadTimeout(timeouts.readTimeout());

RestClient restClient = builder.clone()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .requestInterceptor(new InternalRequestHeadersInterceptor(requestContext))
        .defaultStatusHandler(
                HttpStatusCode::isError,
                new ApiResponseErrorHandler(objectMapper, targetService))
        .build();

return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(clientType);
```

错误处理器的 target service 从独立 `targetService` 参数传入，不得误用
caller service；测试固定为 `worthit-reminder`。

- [ ] **Step 4: 验证 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-http -am test
```

Expected: PASS，测试使用真实随机端口，不依赖 Mock 调用次数。

- [ ] **Step 5: 提交 Common HTTP**

```bash
git add -- worthit-common/worthit-common-http
git diff --cached --check
git commit -m "feat(common-http): 实现内部HTTP代理与错误解码"
```

---

### Task 6: 扩展 Common Test 架构门禁

**Files:**

- Modify: `worthit-common/worthit-common-test/src/main/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRules.java`
- Modify: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRulesTest.java`
- Modify: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/CommonArchitectureTest.java`
- Add fixture classes for Gateway/Client/Common Web positive and negative cases

**Interfaces:**

- Produces:
  - `GATEWAY_MUST_STAY_REACTIVE`
  - `CLIENT_MUST_STAY_CONTRACT_ONLY`
  - `COMMON_WEB_MUST_STAY_RUNTIME_NEUTRAL`

- [ ] **Step 1: 先增加反例 fixture 和失败测试**

每条规则必须有：

- 一个合法 fixture；
- 一个直接违规 fixture；
- 断言违规报告包含目标依赖包。

- [ ] **Step 2: 确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-test -am test
```

Expected: FAIL，因为规则常量不存在。

- [ ] **Step 3: 实现规则**

Gateway 禁止包：

```text
jakarta.servlet..
org.springframework.web.servlet..
org.apache.catalina..
org.apache.tomcat..
com.shaopc.worthit.common.webmvc..
```

Client 禁止包：

```text
org.springframework.boot..
org.springdoc..
jakarta.servlet..
org.apache.catalina..
org.apache.tomcat..
```

Common Web 禁止包：

```text
org.springframework.web.servlet..
org.springframework.web.reactive..
org.springframework.web.server..
jakarta.servlet..
org.apache.catalina..
org.apache.tomcat..
org.springdoc..
```

Swagger annotation 包不在禁止范围。

- [ ] **Step 4: 验证 GREEN 并提交**

```bash
mvn -pl worthit-common/worthit-common-test -am test
git add -- worthit-common/worthit-common-test
git diff --cached --check
git commit -m "test(common): 扩展运行栈架构隔离门禁"
```

---

### Task 7: 接入 Gateway 可信头过滤器

**Files:**

- Modify: `pom.xml`
- Modify: `worthit-gateway/pom.xml`
- Create: `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/WorthItGatewayApplication.java`
- Create: `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/security/TrustedHeadersGlobalFilter.java`
- Create: `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/TrustedHeadersGlobalFilterTest.java`
- Create: `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/architecture/GatewayArchitectureTest.java`

**Interfaces:**

- Consumes: `TraceIdGenerator`, `SameTokenProvider`
- Produces: clean trusted internal headers

- [ ] **Step 1: 写失败过滤器测试**

用 `MockServerWebExchange` 构造含伪造五类头的请求，捕获下游 request，断言：

- 原值全部移除；
- 新 TraceId 来自测试 Generator；
- Same-Token 来自测试 Provider；
- 不重建 `X-User-Id`、`X-Session-Id`、`X-Caller-Service`。

- [ ] **Step 2: 确认 RED**

```bash
mvn -pl worthit-gateway -am test
```

Expected: FAIL，Gateway 尚无依赖和源码。

- [ ] **Step 3: 增加依赖与实现**

Gateway 使用官方 Artifact：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
```

并依赖 `common-core`、`common-security`、Sa-Token Reactor Boot3 Starter、
`spring-boot-starter-test`、`reactor-test`、`common-test` test scope。

Filter 实现 `GlobalFilter, Ordered`，顺序为
`Ordered.HIGHEST_PRECEDENCE`，使用 `request.mutate().headers(...)` 精确清洗。

- [ ] **Step 4: 验证运行栈隔离**

```bash
mvn -pl worthit-gateway -am test
mvn -pl worthit-gateway dependency:tree -Dscope=compile
```

Expected: PASS；依赖树无 WebMVC Starter、Servlet、Tomcat、JDBC、common-http。

- [ ] **Step 5: 提交**

```bash
git add -- pom.xml worthit-gateway
git diff --cached --check
git commit -m "feat(gateway): 建立可信内部请求头过滤"
```

---

### Task 8: 接入三个 MVC App 的可信来源门禁与 Common Data

**Files:**

- Modify three App POM files
- Create three Spring Boot application classes
- Create three service-owned `TrustedSourceFilter` classes
- Create three filter tests
- Create three architecture tests

**Interfaces:**

- Consumes: `SameTokenVerifier`
- Applies to: `/api/**`, `/internal/**`
- Excludes: `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`

- [ ] **Step 1: 写一个 App 的失败行为测试并复制契约到另外两个 App**

每个 App 测试：

- 缺少 Same-Token → HTTP 403，`AUTH_FORBIDDEN`；
- 错误 Same-Token → HTTP 403；
- 正确 Same-Token → 到达测试 Controller；
- 响应包含可信 TraceId；
- docs/health 路径不被该 Filter 拦截。

- [ ] **Step 2: 确认 RED**

```bash
mvn -pl \
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
-am test
```

Expected: FAIL，三个 App 尚无启动类和安全 Filter。

- [ ] **Step 3: 增加依赖**

三个 App 增加：

- `common-security`
- `common-data`
- Sa-Token Spring Boot3 Starter
- MySQL Driver runtime
- `spring-boot-starter-test` test
- `common-test` test
- ArchUnit JUnit 5 test

Tracking 另加 `common-http` 和 LoadBalancer；Reminder 保留 Client。

- [ ] **Step 4: 实现 App-owned Filter**

三个 Filter 都继承 `OncePerRequestFilter`，但类型位于各自 App 包内。
Filter：

- 只处理 `/api/` 与 `/internal/`；
- 读取 Same-Token 并调用 Verifier；
- 捕获 Sa-Token 认证异常并输出 403 统一 `ApiResponse`；
- 不记录 Token；
- 不在 Common 注册。

- [ ] **Step 5: 显式导入数据配置**

三个启动类使用 `@Import(WorthItMybatisPlusConfiguration.class)`。测试上下文
断言存在 MyBatisPlusInterceptor，且没有任何 Common BaseDO。

- [ ] **Step 6: 验证 GREEN**

Run:

```bash
mvn -pl \
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
-am test
```

Expected: PASS。

---

### Task 9: 接入 Tracking → Reminder HTTP Interface

**Files:**

- Create: `worthit-tracking/worthit-tracking-app/src/main/java/com/shaopc/worthit/tracking/infrastructure/client/ReminderClientProperties.java`
- Create: `worthit-tracking/worthit-tracking-app/src/main/java/com/shaopc/worthit/tracking/infrastructure/client/ReminderClientConfiguration.java`
- Create: `worthit-tracking/worthit-tracking-app/src/test/java/com/shaopc/worthit/tracking/infrastructure/client/ReminderClientConfigurationTest.java`

**Interfaces:**

- Produces: `ReminderCommandClient`
- service id: `worthit-reminder`
- caller service: `worthit-tracking`

- [ ] **Step 1: 写失败 Spring 上下文测试**

使用 `ApplicationContextRunner` 提供测试 `RestClient.Builder`、
SameTokenProvider、TraceIdProvider、ObjectMapper，断言生成
`ReminderCommandClient` Bean，并通过随机本机 HTTP Server 发出真实
reconcile 请求。

- [ ] **Step 2: 确认 RED**

```bash
mvn -pl worthit-tracking/worthit-tracking-app -am \
  -Dtest=ReminderClientConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，配置和属性类型不存在。

- [ ] **Step 3: 实现类型安全配置**

属性：

```java
@ConfigurationProperties("worthit.clients.reminder")
public record ReminderClientProperties(
        @DefaultValue("worthit-reminder") String serviceId,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout) {
}
```

配置创建 `HttpServiceClientFactory`，base URL 使用
`URI.create("http://" + properties.serviceId())`，caller 固定
`worthit-tracking`。

- [ ] **Step 4: 验证请求契约**

真实 HTTP 测试断言：

- path 为 `/internal/v1/reminders/reconcile`；
- `X-Idempotency-Key` 等于 eventId；
- body 含 operationType/schemaVersion，不含冻结禁止字段；
- Same-Token、caller、TraceId 正确。

- [ ] **Step 5: 验证 GREEN 并提交 MVC App/Client 接入**

```bash
mvn -pl \
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
-am test
git add -- \
  worthit-auth/worthit-auth-app \
  worthit-tracking/worthit-tracking-app \
  worthit-reminder/worthit-reminder-app
git diff --cached --check
git commit -m "feat(app): 接入公共安全数据与内部HTTP能力"
```

---

### Task 10: 全量门禁、依赖审阅和完成审计

**Files:**

- Modify plan checkboxes as tasks complete
- No production changes unless verification exposes a defect; defects must先加失败测试

- [ ] **Step 1: Common 全模块测试**

```bash
mvn -pl \
worthit-common/worthit-common-core,\
worthit-common/worthit-common-web,\
worthit-common/worthit-common-webmvc-starter,\
worthit-common/worthit-common-security,\
worthit-common/worthit-common-data,\
worthit-common/worthit-common-http,\
worthit-common/worthit-common-test \
-am test
```

Expected: PASS，七个模块均实际进入 Reactor 并执行测试。

- [ ] **Step 2: 全 Reactor**

```bash
mvn validate
mvn clean test
mvn package
```

Expected: 17 个 Reactor 模块全部 PASS。

- [ ] **Step 3: 依赖树门禁**

```bash
mvn -pl worthit-reminder/worthit-reminder-client dependency:tree -Dscope=compile
mvn -pl worthit-gateway dependency:tree -Dscope=compile
mvn -pl worthit-common/worthit-common-web dependency:tree -Dscope=compile
mvn -pl worthit-common/worthit-common-security dependency:tree -Dscope=compile
```

Expected:

- Client 无 Boot Starter/springdoc/Servlet/Tomcat；
- Gateway 无 WebMVC Starter/spring-webmvc/Tomcat/JDBC/common-http；
- common-web 无 MVC/WebFlux/Servlet/Tomcat/springdoc runtime；
- common-security 无 MVC/Reactor Starter。

- [ ] **Step 4: 静态与 Git 审计**

```bash
git diff --check
git status --short --untracked-files=all
git log --oneline --decorate -12
```

Expected: 无空白错误；只剩计划勾选或明确未提交文件；无 push。

- [ ] **Step 5: 最终计划与验证提交**

```bash
git add -- docs/superpowers/plans/2026-07-25-phase0-common-completion.md
git diff --cached --check
git commit -m "docs(common): 记录Phase 0公共模块实施结果"
```

- [ ] **Step 6: 完成审计**

逐项对照设计第 9 节：

- 已实现；
- 已由单元/上下文/真实本地 HTTP/架构/依赖树哪类证据验证；
- 未验证的 Redis/Nacos/MySQL/业务 E2E；
- 当前分支、commit 列表和未提交改动；
- 明确未执行 push。
