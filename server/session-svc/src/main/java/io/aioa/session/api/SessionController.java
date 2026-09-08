package io.aioa.session.api;

import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import io.aioa.common.id.Snowflake;
import io.aioa.session.api.dto.CreateSessionRequest;
import io.aioa.session.api.dto.SessionVo;
import io.aioa.session.repo.entity.SessionEntity;
import io.aioa.session.repo.mapper.SessionMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话 CRUD（M1 阶段：建会话/列表/详情；M2 接入流式）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionMapper sessionMapper;

    @PostMapping
    public R<SessionVo> create(@Valid @RequestBody CreateSessionRequest req) {
        UserContext.CurrentUser user = UserContext.get();
        SessionEntity e = new SessionEntity();
        e.setIdStr(Snowflake.nextIdStr());
        e.setTenantId(user.tenantId());
        e.setUserId(user.userId());
        e.setAgentId(req.getAgentId());
        e.setTitle(req.getTitle() == null || req.getTitle().isBlank() ? "新会话" : req.getTitle());
        e.setStatus(1);
        sessionMapper.insert(e);
        log.info("[session] created sid={} uid={}", e.getIdStr(), user.userId());
        return R.ok(toVo(e));
    }

    @GetMapping
    public R<List<SessionVo>> list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        UserContext.CurrentUser user = UserContext.get();
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getTenantId, user.tenantId())
                .eq(SessionEntity::getUserId, user.userId())
                .orderByDesc(SessionEntity::getLastMessageAt)
                .last("LIMIT " + ((page - 1) * size) + "," + size);
        return R.ok(sessionMapper.selectList(query).stream().map(this::toVo).toList());
    }

    @GetMapping("/{id}")
    public R<SessionVo> detail(@PathVariable Long id) {
        SessionEntity e = sessionMapper.selectById(id);
        if (e == null) return R.ok(null);
        return R.ok(toVo(e));
    }

    private SessionVo toVo(SessionEntity e) {
        return SessionVo.builder()
                .id(e.getIdStr())
                .title(e.getTitle())
                .agentId(e.getAgentId())
                .status(e.getStatus())
                .lastMessageAt(e.getLastMessageAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
