package com.sk.asset.dto.asset;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资产报废请求 DTO
 */
@Data
public class AssetDiscardReq {

    @Size(max = 500, message = "报废原因长度不能超过 500")
    private String reason;
}
