# Git 与仓库操作规则

## 仓库边界

`worthit-server` 是独立 Git 仓库。上级 WorthIt 协作仓、前端仓库和其他目录不属于后端仓提交范围。

开始修改前执行：

```bash
git rev-parse --show-toplevel
git branch --show-current
git status --short --untracked-files=all
```

确认命令作用于预期仓库。不要因当前目录嵌套而把上级仓或兄弟仓当作同一提交范围。

## Worktree 位置与所有权

- 本项目创建的所有 Git worktree 必须位于后端仓库根目录的 `.worktrees/` 下。
- 每个 worktree 使用独立、可识别的任务目录，例如 `.worktrees/reminder-reconcile`。
- 创建前先运行 `git check-ignore .worktrees/<task-slug>`，确认 `.worktrees/` 已被 Git 忽略。
- 禁止把本项目 worktree 创建到上级 WorthIt 目录、兄弟仓库、用户主目录或其他仓库内部。
- 创建 worktree 前检查目标分支和目标目录均不存在，避免复用含有未知修改的目录。
- 只清理由当前任务创建、且已确认不再需要的 worktree；用户创建或来源不明的 worktree 不得删除。
- 删除 worktree 前检查未提交修改和未推送提交；任何可能丢失内容的清理都需要用户明确确认。

标准位置示例：

```bash
git worktree add .worktrees/<task-slug> -b feature/<task-slug>
```

`.worktrees/` 只定义本项目 worktree 的存放位置，不扩大任务对分支、提交、推送或删除操作的授权。

## 保护用户修改

- 所有已有 staged、unstaged 和 untracked 文件默认属于用户，除非当前任务明确创建。
- 不覆盖、格式化、移动、删除或顺带提交任务范围外的改动。
- 修改与用户变更重叠时，先检查差异；无法安全隔离时停止并请求确认。
- 不以“清理工作区”为理由删除未跟踪文件或构建资料。
- 不修改用户环境配置、IDE 配置或本机 Secret。

## 精确暂存

只在用户明确要求提交时暂存，并使用精确路径：

```bash
git add -- path/to/file-a path/to/file-b
```

混合工作区禁止使用：

```text
git add -A
git add .
git commit -am
```

提交前必须检查：

```bash
git diff --check
git diff --cached --check
git diff --cached --name-only
git status --short
```

如果 staged 列表包含任务范围外文件，停止提交并先恢复正确的暂存边界；不得丢弃文件内容。

## 分支与提交

- 未经明确要求，不创建或切换分支。
- 未经明确要求，不执行 commit、push、merge、rebase、cherry-pick 或 tag。
- 提交信息使用 Conventional Commits：

```text
<type>(<scope>): <中文描述>
```

- `type` 和 `scope` 必须反映真实变更，例如 `feat(auth)`、`fix(reminder)`、`test(tracking)`、`docs(backend)`。
- 一个提交只包含一个可审阅目的；不要把格式化、配置和业务改动无理由混在一起。
- 提交前重新运行与该提交风险相称的测试，不能引用修改前的旧结果。

## 推送与集成

- 只有用户明确要求时才推送。
- 推送前确认远端、目标分支、当前提交和上游关系。
- 不 force push，除非用户明确授权且已说明会被覆盖的提交范围。
- 不自动合并主分支或创建 PR；先报告当前分支是否可合并、测试状态和剩余改动。
- 推送失败时报告实际错误，不通过更换远端、协议或凭证绕过用户配置。

## 禁止的破坏性操作

未经用户针对准确目标明确授权，禁止：

- `git reset --hard`；
- 强制 checkout/restore 覆盖工作区文件；
- `git clean -fd` 或递归删除仓库内容；
- `git branch -D`；
- `git push --force` 或 `--force-with-lease`；
- 改写共享分支历史。

需要删除时先确认精确路径、Git 状态和可恢复方式；能移动到废纸篓时优先使用可恢复操作。

## 完成前检查

完成任务前执行并报告：

1. 与任务相称的测试和构建；
2. `git diff --check`；
3. `git status --short --untracked-files=all`；
4. staged、unstaged、untracked 的范围；
5. 当前分支和是否产生提交；
6. 未验证事项和失败命令。

“代码已修改”不等于“任务已完成”。涉及数据库、HTTP、外部服务或部署时，必须提供对应运行证据。

## 本轮范围说明

独立 worktree 中如何定位上级项目文档不属于当前规则初始化范围，后续单独设计；本文件当前只冻结 worktree 的存放位置和安全操作边界。
