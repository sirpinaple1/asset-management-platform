package com.sk.asset.dto.stocktake;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点报告响应（GET /api/v1/stocktakes/{id}/report）：
 * 各状态汇总计数 + 差异明细（位置不符/盘亏/盘盈）。
 * 任务进行中也可查看（实时统计）。
 */
@Data
public class StocktakeReportResp {

    private Long stocktakeId;

    /** 盘点任务名称 */
    private String name;

    private String status;

    private String statusLabel;

    private LocalDateTime completeTime;

    // ---- 汇总计数 ----

    /** 明细总数 */
    private Long totalCount;

    /** 待盘数 */
    private Long pendingCount;

    /** 账实相符数 */
    private Long matchedCount;

    /** 位置不符数 */
    private Long mismatchCount;

    /** 盘亏数 */
    private Long notFoundCount;

    /** 盘盈数 */
    private Long extraCount;

    // ---- 差异明细 ----

    /** 位置不符明细（可触发调拨归位） */
    private List<StocktakeItemResp> mismatches;

    /** 盘亏明细 */
    private List<StocktakeItemResp> notFounds;

    /** 盘盈明细 */
    private List<StocktakeItemResp> extras;
}
