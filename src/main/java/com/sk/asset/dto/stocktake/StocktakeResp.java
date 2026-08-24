package com.sk.asset.dto.stocktake;

import com.sk.asset.entity.stocktake.Stocktake;
import com.sk.asset.enums.stocktake.StocktakeStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点任务响应（列表与详情共用；含范围名称、统计计数，
 * 详情/创建返回时附带明细行 items）。
 */
@Data
public class StocktakeResp {

    private Long id;

    /** 盘点任务名称 */
    private String name;

    private String status;

    private String statusLabel;

    /** 盘点范围-位置（asset_location.id，空=全库；含子树） */
    private Long locationId;

    /** 盘点范围-位置名称（查询回填，空=全库） */
    private String locationName;

    /** 盘点范围-分类（asset_category.id，空=全类） */
    private Long categoryId;

    /** 盘点范围-分类名称（查询回填，空=全类） */
    private String categoryName;

    private Long creatorUserId;

    /** 创建人姓名（提交时快照，空时前端回退展示用户 ID） */
    private String creatorName;

    private LocalDateTime startTime;

    private LocalDateTime completeTime;

    private String remark;

    private Long companyId;

    // ---- 统计（查询回填） ----

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

    /** 明细行（详情/创建返回时回填） */
    private List<StocktakeItemResp> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static StocktakeResp from(Stocktake entity) {
        StocktakeResp resp = new StocktakeResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setStatus(entity.getStatus());
        resp.setStatusLabel(StocktakeStatus.of(entity.getStatus()).getLabel());
        resp.setLocationId(entity.getLocationId());
        resp.setLocationName(entity.getLocationName());
        resp.setCategoryId(entity.getCategoryId());
        resp.setCategoryName(entity.getCategoryName());
        resp.setCreatorUserId(entity.getCreatorUserId());
        resp.setCreatorName(entity.getCreatorName());
        resp.setStartTime(entity.getStartTime());
        resp.setCompleteTime(entity.getCompleteTime());
        resp.setRemark(entity.getRemark());
        resp.setCompanyId(entity.getCompanyId());
        resp.setTotalCount(entity.getTotalCount());
        resp.setPendingCount(entity.getPendingCount());
        resp.setMatchedCount(entity.getMatchedCount());
        resp.setMismatchCount(entity.getMismatchCount());
        resp.setNotFoundCount(entity.getNotFoundCount());
        resp.setExtraCount(entity.getExtraCount());
        resp.setItems(StocktakeItemResp.fromList(entity.getItems()));
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
