package com.sk.asset.entity.change;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 变更单明细（M06，一单对多资产，每台资产的每个实际变更字段一行）。
 * value_before / value_after 存展示值（位置/公司存名称、使用人存姓名），
 * 供前端变更前/后对比展示与 AOC 打印格式；资产编码/名称/序列号仅用于查询回填，非表列。
 */
@Data
@TableName("change_order_item")
public class ChangeOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 变更单（change_order.id） */
    private Long orderId;

    /** 资产（asset.id） */
    private Long assetId;

    /** 变更字段名（白名单见 ChangeField 枚举，如 user_id / location_id） */
    private String fieldName;

    /** 字段展示名（如 使用人 / 区域） */
    private String fieldLabel;

    /** 变更前值（发起时快照的展示值） */
    private String valueBefore;

    /** 变更后值（展示值） */
    private String valueAfter;

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
