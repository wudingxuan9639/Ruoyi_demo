package com.example.tidbdemo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 入口：
 *   bootstrap —— 用 root 建库、建应用账号、给 root 设密码（仅首次）
 *   once      —— 一次跑完 11 项真实验证并打印，退出码 0/1
 *   serve     —— 常驻 HTTP 服务（127.0.0.1:18080）
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "once";
        Config cfg = Config.load();
        if (cfg.get("jdbc.url") == null || cfg.get("jdbc.url").isBlank()) {
            System.err.println("未找到配置：请用 -Dconfig=/opt/tidb-demo/app.properties 指定，"
                    + "或把 app.properties 放到 classpath。");
            System.exit(2);
        }
        Db db = new Db(cfg);
        switch (cmd) {
            case "bootstrap" -> bootstrap(db, cfg);
            case "once"      -> { int fail = once(db, cfg); System.exit(fail == 0 ? 0 : 1); }
            case "serve"     -> serve(db, cfg);
            default -> {
                System.out.println("用法: java -jar tidb-demo.jar [bootstrap|once|serve]");
                System.exit(2);
            }
        }
    }

    // ---------------------------------------------------------------- bootstrap
    private static void bootstrap(Db db, Config cfg) throws SQLException {
        String dbName = cfg.get("jdbc.dbname", "demo_db");
        String appUser = cfg.get("jdbc.user", "demo");
        String appPwd = cfg.get("jdbc.password", "");
        try (Connection c = db.connectRoot()) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS " + dbName);
                System.out.println("[bootstrap] CREATE DATABASE " + dbName + "  OK");
                st.execute("CREATE USER IF NOT EXISTS '" + appUser + "'@'127.0.0.1' IDENTIFIED BY '" + appPwd + "'");
                st.execute("CREATE USER IF NOT EXISTS '" + appUser + "'@'localhost' IDENTIFIED BY '" + appPwd + "'");
                st.execute("GRANT ALL PRIVILEGES ON " + dbName + ".* TO '" + appUser + "'@'127.0.0.1'");
                st.execute("GRANT ALL PRIVILEGES ON " + dbName + ".* TO '" + appUser + "'@'localhost'");
                // information_schema.cluster_info 需要 PROCESS 权限
                st.execute("GRANT PROCESS ON *.* TO '" + appUser + "'@'127.0.0.1'");
                st.execute("GRANT PROCESS ON *.* TO '" + appUser + "'@'localhost'");
                // 允许重复执行：显式改密，保证与 app.env 中的密码一致
                st.execute("ALTER USER '" + appUser + "'@'127.0.0.1' IDENTIFIED BY '" + appPwd + "'");
                st.execute("ALTER USER '" + appUser + "'@'localhost' IDENTIFIED BY '" + appPwd + "'");
                // 允许重复执行：显式改密，保证与 app.env 中的密码一致
                st.execute("ALTER USER '" + appUser + "'@'127.0.0.1' IDENTIFIED BY '" + appPwd + "'");
                st.execute("ALTER USER '" + appUser + "'@'localhost' IDENTIFIED BY '" + appPwd + "'");
                st.execute("FLUSH PRIVILEGES");
                new AccountRepo(c).createTableIfNotExists(dbName);
                System.out.println("[bootstrap] CREATE TABLE IF NOT EXISTS " + dbName + ".account  OK");
                System.out.println("[bootstrap] CREATE USER " + appUser + " + 授权  OK");
            }
        }
        System.out.println("bootstrap OK");
    }

    // -------------------------------------------------------------------- once
    private static int once(Db db, Config cfg) throws Exception {
        int fail = 0;
        int step = 0;
        try (Connection c = db.connect()) {
            AccountRepo repo = new AccountRepo(c);

            p(++step, "连接信息", cfg.get("jdbc.url") + "  user=" + cfg.get("jdbc.user"));
            p(++step, "TiDB 版本", Report.singleString(c, "SELECT VERSION()"));

            List<String> comps = Report.rows(c,
                "SELECT * FROM information_schema.cluster_info");
            p(++step, "集群组件", String.join(" ; ", comps));

            List<String> stores = Report.rows(c,
                "SELECT * FROM information_schema.tikv_store_status");
            p(++step, "TiKV store", String.join(" ; ", stores));

            long t0 = System.currentTimeMillis();
            repo.dropAndCreateTable();
            p(++step, "DDL", "DROP+CREATE TABLE account  OK  (" + (System.currentTimeMillis() - t0) + " ms)");

            List<String> names = new ArrayList<>();
            for (int i = 1; i <= 100; i++) names.add("user" + i);
            t0 = System.currentTimeMillis();
            int inserted = repo.batchInsert(names, 1000L);
            long insertMs = System.currentTimeMillis() - t0;
            long sumAfterInsert = repo.sumBalance();
            p(++step, "批量插入", inserted + " 行, 耗时 " + insertMs + " ms, SUM(balance)=" + sumAfterInsert);
            if (inserted != 100 || sumAfterInsert != 100_000) { System.out.println("   [FAIL] 插入结果不符预期"); fail++; }

            // 事务：成功
            long id1 = firstId(c), id2 = secondId(c);
            long a1 = repo.getBalance(id1), b1 = repo.getBalance(id2);
            repo.transfer(id1, id2, 100);
            long a2 = repo.getBalance(id1), b2 = repo.getBalance(id2);
            boolean okTx = (a2 == a1 - 100) && (b2 == b1 + 100);
            p(++step, "事务-成功", String.format("id%d: %d->%d, id%d: %d->%d  COMMIT %s",
                    id1, a1, a2, id2, b1, b2, okTx ? "OK" : "FAIL"));
            if (!okTx) fail++;

            // 事务：余额不足回滚
            long before = repo.sumBalance();
            String rollbackMsg;
            try {
                repo.transfer(id1, id2, 999_999_999L);
                rollbackMsg = "未触发回滚（异常）";
                fail++;
            } catch (SQLException e) {
                rollbackMsg = "已回滚: " + e.getMessage();
            }
            long after = repo.sumBalance();
            boolean conserved = (before == after);
            p(++step, "事务-回滚", rollbackMsg + " ; SUM(balance) " + before + " -> " + after
                    + (conserved ? "  守恒 OK" : "  守恒 FAIL"));
            if (!conserved) fail++;

            long sumFinal = repo.sumBalance();
            p(++step, "守恒校验", "插入后=100000, 当前=" + sumFinal + (sumFinal == 100_000 ? "  OK" : "  FAIL"));
            if (sumFinal != 100_000) fail++;

            List<String> plan = Report.rows(c, "EXPLAIN SELECT * FROM account WHERE name = 'user7'");
            p(++step, "EXPLAIN", String.join(" ; ", plan));

            p(++step, "聚合查询", repo.stats());

            p(++step, "索引查询", String.join(" ; ",
                Report.rows(c, "SELECT id, name, balance FROM account WHERE name = 'user7'")));
        }

        System.out.println();
        System.out.printf("RESULT: %s (%d/%d)%n", fail == 0 ? "PASS" : "FAIL", 12 - fail, 12);
        return fail;
    }

    // ------------------------------------------------------------------- serve
    private static void serve(Db db, Config cfg) throws Exception {
        // 服务启动时确保表存在，避免空库导致页面报错（幂等，不影响已有数据）
        try (Connection c = db.connect()) {
            new AccountRepo(c).createTableIfNotExists(null);
        }
        HttpApi api = new HttpApi(cfg, db);
        api.start();
        String host = cfg.get("http.host", "127.0.0.1");
        int port = cfg.getInt("http.port", 18080);
        System.out.println("tidb-demo serving on http://" + host + ":" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            api.stop();
            System.out.println("shutdown complete, port " + port + " released");
        }));
        Thread.currentThread().join();
    }

    private static long firstId(Connection c) throws SQLException {
        return Long.parseLong(Report.singleString(c, "SELECT MIN(id) FROM account"));
    }
    private static long secondId(Connection c) throws SQLException {
        return Long.parseLong(Report.singleString(c, "SELECT MIN(id) FROM account WHERE id > (SELECT MIN(id) FROM account)"));
    }
    private static void p(int n, String title, String value) {
        System.out.printf("[%2d] %-12s : %s%n", n, title, value);
    }
}
