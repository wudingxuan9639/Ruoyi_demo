package com.example.tidbdemo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内嵌 HTTP 服务：提供管理页面（/）+ CRUD 接口（/api/*）。
 * 只绑定 127.0.0.1，使用 JDK 自带 HttpServer，无第三方依赖、不额外占用端口。
 */
public final class HttpApi {
    private static final String PAGE = "/webui/index.html";
    private static final int MAX_QUERY_ROWS = 200;

    private final HttpServer server;
    private final ExecutorService pool;
    private final Config cfg;
    private final Db db;
    private final Map<String, HttpHandler> routes = new HashMap<>();

    public HttpApi(Config cfg, Db db) throws IOException {
        this.cfg = cfg;
        this.db = db;
        String host = cfg.get("http.host", "127.0.0.1");
        int port = cfg.getInt("http.port", 18080);
        this.server = HttpServer.create(new InetSocketAddress(host, port), cfg.getInt("http.backlog", 64));
        this.pool = Executors.newFixedThreadPool(8);
        server.setExecutor(pool);
        bindRoutes();
    }

    private void bindRoutes() {
        // 页面
        route("/", ex -> {
            String p = ex.getRequestURI().getPath();
            if (p.equals("/") || p.equals("/index.html") || p.equals("/ui")) {
                html(ex);
            } else {
                text(ex, 404, "404 Not Found: " + p + "\n\n可用入口:\n"
                    + "  GET  /               管理页面\n"
                    + "  GET  /api/info       数据库与后端信息\n"
                    + "  GET  /api/accounts   列表（支持 keyword/limit/offset，或 ?id= 查单条）\n"
                    + "  POST /api/accounts   新增 {name,balance}\n"
                    + "  PUT  /api/accounts   修改 {id,name,balance}\n"
                    + "  DELETE /api/accounts 删除 {id}\n"
                    + "  POST /api/transfer   转账 {from,to,amount}\n"
                    + "  GET  /api/stats      聚合统计\n"
                    + "  POST /api/query      只读 SQL {sql}\n");
            }
        });

        route("/api/info", ex -> {
            try (Connection c = db.connect()) {
                String v = Report.singleString(c, "SELECT VERSION()");
                String dbName = Report.singleString(c, "SELECT DATABASE()");
                String stores = String.join(" ; ", Report.rows(c, "SELECT * FROM information_schema.tikv_store_status"));
                json(ex, 200, String.format(
                    "{\"status\":\"UP\",\"db\":\"UP\",\"version\":\"%s\",\"database\":\"%s\","
                  + "\"host\":\"%s\",\"port\":%d,\"jdbc\":\"%s\",\"stores\":\"%s\"}",
                    Json.esc(v), Json.esc(dbName),
                    cfg.get("http.host", "127.0.0.1"), cfg.getInt("http.port", 18080),
                    Json.esc(cfg.get("jdbc.url", "")), Json.esc(stores)));
            } catch (Exception e) {
                json(ex, 200, "{\"status\":\"UP\",\"db\":\"DOWN\",\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            }
        });

        route("/api/accounts", ex -> {
            String m = ex.getRequestMethod().toUpperCase(Locale.ROOT);
            try (Connection c = db.connect()) {
                AccountRepo repo = new AccountRepo(c);
                switch (m) {
                    case "GET" -> {
                        String idStr = q(ex, "id");
                        if (idStr != null) {
                            String row = repo.getAccount(Long.parseLong(idStr.trim()));
                            if (row == null) json(ex, 404, "{\"error\":\"记录不存在\"}");
                            else json(ex, 200, row);
                            return;
                        }
                        int limit = clamp(q(ex, "limit"), 20, 1, 500);
                        int offset = clamp(q(ex, "offset"), 0, 0, 1_000_000);
                        String kw = q(ex, "keyword");
                        List<String> rows = repo.searchAccounts(kw, limit, offset);
                        long total = repo.countAccounts(kw);
                        json(ex, 200, String.format("{\"total\":%d,\"limit\":%d,\"offset\":%d,\"rows\":[%s]}",
                                total, limit, offset, String.join(",", rows)));
                    }
                    case "POST" -> {
                        String body = readBody(ex);
                        String name = Json.str(body, "name");
                        long bal = Json.lng(body, "balance");
                        long id = repo.insertAccount(name, bal);
                        json(ex, 200, String.format("{\"inserted_id\":%d,\"name\":\"%s\",\"balance\":%d}",
                                id, Json.esc(name), bal));
                    }
                    case "PUT" -> {
                        String body = readBody(ex);
                        long id = Json.lng(body, "id");
                        String name = Json.str(body, "name");
                        long bal = Json.lng(body, "balance");
                        int n = repo.updateAccount(id, name, bal);
                        if (n == 0) json(ex, 404, "{\"error\":\"记录不存在: id=" + id + "\"}");
                        else json(ex, 200, String.format(
                            "{\"updated\":%d,\"id\":%d,\"name\":\"%s\",\"balance\":%d}", n, id, Json.esc(name), bal));
                    }
                    case "DELETE" -> {
                        String body = readBody(ex);
                        String idStr = q(ex, "id");
                        long id = idStr != null ? Long.parseLong(idStr.trim()) : Json.lng(body, "id");
                        int n = repo.deleteAccount(id);
                        if (n == 0) json(ex, 404, "{\"error\":\"记录不存在: id=" + id + "\"}");
                        else json(ex, 200, String.format("{\"deleted\":%d,\"id\":%d}", n, id));
                    }
                    default -> text(ex, 405, "Method Not Allowed: " + m);
                }
            } catch (SQLException e) {
                json(ex, 400, "{\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            } catch (Exception e) {
                json(ex, 500, "{\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            }
        });

        route("/api/transfer", ex -> {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { text(ex, 405, "use POST"); return; }
            try (Connection c = db.connect()) {
                String body = readBody(ex);
                long from = Json.lng(body, "from");
                long to = Json.lng(body, "to");
                long amt = Json.lng(body, "amount");
                new AccountRepo(c).transfer(from, to, amt);
                json(ex, 200, String.format(
                    "{\"ok\":true,\"from\":%d,\"to\":%d,\"amount\":%d,\"from_balance\":%d,\"to_balance\":%d}",
                    from, to, amt, new AccountRepo(c).getBalance(from), new AccountRepo(c).getBalance(to)));
            } catch (SQLException e) {
                json(ex, 400, "{\"ok\":false,\"rolled_back\":true,\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            } catch (Exception e) {
                json(ex, 500, "{\"ok\":false,\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            }
        });

        route("/api/stats", ex -> {
            try (Connection c = db.connect()) {
                json(ex, 200, new AccountRepo(c).stats());
            } catch (Exception e) {
                json(ex, 500, "{\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            }
        });

        route("/api/query", ex -> {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { text(ex, 405, "use POST"); return; }
            String sql = Json.str(readBody(ex), "sql").trim();
            String head = sql.toLowerCase(Locale.ROOT);
            if (!(head.startsWith("select") || head.startsWith("show")
               || head.startsWith("explain") || head.startsWith("desc") || head.startsWith("with"))) {
                json(ex, 400, "{\"error\":\"出于安全限制，只允许 SELECT / SHOW / EXPLAIN / DESC 语句\"}");
                return;
            }
            try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setMaxRows(MAX_QUERY_ROWS + 1);
                boolean hasRs = ps.execute();
                if (!hasRs) { json(ex, 200, "{\"columns\":[],\"rows\":[],\"note\":\"该语句无结果集\"}"); return; }
                StringBuilder cols = new StringBuilder();
                StringBuilder rows = new StringBuilder();
                try (ResultSet rs = ps.getResultSet()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) cols.append(',');
                        cols.append('"').append(Json.esc(md.getColumnLabel(i))).append('"');
                    }
                    int cnt = 0;
                    while (rs.next() && cnt < MAX_QUERY_ROWS) {
                        cnt++;
                        if (cnt > 1) rows.append(',');
                        rows.append('[');
                        for (int i = 1; i <= n; i++) {
                            if (i > 1) rows.append(',');
                            String v = rs.getString(i);
                            rows.append(v == null ? "null" : '"' + Json.esc(v) + '"');
                        }
                        rows.append(']');
                    }
                    json(ex, 200, String.format("{\"columns\":[%s],\"rows\":[%s],\"rowcount\":%d,\"truncated\":%s}",
                            cols, rows, cnt, rs.next() ? "true" : "false"));
                }
            } catch (SQLException e) {
                json(ex, 400, "{\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            } catch (Exception e) {
                json(ex, 500, "{\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            }
        });

        // ---- 旧路径兼容 ----
        route("/health", ex -> {
            try (Connection c = db.connect()) {
                json(ex, 200, String.format("{\"status\":\"UP\",\"db\":\"UP\",\"version\":\"%s\"}",
                        Report.singleString(c, "SELECT VERSION()")));
            } catch (Exception e) {
                json(ex, 500, "{\"status\":\"UP\",\"db\":\"DOWN\",\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            }
        });
        route("/accounts", ex -> {
            try (Connection c = db.connect()) {
                json(ex, 200, "[" + String.join(",", new AccountRepo(c).listAccounts(100)) + "]");
            } catch (Exception e) {
                json(ex, 500, "{\"error\":\"" + Json.esc(e.getMessage()) + "\"}");
            }
        });
        route("/transfer", ex -> routeToApi(ex, "/api/transfer"));
        route("/stats", ex -> routeToApi(ex, "/api/stats"));
    }

    /** 注册路由并保存 handler，供旧路径复用 */
    private void route(String path, HttpHandler h) {
        routes.put(path, h);
        server.createContext(path, h);
    }

    /** 旧路径转发到 /api/* 的同一 handler */
    private void routeToApi(HttpExchange ex, String apiPath) {
        try {
            routes.get(apiPath).handle(ex);
        } catch (IOException e) {
            try { json(ex, 500, "{\"error\":\"" + Json.esc(e.getMessage()) + "\"}"); } catch (IOException ignored) {}
        }
    }

    public void start() { server.start(); }

    /** SIGTERM 时立即释放端口 */
    public void stop() {
        server.stop(0);
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------ utils

    private void html(HttpExchange ex) throws IOException {
        byte[] b;
        try (InputStream in = getClass().getResourceAsStream(PAGE)) {
            if (in == null) { text(ex, 500, "页面资源缺失: " + PAGE); return; }
            b = readAll(in);
        }
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String q(HttpExchange ex, String key) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) return null;
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i < 0) continue;
            if (part.substring(0, i).equals(key)) {
                return URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static int clamp(String v, int def, int min, int max) {
        if (v == null) return def;
        try { return Math.max(min, Math.min(max, Integer.parseInt(v.trim()))); }
        catch (NumberFormatException e) { return def; }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void text(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static void json(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

}
