package io.aioa.ledger.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuotaVo {
    private Long totalQuota;
    private Long usedQuota;
    private Long remainingQuota;
}
