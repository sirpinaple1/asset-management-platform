package com.sk.asset.entity.basedata;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 折旧规则表（骨架，完整计算逻辑由 M09/Phase 4 实现）
 */
@Data
@TableName("depreciation_rule")
public class DepreciationRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String method;
    private Integer usefulLifeMonths;
    private BigDecimal salvageRate;
    private Long companyId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
