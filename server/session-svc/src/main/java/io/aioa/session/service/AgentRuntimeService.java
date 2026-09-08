package io.aioa.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aioa.common.context.UserContext;
import io.aioa.common.id.Snowflake;
import io.aioa.session.repo.entity.MessageEntity;
import io.aioa.session.repo.entity.SessionEntity;
import io.aioa.session.repo.mapper.MessageMapper;
import io.aioa.session.repo.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Agent Runtime — 智能体运行时
 * 职责：上下文维护 + 记忆管理 + Function Calling 调用链
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {

    private final DeepSeekClient deepSeekClient;
    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String GATEWAY_URL = "http://localhost:8090";

    /**
     * 流式聊天主流程
     * 1. 保存用户消息
     * 2. 构建上下文（历史消息 + 系统提示）
     * 3. 获取 Tool 列表
     * 4. 调用 DeepSeek Sidecar 流式输出
     * 5. 如有 tool_calls → 执行 Tool → 结果回传 → 继续对话
     * 6. 保存 AI 回复
     */
    public void streamChat(Long sessionId, String userContent,
                           UserContext.CurrentUser user, SseEmitter emitter) {
        try {

            // 1. 保存用户消息
            saveMessage(sessionId, user.tenantId(), 1, userContent, 0);

            // 2. 构建上下文
            List<Map<String, Object>> messages = buildContext(sessionId, user);

            // 3. 获取 Tool 列表（从 agent-svc 连接器引擎）
            List<Map<String, Object>> tools = fetchTools(user);
            log.info("[runtime] session={} tools={}", sessionId, tools.size());

            // 4. 调用 DeepSeek 流式输出
            StringBuilder aiContent = new StringBuilder();
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            int[] tokensUsed = {0};

            int sidecarStatus = deepSeekClient.streamChat(messages, tools, event -> {
                String type = event.path("type").asText("");
                try {
                    switch (type) {
                        case "text":
                            String chunk = event.path("content").asText("");
                            aiContent.append(chunk);
                            emitter.send(SseEmitter.event().data(Map.of(
                                    "type", "text", "content", chunk)));
                            break;
                        case "tool_call":
                            JsonNode tcNode = event.path("tool_calls");
                            if (tcNode.isArray()) {
                                for (JsonNode tc : tcNode) {
                                    Map<String, Object> tcMap = mapper.convertValue(tc, Map.class);
                                    toolCalls.add(tcMap);
                                }
                            }
                            emitter.send(SseEmitter.event().data(Map.of(
                                    "type", "tool_call",
                                    "tool_name", toolCalls.isEmpty() ? "" :
                                            ((Map<String, Object>) toolCalls.get(0).get("function")).get("name"))));
                            break;
                        case "done":
                            tokensUsed[0] = event.path("tokens_used").asInt(0);
                            break;
                    }
                } catch (Exception e) {
                    log.warn("SSE 发送失败: {}", e.getMessage());
                }
            });

            // 检查 Sidecar 调用是否成功
            if (sidecarStatus != 200) {
                log.error("[runtime] Sidecar 返回非 200 状态: {}", sidecarStatus);
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "error",
                        "message", "AI 服务调用失败 (status=" + sidecarStatus + ")")));
                emitter.complete();
                return;
            }

            // 5. 处理 Function Calling 调用链
            if (!toolCalls.isEmpty()) {
                handleToolCalls(sessionId, user, messages, tools, toolCalls,
                        aiContent, tokensUsed, emitter);
            }

            // 6. 保存 AI 回复
            Long aiMsgId = saveMessage(sessionId, user.tenantId(), 2, aiContent.toString(), tokensUsed[0]);

            // 7. 记录 Token 用量到 ledger-svc
            recordTokenUsage(sessionId, aiMsgId, user, tokensUsed[0]);

            // 更新 session 最后消息时间
            SessionEntity session = sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setLastMessageAt(java.time.LocalDateTime.now());
                sessionMapper.updateById(session);
            }

            // 发送完成事件
            emitter.send(SseEmitter.event().data(Map.of(
                    "type", "done",
                    "tokens_used", tokensUsed[0],
                    "content", aiContent.toString())));
            emitter.complete();

        } catch (Exception e) {
            log.error("[runtime] 流式聊天失败: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "error", "message", e.getMessage())));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 处理 Function Calling 调用链
     * Tool 结果回传 DeepSeek → 继续流式输出
     */
    private void handleToolCalls(Long sessionId, UserContext.CurrentUser user,
                                  List<Map<String, Object>> messages,
                                  List<Map<String, Object>> tools,
                                  List<Map<String, Object>> toolCalls,
                                  StringBuilder aiContent, int[] tokensUsed,
                                  SseEmitter emitter) {
        try {
            // 添加 assistant 消息（含 tool_calls）
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", aiContent.toString());
            assistantMsg.put("tool_calls", toolCalls);
            messages.add(assistantMsg);

            // 执行每个 Tool Call
            for (Map<String, Object> tc : toolCalls) {
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                String toolName = (String) func.get("name");
                String argsJson = (String) func.get("arguments");
                String toolCallId = (String) tc.get("id");

                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "tool_executing", "tool_name", toolName)));

                // 调用连接器引擎执行 Tool
                String toolResult = executeToolViaConnector(toolName, argsJson, user);

                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "tool_result", "tool_name", toolName,
                        "result", toolResult)));

                // 将 Tool 结果加入上下文
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("content", toolResult);
                toolMsg.put("tool_call_id", toolCallId);
                messages.add(toolMsg);
            }

            // 第二轮调用 DeepSeek（Tool 结果回传后继续对话）
            StringBuilder secondContent = new StringBuilder();
            int secondStatus = deepSeekClient.streamChat(messages, tools, event -> {
                String type = event.path("type").asText("");
                try {
                    if ("text".equals(type)) {
                        String chunk = event.path("content").asText("");
                        secondContent.append(chunk);
                        emitter.send(SseEmitter.event().data(Map.of(
                                "type", "text", "content", chunk)));
                    } else if ("done".equals(type)) {
                        tokensUsed[0] += event.path("tokens_used").asInt(0);
                    }
                } catch (Exception e) {
                    log.warn("SSE 发送失败: {}", e.getMessage());
                }
            });

            if (secondStatus != 200) {
                log.error("[runtime] 第二轮 Sidecar 调用失败: {}", secondStatus);
            }

            aiContent.append(secondContent);

        } catch (Exception e) {
            log.error("[runtime] Function Calling 调用链失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建上下文：系统提示 + 历史消息
     */
    private List<Map<String, Object>> buildContext(Long sessionId, UserContext.CurrentUser user) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // 系统提示
        messages.add(Map.of(
                "role", "system",
                "content", "你是 AIOA 智能办公助手。你可以回答问题，也可以通过工具调用业务系统（如工单系统）来帮用户查询和操作。"));

        // 历史消息（最近 20 条）
        var query = new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getSessionId, sessionId)
                .orderByDesc(MessageEntity::getCreatedAt)
                .last("LIMIT 20");
        List<MessageEntity> history = messageMapper.selectList(query);
        Collections.reverse(history);

        for (MessageEntity m : history) {
            String role = m.getRole() == 1 ? "user" : "assistant";
            messages.add(Map.of("role", role, "content", m.getContent() != null ? m.getContent() : ""));
        }

        return messages;
    }

    /**
     * 获取 Tool 列表（从 agent-svc 连接器引擎）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTools(UserContext.CurrentUser user) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GATEWAY_URL + "/api/v1/connectors/tools"))
                    .header("Authorization", "Bearer " + user.token())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = mapper.readTree(resp.body());
            JsonNode data = node.path("data");
            if (data.isArray()) {
                return mapper.convertValue(data, List.class);
            }
        } catch (Exception e) {
            log.warn("获取 Tool 列表失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 通过连接器引擎执行 Tool
     */
    @SuppressWarnings("unchecked")
    private String executeToolViaConnector(String toolName, String argsJson,
                                            UserContext.CurrentUser user) {
        try {
            // 先查找 Tool ID（通过 slug）
            // 简化：直接 POST 到连接器执行接口，用 toolName 查找
            Map<String, Object> body = new HashMap<>();
            body.put("arguments", mapper.readValue(argsJson, Map.class));

            // 通过 gateway 调用连接器（用 toolName 作为路径参数需要 ID，
            // 简化方案：直接调用 agent-svc 内部接口）
            // 这里用 Tool slug 查找 ID 的接口暂时不存在，
            // 用简化方案：直接 HTTP 调用 demo-ticket
            return executeToolDirectly(toolName, argsJson);
        } catch (Exception e) {
            log.error("执行 Tool 失败: {}", e.getMessage(), e);
            return "Tool 执行失败: " + e.getMessage();
        }
    }

    /**
     * 直接执行 Tool（简化方案：直接调用业务系统）
     * M2 阶段简化实现，后续通过连接器引擎统一调用
     */
    @SuppressWarnings("unchecked")
    private String executeToolDirectly(String toolName, String argsJson) {
        try {
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);
            String url;
            String method;

            // 根据 toolName 构造请求
            if (toolName.contains("get") && toolName.contains("ticket_id")) {
                // 查询工单详情
                Object ticketId = args.get("ticket_id");
                if (ticketId == null) ticketId = args.get("ticketId");
                url = "http://localhost:8092/api/tickets/" + ticketId;
                method = "GET";
            } else if (toolName.contains("get")) {
                // 查询工单列表
                url = "http://localhost:8092/api/tickets";
                if (args.containsKey("status")) {
                    url += "?status=" + args.get("status");
                }
                if (args.containsKey("keyword")) {
                    url += (url.contains("?") ? "&" : "?") + "keyword=" + args.get("keyword");
                }
                method = "GET";
            } else if (toolName.contains("post")) {
                // 创建工单
                url = "http://localhost:8092/api/tickets";
                method = "POST";
            } else if (toolName.contains("put")) {
                // 更新工单
                Object ticketId = args.get("ticket_id");
                if (ticketId == null) ticketId = args.get("ticketId");
                url = "http://localhost:8092/api/tickets/" + ticketId;
                method = "PUT";
            } else if (toolName.contains("delete")) {
                // 删除工单
                Object ticketId = args.get("ticket_id");
                if (ticketId == null) ticketId = args.get("ticketId");
                url = "http://localhost:8092/api/tickets/" + ticketId;
                method = "DELETE";
            } else {
                return "未知的 Tool: " + toolName;
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer demo-token-123")
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15));

            if ("GET".equals(method)) {
                reqBuilder.GET();
            } else if ("POST".equals(method)) {
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString(argsJson));
            } else if ("PUT".equals(method)) {
                reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(argsJson));
            } else if ("DELETE".equals(method)) {
                reqBuilder.DELETE();
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.body();

        } catch (Exception e) {
            log.error("直接执行 Tool 失败: {}", e.getMessage(), e);
            return "Tool 执行失败: " + e.getMessage();
        }
    }

    /**
     * 保存消息到数据库
     * @return 消息 ID（数据库自增 id）
     */
    private Long saveMessage(Long sessionId, Long tenantId, int role,
                             String content, int tokens) {
        MessageEntity msg = new MessageEntity();
        msg.setIdStr(Snowflake.nextIdStr());
        msg.setSessionId(sessionId);
        msg.setTenantId(tenantId);
        msg.setRole(role);  // 1=user 2=assistant
        msg.setContent(content);
        msg.setContentType(1);  // 1=text
        msg.setTokenInput(0L);
        msg.setTokenOutput((long) tokens);
        msg.setStatus(1);
        messageMapper.insert(msg);
        return msg.getId();
    }

    /**
     * 记录 Token 用量到 ledger-svc（通过 gateway）
     */
    private void recordTokenUsage(Long sessionId, Long messageId,
                                   UserContext.CurrentUser user, long tokensUsed) {
        if (tokensUsed <= 0) return;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("sessionId", sessionId);
            body.put("messageId", messageId);
            body.put("model", "deepseek-chat");
            body.put("inputTokens", 0);
            body.put("outputTokens", tokensUsed);

            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GATEWAY_URL + "/api/v1/ledger/usage"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + user.token())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBody.getBytes(StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("[runtime] token 用量记录: sessionId={} tokens={} resp={}",
                    sessionId, tokensUsed, resp.body());
        } catch (Exception e) {
            log.warn("[runtime] 记录 token 用量失败: {}", e.getMessage());
        }
    }
}
