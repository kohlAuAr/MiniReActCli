# DAG 完整案例：依赖完成后才能执行

## 本轮目标与位置

继续使用 C:\workspace\MiniReActCli，在 com.paicli.plan 下新增 DAG 练习。现有 ReAct、工具注册表和默认入口保留。
A 读取样例 README，B 读取样例 pom.xml，C 使用两者结果汇总。演示输入文件在 notes/dag-demo 下，均为明确标注的样例。
本轮是顺序执行的依赖调度，不是并行或 Multi-Agent。先完成这块，随后把 A/B 的动作换成现有 LoopPracticeAgent.run，并连接不同子 Agent。

## 已准备的三个文件

- src/main/java/com/paicli/plan/DagTask.java：一个任务，包括 ID、依赖 ID 列表、执行动作。Action.execute 是本项目定义的接口，执行框架调用它；不是 Java 已有的文件方法。
- src/main/java/com/paicli/plan/DagPracticeExecutor.java：校验依赖、维护状态、执行任务、传递结果。只有 findReadyTasks 未实现，留给学习者。
- src/main/java/com/paicli/cli/DagPracticeMain.java：建立 C/A/B 三个任务并调用执行框架。故意把 C 放在列表最前面，验证依赖决定执行顺序。

任务动作、状态处理框架、演示和测试由助手准备，不计为学习者独立实现。Files.readString、Path.of、List/Map 操作来自 Java 标准库；record 的 id()、dependencies()、action() 是 Java 自动生成的访问方法。没有新的 Maven 依赖。

## 固定请求与数据流

```text
任务列表：[C, A, B]
C.dependencies = [A, B]
A.dependencies = []
B.dependencies = []

第一次筛选：状态均为 PENDING，只返回 [A, B]
框架执行 A/B，保存结果并将两者改为 COMPLETED
第二次筛选：A/B 已完成，C 为 PENDING，只返回 [C]
框架将 {A: README内容, B: pom内容} 交给 C.action.execute
C 返回汇总文字，框架将 C 改为 COMPLETED，计划结束
```

状态含义：PENDING 等待；RUNNING 正在执行；COMPLETED 成功；FAILED 执行失败；BLOCKED 依赖失败导致不能执行。
独立任务可以继续，依赖失败的任务不会汇总错误内容。未知依赖在执行前拒绝；无法继续推进的循环依赖明确报错。循环检测是运行时检测，含独立任务的错误计划可能先执行独立任务，不是全图预校验。

## 你只实现一个完整方法

位置：DagPracticeExecutor.findReadyTasks(List<DagTask> tasks, Map<String, Status> statuses)。

- 输入 tasks：完整任务列表，每个任务可通过 task.id() 和 task.dependencies() 读取 ID 与依赖。
- 输入 statuses：任务 ID 到当前状态，例如 A -> COMPLETED。
- 输出：本轮可运行的 List<DagTask>；使用原任务对象，不执行动作、不修改状态。

处理规则：创建结果列表；遍历任务，只检查 PENDING；检查它的所有依赖是否都是 COMPLETED；满足条件则加入结果列表；最后返回列表。无依赖任务自然满足条件，非 PENDING 的任务不能重复执行。
任务当前状态使用 statuses.get(task.id())，依赖状态使用 statuses.get(dependencyId)。这一块集中写完整方法，不分成逐行练习。

## 验收与运行

当前骨架能编译，findReadyTasks 是明确的占位异常，演示尚不能成功执行。当前全项目 24 项测试，原有 20 项及新增未知依赖检查通过，另外 3 项等待该方法实现。

先运行 DAG 四项测试：

```powershell
mvn test -DskipTests=false "-Dtest=DagPracticeExecutorTest"
```

验证：C 等待 A/B 且收到实际结果；A 失败时 C 阻塞但 B 继续；循环依赖停止；未知依赖在任何任务执行前停止。
通过后运行全项目 mvn test -DskipTests=false，目标 24 项全部通过。

完成方法后运行样例：

```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.paicli.cli.DagPracticeMain"
```

预期执行顺序 [A, B, C]，三者状态 COMPLETED，C 汇总两个真实样例文件内容。本轮不发送模型请求，不需要 API Key。
参考现有 C:\workspace\DAY39.md（任务与依赖）、DAY42.md（按依赖执行），无需重读全部教程。
