package com.sk.asset.service.notification.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.PageResp;
import com.sk.asset.dto.notification.NotificationResp;
import com.sk.asset.entity.notification.SysNotification;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.notification.SysNotificationMapper;
import com.sk.asset.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知服务实现（B2 通知中心）。
 * 读接口无事务；写入点（notify）被单据流的外层事务覆盖，同事务回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 标题列长 VARCHAR(200)，超长截断防御（含表情等多字节字符时按字符数截） */
    private static final int TITLE_MAX_LENGTH = 200;

    private final SysNotificationMapper notificationMapper;

    @Override
    public void notify(Long recipientUserId, NotificationType type, String title,
                       String bizType, Long bizId) {
        if (recipientUserId == null) {
            log.warn("通知接收人为空，跳过写入（type={}, bizType={}, bizId={}）", type, bizType, bizId);
            return;
        }
        SysNotification notification = new SysNotification();
        notification.setUserId(recipientUserId);
        notification.setType(type.name());
        notification.setTitle(truncate(title));
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notificationMapper.insert(notification);
    }

    @Override
    public PageResp<NotificationResp> page(Long userId, boolean unreadOnly, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);

        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId);
        if (unreadOnly) {
            wrapper.eq(SysNotification::getReadFlag, 0);
        }
        wrapper.orderByDesc(SysNotification::getId);

        Page<SysNotification> result = notificationMapper.selectPage(
                new Page<>(safePage, safeSize), wrapper);
        List<NotificationResp> records = result.getRecords().stream()
                .map(NotificationResp::from)
                .collect(Collectors.toList());

        PageResp<NotificationResp> resp = new PageResp<>();
        resp.setRecords(records);
        resp.setTotal(result.getTotal());
        resp.setPage(safePage);
        resp.setSize(safeSize);
        return resp;
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getReadFlag, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysNotification markRead(Long id, Long userId) {
        SysNotification notification = notificationMapper.selectById(id);
        if (notification == null || !userId.equals(notification.getUserId())) {
            // 不存在与他人通知统一 404，不泄露存在性
            throw new BusinessException(404, "通知不存在");
        }
        if (notification.getReadFlag() == 0) {
            notification.setReadFlag(1);
            notificationMapper.updateById(notification);
        }
        return notification;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId) {
        return notificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getReadFlag, 0)
                .set(SysNotification::getReadFlag, 1));
    }

    private static String truncate(String title) {
        if (title == null || title.length() <= TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, TITLE_MAX_LENGTH - 1) + "…";
    }
}
