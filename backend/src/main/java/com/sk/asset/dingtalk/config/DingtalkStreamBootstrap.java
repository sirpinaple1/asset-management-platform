package com.sk.asset.dingtalk.config;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.message.GenericOpenDingTalkEvent;
import com.dingtalk.open.app.stream.protocol.event.EventAckStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dingtalk.client.DingTalkTokenClient;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.service.dingtalk.ApprovalCallbackService;
import com.sk.asset.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 钉钉 Stream 事件订阅引导（M10，ADR-0007：出站长连接，无需公网 IP/域名）。
 *
 * <p>registerAllEventListener 订阅全部事件，按 eventType 交给
 * {@link ApprovalCallbackService} 路由（当前消费 bpms_task_change / bpms_instance_change）；
 * 事件处理方已内捕全部异常，此处恒返回 SUCCESS——LATER 会触发钉钉无限重推放大告警。
 * corpId 在事件元数据（eventCorpId）而部分事件 data 不携带，统一补写进 data 后下发校验。</p>
 *
 * <p>{@code app.dingtalk.enabled=false}（默认）时不建连；建连失败仅告警不阻断启动
 * （站内审批通道兜底，ADR-0007 D4）。实验公司 → 生产公司切换只换凭证配置（R3）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingtalkStreamBootstrap implements SmartLifecycle {

    private final DingtalkProperties props;
    private final DingTalkTokenClient tokenClient;
    private final ApprovalCallbackService callbackService;
    private final UserDirectory userDirectory;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean running;
    private OpenDingTalkClient client;

    @Override
    public void start() {
        if (!props.isEnabled() || !props.isStreamEnabled()) {
            log.info("钉钉 Stream 未启用（enabled={}, streamEnabled={}），事件监听关闭",
                    props.isEnabled(), props.isStreamEnabled());
            return;
        }
        try {
            client = OpenDingTalkStreamClientBuilder.custom()
                    .credential(tokenClient.credential())
                    .registerAllEventListener(this::onEvent)
                    .build();
            client.start();
            running = true;
            log.info("钉钉 Stream 长连接已启动（事件订阅就绪，corpId={}）", props.getCorpId());
        } catch (Exception e) {
            log.error("钉钉 Stream 长连接启动失败（站内审批兜底，可重启重试）：{}", e.getMessage(), e);
            alertAdmins("钉钉 Stream 启动失败：" + e.getMessage() + "（审批回传不可用，请检查应用凭证/网络）");
        }
    }

    @Override
    public void stop() {
        if (client != null) {
            try {
                client.stop();
            } catch (Exception e) {
                log.warn("钉钉 Stream 连接关闭异常：{}", e.getMessage());
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private EventAckStatus onEvent(GenericOpenDingTalkEvent event) {
        try {
            String dataJson = event.getData() == null ? "{}" : event.getData().toJSONString();
            callbackService.onEvent(event.getEventId(), event.getEventType(),
                    mergeCorpId(dataJson, event.getEventCorpId()));
        } catch (Exception e) {
            // 双保险：回调服务已内捕异常；此处再漏即记日志，仍 ACK 避免重推放大
            log.error("钉钉事件分发异常（eventType={}）：{}", event.getEventType(), e.getMessage(), e);
        }
        return EventAckStatus.SUCCESS;
    }

    /** data 无 corpId 时以事件元数据补写（回调服务做跨企业串扰校验的依据） */
    private String mergeCorpId(String dataJson, String eventCorpId) {
        if (eventCorpId == null || eventCorpId.isBlank()) {
            return dataJson;
        }
        try {
            JsonNode node = objectMapper.readTree(dataJson);
            if (node instanceof ObjectNode obj && obj.path("corpId").asText("").isBlank()) {
                obj.put("corpId", eventCorpId);
                return objectMapper.writeValueAsString(obj);
            }
        } catch (Exception e) {
            log.warn("钉钉事件 data 解析失败，按原文下发：{}", e.getMessage());
        }
        return dataJson;
    }

    private void alertAdmins(String message) {
        for (Long adminId : userDirectory.userIdsByRole(props.getAdminRole())) {
            notificationService.notify(adminId, NotificationType.DINGTALK_SYNC_ALERT,
                    message, "DINGTALK", 0L);
        }
    }
}
