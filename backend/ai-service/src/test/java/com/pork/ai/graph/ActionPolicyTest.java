package com.pork.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.ai.config.ActionProperties;
import com.pork.ai.graph.model.ApiOperation;
import com.pork.ai.graph.model.ApiParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行策略测试 —— 这套断言就是「模型被注入时能造成多大破坏」的上界。
 *
 * <p>每一条都对应一种真实存在的越权路径，不要为了让某个功能好用来放宽它们。</p>
 */
class ActionPolicyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ActionExecutor executor(ActionProperties props) {
        return new ActionExecutor(props, mapper, null);
    }

    private ActionProperties defaults() {
        ActionProperties p = new ActionProperties();
        p.setEnabled(true);
        p.setAllowedMethods(Set.of("GET"));
        p.setDeniedServices(Set.of("auth-service"));
        p.setDeniedPathPrefixes(Set.of("/system", "/internal", "/ingest", "/ai/"));
        return p;
    }

    private ApiOperation op(String service, String method, String path, boolean internal) {
        return new ApiOperation(
                "op:" + service + ":" + method + ":" + path,
                service, "mod:" + service + ":x", method, path, "/api/v1" + path,
                "测试接口", "test", "Result", internal,
                List.of(new ApiParam("id", "query", "Long", true, null, List.of())));
    }

    @Test
    @DisplayName("GET 默认允许执行")
    void getIsAllowed() {
        ActionExecutor ex = executor(defaults());
        assertTrue(ex.isExecutable(op("breeding-service", "GET", "/farms", false)));
    }

    @Test
    @DisplayName("POST 默认拒绝 —— 这是最重要的一条")
    void postIsDeniedByDefault() {
        ActionExecutor ex = executor(defaults());
        ApiOperation post = op("breeding-service", "POST", "/farms", false);
        assertFalse(ex.isExecutable(post));
        assertNotNull(ex.denyReason(post));
        assertTrue(ex.denyReason(post).contains("会修改数据"));
    }

    @Test
    @DisplayName("显式登记的写操作才放行")
    void whitelistedWriteIsAllowed() {
        ActionProperties props = defaults();
        props.setAllowedWriteOperations(Set.of("op:breeding-service:POST:/farms"));
        ActionExecutor ex = executor(props);
        assertTrue(ex.isExecutable(op("breeding-service", "POST", "/farms", false)));
        // 没登记的其他写操作仍然拒绝
        assertFalse(ex.isExecutable(op("breeding-service", "DELETE", "/farms", false)));
    }

    @Test
    @DisplayName("内部接口一律拒绝，哪怕它是 GET")
    void internalIsDenied() {
        ActionExecutor ex = executor(defaults());
        ApiOperation internal = op("breeding-service", "GET", "/internal/farms", true);
        assertFalse(ex.isExecutable(internal));
    }

    @Test
    @DisplayName("方法级 /internal 路径也要算内部接口 —— 不能只靠类名判定")
    void internalPathMidSegmentIsDenied() {
        ActionExecutor ex = executor(defaults());
        // internal 传 false 模拟扫描器漏标：真实项目里 sales/distribution/trace 都有
        // 这种把 /internal 写在方法级注解上的 Controller
        ApiOperation op = op("sales-service", "GET", "/sales/internal/products", false);
        assertTrue(op.internal(), "路径含 /internal 段必须被判为服务间接口");
        assertFalse(ex.isExecutable(op), "漏标不能变成可执行的漏洞");
    }

    @Test
    @DisplayName("黑名单前缀要按路径段匹配，不能只 startsWith")
    void deniedPrefixMatchesMidPath() {
        ActionProperties props = defaults();
        props.setDeniedPathPrefixes(Set.of("/admin"));
        ActionExecutor ex = executor(props);
        // 不以 /admin 开头，但中间有完整的 admin 段
        ApiOperation mid = op("sales-service", "GET", "/sales/admin/reports", false);
        assertFalse(ex.isExecutable(mid));
        // 只是前缀字符串像，不是完整路径段，不该被误杀
        assertTrue(ex.isExecutable(op("sales-service", "GET", "/administration/list", false)));
    }

    @Test
    @DisplayName("ai-service 自己的接口经网关后落在 /ai/ 下，拒绝自调用")
    void aiSelfRecursionIsDenied() {
        ActionExecutor ex = executor(defaults());
        ApiOperation self = new ApiOperation(
                "op:ai-service:GET:/graph/stats", "ai-service", "mod:ai-service:x", "GET",
                "/graph/stats", "/api/v1/ai/graph/stats", "架构图谱概览", "stats", "Result", false,
                List.of());
        assertFalse(ex.isExecutable(self), "否则对话接口可以被自己无限套娃调用");
    }

    @Test
    @DisplayName("认证服务不给 AI 调用")
    void authServiceIsDenied() {
        ActionExecutor ex = executor(defaults());
        assertFalse(ex.isExecutable(op("auth-service", "GET", "/users", false)));
    }

    @Test
    @DisplayName("/system 前缀属于系统管理，拒绝")
    void systemPathIsDenied() {
        ActionExecutor ex = executor(defaults());
        assertFalse(ex.isExecutable(op("auth-service", "GET", "/system/users", false)));
    }

    @Test
    @DisplayName("总开关关闭时什么都不执行")
    void disabledBlocksEverything() {
        ActionProperties props = defaults();
        props.setEnabled(false);
        ActionExecutor ex = executor(props);
        assertFalse(ex.isExecutable(op("breeding-service", "GET", "/farms", false)));
    }

    @Test
    @DisplayName("错误文案不能泄漏内网地址")
    void errorMessageIsRedacted() {
        ActionExecutor ex = executor(defaults());
        String msg = ex.shortMessage(new IllegalStateException(
                "Connection refused: getsockopt: localhost/127.0.0.1:8083"));
        assertFalse(msg.contains("127.0.0.1"), msg);
        assertFalse(msg.contains("localhost"), msg);
        assertTrue(msg.contains("内网服务"), msg);
    }

    @Test
    @DisplayName("限频：超过阈值的请求被拒绝")
    void rateLimitWorks() {
        ActionProperties props = defaults();
        props.setRateLimitPerMinute(3);
        ActionExecutor ex = executor(props);

        assertTrue(ex.allow("u1"));
        assertTrue(ex.allow("u1"));
        assertTrue(ex.allow("u1"));
        assertFalse(ex.allow("u1"), "第 4 次应该被限流");
        assertTrue(ex.allow("u2"), "不同用户互不影响");
    }
}
