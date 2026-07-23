# WorthIt Common 最小基线与架构门禁设计

日期：2026-07-23

## 1. 目标

在不创建分支、不创建 worktree、不启动四个服务、不接入中间件的前提下，为 WorthIt Server 建立第一组真实可用的 Common 类型和可执行架构门禁。

本轮只让以下模块产生真实代码：

- `worthit-common-core`
- `worthit-common-web`
- `worthit-common-test`

以下模块继续只保留 POM：

- `worthit-common-security`
- `worthit-common-data`
- `worthit-common-http`

## 2. 架构治理原则

现有架构文档是当前默认实现基线，但不是不可质疑或不可修改的最终真理。Agent 和开发者有责任主动识别不合理的模块边界、依赖方向、技术选型、数据流和质量门禁。

发现架构问题时，应先提交：

1. 可复现的问题和证据；
2. 继续使用现状的风险；
3. 两个以上可行方案及推荐方案；
4. 对模块、契约、数据、部署、测试和迁移的影响；
5. 回滚方式和文档同步范围。

获得项目负责人确认后，可以推翻或重构既有架构，并同步修改实现、规则和对应权威文档。未获得确认前，现有架构仍作为默认基线，禁止静默偏离。

本轮将把该原则写入根 `AGENTS.md` 和 `rules/10-architecture.md`。

## 3. 方案选择

### 3.1 采用方案

采用“`common-test` 可复用规则库 + 当前 Common 实际门禁”：

- `common-test` 主源码提供统一 ArchUnit 规则；
- `common-test` 测试源码验证规则能够识别正例和反例；
- `CommonArchitectureTest` 扫描六个 Common 模块的生产类；
- 后续 App/Client 模块通过 test scope 依赖 `common-test` 并复用同一规则。

### 3.2 未采用方案

- 不在每个模块复制 ArchUnit 规则，避免规则漂移。
- 不新建 `architecture-tests` 模块，避免偏离现有模块终稿。
- 不把架构测试放进生产 App，也不让 `common-test` 成为生产运行依赖。

## 4. `common-core`

### 4.1 `ErrorCode`

包：

```text
com.shaopc.worthit.common.core.error
```

接口只包含：

```java
String code();
String defaultMessage();
```

`ErrorCode` 不包含 HTTP 状态。HTTP 映射属于 Web/App 边界，不能反向污染 Core。

本轮不在 Common 定义 Auth、Tracking、Reminder 或业务错误码枚举。具体错误码由契约所有者维护。

### 4.2 `BusinessException`

`BusinessException`：

- 继承 `RuntimeException`；
- 保存非空 `ErrorCode`；
- 支持默认消息；
- 支持自定义消息；
- 支持保留原始 cause；
- 提供稳定 code 和 ErrorCode 访问方法。

不得用异常 message 代替稳定业务 code。

### 4.3 `PageQuery`

包：

```text
com.shaopc.worthit.common.core.pagination
```

冻结规则：

- `page` 从 `1` 开始；
- `size` 默认 `20`；
- `size` 最小 `1`、最大 `50`；
- 提供处理可空外部参数的工厂方法；
- 构造后不可变；
- 非法值立即抛出 `IllegalArgumentException`。

分页基础类型不绑定 Spring MVC 注解或 MyBatis 类型。

### 4.4 `PageResult<T>`

字段：

- `items`
- `page`
- `size`
- `total`
- `hasMore`

约束：

- `items` 使用不可变副本；
- `total` 不小于 `0`；
- `page`、`size` 使用 `PageQuery` 的相同约束；
- `hasMore` 由 `(long) page * size < total` 统一计算；
- 调用方不能传入自相矛盾的 `hasMore`。

## 5. `common-web`

`common-web` 依赖 `common-core`，但不依赖 Spring MVC、WebFlux、Servlet API 或 Gateway。

### 5.1 `FieldViolation`

包：

```text
com.shaopc.worthit.common.web.response
```

字段：

- `field`
- `issue`

两个字段均不可为空或空白。

### 5.2 `ApiResponse<T>`

JSON 字段顺序和名称固定为：

```text
success
code
message
data
traceId
details
```

约束：

- 成功工厂固定 `success=true`、`code=OK`、`message=OK`；
- 失败工厂接收 `ErrorCode`；
- 失败响应固定 `success=false`、`data=null`；
- `traceId` 不可为空或空白；
- `details` 使用不可变副本；
- `details` 为空时不输出该 JSON 字段；
- 错误响应仍输出 `"data": null`；
- 不在本轮创建全局异常处理器。

全局异常适配留到 App 阶段分别实现 WebFlux 和 Servlet/MVC 版本。

## 6. `common-test` 与架构门禁

### 6.1 依赖

- ArchUnit `1.4.2`；
- JUnit 5、AssertJ 和 Jackson 版本继续由 Spring Boot BOM 管理；
- `common-test` 在测试作用域依赖其余五个 Common 模块，以扫描其生产类；
- 消费者只能以 test scope 依赖 `common-test`。

### 6.2 可复用规则

`WorthItArchitectureRules` 提供：

1. Common 不得依赖 Auth、Tracking、Reminder、Gateway 业务包；
2. Client 不得依赖 App 或 Infrastructure；
3. Domain 不得依赖 Web、MyBatis、Interfaces 或 Infrastructure。

规则使用 ArchUnit `ArchRule`，由后续模块的 `ArchitectureTest` 直接复用。

### 6.3 当前实际门禁

`CommonArchitectureTest`：

- 使用 JUnit 5 ArchUnit Engine；
- 扫描 `com.shaopc.worthit.common..`；
- 排除 test classes；
- 对实际生产类执行 Common 依赖规则；
- 禁止“没有类可检查”时静默通过。

规则单元测试使用测试 fixture 证明：

- 合法 Common 类型可以通过；
- Common 引用 Tracking fixture 时规则失败；
- Client 引用 App fixture 时规则失败；
- Domain 引用 Infrastructure fixture 时规则失败。

### 6.4 门禁状态

- `TECH-ARCH-003` 在本轮成为真实可执行门禁。
- `TECH-ARCH-001` 和 `TECH-ARCH-002` 在本轮提供可复用规则，但 Tracking Domain 和 Reminder Client 尚无真实源码，不能宣称已经完成实际模块验收。
- `TECH-ARCH-004` 通过 Maven Enforcer 禁止 App artifact 成为其他模块依赖，并补充依赖树检查。

## 7. Maven 变化

根 POM：

- 新增 `archunit.version=1.4.2`；
- 在 `dependencyManagement` 管理 ArchUnit core 和 JUnit 5 便利依赖；
- 在现有 Enforcer 执行中增加禁止依赖任何 `worthit-*-app` artifact 的规则。

模块 POM：

- `common-core`：JUnit 5、AssertJ 仅用于测试；
- `common-web`：compile 依赖 `common-core` 和 Jackson annotations；测试依赖 Jackson databind、JUnit 5、AssertJ；
- `common-test`：compile 依赖 ArchUnit core；测试依赖 ArchUnit JUnit 5、JUnit 5、AssertJ，以及其余五个 Common 模块；
- `common-security`、`common-data`、`common-http` 不增加源码或运行依赖。

## 8. 测试策略

遵循 RED → GREEN → REFACTOR：

1. 先编写 Core 类型测试并确认因类型不存在而失败；
2. 实现最小 Core 类型并通过测试；
3. 先编写 `ApiResponse` JSON 契约测试并确认失败；
4. 实现 Web 类型并通过测试；
5. 先编写架构规则正反例和实际 Common 门禁并确认失败；
6. 实现规则库并通过测试；
7. 执行全 Reactor 验证。

重点断言：

- 错误 code 与 message 行为；
- 分页默认值、边界、不可变 items 和 `hasMore`；
- 成功与失败响应精确 JSON 结构；
- 空 `details` 不输出、错误 `data` 保留为 null；
- 三类架构违规能被 ArchUnit 捕获；
- Common 实际生产代码满足依赖边界。

## 9. 验收标准

必须全部满足：

- 只有 `common-core`、`common-web`、`common-test` 出现 Java 源码；
- `common-security`、`common-data`、`common-http` 仍只有 POM；
- `mvn validate` 成功；
- `mvn test` 成功且实际执行新增测试；
- `mvn package` 成功；
- `TECH-ARCH-003` 实际执行并通过；
- TECH-ARCH-001/002 的规则反例测试证明能够拦截违规；
- Maven Enforcer 拦截 App → App artifact 依赖；
- 依赖树不存在 App → App 实现依赖；
- `git diff --check` 通过；
- 当前分支仍为 `main`；
- 不创建新分支或 worktree；
- 未经用户另行要求，不提交或推送实现代码。
