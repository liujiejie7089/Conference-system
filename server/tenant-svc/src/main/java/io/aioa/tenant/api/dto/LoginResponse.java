package io.aioa.tenant.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String tokenType;
    private long expiresInSeconds;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String username;
        private String displayName;
        private String avatar;
        private Long tenantId;
    }
}
