package com.sk.asset.controller.approval;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.PageResp;
import com.sk.asset.common.Result;
import com.sk.asset.dto.approval.ApprovalConfigReq;
import com.sk.asset.dto.approval.ApprovalConfigResp;
import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.enums.approval.ApprovalConfigType;
import com.sk.asset.service.approval.ApprovalConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批链配置（两级审批人路由表，仅 systemAdmin 可见可管理）。
 */
@Tag(name = "审批链配置（超管）")
@RestController
@RequestMapping("/api/v1/approval-configs")
@RequiredArgsConstructor
public class ApprovalConfigController {

    private final ApprovalConfigService configService;

    /** 超管角色名（comm_public_basic sys_role.name，经 userSystemAuth 进 asset 角色列表） */
    @Value("${app.approval-chain.admin-role:systemAdmin}")
    private String adminRole;

    @Operation(summary = "配置列表（分页，支持 type 筛选 + keyword 匹配配置键/备注；仅超管）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<PageResp<ApprovalConfigResp>> page(
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100")
            @RequestParam(defaultValue = "20") long size,
            @Parameter(description = "配置类型：DEPT_SUPERVISOR-部门主管 WAREHOUSE_KEEPER-领料仓管理员")
            @RequestParam(required = false) String type,
            @Parameter(description = "关键词（模糊匹配配置键/备注）")
            @RequestParam(required = false) String keyword) {

        requireSystemAdmin();
        ApprovalConfigType typeEnum = null;
        if (type != null && !type.isBlank()) {
            try {
                typeEnum = ApprovalConfigType.of(type.trim());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, e.getMessage());
            }
        }
        IPage<ApprovalConfig> result = configService.page(Math.max(page, 1), Math.min(size, 100), typeEnum, keyword);
        List<ApprovalConfigResp> records = result.getRecords().stream()
                .map(ApprovalConfigResp::from)
                .collect(Collectors.toList());
        return Result.ok(PageResp.of(result, records));
    }

    @Operation(summary = "新增配置（仅超管；审批人需存在，同部门/位置重复配置返回 400）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<ApprovalConfigResp> create(@RequestBody @Valid ApprovalConfigReq req) {
        requireSystemAdmin();
        ApprovalConfig entity = new ApprovalConfig();
        entity.setConfigType(req.getConfigType());
        entity.setConfigKey(req.getConfigKey());
        entity.setApproverUserId(req.getApproverUserId());
        entity.setRemark(req.getRemark());
        configService.save(entity);
        return Result.ok(ApprovalConfigResp.from(configService.getById(entity.getId())));
    }

    @Operation(summary = "更新配置（仅超管）")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public Result<ApprovalConfigResp> update(@PathVariable Long id,
                                             @RequestBody @Valid ApprovalConfigReq req) {
        requireSystemAdmin();
        ApprovalConfig existing = configService.getById(id);
        if (existing == null) {
            return Result.fail(404, "审批链配置不存在");
        }
        ApprovalConfig entity = new ApprovalConfig();
        entity.setId(id);
        entity.setConfigType(req.getConfigType() != null && !req.getConfigType().isBlank()
                ? req.getConfigType() : existing.getConfigType());
        entity.setConfigKey(req.getConfigKey());
        entity.setApproverUserId(req.getApproverUserId());
        entity.setRemark(req.getRemark());
        configService.updateById(entity);
        return Result.ok(ApprovalConfigResp.from(configService.getById(id)));
    }

    @Operation(summary = "删除配置（逻辑删除；仅超管）")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireSystemAdmin();
        configService.deleteById(id);
        return Result.ok();
    }

    /** 超管门禁：无 systemAdmin 角色 403（不泄露配置存在性） */
    private void requireSystemAdmin() {
        if (!UserContext.require().assetRoleNames().contains(adminRole)) {
            throw new BusinessException(403, "审批链配置仅系统管理员可管理");
        }
    }
}
