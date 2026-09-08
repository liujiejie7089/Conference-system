package io.aioa.session.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("messages")
public class MessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long sessionId;
    private Long tenantId;
    private Integer role;
    private String content;
    private Integer contentType;
    private String toolCallId;
    private Long parentId;
    private Long tokenInput;
    private Long tokenOutput;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
