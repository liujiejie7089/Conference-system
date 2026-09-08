package io.aioa.ledger.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import io.aioa.common.id.Snowflake;
import io.aioa.ledger.api.dto.MemberPlanVo;
import io.aioa.ledger.api.dto.RechargeOrderVo;
import io.aioa.ledger.repo.entity.QuotaEntity;
import io.aioa.ledger.repo.entity.RechargeOrderEntity;
import io.aioa.ledger.repo.mapper.QuotaMapper;
import io.aioa.ledger.repo.mapper.RechargeOrderMapper;
import io.aioa.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会员与充值接口
 * 额度用尽三分支引导：① 升级会员 ② 充值词元包 ③ 切换免费模型
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final LedgerService ledgerService;
    private final QuotaMapper quotaMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final io.aioa.ledger.service.OperationLogService operationLogService;

    /** 词元包商品 */
    private static final List<Map<String, Object>> TOKEN_PACKS = List.of(
            Map.of("code", "token_pack_s", "name", "10万词元包", "tokens", 100000L, "price", new BigDecimal("9.90")),
            Map.of("code", "token_pack_m", "name", "50万词元包", "tokens", 500000L, "price", new BigDecimal("39.90")),
            Map.of("code", "token_pack_l", "name", "200万词元包", "tokens", 2000000L, "price", new BigDecimal("129.00"))
    );

    /** 会员套餐 */
    private static final List<Map<String, Object>> MEMBER_PLANS = List.of(
            Map.of("code", "member_silver", "name", "银牌会员", "level", 2, "tokens", 500000L, "price", new BigDecimal("99.00"),
                    "benefits", List.of("每月50万词元", "优先排队", "标准响应")),
            Map.of("code", "member_gold", "name", "金牌会员", "level", 3, "tokens", 2000000L, "price", new BigDecimal("299.00"),
                    "benefits", List.of("每月200万词元", "极速响应", "专属模型", "优先审批"))
    );

    /**
     * 获取会员套餐列表
     */
    @GetMapping("/plans")
    public R<MemberPlanVo> plans() {
        UserContext.CurrentUser user = UserContext.get();
        QuotaEntity q = ledgerService.getQuota(user.tenantId(), user.userId());
        int currentLevel = q != null && q.getMemberLevel() != null ? q.getMemberLevel() : 1;

        return R.ok(MemberPlanVo.builder()
                .currentLevel(currentLevel)
                .currentLevelName(levelName(currentLevel))
                .tokenPacks(TOKEN_PACKS)
                .memberPlans(MEMBER_PLANS)
                .build());
    }

    /**
     * 充值词元包
     * POST /api/v1/membership/recharge  { "productCode": "token_pack_m" }
     */
    @PostMapping("/recharge")
    public R<RechargeOrderVo> recharge(@RequestBody Map<String, String> body) {
        String productCode = body.get("productCode");
        Map<String, Object> pack = findPack(productCode);
        if (pack == null) {
            return R.fail("词元包不存在: " + productCode);
        }

        UserContext.CurrentUser user = UserContext.get();
        long tokens = (Long) pack.get("tokens");
        BigDecimal price = (BigDecimal) pack.get("price");

        // 模拟支付成功
        RechargeOrderEntity order = createOrder(user, 1, productCode, (String) pack.get("name"), tokens, price);
        quotaMapper.recharge(user.tenantId(), user.userId(), tokens);
        markPaid(order);
        operationLogService.log(user.tenantId(), user.userId(), "recharge",
                (String) pack.get("name"), 1, tokens + " tokens");

        log.info("[membership] 充值成功: user={} product={} tokens={}", user.userId(), productCode, tokens);
        return R.ok(toOrderVo(order));
    }

    /**
     * 升级会员
     * POST /api/v1/membership/upgrade  { "productCode": "member_silver" }
     */
    @PostMapping("/upgrade")
    public R<RechargeOrderVo> upgrade(@RequestBody Map<String, String> body) {
        String productCode = body.get("productCode");
        Map<String, Object> plan = findPlan(productCode);
        if (plan == null) {
            return R.fail("会员套餐不存在: " + productCode);
        }

        UserContext.CurrentUser user = UserContext.get();
        int level = (Integer) plan.get("level");
        long bonus = (Long) plan.get("tokens");
        BigDecimal price = (BigDecimal) plan.get("price");

        RechargeOrderEntity order = createOrder(user, 2, productCode, (String) plan.get("name"), bonus, price);
        quotaMapper.upgradeMember(user.tenantId(), user.userId(), level, bonus);
        markPaid(order);
        operationLogService.log(user.tenantId(), user.userId(), "upgrade",
                (String) plan.get("name"), 1, "level=" + level + " bonus=" + bonus);

        log.info("[membership] 升级会员成功: user={} level={} bonus={}", user.userId(), level, bonus);
        return R.ok(toOrderVo(order));
    }

    /**
     * 切换模型（免费模型分支）
     * POST /api/v1/membership/model  { "model": "deepseek-free" }
     */
    @PostMapping("/model")
    public R<Map<String, Object>> switchModel(@RequestBody Map<String, String> body) {
        String model = body.get("model");
        if (!"deepseek-chat".equals(model) && !"deepseek-free".equals(model)) {
            return R.fail("不支持的模型: " + model);
        }

        UserContext.CurrentUser user = UserContext.get();
        // 免费模型不扣额度：自动重置今日免费额度（演示）
        if ("deepseek-free".equals(model)) {
            QuotaEntity q = ledgerService.getQuota(user.tenantId(), user.userId());
            if (q != null && q.getTotalQuota() - q.getUsedQuota() <= 0) {
                quotaMapper.recharge(user.tenantId(), user.userId(), 5000L);
                log.info("[membership] 免费模型发放5000额度: user={}", user.userId());
            }
        }
        quotaMapper.switchModel(user.tenantId(), user.userId(), model);
        operationLogService.log(user.tenantId(), user.userId(), "switch_model", model, 1, null);

        return R.ok(Map.of("model", model, "isFree", "deepseek-free".equals(model)));
    }

    /**
     * 充值/升级订单列表
     */
    @GetMapping("/orders")
    public R<List<RechargeOrderVo>> orders(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        UserContext.CurrentUser user = UserContext.get();
        var q = new LambdaQueryWrapper<RechargeOrderEntity>()
                .eq(RechargeOrderEntity::getTenantId, user.tenantId())
                .eq(RechargeOrderEntity::getUserId, user.userId())
                .orderByDesc(RechargeOrderEntity::getCreatedAt)
                .last("LIMIT " + ((page - 1) * size) + "," + size);
        List<RechargeOrderEntity> list = rechargeOrderMapper.selectList(q);
        return R.ok(list.stream().map(this::toOrderVo).toList());
    }

    // ==================== helpers ====================

    private Map<String, Object> findPack(String code) {
        return TOKEN_PACKS.stream().filter(p -> p.get("code").equals(code)).findFirst().orElse(null);
    }

    private Map<String, Object> findPlan(String code) {
        return MEMBER_PLANS.stream().filter(p -> p.get("code").equals(code)).findFirst().orElse(null);
    }

    private RechargeOrderEntity createOrder(UserContext.CurrentUser user, int type, String code,
                                            String name, long tokens, BigDecimal price) {
        RechargeOrderEntity order = new RechargeOrderEntity();
        order.setIdStr(Snowflake.nextIdStr());
        order.setTenantId(user.tenantId());
        order.setUserId(user.userId());
        order.setType(type);
        order.setProductCode(code);
        order.setProductName(name);
        order.setTokenAmount(tokens);
        order.setAmount(price);
        order.setStatus(1);
        order.setPayMethod("alipay");
        rechargeOrderMapper.insert(order);
        return order;
    }

    private void markPaid(RechargeOrderEntity order) {
        order.setStatus(2);
        order.setTradeNo("TP" + System.currentTimeMillis());
        order.setPaidAt(LocalDateTime.now());
        rechargeOrderMapper.updateById(order);
    }

    private RechargeOrderVo toOrderVo(RechargeOrderEntity e) {
        return RechargeOrderVo.builder()
                .id(e.getIdStr())
                .type(e.getType())
                .typeName(e.getType() == 1 ? "词元包" : "会员升级")
                .productName(e.getProductName())
                .tokenAmount(e.getTokenAmount())
                .amount(e.getAmount())
                .status(e.getStatus())
                .statusName(statusName(e.getStatus()))
                .createdAt(e.getCreatedAt())
                .build();
    }

    private String levelName(int level) {
        return switch (level) {
            case 2 -> "银牌会员";
            case 3 -> "金牌会员";
            default -> "铜牌会员";
        };
    }

    private String statusName(int status) {
        return switch (status) {
            case 1 -> "待支付";
            case 2 -> "已支付";
            case 3 -> "已取消";
            case 4 -> "已退款";
            default -> "未知";
        };
    }
}
