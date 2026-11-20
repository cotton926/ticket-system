package com.example.ticket.system.entity;

import lombok.Data;

@Data
public class SysDept {
    private Long id;
    private Long parentId;
    private String name;
    /** 祖级路径，如 "0,1,2"；子部门查询用 FIND_IN_SET 免递归 */
    private String ancestors;
    private Integer sort;
    private Integer status;
}
