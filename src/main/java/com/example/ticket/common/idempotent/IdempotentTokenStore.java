package com.example.ticket.common.idempotent;

/**
 * 幂等令牌存储（第 1 层防线）。
 *
 * <p>抽象成接口是因为"原子消费"这个语义在两种存储下都能满足：
 * Redis 用 DEL（返回 1 即抢到），进程内用 ConcurrentHashMap.remove（返回旧值即抢到）。
 * <b>绝不能实现成 get + remove 两步</b> —— 中间有窗口期，两个并发请求会同时通过。</p>
 */
public interface IdempotentTokenStore {

    /** 下发令牌，返回可直接给前端的字符串 */
    String issue();

    /**
     * 原子消费令牌。
     *
     * @return true 表示本次抢到（首次请求，应放行）；false 表示令牌不存在或已被消费（重复提交）
     */
    boolean consume(String token);

    /** 仅用于诊断/测试，不参与并发控制 */
    boolean isValid(String token);
}
