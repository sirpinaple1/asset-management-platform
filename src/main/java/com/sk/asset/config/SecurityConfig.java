package com.sk.asset.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * M01-A 阶段安全占位配置（仅 local profile 生效）：全部放行 + 无状态会话，
 * 保证骨架可启动、API 文档可访问。
 * <p>
 * 非 local 环境不注册本链，回退到 Spring Boot 默认安全配置（全部请求需认证），
 * 确保 permitAll 占位不会泄漏到测试/生产。
 * <p>
 * M01-B 将新增 TokenFilter 鉴权链（适用所有环境）：从 Authorization: Bearer 头提取 token，
 * HTTP 调 comm_public_basic /login/checkToken 校验（ADR-0004），失败统一 401。
 * <p>
 * CORS 允许 asset-frontend（M-FE01 Vite 开发服务器）跨域调用；
 * origin 白名单配置在 application.yml 的 app.cors.allowed-origins，测试/生产由各自 profile 覆盖。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Profile("local")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * CORS 白名单（M-FE01 前端联调依赖；M01-B TokenFilter 鉴权链复用此配置）。
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
}
