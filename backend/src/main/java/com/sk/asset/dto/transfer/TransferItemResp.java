package com.sk.asset.dto.transfer;

import com.sk.asset.entity.transfer.TransferOrderItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 调拨单明细行响应（含资产编码/名称/序列号）。
 */
@Data
public class TransferItemResp {

    private Long id;

    private Long orderId;

    private Long assetId;

    /** 资产编码（查询回填） */
    private String assetBarcode;

    /** 资产名称（查询回填） */
    private String assetName;

    /** 资产序列号（查询回填） */
    private String assetSn;

    private LocalDateTime createdAt;

    public static TransferItemResp from(TransferOrderItem item) {
        TransferItemResp resp = new TransferItemResp();
        resp.setId(item.getId());
        resp.setOrderId(item.getOrderId());
        resp.setAssetId(item.getAssetId());
        resp.setAssetBarcode(item.getAssetBarcode());
        resp.setAssetName(item.getAssetName());
        resp.setAssetSn(item.getAssetSn());
        resp.setCreatedAt(item.getCreatedAt());
        return resp;
    }

    public static List<TransferItemResp> fromList(List<TransferOrderItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(TransferItemResp::from).collect(Collectors.toList());
    }
}
