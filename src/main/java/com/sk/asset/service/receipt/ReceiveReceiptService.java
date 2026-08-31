package com.sk.asset.service.receipt;

import com.sk.asset.dto.receipt.ApprovalPreviewResp;
import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.receipt.ReceiptQuery;
import com.sk.asset.entity.receipt.ReceiveReceipt;

import java.util.List;

/**
 * 领用/借用单服务（M04）：申请→审批（批准/拒绝）→资产状态联动。
 * 状态变更与日志统一经 AssetService.changeStatus（事务内）。
 * 两级审批链（V20260833）：提交时解析并冻结两级审批人快照，
 * 单据状态保持 PENDING、approval_step 区分层级（1=部门主管，2=领料仓管理员）。
 */
public interface ReceiveReceiptService {

    /**
     * 发起申请：审批链解析（两级审批人快照冻结，任一级解析不到 400 阻止提交）+
     * 资产存在性 + 待审批占用校验 → 生成 serial_no →
     * 写主表与明细 → 资产 IDLE/IN_USE → PENDING_CONFIRM（写日志）。
     *
     * @param req              申请内容（type/assetIds/department/reason；assigneeUserId 已废弃忽略）
     * @param applicantUserId  申请人（UserContext）
     * @param applicantName    申请人姓名（提交时快照）
     */
    ReceiveReceipt create(ReceiptApplyReq req, Long applicantUserId, String applicantName);

    /** 发起预解析：返回当前用户 + 领用区域解析出的两级审批人（解析失败返回 resolvable=false + 提示，不抛 400） */
    ApprovalPreviewResp previewApprovalChain(Long applicantUserId, String applicantName, Long locationId);

    /** 列表（含明细与资产名称回填），支持 type/status/userId/date 筛选 */
    List<ReceiveReceipt> list(ReceiptQuery query);

    /** 详情（含明细行）；不存在返回 null */
    ReceiveReceipt getById(Long id);

    /**
     * 批准：PENDING 校验 + 审批人≠申请人 → 资产 PENDING_CONFIRM → IN_USE（写日志）→
     * 更新 asset 持有人 → 写 asset_allocation → 单据置 APPROVED。
     * 两级链单据：approval_step=1 时推进层级并通知二级审批人；=2 时走上述终态逻辑。
     * 存量单（无链快照）：保持旧单层语义（assignee/共享池一次审批终态）。
     */
    ReceiveReceipt approve(Long id, Long approverUserId, String approverName);

    /** 拒绝：PENDING 校验 + 审批人≠申请人 → 资产 PENDING_CONFIRM → IDLE（写日志）→ 单据置 REJECTED（通知含拒绝层级） */
    ReceiveReceipt reject(Long id, String reason, Long approverUserId, String approverName);
}
