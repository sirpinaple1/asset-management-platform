package com.sk.asset.controller.dingtalk;

import com.sk.asset.common.Result;
import com.sk.asset.dto.dingtalk.ReturnApprovalResp;
import com.sk.asset.service.dingtalk.ReturnApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 钉钉退还审批（入口 B）：审批中心"我发起的"数据源。
 * 退还无系统单据（方案 B），此处返回映射记录 + 资产快照明细；
 * 审批动作在钉钉侧完成，系统不提供站内审批入口。
 */
@Tag(name = "钉钉退还审批")
@RestController
@RequestMapping("/api/v1/return-approvals")
@RequiredArgsConstructor
public class ReturnApprovalController {

    private final ReturnApprovalService returnApprovalService;

    @Operation(summary = "退还审批列表（含发起人、资产快照明细，按导入时间倒序）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<ReturnApprovalResp>> list() {
        return Result.ok(returnApprovalService.list());
    }
}
