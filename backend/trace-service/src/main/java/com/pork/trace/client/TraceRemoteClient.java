package com.pork.trace.client;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TraceRemoteClient {
    private final RestClient restClient;
    private final String distributionUrl;
    private final String salesUrl;
    private final String blockchainUrl;
    private final String breedingUrl;
    private final String slaughterUrl;

    public TraceRemoteClient(RestClient.Builder builder,
            @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl,
            @Value("${services.sales-url:http://localhost:8085}") String salesUrl,
            @Value("${services.blockchain-url:http://localhost:8087}") String blockchainUrl,
            @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
            @Value("${services.slaughter-url:http://localhost:8083}") String slaughterUrl) {
        this.restClient = builder.build();
        this.distributionUrl = distributionUrl;
        this.salesUrl = salesUrl;
        this.blockchainUrl = blockchainUrl;
        this.breedingUrl = breedingUrl;
        this.slaughterUrl = slaughterUrl;
    }

    public Object productByQr(String qrCode) { return get(salesUrl + "/sales/internal/products/qr/" + qrCode); }
    public Object split(Long id) { return get(distributionUrl + "/distribution/splits/" + id); }
    public Object upstream(Long id) { return get(distributionUrl + "/distribution/internal/splits/" + id + "/upstream"); }
    public Object tree(String batchNo) { return get(distributionUrl + "/distribution/splits/tree/" + batchNo); }
    public Object downstream(String batchNo) { return get(distributionUrl + "/distribution/internal/batches/" + batchNo + "/downstream"); }
    public Object productsBySplits(List<Long> ids) { return get(salesUrl + "/sales/internal/products?splitIds=" + ids(ids)); }
    public Object chainStatus(String key) { return get(blockchainUrl + "/blockchain/status/" + key); }
    public Object verify(String bizType, Long bizId) {
        return post(blockchainUrl + "/blockchain/verify", Map.of("bizType", bizType, "bizId", bizId));
    }
    public Object breedingByPigs(List<Long> ids) { return get(breedingUrl + "/breeding/internal/pigs?ids=" + ids(ids)); }
    public Object slaughterByPigs(List<Long> ids) { return get(slaughterUrl + "/slaughter/internal/pigs?ids=" + ids(ids)); }
    public Object breedingPigs(Integer status) { return get(breedingUrl + "/breeding/pigs?pageNum=1&pageSize=1&status=" + status); }
    public Object slaughterStamps(String startDate) { return get(slaughterUrl + "/slaughter/stamps?pageNum=1&pageSize=1&status=1&startDate=" + startDate); }
    public Object transports(Integer status) { return get(distributionUrl + "/distribution/transports?pageNum=1&pageSize=1&status=" + status); }
    public Object batches() { return get(distributionUrl + "/distribution/batches?pageNum=1&pageSize=1"); }
    public Object salesQrs() { return get(salesUrl + "/sales/qrcodes?pageNum=1&pageSize=1"); }
    public Object warnings() { return get(salesUrl + "/sales/warnings?pageNum=1&pageSize=200&handled=0"); }
    public Object chainRecords() { return get(blockchainUrl + "/blockchain/records?pageNum=1&pageSize=200"); }

    private String ids(List<Long> ids) {
        return ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Object get(String url) {
        try {
            Map<String, Object> envelope = restClient.get().uri(url).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !Integer.valueOf(200).equals(envelope.get("code")))
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "下游服务返回异常");
            return envelope.get("data");
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "溯源下游服务不可用");
        }
    }

    private Object post(String url, Object body) {
        try {
            Map<String, Object> envelope = restClient.post().uri(url).body(body).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !Integer.valueOf(200).equals(envelope.get("code")))
                throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "下游服务返回异常");
            return envelope.get("data");
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "溯源下游服务不可用");
        }
    }
}
