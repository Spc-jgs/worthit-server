# Gateway P0 修复设计

## 目标

只修复 2026-07-26 代码审查中已经确认的两个 Gateway P0：

1. 未登录请求在可信 Header GlobalFilter 执行前进入 Sa-Token 错误处理，导致 TraceId 缺失或回显外部伪造值。
2. 用户明确要求新增的账号密码登录没有加入 Gateway 匿名白名单，公网入口不可达。

物品管理闭环缺口和其余 P1 本轮不修改代码，统一登记为后续待办。

## 设计

### 认证失败 TraceId

`GatewaySecurityErrorWriter` 注入 `TraceIdGenerator`。认证失败时不读取公网请求携带的
`X-Trace-Id`，而是直接生成新的服务端可信 TraceId，并同时写入统一错误信封和响应头。

这样不改变成功代理链路：正常请求仍由 `TrustedHeadersGlobalFilter` 清洗外部 Header、
生成 TraceId，并注入 Same-Token。错误链路不再错误依赖尚未执行的 GlobalFilter。

### 密码登录白名单

`GatewaySaTokenConfiguration` 将以下两个登录入口同时排除登录校验：

- `/api/v1/auth/wechat/login`
- `/api/v1/auth/password/login`

其他 `/api/**` 路径继续要求有效登录态，健康检查继续保持原行为。

## 测试

采用 TDD 增加或调整 Gateway 测试：

1. 直接按真实外层顺序执行 `SaReactorFilter`，未登录请求不经过
   `TrustedHeadersGlobalFilter` 也必须返回统一 401。
2. 请求携带伪造 TraceId 时，401 响应必须使用新生成的可信 TraceId。
3. 微信登录和密码登录都能匿名进入下游链路。
4. 普通 `/api/**` 路径仍被认证拦截。
5. 执行 Gateway 定向测试和相关 Reactor 测试；提交前执行完整 Maven 门禁。

## 非目标与待办

- 不删除或重构账号密码登录、密码凭据表和本地账号初始化。
- 不在本轮修改上级 WorthIt 权威文档。
- 不在本轮实现 Item PATCH、DELETE、restore、Outbox Relay 或 Reminder reconcile。
- 不在本轮修复分类删除并发、超长 categoryId、Flyway/MySQL 兼容性等 P1。

