package com.pork.agent.controller;

import com.pork.agent.config.AgentProperties;
import com.pork.agent.dto.ChatRequest;
import com.pork.agent.dto.ChatResponse;
import com.pork.agent.entity.AgentMessage;
import com.pork.agent.entity.AgentThread;
import com.pork.agent.service.AgentChatService;
import com.pork.agent.support.AgentContext;
import com.pork.agent.tool.ToolRegistry;
import com.pork.core.exception.BusinessException;
import com.pork.core.enums.ErrorCode;
import com.pork.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能助手接口。
 * <p>
 * 网关已保证到达这里的请求都带 {@code X-User-*}（且调用方自己填的会被丢弃），
 * 所以身份只从请求头读，不从请求体读——否则「换个 userId 就能翻别人的会话」。
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentChatService chatService;
    private final ToolRegistry registry;
    private final AgentProperties props;

    @PostMapping("/chat")
    @Operation(summary = "提问")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request, AgentContext ctx) {
        requireAccess(ctx);
        return Result.success(chatService.chat(request, ctx));
    }

    @GetMapping("/threads")
    @Operation(summary = "我的会话")
    public Result<List<AgentThread>> threads(AgentContext ctx) {
        requireAccess(ctx);
        return Result.success(chatService.threads(ctx));
    }

    @GetMapping("/threads/{id}/messages")
    @Operation(summary = "会话消息")
    public Result<List<AgentMessage>> messages(@PathVariable Long id, AgentContext ctx) {
        requireAccess(ctx);
        return Result.success(chatService.messages(id, ctx));
    }

    /**
     * 能力与运行状态：前端据此显示「当前是模型作答还是直接取数」，
     * 用户不会因为一句措辞生硬就以为系统坏了，也不会误以为背后有个大模型在编答案。
     */
    @GetMapping("/meta")
    public Result<Map<String, Object>> meta(AgentContext ctx) {
        requireAccess(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("capabilities", registry.capabilities(ctx.role()));
        data.put("capabilityDetails", registry.capabilityDetails(ctx.role()));
        data.put("llmEnabled", props.getLlm().usable());
        data.put("model", props.getLlm().usable() ? props.getLlm().getModel() : "");
        data.put("readonly", true);
        return Result.success(data);
    }

    /**
     * 准入闸门：只有配置里列出的角色能用。
     * <p>
     * 为什么要拦在后端而不是只隐藏入口：前端隐藏只挡住「点不到」，挡不住直接调接口。
     * 这个助手能读全平台数据，藏起来不等于关掉。
     * <p>
     * 空角色直接拒——宁可让人重新登录，也不要拿「没有角色」当成「管理员」放进来。
     */
    private void requireAccess(AgentContext ctx) {
        if (!props.getAccess().allows(ctx.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "智能助手当前仅对系统管理员开放");
        }
    }
}
