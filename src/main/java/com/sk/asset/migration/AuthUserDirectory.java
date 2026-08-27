package com.sk.asset.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * comm_public_basic sys_user 用户目录（M08 迁移用，独立 JDBC 连接，不走 HTTP / 主数据源）。
 *
 * <p>匹配顺序：工号（username 精确）→ 姓名（存活用户唯一命中；重名取最小 id 并记警告）
 * → 未命中且开启自动建号时创建新账号（SK 工号段顺延，BCrypt 默认密码，
 * remark 标记迁移来源）。重跑幂等：上次创建的账号经姓名匹配复用，不重复创建。</p>
 */
public class AuthUserDirectory {

    private static final Pattern SK_USERNAME = Pattern.compile("^SK(\\d+)$");

    /** 账号创建回调：入参 username/name/dept，返回新账号 id（由 JDBC 实现落库） */
    public interface AccountCreator {
        long create(String username, String name, String deptHint);
    }

    public record UserRow(long id, String username, String name, String dept) {
    }

    private final AccountCreator creator;
    private final boolean autoCreate;

    /** 仅存活用户（deleted=0），username → 用户 */
    private final Map<String, UserRow> byUsername = new HashMap<>();
    /** 仅存活用户，姓名 → 用户列表（重名场景取最小 id） */
    private final Map<String, List<UserRow>> byName = new HashMap<>();

    private long maxSkSequence;
    private final List<String> createdUsernames = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public AuthUserDirectory(AccountCreator creator, boolean autoCreate) {
        this.creator = creator;
        this.autoCreate = autoCreate;
    }

    public static AuthUserDirectory load(Connection conn, AccountCreator creator,
                                         boolean autoCreate) throws SQLException {
        AuthUserDirectory dir = new AuthUserDirectory(creator, autoCreate);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, username, name, dept, deleted FROM sys_user");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (rs.getInt("deleted") != 0) {
                    continue;
                }
                dir.register(new UserRow(rs.getLong("id"), rs.getString("username"),
                        rs.getString("name"), rs.getString("dept")));
            }
        }
        return dir;
    }

    /** 包可见：注册用户行并刷新索引（load 与测试共用） */
    void register(UserRow row) {
        byUsername.put(row.username(), row);
        byName.computeIfAbsent(row.name(), k -> new ArrayList<>()).add(row);
        Matcher m = SK_USERNAME.matcher(row.username());
        if (m.matches()) {
            maxSkSequence = Math.max(maxSkSequence, Long.parseLong(m.group(1)));
        }
    }

    /** 工号（如 SK544）精确匹配 username；未命中返回 null */
    public Long resolveByJobNumber(String jobNo) {
        UserRow row = jobNo == null ? null : byUsername.get(jobNo);
        return row == null ? null : row.id();
    }

    /**
     * 姓名匹配：唯一命中 → id；重名 → 最小 id（记警告）；未命中 → 自动建号（关闭时返回 null 并记警告）。
     *
     * @param deptHint 建号时写入的部门线索（可空）
     */
    public Long resolveByName(String name, String deptHint) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            return null;
        }
        List<UserRow> rows = byName.get(clean);
        if (rows != null && !rows.isEmpty()) {
            if (rows.size() > 1) {
                warnings.add("姓名「" + clean + "」在 sys_user 中有 " + rows.size() + " 条存活记录，取最小 id");
            }
            return rows.stream().mapToLong(UserRow::id).min().getAsLong();
        }
        if (!autoCreate) {
            warnings.add("姓名「" + clean + "」未匹配到用户且未开启自动建号，user_id 置空");
            return null;
        }
        String username = "SK" + (++maxSkSequence);
        long id = creator.create(username, truncate(clean, 50), truncate(deptHint, 100));
        register(new UserRow(id, username, clean, deptHint));
        createdUsernames.add(username + " " + clean);
        return id;
    }

    /**
     * 操作人文本解析：工号优先，无工号或工号未命中时按姓名（自动建号兜底）。
     */
    public Long resolveByOperatorLabel(String label) {
        String jobNo = OperatorTextParser.extractJobNo(label);
        if (jobNo != null) {
            Long id = resolveByJobNumber(jobNo);
            if (id != null) {
                return id;
            }
        }
        String name = OperatorTextParser.extractName(label);
        return resolveByName(name, null);
    }

    public List<String> getCreatedUsernames() {
        return List.copyOf(createdUsernames);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * JDBC 落库实现：INSERT sys_user（BCrypt 默认密码 + 迁移来源 remark）。
     */
    public static class JdbcAccountCreator implements AccountCreator {

        private final Connection conn;
        private final String defaultPassword;
        private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

        public JdbcAccountCreator(Connection conn, String defaultPassword) {
            this.conn = conn;
            this.defaultPassword = defaultPassword;
        }

        @Override
        public long create(String username, String name, String deptHint) {
            String sql = "INSERT INTO sys_user (name, username, password, dept, status, remark, do_user, do_time, deleted) "
                    + "VALUES (?, ?, ?, ?, 0, 'M08 历史数据迁移自动创建', 'asset-migration', NOW(), 0)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, username);
                ps.setString(3, encoder.encode(defaultPassword));
                ps.setString(4, deptHint);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
                throw new SQLException("创建 sys_user 未返回自增 id（username=" + username + "）");
            } catch (SQLException e) {
                throw new IllegalStateException("创建 sys_user 失败（username=" + username + "）：" + e.getMessage(), e);
            }
        }
    }
}
