package com.example.ticket.stat.service;

import com.example.ticket.stat.mapper.StatMapper;
import com.example.ticket.stat.vo.StatOverviewVO;
import com.example.ticket.ticket.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatService {

    private static final int TOP_LOAD_LIMIT = 10;

    private final StatMapper statMapper;

    public StatOverviewVO overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        List<StatOverviewVO.StatusCount> byStatus = new ArrayList<>();
        for (Map<String, Object> row : statMapper.countByStatus()) {
            Integer code = ((Number) row.get("status")).intValue();
            Long cnt = ((Number) row.get("cnt")).longValue();
            byStatus.add(new StatOverviewVO.StatusCount(code, TicketStatus.of(code).getDesc(), cnt));
        }

        List<StatOverviewVO.AssigneeLoad> loads = new ArrayList<>();
        for (Map<String, Object> row : statMapper.topAssigneeLoad(TOP_LOAD_LIMIT)) {
            loads.add(new StatOverviewVO.AssigneeLoad(
                    ((Number) row.get("assigneeId")).longValue(),
                    row.get("assigneeName") == null ? null : String.valueOf(row.get("assigneeName")),
                    ((Number) row.get("cnt")).longValue()));
        }

        StatOverviewVO vo = new StatOverviewVO();
        vo.setTotal(statMapper.countAll());
        vo.setTodayCreated(statMapper.countCreatedBetween(todayStart, todayStart.plusDays(1)));
        vo.setTimeoutCount(statMapper.countTimeout());
        Double avg = statMapper.avgCostMinutes(todayStart.minusDays(30));
        vo.setAvgCostMinutes(avg == null ? null : avg.longValue());
        vo.setByStatus(byStatus);
        vo.setTopLoad(loads);
        return vo;
    }
}
