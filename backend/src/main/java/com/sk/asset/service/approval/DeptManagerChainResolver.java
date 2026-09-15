package com.sk.asset.service.approval;

import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.dingtalk.DingtalkDept;
import com.sk.asset.mapper.dingtalk.DingtalkDeptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 多级主管审批链解析器（领用/借用单提交时路由审批人，替代两级链的"部门主管+仓管员"）。
 *
 * <p>链结构：固定一级审批人（app.dingtalk.fixed-first-approver-dd-user-id，如张三）
 * → 发起人所在部门逐级向上的各级主管（直接主管 → 部门主管 → … → 顶层部门）。
 * 站内快照只记前两级（step1=固定一级，step2=直接主管，快照外审批节点的操作
 * 由回调侧记日志，见 ApprovalCallbackServiceImpl）；钉钉推送则使用完整节点序列。</p>
 *
 * <p>部门定位：sys_user.dept 为钉钉完整路径（实测存在「/」与「-」两种分隔符），
 * 先按原始路径精确匹配 dingtalk_dept 全路径，再逐级去末级回退；特殊部门
 * （示例丰/锐鑫智能等不在钉钉树内）经 app.dingtalk.special-dept-managers 配置主管。
 * 未设主管部门一律向上回退（不填临时主管），多主管取第一人入链。</p>
 *
 * <p>兜底 = 阻止提交（400）：固定一级未配置/未绑定、申请人无部门、链上无可绑定主管、
 * 固定一级=申请人本人（死单防御，对齐 B1 assignee 自审拦截）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptManagerChainResolver {

    /** 链上主管数量上限（防脏数据/环结构拼出超长链；正常企业层级远小于此） */
    private static final int MAX_MANAGERS = 10;

    /** 部门树向上层级上限（防 parent 环） */
    private static final int MAX_DEPTH = 12;

    private final DingtalkDeptMapper deptMapper;
    private final UserDirectory userDirectory;
    private final DingtalkProperties props;

    /**
     * 多级链解析结果：snapshot 与 allDdUserIds 二选一有效（error 非空 = 不可提交）。
     * snapshot = 站内两级快照（复用 {@link ApprovalChainResolver.ResolvedChain}，
     * doCreate/审批中心/推进语义零改动）；allDdUserIds = 完整钉钉审批节点序列（入口 A 推送用）。
     */
    public record MultiResolution(ApprovalChainResolver.ResolvedChain snapshot,
                                  List<String> allDdUserIds, String error) {
        public boolean resolvable() {
            return error == null;
        }
    }

    /**
     * 尝试解析多级主管链（不抛异常，供提交校验与发起预览共用）。
     *
     * @param applicantUserId 发起人（部门实时取 sys_user.dept）
     */
    public MultiResolution tryResolveMultiLevel(Long applicantUserId) {
        // 1. 固定一级审批人（配置的钉钉 userid 反查内部用户）
        String fixedDd = props.getFixedFirstApproverDdUserId();
        if (fixedDd == null || fixedDd.isBlank()) {
            return failed("未配置固定一级审批人（app.dingtalk.fixed-first-approver-dd-user-id），请联系管理员处理");
        }
        UserResp fixedFirst = userDirectory.findByDdUserId(fixedDd);
        if (fixedFirst == null) {
            return failed("固定一级审批人未绑定系统用户（ddUserId=" + fixedDd + "），请先执行钉钉 userid 同步");
        }

        // 2. 申请人部门（钉钉完整路径）
        Map<Long, UserResp> users = userDirectory.namesByIds(List.of(applicantUserId));
        UserResp applicant = users.get(applicantUserId);
        String dept = applicant == null ? null : trimToNull(applicant.dept());
        if (dept == null) {
            return failed("未找到申请人的部门信息（用户目录未配置或用户不存在），无法解析多级主管审批链，请联系管理员处理");
        }
        if (Objects.equals(fixedFirst.id(), applicantUserId)) {
            return failed("固定一级审批人与申请人不能是同一人，无法发起审批");
        }
        String applicantDd = userDirectory.ddUserIdsByIds(List.of(applicantUserId)).get(applicantUserId);

        // 3. 链上主管收集（固定一级 → 直接主管 → … 逐级向上；去重去本人）
        List<Manager> managers = collectManagers(dept, applicantDd, fixedDd);
        if (managers.isEmpty()) {
            return failed("未解析到〔" + dept + "〕逐级向上的主管审批人（部门均未设主管或主管未绑定系统账号），请联系管理员处理");
        }

        // 4. step2 = 链上第一个绑定系统账号的主管（钉钉节点可含未绑定者，站内推进须可反查）
        Manager step2 = null;
        for (Manager m : managers) {
            if (m.user() != null) {
                step2 = m;
                break;
            }
        }
        if (step2 == null) {
            return failed("〔" + dept + "〕链上主管均未绑定系统账号，无法站内推进审批，请先执行钉钉 userid 同步");
        }

        List<String> allDdUserIds = new ArrayList<>();
        allDdUserIds.add(fixedDd);
        for (Manager m : managers) {
            // 直接主管恰为固定一级：节点去重（快照 merged，钉钉侧单节点一次终审）
            if (!fixedDd.equals(m.ddUserId())) {
                allDdUserIds.add(m.ddUserId());
            }
        }
        boolean merged = Objects.equals(fixedFirst.id(), step2.user().id());
        return new MultiResolution(new ApprovalChainResolver.ResolvedChain(
                fixedFirst.id(), displayName(fixedFirst), "multi-level-fixed",
                step2.user().id(), displayName(step2.user()), step2.deptName(),
                merged), List.copyOf(allDdUserIds), null);
    }

    /** 链上主管（钉钉 userid + 反查的内部用户，可能 null=未绑定） */
    private record Manager(String ddUserId, UserResp user, String deptName) {
    }

    /** 从申请人部门路径定位最深部门，沿 parent 向上收集主管（含特殊部门覆盖） */
    private List<Manager> collectManagers(String dept, String applicantDd, String fixedDd) {
        List<DingtalkDept> rows = deptMapper.selectList(null);
        Map<Long, DingtalkDept> byId = new HashMap<>();
        for (DingtalkDept row : rows) {
            byId.putIfAbsent(row.getDeptId(), row);
        }

        List<Manager> managers = new ArrayList<>();
        Set<String> seenDd = new HashSet<>();

        DingtalkDept start = locateStartDept(rows, byId, dept);
        if (start != null) {
            DingtalkDept current = start;
            for (int depth = 0; depth < MAX_DEPTH && current != null && managers.size() < MAX_MANAGERS; depth++) {
                String managerDd = managerOf(current);
                if (managerDd != null && !managerDd.equals(applicantDd) && seenDd.add(managerDd)) {
                    managers.add(new Manager(managerDd, userDirectory.findByDdUserId(managerDd), current.getName()));
                }
                current = byId.get(current.getParentId());
            }
            return managers;
        }

        // 钉钉树未命中（示例丰/锐鑫智能等特殊部门）：路径段从深到浅匹配特殊部门配置
        List<String> segments = splitSegments(dept);
        for (int i = segments.size() - 1; i >= 0; i--) {
            String special = specialManagerOf(segments.get(i));
            if (special != null && !special.equals(applicantDd) && seenDd.add(special)) {
                managers.add(new Manager(special, userDirectory.findByDdUserId(special), segments.get(i)));
            }
        }
        if (managers.isEmpty()) {
            log.warn("部门路径未命中钉钉部门树且无特殊部门配置（dept={}）", dept);
        }
        return managers;
    }

    /**
     * 定位申请人对应的钉钉部门：原始路径精确匹配 → 分隔符切分后逐级去末级回退。
     * dingtalk_dept 全路径 = 沿 parent 链拼接部门名（「/」连接，根不落库自然截止）。
     */
    private DingtalkDept locateStartDept(List<DingtalkDept> rows, Map<Long, DingtalkDept> byId, String dept) {
        Map<String, DingtalkDept> byPath = new HashMap<>();
        for (DingtalkDept row : rows) {
            byPath.putIfAbsent(fullPath(row, byId), row);
        }
        // 原始路径精确匹配（部门名本身可能含「-」，不能先切分）
        DingtalkDept hit = byPath.get(dept);
        if (hit != null) {
            return hit;
        }
        // 切分回退：逐级去末级向上（与两级链 ApprovalChainResolver.deptFallbackChain 规则一致）
        List<String> segments = splitSegments(dept);
        for (int i = segments.size(); i > 0; i--) {
            hit = byPath.get(String.join("/", segments.subList(0, i)));
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** 部门全路径（沿 parent 链拼接，含环/断链防护） */
    private String fullPath(DingtalkDept dept, Map<Long, DingtalkDept> byId) {
        List<String> names = new ArrayList<>();
        names.add(dept.getName());
        DingtalkDept current = dept;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Long parentId = current.getParentId();
            if (parentId == null || Long.valueOf(1L).equals(parentId)) {
                break;
            }
            DingtalkDept parent = byId.get(parentId);
            if (parent == null || parent.getDeptId().equals(current.getDeptId())) {
                break;
            }
            names.add(parent.getName());
            current = parent;
        }
        // 反转后拼接（浅层在前）
        StringBuilder path = new StringBuilder();
        for (int i = names.size() - 1; i >= 0; i--) {
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(names.get(i));
        }
        return path.toString();
    }

    /**
     * 部门路径切分：含「/」优先按「/」（钉钉标准），否则按「-」
     * （实测 sys_user.dept 存在旧「-」写法）；单段无切分。
     */
    private List<String> splitSegments(String dept) {
        String separator = dept.contains("/") ? "/" : (dept.contains("-") ? "-" : null);
        if (separator == null) {
            return List.of(dept.trim());
        }
        List<String> segments = new ArrayList<>();
        for (String s : dept.split(separator)) {
            if (!s.isBlank()) {
                segments.add(s.trim());
            }
        }
        return segments;
    }

    /** 部门主管：特殊部门配置优先（示例丰/锐鑫智能等），否则取缓存表第一主管 */
    private String managerOf(DingtalkDept dept) {
        String special = specialManagerOf(dept.getName());
        if (special != null) {
            return special;
        }
        String managers = dept.getManagerDdUserIds();
        if (managers == null || managers.isBlank()) {
            return null;
        }
        String first = managers.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    private String specialManagerOf(String deptName) {
        if (deptName == null) {
            return null;
        }
        String manager = props.getSpecialDeptManagers().get(deptName.trim());
        return (manager == null || manager.isBlank()) ? null : manager.trim();
    }

    private static MultiResolution failed(String error) {
        return new MultiResolution(null, List.of(), error);
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String displayName(UserResp user) {
        return (user.name() != null && !user.name().isBlank()) ? user.name() : String.valueOf(user.id());
    }
}
