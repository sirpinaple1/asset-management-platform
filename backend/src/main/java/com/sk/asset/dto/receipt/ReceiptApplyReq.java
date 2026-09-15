package com.sk.asset.dto.receipt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * 发起领用/借用申请请求（POST /api/v1/receipts）。
 * 申请人由后端 UserContext 取，前端不传。
 */
@Data
public class ReceiptApplyReq {

    /** 单据类型：RECEIVE-领用 BORROW-借用 */
    @NotBlank(message = "单据类型不能为空")
    private String type;

    /** 申请领用/借用的资产 ID 列表（一次可多台） */
    @NotEmpty(message = "请至少选择一台资产")
    private List<Long> assetIds;

    /** 领用区域（必填）：审批通过后资产位置更新至此，盘点按位置扫资产的依据 */
    @NotNull(message = "领用区域不能为空")
    private Long locationId;

    @NotBlank(message = "领用部门不能为空")
    private String department;

    @NotBlank(message = "领用事由不能为空")
    private String reason;

    /**
     * @deprecated 已废弃（V20260833 两级审批链）：审批人由 approval_config 自动路由并在提交时冻结快照，
     * 本字段提交时忽略（保留字段兼容旧前端报文，前端发起弹窗应改用 approval-preview 预览审批链）
     */
    @Positive(message = "指定处理人 ID 需为正整数")
    private Long assigneeUserId;
}
