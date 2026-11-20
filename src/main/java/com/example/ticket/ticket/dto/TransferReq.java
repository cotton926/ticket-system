package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转派：只换处理人，不改变状态。
 * <p>目标状态为 null，走 Service 里的独立分支 —— 这是状态机里唯一的"状态不变"动作。</p>
 */
@Data
public class TransferReq {

    @NotNull(message = "请选择新的处理人")
    private Long assigneeId;

    @NotNull(message = "请填写转派原因")
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
