package io.aioa.ledger.service;

import io.aioa.common.id.Snowflake;
import io.aioa.ledger.repo.entity.OperationLogEntity;
import io.aioa.ledger.repo.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作留痕服务 FR-H1
 * 被其他服务通过 HTTP 调用 / 或自身服务内调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 记录操作日志
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param action 动作类型: login/create_session/send_message/upload_file/recharge/delete_session/switch_model
     * @param target 操作目标描述
     * @param result 1=成功 2=失败 3=逻辑删除
     * @param detail 额外详情
     */
    public void log(Long tenantId, Long userId, String action, String target, int result, String detail) {
        try {
            OperationLogEntity e = new OperationLogEntity();
            e.setIdStr(Snowflake.nextIdStr());
            e.setTenantId(tenantId);
            e.setUserId(userId);
            e.setAction(action);
            e.setTarget(target == null ? "" : target);
            e.setResult(result);
            e.setDetail(detail);
            operationLogMapper.insert(e);
        } catch (Exception ex) {
            log.warn("[oplog] 记录失败: {}", ex.getMessage());
        }
    }
}
