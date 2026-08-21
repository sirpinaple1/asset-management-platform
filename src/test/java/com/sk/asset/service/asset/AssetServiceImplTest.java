package com.sk.asset.service.asset;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.mapper.asset.AssetLogMapper;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.AssetModelMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.service.asset.impl.AssetServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaUpdateWrapper.set 需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Asset.class);
    }

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private AssetLogMapper assetLogMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private AssetModelMapper assetModelMapper;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private CompanyMapper companyMapper;

    @InjectMocks
    private AssetServiceImpl assetService;

    private Asset idleAsset(Long id, String barcode) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setBarcode(barcode);
        asset.setName("测试资产");
        asset.setStatus(AssetStatus.IDLE.name());
        return asset;
    }

    // ---- save ----

    @Test
    void save_shouldInsertWithIdleStatusAndWriteCreateLog() {
        // Given
        Asset asset = new Asset();
        asset.setBarcode("SKSCDM-0001");
        asset.setName("镀膜机");
        asset.setStatus(AssetStatus.IN_USE.name()); // 客户端传入的状态应被强制覆盖

        when(assetMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            ((Asset) invocation.getArgument(0)).setId(1L);
            return 1;
        }).when(assetMapper).insert(any(Asset.class));

        // When
        assetService.save(asset, 100L);

        // Then
        assertEquals(AssetStatus.IDLE.name(), asset.getStatus());
        ArgumentCaptor<AssetLog> logCaptor = ArgumentCaptor.forClass(AssetLog.class);
        verify(assetLogMapper, times(1)).insert(logCaptor.capture());
        AssetLog log = logCaptor.getValue();
        assertEquals(1L, log.getAssetId());
        assertEquals("新增", log.getOperationType());
        assertEquals(100L, log.getOperatorUserId());
        assertTrue(log.getContent().contains("镀膜机"));
        assertTrue(log.getContent().contains("SKSCDM-0001"));
    }

    @Test
    void save_shouldRejectDuplicateBarcode() {
        Asset asset = new Asset();
        asset.setBarcode("SKSCDM-0001");
        asset.setName("镀膜机");

        when(assetMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.save(asset, 100L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("已存在"));
        verify(assetMapper, never()).insert(any(Asset.class));
    }

    @Test
    void save_shouldRejectWhenCategoryNotExists() {
        Asset asset = new Asset();
        asset.setBarcode("SKSCDM-0001");
        asset.setName("镀膜机");
        asset.setCategoryId(99L);

        when(assetMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.save(asset, 100L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("资产分类不存在"));
        verify(assetMapper, never()).insert(any(Asset.class));
    }

    @Test
    void save_shouldRejectWhenLocationNotExists() {
        Asset asset = new Asset();
        asset.setBarcode("SKSCDM-0001");
        asset.setName("镀膜机");
        asset.setLocationId(99L);

        when(assetMapper.selectCount(any())).thenReturn(0L);
        when(locationMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.save(asset, 100L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("位置不存在"));
    }

    // ---- updateById ----

    @Test
    void updateById_shouldKeepExistingStatus() {
        Asset existing = idleAsset(1L, "SKSCDM-0001");
        existing.setStatus(AssetStatus.IN_USE.name());

        Asset update = new Asset();
        update.setId(1L);
        update.setBarcode("SKSCDM-0001");
        update.setName("镀膜机-改名");
        // AssetReq 无状态字段 → update.status 为 null，service 应保留 existing 状态

        when(assetMapper.selectById(1L)).thenReturn(existing);
        when(assetMapper.selectCount(any())).thenReturn(0L);
        when(assetMapper.updateById(update)).thenReturn(1);

        assetService.updateById(update);

        assertEquals(AssetStatus.IN_USE.name(), update.getStatus());
        verify(assetMapper, times(1)).updateById(update);
    }

    @Test
    void updateById_shouldRejectWhenNotExists() {
        Asset update = new Asset();
        update.setId(99L);
        update.setBarcode("SKSCDM-0001");

        when(assetMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.updateById(update));
        assertEquals(404, ex.getCode());
    }

    @Test
    void updateById_shouldAllowBarcodeUnchangedForSameAsset() {
        // barcode 排除自身：selectCount 返回 0（无其他资产占用该编码）
        Asset existing = idleAsset(1L, "SKSCDM-0001");
        Asset update = new Asset();
        update.setId(1L);
        update.setBarcode("SKSCDM-0001");
        update.setName("镀膜机");

        when(assetMapper.selectById(1L)).thenReturn(existing);
        when(assetMapper.selectCount(any())).thenReturn(0L);
        when(assetMapper.updateById(update)).thenReturn(1);

        assetService.updateById(update);

        verify(assetMapper, times(1)).updateById(update);
    }

    // ---- changeStatus / discard ----

    @Test
    void changeStatus_shouldRejectWhenAssetNotExists() {
        when(assetMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.changeStatus(99L, AssetStatus.IN_USE, 100L, null));
        assertEquals(404, ex.getCode());
    }

    @Test
    void changeStatus_shouldRejectIllegalTransition() {
        when(assetMapper.selectById(1L)).thenReturn(idleAsset(1L, "SKSCDM-0001"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.changeStatus(1L, AssetStatus.IDLE, 100L, null));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("闲置"));
        verify(assetLogMapper, never()).insert(any(AssetLog.class));
    }

    @Test
    void changeStatus_shouldRejectWhenDiscarded() {
        Asset discarded = idleAsset(1L, "SKSCDM-0001");
        discarded.setStatus(AssetStatus.DISCARD.name());
        when(assetMapper.selectById(1L)).thenReturn(discarded);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.changeStatus(1L, AssetStatus.IDLE, 100L, null));
        assertEquals(409, ex.getCode());
    }

    @Test
    void changeStatus_shouldUpdateAndWriteLog() {
        when(assetMapper.selectById(1L)).thenReturn(idleAsset(1L, "SKSCDM-0001"));

        assetService.changeStatus(1L, AssetStatus.IN_USE, 100L, "生产领用");

        verify(assetMapper, times(1)).update(isNull(), any(Wrapper.class));
        ArgumentCaptor<AssetLog> logCaptor = ArgumentCaptor.forClass(AssetLog.class);
        verify(assetLogMapper, times(1)).insert(logCaptor.capture());
        AssetLog log = logCaptor.getValue();
        assertEquals("领用", log.getOperationType());
        assertEquals(100L, log.getOperatorUserId());
        assertEquals("【状态】由【闲置】变更为【在用】：生产领用", log.getContent());
    }

    @Test
    void discard_shouldWriteScrapLogWithReason() {
        when(assetMapper.selectById(1L)).thenReturn(idleAsset(1L, "SKSCDM-0001"));

        assetService.discard(1L, "设备老化", 100L);

        ArgumentCaptor<AssetLog> logCaptor = ArgumentCaptor.forClass(AssetLog.class);
        verify(assetLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("报废", logCaptor.getValue().getOperationType());
        assertTrue(logCaptor.getValue().getContent().contains("设备老化"));
    }

    @Test
    void discard_shouldAllowFromInUse() {
        Asset inUse = idleAsset(1L, "SKSCDM-0001");
        inUse.setStatus(AssetStatus.IN_USE.name());
        when(assetMapper.selectById(1L)).thenReturn(inUse);

        assetService.discard(1L, null, 100L);

        verify(assetMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    // ---- page / fillRelations ----

    @Test
    void page_shouldFillRelationNames() {
        Asset a1 = idleAsset(1L, "SKSCDM-0001");
        a1.setCategoryId(1L);
        a1.setModelId(2L);
        a1.setSupplierId(3L);
        a1.setLocationId(4L);
        a1.setHomeLocationId(5L);
        a1.setCompanyId(6L);

        Page<Asset> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(a1));
        pageResult.setTotal(1);

        when(assetMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        Category category = new Category();
        category.setId(1L);
        category.setName("IT设备");
        when(categoryMapper.selectBatchIds(anyCollection())).thenReturn(List.of(category));

        AssetModel model = new AssetModel();
        model.setId(2L);
        model.setName("MacBook Pro");
        when(assetModelMapper.selectBatchIds(anyCollection())).thenReturn(List.of(model));

        Supplier supplier = new Supplier();
        supplier.setId(3L);
        supplier.setName("供应商A");
        when(supplierMapper.selectBatchIds(anyCollection())).thenReturn(List.of(supplier));

        Location location = new Location();
        location.setId(4L);
        location.setName("一楼车间");
        Location homeLocation = new Location();
        homeLocation.setId(5L);
        homeLocation.setName("物料仓");
        when(locationMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(location, homeLocation));

        Company company = new Company();
        company.setId(6L);
        company.setName("森科五金");
        when(companyMapper.selectBatchIds(anyCollection())).thenReturn(List.of(company));

        IPage<Asset> result = assetService.page(1, 20, null);

        Asset filled = result.getRecords().get(0);
        assertEquals("IT设备", filled.getCategoryName());
        assertEquals("MacBook Pro", filled.getModelName());
        assertEquals("供应商A", filled.getSupplierName());
        assertEquals("一楼车间", filled.getLocationName());
        assertEquals("物料仓", filled.getHomeLocationName());
        assertEquals("森科五金", filled.getCompanyName());
    }

    @Test
    void listLogs_shouldReturnLogs() {
        AssetLog log = new AssetLog();
        log.setAssetId(1L);
        log.setOperationType("新增");
        log.setContent("新增资产");

        when(assetLogMapper.selectList(any())).thenReturn(List.of(log));

        List<AssetLog> result = assetService.listLogs(1L);

        assertEquals(1, result.size());
        assertEquals("新增", result.get(0).getOperationType());
    }
}
