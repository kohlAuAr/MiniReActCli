# MiniReActCli

Java 17 学习项目：通过一个可运行的 Agent，练习 ReAct、工具注册、任务依赖调度，并在开发过程中使用 Git。

## 当前进度

- ReAct：工具说明、参数解析、文件读写、调用结果回传和执行次数限制已接通；20 个相关测试通过。
- 真实模型：已验证 GLM 先写文件、再读取、最后回答的三轮请求。
- DAG：A/B/C 案例、执行框架和四项测试已准备，`DagPracticeExecutor.findReadyTasks()` 尚待实现，因此全项目测试目前有三项未通过。
- Multi-Agent、MCP、Memory 与 PaiSmart 实践按学习路线继续，尚未在本项目完成。

这是学习者与 AI 助手共同完成的练习项目，具体实现分工和验证范围记录在练习文档中。

## 运行

需要 Java 17 和 Maven。在首次克隆后的项目目录创建本机配置：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，填写自己的 `GLM_API_KEY`。`.env` 已被 Git 忽略。

运行已有 ReAct 与工具测试，不需要 API Key：

```powershell
mvn test -DskipTests=false "-Dtest=LoopPracticeAgentTest,PracticeToolRegistryTest,ToolRegistryTest"
```

运行真实模型读文件演示：

```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.paicli.cli.PracticeMain"
```

DAG 核心方法完成后，可运行完整测试和 DAG 演示：

```powershell
mvn test -DskipTests=false
mvn -q compile exec:java "-Dexec.mainClass=com.paicli.cli.DagPracticeMain"
```

## 学习入口

- [整体路线](LEARNING-ROADMAP.md)
- [ReAct 实践](REACT-PRACTICE.md)
- [工具注册表实践](TOOL-REGISTRY-PRACTICE.md)
- [DAG 实践](DAG-PRACTICE.md)
- [随项目练习 Git](GIT-PRACTICE.md)
