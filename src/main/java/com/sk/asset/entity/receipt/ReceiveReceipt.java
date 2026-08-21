package com.sk.asset.entity.receipt;

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
 * 领用/借用单主表（M04，ARE/BOR 共用单据流）。items 仅用于查询回填，非表列。
 */
@Data
@TableName("receive_receipt")
public class ReceiveReceipt {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号：ARE（领用）/BOR（借用）+ yyyyMMdd + 4位序号 */
    private String serialNo;

    /** 类型：RECEIVE-领用 BORROW-借用（对应 ReceiptType 枚举） */
    private String type;

    /** 状态：PENDING-待审批 APPROVED-已批准 REJECTED-已拒绝（对应 ReceiptStatus 枚举） */
    private String status;

    /** 申请人ID（comm_public_basic 用户） */
    private Long applicantUserId;

    /** 申请人姓名（提交时快照） */
    private String applicantName;

    /** 领用/借用部门 */
    private String department;

    /** 领用区域（审批通过后资产位置更新至此；存量单为 NULL，审批时跳过位置更新） */
    private Long locationId;

    /** 事由 */
    private String reason;

    /** 审批人ID */
    private Long approverUserId;

    /** 审批人姓名（审批时快照） */
    private String approverName;

    private LocalDateTime approveTime;

    /** 审批意见 / 拒绝原因 */
    private String approveRemark;

    private Long companyId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 明细行（列表/详情查询时批量回填，含资产编码/名称/序列号） */
    @TableField(exist = false)
    private List<ReceiveReceiptItem> items;

    /** 领用区域名称（列表/详情查询时回填，展示用） */
    @TableField(exist = false)
    private String locationName;
}
