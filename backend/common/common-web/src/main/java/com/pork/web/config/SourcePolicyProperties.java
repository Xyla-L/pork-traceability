package com.pork.web.config;

import com.pork.core.source.SourcePolicy;
import com.pork.core.source.SourcePolicyRegistry;
import com.pork.core.source.TraceStage;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 来源管控配置。
 * <pre>
 * pork:
 *   trace:
 *     source:
 *       enforce-device-only: true          # 总开关，应急演练时可临时置 false
 *       overrides:                          # 单环节应急覆盖，平时留空
 *         SLAUGHTER_INSPECTION: MANUAL_ALLOWED
 * </pre>
 * 默认全部走 {@link TraceStage} 内置策略，不配就是最严的形态；配置只用于临时收紧或放开。
 */
@Data
@ConfigurationProperties(prefix = "pork.trace.source")
public class SourcePolicyProperties {

    /** 是否强制「设备独占」环节禁止人工新增。默认开启；置 false 仅用于应急演练。 */
    private boolean enforceDeviceOnly = true;

    /** 单环节策略覆盖：key 为 TraceStage 名称，value 为 SourcePolicy 名称 */
    private Map<String, String> overrides = new LinkedHashMap<>();

    /** 解析成运行时注册表；配置不合法（写了不认识的环节/策略）时忽略该项并保持默认，不阻断启动 */
    public SourcePolicyRegistry toRegistry() {
        Map<TraceStage, SourcePolicy> parsed = new LinkedHashMap<>();
        overrides.forEach((stageName, policyName) -> {
            TraceStage stage = TraceStage.of(stageName);
            if (stage == null) return;
            try {
                parsed.put(stage, SourcePolicy.valueOf(policyName.trim().toUpperCase()));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // 配置写错不阻断启动：宁可保持默认的严格策略，也不要让服务起不来
            }
        });
        if (!enforceDeviceOnly) {
            // 应急总开关：把所有内置 DEVICE_ONLY 降到 DEVICE_PREFERRED（保留来源标记，仅放开人工入口）
            for (TraceStage stage : TraceStage.values()) {
                if (stage.defaultPolicy() == SourcePolicy.DEVICE_ONLY && !parsed.containsKey(stage)) {
                    parsed.put(stage, SourcePolicy.DEVICE_PREFERRED);
                }
            }
        }
        return new SourcePolicyRegistry(parsed);
    }
}
