package com.sk.asset.dto.change;

import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.enums.change.ChangeStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更单响应（列表与详情共用；列表也回填 items 供前端展示变更明细数）。
 */
@Data
public class ChangeResp {

    private Long id;

    /** 单号：AOC + yyyyMMdd + 4位序号 */
    private String serialNo;

    private String status;

    private String statusLabel;

    private Long applicantUserId;

    /** 发起人姓名（提交时快照，空时前端回退展示用户 ID） */
    private String applicantName;

    /** 指定处理人 ID（NULL=共享池） */
    private Long assigneeUserId;

    private String reason;

    /** 变更后使用人 ID（null = 不变更） */
    private Long newUserId;

    /** 变更后使用人姓名（发起时快照） */
    private String newUserName;

    /** 变更后使用人部门（null = 不变更） */
    private String newUserDepartment;

    /** 变更后位置 ID（null = 不变更） */
    private Long newLocationId;

    /** 变更后位置名称（查询回填） */
    private String newLocationName;

    /** 变更后存放位置明细（null = 不变更） */
    private String newLocationDetail;

    /** 变更后归属公司 ID（null = 不变更） */
    private Long newCompanyId;

    /** 变更后归属公司名称（查询回填） */
    private String newCompanyName;

    private Long confirmerUserId;

    /** 确认人姓名（确认执行时快照） */
    private String confirmerName;

    /** 确认执行时间 */
    private LocalDateTime confirmTime;

    private Long companyId;

    /** 明细行（每台资产的每个实际变更字段一行，含变更前/后值） */
    private List<ChangeItemResp> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ChangeResp from(ChangeOrder entity) {
        ChangeResp resp = new ChangeResp();
        resp.setId(entity.getId());
        resp.setSerialNo(entity.getSerialNo());
        resp.setStatus(entity.getStatus());
        resp.setStatusLabel(ChangeStatus.of(entity.getStatus()).getLabel());
        resp.setApplicantUserId(entity.getApplicantUserId());
        resp.setApplicantName(entity.getApplicantName());
        resp.setAssigneeUserId(entity.getAssigneeUserId());
        resp.setReason(entity.getReason());
        resp.setNewUserId(entity.getNewUserId());
        resp.setNewUserName(entity.getNewUserName());
        resp.setNewUserDepartment(entity.getNewUserDepartment());
        resp.setNewLocationId(entity.getNewLocationId());
        resp.setNewLocationName(entity.getNewLocationName());
        resp.setNewLocationDetail(entity.getNewLocationDetail());
        resp.setNewCompanyId(entity.getNewCompanyId());
        resp.setNewCompanyName(entity.getNewCompanyName());
        resp.setConfirmerUserId(entity.getConfirmerUserId());
        resp.setConfirmerName(entity.getConfirmerName());
        resp.setConfirmTime(entity.getConfirmTime());
        resp.setCompanyId(entity.getCompanyId());
        resp.setItems(ChangeItemResp.fromList(entity.getItems()));
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
