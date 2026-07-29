# 后端架构规则

## Maven 工程职责

根 POM 同时承担以下职责：

- Parent：统一坐标、Java 版本、编码和构建属性；
- Aggregator：聚合全部 Phase 0 模块；
- `dependencyManagement`：统一第三方 BOM、内部模块和依赖版本；
- `pluginManagement`：统一编译、测试、打包、覆盖率和门禁插件版本。

子模块只声明自己真实使用的依赖，不通过复制根配置建立第二套版本治理。

## 当前模块边界

### Common

Phase 0 只包含：

- `worthit-common-core`
- `worthit-common-web`
- `worthit-common-webmvc-autoconfigure`
- `worthit-common-webmvc-starter`
- `worthit-common-security`
- `worthit-common-data`
- `worthit-common-http`
- `worthit-common-test`

`worthit-common-redis` 和 `worthit-common-observability` 只有在至少两个真实使用者出现后，才创建其技术中立部分。禁止为了目录完整提前创建空模块。

Common 不依赖业务 App、业务 Client、业务 DTO 或具体服务实现：

- `common-core` 不放用户、物品、订阅、想买或提醒状态。
- `common-web` 不放具体 Controller、Gateway Filter 或页面模型。
- `common-webmvc-autoconfigure` 承载三个 Servlet/MVC App 共用的类型安全配置、
  条件化自动配置、Servlet Filter、统一异常映射、OpenAPI 分组和可覆盖的默认
  安全策略；生产类只能位于 `com.shaopc.worthit.common.webmvc..`，不放业务
  Controller、领域错误码或服务专属放行策略。
- `common-webmvc-starter` 只聚合 autoconfigure、Spring MVC、Bean Validation、
  springdoc 与 Sa-Token 运行时依赖，不包含 Java 实现或自动配置 imports。
- `common-security` 不放用户表、微信协议或某个运行模型的过滤器。
- `common-data` 通过 Spring Boot 自动装配提供多个业务 App 共用的
  MyBatis-Plus 技术基线；业务 App 引入依赖后不得再手动 `@Import` 通用数据
  配置，也不得复制同一插件链。默认 Bean 必须允许 App 按类型覆盖。
- `common-data` 不放跨服务共享 DO、Mapper 或 Repository。
- `common-http` 不放 Reminder/Tracking 业务契约。
- `common-test` 不进入生产运行依赖。

### App 与 Client

- M1 只有 `worthit-reminder-client`，因为 Tracking 已有真实内部调用需求。
- `worthit-tracking-app` 依赖 `worthit-reminder-client`。
- `worthit-reminder-app` 依赖并实现 `worthit-reminder-client` 的服务端契约。
- Auth 和 Tracking 当前不创建 Client。
- 业务 App 不直接依赖另一服务的 App。
- Client 不依赖 App。

Client 只包含：

- 内部 HTTP 接口；
- Command、Query、Request、Response；
- 稳定的契约枚举和错误码；
- Bean Validation、Jackson 契约注解和必要 Javadoc。

Client 禁止包含：

- 聚合根、领域值对象和业务服务；
- DO、Mapper、Repository 或数据库字段对象；
- Spring Boot 自动配置和运行时代理配置；
- 供应商 SDK、全局工具或无关服务 DTO。

## Web 运行模型

- Gateway 使用 Spring Cloud Gateway 和 WebFlux。
- Auth、Tracking、Reminder 使用 Spring MVC/Servlet，并可使用 JDBC、MyBatis 和本地事务。
- Auth、Tracking、Reminder 通过 `common-webmvc-starter` 复用 Spring MVC、
  Bean Validation、统一异常映射、安全过滤链和 OpenAPI 装配。
- Common 统一参数、安全、资源、下游与未知系统异常的协议映射；各 App 只拥有
  领域错误码、服务专属匿名路径策略和必要的 HTTP 状态解析覆盖，不复制相同的
  Servlet Filter、Controller Advice 或 Sa-Token 运行时装配。
- Gateway、Client、`common-core` 和中立的 `common-web` 禁止依赖
  `common-webmvc-starter` 或 `common-webmvc-autoconfigure`。
- Gateway 的 WebFlux Filter、异常适配和安全配置留在 Gateway。
- 不把两个运行模型的实现细节塞进 Common，也不在业务服务中引入 WebFlux 作为默认编程模型。

## 服务内分层

业务 App 使用以下逻辑层次；可以按业务子域继续分包，但依赖方向不变。

### `interfaces`

- 负责 HTTP、定时任务或消息入口的协议适配；
- 执行请求反序列化、Bean Validation、身份上下文提取和响应转换；
- 不编写业务规则，不直接调用 Mapper，不开启跨用例事务。

### `application`

- 负责编排用例、权限、幂等、事务和跨聚合协调；
- 将接口 DTO 转换为 Command/Query，再调用 Domain；
- 定义对 Repository、Client、Clock 等外部能力的端口；
- 不承载 SQL、HTTP 客户端细节或复杂领域公式。

### `domain`

- 承载聚合、值对象、状态机、业务不变量和领域服务；
- 不依赖 Web、MyBatis、Spring Controller、外部 Client 或持久化 DO；
- 不接受来自外部接口的通用 Map/JSON 对象。

### `infrastructure`

- 实现 Repository、DO/Mapper、Outbox Relay 和外部 Client 适配；
- 将持久化或远程模型转换为上层需要的类型；
- 不把 DO、Mapper、HTTP 响应对象向 Domain 或公网接口泄漏。

依赖方向为 `interfaces → application → domain`；`infrastructure` 实现上层定义的端口。禁止为了省转换代码反转依赖方向。

## 数据与事务边界

- Auth、Tracking、Reminder 各自拥有数据，只能访问自己的逻辑库。
- 禁止跨服务直连数据库、跨库 Join 或共享 Mapper/Entity。
- 禁止用一个本地 `@Transactional` 方法包裹远程调用并假装形成跨服务事务。
- 同一服务内的业务更新和对应 Outbox 写入必须在一个本地事务中完成。
- Tracking 到 Reminder 使用 Outbox、Reminder Client、幂等和版本收敛实现最终一致。
- Reminder 的 Binding 锁、command log、实例变更和版本更新必须遵守架构终稿定义的同一事务顺序。

## API 与内部契约

### 公网 API

- 公网路径使用 `/api/v1/**`。
- 公网写接口幂等头使用 `Idempotency-Key`。
- 公网 Request/Response 面向页面交互，不与内部 Client DTO 或数据库 DO 全量复用。
- 已登录用户访问不存在、已删除或不属于自己的资源，按接口终稿返回 404，避免资源枚举。

### 内部 API

- 内部路径使用 `/internal/v1/**`，不得配置公网路由。
- 内部调用使用 Same-Token 和网络隔离；调用链透传可信 TraceId。
- Outbox 到 reconcile 的幂等头使用 `X-Idempotency-Key`，值为原 `eventId`。
- `X-Caller-Service` 只用于日志和审计，不作为单独的强身份凭证。

### OpenAPI

- Auth、Tracking、Reminder 的 OpenAPI 固定分为 `public` 和 `internal` 两组。
- `public` 只匹配 `/api/**`，`internal` 只匹配 `/internal/**`，两组禁止串入。
- 默认全量 `/v3/api-docs` 关闭，避免绕过分组暴露混合接口。
- Gateway 不配置 `/v3/api-docs/**` 或 `/swagger-ui/**` 的公网路由。
- 默认和生产环境关闭 API Docs 与 Swagger UI；`local`、`dev`、`test`
  环境才显式开启。

### TraceId

- 外部客户端不得控制最终 TraceId。
- Gateway 删除外部伪造的 `X-Trace-Id`，生成或清洗可信值后下传。
- 服务和 Client 只透传可信调用链中的 TraceId；响应按接口契约带回 `traceId`。

## Reminder reconcile 冻结边界

- `operationType` 由 Tracking 应用层根据路由和用例语义生成。
- 小程序 Request 不得提供 `operationType`、`cause` 或 `ResolutionCause`。
- Client 契约必须包含 `sourceVersion` 和 `schemaVersion`；M1 的 `schemaVersion` 固定为 `1`。
- Outbox payload 包含完整期望状态和 `schemaVersion`。
- Client 不包含 `cause`、`ResolutionCause`、`correction` 布尔或 `displayName`。
- 相同 `sourceVersion` 必须查 command log 并比较规范化 payload 摘要：一致为幂等，不一致为契约冲突。
- 乱序命令按 `sourceVersion` 收敛，不得让旧版本覆盖新状态。
- 同一 Binding 任意时刻最多一条 `PENDING`；历史终态允许保留多条。
- Reminder 实例生命周期仅为 `PENDING`、`PROCESSED`、`IGNORED`、`CANCELED`，不增加逻辑删除状态。

具体字段、状态判定和事务步骤必须以架构、接口和数据库终稿为准，本规则不替代终稿。

## 架构评估与重构

- 现行架构是实施工作的默认基线，但可以被质疑、推翻或重构。
- 架构评估只覆盖直接影响当前业务任务正确性、可维护性或交付的问题，不借业务实现发起全仓审查；无关问题只记录，不实施。
- 发现架构不适配真实需求、复杂度不合理或存在可验证风险时，不得为了“遵守文档”继续扩大问题，也不得未经确认直接偏离文档。
- 一般问题先说明现状证据、根因、对当前任务的影响、候选方案和推荐的整体最优解；触发下方架构变更门禁时，变更提案还必须说明兼容、迁移、验证和回滚。
- 在当前业务边界内综合正确性、完整链路、架构一致性、性能、安全、可维护性和长期总成本选择方案，不把改动最少作为默认目标；同时不得借局部问题引入与收益无关的新中间件、质量工具、CI、制品治理、全仓格式化或其他基础设施。
- 获得用户确认后，允许重构现有模块、依赖方向或技术方案，并同步更新工程、规则、架构文档、接口文档和测试门禁。
- 获得确认前，仍以现行权威文档为实施基线，不得先写成另一套架构后再补说明，也不得静默偏离。

## 架构变更门禁

出现以下变化时，先提出变更提案并获得确认，再同步修改对应权威文档和工程：

- 新增、删除或重命名服务、App、Client、Common 模块；
- 改变 App/Client/Common 依赖方向；
- 新增跨服务调用、数据库访问或一致性机制；
- 新增字段、状态、错误码、请求头或 API；
- 改变幂等、TraceId、Same-Token、Outbox 或版本语义；
- 将 M2/M3 扩展提前到当前阶段。
