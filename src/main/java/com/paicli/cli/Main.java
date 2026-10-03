package com.paicli.cli;
import com.paicli.agent.Agent;
import com.paicli.llm.GLMClient;
import com.paicli.tool.ToolRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        String apiKey = loadApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println(
                    "未找到 GLM_API_KEY，请设置环境变量或在项目根目录创建 .env"
            );
            return;
        }

        Path projectRoot = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize();

        GLMClient llmClient = new GLMClient(apiKey);
        ToolRegistry toolRegistry = new ToolRegistry(projectRoot);
        Agent agent = new Agent(llmClient, toolRegistry);

        printBanner(projectRoot);

        try (Scanner scanner = new Scanner(
                System.in,
                StandardCharsets.UTF_8
        )) {
            while (true) {
                System.out.print("你> ");

                if (!scanner.hasNextLine()) {
                    break;
                }

                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                if (input.equalsIgnoreCase("exit")
                        || input.equalsIgnoreCase("quit")) {
                    break;
                }

                if (input.equalsIgnoreCase("clear")) {
                    agent.clearHistory();
                    System.out.println("对话历史已清空");
                    continue;
                }

                String response = agent.run(input);
                System.out.println("Agent> " + response);
            }
        }
    }
    static String loadApiKey(){
        String environmentValue = System.getenv("GLM_API_KEY");
        if(environmentValue != null && !environmentValue.isBlank()){
            return environmentValue.trim();
        }
        Path envFile = Path.of(".env");
        if(!Files.isRegularFile(envFile)){
            return null;
        }
        try{
            List<String> lines = Files.readAllLines(
                    envFile,
                    StandardCharsets.UTF_8
            );
            for(String line : lines){
                String trimmed = line.trim();
                if(trimmed.startsWith("GLM_API_KEY=")){
                    return trimmed
                            .substring("GLM_API_KEY=".length())
                            .trim();
                }
            }
        }catch(IOException e){
            System.err.println("读取 .env 失败: " + e.getMessage());
        }
        return null;
    }
    private static void printBanner(Path projectRoot) {
        System.out.println("====================================");
        System.out.println("  Mini ReAct CLI - Java 17");
        System.out.println("  工作目录: " + projectRoot);
        System.out.println("  clear 清空历史，exit 退出");
        System.out.println("====================================");
    }
}
