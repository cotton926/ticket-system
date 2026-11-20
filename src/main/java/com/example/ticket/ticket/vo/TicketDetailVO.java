package com.example.ticket.ticket.vo;

import com.example.ticket.ticket.entity.TicketComment;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单详情。
 * <p>详情页固定 2 次查询：主表按主键 + 流转日志按 ticket_id（走 idx_ticket_ctime）。
 * 不因日志条数增加而增加查询次数 —— 这是 ADR-3（冗余 operator_name）兑现的地方。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketDetailVO extends TicketVO {

    private String content;

    private String assigneeDeptName;

    private String creatorDeptName;

    private LocalDateTime finishTime;

    private LocalDateTime closeTime;

    private LocalDateTime assignTime;

    private List<FlowLogVO> flows;

    private List<TicketComment> comments;

    /** 当前用户在这个工单上能做的动作，前端据此渲染按钮（真正的拦截仍在后端） */
    private List<String> availableActions;
}
