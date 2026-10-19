package com.renjie.inspection.security;

/**
 * 当前请求的登录用户上下文。
 *
 * <p>用 ThreadLocal 保存，Service 层不必层层传 userId 参数。
 * <b>注意：</b>{@link #clear()} 必须在请求结束时调用（见 {@link AuthInterceptor#afterCompletion}），
 * 否则线程池复用线程时会串号——这是 ThreadLocal 最经典的坑。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 取当前用户 ID；未登录时抛异常（正常情况下拦截器已保证登录） */
    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("当前请求没有登录上下文");
        }
        return user.getUserId();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
