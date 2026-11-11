package com.pork.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

/**
 * 只读取数通道：agent 只通过它读别的服务，<b>没有任何写方法</b>。
 * <p>
 * 这是本服务最硬的一条约束——不是「约定不要写」，而是代码里根本没有可用于写操作的入口。
 * 想加写能力必须先改这个类，改动会在评审时暴露出来。
 * <p>
 * 身份头原样透传：机构隔离靠的是下游的 {@code OrgScope}，agent 不能把它们抹掉。
 */
@Slf4j
@Component
public class ReadOnlyRemote {

    private final RestClient client;
    private final ObjectMapper mapper;

    public ReadOnlyRemote(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = RestClient.builder().build();
    }

    /**
     * GET 一个返回 {@code {code,message,data}} 的接口，只取 data。
     * 下游报错按「查不到」处理并如实说明，不吞成空结果——空结果会被模型解释成「没有数据」，
     * 那是另一种编造。
     */
    public JsonNode getData(String url, AgentContext ctx) {
        String body;
        try {
            body = client.get()
                    .uri(url)
                    .headers(h -> {
                        if (ctx != null && !ctx.anonymous()) {
                            h.set("X-User-Id", ctx.userId());
                            if (ctx.role() != null) h.set("X-User-Role", ctx.role());
                            if (ctx.orgId() != null && !ctx.orgId().isBlank()) h.set("X-User-Org", ctx.orgId());
                            if (ctx.userName() != null) {
                                h.set("X-User-Name", java.net.URLEncoder.encode(ctx.userName(), StandardCharsets.UTF_8));
                            }
                        }
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("agent_remote_failed url={} status={} body={}", url, e.getStatusCode().value(),
                    truncate(e.getResponseBodyAsString()));
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "取数失败：" + hint(e.getStatusCode().value()));
        } catch (Exception e) {
            log.warn("agent_remote_error url={} err={}", url, e.toString());
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "取数失败：下游服务暂时连不上");
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode code = root.get("code");
            if (code != null && code.asInt() != 200) {
                String msg = root.path("message").asText("");
                log.warn("agent_remote_biz_error url={} code={} msg={}", url, code.asInt(), msg);
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "取数失败：" + (msg.isBlank() ? "下游返回异常" : msg));
            }
            return root.has("data") ? root.get("data") : root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "取数失败：下游返回格式无法解析");
        }
    }

    /** 把「没权限」这类系统提示翻成人能照着做的一句话 */
    private String hint(int status) {
        return switch (status) {
            case 401 -> "登录状态已失效，请重新登录后再问";
            case 403 -> "当前账号没有查看这部分数据的权限";
            case 404 -> "没有找到对应的数据";
            default -> "下游服务返回 " + status;
        };
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
