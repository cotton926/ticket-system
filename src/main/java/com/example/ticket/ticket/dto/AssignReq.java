package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssignReq {

    @NotNull(message = "请选择处理人")
    private Long assigneeId;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
