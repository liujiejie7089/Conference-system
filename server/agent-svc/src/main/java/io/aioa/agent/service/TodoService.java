package io.aioa.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aioa.agent.api.dto.TodoVo;
import io.aioa.agent.repo.entity.ApprovalTicketEntity;
import io.aioa.agent.repo.entity.SkillFormEntity;
import io.aioa.agent.repo.mapper.ApprovalTicketMapper;
import io.aioa.agent.repo.mapper.SkillFormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 待办聚合服务 — 把审批工单 + 技能表单合并成统一的待办列表
 * <ul>
 *   <li>pending(待我处理): 当前租户下 status=1 的 approval_ticket + skill_form</li>
 *   <li>applied(我的申请): 我作为申请人的 skill_form + 我会话下的非 pending 工单</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final ApprovalTicketMapper approvalTicketMapper;
    private final SkillFormMapper skillFormMapper;
    // sessions / users 由各自微服务管理;这里仅按 sessionId 关联,前端可拿到 idStr 后再回查

    /**
     * 拉取待办列表
     * @param tenantId 当前租户
     * @param userId   当前用户
     * @param type     pending / applied
     */
    public List<TodoVo> list(Long tenantId, Long userId, String type) {
        List<TodoVo> out = new ArrayList<>();
        if ("pending".equalsIgnoreCase(type)) {
            out.addAll(listPendingSkillForms(tenantId));
            out.addAll(listPendingApprovals(tenantId));
        } else if ("applied".equalsIgnoreCase(type)) {
            out.addAll(listAppliedSkillForms(tenantId, userId));
            out.addAll(listAppliedApprovals(tenantId));
        } else {
            // 不传 type:返回待办+我的申请全部
            out.addAll(listPendingSkillForms(tenantId));
            out.addAll(listPendingApprovals(tenantId));
            out.addAll(listAppliedSkillForms(tenantId, userId));
            out.addAll(listAppliedApprovals(tenantId));
        }
        out.sort(Comparator.comparing(TodoVo::getSubmittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /** 当前租户下待处理(status=1)的 skill_form */
    private List<TodoVo> listPendingSkillForms(Long tenantId) {
        List<SkillFormEntity> rows = skillFormMapper.selectList(
                new LambdaQueryWrapper<SkillFormEntity>()
                        .eq(SkillFormEntity::getTenantId, tenantId)
                        .eq(SkillFormEntity::getStatus, 1)
                        .orderByDesc(SkillFormEntity::getSubmittedAt));
        List<TodoVo> out = new ArrayList<>();
        for (SkillFormEntity e : rows) out.add(toVoFromSkillForm(e));
        return out;
    }

    /** 当前租户下待处理(status=1)的 approval_ticket */
    private List<TodoVo> listPendingApprovals(Long tenantId) {
        List<ApprovalTicketEntity> rows = approvalTicketMapper.selectList(
                new LambdaQueryWrapper<ApprovalTicketEntity>()
                        .eq(ApprovalTicketEntity::getTenantId, tenantId)
                        .eq(ApprovalTicketEntity::getStatus, 1)
                        .orderByDesc(ApprovalTicketEntity::getCreatedAt));
        List<TodoVo> out = new ArrayList<>();
        for (ApprovalTicketEntity e : rows) out.add(toVoFromApproval(e));
        return out;
    }

    /** 我作为申请人(approval_ticket)提交的 skill_form */
    private List<TodoVo> listAppliedSkillForms(Long tenantId, Long userId) {
        List<SkillFormEntity> rows = skillFormMapper.selectList(
                new LambdaQueryWrapper<SkillFormEntity>()
                        .eq(SkillFormEntity::getTenantId, tenantId)
                        .eq(SkillFormEntity::getApplicantUserId, userId)
                        .isNotNull(SkillFormEntity::getApplicantUserId)
                        .orderByDesc(SkillFormEntity::getSubmittedAt));
        List<TodoVo> out = new ArrayList<>();
        for (SkillFormEntity e : rows) out.add(toVoFromSkillForm(e));
        return out;
    }

    /** 我作为申请人发起的 approval_ticket: 通过 session.user_id 关联 */
    private List<TodoVo> listAppliedApprovals(Long tenantId) {
        // 这里不再 join sessions(避免跨服务),只返回当前租户下 status != 1 的工单,前端按 sessionIdStr 回查
        List<ApprovalTicketEntity> rows = approvalTicketMapper.selectList(
                new LambdaQueryWrapper<ApprovalTicketEntity>()
                        .eq(ApprovalTicketEntity::getTenantId, tenantId)
                        .ne(ApprovalTicketEntity::getStatus, 1)
                        .orderByDesc(ApprovalTicketEntity::getCreatedAt));
        List<TodoVo> out = new ArrayList<>();
        for (ApprovalTicketEntity e : rows) out.add(toVoFromApproval(e));
        return out;
    }

    private TodoVo toVoFromSkillForm(SkillFormEntity e) {
        Integer st = e.getStatus();
        return TodoVo.builder()
                .id(e.getIdStr())
                .source("skill_form")
                .status(st)
                .statusText(statusText(st))
                .title(e.getTitle())
                .summary(e.getSummary())
                .sourceLabel(e.getSourceLabel())
                .icon(e.getIcon())
                .iconBg(e.getIconBg())
                .applicantName(e.getApplicantName())
                .sessionId(e.getSessionId())
                .toolId(e.getToolId())
                .submittedAt(e.getSubmittedAt() != null ? e.getSubmittedAt() : e.getCreatedAt())
                .build();
    }

    private TodoVo toVoFromApproval(ApprovalTicketEntity e) {
        Integer st = e.getStatus();
        String title = "审批工单 #" + e.getIdStr();
        String summary = e.getInputPayload();
        if (summary != null && summary.length() > 80) summary = summary.substring(0, 80) + "…";
        return TodoVo.builder()
                .id(e.getIdStr())
                .source("approval_ticket")
                .status(st)
                .statusText(statusText(st))
                .title(title)
                .summary("来源: 会话审批卡点 · " + summary)
                .sourceLabel("数字员工")
                .icon("🔔")
                .iconBg("#fff4e6")
                .sessionId(e.getSessionId())
                .toolId(e.getToolId())
                .submittedAt(e.getCreatedAt())
                .build();
    }

    private String statusText(Integer st) {
        if (st == null) return "未知";
        return switch (st) {
            case 1 -> "待审批";
            case 2 -> "审批中";
            case 3 -> "已驳回";
            case 4 -> "已办结";
            default -> "未知";
        };
    }

    /** 统计当前用户的待办数(用于客户端 tabBar badge) */
    public long countPending(Long tenantId) {
        Long a = approvalTicketMapper.selectCount(
                new LambdaQueryWrapper<ApprovalTicketEntity>()
                        .eq(ApprovalTicketEntity::getTenantId, tenantId)
                        .eq(ApprovalTicketEntity::getStatus, 1));
        Long b = skillFormMapper.selectCount(
                new LambdaQueryWrapper<SkillFormEntity>()
                        .eq(SkillFormEntity::getTenantId, tenantId)
                        .eq(SkillFormEntity::getStatus, 1));
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }
}