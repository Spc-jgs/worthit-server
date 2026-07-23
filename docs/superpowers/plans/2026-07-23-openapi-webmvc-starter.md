# WorthIt OpenAPI 与 WebMVC Starter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 建立不污染 Gateway 和纯契约 Client 的 MVC Starter，并为 Auth、Tracking、Reminder 提供默认关闭、开发环境开启的 public/internal 双组 OpenAPI 基线。

**Architecture:** `worthit-common-web` 只增加轻量 Swagger Schema 注释；新增 `worthit-common-webmvc-starter` 统一承载 Spring MVC、Validation、springdoc 和双组自动配置。三个 MVC App 接入该 Starter，Gateway 与 `reminder-client` 保持现有运行边界。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Framework 6.2、springdoc-openapi 2.8.17、Swagger annotations 2.2.47、JUnit 5、MockMvc、AssertJ、Maven。

## Global Constraints

- 当前分支固定为 `main`，不创建分支或 worktree。
- 未经用户另行明确要求，不执行 `git add`、`commit` 或 `push`。
- `reminder-client` 继续直接依赖 `spring-web`，不得引入 Spring Boot Starter、springdoc、Servlet 或 Tomcat。
- `common-web` 不依赖 Spring MVC、WebFlux、Servlet、Tomcat 或 springdoc runtime。
- Gateway 不依赖 `worthit-common-webmvc-starter`、`spring-boot-starter-web`、`spring-webmvc` 或 Tomcat。
- OpenAPI 组名固定为 `public`、`internal`，路径分别匹配 `/api/**`、`/internal/**`。
- 默认 `/v3/api-docs` 必须关闭；默认及生产环境关闭 API Docs/UI，`local`、`dev`、`test` 显式开启。
- Java 异常、校验和日志文本使用中文；机器契约保持英文。
- 通用类型、主要方法、重要方法、常量和枚举写中文 Javadoc。
- 新普通 Java 类型按规则使用 Lombok；record、接口、枚举和注解不机械添加 Lombok。
- 生产行为遵循 RED → GREEN → REFACTOR；POM、资源配置和文档使用失败的静态/构建检查建立修改前证据。
- 权威文档创建新版本：架构 `V0.3.16`、技术门禁 `V0.2.3`；旧版本进入历史目录。

---

### Task 1: 建立 Maven 版本治理与 Starter 模块骨架

**Files:**

- Modify: `pom.xml`
- Modify: `worthit-common/pom.xml`
- Create: `worthit-common/worthit-common-webmvc-starter/pom.xml`

**Interfaces:**

- Produces: Maven artifact `com.shaopc.worthit:worthit-common-webmvc-starter:${revision}`
- Produces: properties `springdoc-openapi.version=2.8.17` and `swagger-core.version=2.2.47`
- Consumes: existing `worthit-common-web`

- [x] **Step 1: 建立缺失模块的 RED 证据**

先只在根聚合和 Common 聚合中登记：

```xml
<module>worthit-common-webmvc-starter</module>
```

并在根 `dependencyManagement` 登记内部 artifact。执行：

```bash
mvn validate
```

Expected: FAIL，明确报告 `worthit-common-webmvc-starter/pom.xml` 不存在。

- [x] **Step 2: 增加统一版本与依赖管理**

根 POM properties 增加：

```xml
<springdoc-openapi.version>2.8.17</springdoc-openapi.version>
<swagger-core.version>2.2.47</swagger-core.version>
```

根 `dependencyManagement` 增加：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc-openapi.version}</version>
</dependency>
<dependency>
    <groupId>io.swagger.core.v3</groupId>
    <artifactId>swagger-annotations-jakarta</artifactId>
    <version>${swagger-core.version}</version>
</dependency>
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-webmvc-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

- [x] **Step 3: 创建 Starter POM**

创建 `worthit-common/worthit-common-webmvc-starter/pom.xml`，生产依赖为：

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>worthit-common-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

测试依赖为 `spring-boot-starter-test`。

- [x] **Step 4: 验证模块骨架 GREEN**

Run:

```bash
mvn validate
```

Expected: PASS，Reactor 包含 17 个模块，新增 `WorthIt Common WebMVC Starter`。

---

### Task 2: 为中立公共 Web 模型增加 OpenAPI Schema

**Files:**

- Modify: `worthit-common/worthit-common-web/pom.xml`
- Modify: `worthit-common/worthit-common-web/src/main/java/com/shaopc/worthit/common/web/response/ApiResponse.java`
- Modify: `worthit-common/worthit-common-web/src/main/java/com/shaopc/worthit/common/web/response/FieldViolation.java`
- Create: `worthit-common/worthit-common-web/src/test/java/com/shaopc/worthit/common/web/response/OpenApiSchemaAnnotationTest.java`

**Interfaces:**

- Produces: `@Schema` metadata on `ApiResponse` and `FieldViolation`
- Consumes: `io.swagger.v3.oas.annotations.media.Schema`

- [x] **Step 1: 编写失败的 Schema 注释测试**

测试使用反射读取类型和 record component 的 `@Schema`：

```java
@Test
void documentsApiResponseContract() {
    Schema typeSchema = ApiResponse.class.getAnnotation(Schema.class);

    assertThat(typeSchema).isNotNull();
    assertThat(typeSchema.description()).isEqualTo("统一 API 响应信封");
    assertThat(schemaOf(ApiResponse.class, "traceId").description())
            .isEqualTo("可信调用链追踪标识");
}

@Test
void documentsFieldViolationContract() {
    Schema typeSchema = FieldViolation.class.getAnnotation(Schema.class);

    assertThat(typeSchema).isNotNull();
    assertThat(typeSchema.description()).isEqualTo("请求字段校验详情");
    assertThat(schemaOf(FieldViolation.class, "field").description())
            .isEqualTo("违反约束的字段名");
}
```

- [x] **Step 2: 运行测试确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-web -am \
  -Dtest=OpenApiSchemaAnnotationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，因为 `Schema` 依赖或注释尚不存在。

- [x] **Step 3: 增加轻量注释依赖和最小注释**

`common-web` 直接依赖：

```xml
<dependency>
    <groupId>io.swagger.core.v3</groupId>
    <artifactId>swagger-annotations-jakarta</artifactId>
</dependency>
```

类型级描述：

```java
@Schema(description = "统一 API 响应信封")
public record ApiResponse<T>(...) {
}

@Schema(description = "请求字段校验详情")
public record FieldViolation(...) {
}
```

为 record component 添加精确中文 `@Schema(description = "...")`；不改变 JSON 字段名、顺序或现有校验行为。

- [x] **Step 4: 验证 GREEN 与 JSON 回归**

Run:

```bash
mvn -pl worthit-common/worthit-common-web -am test
```

Expected: PASS，原 JSON 契约测试与新增 Schema 测试全部通过。

---

### Task 3: 测试先行实现双组 OpenAPI 自动配置

**Files:**

- Create: `worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiAutoConfigurationTest.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/openapi/OpenApiGroupingIntegrationTest.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/openapi/OpenApiGroupConstants.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiAutoConfiguration.java`
- Create: `worthit-common/worthit-common-webmvc-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**

- Produces bean: `worthItPublicOpenApi`
- Produces bean: `worthItInternalOpenApi`
- Produces group: `public`, matching `/api/**`
- Produces group: `internal`, matching `/internal/**`
- Consumes property: `springdoc.api-docs.enabled=true`

- [x] **Step 1: 编写失败的自动配置测试**

使用 `WebApplicationContextRunner` 断言：

```java
private final WebApplicationContextRunner contextRunner =
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        WorthItOpenApiAutoConfiguration.class));

@Test
void doesNotCreateGroupsWhenApiDocsAreDisabledByDefault() {
    contextRunner.run(context -> assertThat(context)
            .doesNotHaveBean(OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
            .doesNotHaveBean(OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME));
}

@Test
void createsPublicAndInternalGroupsWhenEnabled() {
    contextRunner
            .withPropertyValues("springdoc.api-docs.enabled=true")
            .run(context -> {
                assertThat(context).hasBean(OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME);
                assertThat(context).hasBean(OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME);
            });
}
```

另写自定义同名 Bean 测试，断言 `@ConditionalOnMissingBean(name = ...)` 让位。

- [x] **Step 2: 编写失败的端点隔离集成测试**

测试专用 Controller 暴露：

```text
/api/v1/test-items
/internal/v1/test-reminders
/actuator-like-test
```

MockMvc 断言：

```java
mockMvc.perform(get("/v3/api-docs/public"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/test-items']").exists())
        .andExpect(jsonPath("$.paths['/internal/v1/test-reminders']").doesNotExist())
        .andExpect(jsonPath("$.paths['/actuator-like-test']").doesNotExist());

mockMvc.perform(get("/v3/api-docs/internal"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/internal/v1/test-reminders']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/test-items']").doesNotExist())
        .andExpect(jsonPath("$.paths['/actuator-like-test']").doesNotExist());

mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isNotFound());
```

同时解析 JSON 断言 components 中生成 `ApiResponse` 与 `FieldViolation` 相关 Schema，并断言 Swagger UI 开启时可访问或重定向。

- [x] **Step 3: 运行测试确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am test
```

Expected: FAIL，因为自动配置、常量和导入文件尚不存在。

- [x] **Step 4: 实现常量与自动配置**

`OpenApiGroupConstants`：

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenApiGroupConstants {

    public static final String PUBLIC_GROUP_BEAN_NAME = "worthItPublicOpenApi";
    public static final String INTERNAL_GROUP_BEAN_NAME = "worthItInternalOpenApi";
    public static final String PUBLIC_GROUP_NAME = "public";
    public static final String INTERNAL_GROUP_NAME = "internal";
    public static final String PUBLIC_PATH_PATTERN = "/api/**";
    public static final String INTERNAL_PATH_PATTERN = "/internal/**";
}
```

每个常量写中文 Javadoc。

`WorthItOpenApiAutoConfiguration`：

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(GroupedOpenApi.class)
@ConditionalOnProperty(
        prefix = "springdoc.api-docs",
        name = "enabled",
        havingValue = "true")
public class WorthItOpenApiAutoConfiguration {

    @Bean(name = OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
    @ConditionalOnMissingBean(name = OpenApiGroupConstants.PUBLIC_GROUP_BEAN_NAME)
    public GroupedOpenApi worthItPublicOpenApi() {
        return GroupedOpenApi.builder()
                .group(OpenApiGroupConstants.PUBLIC_GROUP_NAME)
                .pathsToMatch(OpenApiGroupConstants.PUBLIC_PATH_PATTERN)
                .build();
    }

    @Bean(name = OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME)
    @ConditionalOnMissingBean(name = OpenApiGroupConstants.INTERNAL_GROUP_BEAN_NAME)
    public GroupedOpenApi worthItInternalOpenApi() {
        return GroupedOpenApi.builder()
                .group(OpenApiGroupConstants.INTERNAL_GROUP_NAME)
                .pathsToMatch(OpenApiGroupConstants.INTERNAL_PATH_PATTERN)
                .build();
    }
}
```

自动配置导入文件只包含该类的全限定名。

- [x] **Step 5: 验证 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am test
```

Expected: PASS，自动配置条件、覆盖机制、双组端点、默认端点关闭和 Schema 生成测试全部通过。

---

### Task 4: 接入三个 MVC App 并保持运行栈隔离

**Files:**

- Modify: `worthit-auth/worthit-auth-app/pom.xml`
- Modify: `worthit-tracking/worthit-tracking-app/pom.xml`
- Modify: `worthit-reminder/worthit-reminder-app/pom.xml`
- Create: `worthit-auth/worthit-auth-app/src/main/resources/application.yml`
- Create: `worthit-tracking/worthit-tracking-app/src/main/resources/application.yml`
- Create: `worthit-reminder/worthit-reminder-app/src/main/resources/application.yml`

**Interfaces:**

- Consumes: `worthit-common-webmvc-starter`
- Produces config: safe default off; `local|dev|test` on; default docs off

- [x] **Step 1: 建立接入前失败证据**

Run:

```bash
rg -l "worthit-common-webmvc-starter" \
  worthit-auth/worthit-auth-app/pom.xml \
  worthit-tracking/worthit-tracking-app/pom.xml \
  worthit-reminder/worthit-reminder-app/pom.xml
```

Expected: FAIL/无输出，三个 App 尚未接入。

- [x] **Step 2: 接入 Starter**

三个 App POM 各增加：

```xml
<dependency>
    <groupId>com.shaopc.worthit</groupId>
    <artifactId>worthit-common-webmvc-starter</artifactId>
</dependency>
```

保留 Tracking/Reminder 对 `worthit-reminder-client` 的现有依赖。

- [x] **Step 3: 添加安全环境配置**

每个 App 的 `application.yml` 使用相同三段：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
  enable-default-api-docs: false

---
spring:
  config:
    activate:
      on-profile: "local | dev | test"
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
  enable-default-api-docs: false
```

不写端口、Nacos、数据库、账号或 Secret。

- [x] **Step 4: 验证 App 与隔离依赖树**

Run:

```bash
mvn -pl \
worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
-am test
```

Expected: PASS。

Run:

```bash
mvn -pl worthit-reminder/worthit-reminder-client \
  dependency:tree -Dscope=compile
mvn -pl worthit-gateway \
  dependency:tree -Dscope=compile
mvn -pl worthit-common/worthit-common-web \
  dependency:tree -Dscope=compile
```

Expected:

- Client 只有 `spring-web`、Validation API 和其必要传递依赖，不含 Boot Starter/springdoc/Servlet/Tomcat；
- Gateway 不含 MVC Starter/spring-webmvc/Tomcat；
- `common-web` 只有 `common-core`、Jackson annotations、Swagger annotations，不含 MVC/WebFlux/Servlet/Tomcat/springdoc runtime。

---

### Task 5: 同步后端规则

**Files:**

- Modify: `AGENTS.md`
- Modify: `rules/10-architecture.md`
- Modify: `rules/20-java-code-style.md`
- Modify: `rules/30-spring-maven.md`
- Modify: `rules/40-testing-quality.md`

**Interfaces:**

- Produces: 可执行的 Starter、OpenAPI 注释、环境和分组门禁规则

- [x] **Step 1: 增加架构边界**

在 `rules/10-architecture.md`：

- 将 `common-webmvc-starter` 加入当前 Common；
- 说明它只供 Auth/Tracking/Reminder Servlet App 使用；
- 说明 `common-web`、Client、Gateway 禁止依赖该 Starter；
- 说明具体 Controller、异常适配和安全过滤器仍属于 App。

- [x] **Step 2: 增加 Swagger/OpenAPI 注释规则**

在 `rules/20-java-code-style.md` 增加：

- Controller：`@Tag`、`@Operation`、必要的 `@Parameter`/`@ApiResponses`；
- DTO/公共模型：`@Schema`；
- 描述使用中文，机器契约保持英文；
- 不得用 Swagger 注释替代 Bean Validation 或 Javadoc；
- 示例不得包含真实敏感信息。

- [x] **Step 3: 增加依赖与环境规则**

在 `rules/30-spring-maven.md` 增加：

- Client 直接用 `spring-web` 声明 HTTP Interface；
- MVC App 通过 `common-webmvc-starter` 接入运行栈；
- Gateway 禁止引入 MVC Starter；
- OpenAPI 默认/生产关闭，`local/dev/test` 显式开启；
- 默认 `/v3/api-docs` 关闭。

- [x] **Step 4: 增加测试门禁并更新 Agent 指引**

在 `rules/40-testing-quality.md` 增加 public/internal 防串组、默认端点、生产开关和依赖树检查。

在 `AGENTS.md` 增加 Web API/OpenAPI 工作必须执行双组与运行栈隔离门禁的简明入口，不复制全部细则。

- [x] **Step 5: 静态检查**

Run:

```bash
rg -n "common-webmvc-starter|@Tag|@Operation|@Schema|public.*internal|/v3/api-docs" \
  AGENTS.md rules
```

Expected: 每项规则均有唯一、明确、无冲突的归属。

---

### Task 6: 创建权威文档新版本并同步 Markdown

**Files:**

- Create: `../docs/后端架构文档/值不值小程序_微服务技术方案_V0.3.16.docx`
- Create: `../docs/后端架构文档/值不值小程序_微服务技术方案_V0.3.16.md`
- Move: `../docs/后端架构文档/值不值小程序_微服务技术方案_V0.3.15.{docx,md}` to `../docs/后端架构文档/历史归档/`
- Create: `../docs/测试文档/值不值小程序_技术测试与质量门禁_V0.2.3.docx`
- Create: `../docs/测试文档/值不值小程序_技术测试与质量门禁_V0.2.3.md`
- Move: `../docs/测试文档/值不值小程序_技术测试与质量门禁_V0.2.2.{docx,md}` to `../docs/测试文档/历史版本/`
- Modify: `../docs/README.md`

**Interfaces:**

- Produces architecture baseline: `V0.3.16`
- Produces technical gate baseline: `V0.2.3`

- [x] **Step 1: 用原 DOCX 创建新版本并做局部编辑**

使用工作区绑定的 Python：

```text
/Users/shaopc/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3
```

通过 `python-docx`：

- 全文、页眉和页脚版本 `V0.3.15 → V0.3.16`；
- 在工程树和 Common 表中加入 `worthit-common-webmvc-starter`；
- 更新 Common 分期和 ADR-045，明确三个 MVC App 共用 Starter；
- 增加 springdoc `2.8.17`、public/internal 双组、默认/生产关闭和 Gateway 不路由文档端点；
- 全文、页眉和页脚版本 `V0.2.2 → V0.2.3`；
- 增加 `TECH-OPENAPI-001~004`：双组隔离、默认端点关闭、环境开关、依赖树隔离；
- 将新增 P0 门禁并入阻塞清单。

保持既有页面、字体、表格和标题样式，不做全文重排。

- [x] **Step 2: 渲染并逐页检查 DOCX**

使用文档技能渲染器：

```bash
env TMPDIR=/private/tmp \
/Users/shaopc/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 \
/Users/shaopc/.codex/plugins/cache/openai-primary-runtime/documents/26.715.12143/skills/documents/render_docx.py \
<docx> --output_dir <qa-dir> --emit_pdf
```

Expected:

- 每页均生成 PNG；
- 中文无方框；
- 新增表格行无裁切或溢出；
- 页眉页脚版本正确；
- 无异常空白页或标题孤行。

- [x] **Step 3: 同步 Markdown**

以 DOCX 新版本正文为权威，将同一内容同步到 `V0.3.16.md` 和 `V0.2.3.md`。必须包含：

```text
worthit-common-webmvc-starter
springdoc-openapi 2.8.17
public
internal
springdoc.enable-default-api-docs=false
TECH-OPENAPI-001
TECH-OPENAPI-004
```

- [x] **Step 4: 归档旧版本并更新索引**

使用可恢复的 `mv` 将旧版本移入既有历史目录。`../docs/README.md`：

- 当前架构改为 V0.3.16；
- 技术门禁改为 V0.2.3；
- 权威关系和引用版本同步；
- Phase 0 Common 增加 WebMVC Starter；
- 保留 DOCX 为内容入口、Markdown 为镜像的规则。

- [x] **Step 5: 文档结构与一致性检查**

Run:

```bash
rg -n "V0\\.3\\.15|V0\\.2\\.2" ../docs/README.md \
  ../docs/后端架构文档/值不值小程序_微服务技术方案_V0.3.16.md \
  ../docs/测试文档/值不值小程序_技术测试与质量门禁_V0.2.3.md
```

Expected: 无旧版当前引用。

Run:

```bash
rg -n "worthit-common-webmvc-starter|2\\.8\\.17|TECH-OPENAPI-00[1-4]" \
  ../docs/后端架构文档/值不值小程序_微服务技术方案_V0.3.16.md \
  ../docs/测试文档/值不值小程序_技术测试与质量门禁_V0.2.3.md
```

Expected: 新架构与四项门禁全部存在。

---

### Task 7: 全量验收与工作区报告

**Files:**

- Modify: `docs/superpowers/plans/2026-07-23-openapi-webmvc-starter.md`（勾选完成项）

**Interfaces:**

- Produces: 构建、测试、依赖、文档和 Git 状态证据

- [x] **Step 1: 执行 Maven 全门禁**

Run:

```bash
mvn validate
mvn test
mvn package
```

Expected: 三个命令退出码均为 `0`，17 个模块成功，新增测试实际执行。

- [x] **Step 2: 执行依赖污染检查**

保存并审阅三个 compile dependency tree，确认：

```text
reminder-client: no Spring Boot Starter / springdoc / Servlet / Tomcat
gateway: no common-webmvc-starter / spring-webmvc / Tomcat
common-web: no MVC / WebFlux / Servlet / Tomcat / springdoc runtime
```

- [x] **Step 3: 执行源码与文档卫生检查**

Run:

```bash
git diff --check
rg -n "TODO|TBD|2\\.8\\.18" \
  pom.xml worthit-common worthit-auth worthit-tracking worthit-reminder \
  AGENTS.md rules docs/superpowers \
  ../docs/README.md ../docs/后端架构文档 ../docs/测试文档
```

Expected: 无空白错误、占位符或错误版本。

- [x] **Step 4: 核对分支和改动边界**

Run:

```bash
git branch --show-current
git status --short
git diff --stat
```

Expected:

- 分支为 `main`；
- 无新分支或 worktree；
- 改动只覆盖本计划登记文件；
- 没有提交或推送。

- [x] **Step 5: 输出验收报告**

报告必须区分：

- 已实现；
- 已验证及准确命令、退出码、测试数；
- DOCX 页数与逐页渲染结果；
- 未验证或后续事项；
- 当前未提交 Git 状态。
