package com.sk.asset.migration;

import com.sk.asset.auth.UserContext;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M08 历史数据迁移（一次性工具端点）。
 * 双重门禁：app.migration.enabled=true + 当前用户持 app.migration.required-role 角色。
 */
@Slf4j
@Tag(name = "历史数据迁移（M08）")
@RestController
@RequestMapping("/api/v1/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;
    private final MigrationProperties props;

    @Operation(summary = "执行历史数据迁移（幂等，可重复运行；基础数据→资产→领用单→调拨单→日志）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/run")
    public Result<MigrationResult> run() {
        if (!props.isEnabled()) {
            throw new BusinessException(503, "迁移功能未启用（app.migration.enabled）");
        }
        if (!UserContext.require().assetRoleNames().contains(props.getRequiredRole())) {
            throw new BusinessException(403, "无迁移执行权限（需角色：" + props.getRequiredRole() + "）");
        }
        log.warn("触发 M08 历史数据迁移，操作人：{}", UserContext.get().getUsername());
        return Result.ok(migrationService.run());
    }
}
