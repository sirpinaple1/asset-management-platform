package com.sk.asset.service.change;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.sk.asset.service.change.impl.ChangeOrderServiceImpl;
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
class ChangeOrderServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper/LambdaUpdateWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Asset.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetAllocation.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceipt.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceiptItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrderItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChangeOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChangeOrderItem.class);
    }

    @Mock
    private ChangeOrderMapper orderMapper;

    @Mock
    private ChangeOrderItemMapper itemMapper;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private AssetAllocationMapper allocationMapper;

    @Mock
    private ReceiveReceiptMapper receiptMapper;

    @Mock
    private ReceiveReceiptItemMapper receiptItemMapper;

    @Mock
    private TransferOrderMapper transferOrderMapper;

    @Mock
    private TransferOrderItemMapper transferOrderItemMapper;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private CompanyMapper companyMapper;

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
    private ChangeOrderServiceImpl changeService;

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
        asset.setUserId(100L);
        asset.setCompanyId(3L);
        return asset;
    }

    private ChangeApplyReq applyReq(List<Long> assetIds) {
        ChangeApplyReq req = new ChangeApplyReq();
        req.setAssetIds(assetIds);
        req.setNewUserId(300L);
        req.setNewUserName("李四");
        req.setNewUserDepartment("品质部");
        req.setNewLocationId(20L);
        req.setReason("使用人信息纠错");
        return req;
    }

    private ChangeOrder pendingOrder() {
        ChangeOrder order = new ChangeOrder();
        order.setId(1L);
        order.setSerialNo("AOC" + today() + "0001");
        order.setStatus("PENDING");
        order.setApplicantUserId(100L);
        order.setApplicantName("张三");
        order.setNewUserId(300L);
        order.setNewUserName("李四");
        order.setNewUserDepartment("品质部");
        order.setNewLocationId(20L);
        order.setReason("使用人信息纠错");
        order.setCompanyId(3L);
        return order;
    }

    private ChangeOrderItem item(Long orderId, Long assetId, String fieldName,
                                 String fieldLabel, String before, String after) {
        ChangeOrderItem item = new ChangeOrderItem();
        item.setId(10L);
        item.setOrderId(orderId);
        item.setAssetId(assetId);
        item.setFieldName(fieldName);
        item.setFieldLabel(fieldLabel);
        item.setValueBefore(before);
        item.setValueAfter(after);
        return item;
    }

    private Location location(Long id, String name) {
        Location location = new Location();
        location.setId(id);
        location.setName(name);
        return location;
    }

    private Company company(Long id, String name) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        return company;
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

    // ---- create ----

    @Test
    void create_shouldRejectWhenAssigneeNotExist() {
        when(userDirectory.exists(999L)).thenReturn(false);
        ChangeApplyReq req = applyReq(List.of(1L));
        req.setAssigneeUserId(999L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(req, 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("指定处理人不存在"));
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldAllowAssigneeEqualToApplicantAndPersist() {
        // 变更允许发起人自审：assignee=申请人合法
        when(userDirectory.exists(100L)).thenReturn(true);
        AtomicReference<ChangeOrder> inserted = new AtomicReference<>();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 100L, "张三")));
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.insert(any(ChangeOrder.class))).thenAnswer(invocation -> {
            ChangeOrder order = invocation.getArgument(0);
            order.setId(1L);
            inserted.set(order);
            return 1;
        });
        when(orderMapper.selectById(1L)).thenAnswer(inv -> inserted.get());
        when(itemMapper.selectList(any())).thenReturn(List.of());

        ChangeApplyReq req = applyReq(List.of(1L));
        req.setAssigneeUserId(100L);
        changeService.create(req, 100L, "张三");

        assertEquals(100L, inserted.get().getAssigneeUserId());
        // B2：assignee=申请人（自审）不自我通知
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void create_shouldInsertOrderAndItemRowsForChangedFieldsOnly() {
        AtomicReference<ChangeOrder> inserted = new AtomicReference<>();
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 100L, "张三")));
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.insert(any(ChangeOrder.class))).thenAnswer(invocation -> {
            ChangeOrder order = invocation.getArgument(0);
            order.setId(1L);
            inserted.set(order);
            return 1;
        });
        when(orderMapper.selectById(1L)).thenAnswer(inv -> inserted.get());
        when(itemMapper.selectList(any())).thenReturn(List.of());

        ChangeOrder created = changeService.create(applyReq(List.of(1L)), 100L, "张三");

        assertNotNull(created);
        assertEquals("AOC" + today() + "0001", created.getSerialNo());
        assertEquals("PENDING", created.getStatus());
        assertEquals(100L, created.getApplicantUserId());
        assertEquals("张三", created.getApplicantName());
        assertEquals(300L, created.getNewUserId());
        assertEquals("李四", created.getNewUserName());
        assertEquals("品质部", created.getNewUserDepartment());
        assertEquals(20L, created.getNewLocationId());
        assertEquals(3L, created.getCompanyId());
        // 明细行：仅实际变化字段（使用人/使用部门/区域 三行）
        ArgumentCaptor<ChangeOrderItem> itemCaptor = ArgumentCaptor.forClass(ChangeOrderItem.class);
        verify(itemMapper, times(3)).insert(itemCaptor.capture());
        List<ChangeOrderItem> rows = itemCaptor.getAllValues();
        assertEquals("user_id", rows.get(0).getFieldName());
        assertEquals("使用人", rows.get(0).getFieldLabel());
        assertEquals("张三", rows.get(0).getValueBefore());
        assertEquals("李四", rows.get(0).getValueAfter());
        assertEquals("user_department", rows.get(1).getFieldName());
        assertEquals("PMC部", rows.get(1).getValueBefore());
        assertEquals("品质部", rows.get(1).getValueAfter());
        assertEquals("location_id", rows.get(2).getFieldName());
        assertEquals("A区", rows.get(2).getValueBefore());
        assertEquals("B区", rows.get(2).getValueAfter());
        // 变更不锁定资产状态、不写日志（确认时才更新归属）
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
    }

    @Test
    void create_shouldRejectWhenNoChangeFieldSpecified() {
        ChangeApplyReq req = new ChangeApplyReq();
        req.setAssetIds(List.of(1L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(req, 100L, "张三"));

        assertEquals(400, exception.getCode());
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldRejectWhenAssetMissing() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(applyReq(List.of(1L, 2L)), 100L, "张三"));

        assertEquals(404, exception.getCode());
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldRejectWhenAssetDiscarded() {
        Asset discarded = inUseAsset(1L);
        discarded.setStatus(AssetStatus.DISCARD.name());
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(discarded));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("报废"));
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
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
                () -> changeService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("领用/借用单"));
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldRejectWhenOccupiedByPendingTransfer() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
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
                () -> changeService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("调拨单"));
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldRejectWhenOccupiedByPendingChangeOrder() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(itemMapper.selectList(any())).thenReturn(List.of(item(7L, 1L, "location_id", "区域", "A区", "B区")));
        ChangeOrder pending = pendingOrder();
        pending.setId(7L);
        when(orderMapper.selectList(any())).thenReturn(List.of(pending));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("变更单"));
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldRejectWhenNewLocationMissing() {
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(locationMapper.selectById(20L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("变更后位置不存在"));
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldRejectWhenNewCompanyMissing() {
        ChangeApplyReq req = new ChangeApplyReq();
        req.setAssetIds(List.of(1L));
        req.setNewCompanyId(9L);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(companyMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(req, 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("变更后归属公司不存在"));
        verify(orderMapper, never()).insert(any(ChangeOrder.class));
    }

    @Test
    void create_shouldRejectWhenNoActualChange() {
        // 资产当前值与变更目标一致：无明细行可生成 → 400
        Asset asset = inUseAsset(1L);
        asset.setUserId(300L);
        asset.setUserDepartment("品质部");
        asset.setLocationId(20L);
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(asset));
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(orderMapper.insert(any(ChangeOrder.class))).thenAnswer(invocation -> {
            ((ChangeOrder) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 300L, "李四")));
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location(20L, "B区")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.create(applyReq(List.of(1L)), 100L, "张三"));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("无变更内容"));
        verify(itemMapper, never()).insert(any(ChangeOrderItem.class));
    }

    @Test
    void create_shouldGenerateNextSerialFromMax() {
        ChangeOrder latest = new ChangeOrder();
        latest.setSerialNo("AOC" + today() + "0005");
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(orderMapper.selectOne(any())).thenReturn(latest);
        when(locationMapper.selectById(20L)).thenReturn(location(20L, "B区"));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 100L, "张三")));
        when(locationMapper.selectBatchIds(any()))
                .thenReturn(List.of(location(10L, "A区"), location(20L, "B区")));
        when(orderMapper.insert(any(ChangeOrder.class))).thenAnswer(invocation -> {
            ((ChangeOrder) invocation.getArgument(0)).setId(3L);
            return 1;
        });
        when(itemMapper.selectList(any())).thenReturn(List.of());

        changeService.create(applyReq(List.of(1L)), 100L, "张三");

        ArgumentCaptor<ChangeOrder> captor = ArgumentCaptor.forClass(ChangeOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals("AOC" + today() + "0006", captor.getValue().getSerialNo());
    }

    // ---- list ----

    @Test
    void list_shouldQueryOrdersByAssetIdViaItems() {
        when(itemMapper.selectList(any()))
                .thenReturn(List.of(item(1L, 1L, "location_id", "区域", "A区", "B区")));
        when(orderMapper.selectList(any())).thenReturn(List.of(pendingOrder()));
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location(20L, "B区")));

        ChangeQuery query = new ChangeQuery();
        query.setAssetId(1L);
        List<ChangeOrder> orders = changeService.list(query);

        assertEquals(1, orders.size());
        assertEquals("AOC" + today() + "0001", orders.get(0).getSerialNo());
        assertEquals(1, orders.get(0).getItems().size());
        assertEquals("B区", orders.get(0).getNewLocationName());
        verify(orderMapper).selectList(any());
    }

    @Test
    void list_shouldReturnEmptyWhenAssetHasNoChangeHistory() {
        when(itemMapper.selectList(any())).thenReturn(List.of());

        ChangeQuery query = new ChangeQuery();
        query.setAssetId(1L);
        List<ChangeOrder> orders = changeService.list(query);

        assertTrue(orders.isEmpty());
        verify(orderMapper, never()).selectList(any());
    }

    // ---- confirm ----

    @Test
    void confirm_shouldUpdateAssetSyncAllocationAndWriteLog() {
        ChangeOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, "user_id", "使用人", "张三", "李四"),
                item(1L, 1L, "user_department", "使用部门", "PMC部", "品质部"),
                item(1L, 1L, "location_id", "区域", "A区", "B区")));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 100L, "张三")));
        when(orderMapper.updateById(any(ChangeOrder.class))).thenReturn(1);
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location(20L, "B区")));

        ChangeOrder confirmed = changeService.confirm(1L, 400L, "赵六");

        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(400L, confirmed.getConfirmerUserId());
        assertEquals("赵六", confirmed.getConfirmerName());
        assertNotNull(confirmed.getConfirmTime());
        // 资产归属字段更新
        verify(assetMapper).update(isNull(), any());
        // 旧持有关系闭环
        verify(allocationMapper).update(isNull(), any());
        // 新建 CHANGE 持有记录
        ArgumentCaptor<AssetAllocation> allocationCaptor = ArgumentCaptor.forClass(AssetAllocation.class);
        verify(allocationMapper).insert(allocationCaptor.capture());
        AssetAllocation newAllocation = allocationCaptor.getValue();
        assertEquals(1L, newAllocation.getAssetId());
        assertEquals(300L, newAllocation.getUserId());
        assertEquals("李四", newAllocation.getUserName());
        assertEquals("CHANGE", newAllocation.getType());
        assertEquals("品质部", newAllocation.getDepartment());
        assertNotNull(newAllocation.getAllocatedAt());
        // 写变更日志（不改状态），含使用人/部门/区域变更明细
        verify(assetService).writeLog(eq(1L), eq("实物信息变更"), eq(400L), contains("变更单 AOC"));
        verify(assetService).writeLog(eq(1L), eq("实物信息变更"), eq(400L),
                contains("【使用人】由【张三】变更为【李四】"));
        verify(assetService).writeLog(eq(1L), eq("实物信息变更"), eq(400L),
                contains("【使用部门】由【PMC部】变更为【品质部】"));
        verify(assetService).writeLog(eq(1L), eq("实物信息变更"), eq(400L),
                contains("【区域】由【A区】变更为【B区】"));
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void confirm_shouldAllowApplicantSelfConfirm() {
        // 变更单为信息修正单据：发起人可自己确认执行（区别于 M04/M05 审批流）
        ChangeOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, "user_id", "使用人", "张三", "李四")));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 100L, "张三")));
        when(orderMapper.updateById(any(ChangeOrder.class))).thenReturn(1);
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        ChangeOrder confirmed = changeService.confirm(1L, 100L, "张三");

        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(100L, confirmed.getConfirmerUserId());
        // B2：自审（发起人=确认人）不自我通知
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    // ---- confirm：B1 指定处理人门禁 ----

    @Test
    void confirm_shouldRejectWhenOperatorIsNotAssignee() {
        ChangeOrder order = pendingOrder();
        order.setAssigneeUserId(200L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.confirm(1L, 300L, "王五"));

        assertEquals(403, exception.getCode());
        assertTrue(exception.getMessage().contains("指定处理人"));
        verify(orderMapper, never()).updateById(any(ChangeOrder.class));
    }

    @Test
    void confirm_shouldAllowAssigneeToConfirm() {
        ChangeOrder order = pendingOrder();
        order.setAssigneeUserId(400L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, "user_id", "使用人", "张三", "李四")));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(allocationMapper.selectList(any()))
                .thenReturn(List.of(activeAllocation(1L, 100L, "张三")));
        when(orderMapper.updateById(any(ChangeOrder.class))).thenReturn(1);
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of());

        assertEquals("CONFIRMED", changeService.confirm(1L, 400L, "赵六").getStatus());
    }

    @Test
    void confirm_shouldNotSyncAllocationWhenUserUnchanged() {
        // 仅变更位置（new_user_id 为 null）：不触碰持有关系
        ChangeOrder order = pendingOrder();
        order.setNewUserId(null);
        order.setNewUserName(null);
        order.setNewUserDepartment(null);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, "location_id", "区域", "A区", "B区")));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(orderMapper.updateById(any(ChangeOrder.class))).thenReturn(1);
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location(20L, "B区")));

        ChangeOrder confirmed = changeService.confirm(1L, 400L, "赵六");

        assertEquals("CONFIRMED", confirmed.getStatus());
        verify(allocationMapper, never()).selectList(any());
        verify(allocationMapper, never()).update(any(), any());
        verify(allocationMapper, never()).insert(any(AssetAllocation.class));
        verify(assetService).writeLog(eq(1L), eq("实物信息变更"), eq(400L),
                contains("【区域】由【A区】变更为【B区】"));
    }

    @Test
    void confirm_shouldNotSyncAllocationWhenNewUserEqualsCurrent() {
        // 新使用人与当前使用人一致：持有关系无需转移
        ChangeOrder order = pendingOrder();
        order.setNewUserId(100L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, "location_id", "区域", "A区", "B区")));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(inUseAsset(1L)));
        when(orderMapper.updateById(any(ChangeOrder.class))).thenReturn(1);
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location(20L, "B区")));

        changeService.confirm(1L, 400L, "赵六");

        verify(allocationMapper, never()).selectList(any());
        verify(allocationMapper, never()).insert(any(AssetAllocation.class));
    }

    @Test
    void confirm_shouldRejectWhenOrderMissing() {
        when(orderMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.confirm(9L, 400L, "赵六"));

        assertEquals(404, exception.getCode());
    }

    @Test
    void confirm_shouldRejectWhenNotPending() {
        ChangeOrder order = pendingOrder();
        order.setStatus("CONFIRMED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.confirm(1L, 400L, "赵六"));

        assertEquals(409, exception.getCode());
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
    }

    @Test
    void confirm_shouldRejectWhenAssetDiscardedDuringPending() {
        ChangeOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, "user_id", "使用人", "张三", "李四")));
        Asset discarded = inUseAsset(1L);
        discarded.setStatus(AssetStatus.DISCARD.name());
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(discarded));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.confirm(1L, 400L, "赵六"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("报废"));
        verify(orderMapper, never()).updateById(any(ChangeOrder.class));
    }

    @Test
    void confirm_shouldRejectWhenAssetMissingDuringPending() {
        ChangeOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectList(any())).thenReturn(List.of(
                item(1L, 1L, "user_id", "使用人", "张三", "李四")));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.confirm(1L, 400L, "赵六"));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("不存在或已删除"));
    }

    // ---- cancel ----

    @Test
    void cancel_shouldSetCancelledByApplicant() {
        ChangeOrder order = pendingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any(ChangeOrder.class))).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location(20L, "B区")));

        ChangeOrder cancelled = changeService.cancel(1L, 100L);

        assertEquals("CANCELLED", cancelled.getStatus());
        // 撤销不动资产、不写日志
        verify(assetService, never()).writeLog(anyLong(), any(), anyLong(), any());
        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void cancel_shouldRejectWhenNotApplicant() {
        when(orderMapper.selectById(1L)).thenReturn(pendingOrder());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.cancel(1L, 400L));

        assertEquals(403, exception.getCode());
        verify(orderMapper, never()).updateById(any(ChangeOrder.class));
    }

    @Test
    void cancel_shouldRejectWhenNotPending() {
        ChangeOrder order = pendingOrder();
        order.setStatus("CONFIRMED");
        when(orderMapper.selectById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> changeService.cancel(1L, 100L));

        assertEquals(409, exception.getCode());
        verify(orderMapper, never()).updateById(any(ChangeOrder.class));
    }
}
