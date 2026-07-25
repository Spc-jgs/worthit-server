# WorthIt Phase 0 Common 全量能力与运行接入设计

日期：2026-07-25

## 1. 目标

在保留现有 OpenAPI/WebMVC Starter 未提交重命名现场的前提下，补齐
Phase 0 七个 Common 模块的当前真实职责，并同步完成 Gateway、Auth、
Tracking、Reminder 的必要运行接入。

本轮完成条件是：

- 七个 Common 模块均有与现行架构匹配的真实能力，不保留空实现模块；
- Common 不包含业务 DTO、业务错误码、DO、Mapper、Repository 或服务实现；
- Gateway 保持 WebFlux，三个业务 App 保持 Servlet/MVC；
- Tracking 能通过 `RestClient + LoadBalancer + HTTP Interface` 创建
  `ReminderCommandClient`；
- 可信 TraceId、Same-Token 和 `X-Caller-Service` 的生成、清洗、注入与校验
  边界有可执行测试；
- MyBatis-Plus 的 MySQL 分页、乐观锁、审计填充和逻辑删除约定有可执行测试；
- 现有 OpenAPI 双组、Reminder Client 契约及架构门禁不回退；
- 不提交、不推送，不覆盖任务开始前已有修改。

本轮不实现登录、Dashboard、Reminder 领域逻辑、业务 DO/Mapper、Flyway
Repository、Nacos/Redis/MySQL 部署编排或完整业务 E2E。这些能力依赖后续业务
App 实现，不能用 Common 测试冒充已通过。

## 2. 方案选择

### 2.1 采用方案：薄 Common 能力层 + App 自有运行适配器

Common 提供技术中立契约、可组合实现和测试能力；WebFlux GlobalFilter、
Servlet Filter、安全路由和具体 Client Bean 仍由运行模块拥有。

该方案满足以下现行边界：

- `common-security` 不引入 MVC/Reactor Starter 自动配置；
- `common-http` 不拥有 Reminder 业务契约；
- `common-data` 不提供跨服务共享 DO；
- Gateway 不依赖 WebMVC Starter、Servlet 或 Tomcat；
- Reminder Client 不依赖 Boot Starter、springdoc、Servlet 或 Tomcat。

### 2.2 不采用：全部做成自动配置 Starter

将安全、数据和 HTTP 能力全部自动装配可减少 App 配置，但会隐藏运行时依赖，
扩大 Bean 副作用，并容易让 WebFlux、Servlet、Sa-Token 和阻塞式 HTTP Client
跨边界混用。

### 2.3 不采用：只填充三个空模块但不接入消费者

该方案只能证明类型能够编译，无法证明 LoadBalancer、请求头、安全校验和
MyBatis 插件能在真实 Spring 上下文中工作，不满足本轮完成目标。

## 3. 总体依赖

```text
common-core
├── common-web
│   └── common-webmvc-starter
├── common-security
├── common-data
├── common-http ──> common-core + common-web + common-security
└── common-test

gateway ──> common-core + common-security
auth-app ──> common-webmvc-starter + common-security + common-data
tracking-app ──> common-webmvc-starter + common-security + common-data
                + common-http + reminder-client
reminder-app ──> common-webmvc-starter + common-security + common-data
                + reminder-client
```

`common-test` 只允许作为测试依赖。业务 App 不互相依赖；Client 不依赖 App。

## 4. 模块设计

### 4.1 `worthit-common-core`

保留现有 `ErrorCode`、`BusinessException`、`PageQuery` 和 `PageResult`。

新增 TraceId 基础能力：

- `TraceIdGenerator`：无框架函数接口；
- `UuidTraceIdGenerator`：使用 JDK UUID 生成 32 位小写十六进制 TraceId；
- 生成值非空、无连字符，长度固定为 32。

外部传入的 `X-Trace-Id` 不参与生成或清洗判断。Gateway 必须无条件删除外部
同名头并生成新值，因此 Common 不提供“信任外部 TraceId”的便捷入口。

不引入 ULID 依赖。接口终稿只冻结 TraceId 为不透明字符串，示例
`01HX...` 不构成格式契约。

### 4.2 `worthit-common-web`

保留统一 `ApiResponse`、`FieldViolation`、JSON 字段顺序和轻量 Schema 注释。

本轮不加入：

- Servlet/WebFlux Filter；
- Controller Advice；
- HTTP 状态映射；
- Sa-Token 运行时；
- 业务错误码。

远端错误解码复用该模块的信封字段，但不改变公网 JSON 契约。

### 4.3 `worthit-common-webmvc-starter`

保留已完成的：

- Spring MVC；
- Bean Validation；
- springdoc；
- `public=/api/**` 与 `internal=/internal/**` 双组自动配置；
- 默认及生产环境关闭文档的配置约定。

本轮不把 Same-Token、用户鉴权、Trace Filter 或业务异常适配塞入 Starter。
三个 App 分别声明自身安全路径和错误映射。

### 4.4 `worthit-common-security`

提供以下技术中立能力：

- `SecurityHeaderNames`：`Authorization`、Sa-Token Same-Token、
  `X-Caller-Service`、`X-User-Id`、`X-Session-Id`、`X-Trace-Id`；
- `SecurityErrorCode`：只包含接口终稿已冻结的
  `AUTH_UNAUTHORIZED`、`AUTH_FORBIDDEN`；
- `UserContext`：保存已通过 Sa-Token 校验的正数 `userId`；
- `SameTokenProvider`：获取当前 Same-Token；
- `SameTokenVerifier`：校验调用方提供的 Same-Token；
- `SaTokenSameTokenService`：基于 Sa-Token 1.45.0 `SaSameUtil` 同时实现
  Provider 与 Verifier。

`INTERNAL_AUTH_FAILED` 只在技术门禁中标记为建议值，尚未被接口终稿冻结，
本轮不新增该错误码。

该模块只依赖 Sa-Token 核心能力，不依赖 MVC/Reactor Starter，不注册 Filter
或自动配置。

### 4.5 `worthit-common-data`

提供显式导入、无业务实体的 MyBatis-Plus 基础配置：

- `WorthItMybatisPlusConfiguration`：
  - `PaginationInnerInterceptor(DbType.MYSQL)`；
  - `OptimisticLockerInnerInterceptor`；
- `LogicalDeleteConstants`：未删除 `0`、已删除 `1`；
- `CurrentAuditor`：返回当前操作用户，可为空；
- `WorthItMetaObjectHandler`：
  - insert 填充 `createTime`、`updateTime`；
  - 有用户上下文时填充 `createBy`、`updateBy`；
  - update 填充 `updateTime` 和可用的 `updateBy`；
  - 时间由注入的 `Clock` 获取，测试不依赖系统当前时间。

不提供 BaseDO。Auth、Tracking、Reminder 的表字段并不完全一致，共享 BaseDO
会把 `createBy`、`delFlag` 或 `version` 错误扩散到不拥有这些字段的表。

逻辑删除由具体 DO 使用 `@TableLogic` 明确选择；Reminder 实例不得使用逻辑
删除字段。

MyBatis-Plus 3.5.9 以后分页插件需要独立 JSqlParser 依赖，POM 必须显式满足
该运行依赖。

### 4.6 `worthit-common-http`

提供阻塞式内部 HTTP Client 基础能力：

- `HttpClientTimeouts`：类型安全的连接、读取超时，必须为正数；
- `TraceIdProvider`：为当前调用提供可信 TraceId；
- `InternalRequestContext`：调用方服务名、Same-Token Provider、TraceId
  Provider；
- `InternalRequestHeadersInterceptor`：覆盖写入 Same-Token、
  `X-Caller-Service` 和 `X-Trace-Id`；
- `RemoteServiceException`：保存目标服务、HTTP 状态、远端稳定 code、
  远端 traceId 和安全消息；
- `ApiResponseErrorHandler`：有界读取错误体，识别统一信封；无法解析时返回
  稳定技术异常，不泄露响应体、堆栈或内部类型；
- `HttpServiceClientFactory`：
  - 克隆调用方提供的 `RestClient.Builder`；
  - 设置服务名 base URL、超时、请求头拦截器和错误处理器；
  - 使用 `RestClientAdapter + HttpServiceProxyFactory` 创建
    `@HttpExchange` 代理。

调用方提供的 Builder 可以是 `@LoadBalanced RestClient.Builder`。base URL
使用 `http://<service-id>`；Spring Cloud LoadBalancer 将虚拟服务名解析为
真实实例。

默认不注册自动重试。GET 或携带幂等键的命令是否重试由业务调用方明确配置，
避免 Common 对非幂等请求做隐式重放。

### 4.7 `worthit-common-test`

保留现有三类 ArchUnit 规则，并增加：

- Gateway 不得依赖 Servlet、Spring MVC、Tomcat 或 WebMVC Starter；
- Client 不得依赖 Boot Starter、springdoc、Servlet 或 Tomcat；
- `common-web` 不得依赖 MVC、WebFlux、Servlet、Tomcat 或 springdoc runtime；
- `common-test` 不得成为 App 的 compile/runtime 依赖。

增加测试辅助能力时只服务于两个以上真实测试消费者；不建立全局测试数据
大杂烩。

## 5. 运行模块接入

### 5.1 Gateway

Gateway 保持 WebFlux：

- 新建 Gateway 启动类和服务自有 `GlobalFilter`；
- 对外部请求先删除 Same-Token、`X-Caller-Service`、`X-User-Id`、
  `X-Session-Id`、`X-Trace-Id`；
- 生成可信 TraceId；
- 获取并写入 Same-Token；
- 不新增 JDBC、阻塞式 `RestClient` 或 Servlet 依赖。

Sa-Token Reactor 的完整用户登录校验依赖后续 Auth/Redis 登录链路。本轮验证
头清洗、可信头重建和运行栈隔离，不宣称 TECH-SEC-001/002 已通过。

### 5.2 Auth、Tracking、Reminder

三个 App 各自拥有 Servlet 安全配置：

- `/internal/**` 必须校验 Same-Token；
- 公网业务路径的用户登录校验在业务接口实现时接入；
- 文档和必要健康端点只在现行环境开关允许时暴露；
- 失败不得进入 Application 成功路径。

本轮使用测试 Controller/测试上下文验证 Same-Token 拒绝和放行，不创建虚假
业务成功接口。

三个 App 显式导入 `common-data` 基础配置；不提前创建业务 DO。

### 5.3 Tracking 到 Reminder

Tracking 在 `infrastructure/client` 拥有 Reminder Client 配置：

- 声明负载均衡 `RestClient.Builder`；
- 通过 `HttpServiceClientFactory` 创建 `ReminderCommandClient`；
- service id 固定为 `worthit-reminder`；
- caller service 固定为 `worthit-tracking`；
- 超时从类型安全配置读取，不在业务代码硬编码；
- 不把代理自动配置放入 `reminder-client`。

使用真实 HTTP 测试服务器验证：

- 请求路径和 JSON 契约；
- `X-Idempotency-Key` 保持调用方传值；
- Same-Token、caller service、TraceId 被注入；
- 4xx/5xx 统一错误解码；
- 读取超时进入技术异常路径。

## 6. 数据流

### 6.1 外部请求

```text
Client
  -> Gateway 删除不可信内部头
  -> Gateway 生成 TraceId + 注入 Same-Token
  -> MVC App 校验 Same-Token
  -> MVC App 通过 Sa-Token 获取 userId
  -> Controller/Application
```

### 6.2 Tracking 内部调用

```text
Tracking Application/Outbox Relay
  -> ReminderCommandClient
  -> common-http 注入 Same-Token/caller/TraceId
  -> LoadBalancer 解析 worthit-reminder
  -> Reminder /internal/v1/reminders/reconcile
  -> Reminder 校验 Same-Token
  -> 统一响应或远端错误解码
```

### 6.3 数据审计

```text
App-owned DO
  -> MyBatis-Plus
  -> common-data MetaObjectHandler
  -> Clock + CurrentAuditor
  -> 只填充实体实际声明的审计字段
```

## 7. 错误处理

- Common Core 异常继续使用稳定 `ErrorCode`，不携带 HTTP 状态。
- 安全失败使用已冻结的 `AUTH_UNAUTHORIZED` 或 `AUTH_FORBIDDEN`。
- 远端 HTTP 错误转换为 `RemoteServiceException`，保留远端稳定 code 和
  traceId，消息使用中文。
- 未识别的 HTML、空 body 或超大错误体不得原样进入异常消息或日志。
- 超时、连接失败和服务实例不可用必须保持 cause，不能返回假成功。
- 资源归属、防枚举和业务冲突仍由业务 App 按接口终稿转换。

## 8. 测试策略

生产行为严格执行 RED → GREEN → REFACTOR：

1. TraceId 生成与边界；
2. Security Header、UserContext、Same-Token Provider/Verifier；
3. MyBatis 插件顺序、审计填充、逻辑删除常量；
4. HTTP 请求头、代理创建、错误解码、超时；
5. Tracking Reminder Client 集成；
6. Gateway 可信头清洗与重建；
7. 三个 MVC App Same-Token 路径门禁；
8. ArchUnit 与依赖树隔离；
9. 现有 OpenAPI 和 Reminder Client 回归；
10. 全 Reactor `validate`、`test`、`package`。

配置文件和 POM 不适合用行为单测直接驱动时，先记录失败的构建、依赖树或
静态检查证据，再做最小修改。

## 9. 验收证据

至少执行并报告：

```bash
mvn validate
mvn test
mvn package
mvn -pl <每个变更模块> -am test
mvn dependency:tree
git diff --check
git status --short --untracked-files=all
```

必须分别说明：

- 单元测试、Spring 上下文测试、真实本地 HTTP 测试各覆盖什么；
- 哪些 Phase 0 P0 门禁已被本轮证据直接证明；
- 哪些门禁因业务实现、Redis、Nacos、MySQL 或部署尚未进入范围而仍未验证；
- 当前已有 OpenAPI 重命名和 handoff 文件仍被保留；
- 当前分支、提交和推送状态。

## 10. 回滚

本轮不提交。若设计需要撤回，可按本次新增文件的精确清单删除，并逐文件恢复
本次对 POM 和运行模块的改动；不得使用 `git reset --hard`、`git clean` 或覆盖
任务开始前已有的 OpenAPI 重命名现场。
