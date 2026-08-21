package com.sk.asset.entity.receipt;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产持有关系快照（M04 审批通过时写入，"查现在在谁手里"）。
 * 资产编码/名称/序列号仅用于查询回填，非表列。
 */
@Data
@TableName("asset_allocation")
public class AssetAllocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产（asset.id） */
    private Long assetId;

    /** 持有人ID（comm_public_basic 用户） */
    private Long userId;

    /** 持有人姓名（发放时快照） */
    private String userName;

    /** 持有性质：RECEIVE-领用 BORROW-借用（对应 ReceiptType 枚举） */
    private String type;

    /** 持有人部门（快照） */
    private String department;

    /** 发放时间 */
    private LocalDateTime allocatedAt;

    /** 归还时间（空=持有中） */
    private LocalDateTime returnedAt;

    private String note;

    private Long companyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ---- 以下为查询回填字段（非表列） ----

    @TableField(exist = false)
    private String assetBarcode;

    @TableField(exist = false)
    private String assetName;

    @TableField(exist = false)
    private String assetSn;
}
