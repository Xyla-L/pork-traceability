package com.pork.agent.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 把下游返回的 JSON 压成「路径: 值」的平铺清单。
 * <p>
 * <b>为什么不直接塞原始 JSON：</b>一是免费小模型对深层嵌套解析不稳，二是原始报文动辄上千字，
 * 既贵又容易答偏。压平后模型看到的就是一张能对着念的表，事实边界清晰，
 * 它编不出清单以外的东西——这也是本服务「不编造」的技术前提。
 * <p>
 * 刻意不按业务字段硬编码：下游加字段不会让这里静默丢掉信息。
 */
public final class JsonFlatten {

    /** 原始报文留档字段：对问答没有价值，且会把提示词撑爆 */
    private static final Set<String> SKIP_KEYS = Set.of("payload", "rawPayload", "evidence", "signature");
    private static final int VALUE_MAX = 160;
    private static final int ARRAY_MAX = 8;

    private JsonFlatten() {
    }

    public static List<String> lines(JsonNode node, int limit) {
        List<String> out = new ArrayList<>();
        walk(node, "", out, limit, 0);
        return out;
    }

    private static void walk(JsonNode node, String path, List<String> out, int limit, int depth) {
        if (out.size() >= limit || node == null || depth > 6) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                if (out.size() >= limit) return;
                if (SKIP_KEYS.contains(e.getKey())) return;
                walk(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), out, limit, depth + 1);
            });
        } else if (node.isArray()) {
            int n = Math.min(node.size(), ARRAY_MAX);
            if (node.size() == 0) {
                out.add(path + ": （空）");
                return;
            }
            for (int i = 0; i < n && out.size() < limit; i++) {
                walk(node.get(i), path + "[" + i + "]", out, limit, depth + 1);
            }
            if (node.size() > n) out.add(path + ": ……另有 " + (node.size() - n) + " 项未展开");
        } else {
            String text = node.asText("");
            if (text.isBlank()) return;
            if (text.length() > VALUE_MAX) text = text.substring(0, VALUE_MAX) + "…";
            out.add(path + ": " + text);
        }
    }
}
