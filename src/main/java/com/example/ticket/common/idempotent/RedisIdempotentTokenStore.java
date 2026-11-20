package com.example.ticket.common.idempotent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 令牌存储（多实例部署时启用：{@code app.idempotent.store=redis}）。
 *
 * <p>关键在于 {@code DEL} 的返回值：Redis 的 DEL 是单命令原子的，返回 1 表示"这次是我删掉的"，
 * 天然具备 CAS 语义。若先 GET 再 DEL，两步之间存在窗口期，两个并发请求可能都读到值而都放行 ——
 * 这是幂等方案最容易被追问倒的一点。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.idempotent.store", havingValue = "redis")
public class RedisIdempotentTokenStore implements IdempotentTokenStore {

    private static final String PREFIX = "idem:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisIdempotentTokenStore(StringRedisTemplate redis,
                                     @Value("${app.idempotent.token-ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public String issue() {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(PREFIX + token, "1", ttl);
        return token;
    }

    @Override
    public boolean consume(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            // DEL 返回 Boolean：true = 本次删掉了（抢到），false/nil = 不存在或已被消费
            return Boolean.TRUE.equals(redis.delete(PREFIX + token));
        } catch (Exception e) {
            // 降级策略必须显式设计：Redis 故障时放行，由数据库唯一索引兜底，
            // 否则一次 Redis 抖动就会让全站提交功能不可用。
            log.warn("redis unavailable when consuming idempotent token, degrade to pass. token={}", token, e);
            return true;
        }
    }

    @Override
    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + token));
    }
}
