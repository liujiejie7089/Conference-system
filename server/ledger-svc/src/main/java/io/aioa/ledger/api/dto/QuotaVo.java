package io.aioa.ledger.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuotaVo {
    private Long totalQuota;
    private Long usedQuota;
    private Long remainingQuota;
    /** 1=铜牌 2=银牌 3=金牌 */
    private Integer memberLevel;
    private String preferredModel;
}
