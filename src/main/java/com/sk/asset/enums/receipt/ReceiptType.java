package com.sk.asset.enums.receipt;

/**
 * 领用/借用单类型（M04）。领用与借用共用单据流（receive_receipt.type），
 * 仅单号前缀与展示语义不同；资产状态机不区分（借用中也算 IN_USE）。
 */
public enum ReceiptType {

    RECEIVE("领用", "ARE"),
    BORROW("借用", "BOR");

    private final String label;

    /** 单号前缀（serial_no = 前缀 + yyyyMMdd + 4位序号，当天按前缀分别自增） */
    private final String serialPrefix;

    ReceiptType(String label, String serialPrefix) {
        this.label = label;
        this.serialPrefix = serialPrefix;
    }

    public String getLabel() {
        return label;
    }

    public String getSerialPrefix() {
        return serialPrefix;
    }

    /** @param name 类型字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static ReceiptType of(String name) {
        for (ReceiptType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("非法单据类型：" + name);
    }
}
