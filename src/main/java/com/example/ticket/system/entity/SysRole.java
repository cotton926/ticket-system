package com.example.ticket.system.entity;

import lombok.Data;

@Data
public class SysRole {
    private Long id;
    private String roleCode;
    private String roleName;
    /** 1本人 2本部门 3本部门及子部门 4全部 */
    private Integer dataScope;
    private Integer status;
}
