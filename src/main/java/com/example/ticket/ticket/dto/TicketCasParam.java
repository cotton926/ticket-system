package com.example.ticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CAS 更新参数。
 *
 * <p>把"状态变更 + 处理人变更 + 时间戳"收敛到一条 UPDATE 里：WHERE 同时带上
 * fromStatus 和 version 两个前置条件，影响行数为 0 就说明有人抢先改过。
 * 一条语句完成"检查并设置"，避免"先 select 判断再 update"的竞态窗口。</p>
 */
@Data
@Builder
public class TicketCasParam {

    private Long id;

    private Integer fromStatus;

    private Integer toStatus;

    private Integer version;

    private Long assigneeId;

    private Long assigneeDept;

    private LocalDateTime finishTime;

    private LocalDateTime closeTime;
}
