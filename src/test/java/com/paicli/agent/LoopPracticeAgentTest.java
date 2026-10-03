package com.paicli.agent;

import com.paicli.llm.GLMClient;
import com.paicli.tool.PracticeToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopPracticeAgentTest {

    @TempDir
    Path projectRoot;

    @Test
    void returnsAnswerWithoutCallingTools() throws Exception {
        ScriptedClient model = new ScriptedClient(List.of(answer("你好"), answer("再见")));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("你好", agent.run("打个招呼"));
        assertEquals(1, model.requests.size());
        assertEquals(List.of("system", "user"), roles(model.requests.get(0)));
        assertEquals("打个招呼", model.requests.get(0).get(1).content());
        assertTrue(model.definitions.stream().anyMatch(tool -> tool.name().equals("read_file")));

        assertEquals("再见", agent.run("再见"));
        assertEquals(2, model.requests.size());
        assertEquals(List.of("system", "user", "assistant", "user"), roles(model.requests.get(1)));
        assertEquals("你好", model.requests.get(1).get(2).content());
    }

    @Test
    void sendsRealFileContentsAndMatchingCallIdToSecondRound() throws Exception {
        String fileContent = "TODO: 增加登录校验\nTODO: 添加上传失败提示";
        Files.writeString(projectRoot.resolve("demo.txt"), fileContent, StandardCharsets.UTF_8);
        GLMClient.ToolCall call = readFile("call_1", "demo.txt");
        ScriptedClient model = new ScriptedClient(List.of(
                new GLMClient.ChatResponse(null, List.of(call)), answer("有两项待办")
        ));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("有两项待办", agent.run("读取 demo.txt，列出待办事项"));
        assertEquals(2, model.requests.size());
        List<GLMClient.Message> secondRequest = model.requests.get(1);
        assertEquals(List.of("system", "user", "assistant", "tool"), roles(secondRequest));
        assertEquals(List.of(call), secondRequest.get(2).toolCalls());
        assertEquals("call_1", secondRequest.get(3).toolCallId());
        assertEquals(fileContent, secondRequest.get(3).content());
    }

    @Test
    void sendsEveryToolResultWithItsOwnCallId() throws Exception {
        Files.writeString(projectRoot.resolve("a.txt"), "A", StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("b.txt"), "B", StandardCharsets.UTF_8);
        ScriptedClient model = new ScriptedClient(List.of(
                new GLMClient.ChatResponse(null, List.of(readFile("call_a", "a.txt"), readFile("call_b", "b.txt"))),
                answer("读取完成")
        ));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("读取完成", agent.run("读取 a.txt 和 b.txt"));
        List<GLMClient.Message> messages = model.requests.get(1);
        assertEquals(List.of("system", "user", "assistant", "tool", "tool"), roles(messages));
        assertEquals("call_a", messages.get(3).toolCallId());
        assertEquals("A", messages.get(3).content());
        assertEquals("call_b", messages.get(4).toolCallId());
        assertEquals("B", messages.get(4).content());
    }

    @Test
    void sendsToolFailureBackToModel() throws Exception {
        ScriptedClient model = new ScriptedClient(List.of(
                new GLMClient.ChatResponse(null, List.of(readFile("call_missing", "missing.txt"))),
                answer("文件不存在，无法列出待办")
        ));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("文件不存在，无法列出待办", agent.run("读取 missing.txt"));
        GLMClient.Message result = model.requests.get(1).get(3);
        assertEquals("tool", result.role());
        assertEquals("call_missing", result.toolCallId());
        assertTrue(result.content().startsWith("工具执行失败:"));
    }

    @Test
    void stopsAfterEightModelCalls() throws Exception {
        GLMClient.ChatResponse repeat = new GLMClient.ChatResponse(null, List.of(readFile("call_repeat", "missing.txt")));
        ScriptedClient model = new ScriptedClient(Collections.nCopies(8, repeat));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("达到最大迭代次数限制，任务仍未完成", agent.run("持续调用工具"));
        assertEquals(8, model.requests.size());
    }

    @Test
    void rejectsMoreThanTwoToolCallsBeforeExecutingAnyTool() throws Exception {
        ScriptedClient model = new ScriptedClient(List.of(
                new GLMClient.ChatResponse(null, List.of(
                        writeFile("call_a", "a.txt"),
                        writeFile("call_b", "b.txt"),
                        writeFile("call_c", "c.txt")
                )),
                answer("已经写完")
        ));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("单轮工具调用超过2个，已停止执行", agent.run("执行本轮任务"));
        assertEquals(1, model.requests.size());
        assertFalse(Files.exists(projectRoot.resolve("a.txt")));
        assertFalse(Files.exists(projectRoot.resolve("b.txt")));
        assertFalse(Files.exists(projectRoot.resolve("c.txt")));
    }

    @Test
    void writesFileAndReportsMatchingResultToSecondRound() throws Exception {
        GLMClient.ToolCall call = new GLMClient.ToolCall("call_write_1", new GLMClient.FunctionCall(
                "write_file", "{\"path\":\"demo.txt\",\"content\":\"你好\"}"
        ));
        ScriptedClient model = new ScriptedClient(List.of(
                new GLMClient.ChatResponse(null, List.of(call)), answer("已将你好写入 demo.txt")
        ));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("已将你好写入 demo.txt", agent.run("把你好写进 demo.txt"));
        assertEquals("你好", Files.readString(projectRoot.resolve("demo.txt"), StandardCharsets.UTF_8));
        assertTrue(model.definitions.stream().anyMatch(tool -> tool.name().equals("write_file")));
        assertEquals(2, model.requests.size());
        List<GLMClient.Message> secondRequest = model.requests.get(1);
        assertEquals(List.of("system", "user", "assistant", "tool"), roles(secondRequest));
        assertEquals(List.of(call), secondRequest.get(2).toolCalls());
        assertEquals("call_write_1", secondRequest.get(3).toolCallId());
        assertEquals("文件已写入: demo.txt", secondRequest.get(3).content());
    }

    @Test
    void countsFailedToolsAndRejectsBatchThatExceedsTotalBudget() throws Exception {
        List<GLMClient.ChatResponse> replies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            replies.add(new GLMClient.ChatResponse(null, List.of(
                    writeFile("write_" + i, "demo" + i + ".txt"),
                    readFile("missing_" + i, "missing.txt")
            )));
        }
        replies.add(new GLMClient.ChatResponse(null, List.of(
                writeFile("blocked_a", "blocked-a.txt"), writeFile("blocked_b", "blocked-b.txt")
        )));
        replies.add(answer("不应执行本轮工具"));
        ScriptedClient model = new ScriptedClient(replies);
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("达到工具调用次数限制，已停止执行", agent.run("执行这些工具"));
        assertEquals(6, model.requests.size());
        assertTrue(model.requests.get(1).get(4).content().startsWith("工具执行失败: "));
        for (int i = 0; i < 5; i++) {
            assertEquals("demo", Files.readString(projectRoot.resolve("demo" + i + ".txt"), StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(projectRoot.resolve("blocked-a.txt")));
        assertFalse(Files.exists(projectRoot.resolve("blocked-b.txt")));
    }

    @Test
    void allowsExactlyTenToolsAndResetsBudgetForNextRun() throws Exception {
        List<GLMClient.ChatResponse> replies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            replies.add(new GLMClient.ChatResponse(null, List.of(
                    writeFile("a_" + i, "a" + i + ".txt"), writeFile("b_" + i, "b" + i + ".txt")
            )));
        }
        replies.add(answer("十个工具已完成"));
        replies.add(new GLMClient.ChatResponse(null, List.of(writeFile("next_run", "next.txt"))));
        replies.add(answer("新任务已完成"));
        ScriptedClient model = new ScriptedClient(replies);
        LoopPracticeAgent agent = new LoopPracticeAgent(model, new PracticeToolRegistry(projectRoot));

        assertEquals("十个工具已完成", agent.run("执行十个工具"));
        assertEquals(6, model.requests.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("demo", Files.readString(projectRoot.resolve("a" + i + ".txt"), StandardCharsets.UTF_8));
            assertEquals("demo", Files.readString(projectRoot.resolve("b" + i + ".txt"), StandardCharsets.UTF_8));
        }
        assertEquals("新任务已完成", agent.run("执行新的任务"));
        assertEquals(8, model.requests.size());
        assertEquals("demo", Files.readString(projectRoot.resolve("next.txt"), StandardCharsets.UTF_8));
    }

    private static GLMClient.ToolCall writeFile(String id, String path) {
        return new GLMClient.ToolCall(id, new GLMClient.FunctionCall(
                "write_file", "{\"path\":\"" + path + "\",\"content\":\"demo\"}"
        ));
    }

    private static GLMClient.ToolCall readFile(String id, String path) {
        return new GLMClient.ToolCall(id, new GLMClient.FunctionCall("read_file", "{\"path\":\"" + path + "\"}"));
    }

    private static GLMClient.ChatResponse answer(String content) {
        return new GLMClient.ChatResponse(content, List.of());
    }

    private static List<String> roles(List<GLMClient.Message> messages) {
        return messages.stream().map(GLMClient.Message::role).toList();
    }

    private static class ScriptedClient extends GLMClient {
        private final List<ChatResponse> replies;
        private final List<List<Message>> requests = new ArrayList<>();
        private List<Tool> definitions;

        private ScriptedClient(List<ChatResponse> replies) {
            super("offline-test-placeholder");
            this.replies = replies;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            requests.add(List.copyOf(messages));
            definitions = List.copyOf(tools);
            return replies.get(requests.size() - 1);
        }
    }
}
