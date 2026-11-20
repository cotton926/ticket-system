package com.example.ticket.common.idempotent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内令牌存储（默认实现，开箱即用，不依赖 Redis）。
 *
 * <p>原子性说明：{@code ConcurrentHashMap.remove(key, value)} 与 {@code remove(key)} 的返回值
 * 都是"被移除的旧值"，且整个 remove 是原子的 —— 两个线程并发 remove 同一个 key，
 * 只有一个能拿到非 null 返回值。这与 Redis 的 DEL 返回 1 是同一套 CAS 语义，
 * 所以单机场景下本实现与 Redis 实现等价。</p>
 *
 * <p>局限：多实例部署时令牌不共享，A 实例下发、B 实例校验会失败。此时把
 * {@code app.idempotent.store} 改为 {@code redis} 即可，业务代码零改动。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.idempotent.store", havingValue = "local", matchIfMissing = true)
public class LocalIdempotentTokenStore implements IdempotentTokenStore {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    private final long ttlMillis;

    public LocalIdempotentTokenStore(@Value("${app.idempotent.token-ttl-seconds:300}") long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000;
    }

    @Override
    public String issue() {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, System.currentTimeMillis() + ttlMillis);
        return token;
    }

    @Override
    public boolean consume(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        // 单次 remove 的返回值即"是否抢到"，不拆成 get + remove
        Long expireAt = tokens.remove(token);
        return expireAt != null && expireAt >= System.currentTimeMillis();
    }

    @Override
    public boolean isValid(String token) {
        // 与 consume 保持同一套入参契约：ConcurrentHashMap.get(null) 会抛 NPE，
        // 不能在"查一下令牌还在不在"这种只读操作上把 500 抛给调用方
        if (token == null || token.isEmpty()) {
            return false;
        }
        Long expireAt = tokens.get(token);
        return expireAt != null && expireAt >= System.currentTimeMillis();
    }

    /** 兜底清理：用户拿了令牌却不提交，会一直占内存 */
    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        int before = tokens.size();
        Iterator<Map.Entry<String, Long>> it = tokens.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now) {
                it.remove();
            }
        }
        int removed = before - tokens.size();
        if (removed > 0) {
            log.debug("evicted {} expired idempotent tokens, remain {}", removed, tokens.size());
        }
    }

    int size() {
        return tokens.size();
    }
}
