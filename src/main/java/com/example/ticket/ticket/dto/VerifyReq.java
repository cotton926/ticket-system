package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyReq {

    /** true=验收通过（→已关闭） false=验收驳回（→处理中） */
    @NotNull(message = "请选择验收结果")
    private Boolean pass;

    /** 驳回时必须填原因；通过时可为空 */
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
