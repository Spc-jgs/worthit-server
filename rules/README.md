# WorthIt Server 后端规则索引

本目录补充根目录 `AGENTS.md`，用于约束后端设计、编码、测试和仓库操作。规则不能替代上级项目终稿；发生冲突时，按 `AGENTS.md` 中的规则优先级处理。

## 强制阅读顺序

| 顺序 | 规则 | 适用任务 |
| --- | --- | --- |
| 1 | [00-project-context.md](00-project-context.md) | 所有任务必读 |
| 2 | [10-architecture.md](10-architecture.md) | 模块、依赖、契约、数据或跨服务任务 |
| 3 | [20-java-code-style.md](20-java-code-style.md) | 新增或修改 Java 代码 |
| 4 | [30-spring-maven.md](30-spring-maven.md) | Spring、配置、依赖或 POM 任务 |
| 5 | [40-testing-quality.md](40-testing-quality.md) | 功能、缺陷修复和交付验收 |
| 6 | [50-git-workflow.md](50-git-workflow.md) | 所有会修改仓库的任务 |

## 按任务选择

- 纯调查或审阅：至少阅读 `00-project-context.md`；如果会执行 Git 操作，再读 `50-git-workflow.md`。
- Java 功能开发或缺陷修复：六份专题规则全部阅读。
- Maven 或基础设施调整：阅读 `00`、`10`、`30`、`40`、`50`。
- API、Client、数据库或跨服务改动：阅读 `00`、`10`、`20`、`30`、`40`、`50`，并打开上级文档索引登记的对应终稿。
- 只修改规则文档：阅读 `00` 和 `50`，并核对根目录 `AGENTS.md`。

## 使用原则

- 规则描述的是实现边界，不是业务需求副本。
- 上级 [WorthIt 项目文档定稿索引](../../docs/README.md) 是需求、架构、接口、数据库和测试终稿的统一入口。
- 不得通过修改规则文件绕过现行终稿或测试门禁。
- 新规则应主题单一、可执行、可验证；不要重复散落同一条约束。
