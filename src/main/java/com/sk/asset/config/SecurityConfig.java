package com.sk.asset.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.AuthPort;
import com.sk.asset.auth.TokenAuthFilter;
import com.sk.asset.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * M01-B 安全链（ADR-0004）：无状态 + TokenAuthFilter（HTTP 调 comm_public_basic 校验 token）。
 * <p>
 * - 所有业务请求需认证；/error 放行（容器内部错误转发，避免错误页被 401 覆盖）。<br>
 * - API 文档端点默认 fail-closed（需认证）；local 环境通过 app.security.docs-permit-all=true 放行
 *   （沿用 M01-A 的环境隔离语义：占位放行不泄漏到测试/生产）。<br>
 * - CORS 复用 M01-B 前置处理的白名单配置（asset-frontend M-FE01 联调依赖）。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthPort authPort,
            ObjectMapper objectMapper,
            @Value("${app.security.docs-permit-all:false}") boolean docsPermitAll) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new TokenAuthFilter(authPort, objectMapper), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    if (docsPermitAll) {
                        // 仅本地开发：knife4j/springdoc 文档端点免认证（非 local 默认 401）
                        auth.requestMatchers("/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll();
                    }
                    auth.requestMatchers("/error").permitAll()
                            .anyRequest().authenticated();
                })
                // 兜底：TokenAuthFilter 放行但 SecurityContext 无认证的边缘场景（正常流量不会走到）
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) ->
                        writeUnauthorized(response, objectMapper)));
        return http.build();
    }

    /**
     * CORS 白名单（M-FE01 前端联调依赖）。
     * <p>
     * allowCredentials=true 时 Spring 不允许通配 "*" origin，必须显式列出。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeUnauthorized(HttpServletResponse response, ObjectMapper objectMapper) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(401, "未认证"));
    }
}
