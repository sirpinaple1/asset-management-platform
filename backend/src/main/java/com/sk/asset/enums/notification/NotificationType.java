package com.sk.asset.enums.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通知类型（sys_notification.type）。
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    /** 单据已提交（通知处理人：待你处理） */
    DOC_SUBMITTED("单据待处理"),

    /** 单据已通过（通知发起人） */
    DOC_APPROVED("单据已通过"),

    /** 单据已拒绝（通知发起人，含拒绝原因） */
    DOC_REJECTED("单据已拒绝"),

    /** 单据已完成/已执行（通知发起人：调拨确认、变更执行） */
    DOC_COMPLETED("单据已完成"),

    /** 审批进度更新（两级审批链：一级通过，通知发起人与二级审批人） */
    DOC_PROGRESS("审批进度更新"),

    /** 抄送我的（钉钉实例终态后按模板抄送人逐一通知，"抄送我的"列表数据源） */
    DOC_CC("抄送我的"),

    /** 审批链配置告警（解析失败/配置失效时通知 systemAdmin 补配置） */
    APPROVAL_CONFIG_ALERT("审批链配置告警"),

    /** 钉钉同步告警（OA 实例创建失败/事件处理异常时通知 systemAdmin，M10） */
    DINGTALK_SYNC_ALERT("钉钉同步告警");

    private final String label;

    public static NotificationType of(String value) {
        for (NotificationType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知通知类型：" + value);
    }
}
