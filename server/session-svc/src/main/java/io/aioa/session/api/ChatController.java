package io.aioa.session.api;

import io.aioa.common.context.UserContext;
import io.aioa.session.api.dto.ChatRequest;
import io.aioa.session.service.AgentRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 流式聊天接口（SSE）
 * 前端 POST → SseEmitter 流式返回 AI 回复
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final AgentRuntimeService agentRuntimeService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 流式聊天
     * POST /api/v1/sessions/{sessionId}/chat
     * 返回 text/event-stream
     */
    @PostMapping("/{sessionId}/chat")
    public SseEmitter chat(@PathVariable Long sessionId, @RequestBody ChatRequest req) {
        // 在当前线程获取 UserContext（异步线程中 ThreadLocal 会失效）
        UserContext.CurrentUser user = UserContext.get();

        SseEmitter emitter = new SseEmitter(300_000L);

        executor.execute(() -> {
            try {
                agentRuntimeService.streamChat(sessionId, req.getContent(), user, emitter);
            } catch (Exception e) {
                log.error("流式聊天异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().data(
                            "{\"type\":\"error\",\"message\":\"" + e.getMessage() + "\"}"));
                } catch (Exception ignored) {}
                emitter.complete();
            }
        });

        return emitter;
    }
}
