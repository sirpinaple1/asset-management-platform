package com.sk.asset.enums.change;

/**
 * 变更单状态机（M06，AOC 单）。
 *
 * （发起）→ PENDING
 * PENDING → CONFIRMED  （确认执行：更新 asset 归属字段 + 同步持有关系 + 写日志）
 * PENDING → CANCELLED  （发起方撤销：资产不变）
 * CONFIRMED / CANCELLED 为终态。
 * 变更单为信息修正单据，确认执行不要求确认人 ≠ 发起人（区别于 M04/M05 审批流，
 * 制单与执行常为同一资产管理员；确认人信息完整记录于 confirmer_user_id/confirmer_name）。
 */
public enum ChangeStatus {

    PENDING("待确认"),
    CONFIRMED("已执行"),
    CANCELLED("已撤销");

    private final String label;

    ChangeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param name 状态字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static ChangeStatus of(String name) {
        for (ChangeStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法变更单状态：" + name);
    }
}
