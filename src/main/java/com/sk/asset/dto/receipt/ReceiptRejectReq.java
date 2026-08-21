package com.sk.asset.dto.receipt;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 拒绝申请请求（POST /api/v1/receipts/{id}/reject）。
 */
@Data
public class ReceiptRejectReq {

    @NotBlank(message = "拒绝原因不能为空")
    private String reason;
}
