package com.sk.asset.dto.stocktake;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建盘点任务请求（POST /api/v1/stocktakes）。
 * 创建人由后端 UserContext 取，前端不传；
 * 范围（位置/分类）均可空 = 全库盘点，位置范围含其子树。
 */
@Data
public class StocktakeCreateReq {

    /** 盘点任务名称 */
    @NotBlank(message = "盘点任务名称不能为空")
    private String name;

    /** 盘点范围-位置（asset_location.id，空=全库；含子树） */
    private Long locationId;

    /** 盘点范围-分类（asset_category.id，空=全类） */
    private Long categoryId;

    /** 备注 */
    private String remark;
}
