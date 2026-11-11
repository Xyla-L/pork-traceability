package com.pork.agent.support;

/**
 * 当前请求的身份上下文。
 * <p>
 * 由网关从令牌解出后透传（{@code X-User-*}），请求里自带的同名头会被网关丢弃，
 * 所以这里的值不可能由前端伪造。agent 拿它去调下游时必须原样带上，
 * 否则「养殖户问自家猪」就会变成「问全平台的猪」——那正是上一轮改掉的越权问题。
 */
public record AgentContext(String userId, String userName, String role, String orgId) {

    public boolean anonymous() {
        return userId == null || userId.isBlank();
    }

    /** 会话归属键：用用户名而不是自增ID，换库/换账号体系都不会串 */
    public String owner() {
        return anonymous() ? "anonymous" : userId;
    }
}
