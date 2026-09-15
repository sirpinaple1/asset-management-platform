package com.sk.asset.enums.transfer;

/**
 * 调拨单状态机（M05）。
 *
 * （调出方发起）→ PENDING
 * PENDING → COMPLETED  （调入方确认收到：更新资产归属 + 闭环旧持有 + 新建调入方持有 + 写日志）
 * PENDING → CANCELLED  （发起方撤销：资产不变）
 * PENDING → REJECTED   （调入方拒绝接收：资产不变，记录拒绝原因）
 * COMPLETED / CANCELLED / REJECTED 为终态
 */
public enum TransferStatus {

    PENDING("待确认"),
    COMPLETED("已完成"),
    CANCELLED("已撤销"),
    REJECTED("已拒绝");

    private final String label;

    TransferStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param name 状态字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static TransferStatus of(String name) {
        for (TransferStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法调拨单状态：" + name);
    }
}
