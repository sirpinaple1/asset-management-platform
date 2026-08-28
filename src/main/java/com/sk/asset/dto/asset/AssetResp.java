package com.sk.asset.dto.asset;

import com.sk.asset.entity.asset.Asset;
import com.sk.asset.enums.asset.AssetStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产响应 DTO（含关联名称与状态展示名）
 */
@Data
public class AssetResp {

    private Long id;
    private String barcode;
    private String name;
    private String sn;

    /** 细则（同品牌型号的配置差异，如内存大小） */
    private String spec;

    /** 状态编码（IDLE/IN_USE/PENDING_CONFIRM/DISCARD） */
    private String status;

    /** 状态展示名（闲置/在用/待确认/报废） */
    private String statusLabel;

    private Long categoryId;
    private String categoryName;
    private Long modelId;
    private String modelName;
    private Long supplierId;
    private String supplierName;
    private Long locationId;
    private String locationName;
    private Long homeLocationId;
    private String homeLocationName;
    private String locationDetail;
    private Long userId;
    private String userDepartment;
    private Long adminUserId;
    private Long companyId;
    private String companyName;
    private LocalDate purchaseDate;
    private BigDecimal amount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AssetResp from(Asset entity) {
        AssetResp resp = new AssetResp();
        resp.setId(entity.getId());
        resp.setBarcode(entity.getBarcode());
        resp.setName(entity.getName());
        resp.setSn(entity.getSn());
        resp.setSpec(entity.getSpec());
        resp.setStatus(entity.getStatus());
        resp.setStatusLabel(AssetStatus.of(entity.getStatus()).getLabel());
        resp.setCategoryId(entity.getCategoryId());
        resp.setCategoryName(entity.getCategoryName());
        resp.setModelId(entity.getModelId());
        resp.setModelName(entity.getModelName());
        resp.setSupplierId(entity.getSupplierId());
        resp.setSupplierName(entity.getSupplierName());
        resp.setLocationId(entity.getLocationId());
        resp.setLocationName(entity.getLocationName());
        resp.setHomeLocationId(entity.getHomeLocationId());
        resp.setHomeLocationName(entity.getHomeLocationName());
        resp.setLocationDetail(entity.getLocationDetail());
        resp.setUserId(entity.getUserId());
        resp.setUserDepartment(entity.getUserDepartment());
        resp.setAdminUserId(entity.getAdminUserId());
        resp.setCompanyId(entity.getCompanyId());
        resp.setCompanyName(entity.getCompanyName());
        resp.setPurchaseDate(entity.getPurchaseDate());
        resp.setAmount(entity.getAmount());
        resp.setRemark(entity.getRemark());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
