package com.example.ticket.auth;

import com.example.ticket.common.context.LoginUser;

/**
 * 会话存储。
 * <p>为什么自己抽一层而不是直接用 Sa-Token / Spring Security：本项目只需要
 * "token → 登录态" 这一个映射，加上注解式权限校验就够；把会话抽象成接口后，
 * 单机用 Map、多实例换 Redis，业务代码零改动，也不引入额外的框架心智负担。</p>
 */
public interface SessionStore {

    void save(String token, LoginUser user, long ttlMinutes);

    /** 取登录态，不存在或已过期返回 null（调用方负责抛 20001） */
    LoginUser get(String token);

    void remove(String token);
}
