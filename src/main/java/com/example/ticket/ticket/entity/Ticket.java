package com.example.ticket.ticket.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Ticket {
    private Long id;
    private String ticketNo;
    private String title;
    private String content;
    private Integer typeId;
    private Integer priority;
    private Integer status;
    private Long creatorId;
    /** 冗余快照：列表的数据权限过滤靠它，避免每次 join sys_user */
    private Long creatorDept;
    private Long assigneeId;
    private Long assigneeDept;
    private LocalDateTime assignTime;
    private LocalDateTime finishTime;
    private LocalDateTime closeTime;
    private LocalDateTime deadline;
    private Integer timeoutFlag;
    /** 乐观锁版本号，CAS 更新的第二个条件 */
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
