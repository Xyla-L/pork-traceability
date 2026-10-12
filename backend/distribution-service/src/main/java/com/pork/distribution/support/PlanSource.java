package com.pork.distribution.support;

/**
 * 发货计划来源「唯一口径」：这张计划是怎么来的。
 * <p>
 * 订单接入不是「本系统长出商流」，而是**多接一条事实源** —— 现实里订单在 ERP/OMS，
 * 本系统只管溯源。所以订单进来只是计划的一种来源，运单的上游永远是计划，不是订单。
 * <p>
 * 为什么要落成字段而不是靠推断：光看计划本身分不出「这车货是门店要的」还是「总部派的」。
 * 将来要统计门店要货量、要接结算时，这个字段就是分界；漏了它以后没有追溯的余地。
 */
public final class PlanSource {

    private PlanSource() {
    }

    /** 内部调度手工制单。补货 / 调拨 / 铺货没有订单，这条永远存在，也是默认值 */
    public static final String MANUAL = "MANUAL";
    /** 由外部系统的选购/订单转入 */
    public static final String ORDER = "ORDER";

    /**
     * 归一化：未知 / 空值一律落到 {@link #MANUAL}。
     * 宁可把来源记保守（当成内部调度），也不让一个拼错的字符串被当成「有订单支撑」。
     */
    public static String normalize(String source) {
        return ORDER.equals(source) ? ORDER : MANUAL;
    }

    /** 界面中文名：把来源翻成业务话，不暴露枚举名 */
    public static String labelOf(String source) {
        if (source == null) {
            return "来源未记录";
        }
        return switch (source) {
            case ORDER -> "订单转入";
            case MANUAL -> "内部调度";
            default -> "来源未记录";
        };
    }
}
