package com.sk.asset.enums.asset;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 资产状态机（M03）。
 *
 * 合法流转：
 *   新增 → IDLE
 *   IDLE → IN_USE（领用）/ DISCARD（报废）/ PENDING_CONFIRM（发起申请）
 *   IN_USE → IDLE（归还）/ DISCARD（报废）/ PENDING_CONFIRM（发起申请）
 *   PENDING_CONFIRM → IN_USE（审批通过）/ IDLE（审批拒绝）
 *   DISCARD 为终态，不可再流转
 *
 * 除"新增"外，状态变更不由 AssetService 自行触发——M04/M05 调
 * AssetService.changeStatus() 完成，该方法同时写 asset_log。
 */
public enum AssetStatus {

    IDLE("闲置"),
    IN_USE("在用"),
    PENDING_CONFIRM("待确认"),
    DISCARD("报废");

    private final String label;

    private static final Map<AssetStatus, Set<AssetStatus>> TRANSITIONS = Map.of(
            IDLE, EnumSet.of(IN_USE, DISCARD, PENDING_CONFIRM),
            IN_USE, EnumSet.of(IDLE, DISCARD, PENDING_CONFIRM),
            PENDING_CONFIRM, EnumSet.of(IN_USE, IDLE),
            DISCARD, EnumSet.noneOf(AssetStatus.class)
    );

    AssetStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @return 当前状态是否允许流转到目标状态 */
    public boolean canTransitionTo(AssetStatus target) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    /** @param name 状态字符串（大小写敏感），非法值抛 IllegalArgumentException */
    public static AssetStatus of(String name) {
        for (AssetStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        throw new IllegalArgumentException("非法资产状态：" + name);
    }
}
