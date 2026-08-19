package com.sk.asset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * 资产管理系统启动类。
 * <p>
 * 端口与激活 profile 从配置读取，不在代码中硬编码地址（可维护性约定）。
 */
@Slf4j
@SpringBootApplication
public class AssetApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AssetApplication.class, args);
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String profiles = String.join(",", env.getActiveProfiles());
        log.info("资产管理系统启动成功！profile=[{}] port=[{}]", profiles, port);
        log.info("API 文档地址: http://localhost:{}/doc.html", port);
    }
}
