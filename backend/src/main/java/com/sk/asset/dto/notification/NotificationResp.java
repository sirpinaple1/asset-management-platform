package com.sk.asset.dto.notification;

import com.sk.asset.entity.notification.SysNotification;
import com.sk.asset.enums.notification.NotificationType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知响应（GET /api/v1/notifications）。
 */
@Data
public class NotificationResp {

    private Long id;

    /** 类型：DOC_SUBMITTED / DOC_APPROVED / DOC_REJECTED / DOC_COMPLETED */
    private String type;

    /** 类型展示名（如"单据待处理"） */
    private String typeLabel;

    /** 通知标题 */
    private String title;

    /** 业务单据类型：RECEIVE / BORROW / TRANSFER / CHANGE */
    private String bizType;

    /** 业务单据ID（前端点击跳转单据详情） */
    private Long bizId;

    /** 已读标记：0-未读 1-已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;

    public static NotificationResp from(SysNotification entity) {
        NotificationResp resp = new NotificationResp();
        resp.setId(entity.getId());
        resp.setType(entity.getType());
        resp.setTypeLabel(NotificationType.of(entity.getType()).getLabel());
        resp.setTitle(entity.getTitle());
        resp.setBizType(entity.getBizType());
        resp.setBizId(entity.getBizId());
        resp.setReadFlag(entity.getReadFlag());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}
