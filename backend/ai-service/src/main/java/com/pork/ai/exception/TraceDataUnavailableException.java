package com.pork.ai.exception;

/**
 * 依赖的 trace-service 取数失败（网络不通 / 超时 / 5xx / 业务码非 200）。
 *
 * 为什么要有这个异常：
 *   原来的实现用 onErrorResume 把任何失败都变成空 Map，
 *   结果"下游挂了"和"这块肉真的没记录"在 AI 看来一模一样，
 *   于是模型会输出"溯源信息完全缺失，建议不要购买"——
 *   这是业务事故级的误导，商家会来投诉。
 *
 *   现在把失败显式抛出来，由上层转成 SSE error 事件，
 *   前端就能提示"数据暂时不可用"，而不是给出错误的食品安全结论。
 *
 * 注意 message 只用于日志，不直接透给前端（避免泄露内网地址）。
 */
public class TraceDataUnavailableException extends RuntimeException {

    /** 哪个接口出的问题，排障时定位用 */
    private final String source;

    public TraceDataUnavailableException(String source, String message) {
        super(message);
        this.source = source;
    }

    public TraceDataUnavailableException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    public String getSource() {
        return source;
    }
}
