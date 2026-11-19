package com.pork.ai.controller;

import com.pork.ai.config.ActionProperties;
import com.pork.ai.config.GraphProperties;
import com.pork.ai.dto.ActionChatRequest;
import com.pork.ai.dto.ActionChatResponse;
import com.pork.ai.dto.ActionConfirmRequest;
import com.pork.ai.graph.ActionExecutor;
import com.pork.ai.graph.ArchGraphService;
import com.pork.ai.graph.model.OperationMatch;
import com.pork.ai.service.ActionAssistantService;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然语言操作系统。
 *
 * <p>和旁边的 {@link AiAssistantController} 的区别：那个是「说给人听」（SSE 流式文本），
 * 这个是「替人做」（结构化 JSON + 真实执行）。所以这里不用 SSE ——
 * 一次操作的结果是「执行了什么、返回了什么」，不是逐字吐出来的文章。</p>
 *
 * <p>鉴权：本组接口全部要求登录。网关对 {@code /api/v1/ai/**} 只放行
 * consumer 和 health，其余必须带 JWT，并会注入 {@code X-User-Id}。
 * 这里再次校验是因为 ai-service 可能在容器网络内被直连 ——
 * 只要 8089 暴露出去，光靠网关那一层就不够了。</p>
 */
@Slf4j
@Tag(name = "AI 自然语言操作", description = "把一句话翻译成接口调用：图谱路由 → 槽位补全 → 安全执行")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class ActionController {

    private final ActionAssistantService actionService;
    private final ArchGraphService graph;
    private final ActionExecutor actionExecutor;
    private final ActionProperties actionProperties;
    private final GraphProperties graphProperties;

    // ==================== 对话 ====================

    @Operation(summary = "自然语言操作（多轮补参数）")
    @PostMapping("/action/chat")
    public Result<ActionChatResponse> chat(
            @RequestBody ActionChatRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        if (!enabled()) {
            return Result.fail(503, "自然语言操作功能未启用");
        }
        if (isBlank(userId)) {
            return Result.fail(401, "请先登录");
        }
        if (isBlank(authorization)) {
            return Result.fail(401, "缺少访问令牌");
        }
        return Result.success(actionService.chat(request, userId, authorization));
    }

    @Operation(summary = "确认执行待确认的写操作")
    @PostMapping("/action/confirm")
    public Result<ActionChatResponse> confirm(
            @RequestBody ActionConfirmRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        if (!enabled()) {
            return Result.fail(503, "自然语言操作功能未启用");
        }
        if (isBlank(userId) || isBlank(authorization)) {
            return Result.fail(401, "请先登录");
        }
        if (request == null || isBlank(request.conversationId())) {
            return Result.fail(400, "缺少 conversationId");
        }
        return Result.success(actionService.confirm(request.conversationId(), userId, authorization));
    }

    // ==================== 图谱 ====================

    /**
     * 图谱概览。
     * <p>开放给所有登录用户：它只有数量统计，没有业务数据。
     * 出问题时第一个要看的就是这里「接口数是不是 0」。</p>
     */
    @Operation(summary = "架构图谱概览")
    @GetMapping("/graph/stats")
    public Result<Map<String, Object>> stats(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (isBlank(userId)) {
            return Result.fail(401, "请先登录");
        }
        return Result.success(graph.stats());
    }

    /**
     * 调试用检索：只返回命中的接口和分数，不执行任何东西。
     * <p>调 Prompt 之前先看这里 —— 如果这一步就没命中正确接口，
     * 改 Prompt 是白改。</p>
     */
    @Operation(summary = "检索调试：自然语言 → 候选接口")
    @GetMapping("/graph/search")
    public Result<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int topK,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (isBlank(userId)) {
            return Result.fail(401, "请先登录");
        }
        if (isBlank(q)) {
            return Result.fail(400, "缺少参数 q");
        }
        int k = Math.min(Math.max(topK, 1), 20);
        List<OperationMatch> matches = graph.search(q, k);
        List<Map<String, Object>> items = new ArrayList<>();
        for (OperationMatch m : matches) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.operation().id());
            item.put("method", m.operation().method());
            item.put("path", m.operation().path());
            item.put("gatewayPath", m.operation().gatewayPath());
            item.put("summary", m.operation().summary());
            item.put("score", m.score());
            item.put("hits", m.hits());
            // 直接问执行器，不要自己拼「enabled && !internal」——
            // 那样 POST 和 /ai/ 下的接口都会显示成可执行，调试时被误导
            item.put("executable", actionExecutor.isExecutable(m.operation()));
            String deny = actionExecutor.denyReason(m.operation());
            if (deny != null) {
                item.put("denyReason", deny);
            }
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", q);
        body.put("count", items.size());
        body.put("items", items);
        return Result.success(body);
    }

    /**
     * 重建图谱（从各服务 /v3/api-docs 拉真实 schema）。
     *
     * <p>路径故意放在 /ai/admin 下，复用 {@code AdminRoleInterceptor}
     * 的 SUPERVISOR/ADMIN 校验 —— 重建会覆盖全局图谱，普通用户不该有这个权力。</p>
     */
    @Operation(summary = "重建架构图谱（需 SUPERVISOR/ADMIN）")
    @PostMapping("/admin/graph/rebuild")
    public Result<ArchGraphService.RebuildReport> rebuild(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (isBlank(userId)) {
            return Result.fail(401, "请先登录");
        }
        if (!graphProperties.isEnabled()) {
            return Result.fail(503, "架构图谱功能未启用");
        }
        log.info("图谱重建开始，操作人={}", userId);
        return Result.success(graph.rebuild());
    }

    private boolean enabled() {
        return actionProperties.isEnabled() && graphProperties.isEnabled();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
