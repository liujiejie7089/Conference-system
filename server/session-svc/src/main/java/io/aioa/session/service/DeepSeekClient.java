package io.aioa.session.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * DeepSeek Harness Sidecar 客户端
 * 调用 Python Sidecar（端口 8091）进行 LLM 推理
 */
@Slf4j
@Component
public class DeepSeekClient {

    private static final String SIDECAR_URL = "http://localhost:8091";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /**
     * SSE 流式聊天
     * @param messages  消息列表 [{role, content}]
     * @param tools     Tool 列表（OpenAI Function Calling 格式）
     * @param onEvent  SSE 事件回调
     */
    public int streamChat(List<Map<String, Object>> messages,
                          List<Map<String, Object>> tools,
                          Consumer<JsonNode> onEvent) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("messages", messages);
            if (tools != null && !tools.isEmpty()) {
                body.put("tools", tools);
            }
            body.put("stream", true);

            String jsonBody = mapper.writeValueAsString(body);
            log.info("[sidecar] 请求体长度: {} bytes", jsonBody.getBytes(StandardCharsets.UTF_8).length);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SIDECAR_URL + "/api/v1/chat/stream"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBody.getBytes(StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            // 用 InputStream 逐行读取 SSE
            HttpResponse<java.io.InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());

            // 检查状态码：422 等错误响应不是 SSE 格式
            if (resp.statusCode() != 200) {
                String errBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("Sidecar 返回错误 {}: {}", resp.statusCode(), errBody);
                return resp.statusCode();
            }

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String json = line.substring(6).trim();
                        if (json.isEmpty() || json.equals("[DONE]")) continue;
                        try {
                            JsonNode event = mapper.readTree(json);
                            onEvent.accept(event);
                        } catch (Exception e) {
                            log.warn("解析 SSE 事件失败: {}", json, e);
                        }
                    }
                }
            }
            return 200;
        } catch (Exception e) {
            log.error("调用 DeepSeek Sidecar 失败: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 非流式聊天（用于 Tool 结果回传后的第二轮对话）
     */
    public Map<String, Object> chat(List<Map<String, Object>> messages,
                                     List<Map<String, Object>> tools) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("messages", messages);
            if (tools != null && !tools.isEmpty()) {
                body.put("tools", tools);
            }

            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SIDECAR_URL + "/api/v1/chat"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBody.getBytes(StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = mapper.readTree(resp.body());
            return mapper.treeToValue(node.path("data"), Map.class);
        } catch (Exception e) {
            log.error("调用 DeepSeek Sidecar（非流式）失败: {}", e.getMessage(), e);
            return Map.of("content", "抱歉，AI 服务暂时不可用。", "tokens_used", 0);
        }
    }
}
