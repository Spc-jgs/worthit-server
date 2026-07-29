# 工程质量决策与 GitHub CI 设计

## 1. 目标

本轮建立两项长期工程基线：

1. 将“发现问题后选择整体最优解，而不是默认做最小修复”写成可执行的 Agent
   决策规则；
2. 为 WorthIt Server 建立可重复、安全、强制执行的 GitHub CI 合码门禁。

这里的“整体最优”不是无限扩大范围或追求抽象数量，而是基于证据比较候选方案，
在当前权威契约与阶段边界内，优先选择能同时守住正确性、完整链路、架构边界、
性能、安全、可维护性和长期总成本的方案。

## 2. 当前证据

- 仓库没有 `.github/workflows`，PR #2、#3 均显示 `no checks reported`；
- `main` 当前没有 Branch Protection，任何有写权限的操作者都能绕过 PR 和测试；
- 根 Reactor 已有 Java/Maven Enforcer、Surefire、ArchUnit、MySQL 8.4
  Testcontainers、API/OpenAPI、迁移和并发测试，共 347 个测试；
- 本机 Maven 3.8.8 已进入 EOL，而 Maven 官方当前维护的 3.x 版本为 3.9.16；
- GitHub Actions 已启用，默认 `GITHUB_TOKEN` 权限为只读，但仓库未要求 Action
  固定到完整提交 SHA。

因此问题不是缺少测试，而是缺少一条可重复、不可静默跳过的远端执行链路。

## 3. 候选方案

### 3.1 只继续依赖本地测试

优点是零配置；缺点是无法防止漏跑、环境差异和直接推送，不能形成合码门禁。
不采用。

### 3.2 使用 Runner 预装 Maven 和 Action 浮动标签

实现简单，但 Maven、Runner 工具和 Action 标签会漂移；标签还可被移动，不满足
可重复构建和供应链安全要求。不采用。

### 3.3 固定 Maven Wrapper、Action SHA 和单一权威质量任务

采用该方案：

- Maven Wrapper 固定 Maven 3.9.16，并校验发行包 SHA-256；
- GitHub 官方 Action 固定到已核实 release 对应的完整提交 SHA；
- 一个 `quality` Job 完成全 Reactor `clean verify` 和 Flyway 源一致性校验；
- Testcontainers 直接使用 GitHub Ubuntu VM 的 Docker，不额外维护共享数据库；
- 失败时上传 Surefire/Failsafe 报告，成功时不保存冗余制品；
- 合并后将 `CI / quality` 设为 `main` 必需状态检查。

单 Job 避免多个 Job 重复下载依赖、重复拉取 MySQL 镜像，也保证 Maven Reactor
只存在一套合码结论。项目规模增长到单次门禁明显超出反馈预算后，再以测试分层
和测量数据决定是否并行拆分。

## 4. Workflow 设计

触发条件：

- 发往 `main` 的 Pull Request；
- `main` push，用于验证实际合并提交；
- `workflow_dispatch`，用于人工重跑。

运行边界：

- `ubuntu-24.04` GitHub-hosted VM；
- Temurin Java 17；
- `permissions: contents: read`；
- PR 同分支新提交自动取消旧运行；
- Job 最长 20 分钟；
- 不使用 `pull_request_target`，不读取仓库 Secret，不执行部署；
- `./mvnw --batch-mode --no-transfer-progress clean verify`；
- `bash scripts/verify-flyway-source-parity.sh`；
- 仅失败时上传测试报告，保留 7 天。

Action 固定值：

| Action | Release | 完整提交 SHA |
| --- | --- | --- |
| `actions/checkout` | `v7.0.1` | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | `v5.6.0` | `03ad4de0992f5dab5e18fcb136590ce7c4a0ac95` |
| `actions/upload-artifact` | `v7.0.1` | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` |

## 5. Maven 可重复构建

使用 Maven Wrapper `only-script` 模式：

- 提交 `mvnw`、`mvnw.cmd` 和 `.mvn/wrapper/maven-wrapper.properties`；
- 不提交 `maven-wrapper.jar`；
- `distributionUrl` 固定 Apache Maven 3.9.16；
- `distributionSha256Sum` 固定并验证下载内容；
- Enforcer 将 Maven 要求收敛为 `[3.9.0,4.0.0)`，拒绝已 EOL 的 3.8 和尚未
  正式采用的 Maven 4。

本地和 CI 的权威命令统一为 `./mvnw`。系统 Maven 只用于首次生成 Wrapper，
不再决定项目实际构建版本。

## 6. Branch Protection

Workflow 在 PR 上成功后合并。合并完成并确认 `main` push 运行成功后，再为
`main` 建立保护：

- 必须通过 `CI / quality`；
- 必须使用 PR；
- 禁止 force push 和删除；
- 保留 merge commit 流程，不要求线性历史；
- 个人仓库不要求他人审批，避免形成无法满足的单人审批门禁；
- 管理员同样受规则约束，避免紧急操作静默绕过质量门禁。

如果当前 GitHub 套餐或权限无法应用保护，必须如实登记为未完成，不能把 Workflow
存在等同于合码门禁已经强制执行。

## 7. 非目标

- 本轮不引入 Sonar、第三方 SaaS、制品发布、自动部署、SBOM 或全仓格式化；
- 不设置覆盖率百分比阈值；现有测试尚未形成经审计的覆盖率基线，直接设数字会
  鼓励为覆盖率写测试，而不是证明风险；
- 不在 CI 连接开发机 MySQL、Redis、Nacos 或读取环境 Secret；
- 不借 CI 任务升级冻结的 Spring Boot/Cloud/SCA 业务基线。

这些能力只有在出现真实风险、明确发布目标和可验证收益后，再独立设计。

## 8. 验收与提交

提交边界：

1. `docs(backend): 明确整体最优解决策原则`
2. `ci: 建立可重复的GitHub质量门禁`

验收：

- Wrapper 实际使用 Maven 3.9.16；
- 本地 `./mvnw clean verify` 全绿；
- Flyway 源一致性脚本通过；
- Workflow YAML 可解析；
- PR 的 `CI / quality` 成功；
- 合并后的 `main` push CI 成功；
- Branch Protection 返回必需状态检查 `CI / quality`；
- 本地 `main` 与 `origin/main` 一致且工作区干净。
