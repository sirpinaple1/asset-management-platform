package com.sk.asset.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * M08 迁移配置（@Value 风格，对齐 app.auth / app.cors 既有惯例）。
 * 敏感项（xlsx 目录、auth 库连接）只在 application-local.yml / 环境变量提供（R3 红线）。
 */
@Slf4j
@Component
public class MigrationProperties {

    /** 迁移端点总开关：未启用时 POST /api/v1/migration/run 返回 503 */
    @Value("${app.migration.enabled:false}")
    private boolean enabled;

    /** 4 份 Excel 所在目录 */
    @Value("${app.migration.xlsx-dir:}")
    private String xlsxDir;

    @Value("${app.migration.asset-file:AssetCard_20260818_165814.xlsx}")
    private String assetFile;

    @Value("${app.migration.receipt-file:Asset Receive Receipt_20260818_165715.xlsx}")
    private String receiptFile;

    @Value("${app.migration.transfer-file:资产调拨单_20260818_165659.xlsx}")
    private String transferFile;

    @Value("${app.migration.log-file:Asset History Logs_20260818_165840.xlsx}")
    private String logFile;

    /** comm_public_basic 库 JDBC（sys_user 读 + 迁移自动建账号写） */
    @Value("${app.migration.auth-db-url:}")
    private String authDbUrl;

    @Value("${app.migration.auth-db-username:}")
    private String authDbUsername;

    @Value("${app.migration.auth-db-password:}")
    private String authDbPassword;

    /** 未匹配人名是否自动创建 sys_user 账号 */
    @Value("${app.migration.auto-create-users:true}")
    private boolean autoCreateUsers;

    /** 新建账号初始密码（BCrypt 入库；默认密码用户被 comm_public_basic 策略拦在业务系统外，改密后可登录） */
    @Value("${app.migration.default-password:123456}")
    private String defaultPassword;

    /** 允许触发迁移的角色名（comm_public_basic sys_role.name） */
    @Value("${app.migration.required-role:asset-资产管理员}")
    private String requiredRole;

    /**
     * 日志幂等清除线：asset_log.created_at 早于该时间视为迁移产物，
     * 重跑迁移前清除后重灌（新系统 2026-08-19 上线，业务日志不可能早于该时间）。
     */
    @Value("${app.migration.log-cutoff:2026-08-19T00:00:00}")
    private LocalDateTime logCutoff;

    public Path resolve(String fileName) {
        if (xlsxDir == null || xlsxDir.isBlank()) {
            throw new IllegalStateException("app.migration.xlsx-dir 未配置（Excel 所在目录）");
        }
        return Path.of(xlsxDir, fileName);
    }

    public LocalDateTime getLogCutoff() {
        return logCutoff != null ? logCutoff
                : LocalDateTime.parse("2026-08-19T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getAssetFile() {
        return assetFile;
    }

    public String getReceiptFile() {
        return receiptFile;
    }

    public String getTransferFile() {
        return transferFile;
    }

    public String getLogFile() {
        return logFile;
    }

    public String getAuthDbUrl() {
        return authDbUrl;
    }

    public String getAuthDbUsername() {
        return authDbUsername;
    }

    public String getAuthDbPassword() {
        return authDbPassword;
    }

    public boolean isAutoCreateUsers() {
        return autoCreateUsers;
    }

    public String getDefaultPassword() {
        return defaultPassword;
    }

    public String getRequiredRole() {
        return requiredRole;
    }
}
