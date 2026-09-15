package com.sk.asset.auth;

/**
 * 鉴权端口（ADR-0004）：asset 后端不自建登录，token 校验/用户信息/系统权限统一
 * HTTP 调 comm_public_basic，本地短缓存摊薄回源。
 * 抽象为接口便于单测 mock 与未来替换实现（ENGINEERING P2 外部依赖端口化）。
 */
public interface AuthPort {

    /**
     * 校验 token 并加载用户信息 + asset 系统权限（命中缓存直接返回）。
     *
     * @param token 裸 token（不含 "Bearer " 前缀）
     * @return 认证上下文；token 无效/过期返回 null（不缓存，下次请求重新回源）
     * @throws AuthException 403=无 asset 系统权限；503=comm_public_basic 不可用/响应异常（fail-closed）
     */
    AuthContext authenticate(String token);
}
