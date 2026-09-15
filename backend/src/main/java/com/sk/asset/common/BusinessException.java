package com.sk.asset.common;

import lombok.Getter;

/**
 * 业务异常（N2：与 comm_public_basic BusinessException 语义对齐）。
 * 由 GlobalExceptionHandler 统一映射为 Result 响应，禁止空捕获。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(Result.FAIL, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
