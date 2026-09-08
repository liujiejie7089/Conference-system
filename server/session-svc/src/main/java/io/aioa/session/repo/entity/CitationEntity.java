package io.aioa.session.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 引用溯源实体
 * source_type: 1=知识库 2=Tool结果 3=外部链接
 */
@Data
@TableName("citations")
public class CitationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long messageId;
    private Long tenantId;
    private Integer sourceType;
    private String sourceId;
    private String title;
    private String url;
    private String snippet;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
