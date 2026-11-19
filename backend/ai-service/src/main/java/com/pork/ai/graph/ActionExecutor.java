package com.pork.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.ActionProperties;
import com.pork.ai.graph.model.ApiOperation;
import com.pork.ai.graph.model.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

/**
 * 把「模型说要做的事」变成「一次真实的 HTTP 调用」。
 *
 * <p>这个类是整套功能里唯一有副作用的组件，所以它默认很保守：</p>
 * <ol>
 *   <li><b>只认 operationId</b>：路径从图谱里取，模型给不出任意 URL。
 *       只要它不能控制 URL，就不能诱导服务去访问别的地方。</li>
 *   <li><b>默认只放行 GET</b>：写操作必须在配置里逐个点名。
 *       代价是初次使用要多改一行 yml，收益是模型被注入时删不掉数据。</li>
 *   <li><b>参数按 schema 过滤</b>：模型多给的键一律丢弃，少给的必填项直接拒绝。</li>
 *   <li><b>经网关转发并透传用户令牌</b>：下游服务看到的就是这个用户本人，
 *       所有机构隔离、角色校验照常生效。ai-service 不制造身份。</li>
 * </ol>
 */
@Slf4j
@Component
public class ActionExecutor {

    private static final Pattern NUMERIC_TYPE = Pattern.compile("^(int|long|short|byte|Integer|Long|Short|Byte|BigDecimal|BigInteger|Double|double|Float|float)$");
    private static final Pattern DATE_TYPE = Pattern.compile("^(LocalDate|Date)$");
    private static final Pattern DATE_VALUE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Set<String> SENSITIVE_KEYS =
            Set.of("password", "pwd", "token", "secret", "accessToken", "refreshToken");

    private final ActionProperties props;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    public ActionExecutor(ActionProperties props, ObjectMapper objectMapper,
                          @Qualifier("actionWebClient") WebClient webClient) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
    }

    // ==================== 执行 ====================

    public ExecutionResult execute(ApiOperation op, Map<String, Object> rawSlots,
                                   String authorization, String userId) {
        String deny = policyDeny(op);
        if (deny != null) {
            log.info("ai_action_denied user={} op={} reason={}", userId, op.id(), deny);
            return ExecutionResult.denied(deny);
        }
        if (!allow(userId)) {
            return ExecutionResult.denied("操作太频繁了，请稍后再试。");
        }

        Map<String, Object> slots = rawSlots == null ? Map.of() : rawSlots;

        // 1) 路径变量：缺一个就没法拼出合法 URL，直接拒绝
        Map<String, String> pathVars = new HashMap<>();
        for (ApiParam p : op.params()) {
            if (!p.isPath()) {
                continue;
            }
            String v = str(slots.get(p.name()));
            if (v == null || v.isBlank()) {
                return ExecutionResult.rejected("缺少路径参数 " + p.name());
            }
            pathVars.put(p.name(), v);
        }

        // 2) query 参数：只取 schema 里有的，类型不对直接拒绝（不猜、不转）
        Map<String, String> query = new LinkedHashMap<>();
        for (ApiParam p : op.params()) {
            if (!p.isQuery()) {
                continue;
            }
            Object v = slots.get(p.name());
            String s = str(v);
            if (s == null || s.isBlank()) {
                if (p.required()) {
                    return ExecutionResult.rejected("缺少必填参数 " + p.name());
                }
                continue;
            }
            String typeError = checkType(p, s);
            if (typeError != null) {
                return ExecutionResult.rejected(typeError);
            }
            query.put(p.name(), s);
        }

        URI uri;
        try {
            UriComponents uc = UriComponentsBuilder
                    .fromUriString(props.getGatewayBaseUrl() + op.gatewayPath())
                    .queryParams(toMultiValueMap(query))
                    .buildAndExpand(pathVars)
                    .encode(StandardCharsets.UTF_8);
            uri = uc.toUri();
        } catch (Exception e) {
            log.warn("URL 构造失败 op={} err={}", op.id(), e.toString());
            return ExecutionResult.rejected("参数格式不正确，无法构造请求");
        }

        // 3) body：只有非 GET 才组装
        Object body = null;
        if (!"GET".equalsIgnoreCase(op.method()) && !"DELETE".equalsIgnoreCase(op.method())) {
            body = buildBody(op, slots);
        }

        long start = System.currentTimeMillis();
        try {
            RawResponse resp = call(op, uri, body, authorization);
            long cost = System.currentTimeMillis() - start;
            boolean ok = resp.status() >= 200 && resp.status() < 300 && businessOk(resp.body());
            audit(userId, op, slots, resp.status(), cost, null);
            return new ExecutionResult(ok, true, resp.status(),
                    truncate(resp.body()), cost, ok ? null : firstError(resp));
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            audit(userId, op, slots, -1, cost, e.toString());
            log.warn("ai_action_failed user={} op={} err={}", userId, op.id(), e.toString());
            return new ExecutionResult(false, true, -1, null, cost, "调用失败：" + shortMessage(e));
        }
    }

    private RawResponse call(ApiOperation op, URI uri, Object body, String authorization) {
        WebClient.RequestBodySpec spec = webClient
                .method(HttpMethod.valueOf(op.method().toUpperCase(Locale.ROOT)))
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
        }
        RawResponse resp = spec.exchangeToMono(r -> read(r))
                .block(Duration.ofSeconds(props.getTimeoutSeconds()));
        if (resp == null) {
            throw new IllegalStateException("下游无响应");
        }
        return resp;
    }

    private Mono<RawResponse> read(ClientResponse r) {
        return r.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(b -> new RawResponse(r.statusCode().value(), b));
    }

    // ==================== 策略 ====================

    /**
     * 这个接口能不能通过对话执行。
     * <p>上层拿它过滤候选：把一个注定会被拒的接口喂给模型，
     * 模型选中它之后用户得到的是一句「不支持」，体验上等于没做。</p>
     */
    public boolean isExecutable(ApiOperation op) {
        return policyDeny(op) == null;
    }

    /** 不能执行时给用户看的原因 */
    public String denyReason(ApiOperation op) {
        return policyDeny(op);
    }

    /** 返回非 null 表示拒绝，值是给用户看的原因 */
    private String policyDeny(ApiOperation op) {

        if (!props.isEnabled()) {
            return "自然语言操作功能当前未开启。";
        }
        if (op == null) {
            return "没有选中任何操作。";
        }
        if (op.internal()) {
            return "这个接口是服务间内部接口，不对外开放调用。";
        }
        if (props.getDeniedServices().contains(op.service())) {
            return "出于安全考虑，不允许通过对话操作「" + op.service() + "」的接口。";
        }
        for (String prefix : props.getDeniedPathPrefixes()) {
            if (pathBlocked(op, prefix)) {
                return "这个接口属于系统管理范围，不通过对话执行。";
            }
        }
        String method = op.method().toUpperCase(Locale.ROOT);
        boolean allowedMethod = props.getAllowedMethods().contains(method);
        boolean allowedOp = props.getAllowedWriteOperations().contains(op.id());
        if (!allowedMethod && !allowedOp) {
            return "「" + op.summary() + "」会修改数据，需要管理员显式开启后才能通过对话执行。";
        }
        return null;
    }

    /**
     * 黑名单前缀是否命中。
     *
     * <p>不能用 {@code startsWith} 一把梭：黑名单写的是 {@code /internal}，
     * 而 {@code /sales/internal/products} 并不以它开头 —— 但语义上同样是
     * 服务间接口。所以既要匹配开头，也要匹配「中间某个完整路径段」。</p>
     *
     * <p>同时看 {@code gatewayPath}：ai-service 自己的接口在本地是
     * {@code /action/chat}，只有拼上网关前缀后才落在 {@code /ai/} 下。</p>
     */
    private boolean pathBlocked(ApiOperation op, String prefix) {
        return hitPrefix(op.path(), prefix) || hitPrefix(op.gatewayPath(), prefix);
    }

    private static boolean hitPrefix(String path, String prefix) {
        if (path == null || prefix == null || prefix.isBlank()) {
            return false;
        }
        String p = trimSlash(path);
        String pre = trimSlash(prefix);
        if (pre.isEmpty()) {
            return false;
        }
        if (p.equals(pre) || p.startsWith(pre + "/")) {
            return true;
        }
        // 中间段命中：/sales/internal/products 对 /internal
        return p.contains(pre + "/");
    }

    private static String trimSlash(String s) {
        String r = s;
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }

    private String checkType(ApiParam p, String value) {
        String type = p.type() == null ? "" : p.type();
        if (NUMERIC_TYPE.matcher(type).matches() && !value.matches("^-?\\d+(\\.\\d+)?$")) {
            return "参数 " + p.name() + " 需要是数字，你给的是「" + value + "」";
        }
        if (DATE_TYPE.matcher(type).matches() && !DATE_VALUE.matcher(value).matches()) {
            return "参数 " + p.name() + " 需要是 YYYY-MM-DD 格式的日期，你给的是「" + value + "」";
        }
        if (value.length() > 512) {
            return "参数 " + p.name() + " 太长了";
        }
        return null;
    }

    /**
     * 组装请求体。
     *
     * <p>模型可能给两种形状：{@code slots.body = {...}}（Prompt 要求的），
     * 或者把字段平铺在 slots 顶层（它偷懒时的常见行为）。
     * 两种都接，但只保留 schema 里声明过的字段 —— 多出来的丢掉，
     * 否则可能把 {@code userId} 这类字段一起塞进下游请求。</p>
     */
    @SuppressWarnings("unchecked")
    private Object buildBody(ApiOperation op, Map<String, Object> slots) {
        ApiParam bodyParam = op.params().stream().filter(ApiParam::isBody).findFirst().orElse(null);
        if (bodyParam == null) {
            return null;
        }
        Set<String> allowedFields = bodyParam.bodyFields().stream()
                .map(ApiParam.BodyField::name).collect(java.util.stream.Collectors.toSet());

        Map<String, Object> body = new LinkedHashMap<>();
        Object nested = slots.get("body");
        if (nested instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                if (allowedFields.isEmpty() || allowedFields.contains(k)) {
                    body.put(k, e.getValue());
                }
            }
        }
        if (body.isEmpty()) {
            for (String field : allowedFields) {
                if (slots.containsKey(field)) {
                    body.put(field, slots.get(field));
                }
            }
        }
        return body.isEmpty() ? null : body;
    }

    // ==================== 限频 ====================

    boolean allow(String userId) {
        int limit = props.getRateLimitPerMinute();
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long window = now - 60_000L;
        Deque<Long> bucket = rateBuckets.computeIfAbsent(
                userId == null ? "anonymous" : userId, k -> new ConcurrentLinkedDeque<>());
        // ArrayDeque 本身非线程安全，但每个 bucket 只在这把锁里访问，
        // 用 ConcurrentLinkedDeque 只是为了让它能被放进 ConcurrentHashMap
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < window) {
                bucket.pollFirst();
            }
            if (bucket.size() >= limit) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }

    // ==================== 结果处理 ====================

    /** 业务码也要看：很多服务出错时返回 HTTP 200 + {"code":500} */
    private boolean businessOk(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.isObject() && node.has("code")) {
                return node.path("code").asInt(200) == 200;
            }
        } catch (Exception ignored) {
            // 不是 JSON（比如导出文件），按 HTTP 状态判断
        }
        return true;
    }

    private String firstError(RawResponse resp) {
        if (resp.body() == null || resp.body().isBlank()) {
            return "下游返回 " + resp.status();
        }
        try {
            JsonNode node = objectMapper.readTree(resp.body());
            String msg = node.path("message").asText("");
            return msg.isBlank() ? "下游返回 " + resp.status() : msg;
        } catch (Exception e) {
            return "下游返回 " + resp.status();
        }
    }

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        int max = props.getMaxResultChars();
        return body.length() <= max ? body : body.substring(0, max) + "\n...(已截断)";
    }

    private void audit(String userId, ApiOperation op, Map<String, Object> slots,
                       int status, long cost, String error) {
        log.info("ai_action user={} op={} method={} path={} status={} cost={}ms error={} params={}",
                userId, op.id(), op.method(), op.path(), status, cost,
                error == null ? "-" : error, mask(slots));
    }

    private String mask(Map<String, Object> slots) {
        if (slots == null || slots.isEmpty()) {
            return "{}";
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        slots.forEach((k, v) -> copy.put(k,
                SENSITIVE_KEYS.contains(k) ? "***" : v));
        try {
            return objectMapper.writeValueAsString(copy);
        } catch (Exception e) {
            return "{...}";
        }
    }

    String shortMessage(Throwable e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        // 内网地址不该出现在给用户看的文案里。
        // 除了 IP，主机名也要抹：WebClient 报错时经常是 "Connection refused: localhost/127.0.0.1:8083"，
        // 只替换 IP 会留下一个 "localhost/" 的残渣。
        String cleaned = m
                .replaceAll("\\b\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?", "内网服务")
                .replaceAll("(?i)\\b(localhost|0\\.0\\.0\\.0)(:\\d+)?", "内网服务");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) + "…" : cleaned;
    }

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return s.trim();
        }
        if (v instanceof Double d && d == Math.floor(d)) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(v);
    }

    private org.springframework.util.MultiValueMap<String, String> toMultiValueMap(Map<String, String> src) {
        org.springframework.util.LinkedMultiValueMap<String, String> map =
                new org.springframework.util.LinkedMultiValueMap<>();
        src.forEach(map::add);
        return map;
    }

    private record RawResponse(int status, String body) {
    }

    /**
     * @param executed 是否真的发出了请求（false = 被策略拦下）
     * @param success  下游是否成功
     */
    public record ExecutionResult(boolean success, boolean executed, int status,
                                  String body, long elapsedMs, String error) {

        static ExecutionResult denied(String reason) {
            return new ExecutionResult(false, false, 0, null, 0, reason);
        }

        static ExecutionResult rejected(String reason) {
            return new ExecutionResult(false, false, 0, null, 0, reason);
        }
    }
}
