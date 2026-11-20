package com.example.ticket.ticket.entity;

import lombok.Data;

@Data
public class TicketType {
    private Integer id;
    private String name;
    private Integer parentId;
    /** SLA 时长（小时），提交时用于算 deadline */
    private Integer slaHours;
    private Integer status;
}
