package com.sk.asset.enums.receipt;

/**
 * 领用/借用单状态机（M04）。
 *
 * （申请人提交）→ PENDING
 * PENDING → APPROVED  （审批通过：资产 PENDING_CONFIRM → IN_USE，写 asset_allocation）
 * PENDING → REJECTED  （拒绝：资产 PENDING_CONFIRM → IDLE）
 * APPROVED / REJECTED 为终态
 */
public enum ReceiptStatus {

    PENDING("待审批"),
    APPROVED("已批准"),
    REJECTED("已拒绝");

    private final String label;

    ReceiptStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param name 状态字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static ReceiptStatus of(String name) {
        for (ReceiptStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法单据状态：" + name);
    }
}
