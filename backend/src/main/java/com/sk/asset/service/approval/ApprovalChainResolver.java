package com.sk.asset.service.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.enums.approval.ApprovalConfigType;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.approval.ApprovalConfigMapper;
import com.sk.asset.service.notification.NotificationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 两级审批链解析器（领用/借用单提交时路由审批人）。
 *
 * <p>解析顺序（依据《资产领用与借用操作流程指导》）：
 * 一级 = DEPT_SUPERVISOR + 发起人部门（sys_user.dept 实时取，精确匹配优先，
 * 未命中按部门路径逐级向上回退——实测 sys_user.dept 存在「/」与「-」两种分隔符，兼容两种）；
 * 二级 = WAREHOUSE_KEEPER + 单据领用区域（asset_location.id）。</p>
 *
 * <p>兜底 = 阻止提交（400）：任一级解析不到、配置的审批人已失效、审批人=发起人本人（死单防御，对齐 B1 assignee 自审拦截）。
 * 解析失败时经 {@link #alertAdmins} 以独立事务通知 systemAdmin 用户补配置
 * （独立事务：即使外层提交事务因 400 回滚，告警也已落库——告警与业务提交解耦）。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalChainResolver {

    private final ApprovalConfigMapper configMapper;
    private final UserDirectory userDirectory;
    private final NotificationService notificationService;

    /** 超管角色名（审批链配置管理门禁 + 配置缺失告警接收人） */
    @Value("${app.approval-chain.admin-role:systemAdmin}")
    private String adminRole;

    /** 解析成功的两级审批链快照（提交时冻结，人员调动不影响在途单据） */
    @Getter
    @RequiredArgsConstructor
    public static class ResolvedChain {
        private final Long step1UserId;
        private final String step1Name;
        private final String step1SourceKey;
        private final Long step2UserId;
        private final String step2Name;
        private final String step2SourceKey;
        /** 两级审批人为同一人（部门主管兼仓管）：合并为一次审批（approval_step 直接置 2） */
        private final boolean merged;
    }

    /** 解析结果：chain 与 error 二选一（error 非空 = 不可提交，链路为空） */
    public record Resolution(ResolvedChain chain, String error) {
        public boolean resolvable() {
            return error == null;
        }
    }

    /**
     * 尝试解析两级审批链（不抛异常，供提交校验与发起预览共用）。
     *
     * @param applicantUserId 发起人（部门实时取 sys_user.dept）
     * @param locationId      领用区域 id
     * @param locationName    领用区域名称（错误提示展示用）
     */
    public Resolution tryResolve(Long applicantUserId, Long locationId, String locationName) {
        Map<Long, UserResp> users = userDirectory.namesByIds(List.of(applicantUserId));
        UserResp applicant = users.get(applicantUserId);
        String dept = applicant == null ? null : trimToNull(applicant.dept());
        if (dept == null) {
            return new Resolution(null, "未找到申请人的部门信息（用户目录未配置或用户不存在），无法解析部门主管审批人，请联系管理员处理");
        }

        // 一级：部门主管，精确优先 + 逐级向上回退
        ApprovalConfig step1 = null;
        String step1Key = null;
        for (String key : deptFallbackChain(dept)) {
            ApprovalConfig cfg = findOne(ApprovalConfigType.DEPT_SUPERVISOR, key);
            if (cfg != null) {
                step1 = cfg;
                step1Key = key;
                break;
            }
        }
        if (step1 == null) {
            return new Resolution(null, "未找到〔" + dept + "〕的主管审批人，请线下联系审批人，并联系管理员配置审批链");
        }

        // 二级：领料仓管理员（按领用区域）
        ApprovalConfig step2 = findOne(ApprovalConfigType.WAREHOUSE_KEEPER, String.valueOf(locationId));
        if (step2 == null) {
            return new Resolution(null, "未找到〔" + (locationName != null ? locationName : "位置" + locationId)
                    + "〕的仓管审批人，请线下联系审批人，并联系管理员配置审批链");
        }

        // 审批人姓名反查 + 配置失效防御（配置后用户被删除）
        Map<Long, UserResp> approvers = userDirectory.namesByIds(
                List.of(step1.getApproverUserId(), step2.getApproverUserId()));
        UserResp u1 = approvers.get(step1.getApproverUserId());
        UserResp u2 = approvers.get(step2.getApproverUserId());
        if (u1 == null || u2 == null) {
            Long staleId = (u1 == null) ? step1.getApproverUserId() : step2.getApproverUserId();
            return new Resolution(null, "审批链配置的审批人已失效（用户不存在，id=" + staleId
                    + "），请联系管理员更新审批链配置");
        }

        // 死单防御：任一级审批人 = 发起人本人 → 阻止提交（对齐 assignee 自审拦截）
        if (Objects.equals(step1.getApproverUserId(), applicantUserId)) {
            return new Resolution(null, "〔" + dept + "〕的主管审批人是申请人自己，无法发起审批，请联系管理员调整审批链配置");
        }
        if (Objects.equals(step2.getApproverUserId(), applicantUserId)) {
            return new Resolution(null, "领用区域〔" + (locationName != null ? locationName : locationId)
                    + "〕的仓管审批人是申请人自己，无法发起审批，请联系管理员调整审批链配置");
        }

        boolean merged = Objects.equals(step1.getApproverUserId(), step2.getApproverUserId());
        return new Resolution(new ResolvedChain(
                step1.getApproverUserId(), displayName(u1), step1Key,
                step2.getApproverUserId(), displayName(u2), String.valueOf(locationId),
                merged), null);
    }

    /**
     * 解析失败告警：通知全部 systemAdmin 用户补配置。
     * REQUIRES_NEW 独立事务——调用方（提交事务）随后抛 400 回滚时告警不丢。
     * 用户目录未配置 / 查询失败时静默跳过（不阻塞 400 报错本身）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void alertAdmins(String message, String bizContext) {
        List<Long> adminIds = userDirectory.userIdsByRole(adminRole);
        for (Long adminId : adminIds) {
            notificationService.notify(adminId, NotificationType.APPROVAL_CONFIG_ALERT,
                    "审批链配置告警：" + message + "（" + bizContext + "）", "CONFIG", 0L);
        }
    }

    /**
     * 部门路径回退链：精确优先，逐级去掉末级向上回退。
     * sys_user.dept 实测存在两种分隔符：「/」（如 示例科技制造中心/生产工程部/设备维护）
     * 与「-」（如 供应链管理中心-示例科技采购部）；含「/」优先按「/」切分，否则按「-」，单段无回退。
     */
    private List<String> deptFallbackChain(String dept) {
        List<String> chain = new ArrayList<>();
        chain.add(dept);
        String separator = dept.contains("/") ? "/" : (dept.contains("-") ? "-" : null);
        if (separator != null) {
            String current = dept;
            int idx;
            while ((idx = current.lastIndexOf(separator)) > 0) {
                current = current.substring(0, idx);
                chain.add(current);
            }
        }
        return chain;
    }

    private ApprovalConfig findOne(ApprovalConfigType type, String key) {
        return configMapper.selectOne(new LambdaQueryWrapper<ApprovalConfig>()
                .eq(ApprovalConfig::getConfigType, type.name())
                .eq(ApprovalConfig::getConfigKey, key));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String displayName(UserResp user) {
        return (user.name() != null && !user.name().isBlank()) ? user.name() : String.valueOf(user.id());
    }
}
