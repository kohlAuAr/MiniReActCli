package com.paicli.llm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GLMClient {
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String MODEL = "glm-5.1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    public GLMClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }
    public ChatResponse chat(List<Message> messages,List<Tool> tools) throws IOException{
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", MODEL);
        requestBody.put("stream",false);
        requestBody.put("tool_choice","auto");
        appendMessages(requestBody,messages);
        appendTools(requestBody,tools);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization","Bearer " + apiKey)
                .post(RequestBody.create(requestBody.toString(), JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseText = body == null ? "" : body.string();
            if(!response.isSuccessful()){
                throw new IOException("GLM API 调用失败，HTTP"
                        + response.code() + ": " + responseText);
            }
            return parseChatResponse(responseText);
        }
    }
    private void appendMessages(ObjectNode requestBody,List<Message> messages){
        ArrayNode messageArray = requestBody.putArray("messages");
        for(Message message: messages){
            ObjectNode messageNode = messageArray.addObject();
            messageNode.put("role",message.role());
            if(message.content() == null){
                messageNode.putNull("content");
            }else {
                messageNode.put("content",message.content());
            }
            if(message.toolCalls() != null && !message.toolCalls().isEmpty()){
                ArrayNode toolCallArray = messageNode.putArray("tool_calls");
                for(ToolCall toolCall: message.toolCalls()) {
                    ObjectNode callNode = toolCallArray.addObject();
                    callNode.put("id", toolCall.id());
                    callNode.put("type", "function");
                    ObjectNode functionNode = callNode.putObject("function");
                    functionNode.put("name", toolCall.function().name());
                    functionNode.put("arguments", toolCall.function().arguments());
                }
            }
            if(message.toolCallId() != null){
                messageNode.put("tool_call_id", message.toolCallId());
            }
        }
    }
    private void appendTools(ObjectNode requestBody,List<Tool> tools){
        if(tools == null || tools.isEmpty()){
            return;
        }
        ArrayNode toolArray = requestBody.putArray("tools");
        for(Tool tool: tools){
            ObjectNode toolNode = toolArray.addObject();
            toolNode.put("type", "function");
            ObjectNode functionNode = toolNode.putObject("function");
            functionNode.put("name", tool.name());
            functionNode.put("description",tool.description());
            functionNode.set("parameters",tool.parameters());
        }
    }
    private ChatResponse parseChatResponse(String responseText) throws IOException {
        JsonNode root = mapper.readTree(responseText);
        JsonNode choices = root.path("choices");
        if(!choices.isArray() || choices.isEmpty()){
            throw new IOException("GLM API 响应缺少 choices: " + responseText);
        }
        JsonNode messageNode = choices.get(0).path("message");
        String content = messageNode.path("content").isNull() ? null : messageNode.path("content").asText(null);
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallArray = messageNode.path("tool_calls");
        if(toolCallArray.isArray()){
            for(JsonNode callNode: toolCallArray){
                JsonNode functionNode = callNode.path("function");
                ToolCall toolcall = new ToolCall(
                        callNode.path("id").asText(),
                        new FunctionCall(
                                functionNode.path("name").asText(),
                                functionNode.path("arguments").asText("{}")
                        )
                );
                toolCalls.add(toolcall);
            }
        }
        return new ChatResponse(content, toolCalls);
    }
    public record Message(String role,String content,List<ToolCall> toolCalls,String toolCallId){
        public static Message system(String content){
            return new Message("system",content,null,null);
        }

        public static Message user(String content){
            return new Message("user",content,null,null);
        }

        public static Message assistant(String content){
            return new Message("assistant",content,null,null);
        }

        public static Message assistant(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, toolCalls, null);
        }

        public static Message tool(String toolCallId, String content) {
            return new Message("tool", content, null, toolCallId);
        }
    }

    public record FunctionCall(String name,String arguments){}

    public record ToolCall(String id,FunctionCall function){}

    public record Tool(String name,String description,JsonNode parameters){}

    public record ChatResponse(String content,List<ToolCall> toolCalls){
        public boolean hasToolCalls(){
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
