package com.pork.ingest.support;

/**
 * 结构性错误：数据本身不成立（缺字段、越界值、仪器未出结果），
 * 允许进入待确认队列留档，但不允许写业务表，人工也无法"确认"通过。
 */
public class RejectException extends RuntimeException {
    public RejectException(String message) {
        super(message);
    }
}
