package com.pork.core.source;

/**
 * 人工录入的操作人身份（来自网关透传的 X-User-Id / X-User-Name）。
 * <p>
 * 关键环节的人工动作必须能落到具体人头上，否则「来源可标识」只是句口号。
 * <p>
 * 注意两者的区别：{@code operatorId} 是网关透传的 X-User-Id，值为用户ID（对应 sys_user.id），
 * 这是系统里既有的身份约定，下游有按 Long 解析的地方，不能改；
 * {@code operatorName} 来自 X-User-Name，取值优先 {@code sys_user.real_name}（实名），
 * 用户未填实名时该声明缺省、网关退回用户ID —— 需展示时退回 ID，需实名时由
 * {@link #hasRealName()} 判定并拦下关键结论动作。
 */
public record ManualOperator(String operatorId, String operatorName, boolean authenticated) {

    public static final ManualOperator UNKNOWN = new ManualOperator("anonymous", "未认证调用方", false);

    /** 落到业务表 source_ref 的展示值：优先姓名，其次账号 */
    public String display() {
        if (operatorName != null && !operatorName.isBlank()) return operatorName;
        return operatorId == null || operatorId.isBlank() ? "未知操作人" : operatorId;
    }

    /**
     * 是否拿到了<b>真人姓名</b>（而不是只拿到用户ID/账号）。关键结论动作（审批、出证）据此把关。
     * <p>
     * 判据说明：令牌签发的姓名优先取 {@code sys_user.real_name}，用户没填实名时该声明缺省，
     * 网关会退回用户ID，因此这里把「纯数字」视为「只有ID、没有实名」。这是约定而非巧合——
     * sys_user.id 是自增数字，而姓名不可能是一串纯数字。
     */
    public boolean hasRealName() {
        if (!authenticated || operatorName == null || operatorName.isBlank()) return false;
        return !operatorName.chars().allMatch(Character::isDigit);
    }
}
