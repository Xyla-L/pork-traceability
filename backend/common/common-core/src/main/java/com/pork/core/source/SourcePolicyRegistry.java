package com.pork.core.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 来源策略解析器：默认策略来自 {@link TraceStage}，配置项可做「应急覆盖」。
 * <p>
 * 覆盖只允许把策略往「严」或往「松」调一格，且必须显式配置，属于演练/故障期的临时开关，
 * 不放宽常量本身——否则时间一长就没人记得哪个环节本该禁人工。
 */
public final class SourcePolicyRegistry {

    private final Map<TraceStage, SourcePolicy> overrides;

    public SourcePolicyRegistry(Map<TraceStage, SourcePolicy> overrides) {
        Map<TraceStage, SourcePolicy> copy = new EnumMap<>(TraceStage.class);
        if (overrides != null) {
            overrides.forEach((stage, policy) -> {
                if (stage != null && policy != null) copy.put(stage, policy);
            });
        }
        this.overrides = Collections.unmodifiableMap(copy);
    }

    public static SourcePolicyRegistry defaults() {
        return new SourcePolicyRegistry(Map.of());
    }

    /** 实际生效策略：无设备通道的环节强制退化为「人工可录」，防止把业务锁死 */
    public SourcePolicy resolve(TraceStage stage) {
        if (stage == null) return SourcePolicy.MANUAL_ALLOWED;
        SourcePolicy configured = overrides.get(stage);
        SourcePolicy base = configured != null ? configured : stage.defaultPolicy();
        if (base == SourcePolicy.DEVICE_ONLY && !stage.deviceCapable()) {
            return SourcePolicy.MANUAL_ALLOWED;
        }
        return base;
    }

    public boolean manualBlocked(TraceStage stage) {
        return resolve(stage).manualBlocked();
    }

    public boolean overridden(TraceStage stage) {
        return overrides.containsKey(stage);
    }

    public boolean hasOverrides() {
        return !overrides.isEmpty();
    }

    /** 全量策略视图，供管理端展示「哪个环节必须用设备」 */
    public List<StagePolicy> list() {
        List<StagePolicy> result = new ArrayList<>(TraceStage.values().length);
        for (TraceStage stage : TraceStage.values()) {
            result.add(new StagePolicy(stage, resolve(stage), overridden(stage)));
        }
        return List.copyOf(result);
    }

    /** 单个环节的策略快照 */
    public record StagePolicy(TraceStage stage, SourcePolicy policy, boolean overridden) {
        public String code() {
            return stage.name();
        }

        public String label() {
            return stage.label();
        }

        public String channel() {
            return stage.channel();
        }

        public String targetTable() {
            return stage.targetTable();
        }

        public String policyLabel() {
            return policy.label();
        }

        public String policyDescription() {
            return policy.description();
        }

        public boolean deviceCapable() {
            return stage.deviceCapable();
        }

        public boolean manualBlocked() {
            return policy.manualBlocked();
        }

        public String note() {
            return stage.note();
        }
    }
}
