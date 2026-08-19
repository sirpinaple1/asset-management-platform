package com.sk.asset.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Token 鉴权过滤器（M01-B 核心，ADR-0004）：
 * 从 Authorization: Bearer 头提取 token → AuthPort 校验（本地缓存 30s 摊薄回源）→
 * 写入 SecurityContext（asset 角色作为 authority）与 UserContext（业务层读取）。
 * <p>
 * 失败语义：401=无 token/无效 token；403=已登录但无 asset 权限；503=comm_public_basic 不可用（fail-closed）。
 * 非 Spring Bean（由 SecurityConfig 手动构造挂链，避免 servlet 容器重复注册）。
 */
@Slf4j
@RequiredArgsConstructor
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthPort authPort;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            writeError(response, 401, "未认证：缺少 Bearer token");
            return;
        }
        try {
            AuthContext context = authPort.authenticate(token);
            if (context == null) {
                writeError(response, 401, "token 无效或已过期");
                return;
            }
            List<SimpleGrantedAuthority> authorities = context.assetRoleNames().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(context, null, authorities));
            UserContext.set(context);
            try {
                chain.doFilter(request, response);
            } finally {
                UserContext.clear();
                SecurityContextHolder.clearContext();
            }
        } catch (AuthException e) {
            writeError(response, e.getStatus(), e.getMessage());
        } catch (Exception e) {
            log.error("鉴权过程发生未预期异常", e);
            writeError(response, 503, "鉴权失败");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX) && header.length() > BEARER_PREFIX.length()) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(status, message));
    }
}
