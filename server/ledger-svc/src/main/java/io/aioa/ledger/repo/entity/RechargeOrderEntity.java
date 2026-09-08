package io.aioa.ledger.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单实体
 * type: 1=词元包, 2=会员升级
 * status: 1=待支付, 2=已支付, 3=已取消, 4=已退款
 */
@Data
@TableName("recharge_orders")
public class RechargeOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long tenantId;
    private Long userId;
    /** 1=词元包 2=会员升级 */
    private Integer type;
    /** 商品标识：token_pack_s / token_pack_m / token_pack_l / member_silver / member_gold */
    private String productCode;
    private String productName;
    private Long tokenAmount;
    private BigDecimal amount;
    /** 1=待支付 2=已支付 3=已取消 4=已退款 */
    private Integer status;
    private String payMethod;
    private String tradeNo;
    private LocalDateTime paidAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
