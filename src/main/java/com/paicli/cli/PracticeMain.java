package com.paicli.cli;

import com.paicli.agent.LoopPracticeAgent;
import com.paicli.llm.GLMClient;
import com.paicli.tool.PracticeToolRegistry;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class PracticeMain {

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String apiKey = Main.loadApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请在项目根目录 .env 或环境变量中设置 GLM_API_KEY");
        }

        GLMClient model = new TracingGLMClient(apiKey);
        PracticeToolRegistry registry = new PracticeToolRegistry(Path.of(System.getProperty("user.dir")));
        LoopPracticeAgent agent = new LoopPracticeAgent(model, registry);
        String question = args.length == 0
                ? "请使用 read_file 读取 notes/react-demo.txt，列出所有 TODO。只读取和回答，不修改任何文件。"
                : String.join(" ", args);
        System.out.println("用户请求：" + question);
        String answer = agent.run(question);
        System.out.println("\n最终返回：" + answer);
    }

    private static class TracingGLMClient extends GLMClient {
        private int round;

        private TracingGLMClient(String apiKey) {
            super(apiKey);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            System.out.printf("\n模型请求 #%d，消息数=%d%n", ++round, messages.size());
            for (int i = 0; i < messages.size(); i++) {
                Message message = messages.get(i);
                System.out.printf("  [%d] role=%s, toolCallId=%s, content=%s%n",
                        i, message.role(), message.toolCallId(), message.content());
                if (message.toolCalls() != null) {
                    for (ToolCall call : message.toolCalls()) {
                        System.out.printf("      请求工具：id=%s, name=%s, arguments=%s%n",
                                call.id(), call.function().name(), call.function().arguments());
                    }
                }
            }
            ChatResponse response = super.chat(messages, tools);
            System.out.printf("模型响应 #%d：hasToolCalls=%s, content=%s%n",
                    round, response.hasToolCalls(), response.content());
            if (response.toolCalls() != null) {
                for (ToolCall call : response.toolCalls()) {
                    System.out.printf("  要执行：id=%s, name=%s, arguments=%s%n",
                            call.id(), call.function().name(), call.function().arguments());
                }
            }
            return response;
        }
    }
}
