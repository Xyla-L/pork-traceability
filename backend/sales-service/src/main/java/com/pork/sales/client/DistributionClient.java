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
}
