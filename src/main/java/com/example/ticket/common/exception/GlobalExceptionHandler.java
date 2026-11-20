package com.example.ticket.common.exception;

import com.example.ticket.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把异常收敛成统一响应体，且**不把堆栈和 SQL 暴露给前端**。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        // 业务异常是预期内的（校验失败、重复提交），WARN 足够，不能污染 ERROR 日志
        log.warn("business error: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + defaultMsg(f))
                .collect(Collectors.joining("; "));
        log.warn("param invalid: {}", msg);
        return Result.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_MISSING, "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public Result<Void> handleBadFormat(Exception e) {
        log.warn("param format error: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_INVALID, "参数格式不正确");
    }

    /**
     * 唯一索引冲突 = 幂等链路的第 2 层兜底（Redis 挂了、令牌过期、并发绕过 AOP 都会落到这里）。
     * 必须单独处理，否则用户看到的是 500 "系统繁忙"，会以为系统坏了而反复重试。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("duplicate key: {}", e.getMessage());
        return Result.fail(ErrorCode.REPEAT_SUBMIT, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler({NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public Result<Void> handleNotFound(Exception e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "接口不存在或请求方法不支持");
    }

    /** 兜底：返回 traceId 让用户报障时能直接定位日志，而不是靠猜时间点 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        String traceId = MDC.get("traceId");
        log.error("unexpected error, traceId={}", traceId, e);
        return Result.fail(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试（traceId: " + traceId + "）");
    }

    private String defaultMsg(FieldError f) {
        return f.getDefaultMessage() == null ? "非法值" : f.getDefaultMessage();
    }
}
