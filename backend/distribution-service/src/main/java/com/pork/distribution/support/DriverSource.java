package com.pork.distribution.support;

/**
 * 司机来源「唯一口径」：这个人名是怎么进到运单里的，以及它算不算**本人确认**。
 * <p>
 * 为什么把两者绑成一个口径，而不是各写一份判断：
 * 「这个字段是怎么来的」与「它是不是本人确认」是同一件事的两面 ——
 * 刷卡和刷脸必然是本人在终端配合，车牌反查必然只是系统推定，人工录入必然是调度代填。
 * 如果允许设备再自报一个 driverConfirmed 布尔值，就会出现「报了 FACE 却说未确认」这种
 * 自相矛盾的报文，两个来源一打架，落库的 driver_confirmed 就不再可信。
 * 所以确认强度由来源**唯一推导**，不接受外部声明。
 * <p>
 * <b>红线不变</b>：{@link #PLATE_INFERRED} 只是替人工省一道查询，**不作为实名凭据** ——
 * 它命中的「常用主驾」仍旧转人工，不允许用「车牌反查到的司机」直接放行，
 * 因为项目原则是「设备 ≠ 人」，车牌号不能顶替责任人。
 */
public final class DriverSource {

    private DriverSource() {
    }

    /**
     * 刷卡读卡号：机器自动读，卡在人手上 → 本人确认。
     * <p>
     * <b>产生它的地方只有车载终端的两次刷卡</b>：上车那一刻的发车确认（DEPART 通道）与
     * 到站那一次（ARRIVE 通道）。这两处是<b>同一台设备、同一个读卡头</b>
     * （见 ingest 侧 {@code IngestChannel.deviceGroup()}），只是两个动作。
     * 装车口没有读卡器，报文里带卡号会被拒绝（不是「忽略」），所以 {@code CARD}
     * 只可能来自这台车载终端。
     */
    public static final String CARD = "CARD";
    /** 人脸 / 实名终端识别：司机本人配合 → 本人确认 */
    public static final String FACE = "FACE";
    /** 车牌反查备案的常用司机 → 系统推定，不是本人确认 */
    public static final String PLATE_INFERRED = "PLATE_INFERRED";
    /** 人工录入（调度员填写，或发货计划里早就填好的） → 非本人确认 */
    public static final String MANUAL = "MANUAL";

    /**
     * 该来源是否构成「本人确认」。
     * 只有 CARD / FACE 算 —— 这两种都需要司机本人在现场配合；
     * 车牌反查与人工录入都不算。
     */
    public static boolean isConfirmed(String source) {
        return CARD.equals(source) || FACE.equals(source);
    }

    /** 落库用的强度值：1=本人确认 0=推定或人工录入 */
    public static int confirmedFlag(String source) {
        return isConfirmed(source) ? 1 : 0;
    }

    /**
     * 归一化：未知 / 空值一律落到 {@link #MANUAL}。
     * 宁可把来源记保守（非本人确认），也不能让一个拼错的字符串被当成「已确认」。
     */
    public static String normalize(String source) {
        if (CARD.equals(source) || FACE.equals(source) || PLATE_INFERRED.equals(source)) {
            return source;
        }
        return MANUAL;
    }

    /** 界面中文名：把来源翻成业务话，不暴露枚举名 */
    public static String labelOf(String source) {
        if (source == null) {
            return "来源未记录";
        }
        return switch (source) {
            case CARD -> "刷卡确认";
            case FACE -> "人脸识别";
            case PLATE_INFERRED -> "车牌反查（推定）";
            case MANUAL -> "人工录入";
            default -> "来源未记录";
        };
    }
}
