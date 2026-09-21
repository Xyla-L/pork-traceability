package com.pork.slaughter.client;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

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

    /**
     * 推进生猪状态（1在养 2已出栏 3已屠宰 4异常）。下游同步失败不抛出异常，避免阻塞业务主流程。
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
     * 产地检疫证核验（设备通道用）：确认证存在、未过期、且证上登记的耳标与闸口扫描的一致。
     * 远程异常时按「核验不通过」返回，由调用方决定拦下还是转人工，不向上抛异常。
     */
    public CertVerify verifyCert(String certNo, String earTagNo) {
        if (certNo == null || certNo.isBlank()) return new CertVerify(false, "未提供检疫证号");
        try {
            String uri = breedingUrl + "/breeding/internal/quarantine-certs/verify?certNo={certNo}";
            if (earTagNo != null && !earTagNo.isBlank()) uri += "&earTagNo={earTagNo}";
            Map<String, Object> envelope = earTagNo == null || earTagNo.isBlank()
                    ? restClient.get().uri(uri, certNo).retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() { })
                    : restClient.get().uri(uri, certNo, earTagNo).retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !(envelope.get("data") instanceof Map<?, ?> data)) {
                return new CertVerify(false, "检疫证核验服务返回异常");
            }
            boolean valid = Boolean.TRUE.equals(data.get("valid"));
            return new CertVerify(valid, value(data.get("reason")));
        } catch (RestClientException e) {
            log.warn("检疫证核验调用失败 certNo={}", certNo, e);
            return new CertVerify(false, "检疫证核验服务不可用");
        }
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value != null) return Long.valueOf(value.toString());
        throw new NumberFormatException("missing id");
    }

    private String value(Object value) { return value == null ? null : value.toString(); }

    public record PigIdentity(Long id, String earTagNo, String farmName) { }

    public record CertVerify(boolean valid, String reason) { }
}
