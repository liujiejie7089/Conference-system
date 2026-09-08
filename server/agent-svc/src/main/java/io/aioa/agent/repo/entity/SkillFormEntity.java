package io.aioa.agent.repo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skill_forms")
public class SkillFormEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String idStr;
    private Long toolId;
    /** 发起会话 id */
    private Long sessionId;
    /** 发起人 id */
    private Long applicantUserId;
    /** 发起人姓名 */
    private String applicantName;
    private Long tenantId;
    private String formSchema;  // JSON: 表单字段定义
    private Integer version;
    /** 1=待处理 2=处理中 3=已驳回 4=已办结 */
    private Integer status;
    /** 卡片标题(客户端待办页直接展示) */
    private String title;
    /** 卡片摘要(时间/来源/详情) */
    private String summary;
    /** 来源标签(请假助手/办文助手…) */
    private String sourceLabel;
    /** emoji 图标 */
    private String icon;
    /** 图标底色 */
    private String iconBg;
    /** 发起时间 */
    private LocalDateTime submittedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
