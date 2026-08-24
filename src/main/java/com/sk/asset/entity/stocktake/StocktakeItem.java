package com.sk.asset.entity.stocktake;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 盘点明细（M07，每台资产一行）。资产与位置名称仅用于查询回填，非表列。
 * 盘盈（EXTRA）行为扫码发现范围外资产时新建，expected 位置 = 该资产当时账面位置。
 */
@Data
@TableName("stocktake_item")
public class StocktakeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点任务（stocktake.id） */
    private Long stocktakeId;

    /** 资产（asset.id；盘盈资产为任务范围外但已登记的资产） */
    private Long assetId;

    /** 状态：PENDING-待盘 MATCHED-账实相符 LOCATION_MISMATCH-位置不符 NOT_FOUND-盘亏 EXTRA-盘盈（对应 StocktakeItemStatus 枚举） */
    private String status;

    /** 系统记录位置（asset_location.id，创建任务时快照） */
    private Long expectedLocationId;

    /** 盘点实际位置（asset_location.id） */
    private Long actualLocationId;

    /** 扫码/确认时间 */
    private LocalDateTime scannedAt;

    /** 盘点人ID（comm_public_basic 用户） */
    private Long scannedByUserId;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ---- 以下为查询回填字段（非表列） ----

    @TableField(exist = false)
    private String assetBarcode;

    @TableField(exist = false)
    private String assetName;

    @TableField(exist = false)
    private String assetSn;

    /** 系统记录位置名称（查询回填） */
    @TableField(exist = false)
    private String expectedLocationName;

    /** 盘点实际位置名称（查询回填） */
    @TableField(exist = false)
    private String actualLocationName;
}
