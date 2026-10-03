package com.paicli.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paicli.llm.GLMClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PracticeToolRegistry {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Path projectRoot;

    public PracticeToolRegistry(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        registerReadFile();
        registerWriteFile();
    }

    public List<GLMClient.Tool> getToolDefinitions() {
        List<GLMClient.Tool> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(new GLMClient.Tool(tool.name(), tool.description(), tool.parameters()));
        }
        return definitions;
    }

    public String executeTool(String name, String argumentsJson) {
        Tool tool = tools.get(name);
        if(tool == null) {
            return "未知工具：" + name;
        }

        try {
            JsonNode arguments = mapper.readTree(argumentsJson);
            if(!arguments.isObject()) {
                return "工具参数必须是JSON对象";
            }
            Map<String, String> argumentMap = toArgumentMap(arguments);
            String result = tool.executor().execute(argumentMap);
            return result;
        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
    }

    private Map<String, String> toArgumentMap(JsonNode arguments) {
        Map<String, String> argumentMap = new LinkedHashMap<>();
        arguments.fields().forEachRemaining(entry ->
                argumentMap.put(entry.getKey(), entry.getValue().asText())
        );
        return argumentMap;
    }

    private void registerReadFile() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");
        parameters.putObject("properties").putObject("path")
                .put("type", "string").put("description", "相对于项目根目录的文件路径");
        parameters.putArray("required").add("path");

        tools.put("read_file", new Tool(
                "read_file", "读取项目根目录内的 UTF-8 文件", parameters,
                args -> {
                    String fileName = args.get("path");
                    if (fileName == null || fileName.isBlank()) {
                        throw new IllegalArgumentException("路径不能为空");
                    }
                    Path path = projectRoot.resolve(fileName).normalize();
                    if (!path.startsWith(projectRoot)) {
                        throw new IllegalArgumentException("路径不能超出项目根目录: " + fileName);
                    }
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
        ));
    }
    private void registerWriteFile() {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");

        ObjectNode properties = parameters.putObject("properties");

        properties.putObject("path")
                .put("type", "string")
                .put("description", "相对于项目根目录的文件路径");

         
        properties.putObject("content")
                .put("type", "string")
                .put("description", "要写入的文件内容");

        parameters.putArray("required").add("path").add("content");

        tools.put("write_file", new Tool(
                "write_file", "在项目根目录内写入 UTF-8 文本文件；会覆盖同名文件", parameters,
                args -> {
                    String fileName = args.get("path");
                    String content = args.get("content");
                    if (fileName == null || fileName.isBlank()) {
                        throw new IllegalArgumentException("路径不能为空");
                    }
                    if (content == null) {
                        throw new IllegalArgumentException("缺少参数: content");
                    }
                    Path path = projectRoot.resolve(fileName).normalize();
                    if (!path.startsWith(projectRoot)) {
                        throw new IllegalArgumentException("路径不能超出项目根目录: " + fileName);
                    }
                    Files.writeString(path, content, StandardCharsets.UTF_8);
                    return "文件已写入: " + fileName;
                }
        ));
    }

    private record Tool(String name, String description, JsonNode parameters, ToolExecutor executor) {}

    @FunctionalInterface
    private interface ToolExecutor {
        String execute(Map<String, String> args) throws Exception;
    }
}
