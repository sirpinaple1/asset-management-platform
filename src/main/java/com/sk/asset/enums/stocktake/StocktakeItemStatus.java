package com.sk.asset.enums.stocktake;

/**
 * 盘点明细状态机（M07）。明细状态单向流转（已盘项不可重盘）：
 *
 * PENDING → MATCHED          （实物与系统记录位置一致）
 *         → LOCATION_MISMATCH（实物在其它位置，可触发调拨归位）
 *         → NOT_FOUND        （盘亏：未找到实物）
 * EXTRA                        （盘盈：扫码发现的资产不在本任务范围内，行由扫码时新建）
 */
public enum StocktakeItemStatus {

    PENDING("待盘"),
    MATCHED("账实相符"),
    LOCATION_MISMATCH("位置不符"),
    NOT_FOUND("盘亏"),
    EXTRA("盘盈");

    private final String label;

    StocktakeItemStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param name 状态字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static StocktakeItemStatus of(String name) {
        for (StocktakeItemStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法盘点明细状态：" + name);
    }
}
