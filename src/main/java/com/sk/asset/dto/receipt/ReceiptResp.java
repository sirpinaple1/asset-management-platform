package com.sk.asset.dto.receipt;

import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.receipt.ReceiptType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 领用/借用单响应（列表与详情共用；列表也回填 items 供前端展示资产数）。
 */
@Data
public class ReceiptResp {

    private Long id;

    /** 单号：ARE/BOR + yyyyMMdd + 4位序号 */
    private String serialNo;

    private String status;

    private String statusLabel;

    /** RECEIVE-领用 BORROW-借用 */
    private String type;

    private String typeLabel;

    private Long applicantUserId;

    /** 申请人姓名（提交时快照，空时前端回退展示用户 ID） */
    private String applicantName;

    /**
     * 当前处理人 ID（两级链单据 = 当前审批层级快照审批人：一级通过后自动推进为二级审批人；
     * 存量单保持 B1 旧语义：NULL=共享池）——审批中心"待我处理"据此过滤，前端共享池逻辑零改动
     */
    private Long assigneeUserId;

    /** 当前审批层级：1=待部门主管审 2=待领料仓管理员审（REJECTED 单保留拒绝时的层级，可据此展示拒绝层级） */
    private Integer approvalStep;

    private Long approvalStep1UserId;

    /** 一级审批人姓名（部门主管，提交时快照） */
    private String approvalStep1UserName;

    /** 一级审批通过时间（两级同一人合并审批时=提交时间） */
    private LocalDateTime approvalStep1At;

    /** 一级审批意见（预留，当前接口无意见入参；合并审批时为合并说明） */
    private String approvalStep1Remark;

    private Long approvalStep2UserId;

    /** 二级审批人姓名（领料仓管理员，提交时快照；NULL=存量单走旧单层审批） */
    private String approvalStep2UserName;

    private String department;

    /** 领用区域 ID（审批通过后资产位置更新至此） */
    private Long locationId;

    /** 领用区域名称（列表/详情回填） */
    private String locationName;

    private String reason;

    private Long approverUserId;

    /** 审批人姓名（审批时快照） */
    private String approverName;

    private LocalDateTime approveTime;

    /** 审批意见 / 拒绝原因 */
    private String approveRemark;

    private Long companyId;

    /** 明细行（含资产编码/名称/序列号） */
    private List<ReceiptItemResp> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ReceiptResp from(ReceiveReceipt entity) {
        ReceiptResp resp = new ReceiptResp();
        resp.setId(entity.getId());
        resp.setSerialNo(entity.getSerialNo());
        resp.setStatus(entity.getStatus());
        resp.setStatusLabel(ReceiptStatus.of(entity.getStatus()).getLabel());
        resp.setType(entity.getType());
        resp.setTypeLabel(ReceiptType.of(entity.getType()).getLabel());
        resp.setApplicantUserId(entity.getApplicantUserId());
        resp.setApplicantName(entity.getApplicantName());
        resp.setAssigneeUserId(entity.getAssigneeUserId());
        resp.setApprovalStep(entity.getApprovalStep());
        resp.setApprovalStep1UserId(entity.getApprovalStep1UserId());
        resp.setApprovalStep1UserName(entity.getApprovalStep1Name());
        resp.setApprovalStep1At(entity.getApprovalStep1At());
        resp.setApprovalStep1Remark(entity.getApprovalStep1Remark());
        resp.setApprovalStep2UserId(entity.getApprovalStep2UserId());
        resp.setApprovalStep2UserName(entity.getApprovalStep2Name());
        resp.setDepartment(entity.getDepartment());
        resp.setLocationId(entity.getLocationId());
        resp.setLocationName(entity.getLocationName());
        resp.setReason(entity.getReason());
        resp.setApproverUserId(entity.getApproverUserId());
        resp.setApproverName(entity.getApproverName());
        resp.setApproveTime(entity.getApproveTime());
        resp.setApproveRemark(entity.getApproveRemark());
        resp.setCompanyId(entity.getCompanyId());
        resp.setItems(ReceiptItemResp.fromList(entity.getItems()));
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
