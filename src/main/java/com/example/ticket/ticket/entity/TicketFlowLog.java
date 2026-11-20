package com.example.ticket.ticket.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketFlowLog {
    private Long id;
    private Long ticketId;
    private String action;
    private String actionDesc;
    private Integer fromStatus;
    private Integer toStatus;
    private Long operatorId;
    /** 冗余快照：追溯页不再回查 sys_user，避免 N+1 */
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
