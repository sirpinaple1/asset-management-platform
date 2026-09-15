package com.sk.asset.service.dingtalk;

import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dingtalk.client.DingTalkApiClient;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import com.sk.asset.entity.dingtalk.DingtalkDept;
import com.sk.asset.enums.notification.NotificationType;
import com.sk.asset.mapper.dingtalk.DingtalkDeptMapper;
import com.sk.asset.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 钉钉部门树与主管缓存同步（多级主管审批链改造 v1.0）。
 *
 * <p>链路：BFS 遍历钉钉部门树（根=1，{@code topapi/v2/department/listsub}）→
 * 逐部门取主管（{@code topapi/v2/department/get} 的 dept_manager_userid_list）→
 * 按 dept_id upsert 全部 → 软删本次不存在的部门。根部门 dept_id=1 不落库
 * （无主管属正常，链在顶层部门自然截止）。</p>
 *
 * <p>触发：启动全量（ApplicationReadyEvent，钉钉启用时）+ 每日定时刷新（cron 可配）。
 * 同步失败仅记日志 + 告警不阻断启动/业务（沿用 Stream 建连失败的处理惯例，
 * 站内审批与既有链路兜底）；单部门主管查询失败按无主管处理（链向上回退天然兼容）。</p>
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class DingtalkDeptSyncService {

    /** 部门树 BFS 上限（防异常环结构死循环；正常企业部门数远小于此） */
    private static final int MAX_DEPT_COUNT = 1000;

    private final DingTalkApiClient apiClient;
    private final DingtalkProperties props;
    private final DingtalkDeptMapper deptMapper;
    private final UserDirectory userDirectory;
    private final NotificationService notificationService;

    /** 同步结果报告（日志/告警展示用） */
    public record DeptSyncReport(int deptCount, int managerCount, int failed) {
    }

    /** 启动全量同步：失败仅告警不阻断启动（ApplicationReadyEvent 已过启动关键路径） */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            DeptSyncReport report = refresh();
            log.info("钉钉部门树启动同步完成：{}", report);
        } catch (Exception e) {
            log.error("钉钉部门树启动同步失败（多级主管链将走既有数据，请检查凭证/网络后重启重试）：{}",
                    e.getMessage(), e);
            alertAdmins("钉钉部门树同步失败：" + e.getMessage()
                    + "（多级主管审批链解析可能受影响，请检查钉钉凭证/网络）");
        }
    }

    /** 每日定时刷新（时刻可配，默认凌晨 02:40；失败仅告警） */
    @Scheduled(cron = "${app.dingtalk.dept-sync-cron:0 40 2 * * ?}")
    public void scheduledRefresh() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            DeptSyncReport report = refresh();
            log.info("钉钉部门树定时刷新完成：{}", report);
        } catch (Exception e) {
            log.error("钉钉部门树定时刷新失败：{}", e.getMessage(), e);
            alertAdmins("钉钉部门树定时刷新失败：" + e.getMessage()
                    + "（多级主管审批链解析可能受影响）");
        }
    }

    /**
     * 全量刷新一次：遍历部门树 → 逐部门取主管 → upsert 全部 → 软删钉钉侧已不存在的部门。
     * 非事务逐行写入（缓存表，部分成功即可用，每日刷新收敛）。
     */
    public DeptSyncReport refresh() {
        // 1. BFS 遍历部门树（含 name/parent_id）
        Map<Long, DingTalkApiClient.DeptBrief> all = new LinkedHashMap<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(1L);
        visited.add(1L);
        while (!queue.isEmpty()) {
            Long parent = queue.poll();
            for (DingTalkApiClient.DeptBrief dept : apiClient.listSubDepts(parent)) {
                if (visited.size() < MAX_DEPT_COUNT && visited.add(dept.deptId())) {
                    all.put(dept.deptId(), dept);
                    queue.add(dept.deptId());
                }
            }
        }

        // 2. 逐部门取主管（单部门失败按无主管处理，链向上回退兼容；全量失败才整体告警）
        int managerCount = 0;
        int failed = 0;
        List<DingtalkDept> rows = new ArrayList<>(all.size());
        for (DingTalkApiClient.DeptBrief dept : all.values()) {
            DingtalkDept row = new DingtalkDept();
            row.setDeptId(dept.deptId());
            row.setName(dept.name());
            row.setParentId(dept.parentId());
            try {
                List<String> managers = apiClient.getDeptManagerIds(dept.deptId());
                if (!managers.isEmpty()) {
                    row.setManagerDdUserIds(String.join(",", managers));
                    managerCount++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("钉钉部门主管查询失败，按无主管处理（deptId={}，name={}）：{}",
                        dept.deptId(), dept.name(), e.getMessage());
            }
            rows.add(row);
        }

        // 3. upsert 全部（含复活：软删行重新出现时置回 deleted=0）
        for (DingtalkDept row : rows) {
            deptMapper.upsert(row);
        }

        // 4. 软删本次不存在的部门（钉钉侧组织调整后残留）
        int removed = 0;
        for (DingtalkDept existing : deptMapper.selectList(null)) {
            if (!all.containsKey(existing.getDeptId())) {
                removed += deptMapper.softDeleteByDeptId(existing.getDeptId());
            }
        }
        log.info("钉钉部门树同步：部门数={}，设主管部门数={}，主管查询失败={}，软删={}",
                all.size(), managerCount, failed, removed);
        return new DeptSyncReport(all.size(), managerCount, failed);
    }

    private void alertAdmins(String message) {
        for (Long adminId : userDirectory.userIdsByRole(props.getAdminRole())) {
            notificationService.notify(adminId, NotificationType.DINGTALK_SYNC_ALERT,
                    message, "DINGTALK", 0L);
        }
    }
}
