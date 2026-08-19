package com.sk.asset.auth;

import lombok.Getter;

/**
 * 鉴权异常：status 为应写回客户端的 HTTP 状态码。
 * 401=token 无效/过期；403=已登录但无 asset 系统权限；503=comm_public_basic 不可用（fail-closed，ADR-0004）。
 */
@Getter
public class AuthException extends RuntimeException {

    private final int status;

    public AuthException(int status, String message) {
        super(message);
        this.status = status;
    }
}
