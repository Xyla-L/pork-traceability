package com.pork.distribution.client;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 屠宰服务内部调用客户端：胴体批次创建/变更前核对生猪是否被瘦肉精阻断（阳性 / 同批次阳性 / 未检），
 * 以及是否已完成胴体盖章。
 * <p>
 * 「哪些猪不能放行」的判断口径只有屠宰服务一份（{@code RactopamineGuard} / {@code StampGuard}），
 * 这里只搬运结论，不在配送侧重写一遍——否则迟早出现「屠宰拦了、分割没拦」。
 */
@Slf4j
@Component
public class SlaughterClient {

    private final RestClient restClient;
    private final String slaughterUrl;

    public SlaughterClient(RestClient.Builder builder,
                           @Value("${services.slaughter-url:http://localhost:8083}") String slaughterUrl) {
        this.restClient = builder.build();
        this.slaughterUrl = slaughterUrl;
    }

    /**
     * 核对这批猪是否被瘦肉精阳性阻断。
     *
     * @return null 表示全部放行；否则返回「哪几头、为什么」的中文摘要
     * @throws BusinessException 核对不可用时抛出——<b>核不了就不放行</b>：
     *         宁可让操作员稍后重试，也不能在查不到检测状态的情况下把猪做成白条
     */
    public String blockedSummary(Collection<Long> pigIds) {
        return summaryOf("/slaughter/internal/ractopamine-blocked", "blocked", pigIds,
                "暂时无法核对瘦肉精检测状态，为避免阳性生猪流入分割环节，请稍后重试");
    }

    /**
     * 核对这批猪是否已完成胴体盖章。
     * <p>
     * 胴体批次是「这批白条已成型、可流通」的登记，而白条得以流通的凭证就是那枚章——
     * 盖章发生在宰后同步检疫之后，所以「有没有章」本身就是「过没过检疫」的外部凭证。
     *
     * @return null 表示全部已盖章；否则返回「哪几头没盖章」的中文摘要
     * @throws BusinessException 核对不可用时抛出——同理，核不了就不放行
     */
    public String unstampedSummary(Collection<Long> pigIds) {
        return summaryOf("/slaughter/internal/unstamped", "unstamped", pigIds,
                "暂时无法核对胴体盖章状态，为避免未盖章的白条流入胴体批次，请稍后重试");
    }

    /**
     * 取屠宰侧批量核对接口返回的一句话摘要。
     *
     * @param path      内部接口路径
     * @param dataKey   返回体 data 里装载明细的键名
     * @param pigIds    待核对的生猪
     * @param failHint  核对不可用时给业务人员看的提示
     */
    private String summaryOf(String path, String dataKey, Collection<Long> pigIds, String failHint) {
        if (pigIds == null || pigIds.isEmpty()) return null;
        List<String> ids = pigIds.stream().filter(Objects::nonNull).distinct().map(String::valueOf).toList();
        if (ids.isEmpty()) return null;
        String joined = String.join(",", ids);

        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(slaughterUrl + path + "?pigIds={ids}", joined)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        } catch (RestClientException e) {
            log.warn("屠宰侧批量核对失败 path={} pigIds={}", path, joined, e);
            throw new BusinessException(ErrorCode.REMOTE_CALL_ERROR, failHint);
        }
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) return null;
        // 明细为空即全部放行；summary 为空同理，两种写法都容忍
        Object detail = data.get(dataKey);
        if (detail instanceof Map<?, ?> map && map.isEmpty()) return null;
        Object summary = data.get("summary");
        if (summary == null) return null;
        String text = summary.toString();
        return StringUtils.hasText(text) ? text : null;
    }
}
