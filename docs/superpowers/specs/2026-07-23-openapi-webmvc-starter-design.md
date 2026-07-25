# WorthIt OpenAPI 与 WebMVC Starter 设计

**日期：** 2026-07-23

**状态：** 已确认并实现

**适用范围：** `worthit-common-web`、新增 `worthit-common-webmvc-starter`、Auth/Tracking/Reminder App

## 1. 目标

为三个 Servlet/MVC 业务服务建立统一的 Web 运行时依赖和 OpenAPI 基线，同时保持公共模型、纯契约 Client 与 Gateway 的依赖边界清晰。

本轮目标：

- `common-web` 继续保持 Servlet/WebFlux 中立；
- 新增仅供 MVC App 使用的 `worthit-common-webmvc-starter`；
- Auth、Tracking、Reminder App 通过该 Starter 获得 Spring MVC、Bean Validation 和 springdoc；
- OpenAPI 固定为 `public`、`internal` 两组；
- 默认环境和生产环境关闭 API Docs 与 Swagger UI；
- `local`、`dev`、`test` 环境显式开启；
- 用自动化测试阻止公网与内部接口在文档中串组；
- 将 Swagger/OpenAPI 注释要求写入后端规则。

本轮不实现任何业务 Controller、Application Service、数据库逻辑、Gateway 文档聚合或公网文档发布。

## 2. 现状与问题

### 2.1 `reminder-client` 的 `spring-web` 依赖是正确的

`worthit-reminder-client` 只声明 `@HttpExchange`、`@PostExchange`、`@RequestHeader` 和 `@RequestBody` 等 Spring HTTP Interface 契约，因此直接依赖 `org.springframework:spring-web`。

它不得改为 `spring-boot-starter-web`，因为纯契约 Client 不拥有：

- Spring Boot 自动配置；
- Servlet/MVC 运行时；
- 内嵌 Web Server；
- Controller；
- HTTP Client 代理装配。

代理创建、LoadBalancer、超时、认证、TraceId 和错误解码仍属于调用方 Infrastructure 与后续 `common-http`。

### 2.2 `common-web` 不能直接引入 `spring-boot-starter-web`

直接引入不会自动形成 Maven 循环依赖，但会通过传递依赖把 MVC、Servlet 和内嵌服务器带给所有 `common-web` 使用者，破坏技术中立边界，并增加 Gateway 误引入 Servlet 栈的风险。

因此：

- `common-web` 只承载跨运行模型可复用的 JSON/响应/Schema 模型；
- MVC 运行时依赖和自动配置进入独立 Starter；
- Gateway 不依赖该 Starter。

## 3. 候选方案

### 3.1 方案 A：每个 App 分别声明全部 Web 与 springdoc 依赖

优点：

- 模块最少；
- 每个服务依赖最直观。

缺点：

- 三个 MVC App 重复依赖、分组配置和环境开关；
- OpenAPI 规则容易漂移；
- 公网/内部串组门禁难以复用。

### 3.2 方案 B：把 Starter 直接放进 `common-web`

优点：

- App 接入最省事。

缺点：

- `common-web` 不再中立；
- 公共响应模型会强制携带 Servlet/MVC 运行时；
- Gateway 与后续非 MVC 消费者更容易被传递依赖污染。

该方案不采用。

### 3.3 选定方案：中立 `common-web` + 独立 WebMVC Starter

当前存在 Auth、Tracking、Reminder 三个真实 MVC 使用者，已经满足“多个真实 App 重复同一运行时装配”这一 Starter 准入条件。

该方案：

- 保留清晰依赖方向；
- 统一三套 MVC/OpenAPI 装配；
- 不污染 Gateway 和 Client；
- 允许未来按真实需求单独增加 WebFlux Starter，而不是现在提前创建。

## 4. 架构变更

### 4.1 模块变化

在 `worthit-common` 下新增：

```text
worthit-common-webmvc-starter
```

根 Reactor、`worthit-common/pom.xml` 和根 `dependencyManagement` 同步登记该模块。

### 4.2 依赖方向

```text
worthit-common-core
        ↑
worthit-common-web
        ↑
worthit-common-webmvc-starter
        ↑
auth-app / tracking-app / reminder-app
```

独立边界：

```text
reminder-client -> spring-web
gateway         -> WebFlux/Gateway 运行栈
```

禁止：

- `common-web -> common-webmvc-starter`；
- `reminder-client -> common-webmvc-starter`；
- `gateway -> common-webmvc-starter`；
- `common-core -> Swagger/Spring Web`；
- MVC Starter 依赖任意业务 App、业务 Client 或业务 DTO。

### 4.3 对现行架构文档的调整

现行终稿要求 MVC/WebFlux 的运行时适配暂留 App。新方案只调整其中已经出现三处真实重复的 MVC 基线：

- 通用 MVC 依赖和 OpenAPI 自动配置提取到独立 Starter；
- 具体 Controller、异常映射、安全过滤器和服务专属配置仍留在 App；
- WebFlux/Gateway 规则不变；
- `common-web` 技术中立规则不变。

实施时必须同步：

- `AGENTS.md`；
- `rules/10-architecture.md`；
- `rules/20-java-code-style.md`；
- `rules/30-spring-maven.md`；
- `rules/40-testing-quality.md`；
- 上级 `docs/README.md` 登记的架构与技术门禁终稿。

## 5. Maven 与版本

根 POM 新增：

```xml
<springdoc-openapi.version>2.8.17</springdoc-openapi.version>
<swagger-core.version>2.2.47</swagger-core.version>
```

版本选择依据：

- springdoc `2.8.x` 是 Spring Boot 3.5 的兼容线；
- `2.8.17` 是当前可解析的稳定版本；
- 其发布 POM 使用 Spring Boot `3.5.13` 构建，并管理 Swagger Core `2.2.47`；
- 已在项目当前 Maven 仓库配置下验证 `springdoc-openapi-starter-webmvc-ui:2.8.17` 可完整解析。

根 `dependencyManagement` 统一管理：

- `org.springdoc:springdoc-openapi-starter-webmvc-ui`；
- `io.swagger.core.v3:swagger-annotations-jakarta`；
- 内部 `worthit-common-webmvc-starter`。

`worthit-common-web` 直接依赖轻量的：

```text
io.swagger.core.v3:swagger-annotations-jakarta
```

该依赖只提供 `@Schema` 等契约注释，不带 Spring MVC、Servlet、Swagger UI 或 Boot 自动配置。

`worthit-common-webmvc-starter` 直接依赖：

```text
worthit-common-web
spring-boot-starter-web
spring-boot-starter-validation
springdoc-openapi-starter-webmvc-ui
```

即使 springdoc 当前传递带入部分组件，Starter 仍直接声明自身公开运行时职责，避免依赖偶然的传递关系。

## 6. Starter 代码结构

```text
worthit-common-webmvc-starter
└── src
    ├── main
    │   ├── java/com/shaopc/worthit/common/webmvc
    │   │   ├── autoconfigure
    │   │   │   └── WorthItOpenApiGroupsAutoConfiguration.java
    │   │   └── openapi
    │   │       └── OpenApiGroupConstants.java
    │   └── resources/META-INF/spring
    │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test
        └── java/...
```

规则：

- 自动配置使用 `@AutoConfiguration`；
- 使用 `@ConditionalOnWebApplication(type = SERVLET)`；
- 使用 `@ConditionalOnClass(GroupedOpenApi.class)`；
- 只有 `springdoc.api-docs.enabled=true` 时创建分组；
- 组 Bean 使用 `@ConditionalOnMissingBean(name = ...)`，允许 App 在明确需要时覆盖；
- 所有公开类型、重要方法、常量和枚举写中文 Javadoc；
- 不在 Starter 中放业务 Controller、业务错误码或服务专属安全策略。

## 7. OpenAPI 分组

固定两个组：

| 组名 | 匹配路径 | JSON 地址 |
| --- | --- | --- |
| `public` | `/api/**` | `/v3/api-docs/public` |
| `internal` | `/internal/**` | `/v3/api-docs/internal` |

Swagger UI：

```text
/swagger-ui.html
```

固定配置：

```yaml
springdoc:
  enable-default-api-docs: false
```

关闭默认 `/v3/api-docs` 全量文档，避免它绕过分组后同时暴露公网和内部接口。只保留两个显式分组端点。

分组必须满足：

- `public` 不包含任何 `/internal/**`；
- `internal` 不包含任何 `/api/**`；
- 非上述前缀的 Actuator、错误页、测试端点或框架端点不进入任一组；
- M2/M3 未实现接口不得提前出现在文档中；
- Gateway 不配置 `/v3/api-docs/**` 或 `/swagger-ui/**` 的公网路由。

## 8. 环境开关

使用 springdoc 官方属性，不再创造第二套同义开关。

每个 MVC App 的安全公共默认值：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
  enable-default-api-docs: false
```

`local`、`dev`、`test` Profile 显式覆盖：

```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
  enable-default-api-docs: false
```

生产环境不依赖“没有激活某 Profile”这一偶然行为，公共默认即关闭；生产配置也应重复声明关闭，形成部署侧显式门禁。

如果未来需要对外发布文档，应走独立的构建产物与受控发布流程，不直接开放生产运行实例的 Swagger UI。

## 9. OpenAPI 注释规范

### 9.1 Controller

- Controller 使用 `@Tag(name = "...", description = "中文说明")`；
- 公开接口方法使用 `@Operation(summary = "中文摘要", description = "中文说明")`；
- 请求头、路径参数和容易误解的查询参数使用 `@Parameter`；
- 对业务有意义的 HTTP 状态和统一错误响应使用 `@ApiResponses`；
- 不用注释虚构尚未实现的成功路径、字段或错误码。

### 9.2 DTO 与公共模型

- 公网 Request/Response、内部 Client DTO 和公共响应模型使用 `@Schema`；
- 类型写清业务含义，字段写清单位、格式、是否可空和稳定机器值；
- 枚举机器值保持英文大写，说明使用中文；
- 示例不得包含真实手机号、Token、Secret、OpenId 或其他敏感数据；
- Bean Validation 负责可执行约束，`@Schema` 负责文档语义，两者不得互相替代。

### 9.3 Javadoc 与 Swagger 的关系

- Javadoc 面向维护源码的开发者；
- Swagger/OpenAPI 注释面向接口消费者；
- 重要类型和方法仍必须写 Javadoc，不能以 `@Operation`、`@Schema` 代替；
- 机器字段名、路径、请求头、错误码和枚举值保持英文契约，描述和校验消息使用中文。

### 9.4 本轮公共模型

为以下类型补充 `@Schema`：

- `ApiResponse<T>`；
- `FieldViolation`。

常量类不添加无意义的 Swagger 注释。

本轮不为尚未存在的业务 Controller 编写占位 OpenAPI 注释。Reminder Client 的业务 DTO 注释可在服务端 Controller 接入前的契约阶段单独完成。

## 10. 测试策略

遵循 RED → GREEN → REFACTOR。

### 10.1 自动配置测试

使用测试专用 Servlet 应用和测试 Controller 验证：

- 默认配置下不创建 WorthIt 分组 Bean；
- `springdoc.api-docs.enabled=true` 时创建且只创建 `public`、`internal` 两个 WorthIt 分组；
- 非 Servlet 环境不装配该 Starter；
- App 自定义同名 Bean 时自动配置让位。

### 10.2 文档端点测试

使用测试 fixture Controller：

- `/api/v1/test-items`；
- `/internal/v1/test-reminders`；
- `/actuator-like-test`。

通过 MockMvc 获取并解析 OpenAPI JSON，断言：

- `/v3/api-docs/public` 只含公网 fixture；
- `/v3/api-docs/internal` 只含内部 fixture；
- 两组均不含无关 fixture；
- `/v3/api-docs` 不存在；
- `ApiResponse`、`FieldViolation` Schema 能生成；
- Swagger UI 只在开启配置时可访问。

### 10.3 依赖与架构门禁

必须验证：

- `reminder-client` 编译依赖树不含任何 Spring Boot Starter、springdoc、Servlet 或 Tomcat；
- `gateway` 编译依赖树不含 `common-webmvc-starter`、`spring-boot-starter-web`、`spring-webmvc` 或 Tomcat；
- `common-web` 编译依赖树不含 Spring MVC、WebFlux、Servlet、Tomcat 或 springdoc runtime；
- 三个 MVC App 通过 `common-webmvc-starter` 获得 MVC 运行时；
- Common 仍不依赖业务包；
- App 仍不依赖其他 App。

## 11. 实施边界

本轮会修改：

- 根 POM；
- `worthit-common/pom.xml`；
- 新 Starter 模块；
- `worthit-common-web` 的 POM、公共 Schema 注释及测试；
- Auth、Tracking、Reminder App 的 POM 与 OpenAPI 环境配置；
- 后端规则；
- 对应权威架构文档和技术门禁文档。

本轮不会修改：

- `reminder-client` 的 `spring-web` 依赖；
- Gateway 运行依赖或路由；
- 业务 Controller、DTO 字段、接口路径和错误码；
- `common-http`；
- 数据库、Flyway、Nacos、Security、TraceId 和 Same-Token 实现；
- 前端代码。

由于当前三个 App 尚未建立启动类和业务 Controller，本轮能够完成 Starter 自身的真实 Spring 容器与 MockMvc 集成验证，但不能宣称三个业务服务已经生成可联调的实际业务 OpenAPI 文档。业务文档会随 App 启动基线和 Controller 落地后出现。

## 12. 验收标准

必须全部满足：

- springdoc `2.8.17` 与 Swagger annotations `2.2.47` 由根 POM 统一管理；
- 新 Starter 被 Reactor 和三个 MVC App 正确接入；
- `common-web` 仍保持运行模型中立；
- `reminder-client` 仍是纯契约模块；
- Gateway 未引入 MVC Starter；
- `public`、`internal` 分组和路径隔离测试通过；
- 默认 `/v3/api-docs` 已关闭；
- 默认和生产文档/UI 关闭，`local`、`dev`、`test` 显式开启；
- Swagger 注释规范进入后端规则；
- `mvn validate`、`mvn test`、`mvn package` 通过；
- 相关 `dependency:tree` 与架构测试通过；
- `git diff --check` 通过；
- 当前分支仍为 `main`；
- 不创建新分支或 worktree；
- 未经用户另行要求，不提交或推送。

## 13. 后续顺序

本设计实现并验收后，下一阶段单独设计 `common-http`，处理：

- `RestClient` / HTTP Interface 代理创建；
- 服务发现与 LoadBalancer；
- 超时；
- Same-Token；
- 可信 TraceId；
- `X-Caller-Service`；
- 统一错误解码。

`common-http` 不与本 Starter 混做，避免把服务端 Web 运行时与调用端 HTTP 能力耦合。

## 14. 回滚

如实现验证发现 Starter 不能稳定保持边界：

1. 移除三个 App 对 `worthit-common-webmvc-starter` 的依赖；
2. 从 Reactor 和 `dependencyManagement` 移除该模块；
3. 将 MVC 与 springdoc 依赖恢复为各 App 直接声明；
4. 保留 `common-web` 的轻量 Schema 注释依赖；
5. 同步回退规则与权威架构文档中的 Starter 描述。

回滚不影响 `reminder-client` 的纯契约设计，也不影响 Gateway 的 WebFlux 边界。

## 15. 参考资料与版本核验

- [Spring Boot 3.5 Web 官方文档](https://docs.spring.io/spring-boot/3.5/reference/web/index.html)
- [Spring Framework 6.2 HTTP Interface 官方文档](https://docs.spring.io/spring-framework/reference/6/integration/rest-clients.html#rest-http-interface)
- [springdoc 官方文档](https://springdoc.org/)
- [springdoc-openapi v2.8.17 发布记录](https://github.com/springdoc/springdoc-openapi/releases/tag/v2.8.17)

本机核验命令：

```bash
mvn dependency:get \
  -Dartifact=org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17 \
  -Dtransitive=true
```

核验结果：退出码 `0`，当前 Maven 仓库配置可解析完整传递依赖。发布 POM 中 `swagger-api.version=2.2.47`。
