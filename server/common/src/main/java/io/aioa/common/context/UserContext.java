package io.aioa.common.context;

/**
 * 当前请求用户上下文（由网关或拦截器写入）
 */
public class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record CurrentUser(long userId, long tenantId, String username,
                              String[] roles, String token) {
    }
}
