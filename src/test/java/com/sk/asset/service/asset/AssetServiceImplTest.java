package com.sk.asset.service.asset;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.mapper.asset.AssetLogMapper;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.AssetModelMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
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
import java.util.Map;

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
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetAllocation.class);
    }

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private AssetLogMapper assetLogMapper;

    @Mock
    private AssetAllocationMapper allocationMapper;

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

    @Mock
    private com.sk.asset.auth.UserDirectory userDirectory;

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
        asset.setBarcode("USER-INPUT"); // 客户端传入编码应被忽略，由服务端生成
        asset.setName("镀膜机");
        asset.setStatus(AssetStatus.IN_USE.name()); // 客户端传入的状态应被强制覆盖
        asset.setCategoryId(1L);

        Category coating = new Category();
        coating.setId(1L);
        coating.setBarcodePrefix("SKSCDM");
        when(categoryMapper.selectById(1L)).thenReturn(coating);
        when(assetMapper.selectOne(any())).thenReturn(null); // 当日该前缀无既有编码，序号从 0001 起
        doAnswer(invocation -> {
            ((Asset) invocation.getArgument(0)).setId(1L);
            return 1;
        }).when(assetMapper).insert(any(Asset.class));

        // When
        assetService.save(asset, 100L);

        // Then
        assertEquals(AssetStatus.IDLE.name(), asset.getStatus());
        assertTrue(asset.getBarcode().matches("SKSCDM-\\d{8}-0001"), "编码应为 分类前缀-日期-序号，实际：" + asset.getBarcode());
        ArgumentCaptor<AssetLog> logCaptor = ArgumentCaptor.forClass(AssetLog.class);
        verify(assetLogMapper, times(1)).insert(logCaptor.capture());
        AssetLog log = logCaptor.getValue();
        assertEquals(1L, log.getAssetId());
        assertEquals("新增", log.getOperationType());
        assertEquals(100L, log.getOperatorUserId());
        assertTrue(log.getContent().contains("镀膜机"));
        assertTrue(log.getContent().contains(asset.getBarcode()));
    }

    @Test
    void save_shouldContinueSequenceFromLatest() {
        Asset asset = new Asset();
        asset.setName("镀膜机");
        asset.setCategoryId(1L);

        Category coating = new Category();
        coating.setId(1L);
        coating.setBarcodePrefix("SKSCDM");
        when(categoryMapper.selectById(1L)).thenReturn(coating);
        when(assetMapper.selectOne(any())).thenReturn(idleAsset(9L, "SKSCDM-20200101-0005"));
        doAnswer(invocation -> {
            ((Asset) invocation.getArgument(0)).setId(2L);
            return 1;
        }).when(assetMapper).insert(any(Asset.class));

        assetService.save(asset, 100L);

        assertTrue(asset.getBarcode().endsWith("-0006"), "序号应在当日最大值上递增，实际：" + asset.getBarcode());
    }

    @Test
    void save_shouldFallbackToSkPrefixWhenCategoryPrefixMissing() {
        Asset asset = new Asset();
        asset.setName("镀膜机");
        asset.setCategoryId(2L); // 分类存在但未配置 barcode_prefix

        Category noPrefix = new Category();
        noPrefix.setId(2L);
        when(categoryMapper.selectById(2L)).thenReturn(noPrefix);
        when(assetMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ((Asset) invocation.getArgument(0)).setId(3L);
            return 1;
        }).when(assetMapper).insert(any(Asset.class));

        assetService.save(asset, 100L);

        assertTrue(asset.getBarcode().matches("SK-\\d{8}-0001"), "无前缀配置应回退 SK，实际：" + asset.getBarcode());
    }

    @Test
    void save_shouldFallbackToSkPrefixWhenCategoryAbsent() {
        Asset asset = new Asset(); // 未选分类
        asset.setName("镀膜机");

        when(assetMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ((Asset) invocation.getArgument(0)).setId(4L);
            return 1;
        }).when(assetMapper).insert(any(Asset.class));

        assetService.save(asset, 100L);

        assertTrue(asset.getBarcode().matches("SK-\\d{8}-0001"), "未选分类应回退 SK，实际：" + asset.getBarcode());
    }

    @Test
    void save_shouldRejectWhenCategoryNotExists() {
        Asset asset = new Asset();
        asset.setName("镀膜机");
        asset.setCategoryId(99L);

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
        asset.setName("镀膜机");
        asset.setLocationId(99L);

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

    @Test
    void updateById_shouldKeepBarcodeWhenBlank() {
        // 编辑未传编码：跳过唯一校验，barcode 置 null（MP updateById 跳过该列，保持原编码）
        Asset existing = idleAsset(1L, "SKSCDM-0001");
        Asset update = new Asset();
        update.setId(1L);
        update.setName("镀膜机-改名");

        when(assetMapper.selectById(1L)).thenReturn(existing);
        when(assetMapper.updateById(update)).thenReturn(1);

        assetService.updateById(update);

        assertNull(update.getBarcode());
        verify(assetMapper, never()).selectCount(any());
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
        // CAS 条件更新：mock 默认返回 0 会触发 409，需显式返回 1（更新命中）
        when(assetMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assetService.changeStatus(1L, AssetStatus.IN_USE, 100L, "生产领用");

        verify(assetMapper, times(1)).update(isNull(), any(Wrapper.class));
        ArgumentCaptor<AssetLog> logCaptor = ArgumentCaptor.forClass(AssetLog.class);
        verify(assetLogMapper, times(1)).insert(logCaptor.capture());
        AssetLog log = logCaptor.getValue();
        assertEquals("领用", log.getOperationType());
        assertEquals(100L, log.getOperatorUserId());
        assertEquals("【状态】由【闲置】变更为【在用】：生产领用", log.getContent());
    }

    // ---- writeLog（M05 调拨：不改状态、只写日志） ----

    @Test
    void writeLog_shouldInsertLogWithoutTouchingAsset() {
        when(assetMapper.selectById(1L)).thenReturn(idleAsset(1L, "SKSCDM-0001"));

        assetService.writeLog(1L, "调拨", 100L, "调拨单 ATR202608210001 确认调拨");

        verify(assetMapper, never()).update(any(), any(Wrapper.class));
        ArgumentCaptor<AssetLog> logCaptor = ArgumentCaptor.forClass(AssetLog.class);
        verify(assetLogMapper, times(1)).insert(logCaptor.capture());
        AssetLog log = logCaptor.getValue();
        assertEquals(1L, log.getAssetId());
        assertEquals("调拨", log.getOperationType());
        assertEquals(100L, log.getOperatorUserId());
        assertEquals("调拨单 ATR202608210001 确认调拨", log.getContent());
    }

    @Test
    void writeLog_shouldRejectWhenAssetNotExists() {
        when(assetMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assetService.writeLog(99L, "调拨", 100L, "内容"));
        assertEquals(404, ex.getCode());
        verify(assetLogMapper, never()).insert(any(AssetLog.class));
    }

    @Test
    void discard_shouldWriteScrapLogWithReason() {
        when(assetMapper.selectById(1L)).thenReturn(idleAsset(1L, "SKSCDM-0001"));
        when(assetMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

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
        when(allocationMapper.selectList(any())).thenReturn(List.of()); // 无持有中记录
        when(assetMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assetService.discard(1L, null, 100L);

        verify(assetMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void discard_shouldCloseActiveAllocationsWhenInUse() {
        // 在用报废：持有关系必须联动闭环（回归 SFBGIT2528 悬死 bug），持有人清空
        Asset inUse = idleAsset(1L, "SKSCDM-0001");
        inUse.setStatus(AssetStatus.IN_USE.name());
        inUse.setUserId(100L);
        when(assetMapper.selectById(1L)).thenReturn(inUse);
        when(assetMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        AssetAllocation active = new AssetAllocation();
        active.setId(12L);
        active.setAssetId(1L);
        active.setUserId(100L);
        active.setUserName("张三");
        when(allocationMapper.selectList(any())).thenReturn(List.of(active));

        assetService.discard(1L, "设备老化", 100L);

        // allocation 闭环：SET 子句含 returned_at
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaUpdateWrapper<AssetAllocation>> wrapperCaptor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(allocationMapper, times(1)).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSet().contains("returned_at"),
                "报废应闭环持有关系（returned_at），实际 SET：" + wrapperCaptor.getValue().getSqlSet());
        // 资产两次 update：状态置 DISCARD + 清空持有人
        verify(assetMapper, times(2)).update(isNull(), any(Wrapper.class));
        // 报废日志追加持有关系终结说明与原持有人
        ArgumentCaptor<AssetLog> logCaptor = ArgumentCaptor.forClass(AssetLog.class);
        verify(assetLogMapper, times(1)).insert(logCaptor.capture());
        assertTrue(logCaptor.getValue().getContent().contains("持有关系随报废终结"));
        assertTrue(logCaptor.getValue().getContent().contains("张三"));
    }

    @Test
    void discard_shouldSkipAllocationClosureWhenIdle() {
        // 闲置报废（无持有关系）：不触碰 allocation，也无持有人清空的额外 update
        when(assetMapper.selectById(1L)).thenReturn(idleAsset(1L, "SKSCDM-0001"));
        when(allocationMapper.selectList(any())).thenReturn(List.of());
        when(assetMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assetService.discard(1L, "设备老化", 100L);

        verify(allocationMapper, never()).update(any(), any());
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
    void page_shouldFillUserNamesFromUserDirectory() {
        Asset a1 = idleAsset(1L, "SKSCDM-0001");
        a1.setUserId(100L);
        a1.setAdminUserId(200L);
        // 200 已被删除/未命中 → adminUserName 置 null（前端兜底 —）
        Asset a2 = idleAsset(2L, "SKSCDM-0002");
        a2.setUserId(100L);

        Page<Asset> pageResult = new Page<>(1, 20);
        pageResult.setRecords(List.of(a1, a2));
        pageResult.setTotal(2);
        when(assetMapper.selectPage(any(Page.class), any())).thenReturn(pageResult);

        Map<Long, UserResp> users = Map.of(
                100L, new UserResp(100L, "SK9802", "张三", "生产部"));
        when(userDirectory.namesByIds(anyCollection())).thenReturn(users);

        IPage<Asset> result = assetService.page(1, 20, null);

        assertEquals("张三", result.getRecords().get(0).getUserName());
        assertNull(result.getRecords().get(0).getAdminUserName());
        assertEquals("张三", result.getRecords().get(1).getUserName());
    }

    @Test
    void getById_shouldFillUserNamesAfterJoinQuery() {
        Asset asset = idleAsset(1L, "SKSCDM-0001");
        asset.setUserId(100L);
        when(assetMapper.selectByIdWithRelations(1L)).thenReturn(asset);
        when(userDirectory.namesByIds(anyCollection()))
                .thenReturn(Map.of(100L, new UserResp(100L, "SK9802", "张三", "生产部")));

        Asset result = assetService.getById(1L);

        assertEquals("张三", result.getUserName());
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

    // ---- keyword 多关键词解析（空格=或，"-"=且） ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<Asset> pageWithKeyword(String keyword) {
        com.sk.asset.dto.asset.AssetQuery query = new com.sk.asset.dto.asset.AssetQuery();
        query.setKeyword(keyword);
        when(assetMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 20));
        assetService.page(1, 20, query);
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(assetMapper).selectPage(any(Page.class), captor.capture());
        return (LambdaQueryWrapper<Asset>) captor.getValue();
    }

    private List<String> likeValues(LambdaQueryWrapper<Asset> wrapper) {
        return wrapper.getParamNameValuePairs().values().stream()
                .map(String::valueOf)
                .toList();
    }

    @Test
    void keyword_singleTerm_shouldKeepThreeFieldLike() {
        LambdaQueryWrapper<Asset> wrapper = pageWithKeyword("笔记本");

        // 单关键词：三字段各一次 LIKE，三个参数均为同一词（与旧实现行为一致）
        assertEquals(3, countOccurrences(wrapper.getSqlSegment(), "LIKE"));
        assertEquals(3, likeValues(wrapper).size());
        assertTrue(likeValues(wrapper).stream().allMatch(v -> v.contains("笔记本")));
    }

    @Test
    void keyword_spaceSeparated_shouldOrGroups() {
        LambdaQueryWrapper<Asset> wrapper = pageWithKeyword("笔记本 台式机");

        // 两组各 3 次 LIKE，组间 OR：6 次 LIKE + 5 个 OR（组内 2×2 + 组间 1）
        assertEquals(6, countOccurrences(wrapper.getSqlSegment(), "LIKE"));
        assertEquals(5, countOccurrences(wrapper.getSqlSegment().toUpperCase(), " OR "));
        List<String> values = likeValues(wrapper);
        assertTrue(values.stream().anyMatch(v -> v.contains("笔记本")));
        assertTrue(values.stream().anyMatch(v -> v.contains("台式机")));
    }

    @Test
    void keyword_dashSeparated_shouldAndTerms() {
        LambdaQueryWrapper<Asset> wrapper = pageWithKeyword("笔记本-Lenovo");

        // 同组两个子词：6 次 LIKE，组内 AND 连接两个子词条件
        assertEquals(6, countOccurrences(wrapper.getSqlSegment(), "LIKE"));
        assertTrue(countOccurrences(wrapper.getSqlSegment().toUpperCase(), " AND ") >= 1);
        List<String> values = likeValues(wrapper);
        assertTrue(values.stream().anyMatch(v -> v.contains("笔记本")));
        assertTrue(values.stream().anyMatch(v -> v.contains("Lenovo")));
    }

    @Test
    void keyword_mixed_shouldCombineOrAndAnd() {
        LambdaQueryWrapper<Asset> wrapper = pageWithKeyword("台式机 笔记本-Lenovo");

        // 第一组单词（3 LIKE），第二组两子词（6 LIKE），组间 OR
        assertEquals(9, countOccurrences(wrapper.getSqlSegment(), "LIKE"));
        List<String> values = likeValues(wrapper);
        assertTrue(values.stream().anyMatch(v -> v.contains("台式机")));
        assertTrue(values.stream().anyMatch(v -> v.contains("笔记本")));
        assertTrue(values.stream().anyMatch(v -> v.contains("Lenovo")));
    }

    @Test
    void keyword_barcodeWithDash_shouldSplitIntoAndTerms() {
        LambdaQueryWrapper<Asset> wrapper = pageWithKeyword("SKBGDN-0001");

        // 编码自带 "-"：切分为 AND 子词后仍能命中同一资产（SKBGDN 且 0001）
        assertEquals(6, countOccurrences(wrapper.getSqlSegment(), "LIKE"));
        List<String> values = likeValues(wrapper);
        assertTrue(values.stream().anyMatch(v -> v.contains("SKBGDN")));
        assertTrue(values.stream().anyMatch(v -> v.contains("0001")));
    }

    @Test
    void keyword_separatorOnly_shouldBehaveAsNoKeyword() {
        LambdaQueryWrapper<Asset> wrapper = pageWithKeyword(" -- ");

        // 纯分隔符输入：解析为空，不生成任何关键词条件
        assertEquals(0, countOccurrences(wrapper.getSqlSegment(), "LIKE"));
    }

    @Test
    void keyword_fullWidthSpace_shouldAlsoSplitGroups() {
        LambdaQueryWrapper<Asset> wrapper = pageWithKeyword("笔记本　台式机");

        assertEquals(6, countOccurrences(wrapper.getSqlSegment(), "LIKE"));
        List<String> values = likeValues(wrapper);
        assertTrue(values.stream().anyMatch(v -> v.contains("笔记本")));
        assertTrue(values.stream().anyMatch(v -> v.contains("台式机")));
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
