package com.example.ticket.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 登录态快照。会整体写入会话存储（local 为进程内 Map，redis 为序列化后的 JSON）。
 * 只存鉴权必需字段，不存密码、不存手机号。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long id;
    private String username;
    private String realName;
    private Long deptId;
    private String deptName;
    /** 数据范围：1本人 2本部门 3本部门及子部门 4全部（取用户所有角色中最大范围） */
    private Integer dataScope;
    private Set<String> roles;
    private Set<String> perms;

    public boolean hasPerm(String perm) {
        return perms != null && (perms.contains(perm) || perms.contains("*"));
    }
}
