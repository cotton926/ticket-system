package com.example.ticket.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 * <p>使用 RuntimeException 而非受检异常：受检异常会污染每一层签名，且 Spring 默认只在
 * RuntimeException/Error 时回滚 —— 配合 {@code @Transactional(rollbackFor = Exception.class)} 双保险。</p>
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
