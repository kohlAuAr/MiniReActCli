package com.paicli.agent;

import com.paicli.llm.GLMClient;
import com.paicli.tool.ToolRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Agent {

    private static final int MAX_ITERATIONS = 8;

    private static final String SYSTEM_PROMPT = """
            你是一个小型 Java 编程助手。
            当回答依赖项目文件时，先调用工具观察真实内容，不要猜测。
            read_file 和 list_dir 是只读工具；write_file 会覆盖文件，只有用户明确要求修改时才能使用。
            工具执行后，根据工具结果继续思考；任务完成后直接给出中文答案。
            """;

    private final GLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final List<GLMClient.Message> conversationHistory =
            new ArrayList<>();

    public Agent(GLMClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;

        conversationHistory.add(
                GLMClient.Message.system(SYSTEM_PROMPT)
        );
    }
    public String run(String userInput){
        conversationHistory.add(GLMClient.Message.user(userInput));
        for(int iteration = 1; iteration <= MAX_ITERATIONS; iteration++){
            try{
                GLMClient.ChatResponse response = llmClient.chat(conversationHistory,toolRegistry.getToolDefinitions());
                if(!response.hasToolCalls()){
                    String answer = response.content() == null
                            ?""
                            : response.content();
                    conversationHistory.add(GLMClient.Message.assistant(answer));
                    return answer;
                }
                conversationHistory.add(GLMClient.Message.assistant(response.content(),response.toolCalls()));
                for(GLMClient.ToolCall toolCall : response.toolCalls()){
                    String toolName = toolCall.function().name();
                    System.out.println("🔧 执行工具: " + toolName);
                    String result = toolRegistry.executeTool(
                            toolName,
                            toolCall.function().arguments()
                    );
                    conversationHistory.add(GLMClient.Message.tool(toolCall.id(),  result));
                }
            }catch(Exception e){
                return "模型调用失败: " + e.getMessage();
            }
        }
        return "达到最大迭代次数限制，任务仍未完成";
    }
    public void clearHistory() {
        conversationHistory.clear();
        conversationHistory.add(
                GLMClient.Message.system(SYSTEM_PROMPT));
    };
}
