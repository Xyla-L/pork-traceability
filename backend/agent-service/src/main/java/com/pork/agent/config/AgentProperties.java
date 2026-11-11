package com.pork.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 智能助手配置。模型供应商、密钥、节奏参数都从配置来，代码里不写死任何一家。 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Llm llm = new Llm();
    private History history = new History();
    private Access access = new Access();

    /**
     * 谁能用这个助手。
     * <p>
     * 现阶段只开给管理员：工具与事实清单还在迭代，一批口径没定死之前不放给一线岗位——
     * 一线看到一条答得不完整的话，比看不到更糟。
     * <p>
     * 用配置而不是写死角色判断，是因为放开时要改的只有一行；工具级的 {@code roles()}
     * 白名单仍然保留在原处，所以放开那天不需要重做一轮权限收窄。
     */
    @Data
    public static class Access {
        /** 允许使用智能助手的角色；空列表＝谁都不许（宁可全关，也不默认全开） */
        private java.util.List<String> roles = java.util.List.of("ADMIN");

        public boolean allows(String role) {
            if (roles == null || roles.isEmpty() || role == null || role.isBlank()) return false;
            String r = role.trim().toUpperCase(java.util.Locale.ROOT);
            return roles.stream().anyMatch(x -> r.equals(x.trim().toUpperCase(java.util.Locale.ROOT)));
        }
    }

    @Data
    public static class Llm {
        /** OpenAI 兼容的 base url，末尾不带 /chat/completions */
        private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
        /** 为空表示「不调用模型」，走本地归纳 */
        private String apiKey = "";
        private String model = "glm-4-flash";
        private int timeoutSeconds = 45;
        private int maxFactLines = 120;
        /**
         * 是否让模型「先思考再回答」，三态：
         * 空串＝不传该参数（各家供应商默认行为，智谱等不认识这个字段的靠这条）；
         * false＝显式关闭（通义千问 qwen3 系默认会思考，关掉能把一次问答从 30 秒压到 10 秒）；
         * true＝显式开启。
         * <p>
         * 本项目的结论都由代码算好，模型只负责把事实说通顺，不需要长篇推理——
         * 这是关思考不会掉质量的原因。
         */
        private String enableThinking = "";

        /** 有没有真的配 key：决定走模型还是走本地归纳 */
        public boolean usable() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    @Data
    public static class History {
        private int maxTurns = 6;
    }
}
