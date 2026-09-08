package io.aioa.ledger.api;

import io.aioa.common.api.BizException;
import io.aioa.common.api.ErrorCode;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import io.aioa.ledger.api.dto.BillVo;
import io.aioa.ledger.api.dto.QuotaVo;
import io.aioa.ledger.api.dto.RecordUsageRequest;
import io.aioa.ledger.api.dto.UsageSummaryVo;
import io.aioa.ledger.repo.entity.QuotaEntity;
import io.aioa.ledger.repo.entity.TokenUsageEntity;
import io.aioa.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 账本接口 — 额度查询 + 用量记录 + 账单查询
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class QuotaController {

    private final LedgerService ledgerService;

    /**
     * 查询当前用户额度
     */
    @GetMapping("/quota")
    public R<QuotaVo> quota() {
        UserContext.CurrentUser user = UserContext.get();
        QuotaEntity e = ledgerService.getQuota(user.tenantId(), user.userId());
        if (e == null) {
            return R.ok(QuotaVo.builder()
                    .totalQuota(0L)
                    .usedQuota(0L)
                    .remainingQuota(0L)
                    .memberLevel(1)
                    .preferredModel("deepseek-chat")
                    .build());
        }
        return R.ok(QuotaVo.builder()
                .totalQuota(e.getTotalQuota())
                .usedQuota(e.getUsedQuota())
                .remainingQuota(e.getTotalQuota() - e.getUsedQuota())
                .memberLevel(e.getMemberLevel() != null ? e.getMemberLevel() : 1)
                .preferredModel(e.getPreferredModel() != null ? e.getPreferredModel() : "deepseek-chat")
                .build());
    }

    /**
     * 记录 Token 用量（由 session-svc 调用）
     * POST /api/v1/ledger/usage
     */
    @PostMapping("/usage")
    public R<Boolean> recordUsage(@RequestBody RecordUsageRequest req) {
        UserContext.CurrentUser user = UserContext.get();
        boolean ok = ledgerService.recordUsage(
                user.tenantId(),
                user.userId(),
                req.getSessionId(),
                req.getMessageId(),
                req.getModel(),
                req.getInputTokens() != null ? req.getInputTokens() : 0L,
                req.getOutputTokens() != null ? req.getOutputTokens() : 0L
        );
        if (!ok) {
            throw new BizException(ErrorCode.BAD_REQUEST, "额度不足");
        }
        return R.ok(true);
    }

    /**
     * 查询用量记录（账单列表）
     * GET /api/v1/ledger/bills?page=1&size=20
     */
    @GetMapping("/bills")
    public R<List<BillVo>> bills(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        UserContext.CurrentUser user = UserContext.get();
        List<TokenUsageEntity> list = ledgerService.getUsageList(
                user.tenantId(), user.userId(), page, size);
        return R.ok(list.stream().map(this::toBillVo).collect(Collectors.toList()));
    }

    /**
     * 查询会话用量汇总
     * GET /api/v1/ledger/usage/summary?sessionId=xxx
     */
    @GetMapping("/usage/summary")
    public R<UsageSummaryVo> sessionSummary(@RequestParam Long sessionId) {
        UserContext.CurrentUser user = UserContext.get();
        LedgerService.UsageSummary s = ledgerService.getSessionSummary(
                user.tenantId(), user.userId(), sessionId);
        return R.ok(UsageSummaryVo.builder()
                .recordCount(s.recordCount())
                .inputTokens(s.inputTokens())
                .outputTokens(s.outputTokens())
                .totalTokens(s.totalTokens())
                .totalCost(s.totalCost())
                .build());
    }

    private BillVo toBillVo(TokenUsageEntity e) {
        return BillVo.builder()
                .id(e.getIdStr())
                .sessionId(e.getSessionId())
                .messageId(e.getMessageId())
                .model(e.getModel())
                .inputTokens(e.getInputTokens())
                .outputTokens(e.getOutputTokens())
                .totalTokens(e.getTotalTokens())
                .costAmount(e.getCostAmount())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
