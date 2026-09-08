package io.aioa.ledger.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作留痕实体 FR-H1
 * action: login / create_session / send_message / upload_file / recharge / delete_session / switch_model
 * result: 1=成功 2=失败 3=逻辑删除
 */
@Data
@TableName("operation_logs")
public class OperationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long tenantId;
    private Long userId;
    private String action;
    private String target;
    private Integer result;
    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
