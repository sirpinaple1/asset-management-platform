package com.sk.asset.service.stocktake.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.stocktake.StocktakeBarcodeScanReq;
import com.sk.asset.dto.stocktake.StocktakeCreateReq;
import com.sk.asset.dto.stocktake.StocktakeQuery;
import com.sk.asset.dto.stocktake.StocktakeReportResp;
import com.sk.asset.dto.stocktake.StocktakeScanReq;
import com.sk.asset.dto.stocktake.StocktakeItemResp;
import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.stocktake.Stocktake;
import com.sk.asset.entity.stocktake.StocktakeItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.enums.stocktake.StocktakeItemStatus;
import com.sk.asset.enums.stocktake.StocktakeStatus;
import com.sk.asset.enums.transfer.TransferSource;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.stocktake.StocktakeItemMapper;
import com.sk.asset.mapper.stocktake.StocktakeMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.asset.AssetService;
import com.sk.asset.service.stocktake.StocktakeService;
import com.sk.asset.service.transfer.TransferOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 盘点服务实现（M07）。
 * 创建按范围快照（位置含子树、报废排除）；扫码单向流转（已盘不可重盘）；
 * 完成时剩余待盘批量记盘亏并写资产日志；位置不符明细按实际位置分组触发
 * INVENTORY_TRIGGERED 调拨单（transfer_order.stocktake_id 防重复生成）。
 * 盘盈（EXTRA）= 任务范围外但已登记的资产，处置走人工调拨（报告展示差异）。
 */
@Service
@RequiredArgsConstructor
public class StocktakeServiceImpl implements StocktakeService {

    /** asset_log.operation_type：盘点差异处理（盘亏日志） */
    private static final String OPERATION_STOCKTAKE = "盘点处理";

    private final StocktakeMapper stocktakeMapper;
    private final StocktakeItemMapper itemMapper;
    private final AssetMapper assetMapper;
    private final LocationMapper locationMapper;
    private final CategoryMapper categoryMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final AssetService assetService;
    private final TransferOrderService transferOrderService;

    // ---- create ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Stocktake create(StocktakeCreateReq req, Long creatorUserId, String creatorName) {
        // 1. 范围校验：位置存在性（子树 id 集），分类存在性
        List<Long> locationScopeIds = null;
        if (req.getLocationId() != null) {
            locationScopeIds = subtreeLocationIds(req.getLocationId());
        }
        if (req.getCategoryId() != null && categoryMapper.selectById(req.getCategoryId()) == null) {
            throw new BusinessException(400, "盘点范围分类不存在（id=" + req.getCategoryId() + "）");
        }

        // 2. 圈资产：报废排除，位置（含子树）/分类过滤
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .ne(Asset::getStatus, AssetStatus.DISCARD.name())
                .orderByAsc(Asset::getId);
        if (locationScopeIds != null) {
            wrapper.in(Asset::getLocationId, locationScopeIds);
        }
        if (req.getCategoryId() != null) {
            wrapper.eq(Asset::getCategoryId, req.getCategoryId());
        }
        List<Asset> assets = assetMapper.selectList(wrapper);
        if (assets.isEmpty()) {
            throw new BusinessException(400, "盘点范围内无待盘资产（报废资产不参与盘点）");
        }

        // 3. 写主表 + 明细快照（expected = 创建时资产账面位置）
        Stocktake task = new Stocktake();
        task.setName(req.getName().trim());
        task.setStatus(StocktakeStatus.PENDING.name());
        task.setLocationId(req.getLocationId());
        task.setCategoryId(req.getCategoryId());
        task.setCreatorUserId(creatorUserId);
        task.setCreatorName(creatorName);
        task.setRemark(req.getRemark());
        task.setCompanyId(assets.get(0).getCompanyId());
        stocktakeMapper.insert(task);

        for (Asset asset : assets) {
            StocktakeItem item = new StocktakeItem();
            item.setStocktakeId(task.getId());
            item.setAssetId(asset.getId());
            item.setStatus(StocktakeItemStatus.PENDING.name());
            item.setExpectedLocationId(asset.getLocationId());
            itemMapper.insert(item);
        }

        return getById(task.getId());
    }

    // ---- list / getById / items ----

    @Override
    public List<Stocktake> list(StocktakeQuery query) {
        LambdaQueryWrapper<Stocktake> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getStatus() != null) {
                wrapper.eq(Stocktake::getStatus, query.getStatus().name());
            }
            if (query.getUserId() != null) {
                wrapper.eq(Stocktake::getCreatorUserId, query.getUserId());
            }
            if (query.getDate() != null) {
                LocalDateTime start = query.getDate().atStartOfDay();
                wrapper.ge(Stocktake::getCreatedAt, start)
                        .lt(Stocktake::getCreatedAt, start.plusDays(1));
            }
        }
        wrapper.orderByDesc(Stocktake::getId);
        List<Stocktake> tasks = stocktakeMapper.selectList(wrapper);
        fillRangeNames(tasks);
        fillCounts(tasks);
        return tasks;
    }

    @Override
    public Stocktake getById(Long id) {
        Stocktake task = stocktakeMapper.selectById(id);
        if (task == null) {
            return null;
        }
        fillRangeNames(List.of(task));
        fillCounts(List.of(task));
        task.setItems(fillItems(listItems(id, null)));
        return task;
    }

    @Override
    public List<StocktakeItem> listItems(Long stocktakeId, StocktakeItemStatus status) {
        LambdaQueryWrapper<StocktakeItem> wrapper = new LambdaQueryWrapper<StocktakeItem>()
                .eq(StocktakeItem::getStocktakeId, stocktakeId)
                .orderByAsc(StocktakeItem::getId);
        if (status != null) {
            wrapper.eq(StocktakeItem::getStatus, status.name());
        }
        return fillItems(itemMapper.selectList(wrapper));
    }

    // ---- start / cancel ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Stocktake start(Long stocktakeId, Long userId) {
        Stocktake task = requireTask(stocktakeId);
        if (!StocktakeStatus.PENDING.name().equals(task.getStatus())) {
            throw new BusinessException(409, "盘点任务已开始或已结束，不能重复开始（当前状态："
                    + StocktakeStatus.of(task.getStatus()).getLabel() + "）");
        }
        task.setStatus(StocktakeStatus.IN_PROGRESS.name());
        task.setStartTime(LocalDateTime.now());
        stocktakeMapper.updateById(task);
        return getById(stocktakeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Stocktake cancel(Long stocktakeId, Long userId) {
        Stocktake task = requireTask(stocktakeId);
        String status = task.getStatus();
        if (!StocktakeStatus.PENDING.name().equals(status)
                && !StocktakeStatus.IN_PROGRESS.name().equals(status)) {
            throw new BusinessException(409, "盘点任务已结束，不能取消（当前状态："
                    + StocktakeStatus.of(status).getLabel() + "）");
        }
        if (userId != null && !userId.equals(task.getCreatorUserId())) {
            throw new BusinessException(403, "仅创建人可取消盘点任务");
        }
        task.setStatus(StocktakeStatus.CANCELLED.name());
        stocktakeMapper.updateById(task);
        return getById(stocktakeId);
    }

    // ---- scan ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeItem scanItem(Long stocktakeId, Long itemId, StocktakeScanReq req, Long userId) {
        Stocktake task = requireTask(stocktakeId);
        requireInProgress(task);

        StocktakeItem item = itemMapper.selectById(itemId);
        if (item == null || !stocktakeId.equals(item.getStocktakeId())) {
            throw new BusinessException(404, "盘点明细不存在（id=" + itemId + "）");
        }
        requirePendingItem(item);

        boolean notFound = Boolean.TRUE.equals(req.getNotFound());
        Long actualLocationId = req.getActualLocationId();
        if (!notFound) {
            if (actualLocationId == null) {
                throw new BusinessException(400, "请提交实际位置，或标记为未找到（盘亏）");
            }
            requireLocationExists(actualLocationId);
        }
        return applyScan(item, notFound ? null : actualLocationId, notFound, req.getRemark(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeItem scanByBarcode(Long stocktakeId, StocktakeBarcodeScanReq req, Long userId) {
        Stocktake task = requireTask(stocktakeId);
        requireInProgress(task);

        Asset asset = assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getBarcode, req.getBarcode().trim()));
        if (asset == null) {
            throw new BusinessException(404, "条码未登记的资产：" + req.getBarcode().trim());
        }
        if (AssetStatus.DISCARD.name().equals(asset.getStatus())) {
            throw new BusinessException(409, "资产已报废（" + asset.getBarcode() + "），不能扫码盘点");
        }

        Long actualLocationId = req.getActualLocationId();
        if (actualLocationId != null) {
            requireLocationExists(actualLocationId);
        }

        StocktakeItem item = itemMapper.selectOne(new LambdaQueryWrapper<StocktakeItem>()
                .eq(StocktakeItem::getStocktakeId, stocktakeId)
                .eq(StocktakeItem::getAssetId, asset.getId()));
        if (item != null) {
            // 任务内资产：未提交位置 = 在系统记录位置找到（视为相符）
            requirePendingItem(item);
            return applyScan(item,
                    actualLocationId != null ? actualLocationId : item.getExpectedLocationId(),
                    false, req.getRemark(), userId);
        }

        // 盘盈：资产已登记但不在本任务范围（expected = 账面位置，actual = 发现位置/任务范围位置）
        StocktakeItem extra = new StocktakeItem();
        extra.setStocktakeId(stocktakeId);
        extra.setAssetId(asset.getId());
        extra.setStatus(StocktakeItemStatus.EXTRA.name());
        extra.setExpectedLocationId(asset.getLocationId());
        extra.setActualLocationId(actualLocationId != null ? actualLocationId : task.getLocationId());
        extra.setScannedAt(LocalDateTime.now());
        extra.setScannedByUserId(userId);
        extra.setRemark(req.getRemark());
        itemMapper.insert(extra);
        return fillItems(List.of(extra)).get(0);
    }

    // ---- complete / report ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Stocktake complete(Long stocktakeId, Long userId) {
        Stocktake task = requireTask(stocktakeId);
        if (!StocktakeStatus.IN_PROGRESS.name().equals(task.getStatus())) {
            throw new BusinessException(409, "盘点任务不在进行中，不能完成（当前状态："
                    + StocktakeStatus.of(task.getStatus()).getLabel() + "）");
        }

        // 1. 剩余待盘明细批量记盘亏
        List<StocktakeItem> pendingItems = itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeItem>()
                        .eq(StocktakeItem::getStocktakeId, stocktakeId)
                        .eq(StocktakeItem::getStatus, StocktakeItemStatus.PENDING.name()));
        for (StocktakeItem item : pendingItems) {
            item.setStatus(StocktakeItemStatus.NOT_FOUND.name());
            itemMapper.updateById(item);
        }

        // 2. 盘亏资产逐台写日志（含扫码时手动标记未找到的明细）
        List<StocktakeItem> notFounds = itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeItem>()
                        .eq(StocktakeItem::getStocktakeId, stocktakeId)
                        .eq(StocktakeItem::getStatus, StocktakeItemStatus.NOT_FOUND.name()));
        for (StocktakeItem item : notFounds) {
            assetService.writeLog(item.getAssetId(), OPERATION_STOCKTAKE, userId,
                    "盘点任务「" + task.getName() + "」盘亏：盘点完成时仍未找到实物");
        }

        task.setStatus(StocktakeStatus.COMPLETED.name());
        task.setCompleteTime(LocalDateTime.now());
        stocktakeMapper.updateById(task);
        return getById(stocktakeId);
    }

    @Override
    public StocktakeReportResp report(Long stocktakeId) {
        Stocktake task = requireTask(stocktakeId);
        List<StocktakeItem> items = listItems(stocktakeId, null);

        Map<StocktakeItemStatus, List<StocktakeItem>> byStatus = items.stream()
                .collect(Collectors.groupingBy(i -> StocktakeItemStatus.of(i.getStatus())));

        StocktakeReportResp resp = new StocktakeReportResp();
        resp.setStocktakeId(task.getId());
        resp.setName(task.getName());
        resp.setStatus(task.getStatus());
        resp.setStatusLabel(StocktakeStatus.of(task.getStatus()).getLabel());
        resp.setCompleteTime(task.getCompleteTime());
        resp.setTotalCount((long) items.size());
        resp.setPendingCount(countOf(byStatus, StocktakeItemStatus.PENDING));
        resp.setMatchedCount(countOf(byStatus, StocktakeItemStatus.MATCHED));
        resp.setMismatchCount(countOf(byStatus, StocktakeItemStatus.LOCATION_MISMATCH));
        resp.setNotFoundCount(countOf(byStatus, StocktakeItemStatus.NOT_FOUND));
        resp.setExtraCount(countOf(byStatus, StocktakeItemStatus.EXTRA));
        resp.setMismatches(StocktakeItemResp.fromList(
                byStatus.getOrDefault(StocktakeItemStatus.LOCATION_MISMATCH, List.of())));
        resp.setNotFounds(StocktakeItemResp.fromList(
                byStatus.getOrDefault(StocktakeItemStatus.NOT_FOUND, List.of())));
        resp.setExtras(StocktakeItemResp.fromList(
                byStatus.getOrDefault(StocktakeItemStatus.EXTRA, List.of())));
        return resp;
    }

    // ---- transfer ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TransferOrder> createTransfers(Long stocktakeId, Long operatorUserId, String operatorName) {
        Stocktake task = requireTask(stocktakeId);
        if (!StocktakeStatus.COMPLETED.name().equals(task.getStatus())) {
            throw new BusinessException(409, "盘点任务未完成，不能生成调拨单（当前状态："
                    + StocktakeStatus.of(task.getStatus()).getLabel() + "）");
        }

        // 防重复生成：同一盘点任务只允许触发一次
        Long existing = transferOrderMapper.selectCount(new LambdaQueryWrapper<TransferOrder>()
                .eq(TransferOrder::getStocktakeId, stocktakeId));
        if (existing != null && existing > 0) {
            throw new BusinessException(409, "该盘点任务已生成过调拨单，不能重复生成");
        }

        List<StocktakeItem> mismatches = itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeItem>()
                        .eq(StocktakeItem::getStocktakeId, stocktakeId)
                        .eq(StocktakeItem::getStatus, StocktakeItemStatus.LOCATION_MISMATCH.name()));
        if (mismatches.isEmpty()) {
            throw new BusinessException(400, "无位置不符明细，无需生成调拨单");
        }

        // 按实际位置分组归位（同目标位置一张调拨单）；from 取资产当前位置（= expected）
        Map<Long, List<StocktakeItem>> byActual = mismatches.stream()
                .filter(i -> i.getActualLocationId() != null)
                .collect(Collectors.groupingBy(StocktakeItem::getActualLocationId));

        List<TransferOrder> created = new ArrayList<>();
        for (Map.Entry<Long, List<StocktakeItem>> entry : byActual.entrySet()) {
            TransferApplyReq req = new TransferApplyReq();
            req.setAssetIds(entry.getValue().stream()
                    .map(StocktakeItem::getAssetId).collect(Collectors.toList()));
            req.setToLocationId(entry.getKey());
            req.setReason("盘点差异归位（任务「" + task.getName() + "」）");
            created.add(transferOrderService.create(req, operatorUserId, operatorName,
                    TransferSource.INVENTORY_TRIGGERED, stocktakeId));
        }
        return created;
    }

    // ---- 私有方法 ----

    /** 扫码结果落地：判定 相符/位置不符/盘亏，写盘点人与时间（明细状态单向流转） */
    private StocktakeItem applyScan(StocktakeItem item, Long actualLocationId, boolean notFound,
                                     String remark, Long userId) {
        String target = notFound ? StocktakeItemStatus.NOT_FOUND.name()
                : Objects.equals(actualLocationId, item.getExpectedLocationId())
                        ? StocktakeItemStatus.MATCHED.name()
                        : StocktakeItemStatus.LOCATION_MISMATCH.name();
        item.setStatus(target);
        item.setActualLocationId(notFound ? null : actualLocationId);
        item.setScannedAt(LocalDateTime.now());
        item.setScannedByUserId(userId);
        item.setRemark(remark);
        itemMapper.updateById(item);
        return fillItems(List.of(item)).get(0);
    }

    private void requireInProgress(Stocktake task) {
        if (!StocktakeStatus.IN_PROGRESS.name().equals(task.getStatus())) {
            throw new BusinessException(409, "盘点任务不在进行中（当前状态："
                    + StocktakeStatus.of(task.getStatus()).getLabel() + "），请先开始盘点");
        }
    }

    private void requirePendingItem(StocktakeItem item) {
        if (!StocktakeItemStatus.PENDING.name().equals(item.getStatus())) {
            throw new BusinessException(409, "该明细已盘点（当前结果："
                    + StocktakeItemStatus.of(item.getStatus()).getLabel() + "），不能重复盘点");
        }
    }

    private void requireLocationExists(Long locationId) {
        if (locationMapper.selectById(locationId) == null) {
            throw new BusinessException(400, "实际位置不存在（id=" + locationId + "）");
        }
    }

    private Stocktake requireTask(Long id) {
        Stocktake task = stocktakeMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "盘点任务不存在（id=" + id + "）");
        }
        return task;
    }

    /** 位置范围子树 id 集（含自身）；位置不存在抛 400 */
    private List<Long> subtreeLocationIds(Long locationId) {
        Location location = locationMapper.selectById(locationId);
        if (location == null || location.getPath() == null) {
            throw new BusinessException(400, "盘点范围位置不存在（id=" + locationId + "）");
        }
        return locationMapper.selectByParentPath(location.getPath()).stream()
                .map(Location::getId)
                .collect(Collectors.toList());
    }

    /** 批量回填明细行的资产编码/名称/序列号与位置名称，返回同一列表 */
    private List<StocktakeItem> fillItems(List<StocktakeItem> items) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        Set<Long> assetIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        for (StocktakeItem item : items) {
            assetIds.add(item.getAssetId());
            if (item.getExpectedLocationId() != null) {
                locationIds.add(item.getExpectedLocationId());
            }
            if (item.getActualLocationId() != null) {
                locationIds.add(item.getActualLocationId());
            }
        }
        Map<Long, Asset> assets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assetIds).stream()
                        .collect(Collectors.toMap(Asset::getId, Function.identity()));
        Map<Long, String> locationNames = locationNamesOf(locationIds);
        for (StocktakeItem item : items) {
            Asset asset = assets.get(item.getAssetId());
            if (asset != null) {
                item.setAssetBarcode(asset.getBarcode());
                item.setAssetName(asset.getName());
                item.setAssetSn(asset.getSn());
            }
            item.setExpectedLocationName(item.getExpectedLocationId() == null
                    ? null : locationNames.get(item.getExpectedLocationId()));
            item.setActualLocationName(item.getActualLocationId() == null
                    ? null : locationNames.get(item.getActualLocationId()));
        }
        return items;
    }

    /** 批量回填盘点任务的范围名称（位置/分类） */
    private void fillRangeNames(List<Stocktake> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<Long> locationIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (Stocktake task : tasks) {
            if (task.getLocationId() != null) {
                locationIds.add(task.getLocationId());
            }
            if (task.getCategoryId() != null) {
                categoryIds.add(task.getCategoryId());
            }
        }
        Map<Long, String> locationNames = locationNamesOf(locationIds);
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        for (Stocktake task : tasks) {
            // 位置/分类范围为空（全库/全类）时 name 置空；Map.of() 不可变 map 不允许 null key 查询
            task.setLocationName(task.getLocationId() == null
                    ? null : locationNames.get(task.getLocationId()));
            task.setCategoryName(task.getCategoryId() == null
                    ? null : categoryNames.get(task.getCategoryId()));
        }
    }

    /** 批量统计各任务明细状态计数（SQL groupBy，避免大任务全量拉明细） */
    private void fillCounts(List<Stocktake> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        List<Long> taskIds = tasks.stream().map(Stocktake::getId).toList();
        QueryWrapper<StocktakeItem> wrapper = new QueryWrapper<>();
        wrapper.select("stocktake_id AS stocktakeId", "status", "COUNT(*) AS cnt")
                .in("stocktake_id", taskIds)
                .groupBy("stocktake_id", "status");
        List<Map<String, Object>> rows = itemMapper.selectMaps(wrapper);

        Map<Long, Map<StocktakeItemStatus, Long>> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long taskId = ((Number) row.get("stocktakeId")).longValue();
            StocktakeItemStatus status = StocktakeItemStatus.of(String.valueOf(row.get("status")));
            long cnt = ((Number) row.get("cnt")).longValue();
            counts.computeIfAbsent(taskId, k -> new EnumMap<>(StocktakeItemStatus.class))
                    .put(status, cnt);
        }
        for (Stocktake task : tasks) {
            Map<StocktakeItemStatus, Long> c = counts.getOrDefault(task.getId(), Map.of());
            task.setTotalCount(c.values().stream().mapToLong(Long::longValue).sum());
            task.setPendingCount(c.getOrDefault(StocktakeItemStatus.PENDING, 0L));
            task.setMatchedCount(c.getOrDefault(StocktakeItemStatus.MATCHED, 0L));
            task.setMismatchCount(c.getOrDefault(StocktakeItemStatus.LOCATION_MISMATCH, 0L));
            task.setNotFoundCount(c.getOrDefault(StocktakeItemStatus.NOT_FOUND, 0L));
            task.setExtraCount(c.getOrDefault(StocktakeItemStatus.EXTRA, 0L));
        }
    }

    private Map<Long, String> locationNamesOf(Set<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        return locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Location::getName, (a, b) -> a));
    }

    private long countOf(Map<StocktakeItemStatus, List<StocktakeItem>> byStatus,
                         StocktakeItemStatus status) {
        return byStatus.getOrDefault(status, List.of()).size();
    }
}
