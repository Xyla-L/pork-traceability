package com.pork.ingest.service.handler;

import com.pork.ingest.support.DispatchResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 通道处理器公用的小工具 */
final class Handlers {

    /**
     * 业务服务侧统一用 "yyyy-MM-dd HH:mm:ss" 解析时间（全局 Jackson 配置），
     * 这里不要直接 toString()——LocalDateTime.toString() 带 'T'，下游会解析失败。
     */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Handlers() {
    }

    static String time(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    /**
     * 把业务服务的标准回执翻译成接入层的处理结论。
     * <p>
     * 业务内部接口统一返回 {@code {written, id, reason, retryable}}：
     * <ul>
     *   <li>written=true ⇒ 已入账</li>
     *   <li>written=false + retryable=true ⇒ 待人工处理（条件可能被人工补齐，如"运输尚未到达"）</li>
     *   <li>written=false + retryable=false ⇒ 拒绝（确定性错误，如"该运单已签收"）</li>
     * </ul>
     */
    static DispatchResult toResult(Map<String, Object> result, String targetTable) {
        if (result == null) {
            return DispatchResult.manual(targetTable, "业务服务未返回处理结果");
        }
        String reason = result.get("reason") == null ? "业务校验未通过" : result.get("reason").toString();
        if (Boolean.TRUE.equals(result.get("written"))) {
            return DispatchResult.accepted(longValue(result.get("id")), targetTable);
        }
        return Boolean.TRUE.equals(result.get("retryable"))
                ? DispatchResult.manual(targetTable, reason)
                : DispatchResult.rejected(targetTable, reason);
    }

    static Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
