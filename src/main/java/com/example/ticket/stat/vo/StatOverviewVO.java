package com.example.ticket.stat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class StatOverviewVO {

    private long total;
    private long todayCreated;
    private long timeoutCount;
    /** 近 30 天平均处理时长（分钟） */
    private Long avgCostMinutes;
    private List<StatusCount> byStatus;
    private List<AssigneeLoad> topLoad;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        private Integer status;
        private String statusDesc;
        private Long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssigneeLoad {
        private Long assigneeId;
        /** 处理人姓名；用户被删除时为空，前端兜底显示"用户#id" */
        private String assigneeName;
        private Long pending;
    }
}
