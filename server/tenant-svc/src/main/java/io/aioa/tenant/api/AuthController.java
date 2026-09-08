package io.aioa.tenant.api;

import io.aioa.common.api.BizException;
import io.aioa.common.api.ErrorCode;
import io.aioa.common.api.R;
import io.aioa.common.auth.JwtUtil;
import io.aioa.tenant.api.dto.LoginRequest;
import io.aioa.tenant.api.dto.LoginResponse;
import io.aioa.tenant.repo.entity.UserEntity;
import io.aioa.tenant.repo.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一期 SSO 简化实现：用户名 + 密码 → 校验 → 颁发 JWT
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final long TTL_SECONDS = 7 * 24 * 3600L; // 7 天
    private static final long DEFAULT_TENANT_ID = 1L; // 默认租户 id

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/sso")
    public R<LoginResponse> sso(@Valid @RequestBody LoginRequest req) {
        UserEntity user = userMapper.findByTenantAndUsername(DEFAULT_TENANT_ID, req.getUsername());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "用户已被停用");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        String token = JwtUtil.issue(user.getId(), user.getTenantId(), user.getUsername(), TTL_SECONDS);
        log.info("[sso] login success uid={} name={}", user.getId(), user.getUsername());

        LoginResponse resp = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresInSeconds(TTL_SECONDS)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getIdStr())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .avatar(user.getAvatar())
                        .tenantId(user.getTenantId())
                        .build())
                .build();
        return R.ok(resp);
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh() {
        // 简化：前端重新走 sso 即可
        throw new BizException(ErrorCode.BAD_REQUEST, "请重新登录");
    }
}
