package com.example.ticket.common.aspect;

import com.example.ticket.common.annotation.Idempotent;
import com.example.ticket.common.context.IdempotentContext;
import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;
import com.example.ticket.common.idempotent.IdempotentTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 幂等切面（第 1 层防线）。
 *
 * <p>流程：前端先 GET /api/v1/idempotent-token 拿令牌 → 提交时放请求头
 * {@code X-Idempotent-Token} → 切面原子消费，抢到才放行。</p>
 *
 * <p>失败补偿：业务抛异常时不把令牌"塞回去"。并发下回滚令牌会重复放行，
 * 宁可让用户重新取一次令牌（多一次点击），也不冒重复写入的风险。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    public static final String TOKEN_HEADER = "X-Idempotent-Token";

    private final IdempotentTokenStore tokenStore;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 非 Web 调用（单测、内部调用）不做令牌校验，交由数据库唯一索引兜底
        if (attrs == null) {
            return pjp.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        String token = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            log.warn("missing idempotent token, bizType={}, uri={}", idempotent.bizType(), request.getRequestURI());
            throw new BizException(ErrorCode.PARAM_MISSING, "缺少幂等令牌请求头 " + TOKEN_HEADER);
        }

        if (!tokenStore.consume(token)) {
            log.warn("duplicated submit intercepted, bizType={}, token={}, user={}",
                    idempotent.bizType(), token, request.getRemoteAddr());
            throw new BizException(ErrorCode.REPEAT_SUBMIT);
        }

        // 把已消费的令牌交给 Service，用于写数据库层兜底记录（两层共用同一个幂等键）
        IdempotentContext.set(token);
        try {
            return pjp.proceed();
        } finally {
            IdempotentContext.clear();
        }
    }
}
