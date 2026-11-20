package com.example.ticket.ticket.service;

import com.example.ticket.ticket.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工单号生成器：TK + yyyyMMdd + 6 位当日序列。
 *
 * <p><b>为什么不用 {@code SELECT MAX(ticket_no)+1}</b>：并发下两个请求会读到同一个 MAX
 * 从而生成同一个号（必然重复），而且在高水位时这条 SQL 还要扫索引尾部。这里用进程内
 * 计数器，只在每天首次调用时查一次 MAX 恢复起点。</p>
 *
 * <p>多实例部署时两个实例会各自从 MAX 起算而撞号 —— 真正的防线是 {@code uk_ticket_no}
 * 唯一索引，Service 捕获冲突后重试一次即可（见 insertWithRetry）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketNoGenerator {

    private static final String PREFIX = "TK";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long MAX_SEQ = 999_999L;

    private final TicketMapper ticketMapper;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public String next() {
        String day = LocalDate.now().format(DAY);
        String prefix = PREFIX + day;
        AtomicLong counter = counters.computeIfAbsent(day, d -> new AtomicLong(seed(prefix)));
        long seq = counter.incrementAndGet();
        if (seq > MAX_SEQ) {
            throw new IllegalStateException("当日工单号已用尽：" + day);
        }
        return prefix + String.format("%06d", seq);
    }

    private long seed(String prefix) {
        try {
            String max = ticketMapper.selectMaxTicketNo(prefix);
            if (max == null || max.length() <= prefix.length()) {
                return 0L;
            }
            long v = Long.parseLong(max.substring(prefix.length()));
            log.info("ticket no counter seeded, prefix={}, start={}", prefix, v);
            return v;
        } catch (NumberFormatException e) {
            // 历史脏数据（比如人工插入过非标准工单号）不能挡住当天的下单
            log.warn("bad ticket_no format while seeding, prefix={}", prefix, e);
            return 0L;
        }
    }
}
