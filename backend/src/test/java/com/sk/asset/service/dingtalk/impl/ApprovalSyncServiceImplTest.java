package com.sk.asset.service.dingtalk.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dingtalk.client.DingTalkApiClient;
import com.sk.asset.dingtalk.client.DingTalkApiException;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dingtalk.event.OaSyncRequestedEvent;
import com.sk.asset.dingtalk.event.OaTaskExecuteRequestedEvent;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.change.ChangeOrderItem;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.entity.transfer.TransferOrderItem;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.change.ChangeOrderItemMapper;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptItemMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderItemMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.approval.DeptManagerChainResolver;
import com.sk.asset.service.notification.NotificationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉钉审批同步测试（M10 入口 A：系统建单 → AFTER_COMMIT 推钉钉）。
 *
 * <p>覆盖降级矩阵：总开关/模板未配置静默跳过、审批链未绑定 FAILED+告警、
 * API 失败 FAILED+告警不抛、已 SYNCED 幂等；审批人节点构造：两级同人合并、
 * 两级不同人两节点；成功路径回填 dingtalk_instance_id。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalSyncServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper/LambdaUpdateWrapper 列名解析需要 TableInfo 缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalInstance.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceipt.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReceiveReceiptItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TransferOrderItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChangeOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChangeOrderItem.class);
    }

    private static final Long APPLICANT = 100L;
    private static final Long STEP1_USER = 200L;
    private static final Long STEP2_USER = 300L;

    @Mock
    private DingTalkApiClient apiClient;
    @Mock
    private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReceiveReceiptMapper receiptMapper;
    @Mock
    private ReceiveReceiptItemMapper receiptItemMapper;
    @Mock
    private TransferOrderMapper transferOrderMapper;
    @Mock
    private TransferOrderItemMapper transferOrderItemMapper;
    @Mock
    private ChangeOrderMapper changeOrderMapper;
    @Mock
    private ChangeOrderItemMapper changeOrderItemMapper;
    @Mock
    private AssetMapper assetMapper;
    @Mock
    private LocationMapper locationMapper;
    @Mock
    private DeptManagerChainResolver deptManagerChainResolver;

    private DingtalkProperties props;
    private ApprovalSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new DingtalkProperties();
        props.setEnabled(true);
        props.getProcessCodes().put("receive", "PROC-R");
        props.getProcessCodes().put("transfer", "PROC-T");
        props.getProcessCodes().put("change", "PROC-C");
        service = new ApprovalSyncServiceImpl(props, apiClient, approvalInstanceMapper,
                userDirectory, notificationService, deptManagerChainResolver, receiptMapper,
                receiptItemMapper, transferOrderMapper, transferOrderItemMapper, changeOrderMapper,
                changeOrderItemMapper, assetMapper, locationMapper);
    }

    private ReceiveReceipt pendingReceipt(Long step1User, Long step2User) {
        ReceiveReceipt r = new ReceiveReceipt();
        r.setId(1L);
        r.setType("RECEIVE");
        r.setStatus("PENDING");
        r.setSerialNo("ARE202608310001");
        r.setApplicantUserId(APPLICANT);
        r.setApprovalStep(step1User.equals(step2User) ? 2 : 1);
        r.setApprovalStep1UserId(step1User);
        r.setApprovalStep2UserId(step2User);
        r.setReason("测试领用");
        return r;
    }

    /** 多级链解析成功 stub（完整钉钉节点序列 = 快照两级 + 后续层级） */
    private void stubMultiLevelChain(List<String> allDdUserIds) {
        when(deptManagerChainResolver.tryResolveMultiLevel(APPLICANT)).thenReturn(
                new DeptManagerChainResolver.MultiResolution(null, allDdUserIds, null));
    }

    private void stubOriginatorDd() {
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of(APPLICANT, "dd100"));
    }

    private void mockNoExistingInstance() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
    }

    // ------------------------------------------------------------ 总开关与前置校验

    @Test
    void 总开关关闭_直接跳过() {
        props.setEnabled(false);

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        verifyNoInteractions(receiptMapper, apiClient, approvalInstanceMapper);
    }

    @Test
    void 领用模板未配置_静默跳过() {
        props.getProcessCodes().clear();
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP2_USER));

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        verifyNoInteractions(apiClient, approvalInstanceMapper, notificationService);
    }

    @Test
    void 单据非待审批_跳过() {
        ReceiveReceipt approved = pendingReceipt(STEP1_USER, STEP2_USER);
        approved.setStatus("APPROVED");
        when(receiptMapper.selectById(1L)).thenReturn(approved);

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        verifyNoInteractions(apiClient, approvalInstanceMapper);
    }

    @Test
    void 未知单据类别_忽略() {
        service.onSyncRequested(new OaSyncRequestedEvent("UNKNOWN", 1L));
        verifyNoInteractions(apiClient, approvalInstanceMapper);
    }

    // ------------------------------------------------------------ 降级矩阵

    @Test
    void 多级链解析失败_FAILED告警() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP2_USER));
        when(deptManagerChainResolver.tryResolveMultiLevel(APPLICANT)).thenReturn(
                new DeptManagerChainResolver.MultiResolution(null, List.of(),
                        "固定一级审批人未绑定系统用户"));
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        verifyNoInteractions(apiClient);
        ArgumentCaptor<ApprovalInstance> captor = ArgumentCaptor.forClass(ApprovalInstance.class);
        verify(approvalInstanceMapper).insert(captor.capture());
        assertEquals(ApprovalInstance.SYNC_FAILED, captor.getValue().getSyncStatus());
        verify(notificationService).notify(eq(999L), eq(NotificationType.DINGTALK_SYNC_ALERT),
                contains("降级站内审批"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 发起人未绑定钉钉_FAILED告警() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP2_USER));
        stubMultiLevelChain(List.of("dd200", "dd300"));
        // 只回空映射：发起人无 dd_user_id
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of());
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        verifyNoInteractions(apiClient);
        ArgumentCaptor<ApprovalInstance> captor = ArgumentCaptor.forClass(ApprovalInstance.class);
        verify(approvalInstanceMapper).insert(captor.capture());
        assertEquals(ApprovalInstance.SYNC_FAILED, captor.getValue().getSyncStatus());
        verify(notificationService).notify(eq(999L), eq(NotificationType.DINGTALK_SYNC_ALERT),
                contains("发起人未绑定钉钉"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 钉钉API失败_FAILED告警_不外抛() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP2_USER));
        stubMultiLevelChain(List.of("dd200", "dd300"));
        stubOriginatorDd();
        mockNoExistingInstance();
        when(apiClient.createProcessInstance(anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenThrow(new DingTalkApiException("errcode=40"));
        when(receiptItemMapper.selectList(any())).thenReturn(List.of());
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        assertDoesNotThrow(() ->
                service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L)));

        ArgumentCaptor<ApprovalInstance> captor = ArgumentCaptor.forClass(ApprovalInstance.class);
        verify(approvalInstanceMapper).insert(captor.capture());
        assertEquals(ApprovalInstance.SYNC_FAILED, captor.getValue().getSyncStatus());
        verify(notificationService).notify(eq(999L), eq(NotificationType.DINGTALK_SYNC_ALERT),
                contains("errcode=40"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 已SYNCED_幂等跳过() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP2_USER));
        ApprovalInstance existing = new ApprovalInstance();
        existing.setBizType(ApprovalInstance.BIZ_RECEIVE);
        existing.setBizId(1L);
        existing.setSyncStatus(ApprovalInstance.SYNC_SYNCED);
        when(approvalInstanceMapper.selectOne(any())).thenReturn(existing);

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        verifyNoInteractions(apiClient);
        verify(approvalInstanceMapper, never()).insert(any(ApprovalInstance.class));
    }

    // ------------------------------------------------------------ 成功路径与审批人节点

    @Test
    void 两级不同人_两个顺序节点_成功回填() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP2_USER));
        stubMultiLevelChain(List.of("dd200", "dd300"));
        stubOriginatorDd();
        mockNoExistingInstance();
        when(receiptItemMapper.selectList(any())).thenReturn(List.of());
        when(apiClient.createProcessInstance(eq("PROC-R"), eq("dd100"), anyList(), anyList(), anyList()))
                .thenReturn("inst-1");

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> approvers = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> cc = ArgumentCaptor.forClass(List.class);
        verify(apiClient).createProcessInstance(eq("PROC-R"), eq("dd100"),
                approvers.capture(), cc.capture(), anyList());
        assertEquals(List.of("dd200", "dd300"), approvers.getValue());
        assertEquals(List.of("dd100"), cc.getValue());

        ArgumentCaptor<ApprovalInstance> recordCaptor = ArgumentCaptor.forClass(ApprovalInstance.class);
        verify(approvalInstanceMapper).insert(recordCaptor.capture());
        assertEquals(ApprovalInstance.SYNC_SYNCED, recordCaptor.getValue().getSyncStatus());
        assertEquals("inst-1", recordCaptor.getValue().getProcessInstanceId());
        // 回填单据 dingtalk_instance_id
        verify(receiptMapper).update(isNull(), any());
    }

    @Test
    void 两级同人_合并单节点() {
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP1_USER));
        stubMultiLevelChain(List.of("dd200"));
        stubOriginatorDd();
        mockNoExistingInstance();
        when(receiptItemMapper.selectList(any())).thenReturn(List.of());
        when(apiClient.createProcessInstance(eq("PROC-R"), eq("dd100"), anyList(), anyList(), anyList()))
                .thenReturn("inst-2");

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> approvers = ArgumentCaptor.forClass(List.class);
        verify(apiClient).createProcessInstance(eq("PROC-R"), eq("dd100"),
                approvers.capture(), anyList(), anyList());
        assertEquals(List.of("dd200"), approvers.getValue());
    }

    @Test
    void 多级主管链_完整节点序列推送() {
        // 钉钉节点 = 固定一级 → 直接主管 → 部门主管 → …（站内快照只记前两级）
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(STEP1_USER, STEP2_USER));
        stubMultiLevelChain(List.of("dd200", "dd300", "dd400"));
        stubOriginatorDd();
        mockNoExistingInstance();
        when(receiptItemMapper.selectList(any())).thenReturn(List.of());
        when(apiClient.createProcessInstance(eq("PROC-R"), eq("dd100"), anyList(), anyList(), anyList()))
                .thenReturn("inst-4");

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> approvers = ArgumentCaptor.forClass(List.class);
        verify(apiClient).createProcessInstance(eq("PROC-R"), eq("dd100"),
                approvers.capture(), anyList(), anyList());
        assertEquals(List.of("dd200", "dd300", "dd400"), approvers.getValue());
    }

    @Test
    void 表单字段_与模板强契约一致() {
        ReceiveReceipt receipt = pendingReceipt(STEP1_USER, STEP2_USER);
        receipt.setLocationId(10L);
        when(receiptMapper.selectById(1L)).thenReturn(receipt);
        stubMultiLevelChain(List.of("dd200", "dd300"));
        stubOriginatorDd();
        mockNoExistingInstance();

        ReceiveReceiptItem item = new ReceiveReceiptItem();
        item.setReceiptId(1L);
        item.setAssetId(55L);
        when(receiptItemMapper.selectList(any())).thenReturn(List.of(item));
        Asset asset = new Asset();
        asset.setId(55L);
        asset.setBarcode("SKSCDM-20260821-0001");
        asset.setName("镀膜机");
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(asset));
        Location location = new Location();
        location.setId(10L);
        location.setName("一号车间");
        when(locationMapper.selectById(10L)).thenReturn(location);
        when(apiClient.createProcessInstance(anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn("inst-3");

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_RECEIPT, 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, String>>> form = ArgumentCaptor.forClass(List.class);
        verify(apiClient).createProcessInstance(anyString(), anyString(), anyList(), anyList(), form.capture());
        List<Map<String, String>> fields = form.getValue();
        assertEquals(5, fields.size());
        assertEquals("单据编号", fields.get(0).get("name"));
        assertEquals("ARE202608310001", fields.get(0).get("value"));
        assertEquals("资产编号", fields.get(1).get("name"));
        assertEquals("SKSCDM-20260821-0001", fields.get(1).get("value"));
        assertEquals("资产名称", fields.get(2).get("name"));
        assertEquals("镀膜机", fields.get(2).get("value"));
        assertEquals("领用区域", fields.get(3).get("name"));
        assertEquals("一号车间", fields.get(3).get("value"));
        assertEquals("事由", fields.get(4).get("name"));
        assertEquals("测试领用", fields.get(4).get("value"));
    }

    // ------------------------------------------------------------ 调拨 / 变更：共享池保持站内

    @Test
    void 调拨无调入人_保持站内审批() {
        TransferOrder order = new TransferOrder();
        order.setId(5L);
        order.setStatus("PENDING");
        order.setToUserId(null);
        when(transferOrderMapper.selectById(5L)).thenReturn(order);

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_TRANSFER, 5L));

        verifyNoInteractions(apiClient, approvalInstanceMapper);
    }

    @Test
    void 变更无指定处理人_保持站内审批() {
        ChangeOrder order = new ChangeOrder();
        order.setId(6L);
        order.setStatus("PENDING");
        order.setAssigneeUserId(null);
        when(changeOrderMapper.selectById(6L)).thenReturn(order);

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_CHANGE, 6L));

        verifyNoInteractions(apiClient, approvalInstanceMapper);
    }

    @Test
    void 调拨成功_回填dingtalkInstanceId() {
        TransferOrder order = new TransferOrder();
        order.setId(5L);
        order.setStatus("PENDING");
        order.setApplicantUserId(APPLICANT);
        order.setToUserId(400L);
        order.setToUserName("赵六");
        when(transferOrderMapper.selectById(5L)).thenReturn(order);
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of(APPLICANT, "dd100", 400L, "dd400"));
        mockNoExistingInstance();
        when(transferOrderItemMapper.selectList(any())).thenReturn(List.of());
        when(apiClient.createProcessInstance(eq("PROC-T"), eq("dd100"), anyList(), anyList(), anyList()))
                .thenReturn("inst-t1");

        service.onSyncRequested(new OaSyncRequestedEvent(OaSyncRequestedEvent.KIND_TRANSFER, 5L));

        verify(transferOrderMapper).update(isNull(), any());
        verify(approvalInstanceMapper).insert(any(ApprovalInstance.class));
    }

    // ------------------------------------------------------------ B5 双向同步：站内审批 → 代执行钉钉待办

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private com.fasterxml.jackson.databind.JsonNode instanceDetail(String status, String taskStatus,
                                                                   String taskUserDd, long taskId) {
        String json = "{\"status\":\"" + status + "\",\"tasks\":[{"
                + "\"taskid\":\"" + taskId + "\",\"userid\":\"" + taskUserDd + "\","
                + "\"task_status\":\"" + taskStatus + "\"}]}";
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 代执行成功_审批人有待办任务() {
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of(STEP1_USER, "dd200"));
        when(apiClient.getProcessInstance("inst-x")).thenReturn(
                instanceDetail("RUNNING", "RUNNING", "dd200", 103538324808L));

        service.onTaskExecuteRequested(new OaTaskExecuteRequestedEvent("inst-x", STEP1_USER, "agree", null));

        verify(apiClient).executeApprovalTask("inst-x", 103538324808L, "dd200", "agree", null);
        verify(notificationService, never()).notify(any(), any(NotificationType.class), anyString(), any(), any());
    }

    @Test
    void 钉钉实例已终态_跳过代执行() {
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of(STEP1_USER, "dd200"));
        when(apiClient.getProcessInstance("inst-x")).thenReturn(
                instanceDetail("COMPLETED", "COMPLETED", "dd200", 103538324808L));

        service.onTaskExecuteRequested(new OaTaskExecuteRequestedEvent("inst-x", STEP1_USER, "agree", null));

        verify(apiClient, never()).executeApprovalTask(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString(), anyString(), any());
    }

    @Test
    void 审批人无待办任务_已在钉钉操作_跳过() {
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of(STEP1_USER, "dd200"));
        when(apiClient.getProcessInstance("inst-x")).thenReturn(
                instanceDetail("RUNNING", "COMPLETED", "dd200", 103538324808L));

        service.onTaskExecuteRequested(new OaTaskExecuteRequestedEvent("inst-x", STEP1_USER, "agree", null));

        verify(apiClient, never()).executeApprovalTask(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString(), anyString(), any());
    }

    @Test
    void 审批人未绑定钉钉_跳过() {
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of());

        service.onTaskExecuteRequested(new OaTaskExecuteRequestedEvent("inst-x", STEP1_USER, "agree", null));

        verifyNoInteractions(apiClient);
    }

    @Test
    void 代执行API失败_告警不抛() {
        when(userDirectory.ddUserIdsByIds(any())).thenReturn(Map.of(STEP1_USER, "dd200"));
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));
        when(apiClient.getProcessInstance("inst-x")).thenReturn(
                instanceDetail("RUNNING", "RUNNING", "dd200", 103538324808L));
        org.mockito.Mockito.doThrow(new DingTalkApiException("钉钉代执行审批失败（aflowProcessInstStatusException）"))
                .when(apiClient).executeApprovalTask(eq("inst-x"), eq(103538324808L),
                        eq("dd200"), eq("refuse"), any());

        service.onTaskExecuteRequested(new OaTaskExecuteRequestedEvent("inst-x", STEP1_USER, "refuse", "站内拒绝：测试"));

        verify(notificationService).notify(eq(999L), eq(NotificationType.DINGTALK_SYNC_ALERT),
                contains("同步钉钉待办失败"), any(), any());
    }
}
