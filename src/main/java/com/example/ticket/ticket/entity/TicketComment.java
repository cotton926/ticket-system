package com.example.ticket.ticket.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketComment {
    private Long id;
    private Long ticketId;
    private Long userId;
    private String userName;
    private String content;
    /** 1 内部备注（提交人不可见） */
    private Integer isInternal;
    private LocalDateTime createTime;
}
