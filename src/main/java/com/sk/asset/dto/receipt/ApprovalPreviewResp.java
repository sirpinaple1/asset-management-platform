package com.sk.asset.dto.receipt;

import lombok.Data;

/**
 * 发起审批人预览响应（GET /api/v1/receipts/approval-preview?locationId=）。
 * 发起弹窗展示"将路由给：部门主管 张三 → 仓管 李四"；解析失败返回 resolvable=false + message，
 * 提交前用户即可看到（真正提交失败为 400 + 超管告警通知）。
 */
@Data
public class ApprovalPreviewResp {

    /** 是否可解析出完整两级审批链 */
    private Boolean resolvable;

    /** 解析失败提示（resolvable=false 时非空） */
    private String message;

    /** 一级审批人ID（部门主管） */
    private Long step1UserId;

    /** 一级审批人姓名 */
    private String step1UserName;

    /** 一级解析依据（命中的部门路径配置键） */
    private String step1SourceKey;

    /** 二级审批人ID（领料仓管理员） */
    private Long step2UserId;

    /** 二级审批人姓名 */
    private String step2UserName;

    /** 二级解析依据（命中的位置 id 配置键） */
    private String step2SourceKey;

    /** 两级审批人为同一人：合并为一次审批（提交后 approval_step 直接为 2） */
    private Boolean merged;
}
