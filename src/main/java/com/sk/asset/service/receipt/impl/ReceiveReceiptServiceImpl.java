package com.sk.asset.service.receipt.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.common.BusinessException;
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
import com.sk.asset.service.receipt.ReceiveReceiptService;
import lombok.RequiredArgsConstructor;
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
 */
@Service
@RequiredArgsConstructor
public class ReceiveReceiptServiceImpl implements ReceiveReceiptService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiveReceipt create(ReceiptApplyReq req, Long applicantUserId, String applicantName) {
        ReceiptType type = ReceiptType.of(req.getType());
        // 去重（前端跨页多选可能重复提交同一资产）
        List<Long> assetIds = req.getAssetIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (assetIds.isEmpty()) {
            throw new BusinessException(400, "请至少选择一台资产");
        }

        // 0. 领用区域存在性校验（必填：审批通过后资产位置更新至此，盘点按位置扫资产的依据）
        Location location = locationMapper.selectById(req.getLocationId());
        if (location == null) {
            throw new BusinessException(400, "领用区域不存在（id=" + req.getLocationId() + "）");
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

        // 4. 写主表 + 明细
        ReceiveReceipt receipt = new ReceiveReceipt();
        receipt.setSerialNo(serialNo);
        receipt.setType(type.name());
        receipt.setStatus(ReceiptStatus.PENDING.name());
        receipt.setApplicantUserId(applicantUserId);
        receipt.setApplicantName(applicantName);
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

        return getById(receipt.getId());
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
            // 更新资产持有人（使用人/部门快照）+ 位置（领用区域）
            LambdaUpdateWrapper<Asset> assetUpdate = new LambdaUpdateWrapper<Asset>()
                    .eq(Asset::getId, item.getAssetId())
                    .set(Asset::getUserId, receipt.getApplicantUserId())
                    .set(Asset::getUserDepartment, receipt.getDepartment());
            if (receipt.getLocationId() != null) {
                assetUpdate.set(Asset::getLocationId, receipt.getLocationId());
            }
            assetMapper.update(null, assetUpdate);
            // 写持有关系（"查现在在谁手里"）
            AssetAllocation allocation = new AssetAllocation();
            allocation.setAssetId(item.getAssetId());
            allocation.setUserId(receipt.getApplicantUserId());
            allocation.setUserName(receipt.getApplicantName());
            allocation.setType(receipt.getType());
            allocation.setDepartment(receipt.getDepartment());
            allocation.setAllocatedAt(LocalDateTime.now());
            allocation.setCompanyId(asset != null ? asset.getCompanyId() : null);
            allocationMapper.insert(allocation);
        }

        receipt.setStatus(ReceiptStatus.APPROVED.name());
        receipt.setApproverUserId(approverUserId);
        receipt.setApproverName(approverName);
        receipt.setApproveTime(LocalDateTime.now());
        receiptMapper.updateById(receipt);

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiveReceipt reject(Long id, String reason, Long approverUserId, String approverName) {
        ReceiveReceipt receipt = requirePendingReceipt(id);
        requireNotApplicant(receipt, approverUserId);

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

        return getById(id);
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
        ReceiveReceipt receipt = receiptMapper.selectById(id);
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
