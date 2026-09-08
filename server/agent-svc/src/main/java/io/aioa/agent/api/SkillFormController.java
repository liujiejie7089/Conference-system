package io.aioa.agent.api;

import io.aioa.agent.service.SkillFormService;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能表单控制器
 * - 获取 Tool 的表单 schema（用于前端动态渲染）
 */
@RestController
@RequestMapping("/api/v1/skill-forms")
@RequiredArgsConstructor
public class SkillFormController {

    private final SkillFormService skillFormService;

    /**
     * 获取指定 Tool 的表单 schema
     * GET /api/v1/skill-forms/{toolId}
     */
    @GetMapping("/{toolId}")
    public R<Object> getForm(@PathVariable Long toolId) {
        return R.ok(skillFormService.getFormSchema(toolId));
    }

    /**
     * 列出当前租户所有 Tool 的表单
     * GET /api/v1/skill-forms
     */
    @GetMapping
    public R<List<Object>> list() {
        UserContext.CurrentUser user = UserContext.get();
        return R.ok(skillFormService.listForms(user.tenantId()));
    }

    /**
     * 保存自定义表单 schema
     * POST /api/v1/skill-forms
     */
    @PostMapping
    public R<Long> save(@RequestBody Map<String, Object> body) {
        UserContext.CurrentUser user = UserContext.get();
        Long toolId = ((Number) body.get("toolId")).longValue();
        String formSchema = (String) body.get("formSchema");
        return R.ok(skillFormService.saveFormSchema(toolId, user.tenantId(), formSchema));
    }
}
