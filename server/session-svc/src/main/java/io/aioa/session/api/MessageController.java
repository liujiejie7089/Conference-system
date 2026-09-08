package io.aioa.session.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aioa.common.api.BizException;
import io.aioa.common.api.ErrorCode;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import io.aioa.session.api.dto.CitationVo;
import io.aioa.session.api.dto.MessageVo;
import io.aioa.session.repo.entity.CitationEntity;
import io.aioa.session.repo.entity.MessageEntity;
import io.aioa.session.repo.entity.SessionEntity;
import io.aioa.session.repo.mapper.CitationMapper;
import io.aioa.session.repo.mapper.MessageMapper;
import io.aioa.session.repo.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息与引用溯源控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageMapper messageMapper;
    private final CitationMapper citationMapper;
    private final SessionMapper sessionMapper;

    /**
     * 查询会话的消息列表（含引用溯源）
     * GET /api/v1/messages/session/{sessionIdStr}
     * 前端传入的是会话 idStr，这里解析为内部数字 id 后查询
     */
    @GetMapping("/session/{sessionIdStr}")
    public R<List<MessageVo>> listBySession(@PathVariable String sessionIdStr) {
        UserContext.CurrentUser user = UserContext.get();

        Long sessionId = resolveSessionId(sessionIdStr, user.userId());

        List<MessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getSessionId, sessionId)
                        .orderByAsc(MessageEntity::getCreatedAt));

        if (messages.isEmpty()) {
            return R.ok(List.of());
        }

        // 批量查询所有消息的引用来源，按 messageId 分组
        List<Long> messageIds = messages.stream().map(MessageEntity::getId).toList();
        Map<Long, List<CitationEntity>> citationMap = citationMapper.selectList(
                        new LambdaQueryWrapper<CitationEntity>()
                                .in(CitationEntity::getMessageId, messageIds)
                                .orderByDesc(CitationEntity::getCreatedAt))
                .stream()
                .collect(Collectors.groupingBy(CitationEntity::getMessageId));

        List<MessageVo> result = messages.stream()
                .map(m -> toVo(m, citationMap.getOrDefault(m.getId(), List.of())))
                .toList();
        return R.ok(result);
    }

    /**
     * 查询消息的引用来源
     * GET /api/v1/messages/{messageId}/citations
     */
    @GetMapping("/{messageId}/citations")
    public R<List<CitationEntity>> citations(@PathVariable Long messageId) {
        List<CitationEntity> citations = citationMapper.selectList(
                new LambdaQueryWrapper<CitationEntity>()
                        .eq(CitationEntity::getMessageId, messageId)
                        .orderByDesc(CitationEntity::getCreatedAt));
        return R.ok(citations);
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

    private MessageVo toVo(MessageEntity m, List<CitationEntity> citations) {
        return MessageVo.builder()
                .id(m.getIdStr())
                .role(m.getRole())
                .content(m.getContent())
                .tokenOutput(m.getTokenOutput())
                .createdAt(m.getCreatedAt())
                .citations(citations.stream().map(this::toCitationVo).toList())
                .build();
    }

    private CitationVo toCitationVo(CitationEntity c) {
        return CitationVo.builder()
                .id(c.getIdStr())
                .sourceType(c.getSourceType())
                .title(c.getTitle())
                .url(c.getUrl())
                .snippet(c.getSnippet())
                .build();
    }
}
