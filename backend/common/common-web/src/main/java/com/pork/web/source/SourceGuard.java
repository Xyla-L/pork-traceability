package com.pork.web.source;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.source.ManualOperator;
import com.pork.core.source.OperatorHeader;
import com.pork.core.source.SourcePolicy;
import com.pork.core.source.SourcePolicyRegistry;
import com.pork.core.source.TraceStage;
import com.pork.web.config.SourcePolicyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 来源管控闸门：关键环节的人工写入入口在这里被挡下。
 * <p>
 * 解决的问题：区块链接收的是「结果」，不是「真相」。检验结论、盖章记录如果允许人工随手新增，
 * 那么链上再不可篡改，也只是把一份可以随手编造的数据永久固化下来。
 * 把关键环节的写入权限收归设备通道之后，链上数据的可信度才来自采集过程本身。
 * <p>
 * 三道闸门，按策略逐级收紧：
 * <ol>
 *   <li><b>设备独占</b>：人工新增直接拒绝。设备报的异常数据走接入层「待人工处理」队列，
 *       由实名人核对原始报文后复核放行——这是复核，不是补录。</li>
 *   <li><b>设备优先</b>：设备为主路径，设备覆盖不到时允许人工补录，写入强制实名并上链留痕。</li>
 *   <li><b>结论字段保护</b>：任何环节的结论字段一旦入账就不许原地改，
 *       只能作废后重录——否则「改一次、补一条新哈希」就能把链上留下两个版本，
 *       而外部无法判断哪一次才是合规的。</li>
 * </ol>
 */
@Slf4j
@Component
public class SourceGuard {

    private final SourcePolicyRegistry registry;
    private final SourcePolicyProperties properties;

    public SourceGuard(SourcePolicyProperties properties) {
        this.properties = properties;
        this.registry = properties.toRegistry();
        if (registry.hasOverrides()) {
            log.warn("source_policy_override_active overrides={} —— 存在来源策略应急覆盖，请确认是否符合预期",
                    properties.getOverrides());
        }
    }

    /**
     * 人工新增入口的统一校验。
     *
     * @param stage 溯源环节
     * @return 当前操作人身份，调用方应写入 source_ref，保证人工数据可追溯到人
     * @throws BusinessException 设备独占时直接拒绝
     */
    public ManualOperator requireManualAllowed(TraceStage stage) {
        return requireManualAllowed(stage, "新增");
    }

    public ManualOperator requireManualAllowed(TraceStage stage, String action) {
        SourcePolicy policy = registry.resolve(stage);
        ManualOperator operator = currentOperator();
        if (policy.manualBlocked()) {
            log.warn("source_guard_blocked stage={} action={} operator={}",
                    stage.name(), action, operator.operatorId());
            throw new BusinessException(ErrorCode.FORBIDDEN, blockMessage(stage, action));
        }
        return operator;
    }

    /**
     * 结论字段保护：结论一旦入账就不许原地改。
     * <p>
     * 允许改的只有备注、附件这类说明性字段；结论字段变了，正确做法是作废原记录后重录，
     * 这样链上留下的是「一条作废 + 一条新录」，责任清楚，而不是「同一条记录被悄悄改了几次」。
     *
     * @param changedFields 本次改动涉及的结论字段（调用方比对后传入）；为空表示只改了说明性字段
     */
    public void rejectConclusionChange(TraceStage stage, List<String> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) return;
        String fields = String.join("、", changedFields);
        log.warn("conclusion_change_rejected stage={} fields={} operator={}",
                stage.name(), fields, currentOperator().operatorId());
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "【结论保护】" + stage.label() + "的" + fields + "属于结论性字段，入账后不可修改。"
                        + "如确需更正，请对该记录执行「作废」（需填写理由），再重新录入一条新记录。");
    }

    /**
     * 作废/更正的统一校验 —— 与人工新增**同级受控**，防止「用作废绕开管控」。
     * <p>
     * 为什么作废也要管：如果新增被拦死、作废却随便调，管控就只是把入口挪了个位置。
     * 作废本质上是「公开撤回一条已经上链的陈述」，它不产生新数据，但会改写结论是否成立，
     * 因此校验标准必须和新增对齐：
     * <ul>
     *   <li><b>实名</b>：必须由登录用户执行。作废推翻的是一条已上链的结论，
     *       留不下具体责任人的话，事后没人能回答「这是谁撤的」。</li>
     *   <li><b>理由</b>：不少于 2 个字，随记录与上链 payload 一并留档。</li>
     *   <li><b>设备独占环节告警</b>：该环节禁止人工新增，但仍允许作废——
     *       录错的数据若连作废都不许，只会在库里越积越脏。这类作废属高风险动作，
     *       强制实名 + 理由 + 上链之外额外打 WARN 日志，方便审计侧主动发现。</li>
     * </ul>
     *
     * @return 实名操作人，调用方应写入 void_operator 与上链 payload
     */
    public ManualOperator requireVoidAllowed(TraceStage stage, String reason) {
        ManualOperator operator = currentOperator();
        if (!operator.authenticated()) {
            log.warn("void_rejected_anonymous stage={}", stage.name());
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "【作废管控】" + stage.label() + "的作废必须由登录用户执行——作废会推翻一条已上链的结论，"
                            + "必须留下具体责任人的痕迹。请重新登录后再操作。");
        }
        if (!StringUtils.hasText(reason) || reason.trim().length() < 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "作废必须填写理由（不少于 2 个字），作废原因将随记录一并上链");
        }
        SourcePolicy policy = registry.resolve(stage);
        if (policy.manualBlocked()) {
            log.warn("void_on_device_only stage={} operator={} reason={} —— 设备独占环节的作废属高风险动作，请审计侧关注",
                    stage.name(), operator.operatorId(), reason.trim());
        }
        log.info("void_accepted stage={} operator={} policy={}", stage.name(), operator.operatorId(), policy.name());
        return operator;
    }

    /**
     * 结论字段比对用的取值比较：数字按数值比（1 与 1.0 视为同一值），其余按 equals。
     * <p>
     * 直接用 equals 会把「前端传 1、库里存 1.0」误判成结论变更，逼用户走一遍作废重录——
     * 误伤比漏判更伤可用性，这里必须按数值语义比。
     */
    public static boolean sameValue(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (left instanceof Number a && right instanceof Number b) {
            return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString())) == 0;
        }
        return left.equals(right);
    }

    /** 该环节人工入口是否已关闭（供内部逻辑判断，不抛异常） */
    public boolean manualBlocked(TraceStage stage) {
        return registry.manualBlocked(stage);
    }

    public SourcePolicy policyOf(TraceStage stage) {
        return registry.resolve(stage);
    }

    /** 全量策略视图，供管理端展示 */
    public List<SourcePolicyRegistry.StagePolicy> policies() {
        return registry.list();
    }

    /**
     * 当前请求的操作人：网关校验 JWT 后透传 X-User-Id / X-User-Name，未认证调用方记为匿名。
     * 姓名在头里是 URL 编码的（中文名不能裸放在 HTTP 头里，会被编成 '?'），这里解回来。
     */
    public ManualOperator currentOperator() {
        String userId = header("X-User-Id");
        String userName = OperatorHeader.decode(header("X-User-Name"));
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(userName)) {
            return ManualOperator.UNKNOWN;
        }
        String resolvedId = StringUtils.hasText(userId) ? userId : userName;
        String resolvedName = StringUtils.hasText(userName) ? userName : userId;
        return new ManualOperator(resolvedId, resolvedName, true);
    }

    private String header(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        return servletAttributes.getRequest().getHeader(name);
    }

    private String blockMessage(TraceStage stage, String action) {
        return "【来源管控】" + stage.label() + "仅接受设备自动采集，人工" + action + "入口已关闭。";
    }
}
