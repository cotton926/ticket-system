package com.example.ticket.common.idempotent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 令牌存储的核心契约。
 *
 * <p>最关键的一条是 {@link #consume_concurrently_exactlyOneSucceeds()}：同一个令牌并发消费只能有
 * 1 个成功。这是"用 remove/DEL 的返回值判断，而不是 get 再 remove"的直接证据 ——
 * 若实现改成 get + remove，线程一多就会稳定出现 2 个以上成功。</p>
 *
 * <p>过期场景一律用负 TTL 构造（签发即过期），不写 {@code Thread.sleep}：
 * {@code consume} 判的是 {@code expireAt >= now}，ttl=0 时"同一毫秒内"仍算有效，
 * 靠 sleep 赌时间既慢又不稳。</p>
 */
class LocalIdempotentTokenStoreTest {

    @Test
    @DisplayName("签发的令牌是 32 位十六进制且互不重复")
    void issue_returnsUniqueHexToken() {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(300);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = store.issue();
            assertTrue(token.matches("[0-9a-f]{32}"), "非法令牌格式: " + token);
            assertTrue(seen.add(token), "令牌重复: " + token);
            assertTrue(store.isValid(token), "刚签发的令牌应当有效");
        }
    }

    @Test
    @DisplayName("令牌只能被消费一次")
    void consume_onlyOnce() {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(300);
        String token = store.issue();

        assertTrue(store.consume(token));
        assertFalse(store.consume(token));
        assertFalse(store.isValid(token));
    }

    @Test
    @DisplayName("不存在 / null / 空串的令牌直接失败，不能放行")
    void consume_invalidToken_fail() {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(300);
        assertFalse(store.consume("not-exists"));
        assertFalse(store.consume(null));
        assertFalse(store.consume(""));
    }

    @Test
    @DisplayName("isValid 对 null / 空串返回 false，不能抛 NPE")
    void isValid_nullOrBlank_false() {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(300);
        // 与 consume 的入参契约保持一致：底层 ConcurrentHashMap.get(null) 会抛 NPE
        assertFalse(store.isValid(null));
        assertFalse(store.isValid(""));
        assertFalse(store.isValid("not-exists"));
    }

    @Test
    @DisplayName("isValid 是只读的，不能消费掉令牌")
    void isValid_doesNotConsume() {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(300);
        String token = store.issue();

        assertTrue(store.isValid(token));
        assertTrue(store.isValid(token), "重复校验不应影响令牌状态");
        assertTrue(store.consume(token), "校验之后再消费仍应成功");
    }

    @Test
    @DisplayName("过期的令牌不能再消费")
    void consume_expiredToken_fail() {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(-1);
        String token = store.issue();

        assertFalse(store.isValid(token));
        assertFalse(store.consume(token));
    }

    @Test
    @Timeout(10)
    @DisplayName("16 线程并发消费同一令牌，只有 1 个成功")
    void consume_concurrently_exactlyOneSucceeds() throws Exception {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(300);
        String token = store.issue();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                // 保留 Future：任务里抛异常会在这里 throw 出来，
                // 而不是被 ExecutorService 吞掉、让用例误判为"0 个成功"以外的情况
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return store.consume(token);
                }));
            }
            startGate.countDown();

            int success = 0;
            for (Future<Boolean> f : futures) {
                if (Boolean.TRUE.equals(f.get(5, TimeUnit.SECONDS))) {
                    success++;
                }
            }
            assertEquals(1, success, "并发消费只能有 1 个成功，否则幂等形同虚设");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("清理任务只删过期令牌，未过期的一个都不能动")
    void evictExpired_removesOnlyExpired() {
        LocalIdempotentTokenStore expired = new LocalIdempotentTokenStore(-1);
        expired.issue();
        expired.issue();
        expired.issue();
        assertEquals(3, expired.size(), "过期不等于已删除，清理前应当仍在 map 里");

        expired.evictExpired();
        assertEquals(0, expired.size(), "过期令牌必须被清理，否则会持续占内存");

        LocalIdempotentTokenStore alive = new LocalIdempotentTokenStore(300);
        alive.issue();
        alive.issue();
        alive.evictExpired();
        assertEquals(2, alive.size(), "未过期令牌被误删，用户提交会全部失败");
    }

    @Test
    @DisplayName("空 map 上跑清理任务不应抛异常")
    void evictExpired_emptyStore_ok() {
        LocalIdempotentTokenStore store = new LocalIdempotentTokenStore(300);
        store.evictExpired();
        assertEquals(0, store.size());
    }
}
