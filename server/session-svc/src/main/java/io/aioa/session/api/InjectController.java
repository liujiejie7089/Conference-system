package io.aioa.session.api;

import io.aioa.common.api.R;
import io.aioa.common.id.Snowflake;
import io.aioa.session.repo.entity.MessageEntity;
import io.aioa.session.repo.entity.SessionEntity;
import io.aioa.session.repo.mapper.MessageMapper;
import io.aioa.session.repo.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话消息注入接口 — 供 agent-svc 审批结果回注会话使用（FR-D6 演示链路）
 * 审批通过/驳回后，将结果以一条 assistant 消息回注到原会话。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class InjectController {

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;

    /**
     * 注入消息到会话（由 agent-svc 内部回调，sessionId 为数据库自增 id）
     * POST /api/v1/sessions/{sessionId}/inject  { "content": "...", "role": 2 }
     */
    @PostMapping("/{sessionId}/inject")
    public R<Boolean> inject(@PathVariable Long sessionId, @RequestBody Map<String, Object> body) {
        SessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return R.fail("会话不存在");
        }

        String content = body.get("content") != null ? String.valueOf(body.get("content")) : "";
        int role = body.get("role") != null ? ((Number) body.get("role")).intValue() : 2;

        MessageEntity msg = new MessageEntity();
        msg.setIdStr(Snowflake.nextIdStr());
        msg.setSessionId(sessionId);
        msg.setTenantId(session.getTenantId());
        msg.setRole(role); // 2=assistant
        msg.setContent(content);
        msg.setContentType(1);
        msg.setTokenInput(0L);
        msg.setTokenOutput(0L);
        msg.setStatus(1);
        messageMapper.insert(msg);

        session.setLastMessageAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        log.info("[session] 注入审批结果消息: sid={} role={}", sessionId, role);
        return R.ok(true);
    }
}
