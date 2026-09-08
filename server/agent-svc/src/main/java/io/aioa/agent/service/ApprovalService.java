package io.aioa.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.aioa.agent.repo.entity.ApprovalTicketEntity;
import io.aioa.agent.repo.mapper.ApprovalTicketMapper;
import io.aioa.common.id.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批服务 — 审批工单生命周期管理
 *
 * 状态机：
 * 1=pending（待审批）
 * 2=approved（已通过）
 * 3=rejected（已驳回）
 * 4=expired（已过期）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalTicketMapper approvalTicketMapper;

    /**
     * 创建审批工单
     * @return 工单 ID
     */
    public Long createTicket(Long tenantId, Long sessionId, Long messageId,
                             Long toolId, String toolCallId, String inputPayload,
                             int expireHours) {
        ApprovalTicketEntity ticket = new ApprovalTicketEntity();
        ticket.setIdStr(Snowflake.nextIdStr());
        ticket.setTenantId(tenantId);
        ticket.setSessionId(sessionId);
        ticket.setMessageId(messageId);
        ticket.setToolId(toolId);
        ticket.setToolCallId(toolCallId);
        ticket.setInputPayload(inputPayload);
        ticket.setStatus(1); // pending
        ticket.setExpiredAt(LocalDateTime.now().plusHours(expireHours > 0 ? expireHours : 24));
        approvalTicketMapper.insert(ticket);
        log.info("[审批] 创建工单: id={} session={} tool={}",
                ticket.getId(), sessionId, toolCallId);
        return ticket.getId();
    }

    /**
     * 审批通过
     * @return 更新后的工单（供回注会话使用）
     */
    public ApprovalTicketEntity approve(Long ticketId, Long approverId, String opinion) {
        ApprovalTicketEntity ticket = approvalTicketMapper.selectById(ticketId);
        if (ticket == null || ticket.getStatus() == null || ticket.getStatus() != 1) {
            throw new RuntimeException("工单不存在或状态非 pending");
        }
        int rows = approvalTicketMapper.update(null,
                new LambdaUpdateWrapper<ApprovalTicketEntity>()
                        .eq(ApprovalTicketEntity::getId, ticketId)
                        .eq(ApprovalTicketEntity::getStatus, 1)
                        .set(ApprovalTicketEntity::getStatus, 2)
                        .set(ApprovalTicketEntity::getApproverId, approverId)
                        .set(ApprovalTicketEntity::getOpinion, opinion)
                        .set(ApprovalTicketEntity::getApprovedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new RuntimeException("工单不存在或状态非 pending");
        }
        log.info("[审批] 通过: ticket={} approver={}", ticketId, approverId);
        return ticket;
    }

    /**
     * 审批驳回
     * @return 更新后的工单（供回注会话使用）
     */
    public ApprovalTicketEntity reject(Long ticketId, Long approverId, String opinion) {
        ApprovalTicketEntity ticket = approvalTicketMapper.selectById(ticketId);
        if (ticket == null || ticket.getStatus() == null || ticket.getStatus() != 1) {
            throw new RuntimeException("工单不存在或状态非 pending");
        }
        int rows = approvalTicketMapper.update(null,
                new LambdaUpdateWrapper<ApprovalTicketEntity>()
                        .eq(ApprovalTicketEntity::getId, ticketId)
                        .eq(ApprovalTicketEntity::getStatus, 1)
                        .set(ApprovalTicketEntity::getStatus, 3)
                        .set(ApprovalTicketEntity::getApproverId, approverId)
                        .set(ApprovalTicketEntity::getOpinion, opinion)
                        .set(ApprovalTicketEntity::getApprovedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new RuntimeException("工单不存在或状态非 pending");
        }
        log.info("[审批] 驳回: ticket={} approver={}", ticketId, approverId);
        return ticket;
    }

    /**
     * 查询工单详情
     */
    public ApprovalTicketEntity getTicket(Long ticketId) {
        return approvalTicketMapper.selectById(ticketId);
    }

    /**
     * 查询会话关联的审批工单
     */
    public List<ApprovalTicketEntity> getTicketsBySession(Long sessionId) {
        return approvalTicketMapper.selectList(
                new LambdaQueryWrapper<ApprovalTicketEntity>()
                        .eq(ApprovalTicketEntity::getSessionId, sessionId)
                        .orderByDesc(ApprovalTicketEntity::getCreatedAt));
    }

    /**
     * 查询待审批工单列表
     */
    public List<ApprovalTicketEntity> getPendingTickets(Long tenantId) {
        return approvalTicketMapper.selectList(
                new LambdaQueryWrapper<ApprovalTicketEntity>()
                        .eq(ApprovalTicketEntity::getTenantId, tenantId)
                        .eq(ApprovalTicketEntity::getStatus, 1)
                        .orderByDesc(ApprovalTicketEntity::getCreatedAt));
    }
}
