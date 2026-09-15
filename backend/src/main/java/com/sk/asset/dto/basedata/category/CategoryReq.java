package com.sk.asset.dto.basedata.category;

import com.sk.asset.entity.basedata.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资产分类请求 DTO
 */
@Data
public class CategoryReq {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 200, message = "分类名称长度不能超过 200")
    private String name;

    @Size(max = 50, message = "分类编码长度不能超过 50")
    private String code;

    /** 资产编码前缀（新增资产自动生成编码用，空则沿父链向上继承） */
    @Size(max = 20, message = "编码前缀长度不能超过 20")
    private String barcodePrefix;

    private Long parentId;

    private Integer sortOrder;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    public Category toEntity() {
        Category entity = new Category();
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setBarcodePrefix(this.barcodePrefix);
        entity.setParentId(this.parentId);
        entity.setSortOrder(this.sortOrder);
        entity.setRemark(this.remark);
        return entity;
    }

    public void updateEntity(Category entity) {
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setBarcodePrefix(this.barcodePrefix);
        entity.setParentId(this.parentId);
        entity.setSortOrder(this.sortOrder);
        entity.setRemark(this.remark);
    }
}
