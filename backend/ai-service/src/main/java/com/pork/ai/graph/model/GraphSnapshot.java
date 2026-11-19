package com.pork.ai.graph.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 某一时刻的图谱快照。
 *
 * <p>为什么用快照 + AtomicReference，而不是直接往 Map 里增删：
 * 重建图谱是「拉 9 个服务的 OpenAPI」这种秒级操作，这期间有请求在检索。
 * 边改边查轻则分数抖动，重则 ConcurrentModificationException。
 * 换整份不可变快照，读侧永远看到一致状态，代价只是一次引用赋值。</p>
 */
public record GraphSnapshot(
        List<ApiModule> modules,
        List<ApiOperation> operations,
        Map<String, ApiModule> moduleById,
        String source,
        Instant builtAt
) {
    public static GraphSnapshot empty() {
        return new GraphSnapshot(List.of(), List.of(), Map.of(), "empty", Instant.now());
    }
}
