package com.sk.asset.enums.stocktake;

/**
 * 盘点任务状态机（M07）。
 *
 * （创建）→ PENDING
 * PENDING → IN_PROGRESS   （开始盘点）
 * PENDING / IN_PROGRESS → CANCELLED  （创建人撤销，明细与资产不变）
 * IN_PROGRESS → COMPLETED （完成盘点：剩余未盘明细记为盘亏 NOT_FOUND）
 * COMPLETED / CANCELLED 为终态
 */
public enum StocktakeStatus {

    PENDING("待开始"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    StocktakeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param name 状态字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static StocktakeStatus of(String name) {
        for (StocktakeStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法盘点任务状态：" + name);
    }
}
