package com.sk.asset.dto.transfer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 拒绝调拨请求（POST /api/v1/transfers/{id}/reject）。
 */
@Data
public class TransferRejectReq {

    @NotBlank(message = "拒绝原因不能为空")
    private String reason;
}
