package com.sk.asset.dto.stocktake;

import lombok.Data;

/**
 * 盘点明细确认请求（POST /api/v1/stocktakes/{id}/items/{itemId}/scan）。
 * 提交实际位置判定相符/位置不符，或标记盘亏（未找到实物）。
 */
@Data
public class StocktakeScanReq {

    /** 实际位置（asset_location.id；notFound=false 时必填，与系统记录位置一致=相符） */
    private Long actualLocationId;

    /** 未找到实物（盘亏）；为 true 时忽略 actualLocationId */
    private Boolean notFound;

    /** 备注 */
    private String remark;
}
