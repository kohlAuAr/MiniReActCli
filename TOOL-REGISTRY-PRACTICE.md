# ToolRegistry 亲手实现：第一步，执行入口

## 为什么进入这一块

ReAct 循环已经调用过 ToolRegistry，但之前工具注册和执行使用的是现成代码。
这一步对应简历的工具注册表能力：你实现“名称与参数 -> 本地工具 -> 执行结果”的入口。
固定调用仍是 read_file，参数 {"path":"demo.txt"}。
参考现有 DAY37.md 第 4.4 节（名称怎样找到 Tool，并调用执行器），不用重读全部教程。

## 本轮位置与边界

- 你填写 src/main/java/com/paicli/tool/PracticeToolRegistry.java 的 executeTool()。
- 原 ToolRegistry、Agent 和默认 Main 继续保留；练习用 LoopPracticeAgent 与 PracticeMain 已切换为 PracticeToolRegistry。
- PracticeToolRegistry 已接入练习循环；本轮用预设模型响应测试读写链路，不使用 API Key。
- 构造器、read_file 注册、工具定义转换及验收测试由助手准备；本轮只将 executeTool 中你写的部分归为你的工作。
- 你已写出按名称找工具及不存在分支。由于 JSON 转换尚不熟悉，助手新增 toArgumentMap(JsonNode) 辅助方法；本轮无需手写字段遍历，它不作为你的独立实现贡献。
- 用户填写了 write_file 的 path、content 取值；参数说明、注册骨架、文件 API 调用、接入修改与验收测试由助手完成，不将助手部分计为用户独立实现。

## 数据在调用前已经是什么

```text
name = "read_file"
argumentsJson = "{\"path\":\"demo.txt\"}"
tools = Map，里面已注册 read_file 对应的 Tool 对象
mapper = ObjectMapper，用于解析 JSON 字符串
```

Tool 对象有名称、说明、JSON 参数定义和 executor。
注册时保存的 executor 接收 Map<String,String>，然后读取其中的 path 并访问真实文件。
它检查空路径及路径是否跨出根目录；这是本练习的路径检查，不是完整文件沙箱。

## 你实现的处理顺序

1. tools.get(name) 查找工具。不存在就返回 `未知工具：` 加工具名，不继续解析和执行。
2. 使用 mapper.readTree(argumentsJson) 得到 JsonNode。
3. 参数必须是 JSON 对象；数组等返回 `工具参数必须是JSON对象`。
4. 调用已准备的 toArgumentMap(arguments)，得到 Map<String,String>，本例中是 path -> demo.txt。
5. 调用该 Tool 保存的 executor，传入参数 Map，把返回的字符串交还调用者。
6. JSON 解析或执行器异常转成 `工具执行失败: ` 加异常信息。

可以查看现有 ToolRegistry.executeTool() 作对照，但先说清每步输入输出，再自己写。
IDE 自动补全和跳转用于找方法，不要求背接口签名。
本轮使用辅助方法处理字段遍历，无需新增 HashMap、Iterator 导入。

你需要分清的两个动作：mapper.readTree 把 JSON 字符串读成 JsonNode，
toArgumentMap 把 JsonNode 的字段整理成执行器接收的 Map。
拿到 argumentMap 后，再调用 tool 保存的 executor；转换参数本身不会读文件。
JSON 转换的库用法后续可以单独练，当前目标仍是工具执行接力。

## 验收

在 C:\workspace\MiniReActCli 中执行：

```powershell
mvn test "-Dtest=PracticeToolRegistryTest" -DskipTests=false
```

七个注册表用例检查：实际读文件、未知工具、错误 JSON/非对象参数、文件不存在、根目录外读取、实际覆盖写文件、根目录外写入。
路径与文件异常应保留为工具结果，不应冒充模型调用异常。
当前 executeTool() 已实现：用户完成查找工具、未知工具分支并尝试对象检查；助手按用户请求写入解析、转换、执行及异常处理，并将对象检查修正为 !arguments.isObject()。
执行入口完成时，2026-10-03 全项目 15 个测试通过，当时尚未接入 LoopPracticeAgent。
循环与原注册表共 11 个用例可用下列命令验证，不执行 PracticeToolRegistryTest：

```powershell
mvn test "-Dtest=LoopPracticeAgentTest,ToolRegistryTest" -DskipTests=false
```

write_file 的参数说明和执行器已完成：构造器调用 registerWriteFile()，练习注册表保存名称、说明、参数定义和执行器。

用户将 fileName、content 改为 args.get(...)；助手补入 Files.writeString(path, content, StandardCharsets.UTF_8) 和成功结果返回。Files.writeString 是 Java 标准库的方法。path 是检查后得到的绝对路径；固定例子为 demo.txt，内容为“你好”。本轮不扩展父目录创建等功能。

注册表的两个临时目录测试验证真实文件被覆盖为“你好”，以及根目录外写入被拒绝。循环新增 writesFileAndReportsMatchingResultToSecondRound，验证工具说明包含 write_file、真实文件写入、第二轮消息角色、调用 ID 和成功结果。

接入验证时，mvn test -DskipTests=false 共 18 个测试全部通过。模型响应由测试预设，文件读写真实执行。随后已验证一次真实 GLM 写入后读取请求，见 REACT-PRACTICE.md 的“真实模型写入后读取验证”；尚未接入 PaiCLI。
