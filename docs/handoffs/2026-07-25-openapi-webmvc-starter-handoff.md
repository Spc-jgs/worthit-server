# OpenAPI 与 WebMVC Starter 交接说明

## 1. 当前状态

- 交接日期：2026-07-25
- 仓库：`worthit-server`
- 分支：`main`
- 修改基线：`88f8fd8`
- 基线与 `origin/main` 一致。
- 本交接涉及的重命名、注释和本文件尚未提交。

已完成 WebMVC Starter 与双组 OpenAPI 基线。三个 MVC App 共用
`worthit-common-webmvc-starter`；Gateway、`reminder-client` 和
`common-web` 继续保持运行栈隔离。

## 2. springdoc 与 WorthIt 配置层的职责

项目没有自行实现 Swagger/OpenAPI 框架。

`org.springdoc:springdoc-openapi-starter-webmvc-ui` 负责：

- 扫描 Spring MVC Controller；
- 解析 Spring Web、`@Operation`、`@Schema` 等契约元数据；
- 生成 OpenAPI JSON；
- 提供 Swagger UI；
- 根据 `GroupedOpenApi` Bean 生成分组文档端点。

`WorthItOpenApiGroupsAutoConfiguration` 只负责：

- 向 springdoc 声明 `public=/api/**`；
- 向 springdoc 声明 `internal=/internal/**`；
- 仅在 Servlet 应用、springdoc 存在且 API Docs 显式开启时装配；
- 允许业务 App 通过同名 Bean 覆盖某个默认分组。

该类不得承载 Controller 扫描、Schema 生成、Swagger UI 实现、业务接口信息、
业务错误码或服务专属安全策略。它是 WorthIt 的分组约定层，不是 Swagger 框架。

## 3. 关键文件

- Starter POM：
  `worthit-common/worthit-common-webmvc-starter/pom.xml`
- 分组自动配置：
  `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiGroupsAutoConfiguration.java`
- 分组常量：
  `worthit-common/worthit-common-webmvc-starter/src/main/java/com/shaopc/worthit/common/webmvc/openapi/OpenApiGroupConstants.java`
- Spring Boot 自动配置登记：
  `worthit-common/worthit-common-webmvc-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 自动配置条件测试：
  `worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/autoconfigure/WorthItOpenApiGroupsAutoConfigurationTest.java`
- 分组端点集成测试：
  `worthit-common/worthit-common-webmvc-starter/src/test/java/com/shaopc/worthit/common/webmvc/openapi/OpenApiGroupingIntegrationTest.java`
- 项目规则：
  `AGENTS.md`、`rules/10-architecture.md`、`rules/20-java-code-style.md`、
  `rules/30-spring-maven.md`、`rules/40-testing-quality.md`

## 4. 固定行为

| 项目 | 约定 |
| --- | --- |
| 公网组 | `public`，只匹配 `/api/**` |
| 内部组 | `internal`，只匹配 `/internal/**` |
| 公网文档 JSON | `/v3/api-docs/public` |
| 内部文档 JSON | `/v3/api-docs/internal` |
| 默认全量文档 | 通过 `springdoc.enable-default-api-docs=false` 关闭 |
| 默认及生产环境 | API Docs 与 Swagger UI 关闭 |
| `local`、`dev`、`test` | API Docs 与 Swagger UI 显式开启 |
| Gateway | 不路由 `/v3/api-docs/**`、`/swagger-ui/**` |

不要把 `/internal/**` 合并进公网分组，也不要重新开放默认
`/v3/api-docs` 全量端点。

## 5. 本轮澄清性修改

原类名：

```text
WorthItOpenApiAutoConfiguration
```

新类名：

```text
WorthItOpenApiGroupsAutoConfiguration
```

重命名目的仅是明确“该类负责分组”，运行行为没有变化。类级 Javadoc 已明确：

- 文档生成和 UI 由 springdoc 提供；
- 本类不实现或替代 Swagger/OpenAPI；
- 本类只注册 WorthIt 的两个 `GroupedOpenApi` Bean。

自动配置登记文件、测试类型、设计文档和实施计划中的类名已同步。

## 6. 已执行验证

模块级验证：

```bash
mvn -pl worthit-common/worthit-common-webmvc-starter -am test
```

结果：

- Reactor 4 个模块通过；
- `worthit-common-core`：12 个测试通过；
- `worthit-common-web`：8 个测试通过；
- `worthit-common-webmvc-starter`：9 个测试通过；
- 合计 29 个测试，0 失败、0 错误、0 跳过。

Starter 集成测试已经验证：

- public/internal 不串组；
- 无关路径不进入任一组；
- 默认 `/v3/api-docs` 不可访问；
- 显式开启时 Swagger UI 可访问；
- `ApiResponse`、`FieldViolation` Schema 可以生成。

全量验证：

```bash
mvn validate
mvn clean test
mvn package
git diff --check
```

结果：

- 17 个 Reactor 模块的 `validate`、`clean test`、`package` 全部通过；
- 全仓 52 个测试通过，0 失败、0 错误、0 跳过；
- 生产代码、测试、自动配置登记、设计与计划中无旧类名残留；
- `git diff --check` 通过。

## 7. Git 与文档边界

`worthit-server` 是独立 Git 仓库。本交接修改当前表现为旧类删除、新类新增；
Git 暂存后会识别为重命名。

上级 `/Users/shaopc/playground/worthit/docs/` 属于父仓库，目前整体仍为
untracked；不要在提交后端仓库时误认为上级权威文档已经同时进入 Git。
父仓库还存在与本任务无关的
`docs/superpowers/specs/2026-07-25-worthit-generic-todo-design.md`，不得修改或纳入
本任务提交。

## 8. 后续建议

下一阶段建议单独设计 `common-http`，处理：

- Spring `RestClient` / HTTP Interface 代理创建；
- LoadBalancer 与服务发现；
- 超时；
- Same-Token；
- 可信 TraceId；
- `X-Caller-Service`；
- 统一错误解码。

不要把调用端 HTTP 能力继续塞入 WebMVC Starter，避免服务端 Web 运行时与
跨服务调用能力耦合。
