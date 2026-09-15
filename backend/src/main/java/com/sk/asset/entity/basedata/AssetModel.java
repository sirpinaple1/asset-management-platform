package com.sk.asset.entity.basedata;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产型号表
 */
@Data
@TableName("asset_model")
public class AssetModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String modelNumber;
    private Long categoryId;
    private Long manufacturerId;
    private Long depreciationId;
    private Integer eolMonths;
    private String notes;
    private Long companyId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // --- JOIN 查询填充字段（非数据库列）---

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String manufacturerName;

    @TableField(exist = false)
    private String depreciationRuleName;
}
