package com.sk.asset.service.transfer.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.dto.transfer.TransferQuery;
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
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.transfer.TransferSource;
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
import com.sk.asset.service.transfer.TransferOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 调拨单服务实现（M05，ATR 单）。
 * 发起不锁定资产状态（撤销/拒绝资产不变）；确认时事务内做状态联动 + 归属更新 +
 * 持有关系闭环/新建 + 写 asset_log，任一步失败整体回滚。
 * 持有终态一致性：填使用人/部门 → TRANSFER 持有 + IN_USE；只填区域 → 回库（持有人归零）+ IDLE。
 */
@Service
@RequiredArgsConstructor
public class TransferOrderServiceImpl implements TransferOrderService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** asset_allocation.type：调拨确认后新建的调入方持有记录类型 */
    private static final String ALLOCATION_TYPE_TRANSFER = "TRANSFER";

    private final TransferOrderMapper orderMapper;
    private final TransferOrderItemMapper itemMapper;
    private final AssetMapper assetMapper;
    private final AssetAllocationMapper allocationMapper;
    private final ReceiveReceiptMapper receiptMapper;
    private final ReceiveReceiptItemMapper receiptItemMapper;
    private final ChangeOrderMapper changeOrderMapper;
    private final ChangeOrderItemMapper changeOrderItemMapper;
    private final LocationMapper locationMapper;
    private final AssetService assetService;

    // ---- create ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrder create(TransferApplyReq req, Long applicantUserId, String applicantName) {
        return create(req, applicantUserId, applicantName, TransferSource.MANUAL, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrder create(TransferApplyReq req, Long applicantUserId, String applicantName,
                                TransferSource source, Long stocktakeId) {
        // 调入区域与调入部门至少一项（调拨必须改变归属维度之一）
        boolean hasToLocation = req.getToLocationId() != null;
        boolean hasToDepartment = req.getToDepartment() != null && !req.getToDepartment().isBlank();
        if (!hasToLocation && !hasToDepartment) {
            throw new BusinessException(400, "调入区域与调入部门至少填写一项");
        }

        // 去重（前端跨页多选可能重复提交同一资产）
        List<Long> assetIds = req.getAssetIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
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

        // 2. 报废资产不可调拨
        List<Long> discarded = assetIds.stream()
                .filter(id -> AssetStatus.DISCARD.name().equals(assets.get(id).getStatus()))
                .toList();
        if (!discarded.isEmpty()) {
            throw new BusinessException(409, "资产已报废，不能调拨（id=" + discarded + "）");
        }

        // 3. 待确认调拨占用校验：同一资产同时只能有一张 PENDING 调拨单
        List<TransferOrderItem> occupiedTransfers = itemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>()
                        .in(TransferOrderItem::getAssetId, assetIds));
        if (!occupiedTransfers.isEmpty()) {
            Set<Long> orderIds = occupiedTransfers.stream()
                    .map(TransferOrderItem::getOrderId)
                    .collect(Collectors.toSet());
            List<TransferOrder> pendingTransfers = orderMapper.selectList(
                    new LambdaQueryWrapper<TransferOrder>()
                            .in(TransferOrder::getId, orderIds)
                            .eq(TransferOrder::getStatus, TransferStatus.PENDING.name()));
            if (!pendingTransfers.isEmpty()) {
                throw new BusinessException(409, "资产已有待确认的调拨单（单号："
                        + pendingTransfers.stream().map(TransferOrder::getSerialNo)
                                .collect(Collectors.joining("、"))
                        + "），不能重复发起");
            }
        }

        // 4. 待审批领用/借用占用校验：资产在 PENDING 单据中不可调拨（与 M04 互斥占用）
        List<ReceiveReceiptItem> occupiedReceipts = receiptItemMapper.selectList(
                new LambdaQueryWrapper<ReceiveReceiptItem>()
                        .in(ReceiveReceiptItem::getAssetId, assetIds));
        if (!occupiedReceipts.isEmpty()) {
            Set<Long> receiptIds = occupiedReceipts.stream()
                    .map(ReceiveReceiptItem::getReceiptId)
                    .collect(Collectors.toSet());
            List<ReceiveReceipt> pendingReceipts = receiptMapper.selectList(
                    new LambdaQueryWrapper<ReceiveReceipt>()
                            .in(ReceiveReceipt::getId, receiptIds)
                            .eq(ReceiveReceipt::getStatus, ReceiptStatus.PENDING.name()));
            if (!pendingReceipts.isEmpty()) {
                throw new BusinessException(409, "资产已有待审批的领用/借用单（单号："
                        + pendingReceipts.stream().map(ReceiveReceipt::getSerialNo)
                                .collect(Collectors.joining("、"))
                        + "），不可调拨");
            }
        }

        // 5. 待确认变更单占用校验（M06）：资产在 PENDING 变更单中不可调拨（互斥占用）
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
                        + "），不可调拨");
            }
        }

        // 6. 调入位置存在性校验
        if (req.getToLocationId() != null
                && locationMapper.selectById(req.getToLocationId()) == null) {
            throw new BusinessException(400, "调入位置不存在（id=" + req.getToLocationId() + "）");
        }

        // 7. 生成单号（锁定读串行取号，见 generateSerialNo）
        String serialNo = generateSerialNo();

        // 8. 写主表 + 明细（调出位置未显式指定时取首台资产当前位置）
        Asset firstAsset = assets.get(assetIds.get(0));
        TransferOrder order = new TransferOrder();
        order.setSerialNo(serialNo);
        order.setStatus(TransferStatus.PENDING.name());
        order.setSource(source.name());
        order.setStocktakeId(stocktakeId);
        order.setApplicantUserId(applicantUserId);
        order.setApplicantName(applicantName);
        order.setFromLocationId(firstAsset != null ? firstAsset.getLocationId() : null);
        order.setToLocationId(req.getToLocationId());
        order.setToDepartment(hasToDepartment ? req.getToDepartment().trim() : null);
        order.setToUserId(req.getToUserId());
        order.setToUserName(req.getToUserName());
        order.setReason(req.getReason());
        order.setCompanyId(firstAsset != null ? firstAsset.getCompanyId() : null);
        orderMapper.insert(order);
        for (Long assetId : assetIds) {
            TransferOrderItem item = new TransferOrderItem();
            item.setOrderId(order.getId());
            item.setAssetId(assetId);
            itemMapper.insert(item);
        }

        // 调拨不锁定资产状态（撤销/拒绝资产不变），确认时才更新归属
        return getById(order.getId());
    }

    // ---- list / getById ----

    @Override
    public List<TransferOrder> list(TransferQuery query) {
        LambdaQueryWrapper<TransferOrder> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getStatus() != null) {
                wrapper.eq(TransferOrder::getStatus, query.getStatus().name());
            }
            if (query.getSource() != null) {
                wrapper.eq(TransferOrder::getSource, query.getSource().name());
            }
            if (query.getUserId() != null) {
                wrapper.eq(TransferOrder::getApplicantUserId, query.getUserId());
            }
            if (query.getDept() != null && !query.getDept().isBlank()) {
                wrapper.like(TransferOrder::getToDepartment, query.getDept().trim());
            }
            if (query.getDate() != null) {
                LocalDateTime start = query.getDate().atStartOfDay();
                wrapper.ge(TransferOrder::getCreatedAt, start)
                        .lt(TransferOrder::getCreatedAt, start.plusDays(1));
            }
        }
        wrapper.orderByDesc(TransferOrder::getId);
        List<TransferOrder> orders = orderMapper.selectList(wrapper);
        fillItems(orders);
        return orders;
    }

    @Override
    public TransferOrder getById(Long id) {
        TransferOrder order = orderMapper.selectById(id);
        if (order == null) {
            return null;
        }
        fillItems(List.of(order));
        return order;
    }

    // ---- confirm / reject / cancel ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrder confirm(Long id, Long confirmerUserId, String confirmerName) {
        TransferOrder order = requirePendingOrder(id);
        requireNotApplicant(order, confirmerUserId);

        List<TransferOrderItem> items = listItems(id);
        List<Long> assetIds = items.stream().map(TransferOrderItem::getAssetId).toList();
        Map<Long, Asset> assets = assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        for (Long assetId : assetIds) {
            Asset asset = assets.get(assetId);
            if (asset == null) {
                throw new BusinessException(409, "资产不存在或已删除（id=" + assetId + "），调拨单无法确认");
            }
            if (AssetStatus.DISCARD.name().equals(asset.getStatus())) {
                throw new BusinessException(409, "资产「" + asset.getBarcode() + "」已报废，调拨单无法确认");
            }
        }

        for (TransferOrderItem item : items) {
            confirmAsset(order, assets.get(item.getAssetId()), confirmerUserId);
        }

        order.setStatus(TransferStatus.COMPLETED.name());
        order.setConfirmerUserId(confirmerUserId);
        order.setConfirmerName(confirmerName);
        order.setConfirmTime(LocalDateTime.now());
        orderMapper.updateById(order);

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrder reject(Long id, String reason, Long confirmerUserId, String confirmerName) {
        TransferOrder order = requirePendingOrder(id);
        requireNotApplicant(order, confirmerUserId);

        // 拒绝：资产不变，仅记录处理人与原因
        order.setStatus(TransferStatus.REJECTED.name());
        order.setConfirmerUserId(confirmerUserId);
        order.setConfirmerName(confirmerName);
        order.setConfirmTime(LocalDateTime.now());
        order.setRejectReason(reason);
        orderMapper.updateById(order);

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrder cancel(Long id, Long operatorUserId) {
        TransferOrder order = requirePendingOrder(id);
        // 撤销仅发起方（调出方）可操作；管理员撤销待角色体系接入后再放开
        if (operatorUserId != null && !operatorUserId.equals(order.getApplicantUserId())) {
            throw new BusinessException(403, "仅发起人可撤销调拨单");
        }

        // 撤销：资产不变
        order.setStatus(TransferStatus.CANCELLED.name());
        orderMapper.updateById(order);

        return getById(id);
    }

    // ---- 私有方法 ----

    /**
     * 单台资产确认调拨：状态联动 + 闭环旧持有 + 更新归属字段 + 新建调入方持有 + 写日志。
     *
     * 持有一致性（终态二选一，避免"在用却无人持有"矛盾）：
     * - 填使用人：新建 type=TRANSFER 持有（user_id=负责人）→ IN_USE
     * - 只填部门：新建"部门持有"（user_id=null, department=新部门）→ IN_USE
     * - 只填区域：视为"调拨回库"，闭环旧持有 + 清空 user_id/user_department → IDLE
     */
    private void confirmAsset(TransferOrder order, Asset asset, Long confirmerUserId) {
        Long assetId = asset.getId();
        boolean hasToLocation = order.getToLocationId() != null;
        boolean hasDept = order.getToDepartment() != null && !order.getToDepartment().isBlank();
        boolean hasUser = order.getToUserId() != null;
        boolean holderTransfer = hasUser || hasDept;

        // 1. 状态联动（走状态机校验，同态跳过）：有新持有 → IN_USE；回库 → IDLE
        AssetStatus targetStatus = holderTransfer ? AssetStatus.IN_USE : AssetStatus.IDLE;
        if (!targetStatus.name().equals(asset.getStatus())) {
            assetService.changeStatus(assetId, targetStatus, confirmerUserId, "调拨",
                    holderTransfer
                            ? "调拨单 " + order.getSerialNo() + " 调入"
                            : "调拨单 " + order.getSerialNo() + " 调拨回库");
        }

        // 2. 闭环旧持有记录（持有人随调出终结）
        List<AssetAllocation> activeAllocations = allocationMapper.selectList(
                new LambdaQueryWrapper<AssetAllocation>()
                        .eq(AssetAllocation::getAssetId, assetId)
                        .isNull(AssetAllocation::getReturnedAt));
        String closureNote = "调拨单 " + order.getSerialNo() + " 调出，持有关系随调拨终结";
        for (AssetAllocation allocation : activeAllocations) {
            allocationMapper.update(null, new LambdaUpdateWrapper<AssetAllocation>()
                    .eq(AssetAllocation::getId, allocation.getId())
                    .set(AssetAllocation::getReturnedAt, LocalDateTime.now())
                    .set(AssetAllocation::getNote, closureNote));
        }

        // 3. 更新资产归属字段：位置（若填）；持有人按终态写入或清零（回库）
        LambdaUpdateWrapper<Asset> assetUpdate = new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, assetId);
        if (hasToLocation) {
            assetUpdate.set(Asset::getLocationId, order.getToLocationId());
        }
        if (holderTransfer) {
            assetUpdate.set(Asset::getUserId, order.getToUserId())
                    .set(Asset::getUserDepartment, order.getToDepartment());
        } else {
            // 调拨回库：持有人归零（与 IDLE 终态对齐，不留悬死归属）
            assetUpdate.set(Asset::getUserId, null)
                    .set(Asset::getUserDepartment, null);
        }
        assetMapper.update(null, assetUpdate);

        // 4. 新建调入方持有记录（type=TRANSFER）：填使用人 → 人持有；只填部门 → 部门持有（user_id=null）
        if (holderTransfer) {
            AssetAllocation allocation = new AssetAllocation();
            allocation.setAssetId(assetId);
            allocation.setUserId(order.getToUserId());
            allocation.setUserName(order.getToUserName());
            allocation.setType(ALLOCATION_TYPE_TRANSFER);
            allocation.setDepartment(order.getToDepartment());
            allocation.setAllocatedAt(LocalDateTime.now());
            allocation.setCompanyId(asset.getCompanyId());
            allocation.setNote("调拨单 " + order.getSerialNo() + " 调入");
            allocationMapper.insert(allocation);
        }

        // 5. 写调拨明细日志（状态日志由 changeStatus 另行记录）：【字段】由【旧值】变更为【新值】
        assetService.writeLog(assetId, "调拨", confirmerUserId,
                buildConfirmLogContent(order, asset, activeAllocations, holderTransfer));
    }

    /** 拼装确认日志内容：调拨单单号 + 归属字段变更明细（位置/部门/使用人） */
    private String buildConfirmLogContent(TransferOrder order, Asset asset,
                                          List<AssetAllocation> activeAllocations,
                                          boolean holderTransfer) {
        Map<Long, String> locationNames = locationNamesOf(
                asset.getLocationId(), order.getToLocationId());
        String oldLocation = displayValue(asset.getLocationId(), locationNames);
        String newLocation = displayValue(order.getToLocationId(), locationNames);
        // 旧持有展示：人持有 → 人名/ID；部门持有（user_id=null）→ 部门名（部门持有）
        String oldHolder = activeAllocations.stream()
                .map(a -> {
                    if (a.getUserName() != null && !a.getUserName().isBlank()) {
                        return a.getUserName();
                    }
                    if (a.getUserId() != null) {
                        return String.valueOf(a.getUserId());
                    }
                    return a.getDepartment() != null && !a.getDepartment().isBlank()
                            ? a.getDepartment() + "（部门持有）" : "";
                })
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.joining("、"));
        String newHolder = holderDisplay(order);

        StringBuilder content = new StringBuilder("调拨单 " + order.getSerialNo() + " 确认调拨");
        if (!holderTransfer) {
            content.append("（回库）");
        }
        if (order.getToLocationId() != null) {
            content.append("；【位置】由【").append(oldLocation).append("】变更为【")
                    .append(newLocation).append("】");
        }
        if (order.getToDepartment() != null && !order.getToDepartment().isBlank()) {
            content.append("；【部门】由【").append(asset.getUserDepartment() == null
                            ? "未设置" : asset.getUserDepartment())
                    .append("】变更为【").append(order.getToDepartment()).append("】");
        }
        content.append("；【使用人】由【").append(oldHolder.isEmpty() ? "未设置" : oldHolder)
                .append("】变更为【").append(newHolder).append("】");
        return content.toString();
    }

    /** 持有终态展示：填使用人 → 人名/ID；只填部门 → 部门持有；回库 → 未设置 */
    private String holderDisplay(TransferOrder order) {
        if (order.getToUserId() != null) {
            return order.getToUserName() != null && !order.getToUserName().isBlank()
                    ? order.getToUserName() : String.valueOf(order.getToUserId());
        }
        if (order.getToDepartment() != null && !order.getToDepartment().isBlank()) {
            return order.getToDepartment() + "（部门持有）";
        }
        return "未设置";
    }

    /** 批量取位置名称（旧/新位置一次查齐），查不到回退 ID 展示 */
    private Map<Long, String> locationNamesOf(Long... locationIds) {
        Set<Long> ids = new HashSet<>();
        for (Long id : locationIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return locationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName, (a, b) -> a));
    }

    private String displayValue(Long locationId, Map<Long, String> locationNames) {
        if (locationId == null) {
            return "未设置";
        }
        return locationNames.getOrDefault(locationId, String.valueOf(locationId));
    }

    /**
     * 单号生成：ATR + yyyyMMdd + 4位序号，当天自增。
     * SELECT ... FOR UPDATE 锁定读（当前读）串行取号：并发事务会阻塞在锁定读上，
     * 待前序事务提交后读到最新 MAX，避免取到重复序号；uk_transfer_serial_no 兜底。
     */
    private String generateSerialNo() {
        String prefix = "ATR" + LocalDate.now().format(DATE_FORMATTER);
        TransferOrder latest = orderMapper.selectOne(new LambdaQueryWrapper<TransferOrder>()
                .likeRight(TransferOrder::getSerialNo, prefix)
                .orderByDesc(TransferOrder::getSerialNo)
                .last("LIMIT 1 FOR UPDATE"));
        int next = 1;
        if (latest != null && latest.getSerialNo() != null
                && latest.getSerialNo().length() > prefix.length()) {
            next = Integer.parseInt(latest.getSerialNo().substring(prefix.length())) + 1;
        }
        return prefix + String.format("%04d", next);
    }

    private TransferOrder requirePendingOrder(Long id) {
        TransferOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "调拨单不存在（id=" + id + "）");
        }
        if (!TransferStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException(409, "调拨单已处理，不能重复操作（当前状态："
                    + TransferStatus.of(order.getStatus()).getLabel() + "）");
        }
        return order;
    }

    /** 确认/拒绝为调入方动作，与发起人（调出方）分离（对齐 M04 审批与申请分离） */
    private void requireNotApplicant(TransferOrder order, Long operatorUserId) {
        if (operatorUserId != null && operatorUserId.equals(order.getApplicantUserId())) {
            throw new BusinessException(403, "调入方确认/拒绝不能由发起人自己操作");
        }
    }

    private List<TransferOrderItem> listItems(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getOrderId, orderId)
                .orderByAsc(TransferOrderItem::getId));
    }

    /** 批量回填明细行（含资产编码/名称/序列号）与位置名称，列表与详情共用 */
    private void fillItems(List<TransferOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream().map(TransferOrder::getId).toList();
        List<TransferOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .in(TransferOrderItem::getOrderId, orderIds)
                .orderByAsc(TransferOrderItem::getId));
        if (items.isEmpty()) {
            orders.forEach(order -> order.setItems(List.of()));
        } else {
            Set<Long> assetIds = items.stream().map(TransferOrderItem::getAssetId)
                    .collect(Collectors.toSet());
            Map<Long, Asset> assets = assetMapper.selectBatchIds(assetIds).stream()
                    .collect(Collectors.toMap(Asset::getId, Function.identity()));
            Map<Long, List<TransferOrderItem>> itemsByOrder = items.stream()
                    .collect(Collectors.groupingBy(TransferOrderItem::getOrderId));
            for (TransferOrder order : orders) {
                List<TransferOrderItem> orderItems = itemsByOrder
                        .getOrDefault(order.getId(), List.of());
                for (TransferOrderItem item : orderItems) {
                    Asset asset = assets.get(item.getAssetId());
                    if (asset != null) {
                        item.setAssetBarcode(asset.getBarcode());
                        item.setAssetName(asset.getName());
                        item.setAssetSn(asset.getSn());
                    }
                }
                order.setItems(orderItems);
            }
        }
        fillLocationNames(orders);
    }

    /** 批量回填调出/调入位置名称 */
    private void fillLocationNames(List<TransferOrder> orders) {
        Set<Long> locationIds = new HashSet<>();
        for (TransferOrder order : orders) {
            if (order.getFromLocationId() != null) {
                locationIds.add(order.getFromLocationId());
            }
            if (order.getToLocationId() != null) {
                locationIds.add(order.getToLocationId());
            }
        }
        if (locationIds.isEmpty()) {
            return;
        }
        Map<Long, String> names = locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName, (a, b) -> a));
        for (TransferOrder order : orders) {
            order.setFromLocationName(names.get(order.getFromLocationId()));
            order.setToLocationName(names.get(order.getToLocationId()));
        }
    }
}
