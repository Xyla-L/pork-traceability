package com.pork.distribution.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 销售服务内部调用：召回收运送到无害化处理中心签收后，把召回单收口。
 * <p>
 * 为什么召回状态由配送去「通知」销售、而不是配送自己改：召回单归销售（{@code recall_order}），
 * 执行状态的唯一写入口在那边。配送这边只知道「货到了、签收了」这个物理事实，
 * 跨界直接写别人的表等于制造第二个写入口。
 * <p>
 * 调用失败只记日志、不抛异常：签收是现场已经发生的事（也已经上链），
 * 不能因为下游没响应就把「货已经无害化」这件事回滚掉。
 */
@Slf4j
@Component
public class SalesClient {

    private final RestClient client;
    private final String salesUrl;

    public SalesClient(RestClient.Builder builder,
                       @Value("${services.sales-url:http://localhost:8085}") String salesUrl) {
        this.client = builder.build();
        this.salesUrl = salesUrl;
    }

    /**
     * 把召回单标记为已完成（sales 侧幂等：重复回调不会重复上链、不会重复通知）。
     *
     * @return 是否成功收口；失败返回 false，由调用方在回执里留痕
     */
    public boolean completeRecall(Long recallId, String transportNo) {
        if (recallId == null) return false;
        try {
            Map<String, Object> envelope = client.post()
                    .uri(salesUrl + "/sales/internal/recalls/{id}/complete?transportNo={no}",
                            recallId, transportNo == null ? "" : transportNo)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            Object code = envelope == null ? null : envelope.get("code");
            boolean ok = code == null || "200".equals(code.toString()) || Integer.valueOf(200).equals(code);
            if (!ok) {
                log.error("recall_complete_rejected recallId={} transportNo={} resp={}", recallId, transportNo, envelope);
            }
            return ok;
        } catch (RestClientException e) {
            // 这条日志是「召回为什么一直挂在执行中」的唯一线索，必须带够上下文
            log.error("recall_complete_failed recallId={} transportNo={} err={}", recallId, transportNo, e.toString());
            return false;
        }
    }
}
