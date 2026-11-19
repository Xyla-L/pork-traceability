package com.pork.ai.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 架构图谱配置。
 *
 * <p>三类东西放在这里，因为它们都会随环境变：</p>
 * <ul>
 *   <li><b>sources</b>：各服务的 /v3/api-docs 地址。服务名和端口在 Docker 里和本机不一样。</li>
 *   <li><b>synonyms</b>：用户口语 → 系统用词。写死在代码里的话，加一个说法就要发版。</li>
 *   <li><b>stopwords</b>：检索要丢掉的词（「帮我」「一下」），不然它们会命中一切。</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "ai.graph")
public class GraphProperties {

    /** 总开关。关掉之后 /ai/graph/** 和 /ai/action/** 都返回未启用 */
    private boolean enabled = true;

    /** 离线种子文件（classpath），运行时拉不到 OpenAPI 时靠它兜底 */
    private String seedResource = "arch-graph-seed.json";

    /**
     * 启动时是否立刻去拉各服务 OpenAPI。
     * 默认 false：本地开发通常只起一两个服务，启动阶段阻塞在超时上很难受。
     */
    private boolean refreshOnStartup = false;

    /** 交给模型挑选的候选接口数量。太多会稀释注意力，实测 8 是上限 */
    private int maxCandidates = 8;

    /** 低于这个分数视为没命中，直接走「我不确定你要操作哪个功能」 */
    private double minScore = 0.15;

    /** 各服务 OpenAPI 源 */
    private List<Source> sources = new ArrayList<>();

    /**
     * 口语同义词表。
     *
     * <p><b>为什么是 List 而不是 Map&lt;String, List&lt;String&gt;&gt;</b>：
     * Spring Boot 用 {@code ConfigurationPropertyName} 校验 Map 的 key，
     * 只接受小写字母、数字和 {@code -}。中文 key（"养殖场"、"卖"）会被静默丢弃 ——
     * 配置看着写对了，运行时一个都没生效（踩过：本地测试里手动 new 的 props 是好的，
     * 一进 yml 就全失效，排查了半天）。改成 List 之后 key 变成普通字符串值，不再受限。</p>
     */
    private List<Synonym> synonyms = new ArrayList<>();

    /** 不参与检索的词 */
    private Set<String> stopwords = new LinkedHashSet<>();

    /** 一组同义说法。{@code words} 是用户可能说的词，{@code expand} 是要额外加进检索的词 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Synonym {
        private List<String> words = new ArrayList<>();
        private List<String> expand = new ArrayList<>();
    }

    @Data
    public static class Source {
        /** 服务名，必须和 Dify 之外其他地方的叫法一致，比如 breeding-service */
        private String service;
        /** 服务内地址，例如 http://localhost:8082/v3/api-docs */
        private String docsUrl;
        /**
         * 网关前缀。gatewayPath = this + path。
         * <p>为什么不由代码自动推导：StripPrefix 每个服务不一样（breeding 是 3，其余是 2），
         * auth/trace 的服务内路径本身已经带了一级（/auth、/dashboard），
         * 自动推导一定会错，写死反而是对的。</p>
         */
        private String gatewayPrefix;
        private boolean enabled = true;
        /** 该服务的接口是否视为「内部接口」，不给 AI 执行 */
        private boolean internal = false;
    }
}
