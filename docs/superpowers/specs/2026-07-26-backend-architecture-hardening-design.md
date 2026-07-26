# WorthIt 后端架构与 Common 生产化加固设计

日期：2026-07-26

## 1. 文档状态

本文记录 2026-07-26 完成的后端架构复审和用户已确认的目标设计。本文先作为
后续权威架构文档、工程规则和实现计划的变更输入；在对应代码、测试、规则和
上级权威文档完成同步前，不能只凭本文宣称迁移已经完成。

本次评审主动质疑现有规则和架构，不以“现有文档已经冻结”为正确性证据。
最终结论同时参考：

- WorthIt 现行 PRD、接口、数据库、技术门禁与生产代码；
- Spring Boot 官方 Starter 与自动配置设计规范；
- Alibaba P3C 的编码与数据库安全规则；
- Alibaba COLA 对业务复杂度、技术复杂度和外部依赖的边界思想；
- Spring Cloud Alibaba 等高关注度 Java 开源项目的 BOM、Starter、Tests、
  Coverage、Wrapper 与 CI 工程治理方式。

参考项目只用于提炼可验证的工程机制，不照搬模块数量、组织结构或全部规则。

## 2. 目标

本次工作的第一目标是把 WorthIt 后端打造成稳定、可测试、可部署、可演进的
生产级项目基础。

未来项目可以复制这套结构和 Common 作为起点，但该可迁移性是架构质量的结果，
不是本期要单独建设的脚手架产品、通用框架仓库或外部 BOM 平台。

目标包括：

- Common 继续承载所有微服务共同使用的鉴权、错误、接口分组、数据访问、
  内部调用和测试能力；
- 公共契约、默认策略、运行时实现、自动装配和依赖入口职责清晰；
- Auth、Tracking、Reminder 引入一个 WebMVC Starter 即可获得 WorthIt 标准
  Web 运行环境；
- Gateway WebFlux 与下游 Servlet/MVC 保持严格隔离；
- 统一异常、TraceId、安全校验和远程错误形成完整调用闭环；
- 架构规则扫描真实生产代码，不允许只验证 fixture 或局部包；
- 本地和 CI 使用同一个可复现的 `./mvnw verify` 门禁；
- 当前开发基线和正式生产支持线明确分离。

## 3. 非目标

本设计不做以下事情：

- 不把 Common 拆成独立仓库或第三方通用框架；
- 不创建脚手架生成平台、独立 Maven Archetype 或模板市场；
- 不为了“Common 纯净”把每项能力拆成单独 Starter；
- 不在没有第二个真实消费者时抽取通用 Outbox、MQ、Integration 或供应商模块；
- 不为了形式给所有服务生成空的 DDD 四层；
- 不在本轮改变公网 API、内部 Reminder Client、数据库字段或业务状态；
- 不把 Common 架构重构和 Spring Boot 大版本升级混成一个不可回滚批次。

## 4. 现状证据与审计结论

### 4.1 已成立的基础

审计时仓库为干净的 `main`，基线提交为
`2b95d7e refactor(common-data): 改为 MyBatis-Plus 自动装配`。

已验证：

- 根 Reactor 包含 17 个 Maven 模块；
- `mvn test` 全量通过；
- Surefire 共执行 161 个测试，0 failures、0 errors、0 skipped；
- MySQL 与 Redis 集成测试使用 Testcontainers；
- Maven 依赖树没有实际 `omitted for conflict`；
- Enforcer 已限制 Java/Maven 版本、动态版本和业务 App 之间直接依赖；
- Gateway 保持 WebFlux，Auth、Tracking、Reminder 保持 Servlet/MVC；
- Common 已形成 core、web、webmvc-starter、security、data、http、test 七个
  初步能力模块。

因此本项目不是需要推倒重建的失败工程，而是尚未完成生产闭环的 Phase 0
架构原型。

### 4.2 P1 问题

1. `BusinessException`、`ErrorCode`、`ApiResponse` 已存在，但没有统一
   `@RestControllerAdvice`，各服务开始业务开发后会自行形成异常映射。
2. MVC 安全自动配置使用一个组合
   `@ConditionalOnMissingBean({SameTokenProvider, SameTokenVerifier})`
   控制同时实现两个接口的 Bean。应用只覆盖 Provider 时，默认 Bean 整体
   回退，Verifier 可能缺失并导致安全 Filter 无法创建。现有测试没有覆盖
   这种局部替换。
3. Gateway 已在可信头过滤器生成 TraceId，安全错误写出器却重新生成 TraceId，
   同一拒绝请求可能出现两个追踪标识。
4. Tracking 架构测试只扫描 `com.shaopc.worthit.tracking.app`，实际
   `com.shaopc.worthit.tracking.infrastructure` 不在门禁内。
5. `CLIENT_MUST_STAY_CONTRACT_ONLY` 已定义，但真实 Reminder Client 测试没有
   执行该规则。
6. 根 POM 只显式管理部分插件。由于项目导入 Spring Boot BOM 而未继承 Boot
   Parent，依赖管理存在但完整插件管理不会自动获得；实际构建仍调用
   `maven-resources-plugin:2.6`。
7. 仓库没有 Maven Wrapper 和 CI，无法证明不同开发机、历史提交和 CI 使用
   相同构建工具。
8. Flyway 11.7.2 在 MySQL 8.4 集成测试中提示其已测试上限为 MySQL 8.1。
   SQL 测试虽通过，但正式发布不能保留该兼容性灰区。

### 4.3 P2 问题

- `common-webmvc-starter` 同时承载实现代码和依赖聚合，职责混合；
- Starter 内直接聚合 Web、Validation、Springdoc、Sa-Token、JWT 和 Redis，
  却没有把各项自动配置拆成独立条件和独立测试；
- API 分组、安全路径和运行策略存在硬编码，缺少 `worthit.*` 配置模型；
- 配置没有生成 IDE 元数据；
- `common-data` 默认 MySQL、审计字段名和 `Long userId`，但扩展接口不完整；
- `WorthItMetaObjectHandler` 尚未形成清晰、可覆盖的自动装配闭环；
- `common-http` 每次创建代理时创建新的 JDK `HttpClient`，没有复用 Spring
  管理的连接资源和观测能力；
- 多个 App 直接使用传递依赖提供的类型，POM 没有声明真实编译依赖；
- JaCoCo 只有版本管理，没有报告、基线和阻塞规则；
- 没有格式、PMD、SpotBugs、SBOM 和漏洞发布门禁；
- TraceId 已进入请求和响应，但没有完整 MDC、结构化日志和统一指标闭环；
- 三个 MVC App 重复维护 Actuator 与 Springdoc 环境配置。

## 5. 候选方案与选择

### 5.1 方案 A：只修复当前缺陷

保留所有模块和装配方式，只增加异常处理、修复 TraceId 和 ArchUnit。

优点是改动最小、交付最快。缺点是 Starter 职责混合、自动配置可替换性、
构建复现和依赖治理仍然存在，无法形成可长期复制的项目底座。

不采用为最终方案，但其中的正确性修复作为迁移第一批执行。

### 5.2 方案 B：能力化 Common + 渐进式生产加固

Common 保持 WorthIt 项目平台层定位；保留一个开箱即用的 WebMVC Starter，
仅将实现下沉到 autoconfigure 模块。同步补齐异常、TraceId、数据、HTTP、
构建、测试、CI 和受支持版本迁移。

该方案既不牺牲业务服务接入体验，也能明确契约、实现、装配和依赖职责。

采用该方案。

### 5.3 方案 C：独立平台、BOM 与多 Starter 产品化

把 Common 拆为独立仓库、独立 BOM、多个可选 Starter，并提供项目生成工具。

该方案适合多个团队和多个独立项目共同维护平台时使用。WorthIt 当前没有第二个
仓库消费者和独立发布需求，提前实施会增加版本、发布、兼容和排障成本。

当前不采用。只有 Common 需要跨仓发布和独立版本治理时重新立项。

## 6. 总体架构决策

### 6.1 Common 定位

Common 是 WorthIt 的项目级平台层，统一所有服务必须遵守的技术协议和默认能力。
它可以包含 WorthIt 统一默认策略，不要求达到第三方库意义上的完全业务无关。

允许放入 Common 的典型内容：

- `/api/**` 与 `/internal/**` 的项目级分区；
- 默认公网接口需要登录；
- 内部接口必须验证 Same-Token；
- 统一响应、异常映射和公共错误码；
- 可信请求头清洗和 TraceId 传播；
- MyBatis-Plus、HTTP Client 和测试的项目级默认配置。

服务特有例外仍归服务所有，例如 Auth 的微信登录路径免登录。

### 6.2 依赖方向

```text
业务服务
  -> Starter（依赖入口）
  -> Autoconfigure（运行装配）
  -> Common API/契约
```

依赖只能向下：

- Common 不依赖 Gateway、Auth、Tracking 或 Reminder；
- Client 不依赖 App；
- App 不直接依赖另一个 App；
- Gateway、Client、`common-core`、`common-web` 不依赖 WebMVC Starter；
- `common-test` 不进入生产运行依赖。

### 6.3 服务边界

保留四个运行服务：

| 服务 | 保留理由 | 领域分层策略 |
| --- | --- | --- |
| Gateway | 公网安全与路由边界；WebFlux 运行栈独立 | 不使用 DDD |
| Auth | 用户、外部身份、登录审计、注销和 Redis 会话独立 | 按状态复杂度渐进分层 |
| Tracking | Item、Subscription、Wish、Category、Dashboard、Lifecycle 核心事实源 | 对复杂状态和规则使用 Domain |
| Reminder | Binding、Instance、Command Log、用户 ignore 与 reconcile 并发、独立故障边界 | 状态机与并发规则进入 Domain |

Reminder 不只是定时工具。它拥有独立数据、状态机、接口和故障边界；Reminder
故障不能回滚 Tracking 对核心业务对象的本地事务，因此继续使用 Outbox 最终
一致性是成立的。

不新增运行服务。未来服务长期只有转发或简单 CRUD，且没有独立数据、扩缩容、
生命周期或故障隔离理由时，必须重新评估是否应合并。

### 6.4 服务内代码组织

按业务能力优先组织代码，在业务能力内部按复杂度选择分层：

```text
tracking
└── item
    ├── interfaces
    ├── application
    ├── domain
    └── infrastructure
```

- `interfaces` 只负责协议适配、校验和响应转换；
- `application` 负责编排用例、事务、权限、幂等和跨聚合协调；
- `domain` 承载状态机、不变量、值对象和领域规则；
- `infrastructure` 实现数据库、缓存、Outbox 和远程 Client 适配。

简单查询、配置读取或无领域不变量的功能不强制创建空的四层目录。

## 7. Common 目标模块

### 7.1 `worthit-common-core`

职责：

- `ErrorCode`；
- `BusinessException`；
- 分页和基础类型；
- TraceId 基础契约；
- 公共错误码段与编码规范。

约束：

- 不依赖 Spring、Web、MyBatis 或业务模块；
- 不包含服务专属业务错误码。

### 7.2 `worthit-common-web`

职责：

- `ApiResponse`；
- `FieldViolation`；
- 公共 OpenAPI 注解与响应结构。

约束：

- 保持 Servlet/WebFlux 中立；
- 不包含 Controller Advice、Filter 或运行时自动配置。

### 7.3 `worthit-common-security`

职责：

- `UserContext`；
- 可信请求头常量；
- Same-Token Provider/Verifier 契约；
- Sa-Token 技术中立适配；
- 公共认证、授权、非法来源错误码。

约束：

- 不选择 MVC 或 WebFlux；
- 不包含服务专属匿名路径；
- 不包含用户表和微信业务协议。

### 7.4 `worthit-common-webmvc-autoconfigure`

新增模块，承载 Servlet/MVC 专属实现：

- `WorthItErrorHandlingAutoConfiguration`；
- `WorthItTraceAutoConfiguration`；
- `WorthItOpenApiAutoConfiguration`；
- `WorthItMvcSecurityAutoConfiguration`；
- `WorthItSecurityRedisAutoConfiguration`；
- 统一 Controller Advice、Trace/MDC Filter、安全 Filter；
- `worthit.web.*`、`worthit.security.*` 等类型安全配置；
- 服务覆盖点和自动配置元数据。

每项自动配置必须拥有：

- 独立的 classpath 条件；
- 独立的属性开关；
- 独立的默认 Bean；
- 独立的用户覆盖测试；
- 缺失类路径测试；
- 与其他自动配置的顺序说明。

Same-Token Provider 和 Verifier 分别回退，不能再由一个组合条件控制两个契约。

### 7.5 `worthit-common-webmvc-starter`

继续作为 Auth、Tracking、Reminder 的唯一标准 WebMVC 依赖入口。

它可以聚合：

- Spring MVC；
- Bean Validation；
- Springdoc；
- Sa-Token MVC；
- JWT；
- Redis 登录态；
- `common-webmvc-autoconfigure`。

该组合对当前三个 MVC 服务都是必备运行基线，因此不为追求形式纯净继续拆碎
Starter。Starter 本身不再存放实现代码。

只有未来出现第二类不需要安全或 Redis 的真实 MVC 服务时，才评估新增其他
Starter。

### 7.6 `worthit-common-data`

职责：

- MyBatis-Plus 自动配置；
- MySQL 分页默认值；
- 乐观锁；
- 审计字段填充；
- 公共类型处理和数据库技术异常转换；
- 可覆盖的数据库方言和审计参与者。

新增 `AuditActorProvider` 等技术端口，使 data 不直接依赖 Sa-Token 或具体
Web 用户上下文。WebMVC 自动配置可以提供默认当前用户适配。

禁止：

- 统一 `BaseEntity`；
- 强迫所有表拥有 `del_flag`、`version` 或统一审计字段；
- 共享业务 DO、Mapper、Repository；
- 把 Outbox 或业务幂等表作为 Common 基类。

Tracking 可以选择逻辑删除，Reminder Instance 继续只通过状态机表达生命周期。

### 7.7 `worthit-common-http`

职责：

- 内部请求头注入；
- Same-Token、TraceId、调用方名称和内部幂等键传播；
- 远程 `ApiResponse` 错误解析；
- 受管理的 `RestClient.Builder` 与 HTTP Interface 代理创建；
- 类型安全的连接、读取超时；
- HTTP 指标和安全日志接口。

连接资源由 Spring 管理并复用，不为每个代理创建新的 JDK `HttpClient`。

具体 Client 契约归服务提供方，具体代理 Bean 归调用方 Infrastructure。
Common 不包含 Reminder/Tracking DTO。

### 7.8 `worthit-common-test`

职责：

- ArchUnit 公共规则；
- 自动配置测试辅助；
- 契约、数据库和测试容器的少量公共能力。

所有公共架构规则必须由消费者测试扫描真实生产根包。规则自身的 fixture 测试只
能证明规则语法，不能代替生产模块执行。

## 8. 错误与响应设计

### 8.1 错误码所有权

- Common 拥有系统、安全、参数、远程调用等跨服务公共错误码；
- Common 统一编码格式、号段分配和响应契约；
- 服务拥有自己的领域错误码；
- 服务错误枚举实现 Common `ErrorCode`；
- 服务不得自行改变统一响应结构。

该边界使错误码体系保持统一，又避免每次新增 Tracking 业务状态都要求所有服务
升级 Common。

### 8.2 统一异常映射

`common-webmvc-autoconfigure` 提供统一 `@RestControllerAdvice`，至少处理：

- Bean Validation 参数错误；
- JSON/类型转换错误；
- `BusinessException`；
- Sa-Token 未登录和无权限异常；
- 远程服务异常；
- 资源不存在和状态冲突；
- 未知系统异常。

要求：

- HTTP 状态表达传输语义，业务 code 表达稳定业务语义；
- 参数错误返回结构化字段列表；
- 未知异常记录完整服务端日志，只返回安全稳定消息；
- 生产响应不得包含堆栈、SQL、类名或内部异常消息；
- Gateway 和 MVC 使用同一个 `ApiResponse` JSON 契约。

## 9. 安全与 TraceId 数据流

### 9.1 外部请求

```text
Client
  -> Gateway 删除外部伪造的可信头
  -> Gateway 生成唯一 TraceId
  -> Gateway 校验用户令牌并注入 Same-Token
  -> MVC Filter 验证 Same-Token
  -> MVC Filter 按服务策略校验登录
  -> 建立可信用户上下文与 MDC
  -> Controller/Application/Domain
  -> 统一响应或统一异常
  -> 清理 MDC 和线程上下文
```

Gateway 对一次请求只生成一个 TraceId。安全失败、参数失败、业务异常、内部调用
和正常响应必须继续使用同一个值。

### 9.2 内部调用

```text
Tracking Application / Outbox Relay
  -> ReminderCommandClient
  -> common-http 注入 Same-Token、Caller、TraceId、X-Idempotency-Key
  -> LoadBalancer
  -> Reminder /internal/v1/reminders/reconcile
  -> Same-Token 校验
  -> 幂等与 Binding 锁
  -> 统一响应或远程错误
```

`X-Caller-Service` 只用于日志和审计，不单独构成强身份。

## 10. 数据、事务与一致性

### 10.1 数据所有权

- Auth、Tracking、Reminder 只访问自己的逻辑库；
- 禁止跨服务直连数据库、跨库 Join 和共享 Mapper/Entity；
- 一个本地事务只修改本服务拥有的数据；
- Domain 和接口层不能暴露持久化 DO。

### 10.2 Tracking 到 Reminder

Outbox 不抽取到 Common：

- Outbox 表、Payload、Relay 和重试状态属于 Tracking；
- reconcile、Binding 锁、command log 和 Reminder 状态机属于 Reminder；
- Common 只提供 HTTP、安全、TraceId 和错误传输机制。

只有第二个真实 Outbox 使用场景出现后，才评估公共端口或组件。

### 10.3 事务原则

- Tracking 业务更新和 Outbox 写入同一事务；
- Reminder 故障不能回滚 Tracking 本地事务；
- 重试必须携带稳定幂等键；
- 乱序由 `sourceVersion` 收敛；
- 无法自动恢复的投递进入 DEAD；
- 人工重放必须可审计、可幂等、可限制目标；
- 禁止使用一个 `@Transactional` 包裹远程调用并假装形成跨服务事务。

## 11. 配置与可观测性

### 11.1 配置分层

- Common 默认：日志、HTTP 超时、Actuator、OpenAPI、安全公共参数；
- 服务配置：端口、数据源、Client 地址、任务和服务专属策略；
- Secret：JWT Secret、数据库/Redis 密码、微信凭据，只通过环境变量或 Secret
  注入。

所有 WorthIt 自有配置使用 `worthit.*` 命名空间、`@ConfigurationProperties`、
启动校验和配置元数据。关键配置缺失时启动失败。

### 11.2 日志与指标

生产日志至少包含：

- 时间、级别；
- 服务名、实例；
- TraceId；
- 稳定错误码。

禁止记录：

- 用户 Token、Same-Token；
- 密码、JWT Secret、微信凭据；
- 未脱敏个人数据；
- 无界完整请求体和响应体。

Micrometer 指标至少覆盖：

- JVM、线程、连接池；
- HTTP 延迟、吞吐和错误；
- 鉴权失败；
- Same-Token 轮换；
- Outbox 重试、租约回收、DEAD；
- Reminder reconcile 幂等、乱序和冲突。

指标标签不得使用 userId、原始 URL 参数、TraceId 等高基数字段。

### 11.3 健康检查

- 区分 liveness 和 readiness；
- 数据库、Redis、Nacos 可影响 readiness；
- 避免外部依赖短暂失败直接触发 liveness 重启风暴；
- Actuator、Prometheus 和管理端点不经 Gateway 暴露公网。

## 12. 构建与质量治理

### 12.1 Maven

根 POM 继续同时承担 Parent、Aggregator、dependencyManagement 和
pluginManagement。单仓阶段不增加独立 BOM。

必须：

- 提交 Maven Wrapper 并固定 Maven 版本；
- 锁定实际参与生命周期的 Maven 插件；
- 配置 `project.build.outputTimestamp`；
- 保留并加强 Enforcer；
- 子模块声明真实使用的直接依赖；
- 使用 `./mvnw verify` 作为正式本地和 CI 门禁。

依赖收敛和 upper-bound 规则启用前先清理当前 BOM 与传递依赖基线；只允许有
证据、带注释的最小排除，不允许用大范围 exclude 让门禁失真。

### 12.2 代码质量工具

采用：

- Spotless：确定性格式和 import 顺序；
- PMD 7：兼容当前 Java 的 P3C 核心规则与 WorthIt 补充规则；
- SpotBugs + FindSecBugs：缺陷、并发和安全问题；
- JaCoCo：覆盖率报告与基线递增；
- SBOM 与依赖漏洞扫描：发布门禁。

不同时引入 Checkstyle 和 Spotless 承担重复格式职责。

P3C 规则按适用性迁移，不直接依赖无法支持当前 Java/Spring 的陈旧规则包。
覆盖率先记录真实模块基线，再要求不下降并按风险逐步提升，不使用一个为了好看
而产生低价值测试的全仓统一百分比。

### 12.3 CI

CI 至少包含：

1. Wrapper 构建环境、格式和静态检查；
2. 单元、应用和架构测试；
3. Testcontainers MySQL/Redis 集成测试；
4. OpenAPI 分组和 Client 契约测试；
5. Flyway 空库迁移和必要升级路径；
6. 覆盖率、依赖分析和漏洞扫描；
7. 四个可运行 Jar 打包与基础启动探测。

自动化门禁不依赖开发机 `/Users/shaopc/Documents/Script/dev-stack`。

## 13. 测试策略

### 13.1 单元与领域测试

覆盖：

- ErrorCode 和统一异常映射；
- 金额、日期和值对象；
- Tracking 状态机和成本规则；
- Reminder 状态机、终态和冲突决策；
- 可控 `Clock`、边界值和非法状态。

### 13.2 自动配置测试

每项自动配置使用 `ApplicationContextRunner`、
`WebApplicationContextRunner` 或必要的 Reactive Runner 覆盖：

- 默认 Bean；
- 属性关闭；
- 用户完整覆盖；
- 用户局部覆盖；
- 缺失可选类路径；
- Servlet 与非 Servlet 上下文；
- 自动配置顺序和无意副作用。

Same-Token 必须增加只覆盖 Provider、只覆盖 Verifier 和分别覆盖两者的测试。

### 13.3 API 与安全测试

覆盖：

- 参数错误、业务异常、未知异常；
- HTTP 状态、业务 code、details、traceId；
- 公网/内部 OpenAPI 分组；
- 外部伪造可信头；
- Same-Token 成功/失败；
- 登录白名单精确匹配；
- Gateway 错误与下游请求使用同一 TraceId。

### 13.4 数据与集成测试

- MySQL 使用真实 MySQL Testcontainers；
- Redis 使用真实 Redis Testcontainers；
- 不使用 H2 证明 MySQL DDL、锁和唯一约束；
- 验证 Flyway 空库和升级路径；
- 验证 Outbox 重复、退避、租约、DEAD 和重放；
- 验证 Reminder 首次并发创建、ignore/reconcile 竞争、摘要冲突和乱序。

### 13.5 架构测试

必须扫描完整生产根包并验证：

- Common 不依赖业务；
- App 不依赖其他 App；
- Client 不依赖 App 或运行时 Starter；
- Domain 不依赖 Web、MyBatis、Infrastructure；
- Mapper/DO 不泄漏到 Domain、Client 或公网模型；
- Gateway 不含 Servlet/MVC；
- `common-web` 不选择 MVC/WebFlux；
- `common-test` 不进入生产依赖。

Fixture 测试保留为规则单元测试，但不能作为生产门禁完成证据。

## 14. 版本支持策略

### 14.1 当前阶段

Common 架构重构期间保持 Java 17、Spring Boot 3.5.16、Spring Cloud 2025.0.3
和 Spring Cloud Alibaba 2025.0.0.0，避免同时引入结构迁移和大版本兼容变化。

该组合只能称为开发与兼容基线，不能作为公开生产完成声明。

### 14.2 生产支持线

Common 加固完成后建立独立兼容迁移批次，验证届时仍受 OSS 支持的：

- Spring Boot 4.1；
- 对应 Spring Cloud / Spring Cloud Alibaba；
- MyBatis-Plus Boot 4 Starter；
- Sa-Token MVC/Reactor/Redis/JWT；
- Springdoc；
- Flyway、Testcontainers 和其他运行依赖。

官方制品存在不等于组合已经兼容。切换前必须通过：

- 四服务启动；
- Gateway WebFlux；
- MVC 鉴权和 Same-Token；
- OpenAPI；
- MySQL/Flyway；
- Redis 会话；
- 内部 HTTP Client；
- 全量 `verify` 和关键 E2E。

如果兼容矩阵存在阻塞，保留当前开发线并记录阻塞，不得虚假标记生产就绪。

## 15. 迁移批次

### 15.1 批次一：正确性修复

- 修复 Same-Token 局部覆盖；
- 修复 Gateway TraceId 重复生成；
- 修复 ArchUnit 扫描和真实规则启用；
- 解决 Flyway/MySQL 支持差距。

完成标准：

- 缺陷先有失败测试；
- 目标模块测试和全量测试通过；
- 不改变既有 API、错误码和数据库契约。

### 15.2 批次二：Common 装配重构

- 新增 `common-webmvc-autoconfigure`；
- 将现有自动配置和 Servlet 实现迁入；
- 在目标 autoconfigure 模块增加统一异常处理；
- 保留薄 `common-webmvc-starter`；
- 增加配置属性、元数据和条件测试；
- 清理直接/传递依赖。

完成标准：

- 三个 App 的依赖入口不变；
- 自动配置可单独测试、关闭和覆盖；
- Gateway、Client 和中立 Common 不获得 Servlet 依赖。

### 15.3 批次三：构建与 CI

- Maven Wrapper；
- 插件锁定和可复现构建；
- Spotless、PMD、SpotBugs、JaCoCo、依赖分析；
- CI 与 `./mvnw verify`；
- SBOM 和漏洞发布门禁。

完成标准：

- 本地和 CI 执行同一命令；
- 先清理存量，再启用阻塞；
- 不用大范围排除伪造通过。

### 15.4 批次四：Data、HTTP 与可观测性

- AuditActorProvider 和 data 覆盖点；
- 受管理的 HTTP 连接资源；
- HTTP 指标和统一错误日志；
- MDC、结构化日志、指标和健康检查；
- 配置去重和敏感信息门禁。

完成标准：

- 不改变业务表和 Client 契约；
- 真实 MySQL/Redis/HTTP 集成测试通过；
- 资源和线程在测试后释放。

### 15.5 批次五：受支持版本迁移

- 建立兼容矩阵；
- 独立迁移 Boot/Cloud/SCA 与第三方 Starter；
- 执行全量验证与关键 E2E；
- 更新开发和生产版本声明。

完成标准：

- 运行在受支持版本线；
- 没有以禁用关键测试或删除能力换取升级通过；
- 完成回滚演练和版本 ADR。

## 16. 文档与规则同步

每批实施必须同步检查：

- `AGENTS.md`；
- `rules/10-architecture.md`；
- `rules/20-java-code-style.md`；
- `rules/30-spring-maven.md`；
- `rules/40-testing-quality.md`；
- 上级架构、接口、数据库和技术门禁终稿；
- 已有 `docs/superpowers/specs/` 与 `plans/` 中仍会被引用的设计。

本设计确认后，需要明确标记或修订以下旧结论：

- “具体业务异常适配仍留在各 App”被统一 WebMVC 异常处理替代；
- “实现直接放入 common-webmvc-starter”被 autoconfigure + 薄 Starter 替代；
- 架构测试必须扫描完整生产包，不能只验证局部 `*.app`；
- Maven 基础门禁由 `mvn validate/test/package` 演进为 `./mvnw verify`；
- 当前 Boot/Cloud 线不得再描述为正式生产推荐。

涉及上级权威 DOCX 的语义变更时，必须按上级文档维护规则创建新版本并同步
Markdown，不能直接修改历史归档或只改 Markdown 镜像。

## 17. 回滚原则

- 每个迁移批次独立提交，不把正确性修复、模块重构和版本升级混为一个提交；
- 每批不涉及业务 DDL，若未来实施产生 DDL，必须新增 Flyway 版本；
- autoconfigure 拆分回滚时可恢复 Starter 内实现，不影响业务 API；
- 构建门禁先以报告模式建立基线，再转阻塞，避免无法定位的大规模一次失败；
- 版本升级保留最后一个通过全量门禁的开发基线，可按独立提交回滚；
- 禁止通过删除测试、放宽安全校验或忽略异常来完成回滚。

## 18. 最终验收

只有同时满足以下条件，才能宣称本设计实施完成：

- Common 契约、自动配置和 Starter 职责与本文一致；
- Same-Token、TraceId 和统一异常闭环有真实测试；
- 四服务边界、数据库所有权和最终一致性语义没有被破坏；
- ArchUnit 扫描真实完整生产代码；
- `./mvnw verify` 在干净环境和 CI 中通过；
- MySQL、Redis、HTTP、Flyway 和可运行 Jar 有集成证据；
- 代码、规则、架构文档和测试门禁保持一致；
- 工作区没有混入无关修改；
- 正式生产声明使用受支持的框架版本线。

完成某一批只能宣称该批已实现和验证，不能提前宣称整个后端已达到生产就绪。
