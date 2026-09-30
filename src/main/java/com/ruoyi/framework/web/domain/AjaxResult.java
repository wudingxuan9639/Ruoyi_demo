package com.ruoyi.framework.web.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * 操作消息提醒（RuoYi 同名类简化版）。
 * 统一 REST 响应体：{"code":200,"msg":"...","data":...}
 */
public class AjaxResult extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    /** 状态码 */
    public static final String CODE_TAG = "code";
    /** 返回内容 */
    public static final String MSG_TAG = "msg";
    /** 数据对象 */
    public static final String DATA_TAG = "data";

    /** 状态类型 */
    public enum Type {
        /** 成功 */
        SUCCESS(200),
        /** 警告 */
        WARN(301),
        /** 错误 */
        ERROR(500);
        private final int value;
        Type(int value) { this.value = value; }
        public int value() { return this.value; }
    }

    public AjaxResult(int code, String msg, Object data) {
        super.put(CODE_TAG, code);
        super.put(MSG_TAG, msg);
        if (data != null) {
            super.put(DATA_TAG, data);
        }
    }

    public AjaxResult(int code, String msg) {
        this(code, msg, null);
    }

    public static AjaxResult success() {
        return new AjaxResult(Type.SUCCESS.value(), "操作成功");
    }

    public static AjaxResult success(String msg) {
        return new AjaxResult(Type.SUCCESS.value(), msg);
    }

    public static AjaxResult success(Object data) {
        return new AjaxResult(Type.SUCCESS.value(), "操作成功", data);
    }

    public static AjaxResult success(String msg, Object data) {
        return new AjaxResult(Type.SUCCESS.value(), msg, data);
    }

    public static AjaxResult error() {
        return new AjaxResult(Type.ERROR.value(), "操作失败");
    }

    public static AjaxResult error(String msg) {
        return new AjaxResult(Type.ERROR.value(), msg);
    }

    public static AjaxResult error(String msg, Object data) {
        return new AjaxResult(Type.ERROR.value(), msg, data);
    }

    public static AjaxResult warn(String msg) {
        return new AjaxResult(Type.WARN.value(), msg);
    }

    /** 便捷追加字段，例如 put("balance", 100) */
    @Override
    public AjaxResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public static Map<String, Object> of(Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put(CODE_TAG, Type.SUCCESS.value());
        map.put(MSG_TAG, "操作成功");
        map.put(DATA_TAG, data);
        return map;
    }
}
