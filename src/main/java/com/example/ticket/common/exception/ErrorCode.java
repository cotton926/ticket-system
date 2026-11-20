package com.example.ticket.common.exception;

import lombok.Getter;

/**
 * 错误码。按区间分段，便于前端/运维按区间归类处理：
 * 1xxxx 参数 / 2xxxx 认证鉴权 / 3xxxx 业务规则 / 4xxxx 并发冲突 / 5xxxx 系统异常
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),

    PARAM_MISSING(10001, "缺少必填参数"),
    PARAM_INVALID(10002, "参数格式不正确"),
    PAGE_SIZE_EXCEED(10003, "每页最多 100 条"),

    UNAUTHORIZED(20001, "登录已过期，请重新登录"),
    LOGIN_FAILED(20002, "用户名或密码错误"),
    FORBIDDEN(20003, "无操作权限"),
    DATA_SCOPE_DENIED(20004, "无权访问该数据"),
    ACCOUNT_DISABLED(20005, "账号已停用"),

    ILLEGAL_STATUS_TRANSITION(30001, "当前状态不支持此操作"),
    REPEAT_SUBMIT(30002, "请勿重复提交"),
    TICKET_NOT_FOUND(30003, "工单不存在或已删除"),
    USER_NOT_FOUND(30004, "处理人不存在或已停用"),
    SLA_CONFIG_MISSING(30005, "该工单类型未配置 SLA"),
    TICKET_TYPE_NOT_FOUND(30006, "工单类型不存在"),
    REOPEN_EXPIRED(30007, "已超过可重开的天数"),

    VERSION_CONFLICT(40001, "工单状态已变更，请刷新后重试"),

    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试"),
    DB_ERROR(50001, "数据操作失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
