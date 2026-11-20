package com.example.ticket.common.web;

import com.example.ticket.auth.SessionStore;
import com.example.ticket.common.annotation.RequirePerm;
import com.example.ticket.common.context.LoginUser;
import com.example.ticket.common.context.UserContext;
import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：认证（token → 登录态）+ 功能权限（@RequirePerm）。
 *
 * <p>放在拦截器而不是 AOP：Controller 层是唯一入口，拦截器天然拿到 HandlerMethod，
 * 能同时读类级和方法级注解，且不需要为每个 Controller 织入代理。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final SessionStore sessionStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 静态资源 / 非 Controller 请求直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        LoginUser user = sessionStore.get(token);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserContext.set(user);

        try {
            checkPerm(handlerMethod, user);
        } catch (RuntimeException e) {
            // preHandle 抛异常时 Spring 不会回调 afterCompletion，这里必须自己清理，
            // 否则 ThreadLocal 残留会让下一个复用该线程的请求"继承"上一个用户的身份。
            UserContext.clear();
            throw e;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        // 必须清理：线程池复用 ThreadLocal 不清会串号，属于最典型的安全事故来源
        UserContext.clear();
    }

    private void checkPerm(HandlerMethod handlerMethod, LoginUser user) {
        RequirePerm perm = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (perm == null) {
            perm = handlerMethod.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (perm == null) {
            return;
        }
        for (String p : perm.value()) {
            if (!user.hasPerm(p)) {
                throw new BizException(ErrorCode.FORBIDDEN, "缺少权限: " + p);
            }
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        return header.startsWith(BEARER) ? header.substring(BEARER.length()).trim() : header.trim();
    }
}
