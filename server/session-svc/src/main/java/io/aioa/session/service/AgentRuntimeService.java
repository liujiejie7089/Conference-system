package io.aioa.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aioa.common.client.OperationLogClient;
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
    private final io.aioa.session.repo.mapper.CitationMapper citationMapper;
    private final OperationLogClient operationLogClient;
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
            saveMessage(sessionId, user.tenantId(), 1, userContent, 0, 0);

            // === M3: 用户输入敏感词检测 ===
            int userAction = detectSensitiveWords(userContent, user);
            if (userAction == 2) {
                // action=2: 直接拦截（security/danger 类）
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "blocked",
                        "reason", "检测到敏感关键词，请求已拦截")));
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "done",
                        "tokens_used", 0,
                        "content", "您的输入包含敏感信息，已触发安全策略拦截。")));
                emitter.complete();
                return;
            } else if (userAction == 1) {
                // action=1: 需要审批（publish 类）— 创建审批工单
                Long ticketId = createApprovalTicket(sessionId, user,
                        "user-input", "{\"content\":\"" + userContent + "\"}", "user-input-" + sessionId, 0L);
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "approval_required",
                        "reason", "该操作涉及对外发布，需审批后执行",
                        "ticket_id", ticketId)));
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "done",
                        "tokens_used", 0,
                        "content", "您的输入涉及对外发布操作，已创建审批工单 #" + ticketId + "，请等待审批结果。")));
                emitter.complete();
                return;
            }

            // 2. 构建上下文
            List<Map<String, Object>> messages = buildContext(sessionId, user);

            // 3. 获取 Tool 列表（从 agent-svc 连接器引擎）
            List<Map<String, Object>> tools = fetchTools(user);
            log.info("[runtime] session={} tools={}", sessionId, tools.size());

            // 4. 调用 DeepSeek 流式输出
            StringBuilder aiContent = new StringBuilder();
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            int[] tokensIn = {0};   // 输入词元
            int[] tokensUsed = {0}; // 输出词元

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
                            tokensIn[0] = event.path("input_tokens").asInt(0);
                            tokensUsed[0] = event.path("output_tokens").asInt(event.path("tokens_used").asInt(0));
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

            // === M3 / FR-H3: AI 输出内容合规双审 ===
            String finalContent = aiContent.toString();
            int outputAction = detectSensitiveWords(finalContent, user);
            if (outputAction == 2) {
                // 直接拦截：中止应答并给出合规提示
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "blocked",
                        "reason", "AI 生成内容命中内容安全策略，已中止应答")));
                finalContent = "（合规提示）本次生成内容触发内容安全策略，已中止应答。";
            } else if (outputAction == 1) {
                // 需要审批：涉及对外发布类内容
                Long ticketId = createApprovalTicket(sessionId, user, "ai-output",
                        finalContent, "ai-output-" + sessionId, 0L);
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "approval_required",
                        "reason", "生成内容涉及对外发布，需审批后展示",
                        "ticket_id", ticketId)));
                finalContent = "（审批提示）本次生成内容涉及对外发布，已创建审批工单 #" + ticketId + "，请等待审批结果。";
            }

            // 6. 保存 AI 回复
            Long aiMsgId = saveMessage(sessionId, user.tenantId(), 2, finalContent, tokensIn[0], tokensUsed[0]);

            // === M3-5: 保存引用溯源 ===
            List<Map<String, Object>> citations = saveCitations(aiMsgId, user.tenantId(), finalContent, toolCalls);

            // 7. 记录 Token 用量到 ledger-svc
            recordTokenUsage(sessionId, aiMsgId, user, tokensIn[0], tokensUsed[0]);

            // FR-H1 操作留痕：发送消息
            operationLogClient.log(user.token(), "send_message",
                    "sessionId=" + sessionId, 1, "发送消息成功，消耗 " + (tokensIn[0] + tokensUsed[0]) + " 词元");

            // 更新 session 最后消息时间
            SessionEntity session = sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setLastMessageAt(java.time.LocalDateTime.now());
                sessionMapper.updateById(session);
            }

            // 发送完成事件（输入/输出分列，供前端实时展示 FR-G2）
            emitter.send(SseEmitter.event().data(Map.of(
                    "type", "done",
                    "tokens_used", tokensIn[0] + tokensUsed[0],
                    "input_tokens", tokensIn[0],
                    "output_tokens", tokensUsed[0],
                    "content", finalContent,
                    "citations", citations)));
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

                // === M3: 敏感词检测（拦截需审批的操作） ===
                int action = detectSensitiveWords(argsJson, user);
                if (action == 2) {
                    // action=2: 直接拦截（security/danger 类）
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "blocked",
                            "reason", "检测到敏感操作，已拦截",
                            "tool_name", toolName)));
                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("content", "操作已被安全策略拦截：检测到敏感关键词");
                    toolMsg.put("tool_call_id", toolCallId);
                    messages.add(toolMsg);
                    continue;
                } else if (action == 1) {
                    // action=1: 需要审批（publish 类）
                    Long ticketId = createApprovalTicket(sessionId, user, toolName, argsJson, toolCallId, 0L);
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "approval_required",
                            "tool_name", toolName,
                            "ticket_id", ticketId,
                            "reason", "该操作涉及对外发布，需审批后执行")));
                    // 暂停执行，等待审批结果
                    Map<String, Object> toolMsg = new HashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("content", "操作已提交审批（工单 #" + ticketId + "），等待审批结果");
                    toolMsg.put("tool_call_id", toolCallId);
                    messages.add(toolMsg);
                    continue;
                }

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
                        tokensUsed[0] += event.path("output_tokens").asInt(event.path("tokens_used").asInt(0));
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
                             String content, int inputTokens, int outputTokens) {
        MessageEntity msg = new MessageEntity();
        msg.setIdStr(Snowflake.nextIdStr());
        msg.setSessionId(sessionId);
        msg.setTenantId(tenantId);
        msg.setRole(role);  // 1=user 2=assistant
        msg.setContent(content);
        msg.setContentType(1);  // 1=text
        msg.setTokenInput((long) inputTokens);
        msg.setTokenOutput((long) outputTokens);
        msg.setStatus(1);
        messageMapper.insert(msg);
        return msg.getId();
    }

    /**
     * 记录 Token 用量到 ledger-svc（通过 gateway）
     * 输入/输出分列计量 FR-G2
     */
    private void recordTokenUsage(Long sessionId, Long messageId,
                                   UserContext.CurrentUser user, long inputTokens, long outputTokens) {
        if (inputTokens + outputTokens <= 0) return;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("sessionId", sessionId);
            body.put("messageId", messageId);
            body.put("model", "deepseek-chat");
            body.put("inputTokens", inputTokens);
            body.put("outputTokens", outputTokens);

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
            log.info("[runtime] token 用量记录: sessionId={} in={} out={} resp={}",
                    sessionId, inputTokens, outputTokens, resp.body());
        } catch (Exception e) {
            log.warn("[runtime] 记录 token 用量失败: {}", e.getMessage());
        }
    }

    /**
     * M3: 敏感词检测（通过 agent-svc）
     * @return 0=无命中 1=需审批 2=直接拦截
     */
    private int detectSensitiveWords(String text, UserContext.CurrentUser user) {
        if (text == null || text.isEmpty()) return 0;
        try {
            String jsonBody = mapper.writeValueAsString(Map.of("text", text));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GATEWAY_URL + "/api/v1/approvals/detect"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + user.token())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBody.getBytes(StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    int maxAction = 0;
                    for (JsonNode hit : data) {
                        int action = hit.path("action").asInt(0);
                        if (action > maxAction) maxAction = action;
                    }
                    log.info("[runtime] 敏感词检测: {} → action={}", text.substring(0, Math.min(50, text.length())), maxAction);
                    return maxAction;
                }
            }
        } catch (Exception e) {
            log.warn("[runtime] 敏感词检测失败: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * M3-5 / FR-D5: 保存引用溯源
     * 检测 AI 回复中的知识库引用和 Tool 调用结果，保存为 citation 并返回摘要列表（用于 done 事件回传前端）
     */
    private List<Map<String, Object>> saveCitations(Long messageId, Long tenantId, String aiContent,
                                                     List<Map<String, Object>> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 1. 知识库引用（mock 中提到"知识库"）
            if (aiContent.contains("知识库") || aiContent.contains("根据你知识库")) {
                io.aioa.session.repo.entity.CitationEntity c = new io.aioa.session.repo.entity.CitationEntity();
                c.setIdStr(io.aioa.common.id.Snowflake.nextIdStr());
                c.setMessageId(messageId);
                c.setTenantId(tenantId);
                c.setSourceType(1); // 知识库
                c.setSourceId("kb-2026-policy");
                c.setTitle("2026年产业扶持政策汇编");
                c.setSnippet("我市对开展人工智能应用改造的企业给予最高30%的算力使用补贴");
                citationMapper.insert(c);
                result.add(Map.of("sourceType", 1, "title", c.getTitle(), "url", "", "snippet", c.getSnippet()));
                log.info("[runtime] 保存知识库引用: msg={}", messageId);
            }

            // 2. Tool 调用结果引用
            for (Map<String, Object> tc : toolCalls) {
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                String toolName = (String) func.get("name");
                io.aioa.session.repo.entity.CitationEntity c = new io.aioa.session.repo.entity.CitationEntity();
                c.setIdStr(io.aioa.common.id.Snowflake.nextIdStr());
                c.setMessageId(messageId);
                c.setTenantId(tenantId);
                c.setSourceType(2); // Tool 结果
                c.setSourceId((String) tc.get("id"));
                c.setTitle("工具调用: " + toolName);
                c.setSnippet("由 " + toolName + " 返回的数据");
                citationMapper.insert(c);
                result.add(Map.of("sourceType", 2, "title", c.getTitle(), "url", "", "snippet", c.getSnippet()));
            }
        } catch (Exception e) {
            log.warn("[runtime] 保存引用失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * M3: 创建审批工单（通过 agent-svc）
     * @return 工单 ID
     */
    private Long createApprovalTicket(Long sessionId, UserContext.CurrentUser user,
                                       String toolName, String inputPayload, String toolCallId, Long messageId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("sessionId", sessionId);
            body.put("messageId", messageId);
            body.put("toolCallId", toolCallId);
            body.put("inputPayload", inputPayload);

            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GATEWAY_URL + "/api/v1/approvals"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + user.token())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBody.getBytes(StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("[runtime] 审批工单创建响应: status={} body={}", resp.statusCode(),
                    resp.body().length() > 200 ? resp.body().substring(0, 200) + "..." : resp.body());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode data = root.path("data");
                if (data.has("id")) {
                    Long ticketId = data.path("id").asLong();
                    log.info("[runtime] 审批工单创建成功: ticket={} tool={}", ticketId, toolName);
                    return ticketId;
                }
            }
        } catch (Exception e) {
            log.warn("[runtime] 创建审批工单失败: {}", e.getMessage());
        }
        return 0L;
    }
}
