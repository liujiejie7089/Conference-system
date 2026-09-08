package io.aioa.agent.api;

import io.aioa.agent.api.dto.TodoVo;
import io.aioa.agent.service.TodoService;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 待办控制器 — 聚合审批工单 + 技能表单为客户端「待办」页消费
 * GET /api/v1/todos?type=pending|applied|all
 * GET /api/v1/todos/count          — 用于 tabBar badge
 */
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public R<List<TodoVo>> list(@RequestParam(defaultValue = "pending") String type) {
        UserContext.CurrentUser user = UserContext.get();
        return R.ok(todoService.list(user.tenantId(), user.userId(), type));
    }

    @GetMapping("/count")
    public R<Map<String, Object>> count() {
        UserContext.CurrentUser user = UserContext.get();
        long n = todoService.countPending(user.tenantId());
        return R.ok(Map.of("pending", n));
    }
}