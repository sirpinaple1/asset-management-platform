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

    /** 指定处理人 ID（NULL=共享池） */
    private Long assigneeUserId;

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
