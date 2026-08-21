package com.sk.asset.enums.change;

/**
 * 变更单可变更字段白名单（M06）。
 *
 * 仅限实物归属类字段，不含资产编码/分类/购入日期等固有属性（走资产编辑）：
 * user_id / user_department / location_id / location_detail / company_id。
 * field_name 对应 change_order_item.field_name 与 asset 列名；
 * field_label 对应 change_order_item.field_label（展示/打印用）。
 */
public enum ChangeField {

    USER_ID("user_id", "使用人"),
    USER_DEPARTMENT("user_department", "使用部门"),
    LOCATION_ID("location_id", "区域"),
    LOCATION_DETAIL("location_detail", "存放位置明细"),
    COMPANY_ID("company_id", "归属公司");

    private final String fieldName;
    private final String label;

    ChangeField(String fieldName, String label) {
        this.fieldName = fieldName;
        this.label = label;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getLabel() {
        return label;
    }

    /** @param fieldName 字段名字符串，白名单外抛 IllegalArgumentException（全局映射 400） */
    public static ChangeField of(String fieldName) {
        for (ChangeField field : values()) {
            if (field.fieldName.equals(fieldName)) {
                return field;
            }
        }
        throw new IllegalArgumentException("非法变更字段：" + fieldName);
    }
}
