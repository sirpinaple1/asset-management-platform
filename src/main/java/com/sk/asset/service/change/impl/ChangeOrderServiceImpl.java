package com.sk.asset.service.change.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.change.ChangeApplyReq;
import com.sk.asset.dto.change.ChangeQuery;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.change.ChangeOrderItem;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.transfer.TransferOrderItem;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.enums.change.ChangeField;
import com.sk.asset.enums.change.ChangeStatus;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.transfer.TransferStatus;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.change.ChangeOrderItemMapper;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptItemMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderItemMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.asset.AssetService;
import com.sk.asset.service.change.ChangeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 实物信息变更单服务实现（M06，AOC 单）。
 * 变更不流转资产状态（闲置/在用均可修正信息），不锁定资产状态；
 * 确认时事务内更新资产归属字段 + 使用人变化时同步持有关系 + 写 asset_log，任一步失败整体回滚。
 * 明细行只记录实际发生变化的字段（发起时与当前值比对），value_before/value_after 存展示值
 * （位置/公司存名称、使用人存姓名），供变更前/后对比展示与 AOC 打印格式。
 */
@Service
@RequiredArgsConstructor
public class ChangeOrderServiceImpl implements ChangeOrderService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** asset_allocation.type：变更确认且使用人变化后新建的持有记录类型 */
    private static final String ALLOCATION_TYPE_CHANGE = "CHANGE";

    private final ChangeOrderMapper orderMapper;
    private final ChangeOrderItemMapper itemMapper;
    private final AssetMapper assetMapper;
    private final AssetAllocationMapper allocationMapper;
    private final ReceiveReceiptMapper receiptMapper;
    private final ReceiveReceiptItemMapper receiptItemMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final TransferOrderItemMapper transferOrderItemMapper;
    private final LocationMapper locationMapper;
    private final CompanyMapper companyMapper;
    private final AssetService assetService;
    private final UserDirectory userDirectory;

    // ---- create ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChangeOrder create(ChangeApplyReq req, Long applicantUserId, String applicantName) {
        // 1. 至少一项变更内容（全部 new_* 为 null 的变更单没有意义）
        boolean hasChangeField = req.getNewUserId() != null
                || req.getNewUserDepartment() != null
                || req.getNewLocationId() != null
                || req.getNewLocationDetail() != null
                || req.getNewCompanyId() != null;
        if (!hasChangeField) {
            throw new BusinessException(400, "请至少指定一项变更内容（使用人/使用部门/区域/存放位置明细/归属公司）");
        }

        // 1b. 指定处理人校验（B1）：变更允许发起人自审，可指定为自己；需存在于 comm_public_basic
        if (req.getAssigneeUserId() != null && !userDirectory.exists(req.getAssigneeUserId())) {
            throw new BusinessException(400, "指定处理人不存在（id=" + req.getAssigneeUserId() + "）");
        }

        // 2. 去重（前端跨页多选可能重复提交同一资产）
        List<Long> assetIds = req.getAssetIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (assetIds.isEmpty()) {
            throw new BusinessException(400, "请至少选择一台资产");
        }

        // 3. 资产存在性校验
        Map<Long, Asset> assets = assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        List<Long> missing = assetIds.stream().filter(id -> !assets.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(404, "资产不存在（id=" + missing + "）");
        }

        // 4. 报废资产不可变更
        List<Long> discarded = assetIds.stream()
                .filter(id -> AssetStatus.DISCARD.name().equals(assets.get(id).getStatus()))
                .toList();
        if (!discarded.isEmpty()) {
            throw new BusinessException(409, "资产已报废，不能变更（id=" + discarded + "）");
        }

        // 5. 待审批领用/借用占用校验（与 M04 互斥）
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
                        + "），不可发起变更");
            }
        }

        // 6. 待确认调拨占用校验（与 M05 互斥）
        List<TransferOrderItem> occupiedTransfers = transferOrderItemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>()
                        .in(TransferOrderItem::getAssetId, assetIds));
        if (!occupiedTransfers.isEmpty()) {
            Set<Long> transferIds = occupiedTransfers.stream()
                    .map(TransferOrderItem::getOrderId)
                    .collect(Collectors.toSet());
            List<TransferOrder> pendingTransfers = transferOrderMapper.selectList(
                    new LambdaQueryWrapper<TransferOrder>()
                            .in(TransferOrder::getId, transferIds)
                            .eq(TransferOrder::getStatus, TransferStatus.PENDING.name()));
            if (!pendingTransfers.isEmpty()) {
                throw new BusinessException(409, "资产已有待确认的调拨单（单号："
                        + pendingTransfers.stream().map(TransferOrder::getSerialNo)
                                .collect(Collectors.joining("、"))
                        + "），不可发起变更");
            }
        }

        // 7. 待确认变更单占用校验：同一资产同时只能有一张 PENDING 变更单
        List<ChangeOrderItem> occupiedChanges = itemMapper.selectList(
                new LambdaQueryWrapper<ChangeOrderItem>()
                        .in(ChangeOrderItem::getAssetId, assetIds));
        if (!occupiedChanges.isEmpty()) {
            Set<Long> changeIds = occupiedChanges.stream()
                    .map(ChangeOrderItem::getOrderId)
                    .collect(Collectors.toSet());
            List<ChangeOrder> pendingChanges = orderMapper.selectList(
                    new LambdaQueryWrapper<ChangeOrder>()
                            .in(ChangeOrder::getId, changeIds)
                            .eq(ChangeOrder::getStatus, ChangeStatus.PENDING.name()));
            if (!pendingChanges.isEmpty()) {
                throw new BusinessException(409, "资产已有待确认的变更单（单号："
                        + pendingChanges.stream().map(ChangeOrder::getSerialNo)
                                .collect(Collectors.joining("、"))
                        + "），不能重复发起");
            }
        }

        // 8. 变更后位置/公司存在性校验
        if (req.getNewLocationId() != null
                && locationMapper.selectById(req.getNewLocationId()) == null) {
            throw new BusinessException(400, "变更后位置不存在（id=" + req.getNewLocationId() + "）");
        }
        if (req.getNewCompanyId() != null
                && companyMapper.selectById(req.getNewCompanyId()) == null) {
            throw new BusinessException(400, "变更后归属公司不存在（id=" + req.getNewCompanyId() + "）");
        }

        // 9. 生成单号（锁定读串行取号，见 generateSerialNo）
        String serialNo = generateSerialNo();

        // 10. 写主表（trim 文本字段；company 取首台资产当前归属）
        Asset firstAsset = assets.get(assetIds.get(0));
        ChangeOrder order = new ChangeOrder();
        order.setSerialNo(serialNo);
        order.setStatus(ChangeStatus.PENDING.name());
        order.setApplicantUserId(applicantUserId);
        order.setApplicantName(applicantName);
        order.setAssigneeUserId(req.getAssigneeUserId());
        order.setReason(req.getReason());
        order.setNewUserId(req.getNewUserId());
        order.setNewUserName(trimToNull(req.getNewUserName()));
        order.setNewUserDepartment(trimToNull(req.getNewUserDepartment()));
        order.setNewLocationId(req.getNewLocationId());
        order.setNewLocationDetail(trimToNull(req.getNewLocationDetail()));
        order.setNewCompanyId(req.getNewCompanyId());
        order.setCompanyId(firstAsset != null ? firstAsset.getCompanyId() : null);
        orderMapper.insert(order);

        // 11. 生成明细行：每台资产的每个"实际变化"字段一行（变更前/后展示值）
        Map<Long, List<ChangeOrderItem>> itemRowsByAsset = buildItemRows(order.getId(), req, assets);
        for (List<ChangeOrderItem> rows : itemRowsByAsset.values()) {
            for (ChangeOrderItem row : rows) {
                itemMapper.insert(row);
            }
        }

        // 变更不锁定资产状态（撤销资产不变），确认时才更新归属字段
        return getById(order.getId());
    }

    // ---- list / getById ----

    @Override
    public List<ChangeOrder> list(ChangeQuery query) {
        LambdaQueryWrapper<ChangeOrder> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getAssetId() != null) {
                // 按资产查变更历史：先查明细行反查主表
                List<ChangeOrderItem> items = itemMapper.selectList(
                        new LambdaQueryWrapper<ChangeOrderItem>()
                                .eq(ChangeOrderItem::getAssetId, query.getAssetId()));
                if (items.isEmpty()) {
                    return List.of();
                }
                Set<Long> orderIds = items.stream()
                        .map(ChangeOrderItem::getOrderId)
                        .collect(Collectors.toSet());
                wrapper.in(ChangeOrder::getId, orderIds);
            }
            if (query.getStatus() != null) {
                wrapper.eq(ChangeOrder::getStatus, query.getStatus().name());
            }
            if (query.getUserId() != null) {
                wrapper.eq(ChangeOrder::getApplicantUserId, query.getUserId());
            }
            if (query.getAssigneeUserId() != null) {
                wrapper.eq(ChangeOrder::getAssigneeUserId, query.getAssigneeUserId());
            }
            if (Boolean.TRUE.equals(query.getUnassigned())) {
                wrapper.isNull(ChangeOrder::getAssigneeUserId);
            }
            if (query.getDate() != null) {
                LocalDateTime start = query.getDate().atStartOfDay();
                wrapper.ge(ChangeOrder::getCreatedAt, start)
                        .lt(ChangeOrder::getCreatedAt, start.plusDays(1));
            }
        }
        wrapper.orderByDesc(ChangeOrder::getId);
        List<ChangeOrder> orders = orderMapper.selectList(wrapper);
        fillItems(orders);
        return orders;
    }

    @Override
    public ChangeOrder getById(Long id) {
        ChangeOrder order = orderMapper.selectById(id);
        if (order == null) {
            return null;
        }
        fillItems(List.of(order));
        return order;
    }

    // ---- confirm / cancel ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChangeOrder confirm(Long id, Long confirmerUserId, String confirmerName) {
        ChangeOrder order = requirePendingOrder(id);
        // 指定处理人门禁（B1）：assignee 非空时确认人必须是 assignee；NULL=共享池保持现状（允许任何人含发起人自审）
        if (order.getAssigneeUserId() != null && confirmerUserId != null
                && !confirmerUserId.equals(order.getAssigneeUserId())) {
            throw new BusinessException(403, "该变更单已指定处理人，仅指定处理人可确认执行");
        }

        List<ChangeOrderItem> items = listItems(id);
        Map<Long, List<ChangeOrderItem>> itemsByAsset = items.stream()
                .collect(Collectors.groupingBy(ChangeOrderItem::getAssetId));
        Map<Long, Asset> assets = assetMapper.selectBatchIds(itemsByAsset.keySet()).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        for (Map.Entry<Long, List<ChangeOrderItem>> entry : itemsByAsset.entrySet()) {
            Asset asset = assets.get(entry.getKey());
            if (asset == null) {
                throw new BusinessException(409, "资产不存在或已删除（id=" + entry.getKey() + "），变更单无法确认");
            }
            if (AssetStatus.DISCARD.name().equals(asset.getStatus())) {
                throw new BusinessException(409, "资产「" + asset.getBarcode() + "」已报废，变更单无法确认");
            }
            confirmAsset(order, asset, entry.getValue(), confirmerUserId);
        }

        // 变更单为信息修正单据，确认执行允许发起人自己操作（区别于 M04/M05 审批流）
        order.setStatus(ChangeStatus.CONFIRMED.name());
        order.setConfirmerUserId(confirmerUserId);
        order.setConfirmerName(confirmerName);
        order.setConfirmTime(LocalDateTime.now());
        orderMapper.updateById(order);

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChangeOrder cancel(Long id, Long operatorUserId) {
        ChangeOrder order = requirePendingOrder(id);
        // 撤销仅发起人可操作；管理员撤销待角色体系接入后再放开
        if (operatorUserId != null && !operatorUserId.equals(order.getApplicantUserId())) {
            throw new BusinessException(403, "仅发起人可撤销变更单");
        }

        // 撤销：资产不变
        order.setStatus(ChangeStatus.CANCELLED.name());
        orderMapper.updateById(order);

        return getById(id);
    }

    // ---- 私有方法 ----

    /**
     * 生成每台资产的明细行：仅记录"新值非 null 且与当前值不同"的字段，
     * value_before/value_after 存展示值（位置/公司存名称、使用人存姓名）。
     * 任一台资产没有任何变化字段时抛 400（发起时点该资产无需变更，不应混入本单）。
     */
    private Map<Long, List<ChangeOrderItem>> buildItemRows(Long orderId, ChangeApplyReq req,
                                                            Map<Long, Asset> assets) {
        // 展示值准备：位置/公司名称（旧值 + 新值一次查齐）、使用人姓名（从持有中记录取）
        Map<Long, String> locationNames = locationNamesOf(collectIds(assets.values(),
                Asset::getLocationId, req.getNewLocationId()));
        Map<Long, String> companyNames = companyNamesOf(collectIds(assets.values(),
                Asset::getCompanyId, req.getNewCompanyId()));
        Map<Long, List<AssetAllocation>> activeAllocations = activeAllocationsOf(assets.keySet());

        String newUserDepartment = trimToNull(req.getNewUserDepartment());
        String newLocationDetail = trimToNull(req.getNewLocationDetail());
        String newUserName = trimToNull(req.getNewUserName());

        Map<Long, List<ChangeOrderItem>> rowsByAsset = new HashMap<>();
        for (Asset asset : assets.values()) {
            List<ChangeOrderItem> rows = new ArrayList<>();

            // 使用人：展示值取持有中记录的姓名（回退用户 ID），对齐 M05 调拨日志口径
            if (req.getNewUserId() != null && !req.getNewUserId().equals(asset.getUserId())) {
                rows.add(rowOf(orderId, asset, ChangeField.USER_ID,
                        holderDisplay(activeAllocations.get(asset.getId()), asset.getUserId()),
                        newUserName != null ? newUserName : String.valueOf(req.getNewUserId())));
            }
            if (newUserDepartment != null && !newUserDepartment.equals(asset.getUserDepartment())) {
                rows.add(rowOf(orderId, asset, ChangeField.USER_DEPARTMENT,
                        asset.getUserDepartment(), newUserDepartment));
            }
            if (req.getNewLocationId() != null && !req.getNewLocationId().equals(asset.getLocationId())) {
                rows.add(rowOf(orderId, asset, ChangeField.LOCATION_ID,
                        locationNames.get(asset.getLocationId()), locationNames.get(req.getNewLocationId())));
            }
            if (newLocationDetail != null && !newLocationDetail.equals(asset.getLocationDetail())) {
                rows.add(rowOf(orderId, asset, ChangeField.LOCATION_DETAIL,
                        asset.getLocationDetail(), newLocationDetail));
            }
            if (req.getNewCompanyId() != null && !req.getNewCompanyId().equals(asset.getCompanyId())) {
                rows.add(rowOf(orderId, asset, ChangeField.COMPANY_ID,
                        companyNames.get(asset.getCompanyId()), companyNames.get(req.getNewCompanyId())));
            }

            if (rows.isEmpty()) {
                throw new BusinessException(400, "资产「" + asset.getBarcode()
                        + "」的指定变更字段与当前值一致，无变更内容");
            }
            rowsByAsset.put(asset.getId(), rows);
        }
        return rowsByAsset;
    }

    private ChangeOrderItem rowOf(Long orderId, Asset asset, ChangeField field,
                                   String valueBefore, String valueAfter) {
        ChangeOrderItem item = new ChangeOrderItem();
        item.setOrderId(orderId);
        item.setAssetId(asset.getId());
        item.setFieldName(field.getFieldName());
        item.setFieldLabel(field.getLabel());
        item.setValueBefore(valueBefore);
        item.setValueAfter(valueAfter);
        return item;
    }

    /**
     * 单台资产确认执行：更新归属字段（new_* 非 null 的字段）+ 使用人变化时同步持有关系 + 写日志。
     * 日志内容以明细行的变更前/后展示值拼装（与单据严格一致）。
     */
    private void confirmAsset(ChangeOrder order, Asset asset, List<ChangeOrderItem> assetItems,
                              Long confirmerUserId) {
        Long assetId = asset.getId();

        // 1. 更新资产归属字段（new_* 为 null = 不变更）
        LambdaUpdateWrapper<Asset> assetUpdate = new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, assetId);
        if (order.getNewUserId() != null) {
            assetUpdate.set(Asset::getUserId, order.getNewUserId());
        }
        if (order.getNewUserDepartment() != null) {
            assetUpdate.set(Asset::getUserDepartment, order.getNewUserDepartment());
        }
        if (order.getNewLocationId() != null) {
            assetUpdate.set(Asset::getLocationId, order.getNewLocationId());
        }
        if (order.getNewLocationDetail() != null) {
            assetUpdate.set(Asset::getLocationDetail, order.getNewLocationDetail());
        }
        if (order.getNewCompanyId() != null) {
            assetUpdate.set(Asset::getCompanyId, order.getNewCompanyId());
        }
        assetMapper.update(null, assetUpdate);

        // 2. 使用人变化时同步持有关系（闭环旧持有 + 新建 CHANGE 持有），
        //    避免"在用报废悬死持有"同类问题：持有关系与资产使用人脱节
        if (order.getNewUserId() != null && !order.getNewUserId().equals(asset.getUserId())) {
            List<AssetAllocation> activeAllocations = allocationMapper.selectList(
                    new LambdaQueryWrapper<AssetAllocation>()
                            .eq(AssetAllocation::getAssetId, assetId)
                            .isNull(AssetAllocation::getReturnedAt));
            String closureNote = "变更单 " + order.getSerialNo() + " 更正使用人，持有关系随变更终结";
            for (AssetAllocation allocation : activeAllocations) {
                allocationMapper.update(null, new LambdaUpdateWrapper<AssetAllocation>()
                        .eq(AssetAllocation::getId, allocation.getId())
                        .set(AssetAllocation::getReturnedAt, LocalDateTime.now())
                        .set(AssetAllocation::getNote, closureNote));
            }

            AssetAllocation allocation = new AssetAllocation();
            allocation.setAssetId(assetId);
            allocation.setUserId(order.getNewUserId());
            allocation.setUserName(order.getNewUserName());
            allocation.setType(ALLOCATION_TYPE_CHANGE);
            allocation.setDepartment(order.getNewUserDepartment() != null
                    ? order.getNewUserDepartment() : asset.getUserDepartment());
            allocation.setAllocatedAt(LocalDateTime.now());
            allocation.setCompanyId(order.getNewCompanyId() != null
                    ? order.getNewCompanyId() : asset.getCompanyId());
            allocation.setNote("变更单 " + order.getSerialNo() + " 更正使用人");
            allocationMapper.insert(allocation);
        }

        // 3. 写变更日志（不改资产状态）：【字段】由【旧值】变更为【新值】（明细行展示值）
        assetService.writeLog(assetId, "实物信息变更", confirmerUserId,
                buildConfirmLogContent(order, assetItems));
    }

    /** 拼装确认日志内容：变更单单号 + 明细行变更明细 */
    private String buildConfirmLogContent(ChangeOrder order, List<ChangeOrderItem> assetItems) {
        StringBuilder content = new StringBuilder("变更单 " + order.getSerialNo() + " 确认执行");
        for (ChangeOrderItem item : assetItems) {
            content.append("；【").append(item.getFieldLabel()).append("】由【")
                    .append(item.getValueBefore() == null ? "未设置" : item.getValueBefore())
                    .append("】变更为【")
                    .append(item.getValueAfter() == null ? "未设置" : item.getValueAfter())
                    .append("】");
        }
        return content.toString();
    }

    /** 使用人展示值：优先持有中记录的姓名（多人顿号连接），无记录回退用户 ID 字符串 */
    private String holderDisplay(List<AssetAllocation> allocations, Long userId) {
        if (allocations != null && !allocations.isEmpty()) {
            return allocations.stream()
                    .map(a -> a.getUserName() != null && !a.getUserName().isBlank()
                            ? a.getUserName() : String.valueOf(a.getUserId()))
                    .distinct()
                    .collect(Collectors.joining("、"));
        }
        return userId == null ? null : String.valueOf(userId);
    }

    /** 批量取持有中记录（returned_at 为空），按 assetId 分组 */
    private Map<Long, List<AssetAllocation>> activeAllocationsOf(Set<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Map.of();
        }
        return allocationMapper.selectList(new LambdaQueryWrapper<AssetAllocation>()
                        .in(AssetAllocation::getAssetId, assetIds)
                        .isNull(AssetAllocation::getReturnedAt))
                .stream()
                .collect(Collectors.groupingBy(AssetAllocation::getAssetId));
    }

    /** 收集资产当前值 + 新值中的非空 ID，供名称批量查询 */
    private Set<Long> collectIds(Iterable<Asset> assets, Function<Asset, Long> getter, Long newValue) {
        Set<Long> ids = new HashSet<>();
        for (Asset asset : assets) {
            if (getter.apply(asset) != null) {
                ids.add(getter.apply(asset));
            }
        }
        if (newValue != null) {
            ids.add(newValue);
        }
        return ids;
    }

    /** 批量取位置名称，查不到回退 null（明细行存 null 由前端展示"未设置"）；空集返回可 null-key 查询的 Map */
    private Map<Long, String> locationNamesOf(Set<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return new HashMap<>();
        }
        return locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName, (a, b) -> a));
    }

    /** 批量取公司名称 */
    private Map<Long, String> companyNamesOf(Set<Long> companyIds) {
        if (companyIds.isEmpty()) {
            return new HashMap<>();
        }
        return companyMapper.selectBatchIds(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName, (a, b) -> a));
    }

    /**
     * 单号生成：AOC + yyyyMMdd + 4位序号，当天自增。
     * SELECT ... FOR UPDATE 锁定读（当前读）串行取号：并发事务会阻塞在锁定读上，
     * 待前序事务提交后读到最新 MAX，避免取到重复序号；uk_change_serial_no 兜底。
     */
    private String generateSerialNo() {
        String prefix = "AOC" + LocalDate.now().format(DATE_FORMATTER);
        ChangeOrder latest = orderMapper.selectOne(new LambdaQueryWrapper<ChangeOrder>()
                .likeRight(ChangeOrder::getSerialNo, prefix)
                .orderByDesc(ChangeOrder::getSerialNo)
                .last("LIMIT 1 FOR UPDATE"));
        int next = 1;
        if (latest != null && latest.getSerialNo() != null
                && latest.getSerialNo().length() > prefix.length()) {
            next = Integer.parseInt(latest.getSerialNo().substring(prefix.length())) + 1;
        }
        return prefix + String.format("%04d", next);
    }

    private ChangeOrder requirePendingOrder(Long id) {
        ChangeOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "变更单不存在（id=" + id + "）");
        }
        if (!ChangeStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException(409, "变更单已处理，不能重复操作（当前状态："
                    + ChangeStatus.of(order.getStatus()).getLabel() + "）");
        }
        return order;
    }

    private List<ChangeOrderItem> listItems(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ChangeOrderItem>()
                .eq(ChangeOrderItem::getOrderId, orderId)
                .orderByAsc(ChangeOrderItem::getId));
    }

    /** 批量回填明细行（含资产编码/名称/序列号）与变更后位置/公司名称，列表与详情共用 */
    private void fillItems(List<ChangeOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream().map(ChangeOrder::getId).toList();
        List<ChangeOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<ChangeOrderItem>()
                .in(ChangeOrderItem::getOrderId, orderIds)
                .orderByAsc(ChangeOrderItem::getId));
        if (items.isEmpty()) {
            orders.forEach(order -> order.setItems(List.of()));
        } else {
            Set<Long> assetIds = items.stream().map(ChangeOrderItem::getAssetId)
                    .collect(Collectors.toSet());
            Map<Long, Asset> assets = assetMapper.selectBatchIds(assetIds).stream()
                    .collect(Collectors.toMap(Asset::getId, Function.identity()));
            Map<Long, List<ChangeOrderItem>> itemsByOrder = items.stream()
                    .collect(Collectors.groupingBy(ChangeOrderItem::getOrderId));
            for (ChangeOrder order : orders) {
                List<ChangeOrderItem> orderItems = itemsByOrder
                        .getOrDefault(order.getId(), List.of());
                for (ChangeOrderItem item : orderItems) {
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

        // 回填变更后位置/公司名称
        Set<Long> locationIds = new HashSet<>();
        Set<Long> companyIds = new HashSet<>();
        for (ChangeOrder order : orders) {
            if (order.getNewLocationId() != null) {
                locationIds.add(order.getNewLocationId());
            }
            if (order.getNewCompanyId() != null) {
                companyIds.add(order.getNewCompanyId());
            }
        }
        Map<Long, String> locationNames = locationNamesOf(locationIds);
        Map<Long, String> companyNames = companyNamesOf(companyIds);
        for (ChangeOrder order : orders) {
            order.setNewLocationName(locationNames.get(order.getNewLocationId()));
            order.setNewCompanyName(companyNames.get(order.getNewCompanyId()));
        }
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
