package com.sk.asset.dto.stocktake;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 按条码扫码请求（POST /api/v1/stocktakes/{id}/scan，PDA 扫码入口）。
 * 条码在任务明细中 → 更新该明细（相符/位置不符）；
 * 不在明细中但资产已登记 → 新建盘盈（EXTRA）明细；
 * 资产未登记 → 404。
 */
@Data
public class StocktakeBarcodeScanReq {

    /** 资产编码（条码） */
    @NotBlank(message = "资产条码不能为空")
    private String barcode;

    /** 实际位置（asset_location.id，可空=视为在系统记录位置找到；盘盈时空时取任务范围位置） */
    private Long actualLocationId;

    /** 备注 */
    private String remark;
}
