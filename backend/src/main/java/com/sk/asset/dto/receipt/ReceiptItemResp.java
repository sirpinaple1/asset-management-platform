package com.sk.asset.dto.receipt;

import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 领用/借用单明细行响应（含资产编码/名称/序列号）。
 */
@Data
public class ReceiptItemResp {

    private Long id;

    private Long receiptId;

    private Long assetId;

    /** 资产编码（查询回填） */
    private String assetBarcode;

    /** 资产名称（查询回填） */
    private String assetName;

    /** 资产序列号（查询回填） */
    private String assetSn;

    private LocalDateTime createdAt;

    public static ReceiptItemResp from(ReceiveReceiptItem item) {
        ReceiptItemResp resp = new ReceiptItemResp();
        resp.setId(item.getId());
        resp.setReceiptId(item.getReceiptId());
        resp.setAssetId(item.getAssetId());
        resp.setAssetBarcode(item.getAssetBarcode());
        resp.setAssetName(item.getAssetName());
        resp.setAssetSn(item.getAssetSn());
        resp.setCreatedAt(item.getCreatedAt());
        return resp;
    }

    public static List<ReceiptItemResp> fromList(List<ReceiveReceiptItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(ReceiptItemResp::from).collect(Collectors.toList());
    }
}
