package com.sk.asset.entity.basedata;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产分类表（二级树）
 */
@Data
@TableName("asset_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String code;

    /** 资产编码前缀（新增资产自动生成编码用，空则回退 SK） */
    private String barcodePrefix;

    private Long parentId;
    private Integer sortOrder;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
