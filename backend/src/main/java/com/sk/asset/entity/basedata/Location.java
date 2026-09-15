package com.sk.asset.entity.basedata;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区域位置表（多级树 + materialized path）
 */
@Data
@TableName("asset_location")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String code;
    private Long parentId;
    private String path;
    private Integer sortOrder;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
