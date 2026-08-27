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
import java.util.List;

/**
 * comm_public_basic sys_user 只读用户目录（B1 定向待办 / 用户搜索）。
 *
 * <p>独立 JDBC 直连（与 M08 AuthUserDirectory 同方式），不走 HTTP、不走主数据源——
 * asset 库不存用户主数据（ADR-0004），sys_user 只读查询落在此处。</p>
 *
 * <p>降级约定：连接未配置（app.user-directory.url 为空）时，
 * {@link #exists} 视为通过（assignee 存在性校验跳过，不阻塞提单），
 * {@link #search} 抛 503（用户搜索是独立功能，明确提示优于静默空结果）。</p>
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
