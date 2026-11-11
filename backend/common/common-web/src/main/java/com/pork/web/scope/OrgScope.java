package com.pork.web.scope;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * 机构数据边界：把「这个人能看哪家机构的数据」收敛到一处。
 * <p>
 * <b>为什么必须有这一层：</b>在此之前，门店ID 是查询条件里一个普通的可选参数，
 * 谁都能传谁都能改——零售商把 {@code storeId} 换个数字就能翻别家的销售记录。
 * 接口照样返回 200，因为服务端从来没问过「你是谁家的」。那是「页面不显示」，不是隔离。
 * <p>
 * <b>机构从哪来：</b>令牌里的 {@code orgId} 声明，经网关透传成 {@code X-User-Org}，
 * <b>请求里自带的 {@code X-User-Org} 一律被网关丢弃</b>。所以这里的值不可能由调用方伪造。
 * <p>
 * <b>零售商的边界是「门店」：</b>{@code sys_user.org_id} 指向 {@code sys_org} 里
 * {@code type='retail'} 的那一行，其 id 就是业务表里的 {@code store_id}
 * （{@code retail_sale.store_id} / {@code store_receipt.store_id} / {@code transport_stop.store_id}）。
 * 其余角色（管理员、监管、养殖、屠宰、配送）在本层不受限——他们的数据边界不是门店，
 * 硬套会让他们什么都看不到。
 */
@Slf4j
@Component
public class OrgScope {

    public static final String HEADER_ROLE = "X-User-Role";
    public static final String HEADER_ORG = "X-User-Org";
    public static final String ROLE_RETAILER = "RETAILER";
    public static final String ROLE_FARMER = "FARMER";

    /** 当前登录角色（缺省为空串，便于 equals 判断，不用到处判 null） */
    public String role() {
        String value = header(HEADER_ROLE);
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** 当前登录人所属机构ID（{@code sys_user.org_id}）；没有该声明时返回 null */
    public Long orgId() {
        String value = header(HEADER_ORG);
        if (!StringUtils.hasText(value)) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("org_scope_bad_header value={}", value);
            return null;
        }
    }

    public boolean retailer() {
        return ROLE_RETAILER.equals(role());
    }

    /**
     * 养殖角色：数据边界是「自己所属机构对应的养殖场」。
     * 机构到场的换算在 breeding-service 的 FarmScope 里做（本层不碰业务库）。
     */
    public boolean farmer() {
        return ROLE_FARMER.equals(role());
    }

    /**
     * 当前请求的数据边界：零售商＝自己门店的ID；其他角色＝null（不限门店）。
     * <p>
     * 零售商拿不到门店时**不放行到全平台**：那是越权。网关已把这种令牌挡在门外
     * （缺 orgId 的零售商直接 401），这里再挡一次是防「绕过网关直连服务端口」。
     */
    public Long storeId() {
        if (!retailer()) return null;
        Long org = orgId();
        if (org == null) {
            log.warn("org_scope_missing_store role=RETAILER —— 令牌缺少机构声明，拒绝按全平台口径返回数据");
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "登录信息缺少所属门店，请重新登录后再试");
        }
        return org;
    }

    /**
     * 合并「调用方传的门店」与「他实际能看的门店」。
     * <p>
     * 零售商：忽略传入值，一律返回自己门店——传别人的门店号不会变成别人的视角。
     * 其他角色：原样返回传入值（null 表示全部门店）。
     */
    public Long storeOr(Long requested) {
        return retailer() ? storeId() : requested;
    }

    /**
     * 校验一条记录确实属于当前门店。写操作（激活、售出、生成二维码、处理预警）必须过这一关：
     * 只过滤列表挡不住「拿着别家商品的ID 直接调接口」。
     *
     * @param actual  记录上的门店ID
     * @param subject 记录的业务名称，用于报错信息（如「该商品」）
     */
    public void requireStore(Long actual, String subject) {
        Long scoped = storeId();
        if (scoped == null) return;
        if (!Objects.equals(scoped, actual)) {
            log.warn("org_scope_denied subject={} scopedStore={} actualStore={}", subject, scoped, actual);
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "【数据隔离】" + subject + "不属于当前登录门店，无权查看或操作");
        }
    }

    /** 只做判断不抛异常：用于「这行数据要不要出现在结果里」 */
    public boolean owns(Long actual) {
        if (!retailer()) return true;
        Long org = orgId();
        return org != null && Objects.equals(org, actual);
    }

    private String header(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        return servletAttributes.getRequest().getHeader(name);
    }
}
