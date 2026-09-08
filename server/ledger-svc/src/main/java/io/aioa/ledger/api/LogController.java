package io.aioa.ledger.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aioa.common.api.R;
import io.aioa.common.context.UserContext;
import io.aioa.common.id.Snowflake;
import io.aioa.ledger.repo.entity.FeedbackEntity;
import io.aioa.ledger.repo.entity.OperationLogEntity;
import io.aioa.ledger.repo.mapper.FeedbackMapper;
import io.aioa.ledger.repo.mapper.OperationLogMapper;
import io.aioa.ledger.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 操作留痕与反馈接口 FR-H1/H2/H4
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LogController {

    private final OperationLogMapper operationLogMapper;
    private final FeedbackMapper feedbackMapper;
    private final OperationLogService operationLogService;

    /**
     * 查询我的操作记录 FR-H2
     * GET /api/v1/ledger/logs?page=1&size=50
     */
    @GetMapping("/ledger/logs")
    public R<List<Map<String, Object>>> logs(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        UserContext.CurrentUser user = UserContext.get();
        var q = new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getTenantId, user.tenantId())
                .eq(OperationLogEntity::getUserId, user.userId())
                .orderByDesc(OperationLogEntity::getCreatedAt)
                .last("LIMIT " + ((page - 1) * size) + "," + size);
        List<OperationLogEntity> list = operationLogMapper.selectList(q);
        return R.ok(list.stream().map(this::toLogVo).toList());
    }

    /**
     * 写入操作留痕 FR-H1（供各微服务经网关调用）
     * POST /api/v1/ledger/oplog  { "action": "...", "target": "...", "result": 1, "detail": "..." }
     * 用户身份由 token 解析，不接受 body 中的 tenantId/userId（防跨用户伪造）
     */
    @PostMapping("/ledger/oplog")
    public R<Boolean> oplog(@RequestBody Map<String, Object> body) {
        UserContext.CurrentUser user = UserContext.get();
        String action = body.get("action") != null ? String.valueOf(body.get("action")) : "";
        String target = body.get("target") != null ? String.valueOf(body.get("target")) : "";
        int result = body.get("result") != null ? ((Number) body.get("result")).intValue() : 1;
        String detail = body.get("detail") != null ? String.valueOf(body.get("detail")) : "";

        operationLogService.log(user.tenantId(), user.userId(), action, target, result, detail);
        return R.ok(true);
    }

    /**
     * 提交反馈/纠错 FR-H4
     * POST /api/v1/ledger/feedback  { "type": 1, "content": "...", "messageId": null }
     */
    @PostMapping("/ledger/feedback")
    public R<Map<String, Object>> feedback(@RequestBody Map<String, Object> body) {
        UserContext.CurrentUser user = UserContext.get();
        int type = body.get("type") != null ? ((Number) body.get("type")).intValue() : 1;
        String content = (String) body.get("content");
        Long messageId = body.get("messageId") != null ? ((Number) body.get("messageId")).longValue() : null;

        if (content == null || content.isBlank()) {
            return R.fail("反馈内容不能为空");
        }

        FeedbackEntity e = new FeedbackEntity();
        e.setIdStr(Snowflake.nextIdStr());
        e.setTenantId(user.tenantId());
        e.setUserId(user.userId());
        e.setMessageId(messageId);
        e.setType(type);
        e.setContent(content.trim());
        e.setStatus(1);
        feedbackMapper.insert(e);

        // 同时记录到操作日志
        String[] typeNames = {"", "错误", "有害", "侵权"};
        operationLogService.log(user.tenantId(), user.userId(),
                "feedback", "type=" + type, 1, typeNames[type] + ": " + content.substring(0, Math.min(50, content.length())));

        log.info("[feedback] 提交成功: user={} type={} content={}", user.userId(), type, content.substring(0, Math.min(30, content.length())));
        return R.ok(Map.of("id", e.getIdStr(), "status", "已提交"));
    }

    private Map<String, Object> toLogVo(OperationLogEntity e) {
        String actionCn = switch (e.getAction()) {
            case "login" -> "登录";
            case "create_session" -> "发起会话";
            case "send_message" -> "发送消息";
            case "upload_file" -> "上传资料";
            case "recharge" -> "购买词元包";
            case "delete_session" -> "删除会话";
            case "switch_model" -> "切换模型";
            case "feedback" -> "提交反馈";
            case "upgrade" -> "升级会员";
            default -> e.getAction();
        };
        String statusText = switch (e.getResult()) {
            case 2 -> "失败";
            case 3 -> "逻辑删除";
            default -> "成功";
        };
        String statusClass = switch (e.getResult()) {
            case 2 -> "wait";
            case 3 -> "wait";
            default -> "ok";
        };
        return Map.of(
                "time", e.getCreatedAt() != null ? e.getCreatedAt().toString() : "",
                "action", actionCn + (e.getTarget() != null && !e.getTarget().isBlank() ? "（" + e.getTarget() + "）" : ""),
                "status", statusClass,
                "statusText", statusText
        );
    }
}
