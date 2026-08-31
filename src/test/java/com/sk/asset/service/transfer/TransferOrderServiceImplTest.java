package com.sk.asset.service.transfer;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.dto.transfer.TransferQuery;
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
import com.sk.asset.service.transfer.impl.TransferOrderServiceImpl;
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
class TransferOrderServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper/LambdaUpdateWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Asset.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetAllocation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceipt.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceiptItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrderItem.class);
    }

    @Mock
    private TransferOrderMapper orderMapper;

    @Mock
    private TransferOrderItemMapper itemMapper;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private AssetAllocationMapper allocationMapper;

    @Mock
    private ReceiveReceiptMapper receiptMapper;

    @Mock
    private ReceiveReceiptItemMapper receiptItemMapper;

    @Mock
    private com.sk.asset.mapper.change.ChangeOrderMapper changeOrderMapper;

    @Mock
    private com.sk.asset.mapper.change.ChangeOrderItemMapper changeOrderItemMapper;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private AssetService assetService;

    @Mock
    private com.sk.asset.auth.UserDirectory userDirectory;

    @Mock
    private com.sk.asset.service.notification.NotificationService notificationService;

    @Mock
    private com.sk.asset.service.approval.ApprovalConfigService approvalConfigService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransferOrderServiceImpl transferService;

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private Asset inUseAsset(Long id) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setBarcode("SKSCDM-000" + id);
        asset.setName("测试资产" + id);
        asset.setStatus(AssetStatus.IN_USE.name());
        asset.setLocationId(10L);
        asset.setUserDepartment("PMC部");
        asset.setCompanyId(3L);
        return asset;
    }

    private TransferApplyReq applyReq(List<Long> assetIds) {
        TransferApplyReq req = new TransferApplyReq();
        req.setAssetIds(assetIds);
        req.setToLocationId(20L);
        req.setToDepartment("品质部");
        req.setToUserId(300L);
        req.setToUserName("李四");
        req.setReason("部门搬迁");
        return req;
    }

    private TransferOrder pendingOrder() {
        TransferOrder order = new TransferOrder();
        order.setId(1L);
        order.setSerialNo("ATR" + today() + "0001");
        order.setStatus("PENDING");
        order.setSource("MANUAL");
        order.setApplicantUserId(100L);
        order.setApplicantName("张三");
        order.setFromLocationId(10L);
        order.setToLocationId(20L);
        order.setToDepartment("品质部");
        order.setToUserId(300L);
        order.setToUserName("李四");
        order.setReason("部门搬迁");
        order.setCompanyId(3L);
        return order;
    }

    private TransferOrderItem item(Long orderId, Long assetId) {
        TransferOrderItem item = new TransferOrderItem();
        item.setId(10L);
        item.setOrderId(orderId);
        item.setAssetId(assetId);
        return item;
    }

    private Location location(Long id, String name) {
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        return location;
    }

    private AssetAllocation activeAllocation(Long assetId, Long userId, String userName) {
        AssetAllocation allocation = new AssetAllocation();
        allocation.setId(99L);
        allocation.setAssetId(assetId);
        allocation.setUserId(userId);
        allocation.setUserName(userName);
        allocation.setType("RECEIVE");
        allocation.setDepartment("PMC部");
        allocation.setAllocatedAt(java.time.LocalDateTime.now().minusDays(10));
        return allocation;
    }

    // ---- create：B1 指定处理人 ----

    @Test
    void create_shouldRejectWhenAssigneeIsApplicant() {
        TransferApplyReq req = applyReq(List.of(1L));
        req.setAssigneeUserId(100L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(req, 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("发起人自己"));
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldRejectWhenAssigneeNotExist() {
        when(userDirectory.exists(999L)).thenReturn(false);
        TransferApplyReq req = applyReq(List.of(1L));
        req.setAssigneeUserId(999L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(req, 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("指定处理人不存在"));
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldPersistAssigneeWhenValid() {
        when(userDirectory.exists(200L)).thenReturn(true);
        AtomicReference<TransferOrder> inserted = new AtomicReference<>();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.insert(any(TransferOrder.class))).thenAnswer(invocation -> {
            TransferOrder order = invocation.getArgument(0);
            order.setId(1L);
            inserted.set(order);
            return 1;
        });

        TransferApplyReq req = applyReq(List.of(1L));
        req.setAssigneeUserId(200L);
        transferService.create(req, 100L, "张三");

        assertEquals(200L, inserted.get().getAssigneeUserId());
        // B2：定向发起应通知处理人
        verify(notificationService).notify(eq(200L),
                eq(com.sk.asset.enums.notification.NotificationType.DOC_SUBMITTED),
                contains("ATR"), eq("TRANSFER"), eq(1L));
    }

    // ---- create ----

    @Test
    void create_shouldInsertOrderAndItemsWithoutLockingAssets() {
        AtomicReference<TransferOrder> inserted = new AtomicReference<>();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L), inUseAsset(2L)));
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(orderMapper.insert(any(TransferOrder.class))).thenAnswer(invocation -> {
            TransferOrder order = invocation.getArgument(0);
            order.setId(1L);
            inserted.set(order);
            return 1;
        });
        when(orderMapper.selectById(1L)).thenAnswer(inv -> inserted.get());
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));

        TransferOrder created = transferService.create(applyReq(List.of(1L, 2L)), 100L, "张三");

        assertNotNull(created);
        assertEquals("ATR" + today() + "0001", created.getSerialNo());
        assertEquals("PENDING", created.getStatus());
        assertEquals("MANUAL", created.getSource());
        assertEquals(100L, created.getApplicantUserId());
        assertEquals("张三", created.getApplicantName());
        assertEquals("李四", created.getToUserName());
        assertEquals("品质部", created.getToDepartment());
        // 调出位置取首台资产当前位置
        assertEquals(10L, created.getFromLocationId());
        assertEquals(3L, created.getCompanyId());
        verify(itemMapper, times(2)).insert(any(TransferOrderItem.class));
        // 调拨不锁定资产状态、不写日志（确认时才更新归属）
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
    }

    @Test
    void create_withSourceAndStocktakeId_shouldPersistInventoryTriggeredOrder() {
        // M07 盘点差异触发：source=INVENTORY_TRIGGERED + stocktake_id 关联落库
        AtomicReference<TransferOrder> inserted = new AtomicReference<>();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(orderMapper.insert(any(TransferOrder.class))).thenAnswer(invocation -> {
            TransferOrder order = invocation.getArgument(0);
            order.setId(1L);
            inserted.set(order);
            return 1;
        });
        when(orderMapper.selectById(1L)).thenAnswer(inv -> inserted.get());
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));

        TransferOrder created = transferService.create(applyReq(List.of(1L)), 100L, "张三",
                com.sk.asset.enums.transfer.TransferSource.INVENTORY_TRIGGERED, 5L);

        assertEquals("INVENTORY_TRIGGERED", created.getSource());
        assertEquals(5L, created.getStocktakeId());
    }

    @Test
    void create_shouldRejectWhenNeitherToLocationNorDepartment() {
        TransferApplyReq req = applyReq(List.of(1L));
        req.setToLocationId(null);
        req.setToDepartment(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(req, 100L, "张三"));

        assertEquals(400, exception.getCode());
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldRejectWhenAssetMissing() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(applyReq(List.of(1L, 2L)), 100L, "张三"));

        assertEquals(404, exception.getCode());
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldRejectWhenAssetDiscarded() {
        Asset discarded = inUseAsset(1L);
        discarded.setStatus(AssetStatus.DISCARD.name());
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(discarded));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("报废"));
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldRejectWhenOccupiedByPendingTransfer() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(7L, 1L)));
        TransferOrder pending = pendingOrder();
        pending.setId(7L);
        when(orderMapper.selectList(any())).thenReturn(List.of(pending));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("调拨单"));
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldRejectWhenOccupiedByPendingReceipt() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        ReceiveReceiptItem receiptItem = new ReceiveReceiptItem();
        receiptItem.setReceiptId(5L);
        receiptItem.setAssetId(1L);
        when(receiptItemMapper.selectList(any())).thenReturn(List.of(receiptItem));
        ReceiveReceipt pendingReceipt = new ReceiveReceipt();
        pendingReceipt.setId(5L);
        pendingReceipt.setSerialNo("ARE" + today() + "0001");
        pendingReceipt.setStatus("PENDING");
        when(receiptMapper.selectList(any())).thenReturn(List.of(pendingReceipt));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("领用/借用单"));
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldRejectWhenOccupiedByPendingChangeOrder() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
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
                () -> transferService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("变更单"));
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldRejectWhenToLocationMissing() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(locationMapper.selectById(20L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("调入位置不存在"));
        verify(orderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void create_shouldGenerateNextSerialFromMax() {
        TransferOrder latest = new TransferOrder();
        latest.setSerialNo("ATR" + today() + "0005");
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(orderMapper.selectOne(any())).thenReturn(latest);
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(orderMapper.insert(any(TransferOrder.class))).thenAnswer(invocation -> {
            ((TransferOrder) invocation.getArgument(0)).setId(3L);
            return 1;
        });
        when(orderMapper.selectById(3L)).thenAnswer(inv -> null);

        transferService.create(applyReq(List.of(1L)), 100L, "张三");

        ArgumentCaptor<TransferOrder> captor = ArgumentCaptor.forClass(TransferOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals("ATR" + today() + "0006", captor.getValue().getSerialNo());
    }

    // ---- confirm ----

    @Test
    void confirm_shouldTransferOwnershipAllocationAndWriteLog() {
        TransferOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 250L, "王五")));
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.updateById(any(TransferOrder.class))).thenReturn(1);

        TransferOrder confirmed = transferService.confirm(1L, 400L, "赵六");

        assertEquals("COMPLETED", confirmed.getStatus());
        assertEquals(400L, confirmed.getConfirmerUserId());
        assertEquals("赵六", confirmed.getConfirmerName());
        assertNotNull(confirmed.getConfirmTime());
        // B2：确认完成通知发起人
        verify(notificationService).notify(eq(100L),
                eq(com.sk.asset.enums.notification.NotificationType.DOC_COMPLETED),
                contains("ATR"), eq("TRANSFER"), eq(1L));
        // 旧持有关系闭环
        verify(allocationMapper).update(isNull(), any());
        // 资产归属更新
        verify(assetMapper).update(isNull(), any());
        // 新建调入方持有记录（type=TRANSFER）
        ArgumentCaptor<AssetAllocation> allocationCaptor = ArgumentCaptor.forClass(AssetAllocation.class);
        verify(allocationMapper).insert(allocationCaptor.capture());
        AssetAllocation newAllocation = allocationCaptor.getValue();
        assertEquals(1L, newAllocation.getAssetId());
        assertEquals(300L, newAllocation.getUserId());
        assertEquals("李四", newAllocation.getUserName());
        assertEquals("TRANSFER", newAllocation.getType());
        assertEquals("品质部", newAllocation.getDepartment());
        assertEquals(3L, newAllocation.getCompanyId());
        assertNotNull(newAllocation.getAllocatedAt());
        // 写调拨日志（不改状态），含位置/部门/使用人变更明细
        verify(assetService).writeLog(eq(1L), eq("调拨"), eq(400L), contains("调拨单 ATR"));
        verify(assetService).writeLog(eq(1L), eq("调拨"), eq(400L),
                contains("【位置】由【A区】变更为【B区】"));
        verify(assetService).writeLog(eq(1L), eq("调拨"), eq(400L),
                contains("【部门】由【PMC部】变更为【品质部】"));
        verify(assetService).writeLog(eq(1L), eq("调拨"), eq(400L),
                contains("【使用人】由【王五】变更为【李四】"));
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void confirm_shouldInsertDeptHolderWhenOnlyDepartmentGiven() {
        // 只填部门（没填人）：应建"部门持有"（user_id=null）→ IN_USE，不漏持有
        TransferOrder order = pendingOrder();
        order.setToUserId(null);
        order.setToUserName(null);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(allocationMapper.selectList(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.updateById(any(TransferOrder.class))).thenReturn(1);

        TransferOrder confirmed = transferService.confirm(1L, 400L, "赵六");

        assertEquals("COMPLETED", confirmed.getStatus());
        // 部门持有：user_id=null + department=新部门 + type=TRANSFER
        ArgumentCaptor<AssetAllocation> allocationCaptor = ArgumentCaptor.forClass(AssetAllocation.class);
        verify(allocationMapper).insert(allocationCaptor.capture());
        AssetAllocation deptHolder = allocationCaptor.getValue();
        assertNull(deptHolder.getUserId());
        assertNull(deptHolder.getUserName());
        assertEquals("品质部", deptHolder.getDepartment());
        assertEquals("TRANSFER", deptHolder.getType());
        // 资产已 IN_USE，同态跳过状态流转
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
        verify(assetService).writeLog(eq(1L), eq("调拨"), eq(400L),
                contains("【使用人】由【未设置】变更为【品质部（部门持有）】"));
    }

    @Test
    void confirm_shouldReturnAssetToWarehouseWhenOnlyLocationGiven() {
        // 只填区域（没人没部门）：视为调拨回库——闭环旧持有 + 持有人归零 + IN_USE→IDLE
        TransferOrder order = pendingOrder();
        order.setToUserId(null);
        order.setToUserName(null);
        order.setToDepartment(null);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 250L, "王五")));
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.updateById(any(TransferOrder.class))).thenReturn(1);

        TransferOrder confirmed = transferService.confirm(1L, 400L, "赵六");

        assertEquals("COMPLETED", confirmed.getStatus());
        // 回库：闭环旧持有 + 状态流转 IDLE，不新建持有
        verify(allocationMapper, never()).insert(any(AssetAllocation.class));
        verify(assetService).changeStatus(eq(1L), eq(AssetStatus.IDLE), eq(400L),
                eq("调拨"), contains("回库"));
        verify(assetService).writeLog(eq(1L), eq("调拨"), eq(400L),
                contains("确认调拨（回库）"));
        verify(assetService).writeLog(eq(1L), eq("调拨"), eq(400L),
                contains("【使用人】由【王五】变更为【未设置】"));
    }

    @Test
    void confirm_shouldFlipIdleToInUseWhenHolderAssigned() {
        // 闲置资产调拨给人/部门：IDLE→IN_USE 状态联动（有新持有必为在用）
        TransferOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        Asset idle = inUseAsset(1L);
        idle.setStatus(AssetStatus.IDLE.name());
        idle.setUserId(null);
        idle.setUserDepartment(null);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(idle));
        when(allocationMapper.selectList(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.updateById(any(TransferOrder.class))).thenReturn(1);

        TransferOrder confirmed = transferService.confirm(1L, 400L, "赵六");

        assertEquals("COMPLETED", confirmed.getStatus());
        verify(assetService).changeStatus(eq(1L), eq(AssetStatus.IN_USE), eq(400L),
                eq("调拨"), contains("调入"));
        verify(allocationMapper).insert(any(AssetAllocation.class));
    }

    @Test
    void confirm_shouldRejectWhenNotPending() {
        TransferOrder order = pendingOrder();
        order.setStatus("COMPLETED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.confirm(1L, 400L, "赵六"));

        assertEquals(409, exception.getCode());
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
    }

    @Test
    void confirm_shouldRejectWhenConfirmerIsApplicant() {
        when(orderMapper.selectById(1L)).thenReturn(pendingOrder());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.confirm(1L, 100L, "张三"));

        assertEquals(403, exception.getCode());
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
    }

    // ---- confirm/reject：B1 指定处理人门禁 ----

    @Test
    void confirm_shouldRejectWhenOperatorIsNotAssignee() {
        TransferOrder order = pendingOrder();
        order.setAssigneeUserId(200L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.confirm(1L, 300L, "王五"));

        assertEquals(403, exception.getCode());
        assertTrue(exception.getMessage().contains("指定处理人"));
        verify(orderMapper, never()).updateById(any(TransferOrder.class));
    }

    @Test
    void confirm_shouldAllowAssigneeToConfirm() {
        TransferOrder order = pendingOrder();
        order.setAssigneeUserId(400L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 250L, "王五")));
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.updateById(any(TransferOrder.class))).thenReturn(1);

        assertEquals("COMPLETED", transferService.confirm(1L, 400L, "赵六").getStatus());
    }

    @Test
    void confirm_shouldRejectWhenAssetDiscardedDuringPending() {
        TransferOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L)));
        Asset discarded = inUseAsset(1L);
        discarded.setStatus(AssetStatus.DISCARD.name());
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(discarded));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.confirm(1L, 400L, "赵六"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("报废"));
        verify(orderMapper, never()).updateById(any(TransferOrder.class));
    }

    @Test
    void confirm_shouldRejectWhenOrderMissing() {
        when(orderMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.confirm(9L, 400L, "赵六"));

        assertEquals(404, exception.getCode());
    }

    // ---- reject ----

    @Test
    void reject_shouldMarkRejectedWithoutTouchingAssets() {
        TransferOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any(TransferOrder.class))).thenReturn(1);

        TransferOrder rejected = transferService.reject(1L, "位置不符", 400L, "赵六");

        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("位置不符", rejected.getRejectReason());
        assertEquals(400L, rejected.getConfirmerUserId());
        assertEquals("赵六", rejected.getConfirmerName());
        assertNotNull(rejected.getConfirmTime());
        // 拒绝：资产与持有关系不变
        verify(assetMapper, never()).update(any(), any());
        verify(allocationMapper, never()).update(any(), any());
        verify(allocationMapper, never()).insert(any(AssetAllocation.class));
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
    }

    @Test
    void reject_shouldRejectWhenNotPending() {
        TransferOrder order = pendingOrder();
        order.setStatus("REJECTED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.reject(1L, "位置不符", 400L, "赵六"));

        assertEquals(409, exception.getCode());
    }

    @Test
    void reject_shouldRejectWhenConfirmerIsApplicant() {
        when(orderMapper.selectById(1L)).thenReturn(pendingOrder());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.reject(1L, "位置不符", 100L, "张三"));

        assertEquals(403, exception.getCode());
    }

    // ---- cancel ----

    @Test
    void cancel_shouldMarkCancelledByApplicant() {
        TransferOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any(TransferOrder.class))).thenReturn(1);

        TransferOrder cancelled = transferService.cancel(1L, 100L);

        assertEquals("CANCELLED", cancelled.getStatus());
        // 撤销：资产与持有关系不变
        verify(assetMapper, never()).update(any(), any());
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
    }

    @Test
    void cancel_shouldRejectWhenNotApplicant() {
        when(orderMapper.selectById(1L)).thenReturn(pendingOrder());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.cancel(1L, 400L));

        assertEquals(403, exception.getCode());
        verify(orderMapper, never()).updateById(any(TransferOrder.class));
    }

    @Test
    void cancel_shouldRejectWhenNotPending() {
        TransferOrder order = pendingOrder();
        order.setStatus("CANCELLED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transferService.cancel(1L, 100L));

        assertEquals(409, exception.getCode());
    }

    // ---- list / getById ----

    @Test
    void list_shouldFillItemsAndLocationNames() {
        TransferOrder order = pendingOrder();
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        TransferOrderItem detail = item(1L, 1L);
        when(itemMapper.selectList(any())).thenReturn(List.of(detail));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));

        List<TransferOrder> result = transferService.list(new TransferQuery());

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getItems().size());
        assertEquals("SKSCDM-0001", result.get(0).getItems().get(0).getAssetBarcode());
        assertEquals("测试资产1", result.get(0).getItems().get(0).getAssetName());
        assertEquals("A区", result.get(0).getFromLocationName());
        assertEquals("B区", result.get(0).getToLocationName());
    }

    @Test
    void getById_shouldReturnNullWhenMissing() {
        when(orderMapper.selectById(9L)).thenReturn(null);

        assertNull(transferService.getById(9L));
    }
}
