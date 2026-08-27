package com.sk.asset.entity.transfer;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调拨单主表（M05，ATR 单）。items 与位置名称仅用于查询回填，非表列。
 */
@Data
@TableName("transfer_order")
public class TransferOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号：ATR + yyyyMMdd + 4位序号 */
    private String serialNo;

    /** 状态：PENDING-待确认 COMPLETED-已完成 CANCELLED-已撤销 REJECTED-已拒绝（对应 TransferStatus 枚举） */
    private String status;

    /** 来源：MANUAL-手动调拨 INVENTORY_TRIGGERED-盘点触发（对应 TransferSource 枚举） */
    private String source;

    /** 关联盘点任务（stocktake.id，盘点差异触发调拨时写入；手动调拨为空） */
    private Long stocktakeId;

    /** 发起人ID（调出方，comm_public_basic 用户） */
    private Long applicantUserId;

    /** 发起人姓名（提交时快照） */
    private String applicantName;

    /** 指定处理人ID（comm_public_basic 用户，NULL=共享池：任何非发起人可确认/拒绝；B1 定向待办） */
    private Long assigneeUserId;

    /** 调出位置（asset_location.id，未显式指定时取首台资产当前位置） */
    private Long fromLocationId;

    /** 调出方管理员ID */
    private Long fromUserId;

    /** 调出方管理员姓名（快照） */
    private String fromUserName;

    /** 调入位置（asset_location.id） */
    private Long toLocationId;

    /** 调入部门 */
    private String toDepartment;

    /** 调入方负责人ID */
    private Long toUserId;

    /** 调入方负责人姓名（发起时快照） */
    private String toUserName;

    /** 调拨原因 */
    private String reason;

    /** 确认人ID（调入方，确认/拒绝时写入） */
    private Long confirmerUserId;

    /** 确认人姓名（确认/拒绝时快照） */
    private String confirmerName;

    /** 确认/拒绝时间 */
    private LocalDateTime confirmTime;

    /** 拒绝原因（调入方拒绝时记录） */
    private String rejectReason;

    private Long companyId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ---- 以下为查询回填字段（非表列） ----

    @TableField(exist = false)
    private String fromLocationName;

    @TableField(exist = false)
    private String toLocationName;

    /** 明细行（列表/详情查询时批量回填，含资产编码/名称/序列号） */
    @TableField(exist = false)
    private List<TransferOrderItem> items;
}
