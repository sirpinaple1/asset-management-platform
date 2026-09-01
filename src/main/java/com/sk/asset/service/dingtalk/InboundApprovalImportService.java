package com.sk.asset.service.dingtalk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.BusinessException;
import com.sk.asset.common.PageResp;
import com.sk.asset.dingtalk.client.DingTalkApiClient;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dto.change.ChangeApplyReq;
import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import com.sk.asset.service.approval.ApprovalChainResolver;
import com.sk.asset.service.change.ChangeOrderService;
import com.sk.asset.service.notification.NotificationService;
import com.sk.asset.service.receipt.ReceiveReceiptService;
import com.sk.asset.service.transfer.TransferOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 入口 B：钉钉原生表单发起的审批实例导入为系统单据（M10 2026-08-31）。
 *
 * <p>触发：回调链路收到本地无映射的实例事件（instance/task），且 processCode 命中
 * 配置模板时，回查实例详情自动建单。审批人<b>以钉钉实例 tasks 为准</b>
 * （模板自带/钉钉侧改派的审批人直接冻结进单据，不走站内解析）：
 * 领用/借用 → approval_step1/2 快照（入口 A 同构两级链）；
 * 调拨 → toUserId（入口 A 契约：钉钉审批人 = 调入方）；
 * 变更 → assigneeUserId（入口 A 契约：钉钉审批人 = 处理人）。
 * 系统建单后回传事件按既有状态机推进，操作人校验即命中钉钉侧审批人。</p>
 *
 * <p>失败语义：表单解析失败 / 资产或区域查不到 / 审批人未绑定内部用户 / 业务校验
 * （资产被占用等 400/409）→ 告警 systemAdmin + 放弃导入（返回 null，事件按未知实例
 * 记日志）；钉钉侧审批继续不受影响，系统侧人工补建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundApprovalImportService {

    private final DingtalkProperties props;
    private final DingTalkApiClient apiClient;
    private final UserDirectory userDirectory;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final AssetMapper assetMapper;
    private final LocationMapper locationMapper;
    private final ReceiveReceiptService receiveReceiptService;
    private final TransferOrderService transferOrderService;
    private final ChangeOrderService changeOrderService;
    private final NotificationService notificationService;
    private final com.sk.asset.mapper.receipt.ReceiveReceiptMapper receiptMapper;
    private final com.sk.asset.mapper.transfer.TransferOrderMapper transferOrderMapper;
    private final com.sk.asset.mapper.change.ChangeOrderMapper changeOrderMapper;

    /** 钉钉模板 → 业务类型（与入口 A 推送契约一一对应） */
    private static final String TEMPLATE_BIZ_RECEIVE = "receive";
    private static final String TEMPLATE_BIZ_BORROW = "borrow";
    private static final String TEMPLATE_BIZ_TRANSFER = "transfer";
    private static final String TEMPLATE_BIZ_CHANGE = "change";

    /**
     * 尝试导入钉钉发起的实例（幂等：已有映射直接返回既有记录）。
     *
     * @return 新建或既有的 ApprovalInstance；非已配置模板或导入失败返回 null
     */
    public ApprovalInstance tryImport(JsonNode eventData) {
        String processCode = eventData.path("processCode").asText("");
        String instanceId = eventData.path("processInstanceId").asText("");
        if (instanceId.isBlank()) {
            return null;
        }
        // 幂等：已导入（或事件乱序重入）直接返回
        ApprovalInstance existing = approvalInstanceMapper.selectOne(
                new LambdaQueryWrapper<ApprovalInstance>()
                        .eq(ApprovalInstance::getProcessInstanceId, instanceId));
        if (existing != null) {
            return existing;
        }

        String template = matchTemplate(processCode);
        if (template == null) {
            log.info("非已配置模板的钉钉发起实例，入口 B 暂不导入（processCode={}）", processCode);
            return null;
        }

        // 先解析详情与发起人：后续导入失败时才能通知发起人本人
        JsonNode detail;
        UserResp applicant;
        try {
            detail = requireDetail(instanceId);
            // 发起人未绑定系统用户时无法站内通知本人，仅告警管理员
            applicant = resolveOriginator(detail, instanceId);
        } catch (BusinessException e) {
            log.warn("钉钉发起实例基础信息解析失败（instanceId={}）：code={}, message={}",
                    instanceId, e.getCode(), e.getMessage());
            alertAdmins("钉钉发起的审批单导入失败（" + e.getMessage() + "），请在系统内人工建单"
                    + "（钉钉实例 " + instanceId + "）");
            return null;
        }

        try {
            return switch (template) {
                case TEMPLATE_BIZ_RECEIVE -> importReceipt(detail, applicant, TEMPLATE_BIZ_RECEIVE, "RECEIVE", instanceId);
                case TEMPLATE_BIZ_BORROW -> importReceipt(detail, applicant, TEMPLATE_BIZ_BORROW, "BORROW", instanceId);
                case TEMPLATE_BIZ_TRANSFER -> importTransfer(detail, applicant, instanceId);
                case TEMPLATE_BIZ_CHANGE -> importChange(detail, applicant, instanceId);
                default -> null;
            };
        } catch (BusinessException e) {
            // 业务校验拦截（资产占用 409 / 字段缺失 400）：告警人工补建 + 通知发起人修正重发
            log.warn("钉钉发起实例导入被业务校验拦截（instanceId={}）：code={}, message={}",
                    instanceId, e.getCode(), e.getMessage());
            alertAdmins("钉钉发起的审批单导入失败（" + e.getMessage() + "），请在系统内人工建单"
                    + "（钉钉实例 " + instanceId + "）");
            notifyOriginator(applicant, "你提交的钉钉审批单（" + detail.path("title").asText("审批单")
                    + "）未同步到资产系统：" + e.getMessage() + "。请修正后重新发起。");
            return null;
        } catch (Exception e) {
            log.error("钉钉发起实例导入异常（instanceId={}）：{}", instanceId, e.getMessage(), e);
            alertAdmins("钉钉发起的审批单导入异常：" + e.getMessage() + "（钉钉实例 " + instanceId + "）");
            notifyOriginator(applicant, "你提交的钉钉审批单（" + detail.path("title").asText("审批单")
                    + "）同步资产系统异常：" + e.getMessage() + "。请联系管理员处理。");
            return null;
        }
    }

    private String matchTemplate(String processCode) {
        var codes = props.getProcessCodes();
        for (String key : List.of(TEMPLATE_BIZ_RECEIVE, TEMPLATE_BIZ_BORROW,
                TEMPLATE_BIZ_TRANSFER, TEMPLATE_BIZ_CHANGE)) {
            String code = codes.get(key);
            if (code != null && !code.isBlank() && code.equals(processCode)) {
                return key;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ 领用 / 借用（两级审批链）

    private ApprovalInstance importReceipt(JsonNode detail, UserResp applicant,
                                           String templateKey, String receiptType, String instanceId) {
        // 审批人快照以钉钉 tasks 为准（两级 AND 节点同人合并）
        ApprovalChainResolver.ResolvedChain chain = resolveApproversFromTasks(detail, instanceId);
        // 系统红线"审批人与申请人不能是同一人"（approve 时 403）——导入前预检：
        // 钉钉允许自审实例，若放行会建出永远无法推进的卡死单（回传事件全部 403）
        if (Objects.equals(chain.getStep1UserId(), applicant.id())
                || Objects.equals(chain.getStep2UserId(), applicant.id())) {
            throw new BusinessException(400, "审批人与申请人不能是同一人（钉钉实例为自审，"
                    + "请在钉钉模板中调整审批人后重新发起，instanceId=" + instanceId + "）");
        }

        ReceiptApplyReq req = new ReceiptApplyReq();
        req.setType(receiptType);
        req.setAssetIds(resolveAssetIds(formValue(detail, "资产编号"), instanceId));
        req.setLocationId(resolveLocationId(formValue(detail, "领用区域"), "领用区域", true));
        req.setDepartment(applicant.dept() == null || applicant.dept().isBlank()
                ? "未提供" : applicant.dept());
        String reason = formValue(detail, "事由");
        req.setReason(reason == null || reason.isBlank() ? "钉钉发起（未填事由）" : reason);

        ReceiveReceipt receipt = receiveReceiptService.createFromDingtalk(
                req, applicant.id(), applicant.name(), chain);
        return insertRecord(bizTypeOf(templateKey), templateKey,
                receipt.getId(), receipt.getSerialNo(), detail, instanceId);
    }

    // ------------------------------------------------------------ 调拨（审批人 = 调入方 toUserId）

    private ApprovalInstance importTransfer(JsonNode detail, UserResp applicant, String instanceId) {
        // 入口 A 契约：钉钉审批人 = 调入方 → toUserId 以钉钉 tasks 为准（表单"目标使用人"不覆盖）
        UserResp approver = resolveFirstApprover(detail, instanceId);

        TransferApplyReq req = new TransferApplyReq();
        req.setAssetIds(resolveAssetIds(formValue(detail, "资产编号"), instanceId));
        req.setToLocationId(resolveLocationId(formValue(detail, "调入区域"), "调入区域", false));
        String toDept = formValue(detail, "调入部门");
        req.setToDepartment(toDept == null || toDept.isBlank() ? null : toDept.trim());
        req.setToUserId(approver.id());
        req.setToUserName(approver.name());
        String reason = formValue(detail, "事由");
        req.setReason(reason == null || reason.isBlank() ? "钉钉发起（未填事由）" : reason);

        TransferOrder order = transferOrderService.createFromDingtalk(
                req, applicant.id(), applicant.name());
        return insertRecord(ApprovalInstance.BIZ_TRANSFER, TEMPLATE_BIZ_TRANSFER,
                order.getId(), order.getSerialNo(), detail, instanceId);
    }

    // ------------------------------------------------------------ 变更（审批人 = 处理人 assignee）

    private ApprovalInstance importChange(JsonNode detail, UserResp applicant, String instanceId) {
        // 入口 A 契约：钉钉审批人 = 处理人 → assigneeUserId 以钉钉 tasks 为准
        UserResp approver = resolveFirstApprover(detail, instanceId);

        ChangeApplyReq req = new ChangeApplyReq();
        req.setAssetIds(resolveAssetIds(formValue(detail, "资产编号"), instanceId));
        UserResp newUser = resolveUserRef(formValue(detail, "变更后使用人"), instanceId);
        if (newUser != null) {
            req.setNewUserId(newUser.id());
            req.setNewUserName(newUser.name());
            req.setNewUserDepartment(newUser.dept());
        }
        req.setNewLocationId(resolveLocationId(formValue(detail, "变更后位置"), "变更后位置", false));
        String reason = formValue(detail, "变更原因");
        req.setReason(reason == null || reason.isBlank() ? "钉钉发起（未填原因）" : reason);
        req.setAssigneeUserId(approver.id());

        ChangeOrder order = changeOrderService.createFromDingtalk(
                req, applicant.id(), applicant.name());
        return insertRecord(ApprovalInstance.BIZ_CHANGE, TEMPLATE_BIZ_CHANGE,
                order.getId(), order.getSerialNo(), detail, instanceId);
    }

    // ------------------------------------------------------------ 共用解析

    private JsonNode requireDetail(String instanceId) {
        JsonNode detail = apiClient.getProcessInstance(instanceId);
        if (detail == null || detail.isEmpty() || !detail.has("form_component_values")) {
            throw new BusinessException(400, "钉钉实例详情查询为空（instanceId=" + instanceId + "）");
        }
        return detail;
    }

    private UserResp resolveOriginator(JsonNode detail, String instanceId) {
        UserResp applicant = userDirectory.findByDdUserId(detail.path("originator_userid").asText(""));
        if (applicant == null) {
            throw new BusinessException(400, "发起人未绑定系统用户（钉钉 originator="
                    + detail.path("originator_userid").asText() + "，请先执行钉钉 userid 同步）");
        }
        return applicant;
    }

    /**
     * 领用/借用审批人快照（M10 决策：钉钉创建的单据自带审批人时系统不覆盖）。
     * 单 task / 双 task 同人 → 合并单（一次审批终态）；双 task 不同人 → 两级链。
     * 任一审批人 userid 无法反查内部用户 → 400（无法回传推进，宁可不导入）。
     */
    private ApprovalChainResolver.ResolvedChain resolveApproversFromTasks(JsonNode detail, String instanceId) {
        List<String> ddUserIds = new ArrayList<>();
        for (JsonNode task : detail.path("tasks")) {
            String userid = task.path("userid").asText("");
            if (!userid.isBlank()) {
                ddUserIds.add(userid);
            }
        }
        if (ddUserIds.isEmpty()) {
            throw new BusinessException(400, "钉钉实例无审批任务（可能模板未配审批节点，instanceId="
                    + instanceId + "）");
        }
        // 去重保序：两级 AND 节点同人合并为一人
        List<String> distinct = ddUserIds.stream().distinct().toList();
        UserResp step1 = userDirectory.findByDdUserId(distinct.get(0));
        UserResp step2 = distinct.size() > 1
                ? userDirectory.findByDdUserId(distinct.get(1)) : step1;
        if (step1 == null || step2 == null) {
            throw new BusinessException(400, "钉钉审批人未绑定系统用户（ddUserIds=" + distinct
                    + "，请先执行钉钉 userid 同步）");
        }
        return new ApprovalChainResolver.ResolvedChain(
                step1.id(), displayName(step1), "dingtalk",
                step2.id(), displayName(step2), "dingtalk",
                distinct.size() == 1 || Objects.equals(step1.id(), step2.id()));
    }

    /**
     * 调拨/变更审批人（单审批人契约：入口 A 推送单审批人节点）。
     * 取首个 task 审批人；模板多节点时以第一人为准并告警提示核对。
     */
    private UserResp resolveFirstApprover(JsonNode detail, String instanceId) {
        List<String> ddUserIds = new ArrayList<>();
        for (JsonNode task : detail.path("tasks")) {
            String userid = task.path("userid").asText("");
            if (!userid.isBlank()) {
                ddUserIds.add(userid);
            }
        }
        if (ddUserIds.isEmpty()) {
            throw new BusinessException(400, "钉钉实例无审批任务（可能模板未配审批节点，instanceId="
                    + instanceId + "）");
        }
        List<String> distinct = ddUserIds.stream().distinct().toList();
        if (distinct.size() > 1) {
            log.warn("钉钉调拨/变更实例含多个审批人，入口 B 以第一人为准（instanceId={}, approvers={}）",
                    instanceId, distinct);
        }
        UserResp approver = userDirectory.findByDdUserId(distinct.get(0));
        if (approver == null) {
            throw new BusinessException(400, "钉钉审批人未绑定系统用户（ddUserId=" + distinct.get(0)
                    + "，请先执行钉钉 userid 同步）");
        }
        return approver;
    }

    /**
     * 表单使用人字段值 → 内部用户。兼容两种取值：钉钉 userid（人员组件）或姓名（文本组件）。
     * 姓名精确匹配唯一才采信；未命中/重名 → 400（宁可不导入，人工补建）。
     */
    private UserResp resolveUserRef(String value, String instanceId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        UserResp byDdId = userDirectory.findByDdUserId(v);
        if (byDdId != null) {
            return byDdId;
        }
        PageResp<UserResp> page = userDirectory.search(v, 1, 100);
        List<UserResp> exact = page.getRecords() == null ? List.of()
                : page.getRecords().stream().filter(u -> v.equals(u.name())).toList();
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            throw new BusinessException(400, "钉钉表单使用人姓名在系统中重名，无法确定（" + v
                    + "，instanceId=" + instanceId + "）");
        }
        throw new BusinessException(400, "钉钉表单使用人在系统中不存在（" + v
                + "，instanceId=" + instanceId + "）");
    }

    /** 表单字段值（入口 A 写入契约的中文 component name） */
    private String formValue(JsonNode detail, String fieldName) {
        for (JsonNode f : detail.path("form_component_values")) {
            if (fieldName.equals(f.path("name").asText(""))) {
                String v = f.path("value").asText("");
                return "null".equals(v) ? null : v;
            }
        }
        return null;
    }

    /** 资产编号（逗号分隔多值，入口 A joinBarcodes 契约）→ 资产 id 列表 */
    private List<Long> resolveAssetIds(String barcodesJoined, String instanceId) {
        if (barcodesJoined == null || barcodesJoined.isBlank()) {
            throw new BusinessException(400, "钉钉表单缺少资产编号（instanceId=" + instanceId + "）");
        }
        List<String> codes = Arrays.stream(barcodesJoined.split("[,，、]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (codes.isEmpty()) {
            throw new BusinessException(400, "钉钉表单资产编号为空（instanceId=" + instanceId + "）");
        }
        List<Asset> assets = assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .in(Asset::getBarcode, codes));
        if (assets.size() != codes.size()) {
            List<String> found = assets.stream().map(Asset::getBarcode).toList();
            String missing = codes.stream().filter(c -> !found.contains(c))
                    .reduce((a, b) -> a + "、" + b).orElse("");
            throw new BusinessException(400, "钉钉表单资产编号在系统中不存在：" + missing);
        }
        return assets.stream().map(Asset::getId).toList();
    }

    /** 区域名称 → 位置 id（required=false 时值为空返回 null，交给建单校验兜底） */
    private Long resolveLocationId(String locationName, String fieldName, boolean required) {
        if (locationName == null || locationName.isBlank()) {
            if (required) {
                throw new BusinessException(400, "钉钉表单缺少" + fieldName);
            }
            return null;
        }
        String keyword = locationName.trim();
        Location location = locationMapper.selectOne(new LambdaQueryWrapper<Location>()
                .eq(Location::getName, keyword));
        if (location == null) {
            // 相近名称提示（包含匹配 + 前2字前缀匹配）：手打区域名与系统仓位对不上时给修正线索
            String prefix = keyword.length() >= 2 ? keyword.substring(0, 2) : keyword;
            List<Location> candidates = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                    .like(Location::getName, keyword)
                    .or().likeRight(Location::getName, prefix));
            String hint = candidates.stream().map(Location::getName).distinct().limit(3)
                    .reduce((a, b) -> a + "、" + b)
                    .map(s -> "（系统相近仓位：" + s + "，请按系统仓位名重新发起）")
                    .orElse("（可在系统位置管理页查看全部仓位名）");
            throw new BusinessException(400, "钉钉表单" + fieldName + "在系统中不存在：" + keyword + hint);
        }
        return location.getId();
    }

    /** 落映射记录（sync_status=SYNCED：实例已存在于钉钉侧，非本系统推送） */
    private ApprovalInstance insertRecord(String bizType, String templateKey, Long bizId,
                                          String serialNo, JsonNode detail, String instanceId) {
        ApprovalInstance record = new ApprovalInstance();
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setProcessCode(props.getProcessCodes().get(templateKey));
        record.setProcessInstanceId(instanceId);
        record.setTitle(detail.path("title").asText(""));
        record.setOriginatorDdUserId(detail.path("originator_userid").asText(""));
        record.setSyncStatus(ApprovalInstance.SYNC_SYNCED);
        record.setStatus(detailStatusOf(detail));
        approvalInstanceMapper.insert(record);
        // 回填单据 dingtalk_instance_id（与入口 A 同步后回填对齐，审批中心区分站内/钉钉通道展示）
        backfillInstanceId(bizType, bizId, instanceId);
        log.info("入口 B 导入完成：钉钉实例 {} → 系统单据 {}（bizType={}, 申请人={}, 审批人以钉钉为准）",
                instanceId, serialNo, bizType, detail.path("originator_userid").asText(""));
        // 实例可能已在钉钉侧终审（导入触发晚于终态事件消费）：不等后续事件，直接落终态
        settleIfFinal(bizType, bizId, detail);
        return record;
    }

    /** 详情状态 → 映射记录状态（RUNNING 等待后续事件按状态机推进） */
    private String detailStatusOf(JsonNode detail) {
        String status = detail.path("status").asText("");
        return "COMPLETED".equals(status) || "TERMINATED".equals(status) ? status : "RUNNING";
    }

    /** 回填单据 dingtalk_instance_id（单据源自钉钉，创建即已知实例号） */
    private void backfillInstanceId(String bizType, Long bizId, String instanceId) {
        switch (bizType) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW ->
                    receiptMapper.update(null, new LambdaUpdateWrapper<ReceiveReceipt>()
                            .eq(ReceiveReceipt::getId, bizId)
                            .set(ReceiveReceipt::getDingtalkInstanceId, instanceId));
            case ApprovalInstance.BIZ_TRANSFER ->
                    transferOrderMapper.update(null, new LambdaUpdateWrapper<TransferOrder>()
                            .eq(TransferOrder::getId, bizId)
                            .set(TransferOrder::getDingtalkInstanceId, instanceId));
            case ApprovalInstance.BIZ_CHANGE ->
                    changeOrderMapper.update(null, new LambdaUpdateWrapper<ChangeOrder>()
                            .eq(ChangeOrder::getId, bizId)
                            .set(ChangeOrder::getDingtalkInstanceId, instanceId));
            default -> log.debug("忽略实例号回填（bizType={}）", bizType);
        }
    }

    /**
     * 详情已终态时直接推进单据（事件乱序/迟到场景的兜底；单据刚建必然 PENDING，
     * 与回调层终审兜底逻辑幂等互备——后到的事件会被单据状态机 409 拦截）。
     */
    private void settleIfFinal(String bizType, Long bizId, JsonNode detail) {
        String status = detail.path("status").asText("");
        String result = detail.path("result").asText("");
        try {
            if ("COMPLETED".equals(status) && "agree".equals(result)) {
                applyFinalAgree(bizType, bizId);
            } else if ("COMPLETED".equals(status) && "refuse".equals(result)) {
                applyFinalRefuse(bizType, bizId, "钉钉审批拒绝（导入时已终审）");
            } else if ("TERMINATED".equals(status)) {
                applyFinalTerminate(bizType, bizId, "发起人在钉钉撤销（导入时已终止）");
            }
        } catch (BusinessException e) {
            // 终态记录已落库；业务推进被拦截（如调拨自确认限制）交人工处理
            log.warn("入口 B 终态落地被业务校验拦截（bizType={}, bizId={}）：code={}, message={}",
                    bizType, bizId, e.getCode(), e.getMessage());
            alertAdmins("钉钉导入单据终态落地被拦截（" + e.getMessage() + "），请在系统内处理（"
                    + bizType + "#" + bizId + "）");
        }
    }

    private void applyFinalAgree(String bizType, Long bizId) {
        switch (bizType) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW -> {
                ReceiveReceipt receipt = receiptMapper.selectById(bizId);
                if (receipt == null) {
                    return;
                }
                if (Integer.valueOf(1).equals(receipt.getApprovalStep())) {
                    receiveReceiptService.approve(bizId,
                            receipt.getApprovalStep1UserId(), receipt.getApprovalStep1Name());
                }
                ReceiveReceipt latest = receiptMapper.selectById(bizId);
                if (latest != null && com.sk.asset.enums.receipt.ReceiptStatus.PENDING.name()
                        .equals(latest.getStatus())) {
                    receiveReceiptService.approve(bizId,
                            latest.getApprovalStep2UserId(), latest.getApprovalStep2Name());
                }
            }
            case ApprovalInstance.BIZ_TRANSFER -> {
                TransferOrder order = transferOrderMapper.selectById(bizId);
                if (order != null) {
                    transferOrderService.confirm(bizId, order.getToUserId(), order.getToUserName());
                }
            }
            case ApprovalInstance.BIZ_CHANGE -> {
                ChangeOrder order = changeOrderMapper.selectById(bizId);
                if (order != null) {
                    changeOrderService.confirm(bizId, order.getAssigneeUserId(), order.getReason());
                }
            }
            default -> log.debug("忽略终审落地（bizType={}）", bizType);
        }
    }

    private void applyFinalRefuse(String bizType, Long bizId, String reason) {
        switch (bizType) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW -> {
                ReceiveReceipt receipt = receiptMapper.selectById(bizId);
                if (receipt == null) {
                    return;
                }
                Long operatorId = Integer.valueOf(1).equals(receipt.getApprovalStep())
                        ? receipt.getApprovalStep1UserId() : receipt.getApprovalStep2UserId();
                String operatorName = Integer.valueOf(1).equals(receipt.getApprovalStep())
                        ? receipt.getApprovalStep1Name() : receipt.getApprovalStep2Name();
                receiveReceiptService.reject(bizId, reason, operatorId, operatorName);
            }
            case ApprovalInstance.BIZ_TRANSFER -> {
                TransferOrder order = transferOrderMapper.selectById(bizId);
                if (order != null) {
                    transferOrderService.reject(bizId, reason,
                            order.getApplicantUserId(), order.getApplicantName());
                }
            }
            case ApprovalInstance.BIZ_CHANGE -> applyFinalTerminate(bizType, bizId, reason);
            default -> log.debug("忽略拒绝落地（bizType={}）", bizType);
        }
    }

    private void applyFinalTerminate(String bizType, Long bizId, String reason) {
        switch (bizType) {
            case ApprovalInstance.BIZ_RECEIVE, ApprovalInstance.BIZ_BORROW ->
                    applyFinalRefuse(bizType, bizId, reason);
            case ApprovalInstance.BIZ_TRANSFER -> {
                TransferOrder order = transferOrderMapper.selectById(bizId);
                if (order != null) {
                    transferOrderService.cancel(bizId, order.getApplicantUserId());
                }
            }
            case ApprovalInstance.BIZ_CHANGE -> {
                ChangeOrder order = changeOrderMapper.selectById(bizId);
                if (order != null) {
                    changeOrderService.cancel(bizId, order.getApplicantUserId());
                }
            }
            default -> log.debug("忽略终止落地（bizType={}）", bizType);
        }
    }

    private String bizTypeOf(String templateKey) {
        return switch (templateKey) {
            case TEMPLATE_BIZ_RECEIVE -> ApprovalInstance.BIZ_RECEIVE;
            case TEMPLATE_BIZ_BORROW -> ApprovalInstance.BIZ_BORROW;
            case TEMPLATE_BIZ_TRANSFER -> ApprovalInstance.BIZ_TRANSFER;
            case TEMPLATE_BIZ_CHANGE -> ApprovalInstance.BIZ_CHANGE;
            default -> ApprovalInstance.BIZ_RECEIVE;
        };
    }

    private void alertAdmins(String message) {
        for (Long adminId : userDirectory.userIdsByRole(props.getAdminRole())) {
            notificationService.notify(adminId, NotificationType.DINGTALK_SYNC_ALERT,
                    message, "DINGTALK", 0L);
        }
    }

    /**
     * 导入失败通知发起人本人（此前仅告警管理员，发起人在钉钉侧看到审批通过、
     * 系统里却没有单据，完全无感知——必须闭环反馈让发起人自己能修正重发）。
     */
    private void notifyOriginator(UserResp applicant, String message) {
        try {
            notificationService.notify(applicant.id(), NotificationType.DINGTALK_SYNC_ALERT,
                    message, "DINGTALK", 0L);
        } catch (Exception e) {
            log.warn("导入失败通知发起人异常（userId={}）：{}", applicant.id(), e.getMessage());
        }
    }

    private static String displayName(UserResp user) {
        return (user.name() != null && !user.name().isBlank()) ? user.name() : String.valueOf(user.id());
    }
}
