package com.pork.core.util;

import java.util.UUID;

public final class TraceContext {
    public static final String HEADER = "X-Trace-Id";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() { }

    public static String create() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getOrCreate() {
        String value = CURRENT.get();
        if (value == null) {
            value = create();
            CURRENT.set(value);
        }
        return value;
    }

    public static void set(String value) {
        CURRENT.set(value);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
