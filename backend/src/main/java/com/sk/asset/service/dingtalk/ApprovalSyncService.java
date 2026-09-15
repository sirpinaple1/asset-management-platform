package com.sk.asset.service.dingtalk;

/**
 * 钉钉审批同步服务（M10 入口 A：系统建单 → 自动创建钉钉 OA 审批 + 抄送）。
 *
 * <p>消费 {@link com.sk.asset.dingtalk.event.OaSyncRequestedEvent}（AFTER_COMMIT），
 * 审批人由系统审批链快照/单据语义驱动（钉钉模板只定义表单不配审批人）；
 * 推送失败记 approval_instance（FAILED）+ 告警 systemAdmin，不回滚业务单据，
 * 站内审批通道保持可用（ADR-0007 D4）。</p>
 */
public interface ApprovalSyncService {

    /** 领用/借用单创建后同步钉钉（type 决定模板：receive/borrow） */
    void syncReceipt(Long receiptId);

    /** 调拨单创建后同步钉钉（审批人=调入方 toUserId；NULL 共享池不同步，走站内） */
    void syncTransfer(Long orderId);

    /** 实物变更单创建后同步钉钉（审批人=assigneeUserId；NULL 不同步，走站内） */
    void syncChange(Long orderId);
}
