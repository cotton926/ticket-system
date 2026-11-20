package com.example.ticket.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 功能权限声明（垂直越权防护）。
 * <p>{@code @RequirePerm("ticket:assign")} 由 AuthInterceptor 统一校验，
 * 不依赖前端隐藏按钮 —— 前端隐藏只是体验，不是安全边界。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 权限点，如 ticket:assign；多个时要求全部满足 */
    String[] value();
}
