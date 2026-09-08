package io.aioa.session.api;

import io.aioa.common.api.R;
import io.aioa.common.client.OperationLogClient;
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
    private final OperationLogClient operationLogClient;

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
        operationLogClient.log(user.token(), "create_session", e.getTitle(), 1, "创建会话 sid=" + e.getIdStr());
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

    @GetMapping("/{idStr}")
    public R<SessionVo> detail(@PathVariable String idStr) {
        UserContext.CurrentUser user = UserContext.get();
        var q = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getIdStr, idStr)
                .eq(SessionEntity::getUserId, user.userId());
        SessionEntity e = sessionMapper.selectOne(q);
        if (e == null) return R.ok(null);
        return R.ok(toVo(e));
    }

    /**
     * 重命名会话 FR-D3（按 idStr 查询）
     */
    @PutMapping("/{idStr}/rename")
    public R<SessionVo> rename(@PathVariable String idStr, @RequestBody java.util.Map<String, String> body) {
        UserContext.CurrentUser user = UserContext.get();
        var q = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getIdStr, idStr)
                .eq(SessionEntity::getUserId, user.userId());
        SessionEntity e = sessionMapper.selectOne(q);
        if (e == null) {
            return R.fail("会话不存在");
        }
        String title = body.get("title");
        if (title != null && !title.isBlank()) {
            e.setTitle(title.trim());
            sessionMapper.updateById(e);
            log.info("[session] renamed sid={} title={}", idStr, title);
        }
        return R.ok(toVo(e));
    }

    /**
     * 逻辑删除会话 FR-D3（按 idStr 查询，删除后留痕）
     */
    @DeleteMapping("/{idStr}")
    public R<Boolean> delete(@PathVariable String idStr) {
        UserContext.CurrentUser user = UserContext.get();
        var q = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SessionEntity>()
                .eq(SessionEntity::getIdStr, idStr)
                .eq(SessionEntity::getUserId, user.userId());
        SessionEntity e = sessionMapper.selectOne(q);
        if (e == null) {
            return R.fail("会话不存在");
        }
        sessionMapper.deleteById(e.getId());
        log.info("[session] deleted(sid={}) by user={}", idStr, user.userId());
        // FR-D3 / FR-H1：逻辑删除并留痕
        operationLogClient.log(user.token(), "delete_session", e.getTitle(), 3, "逻辑删除会话 sid=" + idStr);
        return R.ok(true);
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
