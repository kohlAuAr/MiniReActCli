package com.paicli.agent;

import com.paicli.llm.GLMClient;
import com.paicli.tool.PracticeToolRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoopPracticeAgent {

    private static final int MAX_ITERATIONS = 8;
    private static final int MAX_TOOL_EXECUTIONS = 10;

    private final GLMClient llmClient;
    private final PracticeToolRegistry toolRegistry;
    private final List<GLMClient.Message> conversationHistory = new ArrayList<>();

    public LoopPracticeAgent(GLMClient llmClient, PracticeToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        conversationHistory.add(GLMClient.Message.system(
                "回答依赖文件时先调用工具；根据工具结果回答，不要猜测文件内容。"
        ));
    }

    public String run(String userInput) throws IOException {

        conversationHistory.add(GLMClient.Message.user(userInput));
        int interations = 0;
        int executedToolCount = 0;
        while (interations < MAX_ITERATIONS) {
            interations++;
            GLMClient.ChatResponse response = llmClient.chat(
                    conversationHistory, toolRegistry.getToolDefinitions()
            );
            if (!response.hasToolCalls()) {
                String answer = response.content() == null ? "" : response.content();
                conversationHistory.add(GLMClient.Message.assistant(answer));
                return answer;
            }
            if(response.toolCalls().size() > 2) {
                return "单轮工具调用超过2个，已停止执行";
            }
            if (executedToolCount + response.toolCalls().size() > MAX_TOOL_EXECUTIONS) {
                return "达到工具调用次数限制，已停止执行";
            }
            conversationHistory.add(GLMClient.Message.assistant(
                    response.content(), response.toolCalls()
            ));
            for (GLMClient.ToolCall toolCall : response.toolCalls()) {
                String toolName = toolCall.function().name();
                String result = toolRegistry.executeTool(
                        toolName, toolCall.function().arguments()
                );
                executedToolCount++;
                conversationHistory.add(GLMClient.Message.tool(toolCall.id(), result));
            }
        }
        return "达到最大迭代次数限制，任务仍未完成";
    }
}
