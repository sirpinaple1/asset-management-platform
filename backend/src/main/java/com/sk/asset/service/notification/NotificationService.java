package com.sk.asset.service.notification;

import com.sk.asset.common.PageResp;
import com.sk.asset.dto.notification.NotificationResp;
import com.sk.asset.entity.notification.SysNotification;
import com.sk.asset.enums.notification.NotificationType;

/**
 * 站内通知服务（B2 通知中心）。
 */
public interface NotificationService {

    /**
     * 写入一条通知（供单据流各写入点调用，与业务同事务）。
     * recipient 为空时静默跳过（防御，正常不会出现）。
     */
    void notify(Long recipientUserId, NotificationType type, String title, String bizType, Long bizId);

    /**
     * 我的通知（分页，id 倒序）。
     *
     * @param type      通知类型过滤（null=全部；如 DOC_CC=抄送我的）
     * @param unreadOnly true=仅未读
     */
    PageResp<NotificationResp> page(Long userId, NotificationType type, boolean unreadOnly, long page, long size);

    /** 未读数（徽标轮询） */
    long unreadCount(Long userId);

    /** 标记单条已读（仅本人的通知，404 防越权） */
    SysNotification markRead(Long id, Long userId);

    /** 全部标记已读 */
    int markAllRead(Long userId);
}
