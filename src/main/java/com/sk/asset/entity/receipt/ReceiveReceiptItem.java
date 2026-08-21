package com.sk.asset.entity.receipt;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领用/借用单明细（一单对多资产）。资产编码/名称/序列号仅用于查询回填，非表列。
 */
@Data
@TableName("receive_receipt_item")
public class ReceiveReceiptItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 领用单（receive_receipt.id） */
    private Long receiptId;

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
