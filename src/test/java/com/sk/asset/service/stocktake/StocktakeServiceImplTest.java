package com.sk.asset.service.stocktake;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.stocktake.StocktakeBarcodeScanReq;
import com.sk.asset.dto.stocktake.StocktakeCreateReq;
import com.sk.asset.dto.stocktake.StocktakeReportResp;
import com.sk.asset.dto.stocktake.StocktakeScanReq;
import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.entity.asset.Asset;
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
import com.sk.asset.service.stocktake.impl.StocktakeServiceImpl;
import com.sk.asset.service.transfer.TransferOrderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StocktakeServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Asset.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Location.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Stocktake.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StocktakeItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrder.class);
    }

    @Mock
    private StocktakeMapper stocktakeMapper;

    @Mock
    private StocktakeItemMapper itemMapper;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private TransferOrderMapper transferOrderMapper;

    @Mock
    private AssetService assetService;

    @Mock
    private TransferOrderService transferOrderService;

    @InjectMocks
    private StocktakeServiceImpl stocktakeService;

    // ---- 辅助构造 ----

    private Stocktake task(Long id, StocktakeStatus status) {
        Stocktake task = new Stocktake();
        task.setId(id);
        task.setName("2026年中盘点");
        task.setStatus(status.name());
        task.setCreatorUserId(100L);
        task.setCreatorName("测试用户");
        return task;
    }

    private StocktakeItem item(Long id, Long taskId, Long assetId, StocktakeItemStatus status,
                               Long expected, Long actual) {
        StocktakeItem item = new StocktakeItem();
        item.setId(id);
        item.setStocktakeId(taskId);
        item.setAssetId(assetId);
        item.setStatus(status.name());
        item.setExpectedLocationId(expected);
        item.setActualLocationId(actual);
        return item;
    }

    private Asset asset(Long id, String barcode, AssetStatus status, Long locationId) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setBarcode(barcode);
        asset.setName("资产" + id);
        asset.setStatus(status.name());
        asset.setLocationId(locationId);
        asset.setCompanyId(3L);
        return asset;
    }

    private Location location(Long id, String path) {
        Location location = new Location();
        location.setId(id);
        location.setName("位置" + id);
        location.setPath(path);
        return location;
    }

    /** mock getById 链（主表 + 明细 + 统计，统计返回空） */
    private void mockDetail(Stocktake task, List<StocktakeItem> items) {
        when(stocktakeMapper.selectById(task.getId())).thenReturn(task);
        when(itemMapper.selectList(any())).thenReturn(items);
        when(itemMapper.selectMaps(any())).thenReturn(List.<Map<String, Object>>of());
        if (!items.isEmpty()) {
            when(assetMapper.selectBatchIds(any())).thenReturn(List.of());
            when(locationMapper.selectBatchIds(any())).thenReturn(List.of());
        }
    }

    // ---- create ----

    @Test
    void create_shouldSnapshotAssetsInScopeWithExpectedLocation() {
        StocktakeCreateReq req = new StocktakeCreateReq();
        req.setName(" 2026年中盘点 ");
        req.setLocationId(10L);

        when(locationMapper.selectById(10L)).thenReturn(location(10L, "/10/"));
        when(locationMapper.selectByParentPath("/10/")).thenReturn(
                List.of(location(10L, "/10/"), location(11L, "/10/11/")));
        when(assetMapper.selectList(any())).thenReturn(List.of(
                asset(101L, "SKSCDM-0001", AssetStatus.IDLE, 10L),
                asset(102L, "SKSCDM-0002", AssetStatus.IN_USE, 11L)));
        when(stocktakeMapper.insert(any(Stocktake.class))).thenAnswer(inv -> {
            inv.getArgument(0, Stocktake.class).setId(1L);
            return 1;
        });
        mockDetail(task(1L, StocktakeStatus.PENDING), List.of());

        Stocktake created = stocktakeService.create(req, 100L, "测试用户");

        ArgumentCaptor<Stocktake> taskCaptor = ArgumentCaptor.forClass(Stocktake.class);
        verify(stocktakeMapper).insert(taskCaptor.capture());
        assertEquals("2026年中盘点", taskCaptor.getValue().getName());
        assertEquals(StocktakeStatus.PENDING.name(), taskCaptor.getValue().getStatus());
        assertEquals(10L, taskCaptor.getValue().getLocationId());
        assertEquals("测试用户", taskCaptor.getValue().getCreatorName());
        assertEquals(3L, taskCaptor.getValue().getCompanyId());

        ArgumentCaptor<StocktakeItem> itemCaptor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper, times(2)).insert(itemCaptor.capture());
        List<StocktakeItem> items = itemCaptor.getAllValues();
        assertEquals(101L, items.get(0).getAssetId());
        assertEquals(10L, items.get(0).getExpectedLocationId());
        assertEquals(StocktakeItemStatus.PENDING.name(), items.get(0).getStatus());
        assertEquals(1L, items.get(0).getStocktakeId());
        assertEquals(102L, items.get(1).getAssetId());
        assertEquals(11L, items.get(1).getExpectedLocationId());

        assertEquals(1L, created.getId());
    }

    @Test
    void create_shouldRejectWhenScopeEmpty() {
        StocktakeCreateReq req = new StocktakeCreateReq();
        req.setName("空范围盘点");
        when(assetMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.create(req, 100L, "测试用户"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("无待盘资产"));
    }

    @Test
    void create_shouldRejectWhenScopeLocationNotExist() {
        StocktakeCreateReq req = new StocktakeCreateReq();
        req.setName("盘点");
        req.setLocationId(99L);
        when(locationMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.create(req, 100L, "测试用户"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("位置不存在"));
    }

    @Test
    void create_shouldRejectWhenScopeCategoryNotExist() {
        StocktakeCreateReq req = new StocktakeCreateReq();
        req.setName("盘点");
        req.setCategoryId(88L);
        when(categoryMapper.selectById(88L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.create(req, 100L, "测试用户"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("分类不存在"));
    }

    // ---- start / cancel ----

    @Test
    void start_shouldTransitionToInProgress() {
        Stocktake pending = task(1L, StocktakeStatus.PENDING);
        mockDetail(pending, List.of());

        Stocktake started = stocktakeService.start(1L, 100L);

        ArgumentCaptor<Stocktake> captor = ArgumentCaptor.forClass(Stocktake.class);
        verify(stocktakeMapper).updateById((Stocktake) captor.capture());
        assertEquals(StocktakeStatus.IN_PROGRESS.name(), captor.getValue().getStatus());
        assertNotNull(captor.getValue().getStartTime());
        assertEquals(StocktakeStatus.IN_PROGRESS.name(), started.getStatus());
    }

    @Test
    void start_shouldRejectWhenNotPending() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.COMPLETED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.start(1L, 100L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("不能重复开始"));
    }

    @Test
    void cancel_shouldTransitionToCancelled() {
        Stocktake inProgress = task(1L, StocktakeStatus.IN_PROGRESS);
        mockDetail(inProgress, List.of());

        Stocktake cancelled = stocktakeService.cancel(1L, 100L);

        ArgumentCaptor<Stocktake> captor = ArgumentCaptor.forClass(Stocktake.class);
        verify(stocktakeMapper).updateById((Stocktake) captor.capture());
        assertEquals(StocktakeStatus.CANCELLED.name(), captor.getValue().getStatus());
        assertEquals(StocktakeStatus.CANCELLED.name(), cancelled.getStatus());
    }

    @Test
    void cancel_shouldRejectWhenNotCreator() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.PENDING));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.cancel(1L, 999L));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("仅创建人"));
    }

    @Test
    void cancel_shouldRejectWhenCompleted() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.COMPLETED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.cancel(1L, 100L));
        assertEquals(409, ex.getCode());
    }

    // ---- scanItem ----

    @Test
    void scanItem_shouldMatchWhenActualEqualsExpected() {
        Stocktake inProgress = task(1L, StocktakeStatus.IN_PROGRESS);
        StocktakeItem pendingItem = item(5L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null);
        when(stocktakeMapper.selectById(1L)).thenReturn(inProgress);
        when(itemMapper.selectById(5L)).thenReturn(pendingItem);
        when(locationMapper.selectById(10L)).thenReturn(location(10L, "/10/"));
        when(itemMapper.updateById(any(StocktakeItem.class))).thenReturn(1);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(10L);

        StocktakeItem result = stocktakeService.scanItem(1L, 5L, req, 200L);

        ArgumentCaptor<StocktakeItem> captor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper).updateById((StocktakeItem) captor.capture());
        assertEquals(StocktakeItemStatus.MATCHED.name(), captor.getValue().getStatus());
        assertEquals(10L, captor.getValue().getActualLocationId());
        assertEquals(200L, captor.getValue().getScannedByUserId());
        assertNotNull(captor.getValue().getScannedAt());
        assertEquals(StocktakeItemStatus.MATCHED.name(), result.getStatus());
    }

    @Test
    void scanItem_shouldMismatchWhenActualDiffers() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(itemMapper.selectById(5L)).thenReturn(item(5L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null));
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "/20/"));
        when(itemMapper.updateById(any(StocktakeItem.class))).thenReturn(1);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(20L);

        StocktakeItem result = stocktakeService.scanItem(1L, 5L, req, 200L);

        ArgumentCaptor<StocktakeItem> captor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper).updateById((StocktakeItem) captor.capture());
        assertEquals(StocktakeItemStatus.LOCATION_MISMATCH.name(), captor.getValue().getStatus());
        assertEquals(20L, captor.getValue().getActualLocationId());
        assertEquals(StocktakeItemStatus.LOCATION_MISMATCH.name(), result.getStatus());
    }

    @Test
    void scanItem_shouldMarkNotFound() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(itemMapper.selectById(5L)).thenReturn(item(5L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null));
        when(itemMapper.updateById(any(StocktakeItem.class))).thenReturn(1);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        StocktakeScanReq req = new StocktakeScanReq();
        req.setNotFound(true);

        StocktakeItem result = stocktakeService.scanItem(1L, 5L, req, 200L);

        ArgumentCaptor<StocktakeItem> captor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper).updateById((StocktakeItem) captor.capture());
        assertEquals(StocktakeItemStatus.NOT_FOUND.name(), captor.getValue().getStatus());
        assertNull(captor.getValue().getActualLocationId());
        assertEquals(StocktakeItemStatus.NOT_FOUND.name(), result.getStatus());
    }

    @Test
    void scanItem_shouldRejectWhenTaskNotInProgress() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.PENDING));

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.scanItem(1L, 5L, req, 200L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("请先开始盘点"));
    }

    @Test
    void scanItem_shouldRejectWhenAlreadyScanned() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(itemMapper.selectById(5L)).thenReturn(item(5L, 1L, 101L, StocktakeItemStatus.MATCHED, 10L, 10L));

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.scanItem(1L, 5L, req, 200L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("不能重复盘点"));
    }

    @Test
    void scanItem_shouldRejectWhenActualLocationMissingAndNotMarkedNotFound() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(itemMapper.selectById(5L)).thenReturn(item(5L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null));

        StocktakeScanReq req = new StocktakeScanReq();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.scanItem(1L, 5L, req, 200L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("请提交实际位置"));
    }

    @Test
    void scanItem_shouldRejectWhenActualLocationNotExist() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(itemMapper.selectById(5L)).thenReturn(item(5L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null));
        when(locationMapper.selectById(99L)).thenReturn(null);

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(99L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.scanItem(1L, 5L, req, 200L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("实际位置不存在"));
    }

    @Test
    void scanItem_shouldRejectWhenItemBelongsToOtherTask() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(itemMapper.selectById(5L)).thenReturn(item(5L, 2L, 101L, StocktakeItemStatus.PENDING, 10L, null));

        StocktakeScanReq req = new StocktakeScanReq();
        req.setActualLocationId(10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.scanItem(1L, 5L, req, 200L));
        assertEquals(404, ex.getCode());
    }

    // ---- scanByBarcode ----

    @Test
    void scanByBarcode_shouldUpdateExistingItemAsMatchedWhenNoActualLocation() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(assetMapper.selectOne(any())).thenReturn(asset(101L, "SKSCDM-0001", AssetStatus.IDLE, 10L));
        when(itemMapper.selectOne(any())).thenReturn(item(5L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null));
        when(itemMapper.updateById(any(StocktakeItem.class))).thenReturn(1);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        StocktakeBarcodeScanReq req = new StocktakeBarcodeScanReq();
        req.setBarcode("SKSCDM-0001");

        StocktakeItem result = stocktakeService.scanByBarcode(1L, req, 200L);

        ArgumentCaptor<StocktakeItem> captor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper).updateById((StocktakeItem) captor.capture());
        assertEquals(StocktakeItemStatus.MATCHED.name(), captor.getValue().getStatus());
        assertEquals(10L, captor.getValue().getActualLocationId());
        assertEquals(StocktakeItemStatus.MATCHED.name(), result.getStatus());
    }

    @Test
    void scanByBarcode_shouldCreateExtraItemForOutOfScopeAsset() {
        Stocktake inProgress = task(1L, StocktakeStatus.IN_PROGRESS);
        inProgress.setLocationId(10L);
        when(stocktakeMapper.selectById(1L)).thenReturn(inProgress);
        // 资产账面在 30（任务范围外）→ 盘盈
        when(assetMapper.selectOne(any())).thenReturn(asset(105L, "SKSCDM-0005", AssetStatus.IDLE, 30L));
        when(itemMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.insert(any(StocktakeItem.class))).thenReturn(1);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        StocktakeBarcodeScanReq req = new StocktakeBarcodeScanReq();
        req.setBarcode("SKSCDM-0005");

        StocktakeItem result = stocktakeService.scanByBarcode(1L, req, 200L);

        ArgumentCaptor<StocktakeItem> captor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper).insert(captor.capture());
        assertEquals(StocktakeItemStatus.EXTRA.name(), captor.getValue().getStatus());
        assertEquals(105L, captor.getValue().getAssetId());
        assertEquals(30L, captor.getValue().getExpectedLocationId());
        assertEquals(10L, captor.getValue().getActualLocationId());
        assertEquals(200L, captor.getValue().getScannedByUserId());
        assertEquals(StocktakeItemStatus.EXTRA.name(), result.getStatus());
    }

    @Test
    void scanByBarcode_shouldRejectUnknownBarcode() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(assetMapper.selectOne(any())).thenReturn(null);

        StocktakeBarcodeScanReq req = new StocktakeBarcodeScanReq();
        req.setBarcode("UNKNOWN-001");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.scanByBarcode(1L, req, 200L));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("未登记"));
    }

    @Test
    void scanByBarcode_shouldRejectDiscardedAsset() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(assetMapper.selectOne(any())).thenReturn(asset(101L, "SKSCDM-0001", AssetStatus.DISCARD, 10L));

        StocktakeBarcodeScanReq req = new StocktakeBarcodeScanReq();
        req.setBarcode("SKSCDM-0001");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.scanByBarcode(1L, req, 200L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("已报废"));
    }

    // ---- complete ----

    @Test
    void complete_shouldMarkRemainingPendingAsNotFoundAndWriteLogs() {
        Stocktake inProgress = task(1L, StocktakeStatus.IN_PROGRESS);
        StocktakeItem i1 = item(5L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null);
        StocktakeItem i2 = item(6L, 1L, 102L, StocktakeItemStatus.PENDING, 10L, null);
        when(stocktakeMapper.selectById(1L)).thenReturn(inProgress);
        // 调用顺序：①剩余 PENDING ②NOT_FOUND 明细（写日志）③getById 明细回填
        when(itemMapper.selectList(any()))
                .thenReturn(List.of(i1, i2), List.of(i1, i2), List.of());
        when(itemMapper.updateById(any(StocktakeItem.class))).thenReturn(1);
        when(itemMapper.selectMaps(any())).thenReturn(List.<Map<String, Object>>of());

        Stocktake completed = stocktakeService.complete(1L, 100L);

        // 剩余待盘两条均更新为盘亏
        ArgumentCaptor<StocktakeItem> itemCaptor = ArgumentCaptor.forClass(StocktakeItem.class);
        verify(itemMapper, times(2)).updateById((StocktakeItem) itemCaptor.capture());
        assertTrue(itemCaptor.getAllValues().stream()
                .allMatch(i -> StocktakeItemStatus.NOT_FOUND.name().equals(i.getStatus())));

        // 盘亏逐台写资产日志（operation_type=盘点处理）
        verify(assetService, times(2)).writeLog(any(), eq("盘点处理"), eq(100L), contains("盘亏"));

        ArgumentCaptor<Stocktake> taskCaptor = ArgumentCaptor.forClass(Stocktake.class);
        verify(stocktakeMapper).updateById(taskCaptor.capture());
        assertEquals(StocktakeStatus.COMPLETED.name(), taskCaptor.getValue().getStatus());
        assertNotNull(taskCaptor.getValue().getCompleteTime());
        assertEquals(StocktakeStatus.COMPLETED.name(), completed.getStatus());
    }

    @Test
    void complete_shouldRejectWhenNotInProgress() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.PENDING));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.complete(1L, 100L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("不能完成"));
    }

    // ---- report ----

    @Test
    void report_shouldAggregateCountsAndDiffDetails() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, 101L, StocktakeItemStatus.PENDING, 10L, null),
                item(2L, 1L, 102L, StocktakeItemStatus.MATCHED, 10L, 10L),
                item(3L, 1L, 103L, StocktakeItemStatus.LOCATION_MISMATCH, 10L, 20L),
                item(4L, 1L, 104L, StocktakeItemStatus.NOT_FOUND, 10L, null),
                item(5L, 1L, 105L, StocktakeItemStatus.EXTRA, 30L, 10L)));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        StocktakeReportResp resp = stocktakeService.report(1L);

        assertEquals(5L, resp.getTotalCount());
        assertEquals(1L, resp.getPendingCount());
        assertEquals(1L, resp.getMatchedCount());
        assertEquals(1L, resp.getMismatchCount());
        assertEquals(1L, resp.getNotFoundCount());
        assertEquals(1L, resp.getExtraCount());
        assertEquals(1, resp.getMismatches().size());
        assertEquals(103L, resp.getMismatches().get(0).getAssetId());
        assertEquals(1, resp.getNotFounds().size());
        assertEquals(1, resp.getExtras().size());
    }

    // ---- createTransfers ----

    @Test
    void createTransfers_shouldGroupMismatchesByActualLocation() {
        Stocktake completed = task(1L, StocktakeStatus.COMPLETED);
        when(stocktakeMapper.selectById(1L)).thenReturn(completed);
        when(transferOrderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(3L, 1L, 103L, StocktakeItemStatus.LOCATION_MISMATCH, 10L, 20L),
                item(4L, 1L, 104L, StocktakeItemStatus.LOCATION_MISMATCH, 10L, 20L),
                item(5L, 1L, 105L, StocktakeItemStatus.LOCATION_MISMATCH, 10L, 30L)));
        TransferOrder o1 = new TransferOrder();
        o1.setId(11L);
        o1.setSerialNo("ATR202608240001");
        TransferOrder o2 = new TransferOrder();
        o2.setId(12L);
        o2.setSerialNo("ATR202608240002");
        when(transferOrderService.create(any(), eq(100L), eq("测试用户"),
                eq(TransferSource.INVENTORY_TRIGGERED), eq(1L))).thenReturn(o1, o2);

        List<TransferOrder> created = stocktakeService.createTransfers(1L, 100L, "测试用户");

        assertEquals(2, created.size());
        ArgumentCaptor<TransferApplyReq> reqCaptor = ArgumentCaptor.forClass(TransferApplyReq.class);
        verify(transferOrderService, times(2)).create(reqCaptor.capture(), eq(100L), eq("测试用户"),
                eq(TransferSource.INVENTORY_TRIGGERED), eq(1L));

        // 第一组：实际位置 20，两台；第二组：实际位置 30，一台
        List<TransferApplyReq> reqs = reqCaptor.getAllValues();
        assertEquals(20L, reqs.get(0).getToLocationId());
        assertEquals(List.of(103L, 104L), reqs.get(0).getAssetIds());
        assertEquals(30L, reqs.get(1).getToLocationId());
        assertEquals(List.of(105L), reqs.get(1).getAssetIds());
        assertTrue(reqs.get(0).getReason().contains("盘点差异归位"));
    }

    @Test
    void createTransfers_shouldRejectWhenNotCompleted() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.IN_PROGRESS));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.createTransfers(1L, 100L, "测试用户"));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("不能生成调拨单"));
    }

    @Test
    void createTransfers_shouldRejectWhenAlreadyGenerated() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.COMPLETED));
        when(transferOrderMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.createTransfers(1L, 100L, "测试用户"));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("已生成过调拨单"));
    }

    @Test
    void createTransfers_shouldRejectWhenNoMismatch() {
        when(stocktakeMapper.selectById(1L)).thenReturn(task(1L, StocktakeStatus.COMPLETED));
        when(transferOrderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectList(any())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stocktakeService.createTransfers(1L, 100L, "测试用户"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("无位置不符明细"));
    }
}
