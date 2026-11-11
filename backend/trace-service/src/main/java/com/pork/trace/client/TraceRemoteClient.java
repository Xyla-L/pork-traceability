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
    private final String authUrl;

    public TraceRemoteClient(RestClient.Builder builder,
            @Value("${services.distribution-url:http://localhost:8084}") String distributionUrl,
            @Value("${services.sales-url:http://localhost:8085}") String salesUrl,
            @Value("${services.blockchain-url:http://localhost:8087}") String blockchainUrl,
            @Value("${services.breeding-url:http://localhost:8082}") String breedingUrl,
            @Value("${services.slaughter-url:http://localhost:8083}") String slaughterUrl,
            @Value("${services.auth-url:http://localhost:8081}") String authUrl) {
        this.restClient = builder.build();
        this.distributionUrl = distributionUrl;
        this.salesUrl = salesUrl;
        this.blockchainUrl = blockchainUrl;
        this.breedingUrl = breedingUrl;
        this.slaughterUrl = slaughterUrl;
        this.authUrl = authUrl;
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
    public Object breedingPigs(Integer status) { return get(breedingUrl + "/pigs?pageNum=1&pageSize=1&status=" + status); }
    public Object slaughterStamps(String startDate) { return get(slaughterUrl + "/slaughter/stamps?pageNum=1&pageSize=1&status=1&startDate=" + startDate); }
    public Object transports(Integer status) { return get(distributionUrl + "/distribution/transports?pageNum=1&pageSize=1&status=" + status); }
    public Object batches() { return get(distributionUrl + "/distribution/batches?pageNum=1&pageSize=1"); }
    public Object salesQrs() { return get(salesUrl + "/sales/qrcodes?pageNum=1&pageSize=1"); }
    public Object warnings() { return get(salesUrl + "/sales/warnings?pageNum=1&pageSize=200&handled=0"); }
    public Object chainRecords() { return get(blockchainUrl + "/blockchain/records?pageNum=1&pageSize=200"); }

    // ==================== 分角色工作台取数 ====================
    // 工作台不再只服务管理员：每个角色要看得懂自己那一环。下面这些方法只做「搬运」，
    // 口径一律由各业务服务定，这里不重算、不翻译。

    /** 入场查验分页（可按到达日期过滤） */
    public Object slaughterEntries(String startDate, int size) {
        return get(slaughterUrl + "/slaughter/entries?pageNum=1&pageSize=" + size + dateParam("startDate", startDate));
    }

    /** 屠宰检验分页（宰前 1 / 宰后 2 / 宰后同步 3） */
    public Object slaughterInspections(String startDate, int size) {
        return get(slaughterUrl + "/slaughter/inspections?pageNum=1&pageSize=" + size + dateParam("startDate", startDate));
    }

    /** 盖章记录分页（带单号与耳标，供屠宰岗看「今天盖了哪些」） */
    public Object slaughterStampPage(String startDate, int size) {
        return get(slaughterUrl + "/slaughter/stamps?pageNum=1&pageSize=" + size + dateParam("startDate", startDate));
    }

    /** 出栏申报按状态计数：{0待审批, 1已通过, 2已驳回} */
    public Object applyCounts() { return get(breedingUrl + "/applies/counts"); }

    /** 出栏申报分页 */
    public Object applyPage(Integer approvalStatus, int size) {
        return get(breedingUrl + "/applies?current=1&size=" + size
                + (approvalStatus == null ? "" : "&approvalStatus=" + approvalStatus));
    }

    /** 生猪分页（status 为空＝全部有效档案） */
    public Object pigPage(Integer status, int size) {
        return get(breedingUrl + "/pigs?current=1&size=" + size
                + (status == null ? "" : "&status=" + status));
    }

    /** 运单分页：1待发车 2运输中 3待签收 4已签收 */
    public Object transportPage(Integer status, int size) {
        return get(distributionUrl + "/distribution/transports?pageNum=1&pageSize=" + size
                + (status == null ? "" : "&status=" + status));
    }

    /** 零售经营统计：销售额/销量/趋势/部位排行；{@code storeId} 非空时只统计该门店 */
    public Object salesStats(int days, Long storeId) {
        return get(salesUrl + "/sales/internal/stats/summary?days=" + days
                + (storeId == null ? "" : "&storeId=" + storeId));
    }

    /**
     * 某门店的未处理预警。
     * <p>
     * 走内部统计接口拿不到预警明细，所以直接调列表接口 —— 但**必须带上门店**：
     * 零售商的「临期 3 件」如果来自全平台列表，卡片和下面的表格就对不上，
     * 而且他还看得见别家店的商品名。
     */
    public Object warningsForStore(Long storeId) {
        return get(salesUrl + "/sales/warnings?pageNum=1&pageSize=200&handled=0&storeId=" + storeId);
    }

    /** 机构（门店名）：auth-service 的内部接口，仅供服务间调用 */
    public Object org(Long orgId) { return get(authUrl + "/auth/internal/orgs/" + orgId); }

    private String dateParam(String name, String value) {
        return value == null || value.isBlank() ? "" : "&" + name + "=" + value;
    }

    private String ids(List<Long> ids) {
        return ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    private Object get(String url) {
        try {
            Map<String, Object> envelope = restClient.get().uri(url).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (!ok(envelope)) throw downstream(envelope);
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
            if (!ok(envelope)) throw downstream(envelope);
            return envelope.get("data");
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, "溯源下游服务不可用");
        }
    }

    private boolean ok(Map<String, Object> envelope) {
        return envelope != null && Integer.valueOf(200).equals(envelope.get("code"));
    }

    /**
     * 下游的业务说明原样带上去，别统一盖成「下游服务返回异常」。
     * <p>
     * 消费者扫到一个已作废的码，sales 明明回了「该商品已被作废，不再作为有效销售记录」，
     * 盖掉之后就只剩一句系统错误，用户不知道为什么扫不出来，只能来问客服。
     */
    private BusinessException downstream(Map<String, Object> envelope) {
        Object message = envelope == null ? null : envelope.get("message");
        String text = message == null ? "" : message.toString().trim();
        // 下游文案里的「（操作人 system-reconcile）」是内部审计细节，
        // 消费者只需要知道「这码为什么不能用」，不需要知道是哪个账号操作的
        text = text.replaceAll("[（(]操作人[^）)]*[）)]", "").trim();
        return new BusinessException(ErrorCode.REMOTE_CALL_ERROR,
                text.isEmpty() ? "下游服务返回异常" : text);
    }
}
