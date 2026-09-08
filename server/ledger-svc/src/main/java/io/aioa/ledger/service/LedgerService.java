package io.aioa.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aioa.common.id.Snowflake;
import io.aioa.ledger.repo.entity.QuotaEntity;
import io.aioa.ledger.repo.entity.TokenUsageEntity;
import io.aioa.ledger.repo.mapper.QuotaMapper;
import io.aioa.ledger.repo.mapper.TokenUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 账本服务 — Token 计量 + 额度扣减 + 账单生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final QuotaMapper quotaMapper;
    private final TokenUsageMapper tokenUsageMapper;

    // DeepSeek 定价（元/千 token）— M2 阶段简化定价
    private static final BigDecimal INPUT_PRICE_PER_1K = new BigDecimal("0.001");
    private static final BigDecimal OUTPUT_PRICE_PER_1K = new BigDecimal("0.002");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    /**
     * 记录 Token 用量并扣减额度
     * @return 扣减成功返回 true，余额不足返回 false
     */
    public boolean recordUsage(Long tenantId, Long userId, Long sessionId,
                                Long messageId, String model,
                                long inputTokens, long outputTokens) {
        long totalTokens = inputTokens + outputTokens;
        if (totalTokens <= 0) {
            return true; // 无需扣减
        }

        // 1. 计算费用
        BigDecimal cost = INPUT_PRICE_PER_1K.multiply(BigDecimal.valueOf(inputTokens))
                .divide(THOUSAND, 6, java.math.RoundingMode.HALF_UP)
                .add(OUTPUT_PRICE_PER_1K.multiply(BigDecimal.valueOf(outputTokens))
                        .divide(THOUSAND, 6, java.math.RoundingMode.HALF_UP));

        // 2. 插入 token_usages 记录
        TokenUsageEntity usage = new TokenUsageEntity();
        usage.setIdStr(Snowflake.nextIdStr());
        usage.setTenantId(tenantId);
        usage.setUserId(userId);
        usage.setSessionId(sessionId);
        usage.setMessageId(messageId);
        usage.setModel(model != null ? model : "deepseek-chat");
        usage.setInputTokens(inputTokens);
        usage.setOutputTokens(outputTokens);
        usage.setTotalTokens(totalTokens);
        usage.setCostAmount(cost);
        tokenUsageMapper.insert(usage);
        log.info("[ledger] 记录用量: tenant={} user={} session={} tokens={} cost={}",
                tenantId, userId, sessionId, totalTokens, cost);

        // 3. 原子扣减额度
        int rows = quotaMapper.consume(tenantId, userId, totalTokens);
        if (rows == 0) {
            // 可能是 quota 记录不存在，尝试自动创建
            QuotaEntity existing = getQuota(tenantId, userId);
            if (existing == null) {
                QuotaEntity qe = new QuotaEntity();
                qe.setIdStr(Snowflake.nextIdStr());
                qe.setTenantId(tenantId);
                qe.setUserId(userId);
                qe.setTotalQuota(1_000_000L);
                qe.setUsedQuota(0L);
                quotaMapper.insert(qe);
                log.info("[ledger] 自动创建 quota: tenant={} user={}", tenantId, userId);
                rows = quotaMapper.consume(tenantId, userId, totalTokens);
            }
            if (rows == 0) {
                log.warn("[ledger] 额度不足: tenant={} user={} need={}", tenantId, userId, totalTokens);
                return false;
            }
        }

        log.info("[ledger] 扣减额度成功: tenant={} user={} tokens={}", tenantId, userId, totalTokens);
        return true;
    }

    /**
     * 查询用户额度
     */
    public QuotaEntity getQuota(Long tenantId, Long userId) {
        var q = new LambdaQueryWrapper<QuotaEntity>()
                .eq(QuotaEntity::getTenantId, tenantId)
                .eq(QuotaEntity::getUserId, userId);
        return quotaMapper.selectOne(q);
    }

    /**
     * 查询用户用量记录（分页）
     */
    public List<TokenUsageEntity> getUsageList(Long tenantId, Long userId, int page, int size) {
        var q = new LambdaQueryWrapper<TokenUsageEntity>()
                .eq(TokenUsageEntity::getTenantId, tenantId)
                .eq(TokenUsageEntity::getUserId, userId)
                .orderByDesc(TokenUsageEntity::getCreatedAt)
                .last("LIMIT " + ((page - 1) * size) + "," + size);
        return tokenUsageMapper.selectList(q);
    }

    /**
     * 查询会话用量汇总
     */
    public UsageSummary getSessionSummary(Long tenantId, Long userId, Long sessionId) {
        var q = new LambdaQueryWrapper<TokenUsageEntity>()
                .eq(TokenUsageEntity::getTenantId, tenantId)
                .eq(TokenUsageEntity::getUserId, userId)
                .eq(TokenUsageEntity::getSessionId, sessionId);
        List<TokenUsageEntity> list = tokenUsageMapper.selectList(q);

        long totalInput = 0, totalOutput = 0, totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (TokenUsageEntity u : list) {
            totalInput += u.getInputTokens();
            totalOutput += u.getOutputTokens();
            totalTokens += u.getTotalTokens();
            if (u.getCostAmount() != null) {
                totalCost = totalCost.add(u.getCostAmount());
            }
        }
        return new UsageSummary(list.size(), totalInput, totalOutput, totalTokens, totalCost);
    }

    /**
     * 用量汇总 DTO
     */
    public record UsageSummary(int recordCount, long inputTokens, long outputTokens,
                               long totalTokens, BigDecimal totalCost) {}
}
