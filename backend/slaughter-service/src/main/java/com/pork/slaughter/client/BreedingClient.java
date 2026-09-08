package com.pork.slaughter.client;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class BreedingClient {
    private final RestClient restClient;
    private final String breedingUrl;

    public BreedingClient(RestClient.Builder builder,
                          @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl) {
        this.restClient = builder.build();
        this.breedingUrl = breedingUrl;
    }

    public PigIdentity byEarTag(String earTagNo) {
        try {
            Map<String, Object> envelope = restClient.get()
                    .uri(breedingUrl + "/breeding/internal/pigs/by-ear-tag?earTagNo={earTagNo}", earTagNo)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !Integer.valueOf(200).equals(envelope.get("code")) || !(envelope.get("data") instanceof Map<?, ?> data))
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "养殖服务返回异常");
            return new PigIdentity(number(data.get("id")), value(data.get("earTagNo")), value(data.get("farmName")));
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException | NumberFormatException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "养殖服务不可用或返回数据异常");
        }
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value != null) return Long.valueOf(value.toString());
        throw new NumberFormatException("missing id");
    }

    private String value(Object value) { return value == null ? null : value.toString(); }

    public record PigIdentity(Long id, String earTagNo, String farmName) { }
}
