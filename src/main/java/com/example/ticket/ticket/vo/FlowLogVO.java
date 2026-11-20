package com.example.ticket.ticket.vo;

import com.example.ticket.ticket.enums.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流转日志项（时间线）。
 * <p>{@code operatorName} 在写入时就冗余落库，所以整页时间线只需要 1 次查询，
 * 不需要为每条日志回查 sys_user（否则 20 条日志就是 21 次查询的 N+1）。</p>
 */
@Data
public class FlowLogVO {

    private Long id;
    private String action;
    private String actionDesc;
    private Integer fromStatus;
    private Integer toStatus;
    private Long operatorId;
    private String operatorName;
    private String remark;

    private LocalDateTime createTime;

    public String getFromStatusDesc() {
        return fromStatus == null ? null : TicketStatus.of(fromStatus).getDesc();
    }

    public String getToStatusDesc() {
        return toStatus == null ? null : TicketStatus.of(toStatus).getDesc();
    }
}
