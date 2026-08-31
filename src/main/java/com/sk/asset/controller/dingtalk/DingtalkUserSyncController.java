package com.sk.asset.controller.dingtalk;

import com.sk.asset.auth.UserContext;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.Result;
import com.sk.asset.service.dingtalk.DingtalkUserSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钉钉 userid 批量同步（仅 systemAdmin；生产环境全公司绑定入口，替代员工逐个报 userid）。
 */
@Tag(name = "钉钉 userid 同步（超管）")
@RestController
@RequestMapping("/api/v1/dingtalk/user-sync")
@RequiredArgsConstructor
public class DingtalkUserSyncController {

    private final DingtalkUserSyncService syncService;

    /** 超管角色名（comm_public_basic sys_role.name，经 userSystemAuth 进 asset 角色列表） */
    @Value("${app.approval-chain.admin-role:systemAdmin}")
    private String adminRole;

    @Operation(summary = "全量同步钉钉 userid（仅超管；需钉钉启用 + 应用有通讯录只读权限）",
            description = "BFS 遍历钉钉部门树拉全公司用户，按手机号（优先）/姓名（唯一）匹配 "
                    + "comm_public_basic.sys_user 并回填 dd_user_id。只填空不覆盖已有绑定，"
                    + "冲突与未匹配项见返回报告，需人工核对。")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<DingtalkUserSyncService.SyncReport> sync() {
        requireSystemAdmin();
        try {
            return Result.ok(syncService.syncAll());
        } catch (IllegalStateException e) {
            // 前置条件不满足（钉钉未启用/目录未配置）：400 提示
            throw new BusinessException(400, e.getMessage());
        }
    }

    /** 超管门禁：无 systemAdmin 角色 403 */
    private void requireSystemAdmin() {
        if (!UserContext.require().assetRoleNames().contains(adminRole)) {
            throw new BusinessException(403, "钉钉 userid 同步仅系统管理员可触发");
        }
    }
}
