package io.aioa.ledger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用量汇总 VO
 */
@Data
@Builder
public class UsageSummaryVo {
    private int recordCount;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private BigDecimal totalCost;
}
