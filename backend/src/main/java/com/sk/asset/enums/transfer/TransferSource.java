package com.sk.asset.enums.transfer;

/**
 * 调拨单来源（M05）。
 * MANUAL-手动调拨（本模块发起）；INVENTORY_TRIGGERED-盘点触发
 * （M07 盘点发现资产在错误位置时自动创建，对应旧系统 ATR 单 "+盘点" 后缀场景）。
 */
public enum TransferSource {

    MANUAL("手动调拨"),
    INVENTORY_TRIGGERED("盘点触发");

    private final String label;

    TransferSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param name 来源字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static TransferSource of(String name) {
        for (TransferSource source : values()) {
            if (source.name().equals(name)) {
                return source;
            }
        }
        throw new IllegalArgumentException("非法调拨单来源：" + name);
    }
}
