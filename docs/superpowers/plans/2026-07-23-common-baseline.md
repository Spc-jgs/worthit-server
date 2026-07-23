# WorthIt Common Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `common-core`、`common-web`、`common-test` 建立最小可用类型、精确 JSON 契约和可复用 ArchUnit 架构门禁。

**Architecture:** Core 只承载无 Web 依赖的错误与分页模型；Web 只承载技术中立的响应模型；Test 以普通 JAR 提供可复用 `ArchRule`，消费者只能用 test scope 引入。当前实际启用 Common 门禁，并为后续 Client、Domain 提供经过正反例验证的统一规则。

**Tech Stack:** Java 17、Maven、JUnit 5、AssertJ、Jackson、ArchUnit 1.4.2

## Global Constraints

- 直接在当前 `main` 工作区实施，不创建分支或 worktree。
- 只有 `worthit-common-core`、`worthit-common-web`、`worthit-common-test` 新增 Java 源码。
- `worthit-common-security`、`worthit-common-data`、`worthit-common-http` 继续只保留 POM。
- 不创建 Spring Boot 启动类、全局异常处理器、业务错误码、中间件配置或业务代码。
- JUnit 5、AssertJ、Jackson 版本由 Spring Boot BOM 管理；ArchUnit 固定为 `1.4.2`。
- 当前实际完成 `TECH-ARCH-003`；`TECH-ARCH-001/002` 只提供并验证可复用规则，不虚报真实模块已验收。
- 现有架构是默认基线但可以被有证据地推翻；未获确认前不得静默偏离。
- 实现代码不 commit、不 push；只保留可审阅工作区差异。

---

### Task 1: Maven 依赖治理与 App 依赖门禁

**Files:**
- Modify: `pom.xml`
- Modify: `worthit-common/worthit-common-core/pom.xml`
- Modify: `worthit-common/worthit-common-web/pom.xml`
- Modify: `worthit-common/worthit-common-test/pom.xml`

**Interfaces:**
- Consumes: Spring Boot BOM 管理的 JUnit 5、AssertJ、Jackson 版本。
- Produces: ArchUnit `1.4.2` 版本治理、三个 Common 模块的最小依赖图、App artifact 禁止作为依赖的 Enforcer 门禁。

- [ ] **Step 1: 执行 Maven 配置 RED 检查**

Run:

```bash
value="$(mvn help:evaluate -Dexpression=archunit.version -q -DforceStdout)"
test "$value" != "1.4.2"
```

Expected: 成功确认 `archunit.version` 尚未解析为 `1.4.2`。

- [ ] **Step 2: 修改根 POM**

在 `<properties>` 增加：

```xml
<archunit.version>1.4.2</archunit.version>
```

在 `<dependencyManagement>` 增加：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit</artifactId>
    <version>${archunit.version}</version>
</dependency>
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>${archunit.version}</version>
</dependency>
```

在现有 Enforcer `<rules>` 增加：

```xml
<bannedDependencies>
    <excludes>
        <exclude>com.shaopc.worthit:worthit-*-app</exclude>
    </excludes>
    <searchTransitive>true</searchTransitive>
    <message>Application modules must not depend on other application modules.</message>
</bannedDependencies>
```

- [ ] **Step 3: 配置 `common-core` 测试依赖**

```xml
<dependencies>
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
</dependencies>
```

- [ ] **Step 4: 配置 `common-web` 依赖**

```xml
<dependencies>
    <dependency>
        <groupId>com.shaopc.worthit</groupId>
        <artifactId>worthit-common-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-annotations</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
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
</dependencies>
```

- [ ] **Step 5: 配置 `common-test` 依赖**

`common-test` compile 依赖：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit</artifactId>
</dependency>
```

测试依赖：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
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
```

再以 test scope 依赖：

```text
worthit-common-core
worthit-common-web
worthit-common-security
worthit-common-data
worthit-common-http
```

- [ ] **Step 6: 验证 Maven 配置**

Run:

```bash
xmllint --noout pom.xml \
  worthit-common/worthit-common-core/pom.xml \
  worthit-common/worthit-common-web/pom.xml \
  worthit-common/worthit-common-test/pom.xml
mvn validate
mvn help:evaluate -Dexpression=archunit.version -q -DforceStdout
```

Expected: XML 全部有效，Reactor `BUILD SUCCESS`，最后输出 `1.4.2`。

---

### Task 2: `common-core` 错误与分页基线

**Files:**
- Create: `worthit-common/worthit-common-core/src/main/java/com/shaopc/worthit/common/core/error/ErrorCode.java`
- Create: `worthit-common/worthit-common-core/src/main/java/com/shaopc/worthit/common/core/error/BusinessException.java`
- Create: `worthit-common/worthit-common-core/src/main/java/com/shaopc/worthit/common/core/pagination/PageQuery.java`
- Create: `worthit-common/worthit-common-core/src/main/java/com/shaopc/worthit/common/core/pagination/PageResult.java`
- Create: `worthit-common/worthit-common-core/src/test/java/com/shaopc/worthit/common/core/error/BusinessExceptionTest.java`
- Create: `worthit-common/worthit-common-core/src/test/java/com/shaopc/worthit/common/core/pagination/PageQueryTest.java`
- Create: `worthit-common/worthit-common-core/src/test/java/com/shaopc/worthit/common/core/pagination/PageResultTest.java`

**Interfaces:**
- Produces: `ErrorCode#code()`, `ErrorCode#defaultMessage()`；`BusinessException#errorCode()`、`code()`；`PageQuery.of(Integer,Integer)`；`PageResult.of(List,PageQuery,long)`。

- [ ] **Step 1: 先写错误基线测试**

测试必须包含：

```java
enum TestErrorCode implements ErrorCode {
    INVALID;

    @Override
    public String code() {
        return "VAL_INVALID";
    }

    @Override
    public String defaultMessage() {
        return "invalid";
    }
}
```

断言：

```java
BusinessException defaultException = new BusinessException(TestErrorCode.INVALID);
assertThat(defaultException.code()).isEqualTo("VAL_INVALID");
assertThat(defaultException.getMessage()).isEqualTo("invalid");

IllegalStateException cause = new IllegalStateException("root");
BusinessException custom = new BusinessException(TestErrorCode.INVALID, "custom", cause);
assertThat(custom.errorCode()).isSameAs(TestErrorCode.INVALID);
assertThat(custom.getMessage()).isEqualTo("custom");
assertThat(custom.getCause()).isSameAs(cause);
```

并断言 null ErrorCode、空 code、空 message 被拒绝。

- [ ] **Step 2: 运行错误测试确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-core \
  -Dtest=BusinessExceptionTest test
```

Expected: test compile 因 `ErrorCode`、`BusinessException` 不存在而失败。

- [ ] **Step 3: 实现错误类型**

`ErrorCode`：

```java
public interface ErrorCode {
    String code();
    String defaultMessage();
}
```

`BusinessException` 使用 `Objects.requireNonNull` 和私有 `requireText` 验证 code/message，保留 cause，并提供：

```java
public ErrorCode errorCode()
public String code()
```

- [ ] **Step 4: 运行错误测试确认 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-core \
  -Dtest=BusinessExceptionTest test
```

Expected: `BusinessExceptionTest` 全部通过。

- [ ] **Step 5: 先写分页测试**

`PageQueryTest` 断言：

```java
assertThat(PageQuery.of(null, null)).isEqualTo(new PageQuery(1, 20));
assertThat(new PageQuery(1, 50).size()).isEqualTo(50);
assertThatThrownBy(() -> new PageQuery(0, 20))
    .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new PageQuery(1, 51))
    .isInstanceOf(IllegalArgumentException.class);
```

`PageResultTest` 断言：

```java
List<String> source = new ArrayList<>(List.of("a", "b"));
PageResult<String> result = PageResult.of(source, new PageQuery(2, 2), 5);
source.add("c");

assertThat(result.getItems()).containsExactly("a", "b");
assertThat(result.isHasMore()).isTrue();
assertThatThrownBy(() -> result.getItems().add("x"))
    .isInstanceOf(UnsupportedOperationException.class);
assertThat(PageResult.of(List.of(), new PageQuery(3, 2), 5).isHasMore())
    .isFalse();
assertThatThrownBy(() -> PageResult.of(List.of(), new PageQuery(1, 20), -1))
    .isInstanceOf(IllegalArgumentException.class);
```

- [ ] **Step 6: 运行分页测试确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-core \
  -Dtest=PageQueryTest,PageResultTest test
```

Expected: test compile 因分页类型不存在而失败。

- [ ] **Step 7: 实现分页类型**

`PageQuery`：

```java
public record PageQuery(int page, int size) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(
            page == null ? DEFAULT_PAGE : page,
            size == null ? DEFAULT_SIZE : size
        );
    }
}
```

`PageResult<T>` 是 `final` 不可变类，私有构造器接收 `List<T>`、`PageQuery`、`long total`，使用 `List.copyOf`，计算：

```java
this.hasMore = (long) page * size < total;
```

提供 `getItems/getPage/getSize/getTotal/isHasMore`。

- [ ] **Step 8: 运行 Core 全部测试**

Run:

```bash
mvn -pl worthit-common/worthit-common-core test
```

Expected: `BusinessExceptionTest`、`PageQueryTest`、`PageResultTest` 全部通过。

---

### Task 3: `common-web` 精确响应契约

**Files:**
- Create: `worthit-common/worthit-common-web/src/main/java/com/shaopc/worthit/common/web/response/FieldViolation.java`
- Create: `worthit-common/worthit-common-web/src/main/java/com/shaopc/worthit/common/web/response/ApiResponse.java`
- Create: `worthit-common/worthit-common-web/src/test/java/com/shaopc/worthit/common/web/response/ApiResponseJsonTest.java`

**Interfaces:**
- Consumes: `ErrorCode`。
- Produces: `FieldViolation(field,issue)`；`ApiResponse.success(data,traceId)`；`ApiResponse.error(errorCode,traceId,details)`；`ApiResponse.error(errorCode,message,traceId,details)`。

- [ ] **Step 1: 先写 JSON 契约测试**

使用 `ObjectMapper` 和测试 ErrorCode，断言成功 JSON：

```json
{
  "success": true,
  "code": "OK",
  "message": "OK",
  "data": {"id":"1"},
  "traceId": "trace-1"
}
```

断言失败 JSON：

```json
{
  "success": false,
  "code": "VAL_INVALID_ARGUMENT",
  "message": "invalid",
  "data": null,
  "traceId": "trace-2",
  "details": [{"field":"size","issue":"must be <= 50"}]
}
```

同时断言：

- 字段顺序与接口终稿一致；
- 空 details 不输出；
- 错误 data 字段存在且为 null；
- 修改原始 details 列表不影响响应；
- 空 traceId、field、issue 被拒绝。

- [ ] **Step 2: 运行 Web 测试确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-web -am \
  -Dtest=ApiResponseJsonTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `common-web` test compile 因响应类型不存在而失败。

- [ ] **Step 3: 实现 `FieldViolation`**

```java
public record FieldViolation(String field, String issue) {
    public FieldViolation {
        field = requireText(field, "field");
        issue = requireText(issue, "issue");
    }
}
```

私有 `requireText` 拒绝 null 和 blank。

- [ ] **Step 4: 实现 `ApiResponse<T>`**

使用：

```java
@JsonPropertyOrder({"success", "code", "message", "data", "traceId", "details"})
public record ApiResponse<T>(
    boolean success,
    String code,
    String message,
    T data,
    String traceId,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<FieldViolation> details
) {
}
```

compact constructor 验证 code/message/traceId，使用 `List.copyOf`。工厂方法：

```java
public static <T> ApiResponse<T> success(T data, String traceId)
public static <T> ApiResponse<T> error(
    ErrorCode errorCode,
    String traceId,
    List<FieldViolation> details
)
public static <T> ApiResponse<T> error(
    ErrorCode errorCode,
    String message,
    String traceId,
    List<FieldViolation> details
)
```

失败工厂固定 `data=null`；默认失败消息使用 `errorCode.defaultMessage()`。

- [ ] **Step 5: 运行 Web 测试确认 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-web -am \
  -Dtest=ApiResponseJsonTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: Core 和 Web 构建成功，`ApiResponseJsonTest` 全部通过。

---

### Task 4: 可复用 ArchUnit 规则与实际 Common 门禁

**Files:**
- Create: `worthit-common/worthit-common-test/src/main/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRules.java`
- Create: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/WorthItArchitectureRulesTest.java`
- Create: `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/test/architecture/CommonArchitectureTest.java`
- Create fixture classes under:
  - `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/common/fixture/`
  - `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/tracking/fixture/`
  - `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/reminder/client/fixture/`
  - `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/reminder/app/fixture/`
  - `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/tracking/item/domain/fixture/`
  - `worthit-common/worthit-common-test/src/test/java/com/shaopc/worthit/tracking/item/infrastructure/fixture/`

**Interfaces:**
- Produces: `WorthItArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_BUSINESS`、`CLIENT_MUST_NOT_DEPEND_ON_APP`、`DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORKS`。

- [ ] **Step 1: 先写规则正反例测试**

通过 `ClassFileImporter#importClasses` 导入 fixture。

正例：

```java
assertThatCode(() ->
    WorthItArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_BUSINESS.check(validClasses)
).doesNotThrowAnyException();
```

三个反例分别断言：

```java
assertThatThrownBy(() -> rule.check(invalidClasses))
    .isInstanceOf(AssertionError.class);
```

Fixture 依赖：

- `CommonDependsOnTrackingFixture` 持有 `TrackingFixture`；
- `ClientDependsOnAppFixture` 持有 `ReminderAppFixture`；
- `DomainDependsOnInfrastructureFixture` 持有 `TrackingInfrastructureFixture`。

- [ ] **Step 2: 运行规则测试确认 RED**

Run:

```bash
mvn -pl worthit-common/worthit-common-test -am \
  -Dtest=WorthItArchitectureRulesTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compile 因 `WorthItArchitectureRules` 不存在而失败。

- [ ] **Step 3: 实现规则库**

使用 `ArchRuleDefinition.noClasses()`：

```java
public static final ArchRule COMMON_MUST_NOT_DEPEND_ON_BUSINESS =
    noClasses()
        .that().resideInAPackage("com.shaopc.worthit.common..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "com.shaopc.worthit.auth..",
            "com.shaopc.worthit.tracking..",
            "com.shaopc.worthit.reminder..",
            "com.shaopc.worthit.gateway.."
        )
        .because("Common must stay independent from business services")
        .allowEmptyShould(false);
```

Client 规则匹配 `..client..`，禁止依赖 `..app..`、`..application..`、`..domain..`、`..infrastructure..`。

Domain 规则匹配 `..domain..`，禁止依赖：

```text
..interfaces..
..infrastructure..
org.springframework.web..
com.baomidou.mybatisplus..
org.apache.ibatis..
```

三个规则都使用 `allowEmptyShould(false)`。

- [ ] **Step 4: 运行规则测试确认 GREEN**

Run:

```bash
mvn -pl worthit-common/worthit-common-test -am \
  -Dtest=WorthItArchitectureRulesTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 正例通过，三个违规 fixture 均被捕获，测试整体通过。

- [ ] **Step 5: 写实际 Common 门禁**

```java
@AnalyzeClasses(
    packages = "com.shaopc.worthit.common",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class CommonArchitectureTest {

    @ArchTest
    static final ArchRule commonMustNotDependOnBusiness =
        WorthItArchitectureRules.COMMON_MUST_NOT_DEPEND_ON_BUSINESS;

    @ArchTest
    static void mustImportProductionClasses(JavaClasses classes) {
        assertThat(classes).isNotEmpty();
    }
}
```

- [ ] **Step 6: 运行实际门禁**

Run:

```bash
mvn -pl worthit-common/worthit-common-test -am \
  -Dtest=CommonArchitectureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 导入实际 Common 生产类，`TECH-ARCH-003` 通过且不是空扫描。

---

### Task 5: 架构治理规则与全量验收

**Files:**
- Modify: `AGENTS.md`
- Modify: `rules/10-architecture.md`
- Verify: all changed POM and Java files

**Interfaces:**
- Produces: “架构可被证据推翻，但不能静默偏离”的仓库治理规则和完整验收证据。

- [ ] **Step 1: 执行治理规则 RED 检查**

Run:

```bash
rg -n '架构.*可.*推翻|不能静默偏离' AGENTS.md rules/10-architecture.md
```

Expected: 退出码非 0，当前规则尚未完整表达新原则。

- [ ] **Step 2: 修改 `AGENTS.md`**

在“工作方式”增加：

```markdown
- 现有架构是当前默认基线，不是不可修改的教条；发现问题时应主动提出证据、备选方案、影响、迁移和回滚设计。
- 获得用户确认后可以推翻或重构现有架构，并同步更新代码、规则和权威文档；确认前不能静默偏离。
```

- [ ] **Step 3: 修改 `rules/10-architecture.md`**

在“架构变更门禁”前增加“架构评估与重构”章节，明确：

- 主动审查现有架构；
- 提交问题证据、现状风险、至少两个方案、推荐理由、影响、测试、迁移、回滚和文档范围；
- 获批后允许改变模块、依赖、技术选型和终稿；
- 未获批前按当前基线实施；
- 不以“文档已冻结”为理由拒绝合理重构，也不以“架构有问题”为理由先斩后奏。

- [ ] **Step 4: 执行治理规则 GREEN 检查**

Run:

```bash
rg -n '架构.*可.*推翻|不能静默偏离|至少两个.*方案|回滚' \
  AGENTS.md rules/10-architecture.md
```

Expected: 新治理原则全部命中。

- [ ] **Step 5: 验证源码范围**

Run:

```bash
test -z "$(find \
  worthit-common/worthit-common-security \
  worthit-common/worthit-common-data \
  worthit-common/worthit-common-http \
  -type f ! -name pom.xml ! -path '*/target/*' -print -quit)"
find worthit-common -type f -name '*.java' | sort
```

Expected: 只有 core、web、test 三个模块输出 Java 文件。

- [ ] **Step 6: 执行全量 Maven 门禁**

Run:

```bash
mvn validate
mvn test
mvn package
```

Expected: 16 个 Reactor 模块全部成功；新增 Unit、JSON Contract、ArchUnit 测试均实际执行。

- [ ] **Step 7: 检查依赖边界**

Run:

```bash
mvn -pl worthit-common/worthit-common-web -am package dependency:tree \
  -Dincludes=com.shaopc.worthit
mvn -pl worthit-common/worthit-common-test -am package dependency:tree \
  -Dincludes=com.shaopc.worthit
```

Expected:

- Web 只依赖 Core；
- Test 只在 test scope 依赖其余 Common；
- 不存在 App → App 实现依赖。

- [ ] **Step 8: 完成 Git 与范围检查**

Run:

```bash
git diff --check
git status --short --untracked-files=all
git branch --show-current
git worktree list
```

Expected:

- `git diff --check` 无输出；
- 当前分支为 `main`；
- 未创建新分支或 worktree；
- 只有本计划列出的实现、测试、POM、规则文件发生变化；
- 实现代码未暂存、未提交、未推送。
