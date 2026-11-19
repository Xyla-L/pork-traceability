package com.pork.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 自然语言操作（Action）的安全策略。
 *
 * <p>这个类存在的唯一理由：<b>模型说的任何话都不能直接变成一次写操作</b>。
 * 所有会改数据的动作都必须在这里显式放开，否则执行器一律拒绝。
 * 默认配置下只有 GET 可执行，就算模型被提示词注入了也删不掉东西。</p>
 */
@Data
@ConfigurationProperties(prefix = "ai.action")
public class ActionProperties {

    /** 总开关。关掉后只保留图谱查询能力，不执行任何真实调用 */
    private boolean enabled = true;

    /**
     * 执行引擎实际请求的地址。
     * <p>走网关而不是直连目标服务：这样 JWT 校验、X-User-* 注入、黑名单
     * 全部复用现有那一套，ai-service 不自己发明一套身份传递。
     * 直连的话，ai-service 就得自己决定「这个用户是谁」，那才是越权的开始。</p>
     */
    private String gatewayBaseUrl = "http://localhost:8080";

    /** 允许执行的 HTTP 方法。默认只有 GET —— 查询不会毁数据 */
    private Set<String> allowedMethods = new LinkedHashSet<>(Set.of("GET"));

    /**
     * 额外放开的写操作 operationId（精确匹配）。
     * 想放开「创建养殖场」就把 op:breeding-service:POST:/farms 加进来。
     */
    private Set<String> allowedWriteOperations = new LinkedHashSet<>();

    /** 这些服务的接口一律不执行（认证/系统管理不该被自然语言驱动） */
    private Set<String> deniedServices = new LinkedHashSet<>(Set.of("auth-service"));

    /** 路径前缀黑名单：内部接口、系统管理、设备上报 */
    private Set<String> deniedPathPrefixes = new LinkedHashSet<>(
            Set.of("/system", "/internal", "/ingest", "/ai/"));

    /** 单次调用超时（秒） */
    private int timeoutSeconds = 30;

    /** 结果回填给模型的最大字符数，超了截断并告知模型 */
    private int maxResultChars = 4000;

    /** 会话（多轮补槽）存活时间 */
    private int sessionTtlMinutes = 10;

    /** 会话数量上限，防止有人刷 conversationId 打爆内存 */
    private int sessionMax = 5000;

    /** 单用户每分钟最多执行次数 */
    private int rateLimitPerMinute = 20;
}
