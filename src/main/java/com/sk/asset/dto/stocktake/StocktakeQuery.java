package com.sk.asset.dto.stocktake;

import com.sk.asset.enums.stocktake.StocktakeStatus;
import lombok.Data;

import java.time.LocalDate;

/**
 * 盘点任务列表查询条件（GET /api/v1/stocktakes）。
 */
@Data
public class StocktakeQuery {

    /** 状态筛选 */
    private StocktakeStatus status;

    /** 创建人 ID 筛选 */
    private Long userId;

    /** 创建日期（yyyy-MM-dd） */
    private LocalDate date;
}
