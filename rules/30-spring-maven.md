# Spring Boot 与 Maven 规范

## Spring Bean 与依赖注入

- 使用构造器注入，依赖字段声明为 `private final`。
- 单一构造器不添加多余的 `@Autowired`；存在多个构造器时必须明确 Spring 使用哪个构造器。
- 禁止字段注入和通过静态方法从容器取 Bean。
- Bean 按服务和业务职责放置，不依赖扩大 `@ComponentScan` 范围解决错误分包。
- 同一接口存在多个实现时，使用有业务含义的配置或限定符，不依赖 Bean 名称碰巧匹配。
- 多个 App 共用的运行时技术装配使用 Spring Boot `@AutoConfiguration`，
  并登记在
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`；
  同时使用运行模型、classpath 和类型安全属性条件限制生效范围，并用
  `@ConditionalOnMissingBean` 为应用级覆盖让路。每项能力必须能独立启停；
  业务 App 不手动 `@Import` 此类通用自动配置。

Spring Boot 3.5 官方文档推荐构造器注入，因为它允许依赖字段保持不可变。参考：

- [Spring Beans and Dependency Injection](https://docs.spring.io/spring-boot/3.5/reference/using/spring-beans-and-dependency-injection.html)

## 配置绑定

- 一组相关配置使用类型安全的 `@ConfigurationProperties`，不让 `@Value` 散落在业务代码中。
- 配置类型按前缀和所属服务命名，字段使用明确 Java 类型，不用字符串承载时长、URI、集合或布尔值。
- 配置优先使用构造器绑定和不可变字段。
- 必填配置使用 `@Validated` 与 Jakarta Validation 约束，在启动阶段失败。
- 默认值必须明确、可解释且不降低安全性；Secret 不提供可工作的弱默认值。
- 通过 `@ConfigurationPropertiesScan` 或 `@EnableConfigurationProperties` 显式注册配置类型。
- 配置类只表达配置，不注入业务 Service，不在 getter 中访问网络或数据库。

参考：

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)

## 配置与 Profile 边界

- 环境差异通过外部配置、环境变量或配置中心提供，不复制业务代码。
- `application.yml` 只放安全的公共默认值；环境专属地址、账号和密钥不得写入公共配置。
- Profile 只表达运行环境或明确能力组合，不用 Profile 隐藏业务分支。
- Gateway 的 WebFlux 配置与 Auth/Tracking/Reminder 的 Servlet 配置分别归属各自 App。
- Nacos、Redis、MySQL、微信和内部凭证配置按服务最小授权，不建立全服务共享万能账号。
- 本机绝对路径、个人目录和 IDE 配置不得进入可提交配置。

## Secret 管理

- Token、JWT Secret、Same-Token、数据库密码、微信 AppSecret 和第三方密钥只能从 Secret、环境变量或受控配置中心注入。
- Secret 不写入源码、测试固定值、日志、异常消息、示例命令或提交历史。
- 测试需要凭证时使用明显无效的占位测试值或隔离测试 Secret，不复用真实环境凭证。
- 发现疑似真实 Secret 时停止传播，报告位置，并由负责人决定轮换和清理方式。

## Web 与校验

- Controller 入参使用 Bean Validation；嵌套对象按需要添加 `@Valid`。
- 格式校验放在接口边界，依赖数据库或领域状态的校验放在 Application/Domain。
- 公网接口统一使用接口终稿定义的响应信封、HTTP 状态和业务错误码。
- 全局异常转换只处理协议映射，不吞掉系统异常，也不向客户端暴露堆栈。
- Gateway 负责清洗不可信身份头和 TraceId；下游不直接信任外部同名请求头。
- `/internal/**` 不配置公网路由，内部鉴权失败不得进入 Application 成功路径。
- Auth、Tracking、Reminder 通过 `worthit-common-webmvc-starter` 接入
  Spring MVC、Bean Validation、springdoc、统一异常映射和共同的 Servlet
  安全运行时；Starter 只聚合依赖，Java 实现和自动配置 imports 全部属于
  `worthit-common-webmvc-autoconfigure`。三个 App 不复制 Filter、Controller
  Advice、Sa-Token 或默认安全 Bean；服务专属放行策略、领域错误码和必要的
  HTTP 状态解析覆盖仍由 App 显式提供。
- 纯契约 Client 使用 `spring-web` 声明 HTTP Interface，不引入
  `spring-boot-starter-web`、springdoc、Servlet 或内嵌服务器。
- Gateway 禁止依赖 `worthit-common-webmvc-starter`、
  `worthit-common-webmvc-autoconfigure` 或
  `spring-boot-starter-web`，避免 WebFlux/Servlet 运行栈混用。
- OpenAPI 默认及生产环境关闭，`local`、`dev`、`test` 显式开启；
  `springdoc.enable-default-api-docs=false` 固定关闭全量默认文档。

## 事务

- `@Transactional` 放在 Application Service 的公开用例方法或明确的基础设施事务边界。
- 不在 Controller、DTO、Domain Entity 或私有自调用方法上依赖事务代理。
- 事务内只执行当前服务的数据库一致性操作。
- 远程 Client 调用不与本地数据库修改共同维持长事务；跨服务一致性按 Outbox 方案实现。
- 只读查询在有实际收益且语义正确时使用只读事务，不把它当作强制装饰。
- 捕获异常时不得导致事务意外提交；需要转换异常时保留 cause 和回滚语义。
- 并发、锁顺序和条件更新必须与数据库约束、技术门禁共同验证。

## Maven 版本治理

- 根 POM 统一管理 Java、BOM、第三方依赖、内部模块和构建插件版本。
- 子模块不重复声明已由根 POM 或 BOM 管理的版本。
- 属性名使用 `<artifact-or-stack>.version` 的稳定形式，不为同一组件创建多个版本属性。
- 依赖只声明在真实使用它的最小模块中；Aggregator POM 不承载运行时依赖。
- `pluginManagement` 负责统一插件版本和默认配置；需要实际执行的插件必须在正确的 build 层启用。
- 内部模块版本使用根 `revision`，不在子模块写另一套快照版本。
- 不使用 `LATEST`、`RELEASE`、版本区间或未解释的动态版本。
- 不通过排除大量传递依赖掩盖模块归属错误；先检查依赖树和实际所有者。

## 新增依赖准入

新增依赖前必须确认：

1. 当前任务存在真实使用场景；
2. 依赖所属模块符合架构边界；
3. 与 Java 17、当前 Boot/Cloud/SCA 基线兼容；
4. 维护状态、许可证和已知安全风险可接受；
5. 传递依赖不会同时引入 WebFlux/Servlet 冲突或重复实现；
6. 不会把 Starter、自动配置或供应商 SDK 带入纯契约 Client、Domain 或 `common-core`；
7. 已有 JDK、Spring 或现有依赖不能清晰解决同一问题。

新增依赖时记录选择依据，并用 `dependency:tree` 或 effective POM 检查实际解析结果。

## 模块准入

- 新增 Common 需要至少两个真实使用者，且提取内容技术中立。
- 新增 Client 需要真实的内部调用方，由服务提供方拥有。
- 新增 Starter 或自动配置需要多个真实 App 重复同一运行时装配；不能混入基础 Client。
- Starter 不承载实现、资源或自动配置登记；autoconfigure 的可选集成依赖不得
  被 Starter 强制传递给不使用该能力的 App。
- `worthit-common-data` 的 MyBatis-Plus 技术基线随依赖自动生效；Auth、
  Tracking、Reminder 不声明手动导入或重复插件 Bean。
- 新增聚合层、工具模块或抽象接口前，必须证明现有边界无法承载。
- 模块变化属于架构变更，先更新权威架构文档并获得确认。

## Maven 验证

POM 或依赖发生变化时，至少执行：

```bash
./mvnw validate
./mvnw test
./mvnw package
```

同时根据范围执行：

- 相关模块及其上游：`./mvnw -pl <module> -am test`；
- 依赖边界：`./mvnw dependency:tree` 或 ArchUnit；
- BOM/版本：检查 effective POM 和解析版本；
- 打包插件：检查最终产物，而不只检查配置存在。

命令成功不等于运行时集成已验证。涉及 Nacos、MySQL、Redis、Gateway、HTTP Client 或安全链路时，还必须执行对应 Phase 0 集成门禁。
