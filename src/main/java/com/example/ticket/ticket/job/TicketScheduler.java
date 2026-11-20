package com.example.ticket.ticket.job;

import com.example.ticket.common.idempotent.IdempotentRecordMapper;
import com.example.ticket.ticket.entity.Ticket;
import com.example.ticket.ticket.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 周期性任务：超时标记 + 幂等记录清理。
 *
 * <p>超时扫描刻意**只标记不通知**：通知要发站内信/企微，失败会拖垮整个扫描任务，
 * 应当异步解耦（V2 用事件 + 重试）。先把"哪些单超时了"这个事实落库，
 * 报表和后续通知都有依据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketScheduler {

    private static final int SCAN_BATCH = 200;
    private static final int KEEP_DAYS = 7;

    private final TicketMapper ticketMapper;
    private final IdempotentRecordMapper idempotentRecordMapper;

    /** 每 10 分钟扫一次；limit 控制单批，避免一次锁住太多行 */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void markTimeoutTickets() {
        List<Ticket> candidates = ticketMapper.selectTimeoutCandidates(LocalDateTime.now(), SCAN_BATCH);
        if (candidates.isEmpty()) {
            return;
        }
        List<Long> ids = candidates.stream().map(Ticket::getId).toList();
        int affected = ticketMapper.markTimeout(ids);
        log.info("timeout scan: found={}, marked={}", ids.size(), affected);
    }

    /** 每天凌晨 3:30 清理 7 天前的幂等记录：它只用于短窗口去重，不需要长期留存 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanIdempotentRecords() {
        LocalDateTime before = LocalDateTime.now().minusDays(KEEP_DAYS);
        int total = 0;
        // 分批删，避免一次大事务产生长锁等待
        for (int i = 0; i < 20; i++) {
            int deleted = idempotentRecordMapper.deleteBefore(before);
            total += deleted;
            if (deleted < 5000) {
                break;
            }
        }
        if (total > 0) {
            log.info("idempotent records cleaned: {}", total);
        }
    }
}
