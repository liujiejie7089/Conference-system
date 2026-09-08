package io.aioa.common.web;

import io.aioa.common.api.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.aioa.common.auth.JwtUtil;
import io.aioa.common.context.UserContext;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * JWT 拦截器：解析 Authorization 头并写入 UserContext
 * 由各微服务注册到 WebMvcConfigurer 中
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        // 健康检查 / 错误放行
        String uri = req.getRequestURI();
        if (uri.startsWith("/actuator") || uri.contains("/public/")) {
            return true;
        }

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauth(resp, "missing token");
            return false;
        }
        String token = header.substring(7);
        try {
            Claims c = JwtUtil.parse(token);
            UserContext.set(new UserContext.CurrentUser(
                    JwtUtil.uid(c),
                    JwtUtil.tid(c),
                    JwtUtil.usr(c),
                    new String[0],
                    token
            ));
            // 写 trace_id
            if (req.getHeader("X-Trace-Id") == null) {
                resp.setHeader("X-Trace-Id", UUID.randomUUID().toString().replace("-", ""));
            }
            return true;
        } catch (Exception e) {
            writeUnauth(resp, "invalid token");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauth(HttpServletResponse resp, String msg) throws Exception {
        resp.setStatus(HttpStatus.UNAUTHORIZED.value());
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":401,\"message\":\"" + msg + "\"}");
    }
}
