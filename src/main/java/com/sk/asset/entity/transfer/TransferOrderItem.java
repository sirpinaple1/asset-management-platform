package com.sk.asset.entity.transfer;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调拨单明细（M05，一单对多资产）。
 * 资产编码/名称/序列号仅用于查询回填，非表列。
 */
@Data
@TableName("transfer_order_item")
public class TransferOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调拨单（transfer_order.id） */
    private Long orderId;

    /** 资产（asset.id） */
    private Long assetId;

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
