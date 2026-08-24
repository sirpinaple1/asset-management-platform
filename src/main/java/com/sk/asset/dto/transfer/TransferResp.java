package com.sk.asset.dto.transfer;

import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.transfer.TransferSource;
import com.sk.asset.enums.transfer.TransferStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调拨单响应（列表与详情共用；列表也回填 items 供前端展示资产数）。
 */
@Data
public class TransferResp {

    private Long id;

    /** 单号：ATR + yyyyMMdd + 4位序号 */
    private String serialNo;

    private String status;

    private String statusLabel;

    /** MANUAL-手动调拨 INVENTORY_TRIGGERED-盘点触发 */
    private String source;

    private String sourceLabel;

    /** 关联盘点任务（stocktake.id，盘点触发调拨时回填；手动调拨为空） */
    private Long stocktakeId;

    private Long applicantUserId;

    /** 发起人姓名（提交时快照，空时前端回退展示用户 ID） */
    private String applicantName;

    private Long fromLocationId;

    /** 调出位置名称（查询回填） */
    private String fromLocationName;

    private Long fromUserId;

    /** 调出方管理员姓名（快照） */
    private String fromUserName;

    private Long toLocationId;

    /** 调入位置名称（查询回填） */
    private String toLocationName;

    private String toDepartment;

    private Long toUserId;

    /** 调入方负责人姓名（发起时快照） */
    private String toUserName;

    private String reason;

    private Long confirmerUserId;

    /** 确认人姓名（确认/拒绝时快照） */
    private String confirmerName;

    private LocalDateTime confirmTime;

    /** 拒绝原因（调入方拒绝时记录） */
    private String rejectReason;

    private Long companyId;

    /** 明细行（含资产编码/名称/序列号） */
    private List<TransferItemResp> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static TransferResp from(TransferOrder entity) {
        TransferResp resp = new TransferResp();
        resp.setId(entity.getId());
        resp.setSerialNo(entity.getSerialNo());
        resp.setStatus(entity.getStatus());
        resp.setStatusLabel(TransferStatus.of(entity.getStatus()).getLabel());
        resp.setSource(entity.getSource());
        resp.setSourceLabel(entity.getSource() == null
                ? null : TransferSource.of(entity.getSource()).getLabel());
        resp.setStocktakeId(entity.getStocktakeId());
        resp.setApplicantUserId(entity.getApplicantUserId());
        resp.setApplicantName(entity.getApplicantName());
        resp.setFromLocationId(entity.getFromLocationId());
        resp.setFromLocationName(entity.getFromLocationName());
        resp.setFromUserId(entity.getFromUserId());
        resp.setFromUserName(entity.getFromUserName());
        resp.setToLocationId(entity.getToLocationId());
        resp.setToLocationName(entity.getToLocationName());
        resp.setToDepartment(entity.getToDepartment());
        resp.setToUserId(entity.getToUserId());
        resp.setToUserName(entity.getToUserName());
        resp.setReason(entity.getReason());
        resp.setConfirmerUserId(entity.getConfirmerUserId());
        resp.setConfirmerName(entity.getConfirmerName());
        resp.setConfirmTime(entity.getConfirmTime());
        resp.setRejectReason(entity.getRejectReason());
        resp.setCompanyId(entity.getCompanyId());
        resp.setItems(TransferItemResp.fromList(entity.getItems()));
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
