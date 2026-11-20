package com.example.ticket.common.api;

import com.example.ticket.common.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应体。
 * <p>约定：业务失败一律 HTTP 200 + 非 0 code，前端只需一套拦截器逻辑判断 code !== 0；
 * 只有网关级异常（502/503）才用非 200 状态码。</p>
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.message = ErrorCode.SUCCESS.getMessage();
        r.data = data;
        r.traceId = MDC.get("traceId");
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode ec) {
        return fail(ec.getCode(), ec.getMessage());
    }

    public static <T> Result<T> fail(ErrorCode ec, String message) {
        return fail(ec.getCode(), message);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.traceId = MDC.get("traceId");
        return r;
    }
}
