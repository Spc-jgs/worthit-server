# 项目上下文与权威基线

## 当前阶段

WorthIt Server 是《值不值》小程序的后端工程。Phase 0、M1/M2 后端闭环、M3 Tracking
完整恢复和 M3 基础数据导出已经完成；当前处于其余 M3 公开试用准备能力的契约核对与
增量实施阶段。

- 账号注销、导入和备份恢复尚未实现；不得仅凭目录、标题或产品概述自行补全接口、
  状态、表结构、迁移与验收语义。
- 公开试用采用的受支持 Spring Boot、Spring Cloud 和 Spring Cloud Alibaba 版本线尚未
  决策；版本迁移必须独立授权并重新执行 Phase 0。
- 不为“以后可能使用”创建空模块、抽象层、Client 或基础设施。
- 任务未授权业务代码时，不从架构文档自行推导并追加实现范围。

## 唯一文档入口

从后端仓库根目录进入上级 [WorthIt 项目文档定稿索引](../docs/README.md)。从当前文件进入时使用 [同一文档索引](../../docs/README.md)。

需求和测试资料必须通过该索引定位现行终稿：

| 领域 | 权威内容 |
| --- | --- |
| 产品需求 | 产品范围、业务动作、状态语义和阶段目标 |
| 后端架构 | 服务拆分、模块边界、调用方向、部署和技术决策 |
| 接口契约 | HTTP 路径、请求响应字段、错误码、请求头和内部 Client |
| 数据库 | 表、字段、索引、约束和 Flyway 迁移顺序 |
| 技术测试 | Phase 0、P0/P1 技术门禁和合码条件 |
| 产品验收 | M1/M2、M3 完整恢复与基础数据导出验收、金标数据和需求追溯 |

不得把某一领域文档用于覆盖另一领域的权威结论。修改一个基线时，必须检查其他基线中的版本引用、契约和追溯关系。

## 技术基线

- Java 17。
- M1 自用/学习候选线：Spring Boot 3.5.16、Spring Cloud 2025.0.3、Spring Cloud Alibaba 2025.0.0.0、Nacos 3.0.3、Sa-Token 1.45.0。
- 上述 Boot/Cloud 组合不作为公开试用或生产推荐；公开试用前重新选择仍受支持的版本线并重跑 Phase 0。
- 本阶段不升级 Spring Boot 4。
- 根 Maven 版本为 `0.1.0-SNAPSHOT`，版本治理集中在根 POM。

## 服务边界

| 服务 | 主要职责 | 数据边界 |
| --- | --- | --- |
| `worthit-gateway` | 公网入口、路由、安全头清洗、可信 TraceId、限流 | 不持有业务表 |
| `worthit-auth` | 微信登录、身份、会话、账号生命周期 | 只访问 Auth 数据 |
| `worthit-tracking` | 物品、订阅、想买、分类、Dashboard、Outbox | 只访问 Tracking 数据 |
| `worthit-reminder` | Binding、提醒实例、reconcile、待处理与忽略 | 只访问 Reminder 数据 |

服务不得直接访问其他服务的数据库，也不得以一个本地事务包裹多个服务。

## 冻结术语与契约

- 公网写接口使用 `Idempotency-Key`。
- 内部 Outbox 到 Reminder reconcile 使用 `X-Idempotency-Key`。
- 外部客户端不得控制最终 TraceId；Gateway 负责删除不可信同名头并生成或清洗可信 TraceId。
- `operationType` 由 Tracking 服务端按用例生成，不接受小程序伪造。
- `sourceVersion` 用于跨服务乱序收敛；相同版本必须结合规范化 payload 摘要判定幂等或冲突。
- reconcile 和 Outbox payload 必须包含 `schemaVersion`；M1 固定为 `1`。
- Reminder Client 不包含 `cause`、`ResolutionCause`、`correction` 或 `displayName`。
- Reminder 实例生命周期只有 `PENDING`、`PROCESSED`、`IGNORED`、`CANCELED`。

## 停止条件

出现以下任一情况时，停止受影响的实现并请求确认：

- 上级文档索引或当前终稿不可读取；
- 终稿之间存在无法按权威领域消解的冲突；
- 需求需要新增终稿未定义的字段、状态、错误码、接口、表或模块；
- 任务需要实施现行终稿尚未冻结契约的 M3 能力，或扩大到公开试用版本迁移；
- 需要修改任务范围外的上级文档、前端仓库或环境配置。
