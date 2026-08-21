package com.sk.asset.service.receipt;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.receipt.ReceiptQuery;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.transfer.TransferOrderItem;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptItemMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderItemMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.asset.AssetService;
import com.sk.asset.service.receipt.impl.ReceiveReceiptServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiveReceiptServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper/LambdaUpdateWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Asset.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceipt.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceiptItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetAllocation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrderItem.class);
    }

    @Mock
    private ReceiveReceiptMapper receiptMapper;

    @Mock
    private ReceiveReceiptItemMapper itemMapper;

    @Mock
    private AssetAllocationMapper allocationMapper;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private TransferOrderMapper transferOrderMapper;

    @Mock
    private TransferOrderItemMapper transferOrderItemMapper;

    @Mock
    private com.sk.asset.mapper.change.ChangeOrderMapper changeOrderMapper;

    @Mock
    private com.sk.asset.mapper.change.ChangeOrderItemMapper changeOrderItemMapper;

    @Mock
    private AssetService assetService;

    @InjectMocks
    private ReceiveReceiptServiceImpl receiptService;

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private Asset idleAsset(Long id) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setBarcode("SKSCDM-000" + id);
        asset.setName("测试资产" + id);
        asset.setStatus(AssetStatus.IDLE.name());
        asset.setCompanyId(3L);
        return asset;
    }

    private ReceiptApplyReq applyReq(String type, List<Long> assetIds) {
        ReceiptApplyReq req = new ReceiptApplyReq();
        req.setType(type);
        req.setAssetIds(assetIds);
        req.setLocationId(1L);
        req.setDepartment("PMC部");
        req.setReason("产线使用");
        return req;
    }

    private Location location() {
        Location location = new Location();
        location.setId(1L);
        location.setName("一号车间");
        return location;
    }

    /** create 路径位置存在性校验 stub（仅 selectById） */
    private void stubLocationExists() {
        when(locationMapper.selectById(1L)).thenReturn(location());
    }

    /** getById/list 回填区域名称 stub（仅 selectBatchIds） */
    private void stubLocationNames() {
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location()));
    }

    private ReceiveReceipt pendingReceipt() {
        ReceiveReceipt receipt = new ReceiveReceipt();
        receipt.setId(1L);
        receipt.setSerialNo("ARE" + today() + "0001");
        receipt.setType("RECEIVE");
        receipt.setStatus("PENDING");
        receipt.setApplicantUserId(100L);
        receipt.setApplicantName("张三");
        receipt.setDepartment("PMC部");
        receipt.setLocationId(1L);
        receipt.setReason("产线使用");
        return receipt;
    }

    private ReceiveReceiptItem item(Long receiptId, Long assetId) {
        ReceiveReceiptItem item = new ReceiveReceiptItem();
        item.setId(10L);
        item.setReceiptId(receiptId);
        item.setAssetId(assetId);
        return item;
    }

    // ---- create ----

    @Test
    void create_shouldInsertReceiptItemsAndLockAssets() {
        AtomicReference<ReceiveReceipt> inserted = new AtomicReference<>();
        stubLocationExists();
        stubLocationNames();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L), idleAsset(2L)));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(receiptMapper.selectOne(any())).thenReturn(null);
        when(receiptMapper.insert(any(ReceiveReceipt.class))).thenAnswer(invocation -> {
            ReceiveReceipt receipt = invocation.getArgument(0);
            receipt.setId(1L);
            inserted.set(receipt);
            return 1;
        });
        when(receiptMapper.selectById(1L)).thenAnswer(inv -> inserted.get());

        ReceiveReceipt created = receiptService.create(applyReq("RECEIVE", List.of(1L, 2L)), 100L, "张三");

        assertNotNull(created);
        assertEquals("ARE" + today() + "0001", created.getSerialNo());
        assertEquals("PENDING", created.getStatus());
        assertEquals("RECEIVE", created.getType());
        assertEquals(100L, created.getApplicantUserId());
        assertEquals("张三", created.getApplicantName());
        assertEquals(1L, created.getLocationId());
        assertEquals("一号车间", created.getLocationName());
        verify(itemMapper, times(2)).insert(any(ReceiveReceiptItem.class));
        verify(assetService, times(2)).changeStatus(anyLong(), eq(AssetStatus.PENDING_CONFIRM),
                eq(100L), eq("领用"), contains("发起申请，待审批"));
    }

    @Test
    void create_borrowShouldUseBorPrefixAndBorrowLogType() {
        AtomicReference<ReceiveReceipt> inserted = new AtomicReference<>();
        stubLocationExists();
        stubLocationNames();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(receiptMapper.selectOne(any())).thenReturn(null);
        when(receiptMapper.insert(any(ReceiveReceipt.class))).thenAnswer(invocation -> {
            ReceiveReceipt receipt = invocation.getArgument(0);
            receipt.setId(2L);
            inserted.set(receipt);
            return 1;
        });
        when(receiptMapper.selectById(2L)).thenAnswer(inv -> inserted.get());

        ReceiveReceipt created = receiptService.create(applyReq("BORROW", List.of(1L)), 100L, "张三");

        assertEquals("BOR" + today() + "0001", created.getSerialNo());
        assertEquals("BORROW", created.getType());
        verify(assetService).changeStatus(eq(1L), eq(AssetStatus.PENDING_CONFIRM),
                eq(100L), eq("借用"), contains("借用单"));
    }

    @Test
    void create_shouldGenerateNextSerialFromMax() {
        ReceiveReceipt latest = new ReceiveReceipt();
        latest.setSerialNo("ARE" + today() + "0005");
        stubLocationExists();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(receiptMapper.selectOne(any())).thenReturn(latest);
        when(receiptMapper.insert(any(ReceiveReceipt.class))).thenAnswer(invocation -> {
            ((ReceiveReceipt) invocation.getArgument(0)).setId(3L);
            return 1;
        });
        when(receiptMapper.selectById(3L)).thenAnswer(inv -> null);

        receiptService.create(applyReq("RECEIVE", List.of(1L)), 100L, "张三");

        ArgumentCaptor<ReceiveReceipt> captor = ArgumentCaptor.forClass(ReceiveReceipt.class);
        verify(receiptMapper).insert(captor.capture());
        assertEquals("ARE" + today() + "0006", captor.getValue().getSerialNo());
    }

    @Test
    void create_shouldRejectWhenLocationMissing() {
        when(locationMapper.selectById(1L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.create(applyReq("RECEIVE", List.of(1L)), 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("领用区域不存在"));
        verify(receiptMapper, never()).insert(any(ReceiveReceipt.class));
    }

    @Test
    void create_shouldRejectWhenAssetMissing() {
        stubLocationExists();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.create(applyReq("RECEIVE", List.of(1L, 2L)), 100L, "张三"));

        assertEquals(404, exception.getCode());
        verify(receiptMapper, never()).insert(any(ReceiveReceipt.class));
    }

    @Test
    void create_shouldRejectWhenAssetOccupiedByPendingReceipt() {
        stubLocationExists();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(5L, 1L)));
        ReceiveReceipt pending = pendingReceipt();
        pending.setId(5L);
        when(receiptMapper.selectList(any())).thenReturn(List.of(pending));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.create(applyReq("BORROW", List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("待审批"));
        verify(receiptMapper, never()).insert(any(ReceiveReceipt.class));
    }

    @Test
    void create_shouldRejectWhenAssetOccupiedByPendingTransfer() {
        stubLocationExists();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        TransferOrderItem transferItem = new TransferOrderItem();
        transferItem.setOrderId(7L);
        transferItem.setAssetId(1L);
        when(transferOrderItemMapper.selectList(any())).thenReturn(List.of(transferItem));
        TransferOrder pendingTransfer = new TransferOrder();
        pendingTransfer.setId(7L);
        pendingTransfer.setSerialNo("ATR" + today() + "0001");
        pendingTransfer.setStatus("PENDING");
        when(transferOrderMapper.selectList(any())).thenReturn(List.of(pendingTransfer));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.create(applyReq("RECEIVE", List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("调拨单"));
        verify(receiptMapper, never()).insert(any(ReceiveReceipt.class));
    }

    @Test
    void create_shouldRejectWhenAssetOccupiedByPendingChangeOrder() {
        stubLocationExists();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        com.sk.asset.entity.change.ChangeOrderItem changeItem = new com.sk.asset.entity.change.ChangeOrderItem();
        changeItem.setOrderId(9L);
        changeItem.setAssetId(1L);
        when(changeOrderItemMapper.selectList(any())).thenReturn(List.of(changeItem));
        com.sk.asset.entity.change.ChangeOrder pendingChange = new com.sk.asset.entity.change.ChangeOrder();
        pendingChange.setId(9L);
        pendingChange.setSerialNo("AOC" + today() + "0001");
        pendingChange.setStatus("PENDING");
        when(changeOrderMapper.selectList(any())).thenReturn(List.of(pendingChange));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.create(applyReq("RECEIVE", List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("变更单"));
        verify(receiptMapper, never()).insert(any(ReceiveReceipt.class));
    }

    @Test
    void create_shouldRejectInvalidType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> receiptService.create(applyReq("BAD", List.of(1L)), 100L, "张三"));
        assertTrue(exception.getMessage().contains("非法单据类型"));
    }

    @Test
    void create_shouldPropagateIllegalStatusTransition() {
        stubLocationExists();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(receiptMapper.selectOne(any())).thenReturn(null);
        when(receiptMapper.insert(any(ReceiveReceipt.class))).thenAnswer(invocation -> {
            ((ReceiveReceipt) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        doThrow(new BusinessException(409, "资产状态不允许流转"))
                .when(assetService).changeStatus(anyLong(), any(), anyLong(), any(), any());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.create(applyReq("RECEIVE", List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
    }

    // ---- approve ----

    @Test
    void approve_shouldTransitionAssetsWriteAllocationsAndApprove() {
        ReceiveReceipt receipt = pendingReceipt();
        when(receiptMapper.selectById(1L)).thenReturn(receipt);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        Asset asset = idleAsset(1L);
        when(assetMapper.selectById(1L)).thenReturn(asset);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(asset));
        when(receiptMapper.updateById(any(ReceiveReceipt.class))).thenReturn(1);
        stubLocationExists();
        stubLocationNames();

        ReceiveReceipt approved = receiptService.approve(1L, 200L, "李四");

        assertEquals("APPROVED", approved.getStatus());
        assertEquals(200L, approved.getApproverUserId());
        assertEquals("李四", approved.getApproverName());
        assertNotNull(approved.getApproveTime());
        verify(assetService).changeStatus(eq(1L), eq(AssetStatus.IN_USE), eq(200L),
                eq("领用"), contains("使用人：张三"));
        // 日志包含领用区域（盘点追溯依据）
        verify(assetService).changeStatus(eq(1L), eq(AssetStatus.IN_USE), eq(200L),
                eq("领用"), contains("领用区域：一号车间"));
        ArgumentCaptor<AssetAllocation> allocationCaptor = ArgumentCaptor.forClass(AssetAllocation.class);
        verify(allocationMapper).insert(allocationCaptor.capture());
        AssetAllocation allocation = allocationCaptor.getValue();
        assertEquals(1L, allocation.getAssetId());
        assertEquals(100L, allocation.getUserId());
        assertEquals("张三", allocation.getUserName());
        assertEquals("RECEIVE", allocation.getType());
        assertEquals("PMC部", allocation.getDepartment());
        assertEquals(3L, allocation.getCompanyId());
        assertNotNull(allocation.getAllocatedAt());
        assertNull(allocation.getReturnedAt());
        // 资产持有人 + 位置（领用区域）更新为申请人申请的值
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Asset>> updateCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(assetMapper).update(any(), updateCaptor.capture());
        assertTrue(updateCaptor.getValue().getSqlSet().contains("location_id"),
                "审批应更新资产位置（location_id），实际 SET：" + updateCaptor.getValue().getSqlSet());
        verify(receiptMapper).updateById(any(ReceiveReceipt.class));
    }

    @Test
    void approve_shouldSkipLocationUpdateForLegacyReceiptWithoutLocation() {
        // 存量单（加列前创建）location_id 为 NULL：审批跳过位置更新，仅写持有人
        ReceiveReceipt legacy = pendingReceipt();
        legacy.setLocationId(null);
        when(receiptMapper.selectById(1L)).thenReturn(legacy);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        when(assetMapper.selectById(1L)).thenReturn(idleAsset(1L));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        when(receiptMapper.updateById(any(ReceiveReceipt.class))).thenReturn(1);

        receiptService.approve(1L, 200L, "李四");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Asset>> updateCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(assetMapper).update(any(), updateCaptor.capture());
        assertFalse(updateCaptor.getValue().getSqlSet().contains("location_id"),
                "存量单无领用区域，不应更新位置，实际 SET：" + updateCaptor.getValue().getSqlSet());
    }

    @Test
    void approve_shouldRejectWhenNotPending() {
        ReceiveReceipt receipt = pendingReceipt();
        receipt.setStatus("APPROVED");
        when(receiptMapper.selectById(1L)).thenReturn(receipt);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.approve(1L, 200L, "李四"));

        assertEquals(409, exception.getCode());
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void approve_shouldRejectWhenApproverIsApplicant() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.approve(1L, 100L, "张三"));

        assertEquals(403, exception.getCode());
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void approve_shouldRejectWhenReceiptMissing() {
        when(receiptMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.approve(9L, 200L, "李四"));

        assertEquals(404, exception.getCode());
    }

    // ---- reject ----

    @Test
    void reject_shouldReturnAssetsToIdleAndMarkRejected() {
        ReceiveReceipt receipt = pendingReceipt();
        when(receiptMapper.selectById(1L)).thenReturn(receipt);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        when(receiptMapper.updateById(any(ReceiveReceipt.class))).thenReturn(1);
        stubLocationNames();

        ReceiveReceipt rejected = receiptService.reject(1L, "不需要", 200L, "李四");

        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("不需要", rejected.getApproveRemark());
        assertEquals(200L, rejected.getApproverUserId());
        verify(assetService).changeStatus(eq(1L), eq(AssetStatus.IDLE), eq(200L),
                eq("领用"), contains("审批拒绝：不需要"));
        // 拒绝不产生持有关系
        verify(allocationMapper, never()).insert(any(AssetAllocation.class));
    }

    @Test
    void reject_shouldRejectWhenNotPending() {
        ReceiveReceipt receipt = pendingReceipt();
        receipt.setStatus("REJECTED");
        when(receiptMapper.selectById(1L)).thenReturn(receipt);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.reject(1L, "不需要", 200L, "李四"));

        assertEquals(409, exception.getCode());
    }

    @Test
    void reject_shouldRejectWhenApproverIsApplicant() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> receiptService.reject(1L, "不需要", 100L, "张三"));

        assertEquals(403, exception.getCode());
    }

    // ---- list ----

    @Test
    void list_shouldFillItemsWithAssetNames() {
        ReceiveReceipt receipt = pendingReceipt();
        when(receiptMapper.selectList(any())).thenReturn(List.of(receipt));
        ReceiveReceiptItem detail = item(1L, 1L);
        when(itemMapper.selectList(any())).thenReturn(List.of(detail));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idleAsset(1L)));
        stubLocationNames();

        List<ReceiveReceipt> result = receiptService.list(new ReceiptQuery());

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getItems().size());
        assertEquals("SKSCDM-0001", result.get(0).getItems().get(0).getAssetBarcode());
        assertEquals("测试资产1", result.get(0).getItems().get(0).getAssetName());
        assertEquals("一号车间", result.get(0).getLocationName());
    }

    @Test
    void getById_shouldReturnNullWhenMissing() {
        when(receiptMapper.selectById(9L)).thenReturn(null);

        assertNull(receiptService.getById(9L));
    }
}
