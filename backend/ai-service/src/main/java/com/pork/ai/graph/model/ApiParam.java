package com.pork.ai.graph.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 图谱里的一个参数槽位。
 *
 * 字段和 {@code arch-graph-seed.json} 一一对应，来源有两处：
 *   1. 离线静态扫描（scripts/build-arch-graph.py）—— 扫 <code>@RequestParam</code> 等注解
 *   2. 运行时 OpenAPI（/v3/api-docs）—— 拿 springdoc 生成的真实 schema
 *
 * 两种来源的字段名保持一致，所以这里加 ignoreUnknown，
 * 扫描器多写几个辅助字段（simple / bodyType）也不会把反序列化搞挂。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiParam(
        String name,
        String in,
        String type,
        boolean required,
        String defaultValue,
        List<BodyField> bodyFields
) {

    /** 请求体对象里的一个字段（只有 in=body 时才有值） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BodyField(String name, String type) {
    }

    public ApiParam {
        bodyFields = bodyFields == null ? List.of() : List.copyOf(bodyFields);
    }

    @JsonIgnore
    public boolean isPath() {
        return "path".equals(in);
    }

    @JsonIgnore
    public boolean isQuery() {
        return "query".equals(in);
    }

    @JsonIgnore
    public boolean isBody() {
        return "body".equals(in);
    }

    /**
     * 是否是需要用户提供的槽位。
     *
     * header 一律排除：X-User-Id / X-User-Role 这些是网关注入的身份，
     * 如果当成槽位让模型去填，等于把越权的口子直接递给 LLM。
     */
    @JsonIgnore
    public boolean isSlot() {
        return isPath() || isQuery() || isBody();
    }

    /** 给 Prompt 用的一行描述，例如 {@code format(String, 必填) - 导出格式} */
    @JsonIgnore
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('(').append(type == null ? "String" : type);
        if (required) {
            sb.append(", 必填");
        } else if (defaultValue != null && !defaultValue.isBlank()) {
            sb.append(", 默认 ").append(defaultValue);
        } else {
            sb.append(", 可选");
        }
        sb.append(')');
        if (isBody() && !bodyFields.isEmpty()) {
            sb.append(" {");
            for (int i = 0; i < bodyFields.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                BodyField f = bodyFields.get(i);
                sb.append(f.name()).append(':').append(f.type());
            }
            sb.append('}');
        }
        return sb.toString();
    }
}
