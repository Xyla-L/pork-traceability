package com.pork.ingest.client;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;

/**
 * 业务服务调用客户端（内部接口，均不经过网关鉴权）。
 * <p>
 * 接入层不直接写业务库：业务规则（运输中才能记温度、未到达不能签收、产品未激活不能售出）
 * 都留在各自的服务里，避免接入层绕开业务校验写出脏数据。
 */
@Slf4j
@Component
public class BizClient {

    private final RestClient restClient;

    public BizClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public Map<String, Object> get(String url, Map<String, Object> params) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
            if (params != null) {
                params.forEach((k, v) -> {
                    if (v != null) builder.queryParam(k, v);
                });
            }
            return requireData(restClient.get().uri(builder.build(true).toUriString()).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { }), url);
        } catch (RestClientException e) {
            log.warn("ingest_biz_get_failed url={} err={}", url, e.toString());
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "业务服务不可用: " + url);
        }
    }

    public Map<String, Object> post(String url, Map<String, Object> body) {
        try {
            return requireData(restClient.post().uri(url).body(body == null ? Map.of() : body).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { }), url);
        } catch (RestClientException e) {
            log.warn("ingest_biz_post_failed url={} err={}", url, e.toString());
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "业务服务不可用: " + url);
        }
    }

    /** 统一的 Result 信封解析：code 非 200 视为失败，把业务提示原样抛给设备 */
    private Map<String, Object> requireData(Map<String, Object> envelope, String url) {
        if (envelope == null) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "业务服务无响应: " + url);
        }
        Object code = envelope.get("code");
        if (code != null && !"200".equals(code.toString()) && !Integer.valueOf(200).equals(code)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    Objects.toString(envelope.get("message"), "业务校验未通过"));
        }
        return envelope.get("data") instanceof Map<?, ?> data ? cast(data) : Map.of();
    }

    private Map<String, Object> cast(Map<?, ?> raw) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
