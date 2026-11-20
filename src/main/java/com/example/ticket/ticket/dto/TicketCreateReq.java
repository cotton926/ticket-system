package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketCreateReq {

    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题不能超过 120 字")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 5000, message = "描述不能超过 5000 字")
    private String content;

    @NotNull(message = "请选择工单类型")
    private Integer typeId;

    @NotNull(message = "请选择优先级")
    @Min(value = 1, message = "优先级取值 1-4")
    @Max(value = 4, message = "优先级取值 1-4")
    private Integer priority;

    /*
     * 注意：这里刻意不接收 creatorId / status / creatorDept / deadline。
     * 凡是能由服务端从登录态和状态机推导出来的字段，一律不接受客户端传入 ——
     * 这比"接进来再校验"更彻底，从根上消掉一类参数篡改攻击。
     */
}
