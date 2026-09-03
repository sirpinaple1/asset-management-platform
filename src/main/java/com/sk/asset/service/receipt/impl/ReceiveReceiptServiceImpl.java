package com.sk.asset.service.receipt.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.receipt.ApprovalPreviewResp;
import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.receipt.ReceiptQuery;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.change.ChangeOrderItem;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.transfer.TransferOrderItem;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.enums.change.ChangeStatus;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.receipt.ReceiptType;
import com.sk.asset.enums.transfer.TransferStatus;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.change.ChangeOrderItemMapper;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptItemMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderItemMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.asset.AssetService;
import com.sk.asset.service.approval.ApprovalChainResolver;
import com.sk.asset.service.notification.NotificationService;
import com.sk.asset.service.receipt.ReceiveReceiptService;
import com.sk.asset.dingtalk.event.OaSyncRequestedEvent;
import com.sk.asset.dingtalk.event.OaTaskExecuteRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 领用/借用单服务实现。
 * 领用与借用共用单据流（type 区分，serial_no 前缀 ARE/BOR）；
 * 资产状态联动统一走 AssetService.changeStatus（合法性校验 + 写日志），
 * 任一资产流转失败整个事务回滚（单据与资产状态保持一致）。
 *
 * <p>多级主管审批链（v1.0 改造，原两级链"部门主管+仓管员"仅调拨/变更/历史单据保留）：
 * 提交时经 DeptManagerChainResolver 解析「固定一级（如谷仍山）→ 发起人部门逐级向上主管」，
 * 站内快照只冻结前两级（step1=固定一级，step2=直接主管），钉钉推送完整链；
 * 快照外审批节点的操作由钉钉回调侧记日志（单据终态与钉钉实例终审严格同步）。
 * 解析失败 400 阻止提交 + 超管告警；固定一级=申请人本人同样 400 死单防御；
 * assignee_user_id 列保持"当前审批层级快照审批人"（一级通过后物理推进为二级审批人）——
 * 列表筛选/B3 统计/审批中心"待我处理"口径零改动。
 * 存量单（approval_step2_user_id 为 NULL）保持 B1 旧单层审批语义。</p>
 */
@Service
@RequiredArgsConstructor
public class ReceiveReceiptServiceImpl implements ReceiveReceiptService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 两级审批人为同一人时的合并说明（写入 approval_step1_remark，审计可追溯） */
    private static final String MERGED_STEP_REMARK = "两级审批人为同一人，合并为一次审批";

    private final ReceiveReceiptMapper receiptMapper;
    private final ReceiveReceiptItemMapper itemMapper;
    private final AssetAllocationMapper allocationMapper;
    private final AssetMapper assetMapper;
    private final LocationMapper locationMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final TransferOrderItemMapper transferOrderItemMapper;
    private final ChangeOrderMapper changeOrderMapper;
    private final ChangeOrderItemMapper changeOrderItemMapper;
    private final AssetService assetService;
    private final NotificationService notificationService;
    private final ApprovalChainResolver approvalChainResolver;
    private final com.sk.asset.service.approval.DeptManagerChainResolver deptManagerChainResolver;
    private final com.sk.asset.service.approval.ApprovalConfigService approvalConfigService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiveReceipt create(ReceiptApplyReq req, Long applicantUserId, String applicantName) {
        ReceiptType type = ReceiptType.of(req.getType());
        // assigneeUserId 已废弃（V20260833）：审批人由审批链配置自动路由并冻结快照

        // 0. 领用区域存在性校验（必填：审批通过后资产位置更新至此，盘点按位置扫资产的依据）
        Location location = locationMapper.selectById(req.getLocationId());
        if (location == null) {
            throw new BusinessException(400, "领用区域不存在（id=" + req.getLocationId() + "）");
        }

        // 0b. 多级主管审批链解析（固定一级 + 发起人部门逐级向上主管，提交时冻结前两级快照）。
        // 兜底 = 阻止提交：固定一级未配置/未绑定、无部门、链上无可绑定主管、固定一级=申请人本人
        // → 400，同时以独立事务告警 systemAdmin（告警不随本事务回滚丢失）。
        // DEPT_SUPERVISOR/WAREHOUSE_KEEPER 两级链配置保留（调拨/变更/历史单据依赖），领用/借用不再读取。
        com.sk.asset.service.approval.DeptManagerChainResolver.MultiResolution resolution =
                deptManagerChainResolver.tryResolveMultiLevel(applicantUserId);
        if (!resolution.resolvable()) {
            approvalChainResolver.alertAdmins(resolution.error(),
                    displayName(applicantUserId, applicantName) + " 发起" + type.getLabel());
            throw new BusinessException(400, resolution.error());
        }

        return doCreate(req, type, location, applicantUserId, applicantName, resolution.snapshot(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiveReceipt createFromDingtalk(ReceiptApplyReq req, Long applicantUserId, String applicantName,
                                             ApprovalChainResolver.ResolvedChain chain) {
        ReceiptType type = ReceiptType.of(req.getType());
        if (req.getLocationId() == null) {
            throw new BusinessException(400, "领用区域不能为空");
        }
        Location location = locationMapper.selectById(req.getLocationId());
        if (location == null) {
            throw new BusinessException(400, "领用区域不存在（id=" + req.getLocationId() + "）");
        }
        // 审批人快照以钉钉实例 tasks 为准（chain 由 import 服务解析传入），不走站内审批链解析
        return doCreate(req, type, location, applicantUserId, applicantName, chain, true);
    }

    /** 建单主体（create 与 createFromDingtalk 共用）：占用校验 → 取号 → 落表 → 状态联动 → 通知 */
    private ReceiveReceipt doCreate(ReceiptApplyReq req, ReceiptType type, Location location,
                                    Long applicantUserId, String applicantName,
                                    ApprovalChainResolver.ResolvedChain chain, boolean fromDingtalk) {
        List<Long> assetIds = distinctAssetIds(req.getAssetIds());
        if (assetIds.isEmpty()) {
            throw new BusinessException(400, "请至少选择一台资产");
        }

        // 1. 资产存在性校验
        Map<Long, Asset> assets = assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        List<Long> missing = assetIds.stream().filter(id -> !assets.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(404, "资产不存在（id=" + missing + "）");
        }

        // 2. 待审批占用校验：同一资产同时只能有一张 PENDING 单（领用与借用互斥占用）
        List<ReceiveReceiptItem> occupied = itemMapper.selectList(new LambdaQueryWrapper<ReceiveReceiptItem>()
                .in(ReceiveReceiptItem::getAssetId, assetIds));
        if (!occupied.isEmpty()) {
            Set<Long> receiptIds = occupied.stream()
                    .map(ReceiveReceiptItem::getReceiptId)
                    .collect(Collectors.toSet());
            List<ReceiveReceipt> pending = receiptMapper.selectList(new LambdaQueryWrapper<ReceiveReceipt>()
                    .in(ReceiveReceipt::getId, receiptIds)
                    .eq(ReceiveReceipt::getStatus, ReceiptStatus.PENDING.name()));
            if (!pending.isEmpty()) {
                throw new BusinessException(409, "资产已有待审批的领用/借用单（单号："
                        + pending.stream().map(ReceiveReceipt::getSerialNo).collect(Collectors.joining("、"))
                        + "），领用与借用不可同时申请");
            }
        }

        // 2b. 待确认调拨占用校验（M05）：资产在 PENDING 调拨单中不可发起领用/借用（互斥占用）
        List<TransferOrderItem> transferOccupied = transferOrderItemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>()
                        .in(TransferOrderItem::getAssetId, assetIds));
        if (!transferOccupied.isEmpty()) {
            Set<Long> orderIds = transferOccupied.stream()
                    .map(TransferOrderItem::getOrderId)
                    .collect(Collectors.toSet());
            List<TransferOrder> pendingTransfers = transferOrderMapper.selectList(
                    new LambdaQueryWrapper<TransferOrder>()
                            .in(TransferOrder::getId, orderIds)
                            .eq(TransferOrder::getStatus, TransferStatus.PENDING.name()));
            if (!pendingTransfers.isEmpty()) {
                throw new BusinessException(409, "资产已有待确认的调拨单（单号："
                        + pendingTransfers.stream().map(TransferOrder::getSerialNo)
                                .collect(Collectors.joining("、"))
                        + "），不可发起领用/借用");
            }
        }

        // 2c. 待确认变更单占用校验（M06）：资产在 PENDING 变更单中不可发起领用/借用（互斥占用）
        List<ChangeOrderItem> changeOccupied = changeOrderItemMapper.selectList(
                new LambdaQueryWrapper<ChangeOrderItem>()
                        .in(ChangeOrderItem::getAssetId, assetIds));
        if (!changeOccupied.isEmpty()) {
            Set<Long> changeIds = changeOccupied.stream()
                    .map(ChangeOrderItem::getOrderId)
                    .collect(Collectors.toSet());
            List<ChangeOrder> pendingChanges = changeOrderMapper.selectList(
                    new LambdaQueryWrapper<ChangeOrder>()
                            .in(ChangeOrder::getId, changeIds)
                            .eq(ChangeOrder::getStatus, ChangeStatus.PENDING.name()));
            if (!pendingChanges.isEmpty()) {
                throw new BusinessException(409, "资产已有待确认的变更单（单号："
                        + pendingChanges.stream().map(ChangeOrder::getSerialNo)
                                .collect(Collectors.joining("、"))
                        + "），不可发起领用/借用");
            }
        }

        // 3. 生成单号（锁定读串行取号，见 generateSerialNo）
        String serialNo = generateSerialNo(type);

        // 4. 写主表 + 明细（两级审批人快照冻结；两级同一人时 approval_step 直接置 2 合并为一次审批）
        ReceiveReceipt receipt = new ReceiveReceipt();
        receipt.setSerialNo(serialNo);
        receipt.setType(type.name());
        receipt.setStatus(ReceiptStatus.PENDING.name());
        receipt.setApplicantUserId(applicantUserId);
        receipt.setApplicantName(applicantName);
        receipt.setApprovalStep(chain.isMerged() ? 2 : 1);
        receipt.setApprovalStep1UserId(chain.getStep1UserId());
        receipt.setApprovalStep1Name(chain.getStep1Name());
        receipt.setApprovalStep1SourceKey(chain.getStep1SourceKey());
        receipt.setApprovalStep2UserId(chain.getStep2UserId());
        receipt.setApprovalStep2Name(chain.getStep2Name());
        receipt.setApprovalStep2SourceKey(chain.getStep2SourceKey());
        if (chain.isMerged()) {
            // 合并审批：一级视为提交时自动通过（时间+说明留审计痕迹），仅需该人一次审批即终态
            receipt.setApprovalStep1At(LocalDateTime.now());
            receipt.setApprovalStep1Remark(MERGED_STEP_REMARK);
        }
        // assignee = 当前审批层级快照审批人（列表/统计/审批中心"待我处理"据此过滤）
        receipt.setAssigneeUserId(chain.isMerged() ? chain.getStep2UserId() : chain.getStep1UserId());
        receipt.setDepartment(req.getDepartment());
        receipt.setLocationId(req.getLocationId());
        receipt.setReason(req.getReason());
        receiptMapper.insert(receipt);
        for (Long assetId : assetIds) {
            ReceiveReceiptItem item = new ReceiveReceiptItem();
            item.setReceiptId(receipt.getId());
            item.setAssetId(assetId);
            itemMapper.insert(item);
        }

        // 5. 资产 IDLE/IN_USE → PENDING_CONFIRM（状态机校验 + 写日志，非法流转 409 整体回滚）
        String note = type.getLabel() + "单 " + serialNo + " 发起申请，待审批";
        for (Long assetId : assetIds) {
            assetService.changeStatus(assetId, AssetStatus.PENDING_CONFIRM, applicantUserId, type.getLabel(), note);
        }

        // 6. 通知第一级审批人（B2；两级链单据必通知——审批人已解析且必非申请人）
        Long firstApproverId = chain.isMerged() ? chain.getStep2UserId() : chain.getStep1UserId();
        notificationService.notify(firstApproverId, NotificationType.DOC_SUBMITTED,
                displayName(applicantUserId, applicantName) + " 提交的" + type.getLabel()
                        + "单 " + serialNo + " 待你审批",
                type.name(), receipt.getId());

        // 7. 钉钉 OA 同步事件（M10 入口 A：AFTER_COMMIT 消费，失败降级站内审批不回滚）
        // 入口 B（钉钉发起导入）跳过——单据源自钉钉，回推即死循环
        if (!fromDingtalk) {
            eventPublisher.publishEvent(new OaSyncRequestedEvent(
                    OaSyncRequestedEvent.KIND_RECEIPT, receipt.getId()));
        }

        return getById(receipt.getId());
    }

    /** 资产 ID 去重（前端跨页多选可能重复提交同一资产；入口 B 表单多编号同理） */
    private List<Long> distinctAssetIds(List<Long> assetIds) {
        return assetIds == null ? List.of() : assetIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public ApprovalPreviewResp previewApprovalChain(Long applicantUserId, String applicantName, Long locationId) {
        ApprovalPreviewResp resp = new ApprovalPreviewResp();
        if (locationId == null) {
            resp.setResolvable(false);
            resp.setMessage("请先选择领用区域");
            return resp;
        }
        Location location = locationMapper.selectById(locationId);
        if (location == null) {
            resp.setResolvable(false);
            resp.setMessage("领用区域不存在（id=" + locationId + "）");
            return resp;
        }
        com.sk.asset.service.approval.DeptManagerChainResolver.MultiResolution resolution =
                deptManagerChainResolver.tryResolveMultiLevel(applicantUserId);
        if (!resolution.resolvable()) {
            resp.setResolvable(false);
            resp.setMessage(resolution.error());
            return resp;
        }
        ApprovalChainResolver.ResolvedChain chain = resolution.snapshot();
        resp.setResolvable(true);
        resp.setStep1UserId(chain.getStep1UserId());
        resp.setStep1UserName(chain.getStep1Name());
        resp.setStep1SourceKey(chain.getStep1SourceKey());
        resp.setStep2UserId(chain.getStep2UserId());
        resp.setStep2UserName(chain.getStep2Name());
        resp.setStep2SourceKey(chain.getStep2SourceKey());
        resp.setMerged(chain.isMerged());
        return resp;
    }

    @Override
    public List<ReceiveReceipt> list(ReceiptQuery query) {
        LambdaQueryWrapper<ReceiveReceipt> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getType() != null) {
                wrapper.eq(ReceiveReceipt::getType, query.getType().name());
            }
            if (query.getStatus() != null) {
                wrapper.eq(ReceiveReceipt::getStatus, query.getStatus().name());
            }
            if (query.getUserId() != null) {
                wrapper.eq(ReceiveReceipt::getApplicantUserId, query.getUserId());
            }
            if (query.getAssigneeUserId() != null) {
                wrapper.eq(ReceiveReceipt::getAssigneeUserId, query.getAssigneeUserId());
            }
            if (Boolean.TRUE.equals(query.getUnassigned())) {
                wrapper.isNull(ReceiveReceipt::getAssigneeUserId);
            }
            if (query.getDate() != null) {
                LocalDateTime start = query.getDate().atStartOfDay();
                wrapper.ge(ReceiveReceipt::getCreatedAt, start)
                        .lt(ReceiveReceipt::getCreatedAt, start.plusDays(1));
            }
        }
        wrapper.orderByDesc(ReceiveReceipt::getId);
        List<ReceiveReceipt> receipts = receiptMapper.selectList(wrapper);
        fillItems(receipts);
        return receipts;
    }

    @Override
    public ReceiveReceipt getById(Long id) {
        ReceiveReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null) {
            return null;
        }
        fillItems(List.of(receipt));
        return receipt;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiveReceipt approve(Long id, Long approverUserId, String approverName) {
        ReceiveReceipt receipt = requirePendingReceipt(id);
        requireNotApplicant(receipt, approverUserId);
        if (isChainMode(receipt)) {
            // 两级链：操作人必须 = 当前 step 快照审批人（403 拦截他人；一级审批人不能审二级）
            requireChainApprover(receipt, approverUserId);
            if (Integer.valueOf(1).equals(receipt.getApprovalStep())) {
                return advanceToStep2(receipt, approverUserId, approverName);
            }
            // step=2 → 走下方终态逻辑（合并单提交即 step=2，一次审批即终态）
        } else {
            // 存量单（无链快照）：保持 B1 旧语义
            requireAssignee(receipt, approverUserId);
        }

        List<ReceiveReceiptItem> items = listItems(id);
        String applicantDisplay = displayName(receipt.getApplicantUserId(), receipt.getApplicantName());
        String note = ReceiptType.of(receipt.getType()).getLabel() + "单 " + receipt.getSerialNo()
                + " 审批通过，使用人：" + applicantDisplay;
        // 领用区域：审批通过后资产位置更新至此（盘点按位置扫资产的依据）。
        // 存量单（加列前的 PENDING 单）location_id 为 NULL，跳过位置更新。
        Location receiveLocation = receipt.getLocationId() != null
                ? locationMapper.selectById(receipt.getLocationId()) : null;
        if (receipt.getLocationId() != null) {
            note += "，领用区域：" + (receiveLocation != null ? receiveLocation.getName() : receipt.getLocationId());
        }
        for (ReceiveReceiptItem item : items) {
            Asset asset = assetMapper.selectById(item.getAssetId());
            // 资产状态 PENDING_CONFIRM → IN_USE（写日志）
            assetService.changeStatus(item.getAssetId(), AssetStatus.IN_USE, approverUserId,
                    ReceiptType.of(receipt.getType()).getLabel(), note);
            // 更新资产持有人（使用人/部门快照）+ 位置（领用区域 B）
            // + 区域管理员随位置实时解析（B4：责任=使用人；无使用人场景落到区域管理员）
            LambdaUpdateWrapper<Asset> assetUpdate = new LambdaUpdateWrapper<Asset>()
                    .eq(Asset::getId, item.getAssetId())
                    .set(Asset::getUserId, receipt.getApplicantUserId())
                    .set(Asset::getUserDepartment, receipt.getDepartment());
            if (receipt.getLocationId() != null) {
                assetUpdate.set(Asset::getLocationId, receipt.getLocationId())
                        .set(Asset::getAdminUserId,
                                approvalConfigService.keeperUserIdOf(receipt.getLocationId()));
            }
            assetMapper.update(null, assetUpdate);
            // 写持有关系（"查现在在谁手里"），同时冻结发放前位置快照（B4：归还带回 A 区的依据）
            AssetAllocation allocation = new AssetAllocation();
            allocation.setAssetId(item.getAssetId());
            allocation.setUserId(receipt.getApplicantUserId());
            allocation.setUserName(receipt.getApplicantName());
            allocation.setType(receipt.getType());
            allocation.setDepartment(receipt.getDepartment());
            allocation.setAllocatedAt(LocalDateTime.now());
            allocation.setCompanyId(asset != null ? asset.getCompanyId() : null);
            allocation.setLocationBefore(asset != null ? asset.getLocationId() : null);
            allocationMapper.insert(allocation);
        }

        receipt.setStatus(ReceiptStatus.APPROVED.name());
        receipt.setApproverUserId(approverUserId);
        receipt.setApproverName(approverName);
        receipt.setApproveTime(LocalDateTime.now());
        receiptMapper.updateById(receipt);

        // B5 双向同步：站内终审通过 → 代执行钉钉侧待办（事务提交后；无钉钉实例则跳过）
        publishOaTaskExecute(receipt, approverUserId, "agree", null);

        // 通知发起人（B2）：审批人与申请人必不同人（requireNotApplicant），必通知
        notificationService.notify(receipt.getApplicantUserId(), NotificationType.DOC_APPROVED,
                "你发起的" + ReceiptType.of(receipt.getType()).getLabel() + "单 " + receipt.getSerialNo()
                        + " 已通过（审批人：" + approverName + "）",
                receipt.getType(), receipt.getId());

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiveReceipt reject(Long id, String reason, Long approverUserId, String approverName) {
        ReceiveReceipt receipt = requirePendingReceipt(id);
        requireNotApplicant(receipt, approverUserId);
        String rejectLevelLabel;
        if (isChainMode(receipt)) {
            // 两级链：操作人必须 = 当前 step 快照审批人，任一级拒绝整单 REJECTED
            requireChainApprover(receipt, approverUserId);
            rejectLevelLabel = rejectLevelLabel(receipt);
        } else {
            requireAssignee(receipt, approverUserId);
            rejectLevelLabel = null;
        }

        List<ReceiveReceiptItem> items = listItems(id);
        ReceiptType type = ReceiptType.of(receipt.getType());
        String note = type.getLabel() + "单 " + receipt.getSerialNo() + " 审批拒绝：" + reason;
        for (ReceiveReceiptItem item : items) {
            // 资产状态 PENDING_CONFIRM → IDLE（写日志）
            assetService.changeStatus(item.getAssetId(), AssetStatus.IDLE, approverUserId,
                    type.getLabel(), note);
        }

        receipt.setStatus(ReceiptStatus.REJECTED.name());
        receipt.setApproverUserId(approverUserId);
        receipt.setApproverName(approverName);
        receipt.setApproveTime(LocalDateTime.now());
        receipt.setApproveRemark(reason);
        receiptMapper.updateById(receipt);

        // B5 双向同步：站内拒绝 → 代执行钉钉侧待办（refuse，携带拒绝原因）
        publishOaTaskExecute(receipt, approverUserId, "refuse", "站内拒绝：" + reason);

        // 通知发起人（B2）：含拒绝原因 + 拒绝层级（两级链单据）
        String levelPart = rejectLevelLabel != null ? "，" + rejectLevelLabel : "";
        notificationService.notify(receipt.getApplicantUserId(), NotificationType.DOC_REJECTED,
                "你发起的" + type.getLabel() + "单 " + receipt.getSerialNo()
                        + " 已拒绝（审批人：" + approverName + levelPart + "，原因：" + reason + "）",
                receipt.getType(), receipt.getId());

        return getById(id);
    }

    /**
     * 一级审批通过：approval_step 推进到 2，assignee 物理推进为二级审批人
     * （列表/统计/审批中心"待我处理"随推进变化），通知二级审批人与发起人。
     */
    private ReceiveReceipt advanceToStep2(ReceiveReceipt receipt, Long approverUserId, String approverName) {
        ReceiptType type = ReceiptType.of(receipt.getType());
        String applicantDisplay = displayName(receipt.getApplicantUserId(), receipt.getApplicantName());

        // 条件推进（CAS）：仅当单据仍为 PENDING 且处于一级时推进到二级。
        // 防并发重复推进（双击/双端）：affected=0 → 409 回滚，避免重复通知二级审批人
        // + 重复发布钉钉代执行事件
        LocalDateTime step1At = LocalDateTime.now();
        int advanced = receiptMapper.update(null, new LambdaUpdateWrapper<ReceiveReceipt>()
                .eq(ReceiveReceipt::getId, receipt.getId())
                .eq(ReceiveReceipt::getStatus, ReceiptStatus.PENDING.name())
                .eq(ReceiveReceipt::getApprovalStep, 1)
                .set(ReceiveReceipt::getApprovalStep, 2)
                .set(ReceiveReceipt::getApprovalStep1At, step1At)
                .set(ReceiveReceipt::getAssigneeUserId, receipt.getApprovalStep2UserId()));
        if (advanced == 0) {
            throw new BusinessException(409, "单据审批层级已变化，请刷新后重试（"
                    + receipt.getSerialNo() + "）");
        }
        // 同步内存对象：后续通知/返回值仍读快照字段，保持与库内一致的可见性
        receipt.setApprovalStep(2);
        receipt.setApprovalStep1At(step1At);
        receipt.setAssigneeUserId(receipt.getApprovalStep2UserId());

        // 通知二级审批人（B2）：待你审批
        notificationService.notify(receipt.getApprovalStep2UserId(), NotificationType.DOC_SUBMITTED,
                applicantDisplay + " 的" + type.getLabel() + "单 " + receipt.getSerialNo()
                        + " 一级审批已通过，待你审批",
                receipt.getType(), receipt.getId());
        // 通知发起人（B2）：一级通过进度（后续层级在钉钉多级链完成，站内随终审同步）
        notificationService.notify(receipt.getApplicantUserId(), NotificationType.DOC_PROGRESS,
                "你发起的" + type.getLabel() + "单 " + receipt.getSerialNo()
                        + " 一级审批已通过（审批人：" + approverName + "），待部门主管审批",
                receipt.getType(), receipt.getId());

        // B5 双向同步：站内一级通过 → 代执行钉钉侧一级待办（后续节点仍由钉钉多级链推进）
        publishOaTaskExecute(receipt, approverUserId, "agree", null);

        return getById(receipt.getId());
    }

    /** 站内审批结果同步钉钉（B5 双向同步）：单据关联钉钉实例才发布，AFTER_COMMIT 代执行 */
    private void publishOaTaskExecute(ReceiveReceipt receipt, Long approverUserId,
                                      String result, String remark) {
        if (receipt.getDingtalkInstanceId() == null || receipt.getDingtalkInstanceId().isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new OaTaskExecuteRequestedEvent(
                receipt.getDingtalkInstanceId(), approverUserId, result, remark));
    }

    /** 拒绝层级标签（通知展示用）：一级=固定审批人；二级=直接主管；合并单特殊标注 */
    private static String rejectLevelLabel(ReceiveReceipt receipt) {
        boolean merged = Objects.equals(receipt.getApprovalStep1UserId(), receipt.getApprovalStep2UserId());
        if (Integer.valueOf(1).equals(receipt.getApprovalStep())) {
            return "一级审批";
        }
        return merged ? "合并审批（一级兼二级审批人）" : "二级审批（部门主管）";
    }

    /** 两级链模式判定：二级审批人快照非空（新提单必有；存量单 NULL 走旧单层语义） */
    private static boolean isChainMode(ReceiveReceipt receipt) {
        return receipt.getApprovalStep2UserId() != null;
    }

    /** 两级链门禁：操作人必须 = 当前 step 的快照审批人（快照冻结，人员调动不影响在途单据） */
    private void requireChainApprover(ReceiveReceipt receipt, Long operatorUserId) {
        Long currentApproverId = Integer.valueOf(1).equals(receipt.getApprovalStep())
                ? receipt.getApprovalStep1UserId() : receipt.getApprovalStep2UserId();
        String currentApproverName = Integer.valueOf(1).equals(receipt.getApprovalStep())
                ? receipt.getApprovalStep1Name() : receipt.getApprovalStep2Name();
        if (operatorUserId == null || !operatorUserId.equals(currentApproverId)) {
            throw new BusinessException(403, "当前审批层级仅审批人（"
                    + displayName(currentApproverId, currentApproverName) + "）可操作");
        }
    }

    /**
     * 单号生成：前缀 + yyyyMMdd + 4位序号，当天按前缀分别自增。
     * SELECT ... FOR UPDATE 锁定读（当前读）串行取号：并发事务会阻塞在锁定读上，
     * 待前序事务提交后读到最新 MAX，避免取到重复序号；uk_receipt_serial_no 兜底。
     */
    private String generateSerialNo(ReceiptType type) {
        String prefix = type.getSerialPrefix() + LocalDate.now().format(DATE_FORMATTER);
        ReceiveReceipt latest = receiptMapper.selectOne(new LambdaQueryWrapper<ReceiveReceipt>()
                .likeRight(ReceiveReceipt::getSerialNo, prefix)
                .orderByDesc(ReceiveReceipt::getSerialNo)
                .last("LIMIT 1 FOR UPDATE"));
        int next = 1;
        if (latest != null && latest.getSerialNo() != null
                && latest.getSerialNo().length() > prefix.length()) {
            next = Integer.parseInt(latest.getSerialNo().substring(prefix.length())) + 1;
        }
        return prefix + String.format("%04d", next);
    }

    private ReceiveReceipt requirePendingReceipt(Long id) {
        // SELECT ... FOR UPDATE 锁定读：串行化并发审批（站内双击 / 站内与钉钉回调双端并发），
        // 后到事务阻塞至前序提交后读到最新状态，被下方 PENDING 校验 409 拦截
        ReceiveReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<ReceiveReceipt>()
                .eq(ReceiveReceipt::getId, id)
                .last("LIMIT 1 FOR UPDATE"));
        if (receipt == null) {
            throw new BusinessException(404, "单据不存在（id=" + id + "）");
        }
        if (!ReceiptStatus.PENDING.name().equals(receipt.getStatus())) {
            throw new BusinessException(409, "单据已审批，不能重复操作（当前状态："
                    + ReceiptStatus.of(receipt.getStatus()).getLabel() + "）");
        }
        return receipt;
    }

    private void requireNotApplicant(ReceiveReceipt receipt, Long operatorUserId) {
        if (operatorUserId != null && operatorUserId.equals(receipt.getApplicantUserId())) {
            throw new BusinessException(403, "审批人与申请人不能是同一人");
        }
    }

    /** 指定处理人门禁（B1，存量单）：assignee 非空时，审批人必须是 assignee；NULL=共享池保持现状 */
    private void requireAssignee(ReceiveReceipt receipt, Long operatorUserId) {
        if (receipt.getAssigneeUserId() != null && operatorUserId != null
                && !operatorUserId.equals(receipt.getAssigneeUserId())) {
            throw new BusinessException(403, "该单据已指定处理人，仅指定处理人可审批");
        }
    }

    private List<ReceiveReceiptItem> listItems(Long receiptId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ReceiveReceiptItem>()
                .eq(ReceiveReceiptItem::getReceiptId, receiptId)
                .orderByAsc(ReceiveReceiptItem::getId));
    }

    /** 批量回填明细行（含资产编码/名称/序列号），列表与详情共用 */
    private void fillItems(List<ReceiveReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            return;
        }
        List<Long> receiptIds = receipts.stream().map(ReceiveReceipt::getId).toList();
        List<ReceiveReceiptItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReceiveReceiptItem>()
                .in(ReceiveReceiptItem::getReceiptId, receiptIds)
                .orderByAsc(ReceiveReceiptItem::getId));
        fillLocationNames(receipts);
        if (items.isEmpty()) {
            receipts.forEach(receipt -> receipt.setItems(List.of()));
            return;
        }
        Set<Long> assetIds = items.stream().map(ReceiveReceiptItem::getAssetId).collect(Collectors.toSet());
        Map<Long, Asset> assets = assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        Map<Long, List<ReceiveReceiptItem>> itemsByReceipt = items.stream()
                .collect(Collectors.groupingBy(ReceiveReceiptItem::getReceiptId));
        for (ReceiveReceipt receipt : receipts) {
            List<ReceiveReceiptItem> receiptItems = itemsByReceipt
                    .getOrDefault(receipt.getId(), List.of());
            for (ReceiveReceiptItem item : receiptItems) {
                Asset asset = assets.get(item.getAssetId());
                if (asset != null) {
                    item.setAssetBarcode(asset.getBarcode());
                    item.setAssetName(asset.getName());
                    item.setAssetSn(asset.getSn());
                }
            }
            receipt.setItems(receiptItems);
        }
    }

    private String displayName(Long userId, String name) {
        return (name != null && !name.isBlank()) ? name : String.valueOf(userId);
    }

    /** 批量回填领用区域名称（存量单 location_id 为 NULL 时不回填） */
    private void fillLocationNames(List<ReceiveReceipt> receipts) {
        Set<Long> locationIds = receipts.stream()
                .map(ReceiveReceipt::getLocationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (locationIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName));
        for (ReceiveReceipt receipt : receipts) {
            if (receipt.getLocationId() != null) {
                receipt.setLocationName(names.get(receipt.getLocationId()));
            }
        }
    }
}
