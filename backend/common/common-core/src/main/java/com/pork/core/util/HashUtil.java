package com.pork.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic SHA-256 helpers shared by files and blockchain evidence. */
public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Json(Object value) {
        return sha256(CanonicalStringBuilder.build(value));
    }
}
