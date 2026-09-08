package io.aioa.ledger.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈实体 FR-H4
 * type: 1=错误 2=有害 3=侵权
 * status: 1=待处理 2=已处理 3=已关闭
 */
@Data
@TableName("feedbacks")
public class FeedbackEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long tenantId;
    private Long userId;
    private Long messageId;
    /** 1=错误 2=有害 3=侵权 */
    private Integer type;
    private String content;
    /** 1=待处理 2=已处理 3=已关闭 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
