package com.pork.ai.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 送给模型前的数据瘦身。
 *
 * 为什么需要它：
 *   `/trace/search` 返回的一条全链路记录近 1 万字，其中大半是 contentHash、
 *   rawPayload、fileIds 这类审计字段。PromptFactory 的 6000 字上限一截断，
 *   正好把排在后面的 downstream（门店流向）切掉，模型就会回答「查不到门店」——
 *   数据明明在库里，只是没挤进 prompt。
 *
 * 裁剪规则（只删噪音，不删结论）：
 *   1. 丢弃审计类字段：哈希、原始报文、文件 id、创建/更新时间等
 *   2. 列表最多保留 8 条，超出标注「还有 N 条」
 *   3. 单个字符串超过 300 字截断（长备注/长地址对回答没帮助）
 *   4. 嵌套最多 6 层，防止异常结构把递归撑爆
 *
 * ⚠️ 不要在这里「挑业务字段」：字段名各服务不统一，硬编码白名单迟早漏。
 *    黑名单 + 数量/长度上限是稳的，漏掉的只是噪音，不会漏结论。
 */
public final class PayloadCondenser {

    private static final Set<String> DROP_KEYS = Set.of(
            "contentHash", "prevHash", "dataHash", "txHash", "blockHash",
            "rawPayload", "rawPayloadJson", "payloadJson",
            "fileIds", "sourceRef", "traceId",
            "tenantId", "delFlag", "voidFlag",
            "createTime", "updateTime", "createBy", "updateBy"
    );

    private static final int MAX_LIST = 8;
    private static final int MAX_STRING = 300;
    private static final int MAX_DEPTH = 6;

    private PayloadCondenser() {
    }

    public static Object condense(Object value) {
        return walk(value, 0);
    }

    private static Object walk(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            return "…";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (DROP_KEYS.contains(key)) {
                    continue;
                }
                out.put(key, walk(entry.getValue(), depth + 1));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            int limit = Math.min(list.size(), MAX_LIST);
            for (int i = 0; i < limit; i++) {
                out.add(walk(list.get(i), depth + 1));
            }
            if (list.size() > MAX_LIST) {
                out.add("（还有 " + (list.size() - MAX_LIST) + " 条未列出）");
            }
            return out;
        }
        if (value instanceof String s) {
            return s.length() > MAX_STRING ? s.substring(0, MAX_STRING) + "…" : s;
        }
        return value;
    }
}
