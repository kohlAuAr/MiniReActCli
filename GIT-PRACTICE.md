# 随项目练习 Git

目标：每完成一次真实的代码修改，就练习查看改动、选择提交内容、保存本地版本和推送 GitHub。遇到分支、合并和撤销需求时再结合实际任务学习，不先背完整命令表。

## 四个位置

```text
工作区中的修改
  -- git add --> 暂存区：本次准备提交的内容
  -- git commit --> 本地仓库：一个有编号的版本
  -- git push --> GitHub：远程仓库中的版本
```

git add 和 git commit 都只操作本地。即使本地已提交，也必须 push 才会上传。
main 是本地主分支；origin 是目标远程仓库的别名，地址为 https://github.com/kohlAuAr/MiniReActCli.git。
origin/main 是本地记录的远程 main 状态；它通过 fetch/push 等操作更新，不是实时查看 GitHub 的窗口。

## 本次首次上传

此前已执行 git init -b main、git add . 和首次 commit；首次提交为 895f01a，包含已有 ReAct 与待完成的 DAG 框架。
随后绑定 origin；新增 README 与本说明，将作为一次单独的文档提交上传。README 如实说明 DAG 核心方法未完成。

首次绑定和上传的命令是：

```powershell
git remote add origin https://github.com/kohlAuAr/MiniReActCli.git
git push -u origin main
```

remote add 只绑定地址，不上传。-u 建立 main 与 origin/main 的跟踪关系，后续在 main 上可直接 git push。origin 已绑定，不重复执行 remote add。
.env、target、IDE 配置已忽略，实际 API Key 未进入首次提交。

本机 Git 直连 github.com 曾超时，已发现 Windows 配置的代理为 127.0.0.1:10808。若只有 Git 连接失败，可为单次命令指定该现有代理：

```powershell
git -c http.proxy=http://127.0.0.1:10808 push -u origin main
```

-c 只对这次 Git 命令生效，不修改全局配置；需要本机代理服务正在运行。

## 下次完成 DAG 后，走一次完整流程

在 C:\workspace\MiniReActCli 中：

```powershell
git status
git diff
mvn test -DskipTests=false
git add src/main/java/com/paicli/plan/DagPracticeExecutor.java
git diff --cached
git commit -m "feat: implement DAG dependency scheduling"
git push
git status
```

git status 查看改了哪些文件；git diff 查看尚未暂存的具体变化；git add 选定文件；git diff --cached 检查本次提交内容；git commit 保存本地版本；git push 上传；最后 status 检查是否还有未提交改动。
如果还改了测试或文档，检查后按实际文件追加 git add。add 之后再修改文件，要重新 add 才会将新修改纳入提交。
完成方法前，DAG 测试的失败属于学习占位；完成方法后，提交应以完整测试通过为验收。

## 后续结合实际任务学习

先用 DAG 的一次修改练熟上述流程；随后在下一项功能中创建 feature 分支，再练合并。每次提交描述实际改了什么，区分本人实现与助手提供的部分。

官方参考：[git status](https://git-scm.com/docs/git-status)、[git diff](https://git-scm.com/docs/git-diff)、[git add](https://git-scm.com/docs/git-add)、[git commit](https://git-scm.com/docs/git-commit)、[git push](https://git-scm.com/docs/git-push)。
