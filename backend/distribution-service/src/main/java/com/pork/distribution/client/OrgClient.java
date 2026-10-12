package com.pork.distribution.client;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 机构服务内部调用客户端：用于核对「目的地门店在机构树里真的存在吗」。
 * <p>
 * 门店 ID 是召回圈范围的关键字段（出问题按门店圈货），所以它的权威来源只能是
 * auth-service 的 {@code sys_org}，本服务不自己维护一份门店表 —— 两份就会打架。
 * <p>
 * 与 {@link BreedingClient} 的关键差别：那边同步失败只记日志（不影响主流程），
 * <b>这边查不到 / 服务不可用必须让调用方转人工</b>，绝不能默认当作正常门店继续走。
 * 所以这里区分两种结果：
 * <ul>
 *   <li>查到 → 返回机构信息；</li>
 *   <li>查不到（机构不存在 / 不是门店）→ 返回 {@code null}，由调用方转人工；</li>
 *   <li>服务不可用 → 抛 {@link ErrorCode#REMOTE_CALL_ERROR}，由调用方转人工（不许当成"没有"）。</li>
 * </ul>
 */
@Slf4j
@Component
public class OrgClient {

    private final RestClient restClient;
    private final String authUrl;

    public OrgClient(RestClient.Builder builder,
                     @Value("${services.auth-url:http://localhost:8081}") String authUrl) {
        this.restClient = builder.build();
        this.authUrl = authUrl;
    }

    /** 按 ID 查机构；机构不存在返回 null；服务不可用抛异常（调用方须转人工，不得当成"不存在"） */
    public Map<String, Object> findOrg(Long orgId) {
        try {
            Map<String, Object> envelope = restClient.get()
                    .uri(authUrl + "/auth/internal/orgs/{id}", orgId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null) {
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "机构服务无响应");
            }
            Object code = envelope.get("code");
            if (code != null && !"200".equals(code.toString()) && !Integer.valueOf(200).equals(code)) {
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR,
                        String.valueOf(envelope.getOrDefault("message", "机构查询失败")));
            }
            return envelope.get("data") instanceof Map<?, ?> data ? cast(data) : null;
        } catch (RestClientException e) {
            log.warn("org_lookup_failed orgId={} err={}", orgId, e.toString());
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "机构服务不可用，无法核对门店：" + e.getMessage());
        }
    }

    private Map<String, Object> cast(Map<?, ?> raw) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
