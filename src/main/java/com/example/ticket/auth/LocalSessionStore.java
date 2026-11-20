package com.example.ticket.auth;

import com.example.ticket.common.context.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内会话（单机部署 / 本地开发默认）。
 * <p>重启即全员掉线，这是刻意的取舍：本地开发不需要 Redis 也能完整跑通全流程。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.auth.store", havingValue = "local", matchIfMissing = true)
public class LocalSessionStore implements SessionStore {

    private record Entry(LoginUser user, long expireAt) {
    }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(String token, LoginUser user, long ttlMinutes) {
        sessions.put(token, new Entry(user, System.currentTimeMillis() + ttlMinutes * 60_000));
    }

    @Override
    public LoginUser get(String token) {
        if (token == null) {
            return null;
        }
        Entry e = sessions.get(token);
        if (e == null) {
            return null;
        }
        if (e.expireAt() < System.currentTimeMillis()) {
            sessions.remove(token, e);
            return null;
        }
        return e.user();
    }

    @Override
    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    @Scheduled(fixedDelay = 120_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expireAt() < now) {
                it.remove();
            }
        }
    }
}
