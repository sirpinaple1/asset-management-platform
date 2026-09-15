package com.sk.asset.dto.basedata.model;

import com.sk.asset.entity.basedata.AssetModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资产型号请求 DTO
 */
@Data
public class AssetModelReq {

    @NotBlank(message = "型号名称不能为空")
    @Size(max = 200, message = "型号名称长度不能超过 200")
    private String name;

    @Size(max = 100, message = "型号编号长度不能超过 100")
    private String modelNumber;

    private Long categoryId;
    private Long manufacturerId;
    private Long depreciationId;
    private Integer eolMonths;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    private Long companyId;

    public AssetModel toEntity() {
        AssetModel entity = new AssetModel();
        entity.setName(this.name);
        entity.setModelNumber(this.modelNumber);
        entity.setCategoryId(this.categoryId);
        entity.setManufacturerId(this.manufacturerId);
        entity.setDepreciationId(this.depreciationId);
        entity.setEolMonths(this.eolMonths);
        entity.setNotes(this.notes);
        entity.setCompanyId(this.companyId);
        return entity;
    }

    public void updateEntity(AssetModel entity) {
        entity.setName(this.name);
        entity.setModelNumber(this.modelNumber);
        entity.setCategoryId(this.categoryId);
        entity.setManufacturerId(this.manufacturerId);
        entity.setDepreciationId(this.depreciationId);
        entity.setEolMonths(this.eolMonths);
        entity.setNotes(this.notes);
        entity.setCompanyId(this.companyId);
    }
}
