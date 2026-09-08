package io.aioa.agent.api;

import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import io.aioa.agent.api.dto.AgentVo;
import io.aioa.agent.repo.entity.AgentEntity;
import io.aioa.agent.repo.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 专家（Agent）列表接口
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentMapper agentMapper;

    @GetMapping
    public R<List<AgentVo>> list() {
        UserContext.CurrentUser user = UserContext.get();
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getTenantId, user.tenantId())
                .eq(AgentEntity::getStatus, 1)
                .orderByAsc(AgentEntity::getSort);
        return R.ok(agentMapper.selectList(query).stream().map(this::toVo).toList());
    }

    private AgentVo toVo(AgentEntity e) {
        return AgentVo.builder()
                .id(e.getIdStr())
                .slug(e.getSlug())
                .name(e.getName())
                .avatar(e.getAvatar())
                .description(e.getDescription())
                .build();
    }
}
