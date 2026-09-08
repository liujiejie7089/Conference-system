package io.aioa.session.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aioa.common.api.BizException;
import io.aioa.common.api.ErrorCode;
import io.aioa.common.context.UserContext;
import io.aioa.session.api.dto.ChatRequest;
import io.aioa.session.repo.entity.SessionEntity;
import io.aioa.session.repo.mapper.SessionMapper;
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
    private final SessionMapper sessionMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 流式聊天
     * POST /api/v1/sessions/{sessionIdStr}/chat
     * 返回 text/event-stream
     * 前端传入的是会话 idStr，这里解析为内部数字 id 后再走运行时
     */
    @PostMapping("/{sessionIdStr}/chat")
    public SseEmitter chat(@PathVariable String sessionIdStr, @RequestBody ChatRequest req) {
        // 在当前线程获取 UserContext（异步线程中 ThreadLocal 会失效）
        UserContext.CurrentUser user = UserContext.get();

        Long sessionId = resolveSessionId(sessionIdStr, user.userId());

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

    private Long resolveSessionId(String idStr, Long userId) {
        SessionEntity session = sessionMapper.selectOne(new LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getIdStr, idStr)
                .eq(SessionEntity::getUserId, userId));
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session.getId();
    }
}
