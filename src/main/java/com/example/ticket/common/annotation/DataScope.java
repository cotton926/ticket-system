package com.example.ticket.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限声明（水平越权防护）。
 *
 * <p>标注在 Service 方法上，切面会把"当前用户可见的数据范围"拼成一段 SQL 片段，
 * 注入到第一个实现了 {@link com.example.ticket.common.scope.ScopedQuery} 的参数里，
 * 最终由 Mapper 以 <code>${dataScope}</code> 引用。</p>
 *
 * <p><b>为什么这里允许 ${}</b>：片段完全由后端用 Long 类型的 userId/deptId 拼接，
 * 不含任何用户可控字符串，不存在注入面。这是全项目唯一的白名单例外，
 * 新增类似用法必须在 Code Review 时单独说明理由。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 表别名，如 "t"；生成的片段会写成 t.creator_dept = ... */
    String alias() default "t";
}
