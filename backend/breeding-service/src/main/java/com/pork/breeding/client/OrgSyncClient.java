package com.pork.breeding.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 机构登记同步客户端：把养殖场档案的增改删同步到机构表（auth-service 的 sys_org）。
 * <p>
 * 背景：机构有两套登记——{@code db_common.sys_org}（机构管理 / 各页面下拉的数据源）
 * 与 {@code db_breeding.farm}（业务档案）。两边不联动就会出现「机构管理缺机构」。
 * 本客户端走 auth-service 的内部接口（容器内直连，不经网关），把档案变更同步过去。
 * </p>
 * <p>
 * 容错策略：**尽力而为**。同步失败只记告警、不影响养殖场本身的增改删
 * ——机构展示是派生数据，不该因为机构服务抖动就阻断生产业务；
 * 缺失可由 {@code backend/init-sql/20-sync-org-from-business.sql} 幂等补齐。
 * </p>
 */
@Slf4j
@Component
public class OrgSyncClient {

    private static final String TYPE_FARM = "farm";

    private final RestClient restClient;
    private final String authUrl;

    public OrgSyncClient(RestClient.Builder builder,
                         @Value("${services.auth-url:http://localhost:8081}") String authUrl) {
        this.restClient = builder.build();
        this.authUrl = authUrl;
    }

    /**
     * 同步登记养殖场机构。
     *
     * @param name         现在的养殖场名称
     * @param previousName 改名前的名称（非改名场景传 null）
     */
    public void syncFarm(String name, String previousName, String manager, String phone, String address) {
        if (name == null || name.isBlank()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_FARM);
        body.put("name", name);
        body.put("previousName", previousName);
        body.put("manager", manager);
        body.put("phone", phone);
        body.put("address", address);
        body.put("remark", "由养殖场档案同步");
        try {
            restClient.post()
                    .uri(authUrl + "/auth/internal/orgs/sync")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("org_sync_ok type={} name={} previousName={}", TYPE_FARM, name, previousName);
        } catch (RestClientException e) {
            log.warn("org_sync_failed type={} name={} err={}", TYPE_FARM, name, e.toString());
        }
    }

    /** 养殖场档案删除后移除对应机构（机构有下级或已关联用户时 auth-service 会保留） */
    public void removeFarm(String name) {
        if (name == null || name.isBlank()) return;
        try {
            restClient.delete()
                    .uri(authUrl + "/auth/internal/orgs?type={type}&name={name}", TYPE_FARM, name)
                    .retrieve()
                    .toBodilessEntity();
            log.info("org_remove_ok type={} name={}", TYPE_FARM, name);
        } catch (RestClientException e) {
            log.warn("org_remove_failed type={} name={} err={}", TYPE_FARM, name, e.toString());
        }
    }
}
