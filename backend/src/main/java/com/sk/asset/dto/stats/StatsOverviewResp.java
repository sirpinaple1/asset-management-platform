package com.sk.asset.dto.stats;

import lombok.Data;

import java.util.Map;

/**
 * 工作台总览统计（B3）。assetStatusCounts 以 AssetStatus 枚举为全集，
 * 无数据的枚举值补 0，前端免判空。
 */
@Data
public class StatsOverviewResp {

    /** 资产状态分布（key=AssetStatus.name()，含 0 值，LinkedHashMap 保枚举顺序） */
    private Map<String, Long> assetStatusCounts;

    /** 待我处理单据数：领用/借用/调拨 PENDING 且非我发起、定向我或共享池；变更允许自审含我发起 */
    private long myTodoCount;

    /** 我的持有数：asset.user_id = 我 且 status = IN_USE */
    private long myHoldingCount;

    /** 我创建的进行中盘点数：stocktake.creator_user_id = 我 且 status = IN_PROGRESS */
    private long inProgressStocktakeCount;
}
