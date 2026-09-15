package com.sk.asset.dto.basedata.model;

import com.sk.asset.entity.basedata.AssetModel;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产型号响应 DTO（含关联名称，避免前端二次查询）
 */
@Data
public class AssetModelResp {

    private Long id;
    private String name;
    private String modelNumber;
    private Long categoryId;
    private String categoryName;
    private Long manufacturerId;
    private String manufacturerName;
    private Long depreciationId;
    private String depreciationRuleName;
    private Integer eolMonths;
    private String notes;
    private Long companyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AssetModelResp from(AssetModel entity) {
        AssetModelResp resp = new AssetModelResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setModelNumber(entity.getModelNumber());
        resp.setCategoryId(entity.getCategoryId());
        resp.setCategoryName(entity.getCategoryName());
        resp.setManufacturerId(entity.getManufacturerId());
        resp.setManufacturerName(entity.getManufacturerName());
        resp.setDepreciationId(entity.getDepreciationId());
        resp.setDepreciationRuleName(entity.getDepreciationRuleName());
        resp.setEolMonths(entity.getEolMonths());
        resp.setNotes(entity.getNotes());
        resp.setCompanyId(entity.getCompanyId());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
