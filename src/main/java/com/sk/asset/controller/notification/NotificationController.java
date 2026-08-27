package com.sk.asset.controller.notification;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.PageResp;
import com.sk.asset.common.Result;
import com.sk.asset.dto.notification.NotificationResp;
import com.sk.asset.entity.notification.SysNotification;
import com.sk.asset.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知中心（B2）。接收人取 UserContext，只能读写自己的通知。
 */
@Tag(name = "通知中心")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知（分页，id 倒序；unread=true 只看未读）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<PageResp<NotificationResp>> page(
            @Parameter(description = "true=仅未读")
            @RequestParam(defaultValue = "false") boolean unread,
            @Parameter(description = "页码，从 1 起")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数（1-100）")
            @RequestParam(defaultValue = "20") long size) {
        AuthContext user = UserContext.require();
        return Result.ok(notificationService.page(
                Long.valueOf(user.getUserId()), unread, page, size));
    }

    @Operation(summary = "未读数（徽标轮询）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        AuthContext user = UserContext.require();
        long count = notificationService.unreadCount(Long.valueOf(user.getUserId()));
        return Result.ok(Map.of("count", count));
    }

    @Operation(summary = "标记单条已读（仅本人的通知）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/read")
    public Result<NotificationResp> markRead(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        SysNotification notification = notificationService.markRead(
                id, Long.valueOf(user.getUserId()));
        return Result.ok(NotificationResp.from(notification));
    }

    @Operation(summary = "全部标记已读")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/read-all")
    public Result<Map<String, Integer>> markAllRead() {
        AuthContext user = UserContext.require();
        int updated = notificationService.markAllRead(Long.valueOf(user.getUserId()));
        return Result.ok(Map.of("updated", updated));
    }
}
