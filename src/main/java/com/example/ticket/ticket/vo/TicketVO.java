package com.example.ticket.ticket.vo;

import com.example.ticket.ticket.enums.TicketStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单列表项。
 * <p>只含列表需要的字段 —— 不查 content（TEXT，平均 800 字节），
 * 20 条就能省下 16KB 的无用网络与内存开销，也让索引更容易被覆盖。</p>
 */
@Data
public class TicketVO {

    private Long id;
    private String ticketNo;
    private String title;
    private Integer typeId;
    private Integer priority;
    private Integer status;
    private Long creatorId;
    private String creatorName;
    private Long assigneeId;
    private String assigneeName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    @Schema(description = "是否已超 SLA")
    private Boolean timeout;

    private List<String> actions;

    public String getStatusDesc() {
        return status == null ? null : TicketStatus.of(status).getDesc();
    }

    public String getPriorityDesc() {
        if (priority == null) {
            return null;
        }
        return switch (priority) {
            case 1 -> "紧急";
            case 2 -> "高";
            case 3 -> "普通";
            case 4 -> "低";
            default -> "未知";
        };
    }
}
