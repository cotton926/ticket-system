package com.example.ticket.system.vo;

import lombok.Data;

/**
 * 人员下拉选项。只暴露展示所需字段，绝不带 password/phone/email，
 * 避免"随便调个接口就把通讯录拖走"。
 */
@Data
public class UserOptionVO {

    private Long id;

    private String username;

    private String realName;

    private Long deptId;

    private String deptName;

    /** HANDLER / DISPATCHER / ADMIN，前端按此显示角色标签 */
    private String roleCode;
}
