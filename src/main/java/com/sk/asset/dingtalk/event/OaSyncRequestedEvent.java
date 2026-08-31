package com.sk.asset.dingtalk.event;

/**
 * 单据已创建、待同步钉钉审批的事件（M10 入口 A）。
 *
 * <p>业务 Service 在单据事务内发布，监听方以 AFTER_COMMIT 相位消费：
 * 钉钉推送失败只记 FAILED + 告警，不回滚已提交的业务单据（ADR-0007 D4 outbox 语义，
 * 站内审批兜底）。</p>
 *
 * @param kind  单据类别（本事件层的类别常量，见下）
 * @param bizId 单据主键
 */
public record OaSyncRequestedEvent(String kind, Long bizId) {

    /** 领用/借用单（receive_receipt，impl 内按 type 再分 RECEIVE/BORROW） */
    public static final String KIND_RECEIPT = "RECEIPT";

    /** 调拨单（transfer_order） */
    public static final String KIND_TRANSFER = "TRANSFER";

    /** 实物信息变更单（change_order） */
    public static final String KIND_CHANGE = "CHANGE";
}
