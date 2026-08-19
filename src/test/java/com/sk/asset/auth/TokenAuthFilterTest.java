package com.sk.asset.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.auth.dto.SystemAuthDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TokenAuthFilter 单测（mock AuthPort）：401/403/503 语义 + UserContext 生命周期。
 */
class TokenAuthFilterTest {

    private final AuthPort authPort = mock(AuthPort.class);
    private final TokenAuthFilter filter = new TokenAuthFilter(authPort, new ObjectMapper());

    @AfterEach
    void tearDown() {
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    private AuthContext context() {
        AuthUserDto user = new AuthUserDto();
        user.setUserId(1);
        user.setUsername("admin");
        user.setName("管理员");
        com.sk.asset.auth.dto.SystemRoleDto role = new com.sk.asset.auth.dto.SystemRoleDto();
        role.setId(1);
        role.setName("资产管理员");
        SystemAuthDto systemAuth = new SystemAuthDto();
        systemAuth.setSystemCode("asset");
        systemAuth.setRoles(List.of(role));
        return new AuthContext(user, systemAuth);
    }

    @Test
    void 无Bearer头_401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("缺少 Bearer token");
    }

    @Test
    void 无效token_401() throws Exception {
        when(authPort.authenticate(anyString())).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("token 无效或已过期");
    }

    @Test
    void 有效token_放行并设置UserContext_请求结束清理() throws Exception {
        when(authPort.authenticate(anyString())).thenReturn(context());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok-1");

        AtomicReference<AuthContext> seenInChain = new AtomicReference<>();
        jakarta.servlet.FilterChain captureChain = (req, res) -> seenInChain.set(UserContext.get());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, captureChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seenInChain.get()).isNotNull();
        assertThat(seenInChain.get().getUsername()).isEqualTo("admin");
        // 请求结束后 ThreadLocal 必须清理（线程复用防串号）
        assertThat(UserContext.get()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 无asset权限_403() throws Exception {
        when(authPort.authenticate(anyString())).thenThrow(new AuthException(403, "尚未分配 asset 系统角色"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok-1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("尚未分配");
    }

    @Test
    void 鉴权服务不可用_503() throws Exception {
        when(authPort.authenticate(anyString())).thenThrow(new AuthException(503, "鉴权服务不可用，请稍后重试"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok-1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void 未预期异常_503且不外泄细节() throws Exception {
        when(authPort.authenticate(anyString())).thenThrow(new RuntimeException("boom"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok-1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("boom");
    }
}
