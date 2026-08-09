# M3 账号注销交付记录

## 交付范围

本轮按已批准设计完成 Auth 申请、查询、撤销、七天冷静期、到期调度、全会话撤销、
Tracking/Reminder 本地持久写围栏、跨服务幂等物理清理和失败恢复。新增 Auth V3、
Tracking V3、Reminder V2 正常业务迁移；不处理既有 Flyway/MySQL 兼容警告治理。

## 真实四服务验收

2026-08-09 使用 MySQL 8.4、Redis 7.4、Nacos 3.0.3 和四个本地应用，经 Gateway 完成：

- 两个隔离账号、主账号两枚并存 Token，以及 Tracking/Reminder 均有数据的前置夹具；
- 申请同键重放、不同键复用单一开放记录、跨用户撤销隐藏、本人撤销重放和再次申请；
- Reminder 下线后把隔离申请调整为到期，Auth 稳定停留 `EXECUTING`，Tracking 已完成
  物理清理并保持 `CANCELLED` 围栏；
- 两枚历史 Token 同时返回 `401`，执行态密码登录返回 `AUTH_FORBIDDEN`；
- Reminder 恢复后 Auth 自动整组重试并进入 `COMPLETED`；
- Auth 身份、密码凭证、外部身份、登录审计和幂等记录归零，仅保留最小完成记录；
- Tracking 和 Reminder 本人业务数据归零，围栏为 `CANCELLED`，次账号数据计数与登录
  保持不变。
- Auth 暴露 `pending/executing` 数、最老开放任务时长、claim/成功/重试/失败及两下游耗时；
  Tracking、Reminder 暴露清理、重放与围栏冲突指标。

验收只对本轮唯一账号做受控时间夹具和物理清理，没有清库、删除卷或修改 dev-stack。

## 自动化与工程门禁

交付前执行并要求全部通过：

```bash
./mvnw clean verify
./mvnw -DskipTests package
bash scripts/verify-flyway-source-parity.sh
bash scripts/tests/verify-flyway-source-parity-test.sh
scripts/local-infra/verify.sh
git diff --check
```

上述门禁全部通过：`clean verify` 覆盖 19 个 Reactor 模块，共 526 项测试，0 failure、
0 error、0 skipped；四个 `scripts/tests/*.sh`、八份运行时迁移的来源/摘要一致性、package
和 `git diff --check` 均通过。首次全量运行暴露 Auth 最终会话撤销 IT 意外依赖本机 Redis，
已改为对 `UserSession` 端口做测试替身并由定向 IT 与第二次 `clean verify` 复验。

PR 和合并提交在正常 PR 交付完成后补录。MySQL 8.4 下既有 Flyway 兼容警告只记录、不压制、
不宣称解决。
