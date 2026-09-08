package io.aioa.gateway.filter;

import io.aioa.common.auth.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全局鉴权过滤器
 * - 健康检查、登录放行
 * - 其他路由必须带 Authorization
 * - 解析后注入 X-User-Id / X-Tenant-Id / X-Username 头到下游
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);
    private static final String[] WHITELIST = {
            "/api/v1/auth/sso",
            "/api/v1/auth/refresh",
            "/actuator"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getPath().value();

        // trace id
        String traceId = req.getHeaders().getFirst("X-Trace-Id");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        ServerHttpRequest req2 = req.mutate()
                .header("X-Trace-Id", traceId)
                .build();

        // 白名单
        for (String w : WHITELIST) {
            if (path.startsWith(w)) {
                return chain.filter(exchange.mutate().request(req2).build());
            }
        }

        String auth = req.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing token");
        }
        try {
            Claims c = JwtUtil.parse(auth.substring(7));
            ServerHttpRequest authed = req2.mutate()
                    .header("X-User-Id", String.valueOf(JwtUtil.uid(c)))
                    .header("X-Tenant-Id", String.valueOf(JwtUtil.tid(c)))
                    .header("X-Username", JwtUtil.usr(c))
                    .build();
            return chain.filter(exchange.mutate().request(authed).build());
        } catch (Exception e) {
            log.warn("[auth] invalid token: {}", e.getMessage());
            return unauthorized(exchange, "invalid token");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        byte[] body = ("{\"code\":401,\"message\":\"" + msg + "\"}").getBytes();
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body))
        );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
