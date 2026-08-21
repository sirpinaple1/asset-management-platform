package com.sk.asset.dto.receipt;

import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.enums.receipt.ReceiptType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产持有关系响应（含资产编码/名称/序列号）。
 */
@Data
public class AllocationResp {

    private Long id;

    private Long assetId;

    private String assetBarcode;

    private String assetName;

    private String assetSn;

    /** 持有人 ID（comm_public_basic 用户） */
    private Long userId;

    /** 持有人姓名（发放时快照） */
    private String userName;

    /** RECEIVE-领用 BORROW-借用 */
    private String type;

    private String typeLabel;

    private String department;

    private LocalDateTime allocatedAt;

    /** 归还时间（空=持有中） */
    private LocalDateTime returnedAt;

    /** true-持有中 false-已归还 */
    private Boolean active;

    private String note;

    private Long companyId;

    private LocalDateTime createdAt;

    public static AllocationResp from(AssetAllocation entity) {
        AllocationResp resp = new AllocationResp();
        resp.setId(entity.getId());
        resp.setAssetId(entity.getAssetId());
        resp.setAssetBarcode(entity.getAssetBarcode());
        resp.setAssetName(entity.getAssetName());
        resp.setAssetSn(entity.getAssetSn());
        resp.setUserId(entity.getUserId());
        resp.setUserName(entity.getUserName());
        resp.setType(entity.getType());
        resp.setTypeLabel(ReceiptType.of(entity.getType()).getLabel());
        resp.setDepartment(entity.getDepartment());
        resp.setAllocatedAt(entity.getAllocatedAt());
        resp.setReturnedAt(entity.getReturnedAt());
        resp.setActive(entity.getReturnedAt() == null);
        resp.setNote(entity.getNote());
        resp.setCompanyId(entity.getCompanyId());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}
