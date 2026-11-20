package com.example.ticket.common.context;

/**
 * 幂等令牌上下文。
 *
 * <p>切面在"成功消费令牌"后把 token 放进这里，Service 用它写 {@code idempotent_record}
 * 作为数据库层兜底记录 —— 这样内存/Redis 层与 DB 层用的是**同一个幂等键**，
 * 不会出现"两层各用各的键、互相兜不住"的漏洞。</p>
 */
public final class IdempotentContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private IdempotentContext() {
    }

    public static void set(String token) {
        HOLDER.set(token);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
