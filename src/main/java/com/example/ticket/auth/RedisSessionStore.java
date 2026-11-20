package com.example.ticket.auth;

import com.example.ticket.common.context.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 会话（多实例部署时启用：{@code app.auth.store=redis}）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.auth.store", havingValue = "redis")
public class RedisSessionStore implements SessionStore {

    private static final String PREFIX = "sess:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String token, LoginUser user, long ttlMinutes) {
        try {
            redis.opsForValue().set(PREFIX + token, objectMapper.writeValueAsString(user),
                    Duration.ofMinutes(ttlMinutes));
        } catch (Exception e) {
            throw new IllegalStateException("保存会话失败", e);
        }
    }

    @Override
    public LoginUser get(String token) {
        if (token == null) {
            return null;
        }
        String json = redis.opsForValue().get(PREFIX + token);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, LoginUser.class);
        } catch (Exception e) {
            log.warn("bad session json, token={}", token, e);
            return null;
        }
    }

    @Override
    public void remove(String token) {
        if (token != null) {
            redis.delete(PREFIX + token);
        }
    }
}
