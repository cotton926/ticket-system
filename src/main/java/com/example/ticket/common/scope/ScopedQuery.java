package com.example.ticket.common.scope;

/**
 * 可分页 / 可接收数据权限片段的查询对象。
 *
 * <p>用接口而不是反射去 set 字段：切面只需要认识这个接口，任何查询 DTO 实现它就能被注入，
 * 既避免反射带来的运行时不确定性，也让"哪些查询受数据权限约束"在类型层面一目了然。</p>
 */
public interface ScopedQuery {

    void setDataScope(String dataScope);

    String getDataScope();
}
