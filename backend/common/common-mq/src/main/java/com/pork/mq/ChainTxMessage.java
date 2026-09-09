package com.pork.mq;

import com.pork.core.util.HashUtil;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChainTxMessage(
        String eventId,
        String bizType,
        Long bizId,
        String bizKey,
        String contentHash,
        Map<String, Object> payload,
        LocalDateTime occurredAt
) implements Serializable {
    public ChainTxMessage {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        if (bizType == null || bizType.isBlank()) throw new IllegalArgumentException("bizType is required");
        if (bizId == null) throw new IllegalArgumentException("bizId is required");
        if (bizKey == null || bizKey.isBlank()) throw new IllegalArgumentException("bizKey is required");
        if (contentHash == null || !contentHash.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("contentHash must be a SHA-256 hex string");
        payload = payload == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
    }

    public static ChainTxMessage create(String bizType, Long bizId, String bizKey,
                                        String contentHash, Map<String, Object> payload) {
        String eventId = HashUtil.sha256(bizType + ':' + bizId + ':' + contentHash);
        return new ChainTxMessage(eventId, bizType, bizId, bizKey, contentHash, payload, LocalDateTime.now());
    }
}
