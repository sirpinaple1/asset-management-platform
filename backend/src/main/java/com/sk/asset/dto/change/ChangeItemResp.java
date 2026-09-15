package com.sk.asset.dto.change;

import com.sk.asset.entity.change.ChangeOrderItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 变更单明细行响应（含资产编码/名称/序列号与变更前/后值）。
 */
@Data
public class ChangeItemResp {

    private Long id;

    private Long orderId;

    private Long assetId;

    /** 资产编码（查询回填） */
    private String assetBarcode;

    /** 资产名称（查询回填） */
    private String assetName;

    /** 资产序列号（查询回填） */
    private String assetSn;

    /** 变更字段名（如 user_id / location_id，白名单见 ChangeField 枚举） */
    private String fieldName;

    /** 字段展示名（如 使用人 / 区域） */
    private String fieldLabel;

    /** 变更前值（发起时快照的展示值） */
    private String valueBefore;

    /** 变更后值（展示值） */
    private String valueAfter;

    private LocalDateTime createdAt;

    public static ChangeItemResp from(ChangeOrderItem item) {
        ChangeItemResp resp = new ChangeItemResp();
        resp.setId(item.getId());
        resp.setOrderId(item.getOrderId());
        resp.setAssetId(item.getAssetId());
        resp.setAssetBarcode(item.getAssetBarcode());
        resp.setAssetName(item.getAssetName());
        resp.setAssetSn(item.getAssetSn());
        resp.setFieldName(item.getFieldName());
        resp.setFieldLabel(item.getFieldLabel());
        resp.setValueBefore(item.getValueBefore());
        resp.setValueAfter(item.getValueAfter());
        resp.setCreatedAt(item.getCreatedAt());
        return resp;
    }

    public static List<ChangeItemResp> fromList(List<ChangeOrderItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(ChangeItemResp::from).collect(Collectors.toList());
    }
}
