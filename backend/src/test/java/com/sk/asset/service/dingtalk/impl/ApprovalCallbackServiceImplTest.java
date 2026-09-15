package com.sk.asset.service.dingtalk.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dingtalk.client.DingTalkApiClient;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.entity.dingtalk.DingtalkEventLog;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import com.sk.asset.mapper.dingtalk.DingtalkEventLogMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.change.ChangeOrderService;
import com.sk.asset.service.notification.NotificationService;
import com.sk.asset.service.receipt.ReceiveReceiptService;
import com.sk.asset.service.transfer.TransferOrderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉钉审批事件回调测试（M10 入口 B 回传链路）。
 *
 * <p>覆盖：task/instance 事件路由、两级链逐级推进、终态兜底、撤销分发、
 * 操作人越权/不可解析降级、corpId 串扰防护、双端并发幂等（业务 409 吞掉）、
 * 基础设施异常告警不外抛（Stream 恒 ACK 语义）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApprovalCallbackServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalInstance.class);
    }

    private static final String INSTANCE_ID = "inst-001";
    private static final String CORP_ID = "corpA";
    private static final Long STEP1_USER = 200L;
    private static final String STEP1_NAME = "李四";
    private static final Long STEP2_USER = 300L;
    private static final String STEP2_NAME = "王五";

    @Mock
    private DingTalkApiClient apiClient;
    @Mock
    private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock
    private DingtalkEventLogMapper eventLogMapper;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReceiveReceiptMapper receiptMapper;
    @Mock
    private TransferOrderMapper transferOrderMapper;
    @Mock
    private ChangeOrderMapper changeOrderMapper;
    @Mock
    private ReceiveReceiptService receiveReceiptService;
    @Mock
    private TransferOrderService transferOrderService;
    @Mock
    private ChangeOrderService changeOrderService;
    @Mock
    private com.sk.asset.service.dingtalk.InboundApprovalImportService importService;

    private DingtalkProperties props;
    private ApprovalCallbackServiceImpl service;

    @BeforeEach
    void setUp() {
        props = new DingtalkProperties();
        props.setCorpId(CORP_ID);
        service = new ApprovalCallbackServiceImpl(props, apiClient, approvalInstanceMapper,
                eventLogMapper, userDirectory, notificationService, receiptMapper,
                transferOrderMapper, changeOrderMapper, receiveReceiptService,
                transferOrderService, changeOrderService, importService);
    }

    // ------------------------------------------------------------ 事件构造

    private String taskEvent(String result, String staffId, String corpId) {
        return """
                {"type":"finish","processInstanceId":"%s","staffId":"%s","result":"%s","corpId":"%s"}
                """.formatted(INSTANCE_ID, staffId, result, corpId);
    }

    private String instanceEvent(String type, String result, String staffId, String corpId) {
        return """
                {"type":"%s","processInstanceId":"%s","result":"%s","staffId":"%s","corpId":"%s"}
                """.formatted(type, INSTANCE_ID, result, staffId, corpId);
    }

    private ApprovalInstance record(String bizType, Long bizId) {
        ApprovalInstance r = new ApprovalInstance();
        r.setBizType(bizType);
        r.setBizId(bizId);
        r.setProcessInstanceId(INSTANCE_ID);
        r.setSyncStatus(ApprovalInstance.SYNC_SYNCED);
        r.setStatus("RUNNING");
        return r;
    }

    private ReceiveReceipt pendingReceipt(Integer step) {
        ReceiveReceipt r = new ReceiveReceipt();
        r.setId(1L);
        r.setStatus("PENDING");
        r.setApprovalStep(step);
        r.setApprovalStep1UserId(STEP1_USER);
        r.setApprovalStep1Name(STEP1_NAME);
        r.setApprovalStep2UserId(STEP2_USER);
        r.setApprovalStep2Name(STEP2_NAME);
        return r;
    }

    private void mockOperator(String staffId, Long userId, String name) {
        when(userDirectory.findByDdUserId(staffId)).thenReturn(new UserResp(userId, "SK" + userId, name, "IT"));
    }

    /** 钉钉实例详情（status/result）——task 二级同意关单前回查终态用 */
    private com.fasterxml.jackson.databind.node.ObjectNode detail(String status, String result) throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode)
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree("{\"title\":\"IT资产领用单\",\"status\":\"" + status
                                + "\",\"result\":\"" + result + "\"}");
    }

    /** 钉钉实例详情（含抄送人 cc_userids）——终态抄送通知用 */
    private com.fasterxml.jackson.databind.node.ObjectNode detailWithCc(
            String status, String result, String... ccIds) throws Exception {
        String cc = String.join("\",\"", ccIds);
        return (com.fasterxml.jackson.databind.node.ObjectNode)
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree("{\"title\":\"IT资产领用单\",\"status\":\"" + status
                                + "\",\"result\":\"" + result
                                + "\",\"cc_userids\":[\"" + cc + "\"]}");
    }

    // ------------------------------------------------------------ task_change：逐级推进

    @Test
    void 钉钉发起实例_入口B导入后事件继续处理() {
        // 本地无映射 → importService 导入建映射 → 事件按既有状态机推进（task:finish agree）
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null, record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(importService.tryImport(any())).thenAnswer(inv -> record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(1));
        mockOperator("dd200", STEP1_USER, STEP1_NAME);

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID));

        verify(importService).tryImport(any());
        verify(receiveReceiptService).approve(1L, STEP1_USER, STEP1_NAME);
    }

    @Test
    void 钉钉发起实例_导入失败_按未知实例记日志() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);
        when(importService.tryImport(any())).thenReturn(null);

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("start", "", "", CORP_ID));

        verify(importService).tryImport(any());
        verifyNoInteractions(receiveReceiptService);
        // 导入失败不告警重试（importService 内部已告警），也不外抛
    }

    @Test
    void taskAgree_一级审批_推进到二级() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(1));
        mockOperator("dd200", STEP1_USER, STEP1_NAME);

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID));

        verify(receiveReceiptService).approve(1L, STEP1_USER, STEP1_NAME);
        verify(approvalInstanceMapper).updateById(any(ApprovalInstance.class));
    }

    @Test
    void taskAgree_一级阶段非快照审批人_记日志忽略() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(1));
        mockOperator("dd999", 999L, "快照外人");

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd999", CORP_ID));

        // 不推进、不落回调审计（记日志即可）
        verify(receiveReceiptService, never()).approve(anyLong(), anyLong(), anyString());
        verify(approvalInstanceMapper, never()).updateById(any(ApprovalInstance.class));
    }

    @Test
    void taskAgree_二级终态() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail("COMPLETED", "agree"));
        mockOperator("dd300", STEP2_USER, STEP2_NAME);

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd300", CORP_ID));

        verify(receiveReceiptService).approve(1L, STEP2_USER, STEP2_NAME);
    }

    // ------------------------------------------------------------ task_change：多级主管模板终态同步

    @Test
    void taskAgree_二级同意_实例仍在审批_不关单等终审() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        // 快照二级（直接主管）已同意，但连续多级主管的第 2/3 级未审完：实例仍 RUNNING
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail("RUNNING", ""));
        mockOperator("dd300", STEP2_USER, STEP2_NAME);

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd300", CORP_ID));

        // 单据终态必须与钉钉实例同步：不提前关单，等 instance:finish 终审事件
        verify(receiveReceiptService, never()).approve(anyLong(), anyLong(), anyString());
    }

    @Test
    void taskAgree_快照外后续主管同意_实例未终审_仅记日志不落库() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail("RUNNING", ""));
        mockOperator("dd999", 999L, "第2级主管");

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd999", CORP_ID));

        // 第 2/3 级主管同意：记日志即可，不推进不落库
        verify(receiveReceiptService, never()).approve(anyLong(), anyLong(), anyString());
        verify(approvalInstanceMapper, never()).updateById(any(ApprovalInstance.class));
    }

    @Test
    void taskAgree_快照外后续主管同意_实例恰已终审_自愈关单() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        // 终审 instance:finish 事件丢失场景：最后一级主管 task:agree 到达时实例已 COMPLETED
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail("COMPLETED", "agree"));
        mockOperator("dd999", 999L, "第3级主管");

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd999", CORP_ID));

        // 以快照二级审批人身份过链关单（实际审批过程以钉钉侧记录为准）
        verify(receiveReceiptService).approve(1L, STEP2_USER, STEP2_NAME);
    }

    @Test
    void taskAgree_二级同意_实例详情查询失败_保守不关单() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenThrow(new RuntimeException("dingtalk api down"));
        mockOperator("dd300", STEP2_USER, STEP2_NAME);

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd300", CORP_ID));

        verify(receiveReceiptService, never()).approve(anyLong(), anyLong(), anyString());
    }

    @Test
    void taskRefuse_任一级拒绝() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_BORROW, 1L));
        mockOperator("dd200", STEP1_USER, STEP1_NAME);

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("refuse", "dd200", CORP_ID));

        verify(receiveReceiptService).reject(eq(1L), eq("钉钉审批拒绝"), eq(STEP1_USER), eq(STEP1_NAME));
    }

    @Test
    void taskAgree_调拨单路由到confirm() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_TRANSFER, 5L));
        mockOperator("dd400", 400L, "赵六");

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd400", CORP_ID));

        verify(transferOrderService).confirm(5L, 400L, "赵六");
    }

    @Test
    void taskAgree_变更单路由到confirm() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_CHANGE, 6L));
        mockOperator("dd400", 400L, "赵六");

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd400", CORP_ID));

        verify(changeOrderService).confirm(6L, 400L, "赵六");
    }

    @Test
    void taskChange_操作人不可解析_告警不落地() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(userDirectory.findByDdUserId("dd-unknown")).thenReturn(null);
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd-unknown", CORP_ID));

        verify(notificationService).notify(eq(999L), eq(NotificationType.DINGTALK_SYNC_ALERT),
                contains("操作人无法解析"), eq("DINGTALK"), eq(0L));
        verifyNoInteractions(receiveReceiptService);
    }

    // ------------------------------------------------------------ instance_change：终态兜底

    @Test
    void instanceFinishAgree_step1_按快照连续推进两级() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        // 第一次查：step=1；一级 approve 推进后重查：step=2 仍 PENDING（模拟 approve 内部推进）
        ReceiveReceipt step1 = pendingReceipt(1);
        ReceiveReceipt step2 = pendingReceipt(2);
        when(receiptMapper.selectById(1L)).thenReturn(step1, step2);

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "agree", "", CORP_ID));

        verify(receiveReceiptService).approve(1L, STEP1_USER, STEP1_NAME);
        verify(receiveReceiptService).approve(1L, STEP2_USER, STEP2_NAME);
    }

    @Test
    void instanceFinishAgree_已被task事件处理_幂等跳过() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        ReceiveReceipt approved = pendingReceipt(2);
        approved.setStatus("APPROVED");
        when(receiptMapper.selectById(1L)).thenReturn(approved);

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "agree", "", CORP_ID));

        verify(receiveReceiptService, never()).approve(anyLong(), anyLong(), anyString());
    }

    @Test
    void instanceFinishRefuse_操作人兜底当前step快照() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "refuse", "", CORP_ID));

        verify(receiveReceiptService).reject(eq(1L), eq("钉钉审批拒绝（终审）"), eq(STEP2_USER), eq(STEP2_NAME));
    }

    @Test
    void instanceFinishAgree_调拨单兜底confirm() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_TRANSFER, 5L));
        TransferOrder order = new TransferOrder();
        order.setId(5L);
        order.setStatus("PENDING");
        order.setToUserId(400L);
        order.setToUserName("赵六");
        when(transferOrderMapper.selectById(5L)).thenReturn(order);

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "agree", "", CORP_ID));

        verify(transferOrderService).confirm(5L, 400L, "赵六");
    }

    @Test
    void instanceFinishAgree_兜底被业务拦截_终态仍落库() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_TRANSFER, 5L));
        TransferOrder order = new TransferOrder();
        order.setId(5L);
        order.setStatus("PENDING");
        order.setToUserId(762L);
        order.setToUserName("王五");
        when(transferOrderMapper.selectById(5L)).thenReturn(order);
        // 调拨发起人=调入人：兜底 confirm 被业务校验拦截（403）
        when(transferOrderService.confirm(5L, 762L, "王五"))
                .thenThrow(new BusinessException(403, "调入方确认/拒绝不能由发起人自己操作"));

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "agree", "", CORP_ID));

        // 钉钉侧已终审：approval_instance 终态必须落库（业务推进交由 task:finish 事件或人工介入）
        verify(approvalInstanceMapper).updateById(argThat((ApprovalInstance r) ->
                "COMPLETED".equals(r.getStatus()) && "agree".equals(r.getResult())));
    }

    // ------------------------------------------------------------ 终态抄送（"抄送我的"）

    @Test
    void instanceFinishAgree_抄送人逐一站内通知DOC_CC() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        ReceiveReceipt approved = pendingReceipt(2);
        approved.setStatus("APPROVED");
        when(receiptMapper.selectById(1L)).thenReturn(approved);
        // 模板抄送人：李四（已绑定）+ 未绑定者
        when(apiClient.getProcessInstance(INSTANCE_ID))
                .thenReturn(detailWithCc("COMPLETED", "agree", "dd691", "dd-unknown"));
        mockOperator("dd691", 691L, "李四");

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "agree", "", CORP_ID));

        // 绑定者收到 DOC_CC；未绑定者跳过；标题含实例标题与终态
        verify(notificationService).notify(eq(691L), eq(NotificationType.DOC_CC),
                eq("【抄送】IT资产领用单（已通过）"), eq("RECEIVE"), eq(1L));
    }

    @Test
    void instanceFinishRefuse_抄送人收到已拒绝通知() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        when(apiClient.getProcessInstance(INSTANCE_ID))
                .thenReturn(detailWithCc("COMPLETED", "refuse", "dd691"));
        mockOperator("dd691", 691L, "李四");

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "refuse", "", CORP_ID));

        verify(notificationService).notify(eq(691L), eq(NotificationType.DOC_CC),
                contains("已拒绝"), eq("RECEIVE"), eq(1L));
    }

    @Test
    void instanceTerminate_抄送人收到已撤销通知() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(1));
        when(apiClient.getProcessInstance(INSTANCE_ID))
                .thenReturn(detailWithCc("TERMINATED", "", "dd691"));
        mockOperator("dd100", 100L, "张三");
        mockOperator("dd691", 691L, "李四");

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("terminate", "", "dd100", CORP_ID));

        verify(notificationService).notify(eq(691L), eq(NotificationType.DOC_CC),
                contains("已撤销"), eq("RECEIVE"), eq(1L));
    }

    @Test
    void instanceFinish_无抄送人_不通知() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        ReceiveReceipt approved = pendingReceipt(2);
        approved.setStatus("APPROVED");
        when(receiptMapper.selectById(1L)).thenReturn(approved);
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail("COMPLETED", "agree"));

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "agree", "", CORP_ID));

        verify(notificationService, never()).notify(anyLong(),
                eq(NotificationType.DOC_CC), anyString(), anyString(), anyLong());
    }

    @Test
    void instanceFinish_抄送详情查询失败_不影响主流程() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        ReceiveReceipt approved = pendingReceipt(2);
        approved.setStatus("APPROVED");
        when(receiptMapper.selectById(1L)).thenReturn(approved);
        // 抄送回查抛异常：终态落库不受影响，仅记日志
        when(apiClient.getProcessInstance(INSTANCE_ID))
                .thenThrow(new RuntimeException("dingtalk api down"));

        assertDoesNotThrow(() ->
                service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("finish", "agree", "", CORP_ID)));
        verify(notificationService, never()).notify(anyLong(),
                eq(NotificationType.DOC_CC), anyString(), anyString(), anyLong());
    }

    // ------------------------------------------------------------ instance_change：撤销

    @Test
    void instanceTerminate_领用单_以当前step快照审批人身份拒绝() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(1));
        mockOperator("dd100", 100L, "张三");

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("terminate", "", "dd100", CORP_ID));

        verify(receiveReceiptService).reject(eq(1L), contains("钉钉撤销"), eq(STEP1_USER), eq(STEP1_NAME));
    }

    @Test
    void instanceTerminate_调拨单_cancel() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_TRANSFER, 5L));
        TransferOrder order = new TransferOrder();
        order.setId(5L);
        order.setApplicantUserId(100L);
        when(transferOrderMapper.selectById(5L)).thenReturn(order);

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("terminate", "", "dd100", CORP_ID));

        verify(transferOrderService).cancel(5L, 100L);
    }

    @Test
    void instanceTerminate_变更单_cancel() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_CHANGE, 6L));
        ChangeOrder order = new ChangeOrder();
        order.setId(6L);
        order.setApplicantUserId(100L);
        when(changeOrderMapper.selectById(6L)).thenReturn(order);

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("terminate", "", "dd100", CORP_ID));

        verify(changeOrderService).cancel(6L, 100L);
    }

    @Test
    void instanceStart_终态不回退() {
        ApprovalInstance completed = record(ApprovalInstance.BIZ_RECEIVE, 1L);
        completed.setStatus("COMPLETED");
        when(approvalInstanceMapper.selectOne(any())).thenReturn(completed);

        service.onEvent("evt-inst-x", "bpms_instance_change", instanceEvent("start", "", "", CORP_ID));

        // 终态后收到乱序 start：不改 RUNNING、不落库
        verify(approvalInstanceMapper, never()).updateById(any(ApprovalInstance.class));
    }

    // ------------------------------------------------------------ 防护与幂等

    @Test
    void corpId不匹配_忽略() {
        service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd200", "corpB"));

        verifyNoInteractions(approvalInstanceMapper, receiveReceiptService, notificationService);
    }

    @Test
    void 非系统发起实例_仅记日志不异常() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(null);

        assertDoesNotThrow(() ->
                service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID)));
        verifyNoInteractions(receiveReceiptService);
    }

    @Test
    void 双端并发_业务409被吞掉_不告警() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail("COMPLETED", "agree"));
        mockOperator("dd300", STEP2_USER, STEP2_NAME);
        // 站内已审批，钉钉事件后到 → 状态机 409 拦截，事件按幂等忽略
        when(receiveReceiptService.approve(1L, STEP2_USER, STEP2_NAME))
                .thenThrow(new BusinessException(409, "单据已审批"));

        assertDoesNotThrow(() ->
                service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd300", CORP_ID)));
        verifyNoInteractions(notificationService);
    }

    @Test
    void 基础设施异常_告警不外抛() {
        when(approvalInstanceMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));

        assertDoesNotThrow(() ->
                service.onEvent("evt-task-x", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID)));
        verify(notificationService).notify(eq(999L), eq(NotificationType.DINGTALK_SYNC_ALERT),
                contains("db down"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 空事件数据_直接忽略() {
        assertDoesNotThrow(() -> service.onEvent("evt-task-x", "bpms_task_change", "{}"));
        verifyNoInteractions(approvalInstanceMapper);
    }

    // ------------------------------------------------------------ 事件落表（先落库后处理）

    /** 模拟真实 insert：回填自增 id，使 markEvent 状态回写可被观测 */
    private void mockInsertFillsId() {
        when(eventLogMapper.insert(any(DingtalkEventLog.class))).thenAnswer(inv -> {
            inv.getArgument(0, DingtalkEventLog.class).setId(1L);
            return 1;
        });
    }

    @Test
    void 事件先落库_处理成功_回写PROCESSED() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(1));
        mockOperator("dd200", STEP1_USER, STEP1_NAME);
        mockInsertFillsId();

        service.onEvent("evt-new-1", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID));

        // 落库行含去重键与实例索引字段（同一对象引用会被 markEvent 复用改状态，status 不断言在 insert 上）
        verify(eventLogMapper).insert(argThat((DingtalkEventLog e) ->
                "evt-new-1".equals(e.getEventId())
                        && "bpms_task_change".equals(e.getEventType())
                        && INSTANCE_ID.equals(e.getProcessInstanceId())));
        verify(receiveReceiptService).approve(1L, STEP1_USER, STEP1_NAME);
        verify(eventLogMapper).updateById(argThat((DingtalkEventLog e) ->
                DingtalkEventLog.STATUS_PROCESSED.equals(e.getStatus())));
    }

    @Test
    void 重复推送_eventId唯一键冲突_忽略不处理() {
        // uk_dingtalk_event_id 冲突 = 钉钉重复推送（重启后内存去重失效场景的持久化兜底）
        when(eventLogMapper.insert(any(DingtalkEventLog.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_dingtalk_event_id"));

        service.onEvent("evt-dup-1", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID));

        verify(eventLogMapper, never()).updateById(any(DingtalkEventLog.class));
        verifyNoInteractions(approvalInstanceMapper, receiveReceiptService);
    }

    @Test
    void 业务校验拦截_回写IGNORED() throws Exception {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(2));
        when(apiClient.getProcessInstance(INSTANCE_ID)).thenReturn(detail("COMPLETED", "agree"));
        mockOperator("dd300", STEP2_USER, STEP2_NAME);
        // 站内已审批，钉钉事件后到 → 状态机 409 幂等拦截
        when(receiveReceiptService.approve(1L, STEP2_USER, STEP2_NAME))
                .thenThrow(new BusinessException(409, "单据已审批"));
        mockInsertFillsId();

        service.onEvent("evt-conflict-1", "bpms_task_change", taskEvent("agree", "dd300", CORP_ID));

        verify(eventLogMapper).updateById(argThat((DingtalkEventLog e) ->
                DingtalkEventLog.STATUS_IGNORED.equals(e.getStatus()) && e.getError() != null));
        verifyNoInteractions(notificationService);
    }

    @Test
    void 基础设施异常_回写FAILED() {
        when(approvalInstanceMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
        when(userDirectory.userIdsByRole("systemAdmin")).thenReturn(List.of(999L));
        mockInsertFillsId();

        service.onEvent("evt-fail-1", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID));

        // FAILED 留痕（重启后可按状态查滞留事件），且告警不外抛
        verify(eventLogMapper).updateById(argThat((DingtalkEventLog e) ->
                DingtalkEventLog.STATUS_FAILED.equals(e.getStatus())
                        && "db down".equals(e.getError())));
        verify(notificationService).notify(eq(999L), eq(NotificationType.DINGTALK_SYNC_ALERT),
                contains("db down"), eq("DINGTALK"), eq(0L));
    }

    @Test
    void 事件落库失败_降级继续处理() {
        when(approvalInstanceMapper.selectOne(any())).thenReturn(record(ApprovalInstance.BIZ_RECEIVE, 1L));
        when(receiptMapper.selectById(1L)).thenReturn(pendingReceipt(1));
        mockOperator("dd200", STEP1_USER, STEP1_NAME);
        // 日志表写入失败不能阻断审批回传（内存锁 + 状态机幂等仍兜底）
        when(eventLogMapper.insert(any(DingtalkEventLog.class)))
                .thenThrow(new RuntimeException("event log table down"));

        assertDoesNotThrow(() ->
                service.onEvent("evt-degrade-1", "bpms_task_change", taskEvent("agree", "dd200", CORP_ID)));

        verify(receiveReceiptService).approve(1L, STEP1_USER, STEP1_NAME);
    }

    @Test
    void 非审批事件类型_不落表() {
        service.onEvent("evt-other-1", "user_add_org", "{\"userid\":\"dd001\"}");

        verifyNoInteractions(eventLogMapper, approvalInstanceMapper);
    }
}
