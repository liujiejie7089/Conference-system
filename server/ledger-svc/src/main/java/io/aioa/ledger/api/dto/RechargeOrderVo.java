package io.aioa.ledger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RechargeOrderVo {
    private String id;
    /** 1=词元包 2=会员升级 */
    private Integer type;
    private String typeName;
    private String productName;
    private Long tokenAmount;
    private BigDecimal amount;
    private Integer status;
    private String statusName;
    private LocalDateTime createdAt;
}
