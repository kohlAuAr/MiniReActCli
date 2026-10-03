package com.paicli.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paicli.llm.GLMClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class ToolRegistry {
    private static final int MAX_READ_CHARS = 20000;
    private static final int MAX_WRITE_CHARS = 100000;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Path projectRoot;
    public ToolRegistry(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        registerReadFile();
        registerListDir();
        registerWriteFile();
    }
    public List<GLMClient.Tool> getToolDefinitions() {
        List<GLMClient.Tool> definitions = new ArrayList<>();
        for(Tool tool : tools.values()) {
            GLMClient.Tool definition = new GLMClient.Tool(
                    tool.name(),
                    tool.description(),
                    tool.parameters()
            );
            definitions.add(definition);
        }
        return definitions;
    }
    public String executeTool(String name,String argumentsJson){
        Tool tool = tools.get(name);
        if(tool == null) {
            return "未知工具：" + name;
        }
        try{
            JsonNode arguments = mapper.readTree(argumentsJson);
            if(!arguments.isObject()) {
                return "工具参数必须是JSON对象";
            }
            Map<String,String> argumentMap = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = arguments.fields();
            while(fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                argumentMap.put(entry.getKey(), entry.getValue().asText());
            }
            return tool.executor().execute(argumentMap);
        }catch(Exception e){
            return "工具执行失败: " + e.getMessage();
        }
    }
    private JsonNode createParameters(Param... params) {
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type","object");
        ObjectNode properties = parameters.putObject("properties");
        ArrayNode required = parameters.putArray("required");
        for (Param param : params) {
            ObjectNode property = properties.putObject(param.name());
            property.put("type",param.type());
            property.put("description",param.description());
            if(param.required()) {
                required.add(param.name());
            }
        }
        return parameters;
    }
    private void registerReadFile(){
        tools.put("read_file",new Tool(
                "read_file",
                "读取项目根目录内的UTF-8文本文件",
                createParameters(
                        new Param(
                                "path",
                                 "string",
                                "相对于项目根目录的文件路径",
                                true
                        )
                ),
                args -> {
                    Path path = resolveSafe(args.get("path"));
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    if(content.length() > MAX_READ_CHARS) {
                        return content.substring(0, MAX_READ_CHARS)
                                + "\n...[内容已截断]";
                    }
                    return content;
                }
        ));
    }
    private void registerListDir() {
        tools.put("list_dir", new Tool(
                "list_dir",
                "列出项目根目录内某个目录的直接子项",
                createParameters(
                        new Param(
                                "path",
                                "string",
                                "相对于项目根目录的目录；根目录使用 .",
                                true
                        )
                ),
                args -> {
                    Path directory = resolveSafe(args.get("path"));

                    if (!Files.isDirectory(directory)) {
                        return "不是目录: " + args.get("path");
                    }

                    List<Path> children = new ArrayList<>();
                    try (DirectoryStream<Path> stream =
                                 Files.newDirectoryStream(directory)) {
                        for (Path child : stream) {
                            children.add(child);
                        }
                    }

                    children.sort(Comparator.naturalOrder());

                    List<String> lines = new ArrayList<>();
                    int count = Math.min(children.size(), 200);

                    for (int i = 0; i < count; i++) {
                        Path child = children.get(i);
                        String prefix = Files.isDirectory(child)
                                ? "[DIR] "
                                : "[FILE] ";
                        lines.add(prefix + directory.relativize(child));
                    }

                    return lines.isEmpty()
                            ? "目录为空"
                            : String.join(System.lineSeparator(), lines);
                }
        ));
    }
    private void registerWriteFile() {
        tools.put("write_file", new Tool(
                "write_file",
                "在项目根目录内写入 UTF-8 文本文件；会覆盖同名文件",
                createParameters(
                        new Param(
                                "path",
                                "string",
                                "相对于项目根目录的文件路径",
                                true
                        ),
                        new Param(
                                "content",
                                "string",
                                "要写入的完整文件内容",
                                true
                        )
                ),
                args -> {
                    String content = args.get("content");

                    if (content == null) {
                        return "缺少参数: content";
                    }

                    if (content.length() > MAX_WRITE_CHARS) {
                        return "拒绝写入：内容超过 "
                                + MAX_WRITE_CHARS + " 个字符";
                    }

                    Path path = resolveSafe(args.get("path"));
                    Path parent = path.getParent();

                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    Files.writeString(
                            path,
                            content,
                            StandardCharsets.UTF_8
                    );

                    return "文件已写入: " + projectRoot.relativize(path);
                }
        ));
    }
    private Path resolveSafe(String input){
        if(input == null || input.isBlank()){
            throw new IllegalArgumentException("路径不能为空");
        }
        Path resolved = projectRoot.resolve(input).normalize();
        if(!resolved.startsWith(projectRoot)){
            throw new IllegalArgumentException(
                    "路径不能超出项目根目录: " + input
            );
        }
        return resolved;
    }
    private record Param(String name, String type, String description,boolean required) {}
    private record Tool(String name,String description,JsonNode parameters,ToolExecutor executor) {}
    @FunctionalInterface
    private interface ToolExecutor{
        String execute(Map<String,String> args) throws Exception;
    }
}
