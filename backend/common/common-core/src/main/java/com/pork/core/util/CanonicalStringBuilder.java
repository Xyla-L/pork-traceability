package com.pork.core.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Produces stable JSON for evidence hashing regardless of map insertion order. */
public final class CanonicalStringBuilder {

    private CanonicalStringBuilder() {
    }

    public static String build(Object value) {
        Object jsonTree = JSON.parse(JSON.toJSONString(value));
        return JSON.toJSONString(normalize(jsonTree),
                JSONWriter.Feature.MapSortField,
                JSONWriter.Feature.WriteBigDecimalAsPlain);
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), normalize(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> normalized = new ArrayList<>(collection.size());
            collection.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }
        return value;
    }
}
