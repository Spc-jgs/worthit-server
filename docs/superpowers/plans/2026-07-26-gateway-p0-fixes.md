# Gateway P0 Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Gateway 未登录错误链路的可信 TraceId，并放行用户明确要求的账号密码登录入口，同时登记本轮不处理的剩余问题。

**Architecture:** 认证失败发生在 Gateway GlobalFilter 之前，因此错误写入器直接依赖 `TraceIdGenerator` 生成可信值，不读取公网 Header。Sa-Token 白名单同时声明微信与密码登录路径；成功代理链路仍由现有 `TrustedHeadersGlobalFilter` 清洗 Header 和注入 Same-Token。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Cloud Gateway WebFlux、Sa-Token 1.45.0、JUnit 5、Reactor Test、Maven。

## Global Constraints

- 只修改 `worthit-server` 仓库。
- 不删除或重构密码登录、V2 凭据表及本地账号初始化。
- 不修改上级 WorthIt 权威文档。
- 不实现 Item、Outbox Relay 或 Reminder 的剩余业务。
- 所有生产代码修改先写失败测试并观察预期失败。
- 最终提交使用中文 Conventional Commit 描述并推送当前 `main`。

---

### Task 1: 修复认证失败 TraceId

**Files:**
- Modify: `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/GatewaySaTokenSecurityTest.java`
- Modify: `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/security/GatewaySecurityErrorWriter.java`

**Interfaces:**
- Consumes: `com.shaopc.worthit.common.core.trace.TraceIdGenerator#generate()`
- Produces: `GatewaySecurityErrorWriter(ObjectMapper, TraceIdGenerator)`；`unauthorized(Throwable)` 始终返回服务端生成的 TraceId。

- [x] **Step 1: 写真实顺序下的失败测试**

  调整 `GatewaySaTokenSecurityTest`，直接调用 `SaReactorFilter` 处理未登录请求，不再把
  `TrustedHeadersGlobalFilter` 人工包在外层。请求携带 `X-Trace-Id: trace-forged`，
  注入的生成器返回 `trace-trusted`，断言 401 响应头和 JSON 均使用
  `trace-trusted`，且生成器只调用一次。

- [x] **Step 2: 运行测试并确认 RED**

  Run:

  ```bash
  mvn -pl worthit-gateway -am \
    -Dtest=GatewaySaTokenSecurityTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -DfailIfNoTests=false test
  ```

  Expected: `rejectsMissingLoginWithUnifiedUnauthorizedResponse` 因现有实现读取伪造
  Header 或缺失可信 TraceId 而失败。

- [x] **Step 3: 写最小实现**

  `GatewaySecurityErrorWriter` 构造器新增 `TraceIdGenerator`，保存非空依赖；
  `unauthorized` 调用生成器并校验非空白，不再读取请求 Header。

- [x] **Step 4: 运行测试并确认 GREEN**

  重新执行 Step 2 命令，Expected: `GatewaySaTokenSecurityTest` 全部通过。

### Task 2: 放行密码登录入口

**Files:**
- Modify: `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/GatewaySaTokenSecurityTest.java`
- Modify: `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/security/GatewaySaTokenConfiguration.java`

**Interfaces:**
- Consumes: `/api/v1/auth/password/login`
- Produces: 微信和密码登录均可匿名进入 Gateway 下游链路；其他 `/api/**` 保持登录校验。

- [x] **Step 1: 写失败测试**

  将 `/api/v1/auth/password/login` 加入匿名入口参数集合，未登录状态下断言下游链路被调用。

- [x] **Step 2: 运行测试并确认 RED**

  执行 Task 1 Step 2 命令。Expected: 密码登录路径被 Sa-Token 拦截，匿名路径测试失败。

- [x] **Step 3: 写最小实现**

  在 `GatewaySaTokenConfiguration` 为 Sa-ReactorFilter 增加密码登录排除路径。

- [x] **Step 4: 运行测试并确认 GREEN**

  重新执行定向测试，Expected: 全部通过。

- [x] **Step 5: 提交 Gateway 修复**

  ```bash
  git add worthit-gateway/src/main worthit-gateway/src/test
  git commit -m "fix(gateway): 修复认证错误链路并放行密码登录"
  ```

### Task 3: 登记剩余问题

**Files:**
- Create: `docs/tasks/2026-07-26-code-review-followups.md`
- Modify: `docs/superpowers/plans/2026-07-26-gateway-p0-fixes.md`

**Interfaces:**
- Produces: 后续工作清单，明确优先级、证据、验收标准和本轮未实施状态。

- [x] **Step 1: 写待办文档**

  登记以下事项：

  - Item PATCH、DELETE、restore、Outbox Relay 和 Reminder reconcile 闭环。
  - 分类删除与业务对象创建的并发一致性。
  - 19 位 categoryId 超出 `Long.MAX_VALUE` 时应返回 400。
  - Flyway 11.7.2 与 MySQL 8.4 兼容基线。
  - 密码登录对应权威产品、接口和数据库文档同步。

- [x] **Step 2: 检查待办可执行性**

  每项必须包含现状、风险、建议修复边界和可验证完成标准；不得写入未确认的新业务语义。

- [x] **Step 3: 更新本计划勾选状态并提交**

  ```bash
  git add docs/tasks/2026-07-26-code-review-followups.md \
    docs/superpowers/plans/2026-07-26-gateway-p0-fixes.md
  git commit -m "docs(review): 登记剩余代码质量待办"
  ```

### Task 4: 完整验证与推送

**Files:**
- Verify only.

**Interfaces:**
- Produces: 可复查的测试、构建、Git 和远端推送证据。

- [ ] **Step 1: 执行完整 Maven 测试**

  ```bash
  mvn test -DfailIfNoTests=false
  ```

  Expected: Reactor 全模块 `SUCCESS`，0 failures，0 errors。

- [ ] **Step 2: 执行构建与静态 Git 检查**

  ```bash
  mvn package -DskipTests
  git diff --check
  git status --short --branch
  ```

  Expected: 构建成功、无空白错误、工作树干净且只包含预期提交。

- [ ] **Step 3: 推送当前分支**

  ```bash
  git push origin main
  ```

  Expected: 远端 `main` 更新到本次最后一个提交。
