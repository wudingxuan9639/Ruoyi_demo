package com.example.tidbdemo;

/** 极简 JSON 取值与转义（避免引入第三方依赖）。 */
public final class Json {
    private Json() {}

    public static String str(String body, String key) {
        String v = raw(body, key);
        return v == null ? "" : v;
    }

    public static long lng(String body, String key) {
        String v = raw(body, key);
        if (v == null) return 0;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** 转义为合法 JSON 字符串内容，防止名称里的引号/换行破坏响应结构 */
    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.toString();
    }

    private static String raw(String body, String key) {
        if (body == null) return null;
        String q = "\"" + key + "\"";
        int i = body.indexOf(q);
        if (i < 0) return null;
        int c = body.indexOf(':', i + q.length());
        if (c < 0) return null;
        int s = c + 1;
        while (s < body.length() && Character.isWhitespace(body.charAt(s))) s++;
        if (s < body.length() && body.charAt(s) == '"') {
            int e = body.indexOf('"', s + 1);
            return e < 0 ? null : body.substring(s + 1, e);
        }
        int e = s;
        while (e < body.length() && (Character.isDigit(body.charAt(e)) || body.charAt(e) == '-')) e++;
        return body.substring(s, e);
    }
}
