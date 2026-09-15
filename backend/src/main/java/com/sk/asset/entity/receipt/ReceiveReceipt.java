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

    /** 钉钉审批实例id（M10；NULL=站内审批通道，非空=已同步钉钉） */
    private String dingtalkInstanceId;

    /** 申请人ID（comm_public_basic 用户） */
    private Long applicantUserId;

    /** 申请人姓名（提交时快照） */
    private String applicantName;

    /**
     * 当前处理人ID（comm_public_basic 用户）。
     * 语义随两级审批链（V20260833）升级：两级链单据 = 当前审批层级快照审批人
     * （提交时=一级审批人，一级通过后推进为二级审批人，列表/统计/审批中心"待我处理"据此过滤）；
     * 存量单（approval_step2_user_id 为 NULL）保持 B1 旧语义：NULL=共享池，非 NULL=指定处理人。
     */
    private Long assigneeUserId;

    /** 当前审批层级：1=待部门主管审 2=待领料仓管理员审（两级同一人时提交即置 2 合并为一次审批） */
    private Integer approvalStep;

    /** 一级审批人ID（部门主管，提交时解析冻结快照；存量单回填 assignee） */
    private Long approvalStep1UserId;

    /** 一级审批人姓名（提交时快照） */
    private String approvalStep1Name;

    /** 一级审批通过时间（两级同一人合并审批时=提交时间） */
    private LocalDateTime approvalStep1At;

    /** 一级审批意见（预留列，当前 approve 接口无意见入参） */
    private String approvalStep1Remark;

    /** 一级解析依据快照（命中的部门路径配置键，审计追溯） */
    private String approvalStep1SourceKey;

    /** 二级审批人ID（领料仓管理员，提交时解析冻结快照；NULL=存量单走旧单层审批语义） */
    private Long approvalStep2UserId;

    /** 二级审批人姓名（提交时快照） */
    private String approvalStep2Name;

    /** 二级解析依据快照（命中的位置id配置键，审计追溯） */
    private String approvalStep2SourceKey;

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
