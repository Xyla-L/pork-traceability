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

    /**
     * 出栏审批状态（入场查验的硬前提）。
     * <p>
     * {@code reachable=false} 表示养殖服务不可用——调用方必须把它当成「暂时核不了」转人工，
     * 绝不能当成「审批通过」放行；其余情况由 {@code approved} 给出明确结论。
     */
    public ExitApplyCheck exitApplyOfPig(Long pigId) {
        try {
            Map<String, Object> envelope = restClient.get()
                    .uri(breedingUrl + "/breeding/internal/applies/by-pig?pigId={pigId}", pigId)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !(envelope.get("data") instanceof Map<?, ?> data)) {
                return new ExitApplyCheck(false, false, false, null, "养殖服务返回异常，无法核对该猪的出栏审批状态");
            }
            boolean exists = Boolean.TRUE.equals(data.get("exists"));
            boolean approved = Boolean.TRUE.equals(data.get("approved"));
            Integer approvalStatus = data.get("approvalStatus") == null
                    ? null : Integer.valueOf(data.get("approvalStatus").toString());
            String reason = approved ? null : value(data.get("reason"));
            return new ExitApplyCheck(true, approved, exists, approvalStatus, reason);
        } catch (RestClientException | NumberFormatException e) {
            log.warn("出栏审批状态核对失败 pigId={}", pigId, e);
            return new ExitApplyCheck(false, false, false, null, "养殖服务不可用，暂时无法核对该猪的出栏审批状态");
        }
    }

    /**
     * 生猪当前状态（入场查验的第二道硬前提）。
     * <p>
     * 出栏审批管「该不该卖」，状态管「这头猪还在不在可流通的环节里」——
     * 一头已经屠宰过的猪不可能再进厂一次，放进来只会把两条互不相干的追溯链挂到同一个耳标上。
     * <p>
     * {@code reachable=false} 表示养殖服务不可用或该档案已不存在——调用方必须转人工，
     * 绝不能把「核不了」当成「状态正常」。
     */
    public PigState pigState(Long pigId) {
        if (pigId == null) {
            return new PigState(false, null, null, "缺少生猪档案编号，无法核对该猪的状态");
        }
        try {
            Map<String, Object> envelope = restClient.get()
                    .uri(breedingUrl + "/breeding/internal/pigs/{id}/state", pigId)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !(envelope.get("data") instanceof Map<?, ?> data)) {
                return new PigState(false, null, null, "养殖服务返回异常，无法核对该猪的状态");
            }
            Integer status = data.get("status") == null
                    ? null : Integer.valueOf(data.get("status").toString());
            return new PigState(true, status, value(data.get("statusLabel")), null);
        } catch (RestClientException | NumberFormatException e) {
            log.warn("生猪状态核对失败 pigId={}", pigId, e);
            return new PigState(false, null, null, "养殖服务不可用，暂时无法核对该猪的状态");
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

    /**
     * 出栏审批核对结果。
     *
     * @param reachable      养殖服务是否可用（false 时其余字段均不可信）
     * @param approved       是否已审批通过（入场查验放行的唯一依据）
     * @param exists         是否存在出栏申报记录
     * @param approvalStatus 0待审 1通过 2驳回；无申报时为 null
     * @param reason         未通过的原因（已通过时为 null），可直接展示给业务人员
     */
    public record ExitApplyCheck(boolean reachable, boolean approved, boolean exists,
                                 Integer approvalStatus, String reason) {
    }

    /**
     * 生猪状态核对结果。
     *
     * @param reachable 养殖服务是否可用（false 时其余字段均不可信）
     * @param status    1在养 2已出栏 3已屠宰 4异常
     * @param label     状态中文（如「已屠宰」），由养殖服务给出，下游不各自翻译
     * @param reason    核对不了时的原因（可核对时为 null）
     */
    public record PigState(boolean reachable, Integer status, String label, String reason) {

        /** 「在养」：还在养殖场 */
        private static final int IN_FARM = 1;
        /** 「已出栏」：已离场待宰，正是入场查验要接的状态 */
        private static final int EXITED = 2;

        /**
         * 是否可以入场查验：只有「在养」「已出栏」放行。
         * 「已屠宰」「异常」都是终态——一头猪只能进厂一次。
         */
        public boolean enterable() {
            return status != null && (status == IN_FARM || status == EXITED);
        }
    }
}
