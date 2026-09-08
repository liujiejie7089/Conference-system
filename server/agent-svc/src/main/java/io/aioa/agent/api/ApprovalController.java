package io.aioa.agent.api;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import io.aioa.agent.api.dto.ApprovalActionRequest;
import io.aioa.agent.api.dto.ApprovalTicketVo;
import io.aioa.agent.repo.entity.ApprovalTicketEntity;
import io.aioa.agent.service.ApprovalService;
import io.aioa.agent.service.SensitiveWordService;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审批控制器 — 敏感词检测 + 审批工单管理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final SensitiveWordService sensitiveWordService;

    /**
     * 敏感词检测
     * POST /api/v1/approvals/detect
     */
    @PostMapping("/detect")
    public R<List<SensitiveWordService.HitResult>> detect(@RequestBody java.util.Map<String, String> body) {
        String text = body.get("text");
        var hits = sensitiveWordService.detectWithDetail(text);
        return R.ok(hits);
    }

    /**
     * 创建审批工单（由 session-svc 调用）
     * POST /api/v1/approvals
     */
    @PostMapping
    public R<ApprovalTicketVo> create(@RequestBody java.util.Map<String, Object> body) {
        UserContext.CurrentUser user = UserContext.get();
        Long ticketId = approvalService.createTicket(
                user.tenantId(),
                ((Number) body.get("sessionId")).longValue(),
                body.get("messageId") != null ? ((Number) body.get("messageId")).longValue() : 0L,
                body.get("toolId") != null ? ((Number) body.get("toolId")).longValue() : 0L,
                (String) body.get("toolCallId"),
                (String) body.get("inputPayload"),
                24
        );
        ApprovalTicketEntity e = approvalService.getTicket(ticketId);
        return R.ok(toVo(e));
    }

    /**
     * 查询会话关联的审批工单
     * GET /api/v1/approvals/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public R<List<ApprovalTicketVo>> getBySession(@PathVariable Long sessionId) {
        List<ApprovalTicketEntity> list = approvalService.getTicketsBySession(sessionId);
        return R.ok(list.stream().map(this::toVo).collect(Collectors.toList()));
    }

    /**
     * 查询待审批工单列表
     * GET /api/v1/approvals/pending
     */
    @GetMapping("/pending")
    public R<List<ApprovalTicketVo>> pending() {
        UserContext.CurrentUser user = UserContext.get();
        List<ApprovalTicketEntity> list = approvalService.getPendingTickets(user.tenantId());
        return R.ok(list.stream().map(this::toVo).collect(Collectors.toList()));
    }

    /**
     * 审批通过
     * POST /api/v1/approvals/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public R<Boolean> approve(@PathVariable Long id, @RequestBody ApprovalActionRequest req) {
        UserContext.CurrentUser user = UserContext.get();
        ApprovalTicketEntity ticket = approvalService.approve(id, user.userId(), req.getOpinion());
        injectResult(ticket, "已通过", req.getOpinion(), user.token());
        return R.ok(true);
    }

    /**
     * 审批驳回
     * POST /api/v1/approvals/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public R<Boolean> reject(@PathVariable Long id, @RequestBody ApprovalActionRequest req) {
        UserContext.CurrentUser user = UserContext.get();
        ApprovalTicketEntity ticket = approvalService.reject(id, user.userId(), req.getOpinion());
        injectResult(ticket, "已驳回", req.getOpinion(), user.token());
        return R.ok(true);
    }

    /**
     * 审批结果回注会话（FR-D6 演示链路）
     * 通过 session-svc 的 inject 接口将审批结果以 assistant 消息写入原会话
     */
    private void injectResult(ApprovalTicketEntity ticket, String result, String opinion, String token) {
        if (ticket == null || ticket.getSessionId() == null) return;
        try {
            String opinionText = (opinion == null || opinion.isBlank()) ? "" : "，审批意见：" + opinion;
            String content = "审批通知：工单 #" + ticket.getIdStr() + " 已" + result + opinionText;

            Map<String, Object> body = new HashMap<>();
            body.put("content", content);
            body.put("role", 2);

            try (HttpResponse resp = HttpRequest.post(
                            "http://localhost:8090/api/v1/sessions/" + ticket.getSessionId() + "/inject")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(5000)
                    .execute()) {
                log.info("[审批] 结果回注会话: session={} result={} http={}",
                        ticket.getSessionId(), result, resp.getStatus());
            }
        } catch (Exception e) {
            log.warn("[审批] 结果回注会话失败: {}", e.getMessage());
        }
    }

    /**
     * 查询工单详情
     * GET /api/v1/approvals/{id}
     */
    @GetMapping("/{id}")
    public R<ApprovalTicketVo> detail(@PathVariable Long id) {
        ApprovalTicketEntity e = approvalService.getTicket(id);
        if (e == null) return R.ok(null);
        return R.ok(toVo(e));
    }

    private ApprovalTicketVo toVo(ApprovalTicketEntity e) {
        String statusText = switch (e.getStatus()) {
            case 1 -> "pending";
            case 2 -> "approved";
            case 3 -> "rejected";
            case 4 -> "expired";
            default -> "unknown";
        };
        return ApprovalTicketVo.builder()
                .id(e.getId())
                .idStr(e.getIdStr())
                .sessionId(e.getSessionId())
                .messageId(e.getMessageId())
                .toolId(e.getToolId())
                .toolCallId(e.getToolCallId())
                .inputPayload(e.getInputPayload())
                .status(e.getStatus())
                .statusText(statusText)
                .approverId(e.getApproverId())
                .opinion(e.getOpinion())
                .approvedAt(e.getApprovedAt())
                .expiredAt(e.getExpiredAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
