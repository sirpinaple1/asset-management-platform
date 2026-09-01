package com.sk.asset.service.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dingtalk.client.DingTalkApiClient;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import com.sk.asset.service.approval.ApprovalChainResolver;
import com.sk.asset.service.notification.NotificationService;
import com.sk.asset.service.receipt.ReceiveReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 入口 B（钉钉发起实例导入）单测：表单解析、审批人以钉钉 tasks 为准、
 * 幂等重入、失败告警不外抛。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InboundApprovalImportServiceTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper 列名解析需要 TableInfo 缓存，手动初始化
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.sk.asset.entity.dingtalk.ApprovalInstance.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.sk.asset.entity.asset.Asset.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.sk.asset.entity.basedata.Location.class);
    }

    private static final String INSTANCE_ID = "inst-b-001";
    private static final String RECEIVE_CODE = "PROC-RECEIVE-001";
    private static final String TRANSFER_CODE = "PROC-TRANSFER-001";
    private static final String CHANGE_CODE = "PROC-CHANGE-001";
    private static final String ORIGINATOR = "dd-originator";
    private static final String APPROVER1 = "dd-approver1";
    private static final String APPROVER2 = "dd-approver2";

    @Mock
    private DingTalkApiClient apiClient;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock
    private AssetMapper assetMapper;
    @Mock
    private LocationMapper locationMapper;
    @Mock
    private ReceiveReceiptService receiveReceiptService;
    @Mock
    private com.sk.asset.service.transfer.TransferOrderService transferOrderService;
    @Mock
    private com.sk.asset.service.change.ChangeOrderService changeOrderService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.sk.asset.mapper.receipt.ReceiveReceiptMapper receiptMapper;
    @Mock
    private com.sk.asset.mapper.transfer.TransferOrderMapper transferOrderMapper;
    @Mock
    private com.sk.asset.mapper.change.ChangeOrderMapper changeOrderMapper;

    private DingtalkProperties props;
    private InboundApprovalImportService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        props = new DingtalkProperties();
        props.setCorpId("corpA");
        props.getProcessCodes().putAll(Map.of(
                "receive", RECEIVE_CODE, "borrow", "PROC-BORROW-001",
                "transfer", TRANSFER_CODE, "change", CHANGE_CODE));
        service = new InboundApprovalImportService(props, apiClient, userDirectory,
                approvalInstanceMapper, assetMapper, locationMapper,
                receiveReceiptService, transferOrderService, changeOrderService,
                notificationService, receiptMapper, transferOrderMapper, changeOrderMapper);
    }

    // ------------------------------------------------------------ 事件/详情构造

    private String eventData(String processCode) {
        return """
                {"processCode":"%s","processInstanceId":"%s","corpId":"corpA"}
                """.formatted(processCode, INSTANCE_ID);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode instanceDetail(
            String originator, List<String> taskUserids) throws Exception {
        return instanceDetail(originator, taskUserids, """
                {"name":"单据编号","value":"X"},
                    {"name":"资产编号","value":"SKBGDN374"},
                    {"name":"领用区域","value":"IT部在用仓"},
                    {"name":"事由","value":"钉钉发起测试"}
                """);
    }

    /** 自定义表单字段（form_component_values 数组内容）的实例详情 */
    private com.fasterxml.jackson.databind.node.ObjectNode instanceDetail(
            String originator, List<String> taskUserids, String formFieldsJson) throws Exception {
        String tasks = taskUserids.stream()
                .map(id -> "{\"userid\":\"" + id + "\",\"task_status\":\"RUNNING\"}")
                .reduce((a, b) -> a + "," + b).orElse("");
        String json = """
                {
                  "title":"钉钉发起的审批",
                  "originator_userid":"%s",
                  "form_component_values":[%s],
                  "tasks":[%s]
                }
                """.formatted(originator, formFieldsJson.trim(), tasks);
        return (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(json);
    }

    private UserResp user(long id, String name) {
        return new UserResp(id, "SK" + id, name, "综合管理部/IT科");
    }

    private void mockHappyPath(String taskUserid1, String taskUserid2) throws Exception {
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(
                instanceDetail(ORIGINATOR, taskUserid2 == null
                        ? List.of(taskUserid1) : List.of(taskUserid1, taskUserid2)));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(taskUserid1)).thenReturn(user(200L, "李四"));
        if (taskUserid2 != null) {
            when(userDirectory.findByDdUserId(taskUserid2)).thenReturn(user(300L, "王五"));
        }
        Asset asset = new Asset();
        asset.setId(10L);
        asset.setBarcode("SKBGDN374");
        when(assetMapper.selectList(any())).thenReturn(List.of(asset));
        Location location = new Location();
        location.setId(20L);
        location.setName("IT部在用仓");
        when(locationMapper.selectOne(any())).thenReturn(location);
        ReceiveReceipt receipt = new ReceiveReceipt();
        receipt.setId(30L);
        receipt.setSerialNo("ARE202608310099");
        when(receiveReceiptService.createFromDingtalk(any(), eq(100L), eq("张三"), any()))
                .thenReturn(receipt);
    }

    // ------------------------------------------------------------ 正常导入

    @Test
    void 两级审批人_以钉钉tasks为准_冻结进快照() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        mockHappyPath(APPROVER1, APPROVER2);

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertNotNull(result);
        assertEquals(30L, result.getBizId());
        assertEquals(ApprovalInstance.BIZ_RECEIVE, result.getBizType());
        assertEquals(ApprovalInstance.SYNC_SYNCED, result.getSyncStatus());
        assertEquals(INSTANCE_ID, result.getProcessInstanceId());
        assertEquals(ORIGINATOR, result.getOriginatorDdUserId());
        verify(approvalInstanceMapper).insert(any(ApprovalInstance.class));
        // 回填单据 dingtalk_instance_id（与入口 A 同步后回填对齐）
        verify(receiptMapper).update(any(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
        verifyNoInteractions(transferOrderMapper, changeOrderMapper);
        ArgumentCaptor<ReceiptApplyReq> reqCaptor = ArgumentCaptor.forClass(ReceiptApplyReq.class);
        ArgumentCaptor<ApprovalChainResolver.ResolvedChain> chainCaptor =
                ArgumentCaptor.forClass(ApprovalChainResolver.ResolvedChain.class);
        verify(receiveReceiptService).createFromDingtalk(reqCaptor.capture(), eq(100L), eq("张三"),
                chainCaptor.capture());
        ReceiptApplyReq req = reqCaptor.getValue();
        assertEquals("RECEIVE", req.getType());
        assertEquals(List.of(10L), req.getAssetIds());
        assertEquals(20L, req.getLocationId());
        assertEquals("钉钉发起测试", req.getReason());
        assertEquals("综合管理部/IT科", req.getDepartment());
        ApprovalChainResolver.ResolvedChain chain = chainCaptor.getValue();
        assertEquals(200L, chain.getStep1UserId());
        assertEquals("李四", chain.getStep1Name());
        assertEquals(300L, chain.getStep2UserId());
        assertEquals("王五", chain.getStep2Name());
        assertFalse(chain.isMerged());
    }

    @Test
    void 单审批任务_合并单语义() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        mockHappyPath(APPROVER1, null);

        service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        ArgumentCaptor<ApprovalChainResolver.ResolvedChain> chainCaptor =
                ArgumentCaptor.forClass(ApprovalChainResolver.ResolvedChain.class);
        verify(receiveReceiptService).createFromDingtalk(any(), eq(100L), eq("张三"), chainCaptor.capture());
        ApprovalChainResolver.ResolvedChain chain = chainCaptor.getValue();
        assertEquals(200L, chain.getStep1UserId());
        assertEquals(200L, chain.getStep2UserId());
        assertTrue(chain.isMerged());
    }

    @Test
    void 幂等_已有映射直接返回() throws Exception {
        ApprovalInstance existing = new ApprovalInstance();
        existing.setId(9L);
        existing.setProcessInstanceId(INSTANCE_ID);
        when(approvalInstanceMapper.selectOne(any())).thenReturn(existing);

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertEquals(9L, result.getId());
        verifyNoInteractions(apiClient, receiveReceiptService);
    }

    // ------------------------------------------------------------ 终态落地（导入晚于钉钉终审）

    @Test
    void 实例已终审agree_导入后两级链直接落终态() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        mockHappyPath(APPROVER1, APPROVER2);
        // 覆盖详情：钉钉侧已 COMPLETED + agree（终态事件早于导入成功已消费）
        com.fasterxml.jackson.databind.node.ObjectNode detail =
                instanceDetail(ORIGINATOR, List.of(APPROVER1, APPROVER2));
        detail.put("status", "COMPLETED");
        detail.put("result", "agree");
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail);
        // 建单后单据 step=1；一级 approve 后 step=2 仍 PENDING → 二级再推进
        ReceiveReceipt step1 = new ReceiveReceipt();
        step1.setId(30L);
        step1.setApprovalStep(1);
        step1.setApprovalStep1UserId(200L);
        step1.setApprovalStep1Name("李四");
        step1.setApprovalStep2UserId(300L);
        step1.setApprovalStep2Name("王五");
        step1.setStatus("PENDING");
        ReceiveReceipt step2 = new ReceiveReceipt();
        step2.setId(30L);
        step2.setApprovalStep(2);
        step2.setApprovalStep1UserId(200L);
        step2.setApprovalStep1Name("李四");
        step2.setApprovalStep2UserId(300L);
        step2.setApprovalStep2Name("王五");
        step2.setStatus("PENDING");
        when(receiptMapper.selectById(30L)).thenReturn(step1, step2);

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertEquals("COMPLETED", result.getStatus());
        verify(receiveReceiptService).approve(30L, 200L, "李四");
        verify(receiveReceiptService).approve(30L, 300L, "王五");
    }

    @Test
    void 实例已终审agree_调拨导入后直接confirm() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(
                transferDetailFinal("COMPLETED", "agree"));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(APPROVER1)).thenReturn(user(200L, "李四"));
        Asset asset = new Asset();
        asset.setId(10L);
        asset.setBarcode("SKBGDN374");
        when(assetMapper.selectList(any())).thenReturn(List.of(asset));
        Location location = new Location();
        location.setId(20L);
        location.setName("IT部在用仓");
        when(locationMapper.selectOne(any())).thenReturn(location);
        com.sk.asset.entity.transfer.TransferOrder order = new com.sk.asset.entity.transfer.TransferOrder();
        order.setId(40L);
        order.setSerialNo("ATR202608310040");
        order.setToUserId(200L);
        order.setToUserName("李四");
        when(transferOrderService.createFromDingtalk(any(), eq(100L), eq("张三"))).thenReturn(order);
        when(transferOrderMapper.selectById(40L)).thenReturn(order);

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(TRANSFER_CODE)));

        assertEquals("COMPLETED", result.getStatus());
        verify(transferOrderService).confirm(40L, 200L, "李四");
    }

    /** 调拨详情（已终态）：status/result 由参数指定 */
    private com.fasterxml.jackson.databind.node.ObjectNode transferDetailFinal(
            String status, String result) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode detail = instanceDetail(
                ORIGINATOR, List.of(APPROVER1), """
                {"name":"单据编号","value":"X"},
                {"name":"资产编号","value":"SKBGDN374"},
                {"name":"调入区域","value":"IT部在用仓"},
                {"name":"事由","value":"钉钉发起调拨"}
                """);
        detail.put("status", status);
        detail.put("result", result);
        return detail;
    }

    // ------------------------------------------------------------ 调拨导入（审批人 = 调入方 toUserId）

    @Test
    void 调拨导入_审批人以钉钉tasks为准_作为调入方() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(instanceDetail(
                ORIGINATOR, List.of(APPROVER1), """
                {"name":"单据编号","value":"X"},
                {"name":"资产编号","value":"SKBGDN374"},
                {"name":"调出区域","value":"A区"},
                {"name":"调入区域","value":"IT部在用仓"},
                {"name":"调入部门","value":"生产一部"},
                {"name":"目标使用人","value":"李四"},
                {"name":"事由","value":"钉钉发起调拨"}
                """));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(APPROVER1)).thenReturn(user(200L, "李四"));
        Asset asset = new Asset();
        asset.setId(10L);
        asset.setBarcode("SKBGDN374");
        when(assetMapper.selectList(any())).thenReturn(List.of(asset));
        Location location = new Location();
        location.setId(20L);
        location.setName("IT部在用仓");
        when(locationMapper.selectOne(any())).thenReturn(location);
        com.sk.asset.entity.transfer.TransferOrder order = new com.sk.asset.entity.transfer.TransferOrder();
        order.setId(40L);
        order.setSerialNo("ATR202608310040");
        when(transferOrderService.createFromDingtalk(any(), eq(100L), eq("张三"))).thenReturn(order);

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(TRANSFER_CODE)));

        assertNotNull(result);
        assertEquals(ApprovalInstance.BIZ_TRANSFER, result.getBizType());
        assertEquals(40L, result.getBizId());
        assertEquals(ApprovalInstance.SYNC_SYNCED, result.getSyncStatus());
        ArgumentCaptor<com.sk.asset.dto.transfer.TransferApplyReq> reqCaptor =
                ArgumentCaptor.forClass(com.sk.asset.dto.transfer.TransferApplyReq.class);
        verify(transferOrderService).createFromDingtalk(reqCaptor.capture(), eq(100L), eq("张三"));
        com.sk.asset.dto.transfer.TransferApplyReq req = reqCaptor.getValue();
        assertEquals(List.of(10L), req.getAssetIds());
        assertEquals(20L, req.getToLocationId());
        assertEquals("生产一部", req.getToDepartment());
        // 审批人以钉钉 tasks 为准：toUserId = tasks 审批人（非表单"目标使用人"解析）
        assertEquals(200L, req.getToUserId());
        assertEquals("李四", req.getToUserName());
        assertEquals("钉钉发起调拨", req.getReason());
    }

    // ------------------------------------------------------------ 变更导入（审批人 = 处理人 assignee）

    @Test
    void 变更导入_审批人以钉钉tasks为准_作为处理人() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(instanceDetail(
                ORIGINATOR, List.of(APPROVER1), """
                {"name":"单据编号","value":"X"},
                {"name":"资产编号","value":"SKBGDN374"},
                {"name":"变更后使用人","value":"赵七"},
                {"name":"变更后位置","value":"IT部在用仓"},
                {"name":"变更原因","value":"钉钉发起变更"}
                """));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(APPROVER1)).thenReturn(user(200L, "李四"));
        when(userDirectory.findByDdUserId("赵七")).thenReturn(null); // 表单值为姓名而非钉钉 userid
        com.sk.asset.common.PageResp<UserResp> page = new com.sk.asset.common.PageResp<>();
        UserResp newUser = new UserResp(400L, "SK400", "赵七", "生产二部");
        page.setRecords(List.of(newUser));
        when(userDirectory.search("赵七", 1, 100)).thenReturn(page);
        Asset asset = new Asset();
        asset.setId(10L);
        asset.setBarcode("SKBGDN374");
        when(assetMapper.selectList(any())).thenReturn(List.of(asset));
        Location location = new Location();
        location.setId(20L);
        location.setName("IT部在用仓");
        when(locationMapper.selectOne(any())).thenReturn(location);
        com.sk.asset.entity.change.ChangeOrder order = new com.sk.asset.entity.change.ChangeOrder();
        order.setId(50L);
        order.setSerialNo("AOC202608310050");
        when(changeOrderService.createFromDingtalk(any(), eq(100L), eq("张三"))).thenReturn(order);

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(CHANGE_CODE)));

        assertNotNull(result);
        assertEquals(ApprovalInstance.BIZ_CHANGE, result.getBizType());
        assertEquals(50L, result.getBizId());
        ArgumentCaptor<com.sk.asset.dto.change.ChangeApplyReq> reqCaptor =
                ArgumentCaptor.forClass(com.sk.asset.dto.change.ChangeApplyReq.class);
        verify(changeOrderService).createFromDingtalk(reqCaptor.capture(), eq(100L), eq("张三"));
        com.sk.asset.dto.change.ChangeApplyReq req = reqCaptor.getValue();
        assertEquals(List.of(10L), req.getAssetIds());
        // 变更后使用人姓名精确匹配解析
        assertEquals(400L, req.getNewUserId());
        assertEquals("赵七", req.getNewUserName());
        assertEquals("生产二部", req.getNewUserDepartment());
        assertEquals(20L, req.getNewLocationId());
        assertEquals("钉钉发起变更", req.getReason());
        // 审批人以钉钉 tasks 为准：assigneeUserId = tasks 审批人
        assertEquals(200L, req.getAssigneeUserId());
    }

    @Test
    void 变更导入_表单使用人不存在_告警不导入() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(instanceDetail(
                ORIGINATOR, List.of(APPROVER1), """
                {"name":"单据编号","value":"X"},
                {"name":"资产编号","value":"SKBGDN374"},
                {"name":"变更后使用人","value":"无此人"},
                {"name":"变更后位置","value":"IT部在用仓"},
                {"name":"变更原因","value":"钉钉发起变更"}
                """));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(APPROVER1)).thenReturn(user(200L, "李四"));
        when(userDirectory.findByDdUserId("无此人")).thenReturn(null);
        com.sk.asset.common.PageResp<UserResp> empty = new com.sk.asset.common.PageResp<>();
        empty.setRecords(List.of());
        when(userDirectory.search("无此人", 1, 100)).thenReturn(empty);
        Asset okAsset = new Asset();
        okAsset.setId(10L);
        okAsset.setBarcode("SKBGDN374");
        when(assetMapper.selectList(any())).thenReturn(List.of(okAsset));
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(CHANGE_CODE)));

        assertNull(result);
        verifyNoInteractions(changeOrderService);
        verify(notificationService).notify(eq(999L), any(), contains("无此人"), eq("DINGTALK"), eq(0L));
    }

    // ------------------------------------------------------------ 失败语义（告警不外抛）

    @Test
    void 审批人未绑定系统用户_拒绝导入_告警() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(
                instanceDetail(ORIGINATOR, List.of(APPROVER1)));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(APPROVER1)).thenReturn(null);
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertNull(result);
        verifyNoInteractions(receiveReceiptService);
        verify(notificationService).notify(eq(999L), any(), contains("审批人未绑定"), eq("DINGTALK"), eq(0L));
        // 发起人本人也收到失败通知（闭环反馈，可自行处理）
        verify(notificationService).notify(eq(100L), any(), contains("未同步到资产系统"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 资产编号不存在_导入失败_告警() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(
                instanceDetail(ORIGINATOR, List.of(APPROVER1)));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(APPROVER1)).thenReturn(user(200L, "李四"));
        when(assetMapper.selectList(any())).thenReturn(List.of()); // 查无此资产
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertNull(result);
        verify(notificationService).notify(eq(999L), any(), contains("资产编号"), eq("DINGTALK"), eq(0L));
        verify(notificationService).notify(eq(100L), any(), contains("未同步到资产系统"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 区域名不存在_相近仓位提示_通知发起人() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(
                instanceDetail(ORIGINATOR, List.of(APPROVER1), """
                {"name":"单据编号","value":"X"},
                {"name":"资产编号","value":"SKBGDN374"},
                {"name":"领用区域","value":"IT仓位"},
                {"name":"事由","value":"钉钉发起测试"}
                """));
        when(userDirectory.findByDdUserId(ORIGINATOR)).thenReturn(user(100L, "张三"));
        when(userDirectory.findByDdUserId(APPROVER1)).thenReturn(user(200L, "李四"));
        Asset asset = new Asset();
        asset.setId(10L);
        asset.setBarcode("SKBGDN374");
        when(assetMapper.selectList(any())).thenReturn(List.of(asset));
        when(locationMapper.selectOne(any())).thenReturn(null); // 区域名对不上
        Location cand1 = new Location();
        cand1.setId(3L);
        cand1.setName("IT部在用仓");
        Location cand2 = new Location();
        cand2.setId(4L);
        cand2.setName("IT部闲置仓");
        when(locationMapper.selectList(any())).thenReturn(List.of(cand1, cand2));
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertNull(result);
        verifyNoInteractions(receiveReceiptService);
        // 管理员告警含相近仓位提示
        verify(notificationService).notify(eq(999L), any(),
                contains("IT仓位"), eq("DINGTALK"), eq(0L));
        // 发起人本人收到含相近仓位提示的失败通知
        verify(notificationService).notify(eq(100L), any(),
                contains("IT部在用仓"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 自审实例_审批人等于申请人_拒绝导入_通知发起人() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        // 钉钉允许自审：发起人 dd-originator 同时也是审批人 dd-approver1
        mockHappyPath(ORIGINATOR, null);
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertNull(result);
        verifyNoInteractions(receiveReceiptService);
        verify(notificationService).notify(eq(999L), any(), contains("自审"), eq("DINGTALK"), eq(0L));
        verify(notificationService).notify(eq(200L), any(), contains("调整审批人"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 业务校验拦截_如资产被占用_告警人工补建() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        mockHappyPath(APPROVER1, null);
        when(receiveReceiptService.createFromDingtalk(any(), anyLong(), any(), any()))
                .thenThrow(new BusinessException(409, "资产已有待审批的领用/借用单"));
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        ApprovalInstance result = service.tryImport(objectMapper.readTree(eventData(RECEIVE_CODE)));

        assertNull(result);
        verify(notificationService).notify(eq(999L), any(), contains("人工建单"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 非配置模板_不导入() throws Exception {
        ApprovalInstance result = service.tryImport(
                objectMapper.readTree(eventData("PROC-OTHER-999")));

        assertNull(result);
        verifyNoInteractions(apiClient, receiveReceiptService, transferOrderService,
                changeOrderService, notificationService);
    }
}
