package io.aioa.agent.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词实体
 * action: 1=触发审批（publish类） 2=直接拦截（security/danger类）
 */
@Data
@TableName("sensitive_words")
public class SensitiveWordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long tenantId;  // 0=全局
    private String word;
    private String category;
    private Integer action;  // 1=审批 2=拦截
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
