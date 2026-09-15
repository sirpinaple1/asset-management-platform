package com.sk.asset.enums.approval;

/**
 * 审批链配置类型（approval_config.config_type）。
 *
 * 两级审批链（依据《资产领用与借用操作流程指导》）：
 * 一级 = 部门主管（按发起人 sys_user.dept 路由，精确优先 + 逐级向上回退）；
 * 二级 = 领料仓管理员（按单据领用区域 asset_location.id 路由）。
 */
public enum ApprovalConfigType {

    /** 部门主管（config_key = 部门路径字符串，如「资材管理中心/示例科技PMC部」） */
    DEPT_SUPERVISOR("部门主管"),

    /** 领料仓管理员（config_key = 领用区域 id 字符串） */
    WAREHOUSE_KEEPER("领料仓管理员");

    private final String label;

    ApprovalConfigType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** @param name 类型字符串（大小写敏感），非法值抛 IllegalArgumentException（全局映射 400） */
    public static ApprovalConfigType of(String name) {
        for (ApprovalConfigType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("非法审批链配置类型：" + name);
    }
}
