# ReAct 完整链路：亲手实现练习

## 本轮目标和边界

固定请求：`读取 demo.txt，列出待办事项`。

这是基于现有 MiniReActCli 的独立练习类，不是 PaiCLI 已完成的二次开发成果。
已有 Agent.java、Main.java、GLMClient.java、ToolRegistry.java 保留原样。
核心循环位于 `src/main/java/com/paicli/agent/LoopPracticeAgent.java` 的 `run()` 中。
当前练习循环与 PracticeMain 使用 PracticeToolRegistry，已接入 read_file、write_file；原 Agent 和默认 Main 仍使用原 ToolRegistry。
你先完成初稿，随后明确要求助手补全；助手修正了编译、消息角色、结果回传和轮次控制。
构造器、测试、本说明及助手补全部分不作为你独立实现的证据；下一步通过解释、调试和亲手修改验证掌握。
你随后亲手添加了单轮工具数量判断（超过 2 个则停止），2026-10-03 全部 10 个测试通过。

现有项目已有一个完整 Agent.run()。可将它作为对照，但本轮先根据消息流程自己实现；
现有代码的作者身份和你的掌握程度，不由“文件存在”来证明。

## 完成顺序

1. 解释对象和消息变化，再实现 LoopPracticeAgent.run()。
2. 验证不调用工具、真实读文件、多个工具结果、文件不存在和轮次限制。
3. 审查代码，脱稿解释，并做一次现场修改。
4. 将练习入口接到真实 GLMClient，验证真实模型选择工具；离线测试不代表真实模型验证。
5. 对照 PaiCLI Agent.run() 的预算、取消、流式输出等差异，明确接入方式并验证。
6. 完成后才形成对应的个人贡献和简历表述。

本轮不扩展 benchmark，不添加 Memory、DAG、MCP 或前端。

## 先认识四个参与者

| 参与者 | 谁创建/调用 | 输入 | 输出/下一站 |
| --- | --- | --- | --- |
| 调用者（本轮为测试） | 创建模型、注册表、练习 Agent，再调用 run | 用户文本 | 接收最终答案 |
| LoopPracticeAgent | 调用者创建 | 用户文本、模型和工具注册表 | 维护 messages，驱动模型与工具接力 |
| GLMClient | Agent 调用 chat | messages 和工具定义 | ChatResponse：最终内容或工具调用 |
| PracticeToolRegistry | 练习 Agent 调用 executeTool | 工具名和参数 JSON | 文件内容、写入成功说明或失败说明 |

模型决定要调用哪个工具，Java 执行工具。工具执行不会自动触发下一次模型请求；
你写的循环负责把结果加入 messages，并再次调用模型。

## 固定数据与两轮请求

测试中的 demo.txt 是一个真实临时文件，内容：

```text
TODO: 增加登录校验
TODO: 添加上传失败提示
```

第一轮发送：

```text
messages[0] = system：回答依赖文件时先调用工具
messages[1] = user：读取 demo.txt，列出待办事项
tools = 当前注册的工具说明（其中有 read_file）
```

第一轮预设模型返回（不是最终答案）：

```text
ChatResponse
  content = null
  toolCalls = [
    id = call_1
    function.name = read_file
    function.arguments = {"path":"demo.txt"}
  ]
```

Agent 保存这条 assistant 消息，再调用 PracticeToolRegistry.executeTool。
工具执行后，第二轮发送的 messages 应是：

```text
system：规则
user：原始请求
assistant：请求 read_file，调用 ID 为 call_1
tool：demo.txt 的真实内容，toolCallId 为 call_1
```

assistant 表示“模型刚才要求做什么”，tool 表示“Java 做完之后得到什么”。
必须保留两条消息；toolCallId 用来关联结果属于哪一次调用，而不是关联文件名。

第二轮预设模型返回 `content=有两项待办`、`toolCalls=[]`。
保存最终 assistant 消息，返回内容，结束本次 run。
当前 run() 已由助手在你的初稿上补全，原 9 个离线用例通过；也已走通一次真实模型请求，见下方记录。

## 写之前，找到可调用的方法

在 IDE 中从这些方法跳转查看定义，不需要背签名：

```java
GLMClient.Message.user(userInput)
llmClient.chat(conversationHistory, toolRegistry.getToolDefinitions())
response.hasToolCalls()
GLMClient.Message.assistant(response.content())
GLMClient.Message.assistant(response.content(), response.toolCalls())
toolRegistry.executeTool(toolCall.function().name(), toolCall.function().arguments())
GLMClient.Message.tool(toolCall.id(), result)
```

`conversationHistory` 是下一次模型请求的消息列表，本轮不等于完整 Memory 系统。
`MAX_ITERATIONS=8` 表示一次 run 最多调用模型 8 次，不是最多执行 8 个工具。
一次响应可有多个 toolCalls，本轮按返回顺序逐个执行，再进入下一轮。
工具定义告诉模型“有什么工具和参数”；executeTool 才实际读取文件。

## run() 应完成什么

以下是已补全的 LoopPracticeAgent.run() 方法体应承担的职责。

1. 添加用户消息。
2. 在轮次限制内调用模型。
3. 无工具调用：保存最终 assistant 消息，返回答案（null 内容按空字符串处理）。
4. 有工具调用：保存包含调用的 assistant 消息，执行每个工具，保存匹配 ID 的 tool 消息，然后继续下一轮。
5. 达到轮次上限：返回代码注释中约定的提示。

本轮模型 IOException 交给调用者，方法已声明 throws IOException。
文件不存在等工具失败由 PracticeToolRegistry 返回字符串，仍作为 tool 结果交给模型。
两条失败路线不要混成“所有问题都是模型调用失败”。

## 运行验证

在 C:\workspace\MiniReActCli 目录执行：

```powershell
mvn test "-Dtest=LoopPracticeAgentTest" -DskipTests=false
```

或者在 IDE 中运行 LoopPracticeAgentTest。
测试子类覆盖 GLMClient.chat()，按顺序返回预设响应，不读取 .env、不发送模型网络请求。
PracticeToolRegistry 使用真实临时文件。第二轮消息在调用瞬间复制，能检查内容和 ID 的正确性。
最初的占位实现会让 5 个测试因 UnsupportedOperationException 报错；现在该占位异常已被替换。

| 用例 | 应观察到什么 |
| --- | --- |
| 直接回答 | 一次模型调用，带上用户输入与工具定义 |
| 读取 demo.txt | 两次模型调用，第二次收到真实文件内容与 call_1 |
| 同轮两个工具 | 两个结果内容分别对应 call_a、call_b |
| 文件不存在 | 失败说明以 tool 消息回传，随后模型回答 |
| 持续调用工具 | 恰好 8 次模型调用后停止 |

2026-10-03 助手补全后运行：
`mvn test "-Dtest=LoopPracticeAgentTest,ToolRegistryTest" -DskipTests=false`。
循环测试 5/5、原有工具测试 4/4，合计 9/9 通过。测试检查第二轮收到的真实临时文件内容和调用 ID。
这是离线验证；后续已补做一次真实模型验证。你的脱稿解释和修改、PaiCLI 接入尚待完成。

### 2026-10-03 真实模型验证

已新增 PracticeMain 演示入口，实际调用现有 GLMClient，再驱动 LoopPracticeAgent。
原有 Main 的默认入口保持不变，仅将 loadApiKey 改为包内可见供演示复用。
pom.xml 允许用 exec.mainClass 选择演示入口，默认仍为原 Main。

在项目根目录执行：

```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.paicli.cli.PracticeMain"
```

真实文件：notes/react-demo.txt，包含登录校验、上传失败提示、合并缺片检查三项 TODO。
实际模型请求共两轮：第一轮 2 条消息，模型返回 read_file；
第二轮 4 条消息，tool 消息包含真实文件内容，调用 ID 与 assistant 请求一致；
第二轮模型不再请求工具，最终返回三项 TODO。
本次可读运行日志保存在 target/react-live-trace.txt（构建目录清理后会消失）。
这证明一次真实请求走通，不代表所有模型输出与异常场景都已验证。

### 本轮由你修改：限制单轮工具调用数量

原有上限 8 限制模型调用次数，但一轮模型响应仍可能提出很多工具请求。
需求：一轮最多允许 2 个工具调用；超过 2 个，整个本轮不执行任何工具，
直接返回 `单轮工具调用超过2个，已停止执行`。
1 个和 2 个工具的原有行为不变，8 轮上限仍保留。

本轮只修改 LoopPracticeAgent.run()，在确认有工具调用后、记录 assistant 调用及执行工具之前判断。
判断输入是 response.toolCalls() 的列表长度，不是 interations，也不是历史消息数量。
这一段现已由你实现，助手准备验收用例并审查；没有代改你的数量判断。

新用例 rejectsMoreThanTwoToolCallsBeforeExecutingAnyTool 给出 3 个写文件请求，
要求模型只调用一次、三个临时文件都未创建，并返回约定停止提示。
写文件只用于测试隔离临时目录，无需真实模型或 API Key。
已有两个读取工具的用例同时验证上限等于 2 时仍可正常执行。

新用例可以单独运行：

```powershell
mvn test "-Dtest=LoopPracticeAgentTest#rejectsMoreThanTwoToolCallsBeforeExecutingAnyTool" -DskipTests=false
```

2026-10-03 在你实现后运行 `mvn test -DskipTests=false`：循环用例 6/6、工具用例 4/4，合计 10/10 通过。
超过 2 个工具的用例确认三个临时文件均未创建，且只调用模型一次；原有 1、2 个工具、失败回传及 8 轮上限用例通过。
一次真实模型读文件请求已验证。你的脱稿解释、进一步完整模块实现和 PaiCLI 接入仍待完成。

## 与 PaiCLI 的对应位置

当前已完成练习注册表接入：用户填写 write_file 的参数取值，助手补入 Java 标准库 Files.writeString 调用及返回结果，并切换练习入口和测试使用的注册表类型。
新增 writesFileAndReportsMatchingResultToSecondRound 用例：第一次预设模型响应要求 write_file，实际临时文件写入“你好”；第二次请求含 assistant 调用和 tool 成功结果，二者 ID 均为 call_write_1。
全项目 mvn test -DskipTests=false，18/18 通过（循环 7、练习注册表 7、原注册表 4）。注册表接入测试的模型输出预设，文件写入真实执行；后续已运行下方的真实模型写入后读取验证。

- PaiCLI Agent.java 的 run()：用户输入进入 conversationHistory，再调用模型。
- response.hasToolCalls() 分支：保存 assistant 调用、执行工具、按 ID 保存 tool 结果。
- 没有工具调用的分支：保存最终回答并结束。
- PaiCLI 还处理预算、取消、压缩、流式展示等。本练习先验证共用的消息核心，再逐项解释接入差异。

本轮脱稿检查：谁决定读文件？谁真正读文件？文件内容如何进入下一次请求？
为什么 assistant 和 tool 都要保存？为什么需要 ID？文件失败与模型失败分别去哪里？

## 下一轮：跟踪工具结果进入第二次模型请求

### 2026-10-04 真实模型写入后读取验证

getToolDefinitions() 已由助手准备，当前无需重复填写。PracticeMain 新增命令行请求参数；不提供参数时仍运行原来的只读 TODO 演示。
实际调用 GLMClient，使用 PracticeToolRegistry 和 LoopPracticeAgent；请求将“ReAct链路验证：你好”写入 notes/react-write-read-20261004.txt，收到写入结果后再读取同一个文件。

实际三轮请求：消息数依次为 2、4、6。第一轮请求 write_file，ID 为 call_-7224167599263705312；第二轮收到匹配 ID 的写入成功结果，再请求 read_file，ID 为 call_-7224146983420686024；第三轮收到匹配 ID 的真实文件内容，返回“ReAct链路验证：你好”，不再请求工具。
助手另行读取实际文件，确认内容相同；Maven 演示进程退出码为 0。原始运行日志为 target/react-write-read-live-trace.txt，构建目录清理后可能消失。本次验证一个真实多步请求，不代表所有模型输出和异常场景已验证。

可在项目根目录重跑该演示（覆盖本演示创建的文件）：

```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.paicli.cli.PracticeMain" "-Dexec.args=请先调用write_file，将内容ReAct链路验证：你好写入notes/react-write-read-20261004.txt。收到写入结果后，再调用read_file读取同一个文件。最后只回答读到的完整内容。"
```

### 调试工具结果进入下一轮请求

在 LoopPracticeAgent.run() 的 `llmClient.chat(...)` 那一行左侧设置断点，
Debug 运行 LoopPracticeAgentTest 的 `sendsRealFileContentsAndMatchingCallIdToSecondRound`。
第一次停住时，conversationHistory 应有 system、user 两条消息。
继续运行，第二次停在同一行时，应有 system、user、assistant、tool 四条消息。
展开第四条：role=tool、toolCallId=call_1、content 为 demo.txt 的真实内容。

`GLMClient.Message.tool(callId, result)` 创建一条结果消息，
`conversationHistory.add(...)` 将它加入下次请求的输入，
下一次 `chat(conversationHistory, ...)` 把更新后的历史交给模型客户端。
测试客户端保存参数快照；真实 GLMClient 则将这些消息序列化为 HTTP 请求里的 messages。

## 当前练习：一次任务的累计工具调用上限

这是新增能力，现有每轮最多 2 个工具、最多 8 次模型调用的限制保留。总工具调用上限定为 10，使原有 8 轮限制仍可独立验证。
每次 run() 从 0 开始计数，每次 executeTool 返回后加一，失败结果也算一次。若累计次数加本轮工具数超过 10，整轮都不执行，返回“达到工具调用次数限制，已停止执行”；检查在记录本轮 assistant 调用之前。恰好 10 次允许执行，随后模型仍可返回最终答案；下一次 run() 重置预算。

助手在 LoopPracticeAgent 中准备 MAX_TOOL_EXECUTIONS=10 和局部计数器 executedToolCount=0；随后按用户“直接帮我补充”的请求，补入超限判断与计数加一。这两处为助手实现，不计为用户独立编写；JSON、文件 API 和原有数量判断保持不变。
新增测试 countsFailedToolsAndRejectsBatchThatExceedsTotalBudget 检查失败调用也消耗次数，以及超限整轮不写文件；allowsExactlyTenToolsAndResetsBudgetForNextRun 检查等于上限、最终答案与下一次任务重置。
当前 mvn test -DskipTests=false 共 20 项全部通过，验证累计超限整轮不执行、失败调用消耗次数、恰好 10 次允许、最终答案仍可返回，以及下一次任务重置计数。本轮只做离线临时文件测试，不请求 GLM。
