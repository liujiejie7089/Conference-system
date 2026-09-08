package io.aioa.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具：颁发与解析
 * 密钥从环境变量 AIOA_JWT_SECRET 读取，默认值仅本地开发用
 */
public final class JwtUtil {

    /** 本地默认密钥，生产请通过环境变量覆盖 */
    private static final String DEFAULT_SECRET = "aioa-local-dev-secret-key-please-change-in-production-32+chars";

    private static SecretKey key() {
        String secret = System.getenv().getOrDefault("AIOA_JWT_SECRET", DEFAULT_SECRET);
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String issue(long userId, long tenantId, String username, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(Map.of(
                        "uid", userId,
                        "tid", tenantId,
                        "usr", username
                ))
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key())
                .compact();
    }

    public static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static long uid(Claims c) {
        return ((Number) c.get("uid")).longValue();
    }

    public static long tid(Claims c) {
        return ((Number) c.get("tid")).longValue();
    }

    public static String usr(Claims c) {
        return (String) c.get("usr");
    }

    private JwtUtil() {
    }
}
