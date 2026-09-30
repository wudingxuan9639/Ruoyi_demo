package com.example.tidbdemo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** JDBC 连接封装。 */
public final class Db {
    private final Config cfg;

    public Db(Config cfg) { this.cfg = cfg; }

    /** 以应用账号连接 demo_db */
    public Connection connect() throws SQLException {
        return connect(cfg.get("jdbc.url"), cfg.get("jdbc.user"), cfg.get("jdbc.password"));
    }

    /** 以 root 连接（bootstrap 用），demoRootUrl 为空则用同一 url 的 server 段 */
    public Connection connectRoot() throws SQLException {
        String rootUrl = cfg.get("jdbc.root.url");
        String rootPwd = System.getenv("TIDB_BOOTSTRAP_ROOT_PASSWORD");
        if (rootPwd == null) rootPwd = "";
        if (rootUrl == null || rootUrl.isBlank()) {
            rootUrl = cfg.get("jdbc.url").replace("/demo_db", "");
        }
        return connect(rootUrl, "root", rootPwd);
    }

    private Connection connect(String url, String user, String password) throws SQLException {
        Properties info = new Properties();
        info.setProperty("user", user);
        info.setProperty("password", password == null ? "" : password);
        info.setProperty("connectTimeout", "5000");
        info.setProperty("socketTimeout", "20000");
        return DriverManager.getConnection(url, info);
    }
}
