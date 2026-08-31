package com.sk.asset.service.dingtalk;

import com.sk.asset.dingtalk.client.DingTalkApiClient;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 钉钉 userid 批量同步（M10 联调 2026-08-31 补齐：生产环境不可能让员工逐个报 userid）。
 *
 * <p>链路：BFS 遍历钉钉部门树（根=1）→ 每部门分页拉用户（userid/name/mobile）
 * → 与 comm_public_basic.sys_user 匹配 → 回填 dd_user_id 单列。</p>
 *
 * <p>匹配策略（优先级从高到低）：
 * <ol>
 *   <li>手机号精确匹配（钉钉 mobile 可能带 +86 前缀，归一化后比对；一人一号最可靠）</li>
 *   <li>姓名匹配兜底（仅当 sys_user 中该姓名唯一且未绑定；重名跳过防错绑）</li>
 * </ol></p>
 *
 * <p>写入边界（ADR-0004 不破坏：asset 不存用户主数据，本同步只写 sys_user.dd_user_id 一列）：
 * 只填空值，不覆盖已有绑定；同一 dd_user_id 只绑一个 sys_user（防 2026-08-31 撞绑事故重演，
 * 见 UserDirectory.findByDdUserId 重复绑定告警）。</p>
 */
@Slf4j
@Service
public class DingtalkUserSyncService {

    /** 部门树 BFS 上限（防异常环结构死循环；正常企业部门数远小于此） */
    private static final int MAX_DEPT_COUNT = 1000;

    private final DingTalkApiClient apiClient;
    private final DingtalkProperties props;
    private final String url;
    private final String username;
    private final String password;

    public DingtalkUserSyncService(DingTalkApiClient apiClient,
                                   DingtalkProperties props,
                                   @Value("${app.user-directory.url:}") String url,
                                   @Value("${app.user-directory.username:}") String username,
                                   @Value("${app.user-directory.password:}") String password) {
        this.apiClient = apiClient;
        this.props = props;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /** 同步结果报告（前端/运维核对用） */
    public record SyncReport(
            int deptCount,        // 遍历部门数（含根）
            int ddUserCount,      // 钉钉侧用户数
            int phoneMatched,     // 手机号匹配并回填数
            int nameMatched,      // 姓名兜底匹配并回填数
            int skippedConflict,  // 已绑不同 userid 跳过数（人工核对）
            int unmatched         // 两侧都对不上的（人工核对）
    ) {
    }

    /** 内部用户行（sys_user 只读快照 + 回填决策） */
    private record SysUserRow(Long id, String name, String phone, String ddUserId) {
    }

    /**
     * 全量同步一次（超管手动触发；钉钉启用 + user-directory 连接配置才可用）。
     */
    public SyncReport syncAll() {
        if (!props.isEnabled()) {
            throw new IllegalStateException("钉钉联动未启用（app.dingtalk.enabled=false）");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("用户目录连接未配置（app.user-directory.url）");
        }

        // 1. 钉钉侧：部门树 BFS + 每部门分页拉用户
        Set<Long> visitedDepts = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(1L);
        visitedDepts.add(1L);
        Map<String, DingTalkApiClient.DeptUser> ddByUserid = new HashMap<>();
        while (!queue.isEmpty()) {
            Long deptId = queue.poll();
            for (Long sub : apiClient.listSubDeptIds(deptId)) {
                if (visitedDepts.size() < MAX_DEPT_COUNT && visitedDepts.add(sub)) {
                    queue.add(sub);
                }
            }
            Long cursor = null;
            do {
                DingTalkApiClient.DeptUserPage page = apiClient.listDeptUsers(deptId, cursor);
                for (DingTalkApiClient.DeptUser u : page.users()) {
                    if (!u.userid().isBlank()) {
                        ddByUserid.put(u.userid(), u);
                    }
                }
                cursor = page.nextCursor();
            } while (cursor != null);
        }
        log.info("钉钉 userid 同步：部门数={}，钉钉用户数={}", visitedDepts.size(), ddByUserid.size());

        // 2. 内部侧：sys_user 只读快照（手机号/姓名索引）
        List<SysUserRow> sysUsers = loadSysUsers();
        Map<String, List<SysUserRow>> byPhone = new HashMap<>();
        Map<String, List<SysUserRow>> byName = new HashMap<>();
        for (SysUserRow u : sysUsers) {
            if (u.phone() != null && !u.phone().isBlank()) {
                byPhone.computeIfAbsent(normalizePhone(u.phone()), k -> new ArrayList<>()).add(u);
            }
            byName.computeIfAbsent(u.name(), k -> new ArrayList<>()).add(u);
        }

        // 3. 匹配 + 回填（只填空、不覆盖、一个 ddid 只绑一人）
        int phoneMatched = 0, nameMatched = 0, skippedConflict = 0, unmatched = 0;
        Set<String> boundDdIds = new HashSet<>();
        // 已有绑定先行占位（防本轮把同一 ddid 绑给第二人）
        for (SysUserRow u : sysUsers) {
            if (u.ddUserId() != null && !u.ddUserId().isBlank()) {
                boundDdIds.add(u.ddUserId());
            }
        }
        try (Connection conn = openConnection();
             PreparedStatement update = conn.prepareStatement(
                     "UPDATE sys_user SET dd_user_id = ? WHERE id = ? AND dd_user_id IS NULL")) {
            for (DingTalkApiClient.DeptUser dd : ddByUserid.values()) {
                SysUserRow target = matchOne(dd, byPhone, byName);
                if (target == null) {
                    unmatched++;
                    continue;
                }
                if (target.ddUserId() != null && !target.ddUserId().isBlank()) {
                    if (!dd.userid().equals(target.ddUserId())) {
                        skippedConflict++; // 已绑不同钉钉号：不覆盖，人工核对
                        log.warn("userid 绑定冲突跳过：sys_user={}（id={}）已绑 {}，钉钉侧 {}（name={}）",
                                target.name(), target.id(), target.ddUserId(), dd.userid(), dd.name());
                    }
                    continue;
                }
                if (!boundDdIds.add(dd.userid())) {
                    log.warn("钉钉 userid {} 已绑其他用户，跳过重复绑定（name={}）", dd.userid(), dd.name());
                    continue;
                }
                update.setString(1, dd.userid());
                update.setLong(2, target.id());
                update.executeUpdate();
                if (target.phone() != null && !target.phone().isBlank()
                        && normalizePhone(target.phone()).equals(normalizePhone(dd.mobile()))) {
                    phoneMatched++;
                } else {
                    nameMatched++;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("回填 sys_user.dd_user_id 失败：" + e.getMessage(), e);
        }
        log.info("钉钉 userid 同步完成：手机号匹配={}，姓名匹配={}，冲突跳过={}，未匹配={}",
                phoneMatched, nameMatched, skippedConflict, unmatched);
        return new SyncReport(visitedDepts.size(), ddByUserid.size(),
                phoneMatched, nameMatched, skippedConflict, unmatched);
    }

    /** 手机号优先（归一化精确、唯一命中）；姓名兜底（唯一命中）；其余不猜交人工 */
    private SysUserRow matchOne(DingTalkApiClient.DeptUser dd,
                                Map<String, List<SysUserRow>> byPhone,
                                Map<String, List<SysUserRow>> byName) {
        if (dd.mobile() != null && !dd.mobile().isBlank()) {
            List<SysUserRow> hits = byPhone.get(normalizePhone(dd.mobile()));
            if (hits != null && hits.size() == 1) {
                return hits.get(0);
            }
        }
        List<SysUserRow> nameHits = byName.get(dd.name());
        if (nameHits != null && nameHits.size() == 1) {
            return nameHits.get(0);
        }
        return null;
    }

    /** 手机号归一化：去空格/横线，剥 +86 前缀（钉钉 mobile 常带国码） */
    static String normalizePhone(String raw) {
        String p = raw.replaceAll("[\\s-]", "");
        if (p.startsWith("+86")) {
            p = p.substring(3);
        } else if (p.startsWith("86") && p.length() == 13) {
            p = p.substring(2);
        }
        return p;
    }

    private List<SysUserRow> loadSysUsers() {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, phone, dd_user_id FROM sys_user WHERE deleted = 0");
             ResultSet rs = ps.executeQuery()) {
            List<SysUserRow> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new SysUserRow(rs.getLong("id"), rs.getString("name"),
                        rs.getString("phone"), rs.getString("dd_user_id")));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("读取 sys_user 失败：" + e.getMessage(), e);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
