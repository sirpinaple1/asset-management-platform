package com.sk.asset.service.notification;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.notification.SysNotification;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.notification.SysNotificationMapper;
import com.sk.asset.service.notification.impl.NotificationServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysNotification.class);
    }

    @Mock
    private SysNotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void notify_shouldInsertNotificationWithFields() {
        notificationService.notify(200L, NotificationType.DOC_SUBMITTED,
                "张三 提交的领用单 ARE202608300001 待你处理", "RECEIVE", 1L);

        ArgumentCaptor<SysNotification> captor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationMapper).insert(captor.capture());
        SysNotification inserted = captor.getValue();
        assertEquals(200L, inserted.getUserId());
        assertEquals("DOC_SUBMITTED", inserted.getType());
        assertEquals("RECEIVE", inserted.getBizType());
        assertEquals(1L, inserted.getBizId());
        assertEquals("张三 提交的领用单 ARE202608300001 待你处理", inserted.getTitle());
    }

    @Test
    void notify_shouldSkipWhenRecipientNull() {
        notificationService.notify(null, NotificationType.DOC_APPROVED, "t", "TRANSFER", 1L);

        verify(notificationMapper, never()).insert(any(SysNotification.class));
    }

    @Test
    void notify_shouldTruncateOverlongTitle() {
        String longTitle = "长".repeat(300);

        notificationService.notify(1L, NotificationType.DOC_REJECTED, longTitle, "CHANGE", 1L);

        ArgumentCaptor<SysNotification> captor = ArgumentCaptor.forClass(SysNotification.class);
        verify(notificationMapper).insert(captor.capture());
        assertEquals(200, captor.getValue().getTitle().length());
    }

    @Test
    void page_shouldQueryByUserAndReturnPageMeta() {
        SysNotification notification = new SysNotification();
        notification.setId(5L);
        notification.setUserId(100L);
        notification.setType("DOC_APPROVED");
        notification.setTitle("你发起的领用单已通过");
        notification.setBizType("RECEIVE");
        notification.setBizId(1L);
        notification.setReadFlag(0);
        when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<SysNotification> page = invocation.getArgument(0);
                    page.setRecords(List.of(notification));
                    page.setTotal(1L);
                    return page;
                });

        var resp = notificationService.page(100L, true, 0, 1000);

        assertEquals(1, resp.getRecords().size());
        assertEquals(1L, resp.getTotal());
        // 越界参数收敛：page>=1、size<=100
        assertEquals(1L, resp.getPage());
        assertEquals(100L, resp.getSize());
        assertEquals("单据已通过", resp.getRecords().get(0).getTypeLabel());
    }

    @Test
    void unreadCount_shouldCountUnreadOnly() {
        when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertEquals(3L, notificationService.unreadCount(100L));
    }

    @Test
    void markRead_shouldRejectWhenNotificationNotOwn() {
        SysNotification others = new SysNotification();
        others.setId(5L);
        others.setUserId(200L);
        when(notificationMapper.selectById(5L)).thenReturn(others);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> notificationService.markRead(5L, 100L));

        assertEquals(404, exception.getCode());
        verify(notificationMapper, never()).updateById(any(SysNotification.class));
    }

    @Test
    void markRead_shouldMarkUnreadAsRead() {
        SysNotification notification = new SysNotification();
        notification.setId(5L);
        notification.setUserId(100L);
        notification.setType("DOC_APPROVED");
        notification.setReadFlag(0);
        when(notificationMapper.selectById(5L)).thenReturn(notification);

        SysNotification marked = notificationService.markRead(5L, 100L);

        assertEquals(1, marked.getReadFlag());
        verify(notificationMapper).updateById(notification);
    }

    @Test
    void markRead_shouldSkipUpdateWhenAlreadyRead() {
        SysNotification notification = new SysNotification();
        notification.setId(5L);
        notification.setUserId(100L);
        notification.setReadFlag(1);
        when(notificationMapper.selectById(5L)).thenReturn(notification);

        notificationService.markRead(5L, 100L);

        verify(notificationMapper, never()).updateById(any(SysNotification.class));
    }

    @Test
    void markAllRead_shouldUpdateUnreadOfOwnOnly() {
        when(notificationMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(7);

        assertEquals(7, notificationService.markAllRead(100L));
    }
}
