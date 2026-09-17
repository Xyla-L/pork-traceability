package com.pork.distribution.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 养殖服务内部调用客户端：用于胴体批次创建/更新时同步推进生猪档案状态
 */
@Slf4j
@Component
public class BreedingClient {
    private final RestClient restClient;
    private final String breedingUrl;

    public BreedingClient(RestClient.Builder builder,
                          @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl) {
        this.restClient = builder.build();
        this.breedingUrl = breedingUrl;
    }

    /**
     * 推进生猪状态（1在养 2已出栏 3已屠宰 4异常）。同步失败仅记录日志，不阻塞业务主流程。
     */
    public void advanceStatus(Long pigId, int status) {
        try {
            restClient.put()
                    .uri(breedingUrl + "/breeding/internal/pigs/{id}/status?status={status}", pigId, status)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("生猪状态同步失败 pigId={}, status={}", pigId, status, e);
        }
    }
}
