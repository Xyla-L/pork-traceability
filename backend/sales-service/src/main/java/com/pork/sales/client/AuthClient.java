package com.pork.sales.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * 通知收件人解析（auth 内部接口）。
 * <p>
 * 为什么收件人要在 auth 这边查：销售记录里只有 store_id（机构），user_id 只有 auth 知道。
 * 让每个业务服务各自读一张 sys_user，迟早会出现「一处按 org、一处按 org_name」的口径分裂。
 * <p>
 * 查不到人不是异常：账号没建、还没启用都会返回空列表 —— 这时召回照常发起，只是没人收到通知，
 * 由调用方在日志里留痕（不能因为「没人可通知」就把召回整单回滚掉）。
 */
@Slf4j
@Component
public class AuthClient {

    private final RestClient client;
    private final String authUrl;

    public AuthClient(RestClient.Builder builder,
                      @Value("${services.auth-url:http://localhost:8081}") String authUrl) {
        this.client = builder.build();
        this.authUrl = authUrl;
    }

    /** 按角色 / 机构查活跃用户（orgIds 为空表示不限机构） */
    public List<Map<String, Object>> recipients(String role, List<Long> orgIds) {
        try {
            String ids = orgIds == null || orgIds.isEmpty() ? ""
                    : String.join(",", orgIds.stream().map(String::valueOf).toList());
            Map<String, Object> envelope = client.get()
                    .uri(authUrl + "/auth/internal/orgs/users?role={role}&orgIds={ids}",
                            role == null ? "" : role, ids)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (envelope == null || !(envelope.get("data") instanceof List<?> data)) {
                log.warn("auth_recipients_empty role={} orgIds={} resp={}", role, ids, envelope);
                return List.of();
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) data;
            return rows;
        } catch (RestClientException e) {
            log.warn("auth_recipients_failed role={} orgIds={} err={}", role, orgIds, e.toString());
            return List.of();
        }
    }
}
