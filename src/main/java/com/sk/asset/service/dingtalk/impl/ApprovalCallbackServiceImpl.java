package com.sk.asset.service.dingtalk.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.transfer.TransferStatus;
import com.sk.asset.enums.change.ChangeStatus;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.service.change.ChangeOrderService;
import com.sk.asset.service.dingtalk.ApprovalCallbackService;
import com.sk.asset.service.notification.NotificationService;
import com.sk.asset.service.receipt.ReceiveReceiptService;
import com.sk.asset.service.transfer.TransferOrderService;
import com.sk.asset.enums.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 钉钉审批事件回调实现（M10 入口 B）。
 *
 * <p>两级链映射（领用/借用）：钉钉两个顺序审批节点对应 approval_step 1→2，
 * bpms_task_change(agree) 逐级推进（操作人=事件 staffId 反查，须匹配当前 step 快照审批人，
 * 与 B4 越权防护一致）；bpms_instance_change(finish) 为终态兜底（task 事件漏投时补齐剩余层级）。
 * 并发裁决：先到先得，后到者被单据状态机 409 拦截后按幂等忽略。</p>
 *
 * <p>事件不回查 processinstance/get 校验：Stream 通道经应用凭证鉴权（TLS 长连接），
 * 且操作人必须命中审批链快照，伪造/重复事件最多被 403/409 拒绝，无越权面。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCallbackServiceImpl implements ApprovalCallbackService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 同实例事件串行化条带锁：task_change 与 instance_change 终态事件几乎同时到达，
     * 并发处理会双重 approve（重复通知）+ callbacks 读改写互相覆盖。单实例部署下
     * 按 processInstanceId 哈希分条带加锁即可串行（不同实例间不互斥）。
     */
    private static final int LOCK_STRIPES = 16;
    private static final ReentrantLock[] INSTANCE_LOCKS = buildStripes();

    private static ReentrantLock[] buildStripes() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    static ReentrantLock lockFor(String processInstanceId) {
        return INSTANCE_LOCKS[Math.floorMod(processInstanceId.hashCode(), LOCK_STRIPES)];
    }

    private final DingtalkProperties props;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final UserDirectory userDirectory;
    private final NotificationService notificationService;
    private final ReceiveReceiptMapper receiptMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final ChangeOrderMapper changeOrderMapper;
    private final ReceiveReceiptService receiveReceiptService;
    private final TransferOrderService transferOrderService;
    private final ChangeOrderService changeOrderService;
    private final com.sk.asset.service.dingtalk.InboundApprovalImportService importService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onEvent(String eventType, String dataJson) {
        if (eventType == null || dataJson == null || dataJson.isBlank()) {
            return;
        }
        try {
            JsonNode data = objectMapper.readTree(dataJson);
            if (data == null || data.isEmpty()) {
                return;
            }
            log.info("收到钉钉事件（type={}, instanceId={}, staffId={}, result={}）", eventType,
                    data.path("processInstanceId").asText(""), data.path("staffId").asText(""),
                    data.path("result").asText(""));
            // 企业校验：配置了 corpId 且事件携带 corpId 不一致时忽略（防跨企业事件串扰）
            String eventCorpId = data.path("corpId").asText("");
            if (!props.getCorpId().isBlank() && !eventCorpId.isBlank()
                    && !props.getCorpId().equals(eventCorpId)) {
                log.warn("忽略非本企业钉钉事件（corpId={}）", eventCorpId);
                return;
            }
            switch (eventType) {
                case "bpms_task_change" -> handleTaskChange(data);
                case "bpms_instance_change" -> handleInstanceChange(data);
                default -> log.debug("忽略钉钉事件类型：{}", eventType);
            }
        } catch (BusinessException e) {
            // 业务校验拦截（409 已审批 / 403 非当前审批人）：幂等或越权，记日志不告警重试
            log.warn("钉钉事件被业务校验拦截（{}）：code={}, message={}",
                    eventType, e.getCode(), e.getMessage());
        } catch (Exception e) {
            // 基础设施异常：记日志 + 告警（不抛给 Stream，避免 LATER 无限重推放大）
            log.error("钉钉事件处理异常（{}）：{}", eventType, e.getMessage(), e);
            alertAdmins("钉钉事件处理异常：" + e.getMessage() + "（eventType=" + eventType + "）");
        }
    }

    // ------------------------------------------------------------ 任务级事件（两级链逐级推进）

    private void handleTaskChange(JsonNode data) {
        String type = data.path("type").asText("");
        String instanceId = data.path("processInstanceId").asText("");
        if (!"finish".equals(type) || instanceId.isBlank()) {
            return;
        }
        ReentrantLock lock = lockFor(instanceId);
        lock.lock();
        try {
            ApprovalInstance record = findByInstanceId(instanceId);
            if (record == null) {
                record = importService.tryImport(data);
            }
            if (record == null) {
                logUnknownInstance(data);
                return;
            }
            String result = data.path("result").asText("");
            UserResp operator = userDirectory.findByDdUserId(data.path("staffId").asText(""));
            if (operator == null) {
                logAndAlert(record, "任务事件操作人无法解析（staffId=" + data.path("staffId").asText() + "）");
                return;
            }
            if ("agree".equals(result)) {
                applyAgree(record, operator.id(), operator.name());
            } else if ("refuse".equals(result)) {
                applyRefuse(record, operator.id(), operator.name(), "钉钉审批拒绝");
            } else {
                log.debug("忽略任务事件 result={}（instanceId={}）", result, instanceId);
            }
            appendCallback(record, "task:" + type, result, operator.name());
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------ 实例级事件（终态兜底 + 撤销）

    private void handleInstanceChange(JsonNode data) {
        String type = data.path("type").asText("");
        String instanceId = data.path("processInstanceId").asText("");
        if (instanceId.isBlank()) {
            return;
        }
        ReentrantLock lock = lockFor(instanceId);
        lock.lock();
        try {
            ApprovalInstance record = findByInstanceId(instanceId);
            if (record == null) {
                record = importService.tryImport(data);
            }
            if (record == null) {
                logUnknownInstance(data);
                return;
            }
            switch (type) {
                case "start" -> {
                    // 事件可能乱序（start 晚于 finish 到达）：终态不回退
                    if (!"COMPLETED".equals(record.getStatus()) && !"TERMINATED".equals(record.getStatus())) {
                        record.setStatus("RUNNING");
                        appendCallback(record, "instance:start", null, null);
                    }
                }
                case "finish" -> handleInstanceFinish(record, data);
                case "terminate" -> handleInstanceTerminate(record, data);
                default -> log.debug("忽略实例事件 type={}（instanceId={}）", type, instanceId);
            }
        } finally {
            lock.unlock();
        }
    }

    private void handleInstanceFinish(ApprovalInstance record, JsonNode data) {
        String result = data.path("result").asText("");
        record.setStatus("COMPLETED");
        record.setResult(result);
        if ("agree".equals(result)) {
            try {
                applyInstanceAgreeFallback(record);
            } catch (BusinessException e) {
                // 兜底推进被业务校验拦截（如调拨发起人=调入人不能自确认）：
                // 钉钉侧已终审，终态记录必须落库；业务推进交由 task:finish 事件或人工介入
                log.warn("实例终审兜底推进被业务校验拦截（bizType={}, bizId={}）：code={}, message={}",
                        record.getBizType(), record.getBizId(), e.getCode(), e.getMessage());
            }
        } else if ("refuse".equals(result)) {
            applyRefuse(record, null, null, "钉钉审批拒绝（终审）");
        }
        appendCallback(record, "instance:finish", result, null);
    }

    private void handleInstanceTerminate(ApprovalInstance record, JsonNode data) {
        record.setStatus("TERMINATED");
        String staffId = data.path("staffId").asText("");
        UserResp operator = staffId.isBlank() ? null : userDirectory.findByDdUserId(staffId);
        switch (record.getBizType()) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW -> {
                // 钉钉撤销 → 单据拒绝（释放资产占用）；操作人取当前 step 快照审批人（过链校验）
                ReceiveReceipt receipt = receiptMapper.selectById(record.getBizId());
                if (receipt != null && ReceiptStatus.PENDING.name().equals(receipt.getStatus())) {
                    Long snapshotApprover = Integer.valueOf(1).equals(receipt.getApprovalStep())
                            ? receipt.getApprovalStep1UserId() : receipt.getApprovalStep2UserId();
                    String snapshotName = Integer.valueOf(1).equals(receipt.getApprovalStep())
                            ? receipt.getApprovalStep1Name() : receipt.getApprovalStep2Name();
                    receiveReceiptService.reject(record.getBizId(),
                            "发起人在钉钉撤销（操作人：" + displayName(operator) + "）",
                            snapshotApprover, snapshotName);
                }
            }
            case ApprovalInstance.BIZ_TRANSFER -> transferOrderService.cancel(record.getBizId(),
                    transferOrderMapper.selectById(record.getBizId()) != null
                            ? transferOrderMapper.selectById(record.getBizId()).getApplicantUserId() : null);
            case ApprovalInstance.BIZ_CHANGE -> changeOrderService.cancel(record.getBizId(),
                    changeOrderMapper.selectById(record.getBizId()) != null
                            ? changeOrderMapper.selectById(record.getBizId()).getApplicantUserId() : null);
            default -> log.debug("忽略终止事件（bizType={}）", record.getBizType());
        }
        appendCallback(record, "instance:terminate", null, displayName(operator));
    }

    // ------------------------------------------------------------ 审批动作落地（复用状态机）

    /** 任务级同意：操作人 = 事件 staffId（须命中当前 step 快照审批人，否则 403 记日志） */
    private void applyAgree(ApprovalInstance record, Long operatorId, String operatorName) {
        switch (record.getBizType()) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW ->
                    receiveReceiptService.approve(record.getBizId(), operatorId, operatorName);
            case ApprovalInstance.BIZ_TRANSFER ->
                    transferOrderService.confirm(record.getBizId(), operatorId, operatorName);
            case ApprovalInstance.BIZ_CHANGE ->
                    changeOrderService.confirm(record.getBizId(), operatorId, operatorName);
            default -> log.debug("忽略同意动作（bizType={}）", record.getBizType());
        }
    }

    /** 拒绝：操作人优先取事件 staffId；单据拒绝/撤销语义按类型分发 */
    private void applyRefuse(ApprovalInstance record, Long operatorId, String operatorName, String reason) {
        switch (record.getBizType()) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW -> {
                // 操作人兜底：终态兜底事件可能无 staffId，取当前 step 快照审批人过链校验
                if (operatorId == null) {
                    ReceiveReceipt receipt = receiptMapper.selectById(record.getBizId());
                    if (receipt == null) {
                        return;
                    }
                    operatorId = Integer.valueOf(1).equals(receipt.getApprovalStep())
                            ? receipt.getApprovalStep1UserId() : receipt.getApprovalStep2UserId();
                    operatorName = Integer.valueOf(1).equals(receipt.getApprovalStep())
                            ? receipt.getApprovalStep1Name() : receipt.getApprovalStep2Name();
                }
                receiveReceiptService.reject(record.getBizId(), reason, operatorId, operatorName);
            }
            case ApprovalInstance.BIZ_TRANSFER ->
                    transferOrderService.reject(record.getBizId(), reason, operatorId, operatorName);
            case ApprovalInstance.BIZ_CHANGE -> {
                // 变更单无拒绝语义：钉钉拒绝 → 发起人撤销（CANCELLED，资产不变）
                ChangeOrder order = changeOrderMapper.selectById(record.getBizId());
                if (order != null && ChangeStatus.PENDING.name().equals(order.getStatus())) {
                    changeOrderService.cancel(record.getBizId(), order.getApplicantUserId());
                }
            }
            default -> log.debug("忽略拒绝动作（bizType={}）", record.getBizType());
        }
    }

    /**
     * 实例终审同意兜底：task 事件漏投时按快照审批人补齐剩余层级。
     * 两级链在 step1 → 连续推进两级；step2/合并单 → 一次终审。
     */
    private void applyInstanceAgreeFallback(ApprovalInstance record) {
        switch (record.getBizType()) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW -> {
                ReceiveReceipt receipt = receiptMapper.selectById(record.getBizId());
                if (receipt == null || !ReceiptStatus.PENDING.name().equals(receipt.getStatus())) {
                    return; // 已被任务事件处理（幂等）
                }
                if (Integer.valueOf(1).equals(receipt.getApprovalStep())) {
                    receiveReceiptService.approve(record.getBizId(),
                            receipt.getApprovalStep1UserId(), receipt.getApprovalStep1Name());
                }
                // 重新加载（一级推进后 step=2；合并单提交即 step=2）
                ReceiveReceipt latest = receiptMapper.selectById(record.getBizId());
                if (latest != null && ReceiptStatus.PENDING.name().equals(latest.getStatus())) {
                    receiveReceiptService.approve(record.getBizId(),
                            latest.getApprovalStep2UserId(), latest.getApprovalStep2Name());
                }
            }
            case ApprovalInstance.BIZ_TRANSFER -> {
                TransferOrder order = transferOrderMapper.selectById(record.getBizId());
                if (order != null && TransferStatus.PENDING.name().equals(order.getStatus())) {
                    transferOrderService.confirm(record.getBizId(),
                            order.getToUserId(), order.getToUserName());
                }
            }
            case ApprovalInstance.BIZ_CHANGE -> {
                ChangeOrder order = changeOrderMapper.selectById(record.getBizId());
                if (order != null && ChangeStatus.PENDING.name().equals(order.getStatus())) {
                    changeOrderService.confirm(record.getBizId(),
                            order.getAssigneeUserId(), order.getReason());
                }
            }
            default -> log.debug("忽略终审兜底（bizType={}）", record.getBizType());
        }
    }

    // ------------------------------------------------------------ 记录维护

    private ApprovalInstance findByInstanceId(String processInstanceId) {
        return approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getProcessInstanceId, processInstanceId));
    }

    /** 非系统发起且不适用入口 B 导入的实例（非领用/借用模板或导入失败）：记日志，人工处理 */
    private void logUnknownInstance(JsonNode data) {
        log.info("收到非系统发起的钉钉审批事件，入口 B 未导入（processCode={}, instanceId={}）",
                data.path("processCode").asText(""), data.path("processInstanceId").asText(""));
    }

    /** 追加回调审计明细（JSON 数组按次累积）并落库 */
    private void appendCallback(ApprovalInstance record, String phase, String result, String operator) {
        try {
            ArrayNode callbacks = record.getCallbacks() == null || record.getCallbacks().isBlank()
                    ? objectMapper.createArrayNode()
                    : (ArrayNode) objectMapper.readTree(record.getCallbacks());
            ObjectNode entry = callbacks.addObject();
            entry.put("ts", LocalDateTime.now().format(TS));
            entry.put("phase", phase);
            if (result != null) {
                entry.put("result", result);
            }
            if (operator != null) {
                entry.put("operator", operator);
            }
            record.setCallbacks(objectMapper.writeValueAsString(callbacks));
        } catch (Exception e) {
            log.warn("回调审计明细追加失败（instanceId={}）：{}", record.getProcessInstanceId(), e.getMessage());
        }
        approvalInstanceMapper.updateById(record);
    }

    private void logAndAlert(ApprovalInstance record, String message) {
        log.warn("{}（bizType={}, bizId={}, instanceId={}）", message,
                record.getBizType(), record.getBizId(), record.getProcessInstanceId());
        alertAdmins("钉钉事件处理告警：" + message + "（单据 " + record.getBizType()
                + "#" + record.getBizId() + "，请在系统内处理）");
    }

    private void alertAdmins(String message) {
        for (Long adminId : userDirectory.userIdsByRole(props.getAdminRole())) {
            notificationService.notify(adminId, NotificationType.DINGTALK_SYNC_ALERT,
                    message, "DINGTALK", 0L);
        }
    }

    private static String displayName(UserResp user) {
        return user != null ? user.name() : "未知用户";
    }
}
