package io.aioa.agent.api;

import io.aioa.agent.api.dto.ConnectorRegisterRequest;
import io.aioa.agent.api.dto.ToolDefinitionVo;
import io.aioa.agent.api.dto.ToolExecuteRequest;
import io.aioa.agent.api.dto.ToolExecuteResponse;
import io.aioa.agent.service.ConnectorService;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通用连接器引擎 — REST API
 * 1. 注册业务系统（解析 OpenAPI → 自动生成 Tool）
 * 2. 获取 Tool 列表（OpenAI Function Calling 格式）
 * 3. 执行 Tool 调用（鉴权注入 → HTTP 请求 → 返回结果）
 */
@RestController
@RequestMapping("/api/v1/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorService connectorService;

    /**
     * 注册业务系统：解析 OpenAPI 文档，自动生成 Tool
     */
    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody ConnectorRegisterRequest req) {
        UserContext.CurrentUser user = UserContext.get();
        int count = connectorService.registerFromOpenApi(
                req.getOpenApiUrl(), req.getBaseUrl(), req.getAuthToken(),
                user.tenantId(), req.getSystemName());
        return R.ok(Map.of("registered_tools", count));
    }

    /**
     * 获取 Tool 列表（OpenAI Function Calling 格式，供 DeepSeek Sidecar 使用）
     */
    @GetMapping("/tools")
    public R<List<ToolDefinitionVo>> tools() {
        UserContext.CurrentUser user = UserContext.get();
        return R.ok(connectorService.getToolDefinitions(user.tenantId()));
    }

    /**
     * 执行 Tool 调用（Agent Runtime 调用，注入用户 Token）
     */
    @PostMapping("/tools/{toolId}/execute")
    public R<ToolExecuteResponse> execute(
            @PathVariable Long toolId,
            @RequestBody ToolExecuteRequest req,
            @RequestHeader(value = "X-User-Token", required = false) String userToken) {
        req.setToolId(toolId);
        ToolExecuteResponse resp = connectorService.executeTool(toolId, req.getArguments(), userToken);
        return R.ok(resp);
    }
}
