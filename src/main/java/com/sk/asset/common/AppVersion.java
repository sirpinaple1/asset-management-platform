package com.sk.asset.common;

import java.util.List;

/**
 * 后端版本信息（唯一来源）。前端左下角帮助面板经 GET /api/v1/version 读取。
 * 发布纪律（见 AGENTS.md「版本号纪律」）：每次 push 功能/修复上测试/生产，
 * VERSION 末位 +1 并在 CHANGELOG 增补一行；前后端版本独立演进，互不同步。
 */
public final class AppVersion {

    private AppVersion() {
    }

    /** 后端版本号（语义：功能上线 +1 末位） */
    public static final String VERSION = "0.1.2";

    /** 发布日期（yyyy-MM-dd） */
    public static final String RELEASE_DATE = "2026-08-31";

    /** 本版本新增功能（简要，供前端帮助面板展示） */
    public static final List<String> CHANGELOG = List.of(
            "钉钉发起单据自动导入：钉钉原生表单发起的领用/借用/调拨/变更审批自动同步为系统单据，审批人以钉钉为准",
            "钉钉 userid 全公司批量同步：超管一键遍历钉钉通讯录自动绑定，员工无需自报 userid",
            "修复钉钉审批人身份错乱：userid 重复绑定检测告警，杜绝审批人记录错误",
            "钉钉 OA 审批双向集成：系统建单自动推送钉钉审批，审批结果实时回传推进单据状态机",
            "资产责任归属编排：归还自动带回发放前区域，区域管理员随位置实时解析",
            "两级审批链（部门主管 + 区域仓管）与审批链配置管理",
            "移动扫码盘点、通知中心、审批中心、统计聚合",
            "鉴权复用 comm_public_basic（登录/用户/角色/权限不自建）");

    /** 版权署名 */
    public static final String CREDIT = "© 2026 潘雨松 & GLM-5.3 · 共同完成";
}
