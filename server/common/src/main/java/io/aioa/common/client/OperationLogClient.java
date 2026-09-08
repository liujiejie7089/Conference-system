package io.aioa.common.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 操作留痕客户端 FR-H1
 * 各微服务通过 HTTP 调用 ledger-svc 的 /api/v1/ledger/oplog 接口记录关键操作。
 * 用户身份（tenantId/userId）由 ledger-svc 依据 token 从 UserContext 解析，避免跨用户伪造。
 */
@Slf4j
@Component
public class OperationLogClient {

    private static final String GATEWAY_URL = "http://localhost:8090";

    /**
     * 记录操作日志（尽力而为，失败不影响主流程）
     *
     * @param token  用户 JWT token（用于 ledger-svc 解析身份）
     * @param action 动作类型: login/create_session/send_message/upload_file/recharge/delete_session/switch_model
     * @param target 操作目标描述
     * @param result 1=成功 2=失败 3=逻辑删除
     * @param detail 额外详情
     */
    public void log(String token, String action, String target, int result, String detail) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", action);
            payload.put("target", target == null ? "" : target);
            payload.put("result", result);
            payload.put("detail", detail == null ? "" : detail);

            try (HttpResponse resp = HttpRequest.post(GATEWAY_URL + "/api/v1/ledger/oplog")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .body(JSONUtil.toJsonStr(payload))
                    .timeout(5000)
                    .execute()) {
                if (resp.getStatus() != 200) {
                    log.warn("[oplog] 留痕接口返回非 200: {}", resp.getStatus());
                }
            }
        } catch (Exception e) {
            log.warn("[oplog] 记录失败: action={} err={}", action, e.getMessage());
        }
    }
}
