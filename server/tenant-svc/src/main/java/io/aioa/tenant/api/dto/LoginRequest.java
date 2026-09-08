package io.aioa.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    private String username;
    @NotBlank
    private String password;
    /** 租户编码，默认 default */
    private String tenantCode = "default";
}
