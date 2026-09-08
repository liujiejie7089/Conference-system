package io.aioa.agent.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Tool 执行结果
 */
@Data
@Builder
public class ToolExecuteResponse {
    private boolean success;
    private int httpStatus;
    private String body;        // 业务系统返回的响应体
    private String error;      // 失败原因
}
