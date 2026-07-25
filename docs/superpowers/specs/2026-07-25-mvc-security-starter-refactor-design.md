# WorthIt MVC 安全运行时下沉设计

日期：2026-07-25

## 1. 目标与现状证据

Auth、Tracking、Reminder 当前分别复制了以下实现：

- `TrustedSourceFilter`
- `UserLoginVerifier`
- `SaTokenUserLoginVerifier`
- `SaTokenRuntimeConfiguration`
- `SameTokenVerifier`、`TraceIdGenerator` 的启动类 Bean
- 高度同构的 `TrustedSourceFilterTest`

这些实现承担相同的 Servlet 运行时职责，却散落在三个 App 中。继续复制会让
Same-Token、TraceId、登录异常映射和 Sa-Token 配置发生漂移。

本轮目标是把三个真实消费者共同使用的技术运行时下沉到
`worthit-common-webmvc-starter`，删除 App 内重复实现，同时保持现有 HTTP
路径、请求头、错误码和 Gateway WebFlux 边界不变。

## 2. 方案选择

### 2.1 采用：在现有 WebMVC Starter 增加安全自动配置

`common-security` 继续只提供技术中立的头名、错误码、Same-Token 端口与
Sa-Token 适配器。`common-webmvc-starter` 增加 Servlet 专属 Filter、登录校验
和 Spring Boot 自动配置。三个 App 通过可覆盖的登录策略表达差异。

选择理由：

- 三个 Servlet App 都已直接依赖该 Starter，消费者和运行模型完全一致；
- 不新增只有单一职责片段的新模块，满足 YAGNI；
- 自动配置使用 `@ConditionalOnMissingBean`，服务仍能显式覆盖策略和实现；
- Gateway、Client、`common-core`、`common-security` 不会获得 Servlet 依赖。

### 2.2 不采用：只抽取基类或工具方法

该方案仍要求三个 App 分别注册 Filter、Sa-Token Bean 和登录校验器，只消除
局部代码，无法消除运行时装配漂移。

### 2.3 不采用：新增 `common-security-webmvc-starter`

当前所有 Servlet App 已统一使用 `common-webmvc-starter`，新增 Starter 会让
消费方必须维护两个总是一起出现的 Starter，并增加依赖和自动配置顺序成本。
只有出现不同安全运行模型或独立消费者时才重新评估拆分。

## 3. 目标架构

```text
common-core
├── common-web
├── common-security  (无 Servlet、无 Boot 自动配置)
└── common-webmvc-starter
    ├── Spring MVC / Validation / OpenAPI
    ├── Sa-Token MVC 运行依赖
    └── 通用安全 Filter 与自动配置

auth-app
├── Auth 登录接口匿名策略
└── Same-Token 轮换

tracking-app
├── Reminder Client
└── ServletTraceIdProvider

reminder-app
└── 无安全运行时复制

gateway
└── 独立 WebFlux Filter，不依赖 WebMVC Starter
```

## 4. 公共接口与行为

### 4.1 登录策略

Starter 暴露：

```java
@FunctionalInterface
public interface PublicRequestAuthorizationPolicy {

    boolean requiresLogin(String requestPath);
}
```

默认策略对所有 `/api/**` 请求要求登录。Auth 注册自有实现，仅精确路径
`/api/v1/auth/wechat/login` 返回 `false`，其余公网路径返回 `true`。

### 4.2 登录校验

Starter 暴露 `UserLoginVerifier`，默认实现调用 `StpUtil.checkLogin()`。服务可通过
注册同类型 Bean 覆盖默认实现，便于测试或后续认证扩展。

### 4.3 可信来源 Filter

公共 `TrustedSourceFilter`：

- 只处理 `/api/**` 与 `/internal/**`；
- 两类路径均先校验 Same-Token；
- `/api/**` 再按 `PublicRequestAuthorizationPolicy` 决定是否校验登录；
- 接受合法 `X-Trace-Id`，否则生成新的 32 位 TraceId；
- 同时把可信 TraceId 写入 request attribute 和响应头；
- Same-Token 失败返回 403 + `AUTH_FORBIDDEN`；
- 登录失败返回 401 + `AUTH_UNAUTHORIZED`；
- 错误响应继续使用统一 `ApiResponse`。

将 request attribute 写入统一为三个服务的共同语义；此前只有 Tracking 写入是
实现漂移，不是对外契约差异。

### 4.4 自动配置

新增 `WorthItMvcSecurityAutoConfiguration`，仅在 Servlet Web Application 和
必要类存在时生效，并通过 `@ConditionalOnMissingBean` 提供：

- JWT Simple `StpLogic`
- `SaTokenSameTokenService`
- `SameTokenProvider` 与 `SameTokenVerifier` 的共同实例视图
- `UuidTraceIdGenerator`
- `UserLoginVerifier`
- 默认 `PublicRequestAuthorizationPolicy`
- `TrustedSourceFilter`

自动配置通过
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
逐行登记，不依赖组件扫描。

## 5. 依赖与迁移

- Starter 增加 `common-security`、Sa-Token Boot/JWT/Redis 运行依赖。
- App 只保留源码直接使用的最小依赖；Auth/Tracking 因轮换与内部 Client 继续
  直接依赖 `common-security`，Reminder 移除不再直接使用的依赖。
- 三个 App 删除重复的 Filter、登录校验器和 Sa-Token 运行配置。
- 三个启动类删除公共 Bean；Tracking 保留 `TraceIdProvider` Bean。
- Auth 新增匿名登录策略并单测精确路径，禁止使用宽泛前缀放行。

## 6. 兼容、验证与回滚

兼容要求：

- 不新增或修改 API、请求头、错误码、状态码和 Same-Token 语义；
- Gateway 保持 WebFlux 且不获得 Servlet/Starter 依赖；
- `common-security` 仍不依赖 Spring Boot、MVC、Servlet；
- Auth Same-Token 轮换和 Tracking 内部 Client 行为不变。

验证分四层：

1. 公共 Filter 单测覆盖路径、认证、错误信封和 TraceId；
2. `ApplicationContextRunner`/Servlet 上下文测试覆盖默认 Bean 与覆盖点；
3. 三个 App 的策略、架构测试和 Maven 全量测试；
4. 使用 `/Users/shaopc/Documents/Script/dev-stack` 的 MySQL、Redis、Nacos
   重启 Auth、Reminder、Tracking，执行 `scripts/local-infra/verify.sh`。

回滚只需恢复三个 App 的本地实现、移除 Starter 安全自动配置与依赖，不涉及
数据库、配置中心数据或接口契约迁移。
