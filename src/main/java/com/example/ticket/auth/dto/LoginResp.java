package com.example.ticket.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class LoginResp {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private Long deptId;
    private String deptName;
    /** 1本人 2本部门 3本部门及子部门 4全部 */
    private Integer dataScope;
    private Set<String> roles;
    private Set<String> perms;
}
