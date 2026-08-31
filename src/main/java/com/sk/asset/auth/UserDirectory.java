package com.sk.asset.auth;

import com.sk.asset.common.BusinessException;
import com.sk.asset.common.PageResp;
import com.sk.asset.dto.user.UserResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * comm_public_basic sys_user 只读用户目录（B1 定向待办 / 用户搜索 / 资产使用人姓名反查）。
 *
 * <p>独立 JDBC 直连（与 M08 AuthUserDirectory 同方式），不走 HTTP、不走主数据源——
 * asset 库不存用户主数据（ADR-0004），sys_user 只读查询落在此处。</p>
 *
 * <p>降级约定：连接未配置（app.user-directory.url 为空）时，
 * {@link #exists} 视为通过（assignee 存在性校验跳过，不阻塞提单），
 * {@link #search} 抛 503（用户搜索是独立功能，明确提示优于静默空结果），
 * {@link #namesByIds} 返回空 Map（名称回填留空，不阻塞资产列表/详情展示）。</p>
 */
@Slf4j
@Component
public class UserDirectory {

    private final String url;
    private final String username;
    private final String password;

    public UserDirectory(@Value("${app.user-directory.url:}") String url,
                         @Value("${app.user-directory.username:}") String username,
                         @Value("${app.user-directory.password:}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /** @return 是否已配置 comm_public_basic 库连接 */
    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }

    /**
     * 校验用户存在性（仅存活用户 deleted=0）。
     * 未配置连接时返回 true（降级跳过校验）；配置了但查询失败按 500 上抛，不静默放行。
     */
    public boolean exists(Long userId) {
        if (userId == null) {
            return false;
        }
        if (!isConfigured()) {
            return true;
        }
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM sys_user WHERE id = ? AND deleted = 0")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("查询 sys_user 失败（id={}）：{}", userId, e.getMessage());
            throw new BusinessException(500, "用户目录查询失败，请稍后重试");
        }
    }

    /**
     * 用户搜索（分页）：keyword 匹配工号（username）或姓名（name），仅存活用户，按 id 升序。
     */
    public PageResp<UserResp> search(String keyword, long page, long size) {
        if (!isConfigured()) {
            throw new BusinessException(503, "用户目录未配置（app.user-directory.url）");
        }
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (safePage - 1) * safeSize;

        String like = null;
        if (keyword != null && !keyword.isBlank()) {
            like = "%" + escapeLike(keyword.trim()) + "%";
        }

        try (Connection conn = openConnection()) {
            long total = count(conn, like);
            List<UserResp> records = (total == 0 || offset >= total)
                    ? List.of() : queryPage(conn, like, offset, safeSize);
            PageResp<UserResp> resp = new PageResp<>();
            resp.setRecords(records);
            resp.setTotal(total);
            resp.setPage(safePage);
            resp.setSize(safeSize);
            return resp;
        } catch (SQLException e) {
            log.error("搜索 sys_user 失败（keyword={}）：{}", keyword, e.getMessage());
            throw new BusinessException(500, "用户目录查询失败，请稍后重试");
        }
    }

    /**
     * 按 ID 批量反查用户（资产使用人/管理员姓名回填）。
     * 仅存活用户 deleted=0；每批 IN 上限 {@value #MAX_IN_CLAUSE} 防超大页拖垮 sys_user 查询。
     *
     * <p>降级语义：入参空集合或连接未配置返回空 Map（调用方名称留空不阻塞列表）；
     * 已配置但查询失败按 500 上抛（与 exists/search 失败口径一致，不静默吞错）。</p>
     *
     * @return id → 用户（id/工号/姓名/部门）；被删除或未命中的 id 不在 Map 中
     */
    public Map<Long, UserResp> namesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        if (!isConfigured()) {
            return Collections.emptyMap();
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserResp> result = new HashMap<>();
        try (Connection conn = openConnection()) {
            for (int from = 0; from < distinct.size(); from += MAX_IN_CLAUSE) {
                List<Long> batch = distinct.subList(from, Math.min(from + MAX_IN_CLAUSE, distinct.size()));
                result.putAll(queryByIds(conn, batch));
            }
            return result;
        } catch (SQLException e) {
            log.error("批量反查 sys_user 失败（ids.size={}）：{}", distinct.size(), e.getMessage());
            throw new BusinessException(500, "用户目录查询失败，请稍后重试");
        }
    }

    /**
     * 按角色名反查持该角色的存活用户 ID 列表（审批链配置告警：解析失败时通知 systemAdmin）。
     * 角色为 comm_public_basic 全局角色（sys_role.name，如 systemAdmin），跨 sys_role_user 关联。
     * 降级语义：未配置连接返回空列表（告警通知跳过，不阻塞提单报错）。
     */
    public List<Long> userIdsByRole(String roleName) {
        if (roleName == null || roleName.isBlank() || !isConfigured()) {
            return List.of();
        }
        String sql = "SELECT ru.user_id FROM sys_role_user ru "
                + "JOIN sys_role r ON r.id = ru.role_id "
                + "JOIN sys_user u ON u.id = ru.user_id AND u.deleted = 0 "
                + "WHERE r.name = ? AND r.deleted = 0";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong("user_id"));
                }
                return ids;
            }
        } catch (SQLException e) {
            log.error("按角色反查用户失败（role={}）：{}", roleName, e.getMessage());
            return List.of();
        }
    }

    /** IN 子句单批上限（MySQL 同时也受 max_allowed_packet 约束，这里主动分批） */
    static final int MAX_IN_CLAUSE = 1000;

    /**
     * 钉钉 userid → 内部用户（M10 OA 审批联动：事件 staffId 反查操作人）。
     * 降级语义：未配置连接返回 null（调用方按"无法解析操作人"忽略事件并告警）。
     * 同一 dd_user_id 绑定多个用户时无法判定真实操作人，返回 null 并告警
     * （2026-08-31 联调教训：撞绑导致 LIMIT 1 静默取错人，审批人记录错乱）。
     *
     * @return 内部用户（id/工号/姓名/部门）；未绑定、重复绑定或已删除返回 null
     */
    public UserResp findByDdUserId(String ddUserId) {
        if (ddUserId == null || ddUserId.isBlank() || !isConfigured()) {
            return null;
        }
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username, name, dept FROM sys_user "
                             + "WHERE dd_user_id = ? AND deleted = 0")) {
            ps.setString(1, ddUserId);
            List<UserResp> matches = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    matches.add(new UserResp(rs.getLong("id"), rs.getString("username"),
                            rs.getString("name"), rs.getString("dept")));
                }
            }
            if (matches.size() > 1) {
                log.error("钉钉 userid 重复绑定多个内部用户（ddUserId={}，users={}），"
                        + "无法判定操作人——请清理 sys_user.dd_user_id 撞绑", ddUserId, matches);
                return null;
            }
            return matches.isEmpty() ? null : matches.get(0);
        } catch (SQLException e) {
            log.error("按钉钉 userid 反查用户失败（ddUserId={}）：{}", ddUserId, e.getMessage());
            return null;
        }
    }

    /**
     * 内部 userId 批量 → 钉钉 userid（M10：发起单据时解析审批人/发起人的钉钉身份）。
     * 降级语义：未配置连接返回空 Map（调用方按"未绑定钉钉"降级站内审批）。
     *
     * @return id → dd_user_id；未绑定钉钉的用户不在 Map 中
     */
    public Map<Long, String> ddUserIdsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty() || !isConfigured()) {
            return Collections.emptyMap();
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> result = new HashMap<>();
        try (Connection conn = openConnection()) {
            for (int from = 0; from < distinct.size(); from += MAX_IN_CLAUSE) {
                List<Long> batch = distinct.subList(from, Math.min(from + MAX_IN_CLAUSE, distinct.size()));
                String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
                String sql = "SELECT id, dd_user_id FROM sys_user "
                        + "WHERE deleted = 0 AND dd_user_id IS NOT NULL AND dd_user_id != '' "
                        + "AND id IN (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < batch.size(); i++) {
                        ps.setLong(i + 1, batch.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            result.put(rs.getLong("id"), rs.getString("dd_user_id"));
                        }
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            log.error("批量反查钉钉 userid 失败（ids.size={}）：{}", distinct.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<Long, UserResp> queryByIds(Connection conn, List<Long> batch) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
        String sql = "SELECT id, username, name, dept FROM sys_user WHERE deleted = 0 AND id IN ("
                + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < batch.size(); i++) {
                ps.setLong(i + 1, batch.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                Map<Long, UserResp> map = new HashMap<>();
                while (rs.next()) {
                    map.put(rs.getLong("id"), new UserResp(rs.getLong("id"), rs.getString("username"),
                            rs.getString("name"), rs.getString("dept")));
                }
                return map;
            }
        }
    }

    private long count(Connection conn, String like) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sys_user WHERE deleted = 0");
        if (like != null) {
            sql.append(" AND (username LIKE ? OR name LIKE ?)");
        }
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (like != null) {
                ps.setString(1, like);
                ps.setString(2, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private List<UserResp> queryPage(Connection conn, String like, long offset, long size)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, username, name, dept FROM sys_user WHERE deleted = 0");
        if (like != null) {
            sql.append(" AND (username LIKE ? OR name LIKE ?)");
        }
        sql.append(" ORDER BY id LIMIT ? OFFSET ?");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (like != null) {
                ps.setString(idx++, like);
                ps.setString(idx++, like);
            }
            ps.setLong(idx++, size);
            ps.setLong(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<UserResp> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(new UserResp(rs.getLong("id"), rs.getString("username"),
                            rs.getString("name"), rs.getString("dept")));
                }
                return records;
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /** LIKE 通配符转义（% _ \），防 keyword 注入通配符干扰匹配 */
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
