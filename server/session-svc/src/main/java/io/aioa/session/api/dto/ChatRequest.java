package io.aioa.session.api.dto;

import lombok.Data;

/**
 * 聊天请求
 */
@Data
public class ChatRequest {
    private String content;      // 用户消息
    private boolean kbOn;        // 是否启用知识库
    private String agentSlug;    // Agent 标识（可选）
}
