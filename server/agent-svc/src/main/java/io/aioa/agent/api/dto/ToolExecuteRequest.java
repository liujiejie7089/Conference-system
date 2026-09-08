package io.aioa.agent.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * Tool 执行请求
 */
@Data
public class ToolExecuteRequest {
    private Long toolId;
    private Map<String, Object> arguments;  // LLM 生成的参数
}
