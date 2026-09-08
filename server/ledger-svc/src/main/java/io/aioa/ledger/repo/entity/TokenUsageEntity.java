package io.aioa.ledger.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("token_usages")
public class TokenUsageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long tenantId;
    private Long userId;
    private Long sessionId;
    private Long messageId;
    private String model;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private BigDecimal costAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
