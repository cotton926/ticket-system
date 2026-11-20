package com.example.ticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentReq {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论不能超过 2000 字")
    private String content;

    /** true=内部备注，提交人不可见 */
    private Boolean internal = Boolean.FALSE;
}
