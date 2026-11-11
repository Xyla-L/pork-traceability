package com.pork.ingest.support;

/** 待确认队列状态 */
public final class IngestStatus {
    /** 已接收，正在校验/分发 */
    public static final int PENDING = 0;
    /** 校验不通过但可人工兜底（耳标无档、检疫证不匹配等）→ 进待人工处理列表 */
    public static final int MANUAL = 1;
    /** 校验通过并已写入业务表 */
    public static final int ACCEPTED = 2;
    /** 结构性错误直接拒绝（字段缺失、仪器未出结果、越界值等），不允许入业务表 */
    public static final int REJECTED = 3;
    /** 高频上报按最小间隔降采样丢弃（非异常值），留档但不入业务表 */
    public static final int DROPPED = 4;
    /**
     * 重复上报：这条数据此前已经收过（同设备同编号，或同设备同通道同报文），本次拒绝入账。
     * <p>
     * 这是<b>回执层</b>的状态，只描述「本次请求的结论」；队列里不会出现该状态 ——
     * 重复上报不新建流水（否则重推会把待确认队列刷满），原记录的状态另放
     * {@code originalStatus} 返回。
     */
    public static final int DUPLICATED = 5;

    private IngestStatus() {
    }

    public static String label(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case PENDING -> "待处理";
            case MANUAL -> "待人工处理";
            case ACCEPTED -> "已入账";
            case REJECTED -> "已拒绝";
            case DROPPED -> "已降采样";
            case DUPLICATED -> "重复上报";
            default -> "未知";
        };
    }
}
