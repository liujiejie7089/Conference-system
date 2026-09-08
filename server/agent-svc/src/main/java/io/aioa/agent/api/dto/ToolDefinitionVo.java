package io.aioa.agent.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * OpenAI Function Calling 格式的 Tool 定义
 * 发送给 DeepSeek Sidecar 供 LLM 选择调用
 */
@Data
@Builder
public class ToolDefinitionVo {
    private String type;        // "function"
    private FunctionDef function;

    @Data
    @Builder
    public static class FunctionDef {
        private String name;
        private String description;
        private Map<String, Object> parameters;  // JSON Schema
    }
}
