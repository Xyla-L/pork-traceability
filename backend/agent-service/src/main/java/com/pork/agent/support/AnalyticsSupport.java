package com.pork.agent.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 分析类工具共用的小件。
 * <p>
 * 只放「算术」，不放口径：口径（统计哪些单、算不算作废）永远跟着下游接口走，
 * 这里只负责把接口返回的时间字段变成「2 小时 15 分」这种能念的句子。
 */
public final class AnalyticsSupport {

    private static final DateTimeFormatter[] FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    };

    private AnalyticsSupport() {
    }

    /** 下游时间字段兼容两种格式（Jackson 输出与 ISO），解析不了返回 null，不抛异常 */
    public static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter fmt : FORMATS) {
            try {
                return LocalDateTime.parse(value, fmt);
            } catch (Exception ignored) {
                // 试下一种格式
            }
        }
        return null;
    }

    /** 「1 天 3 小时」/「45 分钟」；负值（时钟偏差）按 0 处理 */
    public static String durationText(Duration d) {
        if (d == null || d.isNegative()) return "0 分钟";
        long minutes = d.toMinutes();
        if (minutes < 60) return minutes + " 分钟";
        long hours = minutes / 60;
        if (hours < 48) return hours + " 小时" + (minutes % 60 > 0 ? " " + (minutes % 60) + " 分" : "");
        return (hours / 24) + " 天" + (hours % 24 > 0 ? " " + (hours % 24) + " 小时" : "");
    }

    /** 百分比文本；分母为 0 返回「—」，不返回 NaN */
    public static String percent(long part, long total) {
        if (total <= 0) return "—";
        return String.format("%.0f%%", part * 100.0 / total);
    }

    /** 把编号抺成 * 再归并：同一原因的不同单子聚成一类（与接入诊断同一口径） */
    public static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "未记录原因";
        String key = reason
                .replaceAll("[A-Z]{2,6}[-A-Z0-9]{2,}", "*")
                .replaceAll("\\d+", "*")
                .replaceAll("\\s+", " ")
                .trim();
        return key.length() > 60 ? key.substring(0, 60) + "…" : key;
    }
}
