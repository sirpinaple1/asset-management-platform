package com.sk.asset.entity.asset;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产主表（ADR-0006 D1）。关联名称字段仅用于 JOIN 查询回填，非表列。
 */
@Data
@TableName("asset")
public class Asset {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产编码（条码，唯一，如 SKSCDM-xxxx） */
    private String barcode;

    private String name;

    /** 序列号（SN） */
    private String sn;

    /** 细则（同品牌型号的配置差异，如内存大小） */
    private String spec;

    /** 状态：IDLE-闲置 IN_USE-在用 DISCARD-报废 PENDING_CONFIRM-待确认（对应 AssetStatus 枚举） */
    private String status;

    private Long categoryId;
    private Long modelId;
    private Long supplierId;

    /** 当前位置（asset_location.id） */
    private Long locationId;

    /** 应归放位置（asset_location.id，可空） */
    private Long homeLocationId;

    /** 存放位置明细（楼层/房间等） */
    private String locationDetail;

    /** 使用人ID（comm_public_basic 用户，仅存 ID 引用） */
    private Long userId;

    /** 使用人部门（快照） */
    private String userDepartment;

    /** 资产管理员ID（comm_public_basic 用户） */
    private Long adminUserId;

    private Long companyId;

    private LocalDate purchaseDate;

    /** 购入金额（元） */
    private BigDecimal amount;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ---- 以下为 JOIN 查询回填字段（非表列） ----

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String modelName;

    @TableField(exist = false)
    private String supplierName;

    @TableField(exist = false)
    private String locationName;

    @TableField(exist = false)
    private String homeLocationName;

    @TableField(exist = false)
    private String companyName;

    /** 使用人姓名（实时反查 comm_public_basic sys_user 回填，未命中为 null） */
    @TableField(exist = false)
    private String userName;

    /** 资产管理员姓名（实时反查 comm_public_basic sys_user 回填，未命中为 null） */
    @TableField(exist = false)
    private String adminUserName;
}
