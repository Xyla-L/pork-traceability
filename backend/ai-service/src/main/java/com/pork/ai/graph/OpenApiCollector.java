package com.pork.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.GraphProperties;
import com.pork.ai.graph.model.ApiModule;
import com.pork.ai.graph.model.ApiOperation;
import com.pork.ai.graph.model.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从服务的 {@code /v3/api-docs} 拉取 OpenAPI 并转成图谱节点。
 *
 * <p>为什么还要做这件事，明明已经有静态扫描了：
 * 静态扫描靠正则读 Java 源码，拿不到参数类型、默认值、body 的字段结构，
 * 遇到 record、嵌套类、泛型都只能猜。OpenAPI 是 Spring 自己生成的，
 * 这些信息一次全有。所以线上以 OpenAPI 为准，扫描结果只当离线兜底。</p>
 *
 * <p>解析上刻意保守：任何一个字段拿不到就跳过该字段，而不是抛异常。
 * 图谱少一个参数字段，模型多问一句就能补；整个服务解析失败，那 40 个接口全没了。</p>
 */
@Slf4j
@Component
public class OpenApiCollector {

    private static final List<String> HTTP_METHODS =
            List.of("get", "post", "put", "delete", "patch");

    /** 单个服务的拉取超时：本地服务没起时不能卡住整个重建 */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(8);

    public CollectedService collect(GraphProperties.Source source,
                                    WebClient client,
                                    ObjectMapper mapper) throws Exception {
        String body = client.get()
                .uri(source.getDocsUrl())
                .retrieve()
                .bodyToMono(String.class)
                .block(FETCH_TIMEOUT);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("空响应");
        }
        JsonNode root = mapper.readTree(body);
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            throw new IllegalStateException("响应里没有 paths，可能不是 OpenAPI 文档");
        }
        JsonNode schemas = root.path("components").path("schemas");

        Map<String, ApiModule> modules = new LinkedHashMap<>();
        List<ApiOperation> operations = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> it = paths.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = it.next();
            String path = pathEntry.getKey();
            JsonNode pathNode = pathEntry.getValue();

            for (String method : HTTP_METHODS) {
                JsonNode opNode = pathNode.path(method);
                if (!opNode.isObject()) {
                    continue;
                }
                String tag = firstTag(opNode, source.getService());
                String moduleId = "mod:" + source.getService() + ":" + tag;
                modules.putIfAbsent(moduleId, new ApiModule(
                        moduleId, tag, source.getService(), "",
                        source.isInternal() || isInternalPath(path), ""));

                String summary = text(opNode, "summary");
                if (summary.isBlank()) {
                    summary = text(opNode, "operationId");
                }
                if (summary.isBlank()) {
                    summary = method.toUpperCase(Locale.ROOT) + " " + path;
                }

                List<ApiParam> params = new ArrayList<>();
                JsonNode parameters = opNode.path("parameters");
                if (parameters.isArray()) {
                    for (JsonNode p : parameters) {
                        ApiParam parsed = toParam(p, schemas);
                        if (parsed != null) {
                            params.add(parsed);
                        }
                    }
                }
                ApiParam bodyParam = toBodyParam(opNode, schemas, mapper);
                if (bodyParam != null) {
                    params.add(bodyParam);
                }

                operations.add(new ApiOperation(
                        "op:" + source.getService() + ":" + method.toUpperCase(Locale.ROOT) + ":" + path,
                        source.getService(),
                        moduleId,
                        method.toUpperCase(Locale.ROOT),
                        path,
                        joinPath(source.getGatewayPrefix(), path),
                        summary,
                        text(opNode, "operationId"),
                        "",
                        source.isInternal() || isInternalPath(path),
                        params));
            }
        }
        return new CollectedService(source.getService(),
                new ArrayList<>(modules.values()), operations);
    }

    // ==================== 参数 ====================

    private ApiParam toParam(JsonNode node, JsonNode schemas) {
        String name = text(node, "name");
        String in = text(node, "in");
        if (name.isBlank() || in.isBlank()) {
            return null;
        }
        if ("header".equals(in) || "cookie".equals(in)) {
            // 头参数不当作槽位：让模型去填 X-User-Id 是在教它越权
            return null;
        }
        JsonNode schema = node.path("schema");
        String type = text(schema, "type");
        if (type.isBlank() && schema.has("$ref")) {
            type = refName(schema.path("$ref").asText());
        }
        String def = schema.hasNonNull("default") ? schema.path("default").asText() : null;
        return new ApiParam(name, in, type.isBlank() ? "String" : type,
                node.path("required").asBoolean(false), def, List.of());
    }

    private ApiParam toBodyParam(JsonNode opNode, JsonNode schemas, ObjectMapper mapper) {
        JsonNode requestBody = opNode.path("requestBody");
        if (!requestBody.isObject()) {
            return null;
        }
        JsonNode content = requestBody.path("content");
        JsonNode json = content.has("application/json")
                ? content.path("application/json")
                : content.elements().hasNext() ? content.elements().next() : null;
        if (json == null || json.isMissingNode()) {
            return null;
        }
        JsonNode schema = json.path("schema");
        List<ApiParam.BodyField> fields = fieldsOf(schema, schemas, 0);
        String type = schema.has("$ref") ? refName(schema.path("$ref").asText()) : "object";
        boolean required = requestBody.path("required").asBoolean(false);
        return new ApiParam("body", "body", type, required, null, fields);
    }

    /** 展开 body 的字段。只展开一层——再深就是嵌套对象，模型填不了，也没必要 */
    private List<ApiParam.BodyField> fieldsOf(JsonNode schema, JsonNode schemas, int depth) {
        if (schema == null || depth > 1) {
            return List.of();
        }
        JsonNode resolved = resolve(schema, schemas);
        JsonNode properties = resolved.path("properties");
        if (!properties.isObject()) {
            return List.of();
        }
        List<ApiParam.BodyField> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode f = e.getValue();
            String type = text(f, "type");
            if (type.isBlank() && f.has("$ref")) {
                type = refName(f.path("$ref").asText());
            }
            fields.add(new ApiParam.BodyField(e.getKey(), type.isBlank() ? "object" : type));
        }
        return fields;
    }

    private JsonNode resolve(JsonNode schema, JsonNode schemas) {
        if (schema.has("$ref")) {
            String name = refName(schema.path("$ref").asText());
            JsonNode target = schemas.path(name);
            return target.isMissingNode() ? schema : target;
        }
        return schema;
    }

    private String refName(String ref) {
        int i = ref.lastIndexOf('/');
        return i >= 0 ? ref.substring(i + 1) : ref;
    }

    // ==================== 工具 ====================

    private String firstTag(JsonNode opNode, String fallback) {
        JsonNode tags = opNode.path("tags");
        if (tags.isArray() && !tags.isEmpty()) {
            String t = tags.get(0).asText("");
            if (!t.isBlank()) {
                return t;
            }
        }
        return fallback;
    }

    private boolean isInternalPath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("/internal") || p.contains("/ingest/device");
    }

    private String joinPath(String prefix, String path) {
        String a = prefix == null ? "" : prefix;
        while (a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }
        String b = path.startsWith("/") ? path : "/" + path;
        return a + b;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isValueNode() ? v.asText("") : "";
    }

    /** 一个服务拉取后的结果 */
    public record CollectedService(String service,
                                   List<ApiModule> modules,
                                   List<ApiOperation> operations) {
    }
}
