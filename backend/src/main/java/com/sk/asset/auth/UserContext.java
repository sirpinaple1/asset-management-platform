package com.sk.asset.auth;

/**
 * 当前请求用户上下文（ThreadLocal）。TokenAuthFilter 在鉴权成功后写入、请求结束清理。
 * 业务层（service/controller）通过本类读取当前用户，不直连 Spring Security API。
 */
public final class UserContext {

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthContext context) {
        HOLDER.set(context);
    }

    /** @return 当前认证上下文；未认证（filter 未放行）返回 null */
    public static AuthContext get() {
        return HOLDER.get();
    }

    /** @return 当前认证上下文；未认证抛 BusinessException(401) */
    public static AuthContext require() {
        AuthContext context = HOLDER.get();
        if (context == null) {
            throw new com.sk.asset.common.BusinessException(401, "未认证");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
