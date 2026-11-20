package com.example.ticket.common.context;

import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;

/**
 * 当前请求的登录态。
 * <p>用 ThreadLocal 而非方法到处传 userId：Controller/Service 的每个方法都加一个 operatorId 参数
 * 会污染签名，而且容易漏传。注意**必须在 finally 里 clear**，否则线程池复用会串号（水平越权事故）。
 * 清理动作放在 AuthInterceptor.afterCompletion 里统一做。</p>
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

    /** 取当前用户，未登录直接抛 20001 —— 业务代码里不需要再判 null */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireUserId() {
        return require().getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
