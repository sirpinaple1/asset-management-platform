package com.sk.asset.dto.stocktake;

import com.sk.asset.entity.stocktake.StocktakeItem;
import com.sk.asset.enums.stocktake.StocktakeItemStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 盘点明细行响应（含资产编码/名称/序列号与位置名称）。
 */
@Data
public class StocktakeItemResp {

    private Long id;

    private Long stocktakeId;

    private Long assetId;

    /** 资产编码（查询回填） */
    private String assetBarcode;

    /** 资产名称（查询回填） */
    private String assetName;

    /** 资产序列号（查询回填） */
    private String assetSn;

    private String status;

    private String statusLabel;

    /** 系统记录位置（asset_location.id） */
    private Long expectedLocationId;

    /** 系统记录位置名称（查询回填） */
    private String expectedLocationName;

    /** 盘点实际位置（asset_location.id） */
    private Long actualLocationId;

    /** 盘点实际位置名称（查询回填） */
    private String actualLocationName;

    /** 扫码/确认时间 */
    private LocalDateTime scannedAt;

    /** 盘点人 ID */
    private Long scannedByUserId;

    private String remark;

    private LocalDateTime createdAt;

    public static StocktakeItemResp from(StocktakeItem item) {
        StocktakeItemResp resp = new StocktakeItemResp();
        resp.setId(item.getId());
        resp.setStocktakeId(item.getStocktakeId());
        resp.setAssetId(item.getAssetId());
        resp.setAssetBarcode(item.getAssetBarcode());
        resp.setAssetName(item.getAssetName());
        resp.setAssetSn(item.getAssetSn());
        resp.setStatus(item.getStatus());
        resp.setStatusLabel(StocktakeItemStatus.of(item.getStatus()).getLabel());
        resp.setExpectedLocationId(item.getExpectedLocationId());
        resp.setExpectedLocationName(item.getExpectedLocationName());
        resp.setActualLocationId(item.getActualLocationId());
        resp.setActualLocationName(item.getActualLocationName());
        resp.setScannedAt(item.getScannedAt());
        resp.setScannedByUserId(item.getScannedByUserId());
        resp.setRemark(item.getRemark());
        resp.setCreatedAt(item.getCreatedAt());
        return resp;
    }

    public static List<StocktakeItemResp> fromList(List<StocktakeItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(StocktakeItemResp::from).collect(Collectors.toList());
    }
}
