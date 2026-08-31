package com.sk.asset.service.dingtalk.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dingtalk.client.DingTalkApiClient;
import com.sk.asset.dingtalk.client.DingTalkApiException;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dingtalk.event.OaSyncRequestedEvent;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.change.ChangeOrderItem;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.transfer.TransferOrderItem;
import com.sk.asset.enums.receipt.ReceiptType;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.transfer.TransferStatus;
import com.sk.asset.enums.change.ChangeStatus;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.change.ChangeOrderItemMapper;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptItemMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderItemMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.dingtalk.ApprovalSyncService;
import com.sk.asset.service.notification.NotificationService;
import com.sk.asset.enums.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 钉钉审批同步实现（M10 入口 A）。
 *
 * <p>表单字段名与钉钉模板强契约（模板须按《钉钉OA审批集成方案》§5 建字段，名称完全一致）：
 * 领用/借用 = 单据编号/资产编号/资产名称/领用区域/事由；调拨 = 单据编号/资产编号/
 * 调出区域/调入区域/调入部门/目标使用人/事由；变更 = 单据编号/资产编号/变更后使用人/
 * 变更后位置/变更原因。</p>
 *
 * <p>降级矩阵：总开关关闭/模板未配 → 静默跳过（站内审批）；审批链或发起人未绑定钉钉 →
 * FAILED + 告警；钉钉 API 失败 → FAILED + 告警。三种情况均不阻塞业务单据。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalSyncServiceImpl implements ApprovalSyncService {

    private final DingtalkProperties props;
    private final DingTalkApiClient apiClient;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final UserDirectory userDirectory;
    private final NotificationService notificationService;

    private final ReceiveReceiptMapper receiptMapper;
    private final ReceiveReceiptItemMapper receiptItemMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final TransferOrderItemMapper transferOrderItemMapper;
    private final ChangeOrderMapper changeOrderMapper;
    private final ChangeOrderItemMapper changeOrderItemMapper;
    private final AssetMapper assetMapper;
    private final LocationMapper locationMapper;

    /** AFTER_COMMIT 消费：单据已落库，钉钉失败不影响主流程 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSyncRequested(OaSyncRequestedEvent event) {
        if (!props.isEnabled()) {
            return;
        }
        try {
            switch (event.kind()) {
                case OaSyncRequestedEvent.KIND_RECEIPT -> syncReceipt(event.bizId());
                case OaSyncRequestedEvent.KIND_TRANSFER -> syncTransfer(event.bizId());
                case OaSyncRequestedEvent.KIND_CHANGE -> syncChange(event.bizId());
                default -> log.debug("忽略未知单据类别：{}", event.kind());
            }
        } catch (Exception e) {
            // 兜底：监听器异常不应传播（Stream LATER 重推会放大），记日志 + 告警
            log.error("钉钉同步异常（kind={}, bizId={}）：{}", event.kind(), event.bizId(), e.getMessage(), e);
            alertAdmins("钉钉同步异常：" + e.getMessage() + "（kind=" + event.kind()
                    + ", bizId=" + event.bizId() + "）");
        }
    }

    @Override
    public void syncReceipt(Long receiptId) {
        ReceiveReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null || !ReceiptStatus.PENDING.name().equals(receipt.getStatus())) {
            return;
        }
        ReceiptType type = ReceiptType.of(receipt.getType());
        String processCode = processCode(type == ReceiptType.RECEIVE ? "receive" : "borrow");
        if (processCode == null) {
            log.debug("领用/借用模板未配置，跳过钉钉同步（单号={}）", receipt.getSerialNo());
            return;
        }
        if (alreadySynced(bizTypeOfReceipt(type), receiptId)) {
            return;
        }

        // 审批人快照 → 钉钉节点（两级=两个顺序节点；同人合并=一个节点，与 B4 合并语义一致）
        List<Long> approverIds = new ArrayList<>();
        if (!Objects.equals(receipt.getApprovalStep1UserId(), receipt.getApprovalStep2UserId())) {
            approverIds.add(receipt.getApprovalStep1UserId());
        }
        approverIds.add(receipt.getApprovalStep2UserId());

        List<Long> userIds = new ArrayList<>(approverIds);
        userIds.add(receipt.getApplicantUserId());
        Map<Long, String> ddIds = userDirectory.ddUserIdsByIds(userIds);
        String originatorDd = ddIds.get(receipt.getApplicantUserId());
        if (originatorDd == null || approverIds.stream().anyMatch(id -> ddIds.get(id) == null)) {
            recordFailure(bizTypeOfReceipt(type), receipt.getId(), processCode,
                    "审批链或发起人未绑定钉钉（dd_user_id 缺失），已降级站内审批",
                    receipt.getSerialNo());
            return;
        }
        List<String> approverDdIds = approverIds.stream().map(ddIds::get).toList();

        List<ReceiveReceiptItem> items = receiptItemMapper.selectList(
                new LambdaQueryWrapper<ReceiveReceiptItem>()
                        .eq(ReceiveReceiptItem::getReceiptId, receiptId)
                        .orderByAsc(ReceiveReceiptItem::getId));
        Map<Long, Asset> assets = loadAssets(items.stream()
                .map(ReceiveReceiptItem::getAssetId).toList());
        String locationName = receipt.getLocationId() != null
                ? locationName(receipt.getLocationId()) : null;

        List<Map<String, String>> form = new ArrayList<>();
        form.add(field("单据编号", receipt.getSerialNo()));
        form.add(field("资产编号", joinBarcodes(items, assets)));
        form.add(field("资产名称", joinNames(items, assets)));
        form.add(field("领用区域", locationName));
        form.add(field("事由", receipt.getReason()));

        createInstance(bizTypeOfReceipt(type), receipt.getId(), processCode, originatorDd,
                approverDdIds, form, receipt.getSerialNo(), true);
    }

    @Override
    public void syncTransfer(Long orderId) {
        TransferOrder order = transferOrderMapper.selectById(orderId);
        if (order == null || !TransferStatus.PENDING.name().equals(order.getStatus())) {
            return;
        }
        if (order.getToUserId() == null) {
            // 共享池调拨（无指定调入人）：保持站内审批（已确认决策）
            log.debug("调拨单无调入人，保持站内审批（单号={}）", order.getSerialNo());
            return;
        }
        String processCode = processCode("transfer");
        if (processCode == null) {
            log.debug("调拨模板未配置，跳过钉钉同步（单号={}）", order.getSerialNo());
            return;
        }
        if (alreadySynced(ApprovalInstance.BIZ_TRANSFER, orderId)) {
            return;
        }

        Map<Long, String> ddIds = userDirectory.ddUserIdsByIds(
                List.of(order.getApplicantUserId(), order.getToUserId()));
        String originatorDd = ddIds.get(order.getApplicantUserId());
        String approverDd = ddIds.get(order.getToUserId());
        if (originatorDd == null || approverDd == null) {
            recordFailure(ApprovalInstance.BIZ_TRANSFER, orderId, processCode,
                    "调入方或发起人未绑定钉钉，已降级站内审批", order.getSerialNo());
            return;
        }

        List<TransferOrderItem> items = transferOrderItemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>()
                        .eq(TransferOrderItem::getOrderId, orderId)
                        .orderByAsc(TransferOrderItem::getId));
        Map<Long, Asset> assets = loadAssets(items.stream()
                .map(TransferOrderItem::getAssetId).toList());

        List<Map<String, String>> form = new ArrayList<>();
        form.add(field("单据编号", order.getSerialNo()));
        form.add(field("资产编号", joinBarcodes(items, assets)));
        form.add(field("调出区域", order.getFromLocationId() != null
                ? locationName(order.getFromLocationId()) : null));
        form.add(field("调入区域", order.getToLocationId() != null
                ? locationName(order.getToLocationId()) : null));
        form.add(field("调入部门", order.getToDepartment()));
        form.add(field("目标使用人", order.getToUserName()));
        form.add(field("事由", order.getReason()));

        createInstance(ApprovalInstance.BIZ_TRANSFER, orderId, processCode, originatorDd,
                List.of(approverDd), form, order.getSerialNo(), false);
    }

    @Override
    public void syncChange(Long orderId) {
        ChangeOrder order = changeOrderMapper.selectById(orderId);
        if (order == null || !ChangeStatus.PENDING.name().equals(order.getStatus())) {
            return;
        }
        if (order.getAssigneeUserId() == null) {
            log.debug("变更单无指定处理人，保持站内审批（单号={}）", order.getSerialNo());
            return;
        }
        String processCode = processCode("change");
        if (processCode == null) {
            log.debug("变更模板未配置，跳过钉钉同步（单号={}）", order.getSerialNo());
            return;
        }
        if (alreadySynced(ApprovalInstance.BIZ_CHANGE, orderId)) {
            return;
        }

        Map<Long, String> ddIds = userDirectory.ddUserIdsByIds(
                List.of(order.getApplicantUserId(), order.getAssigneeUserId()));
        String originatorDd = ddIds.get(order.getApplicantUserId());
        String approverDd = ddIds.get(order.getAssigneeUserId());
        if (originatorDd == null || approverDd == null) {
            recordFailure(ApprovalInstance.BIZ_CHANGE, orderId, processCode,
                    "处理人或发起人未绑定钉钉，已降级站内审批", order.getSerialNo());
            return;
        }

        List<ChangeOrderItem> items = changeOrderItemMapper.selectList(
                new LambdaQueryWrapper<ChangeOrderItem>()
                        .eq(ChangeOrderItem::getOrderId, orderId)
                        .orderByAsc(ChangeOrderItem::getId));
        Map<Long, Asset> assets = loadAssets(items.stream()
                .map(ChangeOrderItem::getAssetId).toList());

        List<Map<String, String>> form = new ArrayList<>();
        form.add(field("单据编号", order.getSerialNo()));
        form.add(field("资产编号", joinBarcodes(items, assets)));
        form.add(field("变更后使用人", order.getNewUserName()));
        form.add(field("变更后位置", order.getNewLocationId() != null
                ? locationName(order.getNewLocationId()) : null));
        form.add(field("变更原因", order.getReason()));

        createInstance(ApprovalInstance.BIZ_CHANGE, orderId, processCode, originatorDd,
                List.of(approverDd), form, order.getSerialNo(), false);
    }

    // ------------------------------------------------------------------ 内部

    /** 调钉钉创建实例 + 写映射 + 回填单据；失败记 FAILED + 告警（不抛出） */
    private void createInstance(String bizType, Long bizId, String processCode, String originatorDd,
                                List<String> approverDdIds, List<Map<String, String>> form,
                                String serialNo, boolean isReceipt) {
        // 幂等：已有 SYNCED 映射不重复创建（事件重放/重试场景）
        ApprovalInstance existing = approvalInstanceMapper.selectOne(
                new LambdaQueryWrapper<ApprovalInstance>()
                        .eq(ApprovalInstance::getBizType, bizType)
                        .eq(ApprovalInstance::getBizId, bizId)
                        .eq(ApprovalInstance::getSyncStatus, ApprovalInstance.SYNC_SYNCED));
        if (existing != null) {
            log.info("单据已有钉钉实例，跳过重复同步（bizType={}, bizId={}）", bizType, bizId);
            return;
        }

        ApprovalInstance record = new ApprovalInstance();
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setProcessCode(processCode);
        record.setOriginatorDdUserId(originatorDd);
        try {
            List<String> cc = new ArrayList<>();
            cc.add(originatorDd);
            cc.addAll(props.getCcUserIds());
            String instanceId = apiClient.createProcessInstance(
                    processCode, originatorDd, approverDdIds, cc, form);
            record.setProcessInstanceId(instanceId);
            record.setSyncStatus(ApprovalInstance.SYNC_SYNCED);
            record.setStatus("RUNNING");
            approvalInstanceMapper.insert(record);
            // 回填单据 dingtalk_instance_id（审批中心区分站内/钉钉通道展示）
            if (isReceipt) {
                receiptMapper.update(null, new LambdaUpdateWrapper<ReceiveReceipt>()
                        .eq(ReceiveReceipt::getId, bizId)
                        .set(ReceiveReceipt::getDingtalkInstanceId, instanceId));
            } else if (ApprovalInstance.BIZ_TRANSFER.equals(bizType)) {
                transferOrderMapper.update(null, new LambdaUpdateWrapper<TransferOrder>()
                        .eq(TransferOrder::getId, bizId)
                        .set(TransferOrder::getDingtalkInstanceId, instanceId));
            } else {
                changeOrderMapper.update(null, new LambdaUpdateWrapper<ChangeOrder>()
                        .eq(ChangeOrder::getId, bizId)
                        .set(ChangeOrder::getDingtalkInstanceId, instanceId));
            }
            log.info("单据已同步钉钉审批：{}（bizId={}，instanceId={}）", serialNo, bizId, instanceId);
        } catch (DingTalkApiException e) {
            record.setSyncStatus(ApprovalInstance.SYNC_FAILED);
            record.setSyncError(truncate(e.getMessage()));
            approvalInstanceMapper.insert(record);
            log.error("钉钉审批实例创建失败（{}，bizId={}）：{}", serialNo, bizId, e.getMessage());
            alertAdmins("钉钉审批创建失败：" + e.getMessage() + "（单号：" + serialNo
                    + "，已降级站内审批）");
        }
    }

    /** 发起前校验不过（未绑定钉钉等）：记 FAILED（无实例 id）+ 告警 */
    private void recordFailure(String bizType, Long bizId, String processCode,
                               String error, String serialNo) {
        ApprovalInstance record = new ApprovalInstance();
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setProcessCode(processCode);
        record.setSyncStatus(ApprovalInstance.SYNC_FAILED);
        record.setSyncError(truncate(error));
        approvalInstanceMapper.insert(record);
        log.warn("钉钉同步降级（{}，bizId={}）：{}", serialNo, bizId, error);
        alertAdmins("钉钉审批未创建：" + error + "（单号：" + serialNo + "）");
    }

    /**
     * 幂等前置检查：该单据已存在 SYNCED 记录则跳过（事件重放/并发触发不重复建钉钉实例，
     * 也不追加 FAILED 记录）。
     */
    private boolean alreadySynced(String bizType, Long bizId) {
        return approvalInstanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getBizType, bizType)
                .eq(ApprovalInstance::getBizId, bizId)
                .eq(ApprovalInstance::getSyncStatus, ApprovalInstance.SYNC_SYNCED)) != null;
    }

    private String bizTypeOfReceipt(ReceiptType type) {
        return type == ReceiptType.RECEIVE ? ApprovalInstance.BIZ_RECEIVE : ApprovalInstance.BIZ_BORROW;
    }

    private String processCode(String key) {
        String code = props.getProcessCodes().get(key);
        return (code == null || code.isBlank()) ? null : code;
    }

    private Map<Long, Asset> loadAssets(List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Map.of();
        }
        return assetMapper.selectBatchIds(assetIds.stream()
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
    }

    private String locationName(Long locationId) {
        Location location = locationMapper.selectById(locationId);
        return location != null ? location.getName() : null;
    }

    private String joinBarcodes(List<? extends Object> items, Map<Long, Asset> assets) {
        return items.stream()
                .map(item -> assetIdOf(item))
                .map(assets::get)
                .filter(Objects::nonNull)
                .map(Asset::getBarcode)
                .collect(Collectors.joining("\n"));
    }

    private String joinNames(List<? extends Object> items, Map<Long, Asset> assets) {
        return items.stream()
                .map(this::assetIdOf)
                .map(assets::get)
                .filter(Objects::nonNull)
                .map(Asset::getName)
                .collect(Collectors.joining("\n"));
    }

    /** 明细行类型不同（三种 item），统一取 assetId */
    private Long assetIdOf(Object item) {
        if (item instanceof ReceiveReceiptItem r) {
            return r.getAssetId();
        }
        if (item instanceof TransferOrderItem t) {
            return t.getAssetId();
        }
        if (item instanceof ChangeOrderItem c) {
            return c.getAssetId();
        }
        return null;
    }

    private Map<String, String> field(String name, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("value", value == null ? "" : value);
        return map;
    }

    private void alertAdmins(String message) {
        for (Long adminId : userDirectory.userIdsByRole(props.getAdminRole())) {
            notificationService.notify(adminId, NotificationType.DINGTALK_SYNC_ALERT,
                    message, "DINGTALK", 0L);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
