package io.aioa.ledger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账单 / 用量记录 VO
 */
@Data
@Builder
public class BillVo {
    private String id;
    private Long sessionId;
    private Long messageId;
    private String model;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal costAmount;
    private LocalDateTime createdAt;
}
