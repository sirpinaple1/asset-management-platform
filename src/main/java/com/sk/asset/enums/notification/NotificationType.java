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
    DOC_COMPLETED("单据已完成");

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
