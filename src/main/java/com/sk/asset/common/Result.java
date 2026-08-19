package com.sk.asset.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结构（P3 契约：与 comm_public_basic Result 对齐，前端可复用同一套解析）。
 * code 语义与 comm_public_basic 一致：200 成功，其余为失败；HTTP 状态码仅鉴权层使用（401/403/503）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    public static final int SUCCESS = 200;
    public static final int FAIL = 500;

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, "成功", data);
    }

    public static Result<Void> ok() {
        return new Result<>(SUCCESS, "成功", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
