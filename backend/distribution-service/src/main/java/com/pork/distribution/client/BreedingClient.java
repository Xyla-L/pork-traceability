package com.pork.distribution.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /**
     * 批次内生猪的品种（去重）。供拆分树根节点在批次号后标注「猪的种类」。
     * <p>
     * 品种是展示信息不是判定依据：养殖侧不可用 / 参数为空 / 查不到都返回空列表，
     * 调用方对空值的处理是「不展示」，绝不能因为它把树渲染失败。
     */
    @SuppressWarnings("unchecked")
    public List<String> breedsOf(Collection<Long> pigIds) {
        if (pigIds == null || pigIds.isEmpty()) return List.of();
        List<String> ids = pigIds.stream().filter(Objects::nonNull).distinct().map(String::valueOf).toList();
        if (ids.isEmpty()) return List.of();
        String joined = String.join(",", ids);
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(breedingUrl + "/breeding/internal/pigs/breeds?ids={ids}", joined)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        } catch (RestClientException e) {
            log.warn("生猪品种查询失败 pigIds={}", joined, e);
            return List.of();
        }
        if (body == null || !(body.get("data") instanceof List<?> list)) return List.of();
        return list.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }
}
