package com.example.tidbdemo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 配置加载：支持 ${ENV:VAR} 占位符，运行时从环境变量注入，避免密码落盘。 */
public final class Config {
    private final Properties props = new Properties();

    private Config(Properties p) { this.props.putAll(p); }

    public static Config load() throws IOException {
        String path = System.getProperty("config");
        Properties raw = new Properties();
        if (path != null && Files.exists(Path.of(path))) {
            try (InputStream in = Files.newInputStream(Path.of(path))) { raw.load(in); }
        } else {
            try (InputStream in = Config.class.getResourceAsStream("/app.properties")) {
                if (in != null) raw.load(in);
            }
        }
        Properties resolved = new Properties();
        for (String k : raw.stringPropertyNames()) {
            resolved.setProperty(k, resolve(raw.getProperty(k)));
        }
        return new Config(resolved);
    }

    private static String resolve(String v) {
        if (v == null) return "";
        String out = v;
        int start;
        while ((start = out.indexOf("${ENV:")) >= 0) {
            int end = out.indexOf('}', start);
            if (end < 0) break;
            String name = out.substring(start + 6, end);
            String val = System.getenv(name);
            if (val == null) val = "";
            out = out.substring(0, start) + val + out.substring(end + 1);
        }
        return out;
    }

    public String get(String key) { return props.getProperty(key); }
    public String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null ? def : v;
    }
    public int getInt(String key, int def) {
        String v = props.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }
}
