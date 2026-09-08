package io.aioa.common.api;

/**
 * 业务错误码定义
 */
public enum ErrorCode {

    OK(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    QUOTA_EXHAUSTED(402, "额度已耗尽"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    AGENT_TIMEOUT(510, "Agent 推理超时"),
    APPROVAL_REQUIRED(422, "需要人工审批");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
