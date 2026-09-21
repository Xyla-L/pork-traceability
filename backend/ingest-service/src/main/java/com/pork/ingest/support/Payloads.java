package com.pork.ingest.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 设备报文取值工具：设备侧字段类型不严谨（数字可能带引号），统一在这里做宽松解析 */
public final class Payloads {
    private static final DateTimeFormatter DATE_TIME_T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Payloads() {
    }

    public static String str(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public static Integer integer(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal decimal(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 时间解析：支持 "yyyy-MM-dd HH:mm:ss"、"yyyy-MM-dd HH:mm"、ISO 带 T、以及纯日期 */
    public static LocalDateTime time(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) return null;
        String text = value.toString().trim();
        if (text.isEmpty()) return null;
        try {
            if (text.length() == 10) return LocalDate.parse(text).atStartOfDay();
            String normalized = text.replace(' ', 'T');
            if (normalized.length() == 16) normalized += ":00";
            return LocalDateTime.parse(normalized, DATE_TIME_T);
        } catch (Exception e) {
            return null;
        }
    }

    /** 布尔语义的 0/1 字段，默认值可指定 */
    public static Integer flag(Map<String, Object> data, String key, Integer defaultValue) {
        Object value = data == null ? null : data.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        String text = value.toString().trim();
        return switch (text) {
            case "1", "true", "TRUE", "通过", "是" -> 1;
            case "0", "false", "FALSE", "异常", "否" -> 0;
            default -> defaultValue;
        };
    }
}
