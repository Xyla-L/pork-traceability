package com.pork.sales.client;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class DistributionClient {
    private final RestClient client;
    private final String distributionUrl;

    public DistributionClient(RestClient.Builder builder,
                              @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl) {
        this.client = builder.build();
        this.distributionUrl = distributionUrl;
    }

    public Map<String, Object> requireSplit(Long splitBatchId) {
        try {
            Map<String, Object> envelope = client.get()
                    .uri(distributionUrl + "/distribution/splits/{id}", splitBatchId)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !Integer.valueOf(200).equals(envelope.get("code"))
                    || !(envelope.get("data") instanceof Map<?, ?> data)) {
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务返回的分割批次数据无效");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) data;
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务不可用，无法校验分割批次");
        }
    }

    public Map<String, Object> requireReceipt(Long receiptId) {
        try {
            Map<String, Object> envelope = client.get()
                    .uri(distributionUrl + "/distribution/receipts/{id}", receiptId)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !Integer.valueOf(200).equals(envelope.get("code"))
                    || !(envelope.get("data") instanceof Map<?, ?> data)) {
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务返回的签收单数据无效");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) data;
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务不可用，无法校验签收单");
        }
    }

    public Map<String, Object> requireTransport(Long transportId) {        try {
            Map<String, Object> envelope = client.get()
                    .uri(distributionUrl + "/distribution/transports/{id}", transportId)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !Integer.valueOf(200).equals(envelope.get("code"))
                    || !(envelope.get("data") instanceof Map<?, ?> data)) {
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务返回的运输单数据无效");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) data;
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务不可用，无法校验运输单");
        }
    }

    /**
     * 召回登记（内部接口）：把召回单涉及的「门店 × 批次」写进配送的待收运池。
     * <p>
     * 不吞异常，但也不让调用方把召回整单回滚 —— 由 SalesServiceImpl 决定「尽力而为 + 留痕」：
     * 池子写失败时召回本身仍然有效（召回是既成事实），只是收运得人工补录。
     */
    public Map<String, Object> registerRecallCollect(Map<String, Object> body) {
        try {
            Map<String, Object> envelope = client.post()
                    .uri(distributionUrl + "/distribution/internal/recall-collect")
                    .body(body)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !Integer.valueOf(200).equals(envelope.get("code"))
                    || !(envelope.get("data") instanceof Map<?, ?> data)) {
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务未返回召回登记结果");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) data;
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "配送服务不可用，召回待收运未登记");
        }
    }
}
