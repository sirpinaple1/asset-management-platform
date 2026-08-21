package com.sk.asset.dto.asset;

import com.sk.asset.entity.asset.Asset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产请求 DTO。状态不开放编辑：新增固定 IDLE，变更走 changeStatus 业务方法。
 */
@Data
public class AssetReq {

    @NotBlank(message = "资产编码不能为空")
    @Size(max = 100, message = "资产编码长度不能超过 100")
    private String barcode;

    @NotBlank(message = "资产名称不能为空")
    @Size(max = 200, message = "资产名称长度不能超过 200")
    private String name;

    @Size(max = 100, message = "序列号长度不能超过 100")
    private String sn;

    private Long categoryId;
    private Long modelId;
    private Long supplierId;
    private Long locationId;

    /** 应归放位置 */
    private Long homeLocationId;

    @Size(max = 200, message = "存放位置明细长度不能超过 200")
    private String locationDetail;

    /** 使用人ID（comm_public_basic 用户） */
    private Long userId;

    @Size(max = 100, message = "使用人部门长度不能超过 100")
    private String userDepartment;

    /** 资产管理员ID（comm_public_basic 用户） */
    private Long adminUserId;

    private Long companyId;

    private LocalDate purchaseDate;

    /** 购入金额（元，可空——赠品等无采购价资产） */
    private BigDecimal amount;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    public Asset toEntity() {
        Asset entity = new Asset();
        entity.setBarcode(this.barcode);
        entity.setName(this.name);
        entity.setSn(this.sn);
        entity.setCategoryId(this.categoryId);
        entity.setModelId(this.modelId);
        entity.setSupplierId(this.supplierId);
        entity.setLocationId(this.locationId);
        entity.setHomeLocationId(this.homeLocationId);
        entity.setLocationDetail(this.locationDetail);
        entity.setUserId(this.userId);
        entity.setUserDepartment(this.userDepartment);
        entity.setAdminUserId(this.adminUserId);
        entity.setCompanyId(this.companyId);
        entity.setPurchaseDate(this.purchaseDate);
        entity.setAmount(this.amount);
        entity.setRemark(this.remark);
        return entity;
    }

    public void updateEntity(Asset entity) {
        entity.setBarcode(this.barcode);
        entity.setName(this.name);
        entity.setSn(this.sn);
        entity.setCategoryId(this.categoryId);
        entity.setModelId(this.modelId);
        entity.setSupplierId(this.supplierId);
        entity.setLocationId(this.locationId);
        entity.setHomeLocationId(this.homeLocationId);
        entity.setLocationDetail(this.locationDetail);
        entity.setUserId(this.userId);
        entity.setUserDepartment(this.userDepartment);
        entity.setAdminUserId(this.adminUserId);
        entity.setCompanyId(this.companyId);
        entity.setPurchaseDate(this.purchaseDate);
        entity.setAmount(this.amount);
        entity.setRemark(this.remark);
    }
}
