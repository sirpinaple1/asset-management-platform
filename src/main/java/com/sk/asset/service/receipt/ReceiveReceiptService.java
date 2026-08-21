package com.sk.asset.service.receipt;

import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.receipt.ReceiptQuery;
import com.sk.asset.entity.receipt.ReceiveReceipt;

import java.util.List;

/**
 * 领用/借用单服务（M04）：申请→审批（批准/拒绝）→资产状态联动。
 * 状态变更与日志统一经 AssetService.changeStatus（事务内）。
 */
public interface ReceiveReceiptService {

    /**
     * 发起申请：资产存在性 + 待审批占用校验 → 生成 serial_no →
     * 写主表与明细 → 资产 IDLE/IN_USE → PENDING_CONFIRM（写日志）。
     *
     * @param req              申请内容（type/assetIds/department/reason）
     * @param applicantUserId  申请人（UserContext）
     * @param applicantName    申请人姓名（提交时快照）
     */
    ReceiveReceipt create(ReceiptApplyReq req, Long applicantUserId, String applicantName);

    /** 列表（含明细与资产名称回填），支持 type/status/userId/date 筛选 */
    List<ReceiveReceipt> list(ReceiptQuery query);

    /** 详情（含明细行）；不存在返回 null */
    ReceiveReceipt getById(Long id);

    /**
     * 批准：PENDING 校验 + 审批人≠申请人 → 资产 PENDING_CONFIRM → IN_USE（写日志）→
     * 更新 asset 持有人 → 写 asset_allocation → 单据置 APPROVED。
     */
    ReceiveReceipt approve(Long id, Long approverUserId, String approverName);

    /** 拒绝：PENDING 校验 + 审批人≠申请人 → 资产 PENDING_CONFIRM → IDLE（写日志）→ 单据置 REJECTED */
    ReceiveReceipt reject(Long id, String reason, Long approverUserId, String approverName);
}
