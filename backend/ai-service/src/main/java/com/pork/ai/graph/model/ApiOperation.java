package com.pork.ai.graph.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 图谱里的「操作」节点 —— 一个 REST 接口就是一条可被执行的操作。
 *
 * <p>为什么把 method + path 合成一个节点，而不是把「接口」和「HTTP 动作」拆开：
 * 用户说「导出退货单」时，需要的是唯一一个可调用的东西；拆开反而要模型多做一次选择，
 * 而这一步恰恰是模型最容易错的地方。</p>
 *
 * <p>{@code gatewayPath} 是拼好网关前缀后的完整路径，执行器只用它，
 * 不再自己做字符串拼接 —— 前缀规则（StripPrefix=2 还是 3）每个服务都不一样，
 * 放在执行时算，迟早会有一个服务对不上。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiOperation(
        String id,
        String service,
        String moduleId,
        String method,
        String path,
        String gatewayPath,
        String summary,
        String javaMethod,
        String returnType,
        boolean internal,
        List<ApiParam> params
) {

    public ApiOperation {
        params = params == null ? List.of() : List.copyOf(params);
        // 兜底：internal 不能只信上游给的值。
        // 扫描器早期只按「类名含 Internal」判定，而不少 Controller 是把
        // /internal 写在方法级 @GetMapping 上的（如 /sales/internal/products），
        // 于是这些服务间接口被判成可对外执行。这里按路径再判一次，
        // 无论数据来自种子还是运行时 OpenAPI，结论都一致。
        internal = internal || looksInternal(path) || looksInternal(gatewayPath);
    }

    /** 路径段里出现 internal / ingest 即视为服务间接口，不对外暴露 */
    private static boolean looksInternal(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String seg : path.split("/")) {
            String s = seg.toLowerCase(Locale.ROOT);
            if (s.contains("internal") || s.contains("ingest")) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isReadOnly() {
        return "GET".equalsIgnoreCase(method);
    }

    @JsonIgnore
    public List<ApiParam> slots() {
        return params.stream().filter(ApiParam::isSlot).collect(Collectors.toList());
    }

    @JsonIgnore
    public List<ApiParam> requiredSlots() {
        return params.stream().filter(p -> p.isSlot() && p.required()).collect(Collectors.toList());
    }

    @JsonIgnore
    public ApiParam slot(String name) {
        for (ApiParam p : params) {
            if (p.isSlot() && p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 给模型看的自描述块，一行一个接口。
     * 参数用 {@link ApiParam#describe()} 拼，别在这里塞 JSON ——
     * 模型对「一行一个接口 + 参数列表」的解析准确率明显高于嵌套 JSON，
     * 而我们要的恰恰是它别乱编。
     */
    @JsonIgnore
    public String promptLine() {
        String p = slots().isEmpty()
                ? "无参数"
                : slots().stream().map(ApiParam::describe).collect(Collectors.joining("; "));
        return "%s %s | %s | %s | 参数: %s".formatted(id, method, path, summary, p);
    }
}
