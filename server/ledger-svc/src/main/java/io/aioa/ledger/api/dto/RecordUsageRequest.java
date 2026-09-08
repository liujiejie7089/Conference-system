package io.aioa.ledger.api.dto;

import lombok.Data;

/**
 * 记录 Token 用量请求
 * 由 session-svc 在聊天完成后调用
 */
@Data
public class RecordUsageRequest {
    private Long sessionId;
    private Long messageId;
    private String model;
    private Long inputTokens;
    private Long outputTokens;
}
