package com.sk.asset.dto.receipt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    @NotBlank(message = "领用部门不能为空")
    private String department;

    @NotBlank(message = "领用事由不能为空")
    private String reason;
}
