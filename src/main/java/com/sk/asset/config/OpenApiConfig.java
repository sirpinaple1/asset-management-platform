package com.sk.asset.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（SpringDoc + knife4j，访问 /doc.html）。
 * 接口版本化约定：URL 前缀 /api/v1/，破坏性变更升版本号（ENGINEERING P3）。
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名，/doc.html 的 Authorize 弹窗对应项 */
    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI assetOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("资产管理系统 API")
                        .description("森科资产管理系统：IT 资产 / 固定资产 / 生产设备工装模具 / 耗材配件的全生命周期管理。"
                                + "鉴权复用 comm_public_basic，请求统一携带 Authorization: Bearer <token> 头。")
                        .version("v1"))
                // 注册 Bearer 认证：/doc.html 右上角 Authorize 输入 token 后，
                // 所有请求自动携带 Authorization 头，供开发阶段手动测试受保护接口（M01-B TokenFilter 落地后生效）
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("comm_public_basic 签发的 token（Redis Token，非 JWT），"
                                        + "粘贴 token 原文即可，无需 Bearer 前缀")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
