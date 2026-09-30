package com.example.tidbdemo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 真实的库/表/账号操作：增删改查 + 事务转账。 */
public final class AccountRepo {
    private final Connection conn;

    public AccountRepo(Connection conn) { this.conn = conn; }

    public void dropAndCreateTable() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS account");
            st.execute("""
                CREATE TABLE account (
                  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
                  name       VARCHAR(64) NOT NULL,
                  balance    BIGINT NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY idx_name (name)
                )""");
        }
    }

    /** 幂等建表；dbName 为空表示使用当前连接默认库 */
    public void createTableIfNotExists(String dbName) throws SQLException {
        String prefix = (dbName == null || dbName.isBlank()) ? "" : dbName + ".";
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + prefix + "account (" +
                " id         BIGINT PRIMARY KEY AUTO_INCREMENT," +
                " name       VARCHAR(64) NOT NULL," +
                " balance    BIGINT NOT NULL DEFAULT 0," +
                " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                " KEY idx_name (name))");
        }
    }

    /** 批量插入，返回影响行数 */
    public int batchInsert(List<String> names, long initialBalance) throws SQLException {
        String sql = "INSERT INTO account (name, balance) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String n : names) {
                ps.setString(1, n);
                ps.setLong(2, initialBalance);
                ps.addBatch();
            }
            int[] r = ps.executeBatch();
            int sum = 0;
            for (int v : r) sum += (v > 0 ? v : (v == Statement.SUCCESS_NO_INFO ? 1 : 0));
            return sum;
        }
    }

    public long sumBalance() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(SUM(balance),0) FROM account")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public long getBalance(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /** 事务转账：余额不足时抛异常并回滚 */
    public void transfer(long from, long to, long amount) throws SQLException {
        if (from == to) throw new SQLException("付款账户与收款账户不能相同");
        if (amount <= 0) throw new SQLException("转账金额必须大于 0");
        boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE account SET balance = balance - ? WHERE id = ? AND balance >= ?")) {
                ps.setLong(1, amount); ps.setLong(2, from); ps.setLong(3, amount);
                int n = ps.executeUpdate();
                if (n == 0) throw new SQLException("余额不足或付款账户不存在: id=" + from);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE account SET balance = balance + ? WHERE id = ?")) {
                ps.setLong(1, amount); ps.setLong(2, to);
                int n = ps.executeUpdate();
                if (n == 0) throw new SQLException("收款账户不存在: id=" + to);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(prev);
        }
    }

    // ---------------------------------------------------------------- CRUD

    public long insertAccount(String name, long balance) throws SQLException {
        if (name == null || name.isBlank()) throw new SQLException("name 不能为空");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO account (name, balance) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setLong(2, balance);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /** 更新姓名与余额，返回影响行数（0 表示 id 不存在） */
    public int updateAccount(long id, String name, long balance) throws SQLException {
        if (name == null || name.isBlank()) throw new SQLException("name 不能为空");
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE account SET name = ?, balance = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setLong(2, balance);
            ps.setLong(3, id);
            return ps.executeUpdate();
        }
    }

    /** 删除，返回影响行数（0 表示 id 不存在） */
    public int deleteAccount(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate();
        }
    }

    /** 单条查询，返回 JSON；不存在返回 null */
    public String getAccount(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, balance, updated_at FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs) : null;
            }
        }
    }

    /** 分页 + 关键字（按 id 或 name 模糊）查询 */
    public List<String> searchAccounts(String keyword, int limit, int offset) throws SQLException {
        List<String> out = new ArrayList<>();
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String sql;
        boolean hasKw = kw != null;
        if (hasKw) {
            sql = "SELECT id, name, balance, updated_at FROM account "
                + "WHERE name LIKE ? OR CAST(id AS CHAR) LIKE ? ORDER BY id LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT id, name, balance, updated_at FROM account ORDER BY id LIMIT ? OFFSET ?";
        }
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            if (hasKw) { ps.setString(i++, "%" + kw + "%"); ps.setString(i++, "%" + kw + "%"); }
            ps.setInt(i++, limit);
            ps.setInt(i++, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(row(rs));
            }
        }
        return out;
    }

    public List<String> listAccounts(int limit) throws SQLException {
        return searchAccounts(null, limit, 0);
    }

    public long countAccounts(String keyword) throws SQLException {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String sql = kw == null
            ? "SELECT COUNT(*) FROM account"
            : "SELECT COUNT(*) FROM account WHERE name LIKE ? OR CAST(id AS CHAR) LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (kw != null) { ps.setString(1, "%" + kw + "%"); ps.setString(2, "%" + kw + "%"); }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** 聚合统计 */
    public String stats() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(balance),0), COALESCE(MAX(balance),0), COALESCE(MIN(balance),0) FROM account")) {
            rs.next();
            return String.format("{\"count\":%d,\"sum\":%d,\"max\":%d,\"min\":%d}",
                    rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4));
        }
    }

    private static String row(ResultSet rs) throws SQLException {
        return String.format("{\"id\":%d,\"name\":\"%s\",\"balance\":%d,\"updated_at\":\"%s\"}",
                rs.getLong("id"), Json.esc(rs.getString("name")),
                rs.getLong("balance"), rs.getTimestamp("updated_at"));
    }
}
