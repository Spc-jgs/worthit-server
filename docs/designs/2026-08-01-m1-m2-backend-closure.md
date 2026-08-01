# M1/M2 后端交付闭环设计

## 1. 目标与边界

本轮闭合以下四项已经授权的后端工作：

- 公网接口只要求 `Authorization: Bearer {token}`，Gateway 将其规范化为现有
  Sa-Token 内部请求头后再执行登录校验。
- 分类支持自定义分类重命名，保持系统“未分类”受保护、用户隔离、有效名称唯一。
- 提供显式启用的本地四服务公网 API 门禁，覆盖登录、认证与分类核心链路。
- 同步 PRD、架构、接口、数据库、技术门禁、产品验收和定稿索引。

明确不在本轮范围内：

- Flyway 依赖版本、MySQL 8.4 兼容治理、迁移脚本修改或新增。
- M3、分类批量迁移/排序/适用范围、全仓架构治理。
- 前端工作台、页面、样式或导航修改；前端只允许删除 HTTP Adapter 的兼容请求头。

## 2. Gateway Bearer 边界

### 2.1 现状与根因

接口终稿要求 `Authorization: Bearer`，但四服务的 Sa-Token `token-name` 为
`worthit-token`。前端因此同时发送两个请求头。直接修改 `token-name` 会同时改变
Sa-Token Redis 键前缀和 Same-Token 键名，造成现有会话与内部认证命名空间迁移。

### 2.2 方案

在 Gateway 增加一个先于 `SaReactorFilter` 执行的 WebFilter：

1. 对所有请求先删除外部传入的 `worthit-token`。
2. 当且仅当请求只有一个格式正确、Token 非空的 Bearer Authorization 时，将原始
   Token 写入内部 `worthit-token` 请求头。
3. 缺失、非 Bearer、空 Token 或多个 Authorization 不写入内部头；受保护路径由现有
   `SaReactorFilter` 返回统一 `401 AUTH_UNAUTHORIZED`，匿名登录路径仍可进入 Auth。
4. 保留原始 Authorization 并下传；下游 MVC 服务继续以内部 `worthit-token` 二次
   校验登录态，符合 Gateway 与下游双校验基线。

该方案不改变 Redis 键、JWT、Same-Token、路由或下游 MVC 配置。回滚只需移除新
WebFilter 并暂时恢复客户端兼容头。

### 2.3 验证

- 仅 Bearer 可通过 Gateway 与下游二次校验。
- 外部伪造 `worthit-token` 被覆盖，不能绕过缺失/非法 Authorization。
- 多个或畸形 Authorization 失败关闭。
- 微信/密码登录匿名入口保持可用。
- Redis Same-Token 键仍为 `worthit-token:var:same-token`。

## 3. 分类重命名

### 3.1 公网契约

`PATCH /api/v1/categories/{id}`，请求体：

```json
{ "name": "办公设备" }
```

成功返回现有 `CategoryResponse`。名称沿用创建接口的非空、最大 32 字符和去除首尾
空白规则。分类不存在、已删除或越权返回 `404 RES_NOT_FOUND`；系统分类返回
`409 BIZ_CATEGORY_SYSTEM_PROTECTED`；与当前用户其他有效分类重名返回
`409 BIZ_CONFLICT`。

### 3.2 事务与并发

应用服务在事务内按 `user_id + id` 锁定分类行，先区分不存在与系统分类，再更新名称、
`update_by`、`update_time` 并令 `version = version + 1`。数据库现有
`uk_category_active_name(user_id, active_name)` 是最终并发防线，唯一键冲突统一映射为
`BIZ_CONFLICT`。

重命名不改变分类标识，物品、订阅、想买引用无需迁移；查询通过分类表读取最新名称，
因此不写 Outbox，也不影响 Reminder。

## 4. 本地真实 API 门禁

新增独立、显式执行的脚本，不让常规 Maven 测试依赖开发机容器。脚本要求调用方通过
环境变量提供本地测试用户名和密码，敏感值不得输出。链路只经 Gateway，且只发送
Authorization Bearer：

1. 密码登录并取得 Token。
2. 查询当前用户。
3. 创建唯一命名的自定义分类。
4. 重命名并查询确认。
5. 删除未使用分类并确认列表不可见。

脚本不启动、停止、重建容器，不初始化或清空数据库。失败时输出 HTTP 状态、稳定错误码
和 TraceId，不输出 Token 或密码。

## 5. 文档与交付

语义变化按定稿规则创建新版本：PRD 补充密码登录适用端与简单分类改名；架构补充认证
入口规范化和密码凭据边界；接口冻结密码登录、Bearer 规则和分类 PATCH；数据库只记录
已经存在的密码凭据表，不修改任何 Flyway SQL；技术门禁与产品验收补齐自动化和 E2E。

后端按功能分提交并走 feature branch → PR → CI/可合并检查 → merge → 同步 main → push。
上级文档仓库若无可用远端，仅保留已渲染验证的工作区交付事实，不把它伪装成后端 PR
内容。前端若需移除兼容头，使用独立 feature branch 和 PR，且只修改 HTTP Adapter 及其
测试。
