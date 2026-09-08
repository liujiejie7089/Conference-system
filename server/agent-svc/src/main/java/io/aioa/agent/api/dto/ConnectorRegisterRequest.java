package io.aioa.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 连接器注册请求：提供业务系统 OpenAPI 文档地址
 */
@Data
public class ConnectorRegisterRequest {
    @NotBlank
    private String openApiUrl;   // 业务系统 OpenAPI 文档地址
    @NotBlank
    private String baseUrl;      // 业务系统基础地址（如 http://localhost:8092）
    @NotBlank
    private String authToken;    // 业务系统鉴权 Token
    private String systemName;   // 业务系统名称
}
