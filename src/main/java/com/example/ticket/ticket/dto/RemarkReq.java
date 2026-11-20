package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 挂起 / 恢复 / 关闭 等只需要备注的动作共用 */
@Data
public class RemarkReq {

    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
}
