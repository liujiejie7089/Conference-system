package io.aioa.agent.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待办卡片 VO — 合并审批工单 + 技能表单的统一展示
 * 客户端「待办」页直接消费
 */
@Data
@Builder
public class TodoVo {
    /** 主键(idStr) */
    private String id;
    /** 数据来源:approval_ticket / skill_form */
    private String source;
    /** 1=待处理 2=处理中 3=已驳回 4=已办结 */
    private Integer status;
    /** 状态中文:待审批/审批中/已驳回/已办结 */
    private String statusText;
    /** 卡片标题 */
    private String title;
    /** 摘要(时间/来源/详情) */
    private String summary;
    /** 来源标签(请假助手/办文助手…) */
    private String sourceLabel;
    /** emoji 图标 */
    private String icon;
    /** 图标底色 */
    private String iconBg;
    /** 发起人姓名 */
    private String applicantName;
    /** 关联会话 id(可能为空) */
    private Long sessionId;
    /** 关联会话 idStr(可能为空) */
    private String sessionIdStr;
    /** 关联工具 ID(可能为空) */
    private Long toolId;
    /** 发起时间 */
    private LocalDateTime submittedAt;
}