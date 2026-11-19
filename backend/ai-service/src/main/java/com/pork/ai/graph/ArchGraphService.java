package com.pork.ai.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.GraphProperties;
import com.pork.ai.graph.model.ApiModule;
import com.pork.ai.graph.model.ApiOperation;
import com.pork.ai.graph.model.ApiParam;
import com.pork.ai.graph.model.GraphSnapshot;
import com.pork.ai.graph.model.OperationMatch;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 架构知识图谱：把「系统长什么样」变成可检索的结构。
 *
 * <p>这是整套自然语言操作的地基。它解决一个具体问题：
 * 千问只有几百 token 的上下文能用来描述接口，不可能把 200 多个接口一次塞进去 ——
 * 硬塞的结果是它挑一个「看起来像」的，然后在参数上瞎编。
 * 所以先在图谱里把候选收敛到个位数，再让模型在候选项里做选择题。</p>
 *
 * <p><b>为什么叫「图谱」而不是「索引」：</b>
 * 纯倒排只能回答「哪个接口包含这个词」。图谱多一层模块归属：
 * 「导出退货单」命中的是 trace-service 的退货单接口，同属「退货管理」模块的
 * 「退货单详情」「退货单列表」也会被一并提到候选里 ——
 * 用户说「退货单」有时要的是列表而不是导出，候选里有兄弟接口，模型才有得挑。</p>
 *
 * <p><b>两套数据源：</b>
 * 启动读 classpath 里的静态种子（scripts/build-arch-graph.py 扫源码生成），
 * 之后可以调 /ai/graph/rebuild 从各服务的 /v3/api-docs 覆盖成真实 schema。
 * 种子的价值是「一个服务都没起也能用」，OpenAPI 的价值是「参数类型和默认值更准」。</p>
 */
@Slf4j
@Service
public class ArchGraphService {

    private final GraphProperties props;
    private final ObjectMapper objectMapper;
    private final WebClient plainWebClient;
    private final OpenApiCollector openApiCollector;

    /**
     * 打分饱和常数（K）。
     *
     * <p>最终得分用 {@code raw / (raw + K)}。实测一次正常命中（摘要词 + 路径词 + 同义词扩展）
     * 的原始分在 15~30，对应 0.6~0.75；只命中一个泛词的弱匹配停在 0.2 附近。
     * <b>不要改回「除以本次最高分」</b>：那会让任何一次孤立命中都变成 1.0。</p>
     */
    private static final double REFERENCE_SCORE = 10.0;

    private volatile GraphSnapshot snapshot = GraphSnapshot.empty();
    private volatile Index index = Index.empty();

    public ArchGraphService(GraphProperties props,
                            ObjectMapper objectMapper,
                            @Qualifier("plainWebClient") WebClient plainWebClient,
                            OpenApiCollector openApiCollector) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.plainWebClient = plainWebClient;
        this.openApiCollector = openApiCollector;
    }

    @PostConstruct
    public void init() {
        if (!props.isEnabled()) {
            log.info("架构图谱已关闭（ai.graph.enabled=false）");
            return;
        }
        loadSeed();
        if (props.isRefreshOnStartup()) {
            // 放线程里跑：拉 9 个服务的 OpenAPI，任何一个超时都会把启动拖慢
            Thread t = new Thread(() -> {
                try {
                    rebuild();
                } catch (Exception e) {
                    log.warn("启动期刷新图谱失败，继续使用种子数据: {}", e.toString());
                }
            }, "arch-graph-refresh");
            t.setDaemon(true);
            t.start();
        }
    }

    // ==================== 加载 ====================

    private void loadSeed() {
        String resource = props.getSeedResource();
        ClassPathResource res = new ClassPathResource(resource);
        if (!res.exists()) {
            log.warn("找不到图谱种子文件 {}，图谱为空。"
                    + "请先运行 python scripts/build-arch-graph.py 生成。", resource);
            return;
        }
        try (InputStream in = res.getInputStream()) {
            GraphFile file = objectMapper.readValue(in, GraphFile.class);
            apply(file.modules(), file.operations(), "seed");
            log.info("架构图谱已从种子加载: 模块 {} 个, 接口 {} 个",
                    snapshot.modules().size(), snapshot.operations().size());
        } catch (Exception e) {
            log.error("图谱种子解析失败: {}", e.toString(), e);
        }
    }

    /**
     * 从各服务的 OpenAPI 重建图谱。
     *
     * <p>失败策略是「部分成功」：某个服务没起就跳过它，用种子里该服务的旧数据补齐，
     * 而不是整份重建失败。本地开发时常见一半服务没起，全有或全无等于没法用。</p>
     */
    public synchronized RebuildReport rebuild() {
        if (!props.isEnabled()) {
            return new RebuildReport(false, "图谱功能未启用", 0, 0, List.of());
        }
        List<OpenApiCollector.CollectedService> collected = new ArrayList<>();
        List<RebuildReport.ServiceResult> results = new ArrayList<>();

        for (GraphProperties.Source source : props.getSources()) {
            if (!source.isEnabled()) {
                continue;
            }
            try {
                OpenApiCollector.CollectedService got = openApiCollector.collect(
                        source, plainWebClient, objectMapper);
                collected.add(got);
                results.add(new RebuildReport.ServiceResult(
                        source.getService(), true, got.operations().size(), ""));
            } catch (Exception e) {
                log.warn("拉取 {} 的 OpenAPI 失败: {}", source.getService(), e.toString());
                results.add(new RebuildReport.ServiceResult(
                        source.getService(), false, 0, e.getMessage()));
            }
        }

        if (collected.isEmpty()) {
            log.warn("没有任何服务返回 OpenAPI，图谱保持原样");
            return new RebuildReport(false, "所有服务的 OpenAPI 均不可达，图谱未变更",
                    snapshot.operations().size(), snapshot.modules().size(), results);
        }

        // 用新数据覆盖同 id 节点，种子里剩下的（没拉到的服务）保留
        Map<String, ApiModule> modules = new LinkedHashMap<>(snapshot.moduleById());
        Map<String, ApiOperation> ops = new LinkedHashMap<>();
        for (ApiOperation op : snapshot.operations()) {
            ops.put(op.id(), op);
        }
        for (OpenApiCollector.CollectedService got : collected) {
            for (ApiModule m : got.modules()) {
                modules.put(m.id(), m);
            }
            for (ApiOperation op : got.operations()) {
                ops.put(op.id(), op);
            }
        }
        apply(new ArrayList<>(modules.values()), new ArrayList<>(ops.values()), "openapi");
        log.info("架构图谱已重建: 模块 {} 个, 接口 {} 个（{} 个服务在线）",
                snapshot.modules().size(), snapshot.operations().size(), collected.size());
        return new RebuildReport(true, "重建完成",
                snapshot.operations().size(), snapshot.modules().size(), results);
    }

    private void apply(List<ApiModule> modules, List<ApiOperation> operations, String source) {
        Map<String, ApiModule> moduleById = new LinkedHashMap<>();
        for (ApiModule m : modules) {
            moduleById.put(m.id(), m);
        }
        GraphSnapshot next = new GraphSnapshot(
                List.copyOf(modules), List.copyOf(operations),
                Map.copyOf(moduleById), source, Instant.now());
        this.snapshot = next;
        this.index = buildIndex(next);
    }

    // ==================== 索引 ====================

    private Index buildIndex(GraphSnapshot snap) {
        Map<String, Map<String, Double>> inverted = new HashMap<>();
        Map<String, Set<String>> docTokens = new HashMap<>();
        Set<String> stop = normalizedStopwords();

        for (ApiOperation op : snap.operations()) {
            Map<String, Double> weights = new HashMap<>();
            // 权重不是拍脑袋：摘要是人写给人看的短句，最能代表意图；
            // 路径次之（/return-order/export 里的 return-order 很关键）；
            // 参数名最后，它只能辅助确认，不能决定选哪个接口。
            putWeight(weights, op.summary(), 3.0, stop);
            putWeight(weights, op.javaMethod(), 1.5, stop);
            putWeight(weights, op.service(), 0.5, stop);
            ApiModule module = snap.moduleById().get(op.moduleId());
            if (module != null) {
                putWeight(weights, module.name(), 2.0, stop);
                putWeight(weights, module.description(), 1.0, stop);
            }
            for (String t : GraphText.pathTokens(op.path())) {
                if (!stop.contains(t)) {
                    weights.merge(t, 2.0, Math::max);
                }
            }
            for (ApiParam p : op.params()) {
                if (p.name() != null && !stop.contains(p.name().toLowerCase(Locale.ROOT))) {
                    weights.merge(p.name().toLowerCase(Locale.ROOT), 1.0, Math::max);
                }
                for (ApiParam.BodyField f : p.bodyFields()) {
                    if (f.name() != null && !stop.contains(f.name().toLowerCase(Locale.ROOT))) {
                        weights.merge(f.name().toLowerCase(Locale.ROOT), 0.8, Math::max);
                    }
                }
            }
            docTokens.put(op.id(), weights.keySet());
            for (Map.Entry<String, Double> e : weights.entrySet()) {
                inverted.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                        .put(op.id(), e.getValue());
            }
        }
        return new Index(inverted, docTokens, snap.operations().size());
    }

    private void putWeight(Map<String, Double> weights, String text, double weight, Set<String> stop) {
        for (String t : GraphText.tokens(text)) {
            if (!stop.contains(t) && t.length() > 1) {
                weights.merge(t, weight, Math::max);
            }
        }
    }

    private Set<String> normalizedStopwords() {
        Set<String> set = new LinkedHashSet<>();
        for (String s : props.getStopwords()) {
            set.add(s.toLowerCase(Locale.ROOT));
        }
        return set;
    }

    // ==================== 检索 ====================

    /**
     * 自然语言 → 候选接口。
     *
     * @param query 用户原话
     * @param topK  取前几个
     * @return 按得分降序，分值已归一化到 0~1
     */
    public List<OperationMatch> search(String query, int topK) {
        Index idx = this.index;
        GraphSnapshot snap = this.snapshot;
        if (idx.docCount == 0 || query == null || query.isBlank()) {
            return List.of();
        }

        Set<String> qTokens = expandQuery(query);
        if (qTokens.isEmpty()) {
            return List.of();
        }

        Map<String, Double> raw = new HashMap<>();
        Map<String, List<String>> hits = new HashMap<>();

        for (String token : qTokens) {
            Map<String, Double> posting = idx.inverted.get(token);
            if (posting == null || posting.isEmpty()) {
                continue;
            }
            // idf：在越少的接口里出现，这个词越有区分度。
            // 「查询」这种词几乎命中一半接口，权重自然被压下去。
            double idf = Math.log(1.0 + (double) idx.docCount / (1 + posting.size()));
            for (Map.Entry<String, Double> e : posting.entrySet()) {
                raw.merge(e.getKey(), e.getValue() * idf, Double::sum);
                hits.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(token);
            }
        }
        if (raw.isEmpty()) {
            return List.of();
        }

        // 意图动词加权：说「删除」时 DELETE 应该压过同名的 GET；
        // 反过来，说了「查询」还去匹配写接口，要压下去 ——
        // 用户说「查一下各养殖场」时，/farms 的 GET 和 DELETE 都会命中「养殖场」，
        // 不区分动词的话 DELETE 会混进候选，模型偶尔真的会挑它。
        String methodHint = methodHint(query);
        if (methodHint != null) {
            for (ApiOperation op : snap.operations()) {
                double factor = methodHint.equalsIgnoreCase(op.method()) ? 1.40 : 0.65;
                raw.computeIfPresent(op.id(), (k, v) -> v * factor);
            }
        }

        // 模块扩散：同模块的兄弟接口沾一点光，用于「用户其实想要列表」的情况
        Map<String, Double> moduleMax = new HashMap<>();
        Map<String, ApiOperation> opById = new LinkedHashMap<>();
        for (ApiOperation op : snap.operations()) {
            opById.put(op.id(), op);
            double v = raw.getOrDefault(op.id(), 0.0);
            if (v > 0) {
                moduleMax.merge(op.moduleId(), v, Math::max);
            }
        }

        Map<String, Double> finalScore = new HashMap<>();
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            ApiOperation op = opById.get(e.getKey());
            if (op == null) {
                continue;
            }
            double bonus = moduleMax.getOrDefault(op.moduleId(), 0.0) * 0.10;
            double score = e.getValue() + bonus;
            // 内部接口降权：它们不对外开放执行，但词面往往非常匹配
            // （「新建养殖场」会撞上 /internal/ingest/farm-batch），
            // 不压下去就会把真正能用的接口挤出候选。
            if (op.internal()) {
                score *= 0.40;
            }
            finalScore.put(e.getKey(), score);
        }

        double max = finalScore.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max <= 0) {
            return List.of();
        }

        List<OperationMatch> matches = new ArrayList<>();
        for (Map.Entry<String, Double> e : finalScore.entrySet()) {
            // 用饱和函数而不是「除以本次最高分」：
            // 除以 max 会让任何一次孤立命中都显示成 1.0（踩过：「看看有哪些过期的产品」
            // 把区块链迁移接口顶成满分，界面上看起来像百分之百确定）。
            // raw/(raw+K) 单调、可跨 query 比较，弱命中就老老实实停在 0.2 附近。
            double score = e.getValue() / (e.getValue() + REFERENCE_SCORE);
            if (score < props.getMinScore()) {
                continue;
            }
            ApiOperation op = opById.get(e.getKey());
            matches.add(new OperationMatch(op, round(score), hits.getOrDefault(e.getKey(), List.of())));
        }
        matches.sort(Comparator
                .comparingDouble(OperationMatch::score).reversed()
                // 同分时读操作优先、参数少的优先：
                // 用户问「查养殖场」时，无参数的 GET /farms 比 DELETE /farms/{id} 更可能是想要的
                .thenComparing(m -> m.operation().isReadOnly() ? 0 : 1)
                // 同类结果里，集合查询优先于单条详情：
                // 用户说「各养殖场」想要的是列表，不是 GET /farms/{id}
                .thenComparing(m -> m.operation().path().contains("{") ? 1 : 0)
                .thenComparingInt(m -> m.operation().slots().size()));
        return matches.size() > topK ? new ArrayList<>(matches.subList(0, topK)) : matches;
    }

    /** 查询词 = 原词 + 同义词扩展，再去掉停用词 */
    private Set<String> expandQuery(String query) {
        Set<String> stop = normalizedStopwords();
        Set<String> tokens = new LinkedHashSet<>(GraphText.tokens(query));
        // 同义词按「配置里的说法是否出现在原话里」匹配，而不是按分词结果匹配：
        // 配置里常是两三个字的词组（「退货单」），2-gram 之后不一定保留成整体
        for (GraphProperties.Synonym syn : props.getSynonyms()) {
            if (syn.getWords() == null) {
                continue;
            }
            boolean hit = syn.getWords().stream()
                    .anyMatch(w -> w != null && !w.isBlank() && query.contains(w));
            if (hit && syn.getExpand() != null) {
                for (String v : syn.getExpand()) {
                    tokens.addAll(GraphText.tokens(v));
                }
            }
        }
        tokens.removeIf(t -> stop.contains(t) || t.length() < 2);
        return tokens;
    }

    private String methodHint(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        if (containsAny(q, "删除", "删掉", "作废", "移除", "delete")) {
            return "DELETE";
        }
        if (containsAny(q, "新增", "创建", "新建", "添加", "录入", "登记", "create", "add")) {
            return "POST";
        }
        if (containsAny(q, "修改", "更新", "编辑", "改成", "标记", "设为", "update")) {
            return "PUT";
        }
        if (containsAny(q, "查询", "查看", "看看", "列出", "列表", "统计", "导出",
                "有多少", "多少", "查", "看", "列", "找", "get", "list", "export")) {
            return "GET";
        }
        return null;
    }

    private boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ==================== 查询接口 ====================

    public GraphSnapshot snapshot() {
        return snapshot;
    }

    public ApiOperation findById(String id) {
        if (id == null) {
            return null;
        }
        for (ApiOperation op : snapshot.operations()) {
            if (op.id().equals(id)) {
                return op;
            }
        }
        return null;
    }

    public Map<String, Object> stats() {
        GraphSnapshot s = snapshot;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", props.isEnabled());
        map.put("source", s.source());
        map.put("builtAt", s.builtAt() == null ? null : s.builtAt().toString());
        map.put("moduleCount", s.modules().size());
        map.put("operationCount", s.operations().size());
        map.put("serviceCount", s.operations().stream()
                .map(ApiOperation::service).distinct().count());
        map.put("internalCount", s.operations().stream()
                .filter(ApiOperation::internal).count());
        map.put("readOnlyCount", s.operations().stream()
                .filter(ApiOperation::isReadOnly).count());
        map.put("services", s.operations().stream()
                .collect(Collectors.groupingBy(ApiOperation::service, LinkedHashMap::new, Collectors.counting())));
        return map;
    }

    // ==================== 内部类型 ====================

    private record Index(Map<String, Map<String, Double>> inverted,
                         Map<String, Set<String>> docTokens,
                         int docCount) {
        static Index empty() {
            return new Index(Map.of(), Map.of(), 0);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphFile(List<ApiModule> modules, List<ApiOperation> operations, String source) {
        GraphFile {
            modules = modules == null ? List.of() : modules;
            operations = operations == null ? List.of() : operations;
        }
    }

    /** 重建结果，给运维接口看 */
    public record RebuildReport(boolean success, String message, int operationCount,
                                int moduleCount, List<ServiceResult> services) {
        public record ServiceResult(String service, boolean ok, int operationCount, String error) {
        }
    }
}
