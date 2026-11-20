package com.example.ticket.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等声明（第 1 层防线）。
 * <p>要求请求头带 {@code X-Idempotent-Token}，令牌由 {@code GET /api/v1/idempotent-token} 下发。
 * 切面用**原子 DEL**（或 Map.remove 的原子语义）判定是否首次，抢到令牌才放行。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 业务类型，用于日志与 DB 兜底记录，如 TICKET_SUBMIT */
    String bizType() default "";
}
