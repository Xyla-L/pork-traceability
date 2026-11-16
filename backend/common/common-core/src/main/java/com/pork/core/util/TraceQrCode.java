package com.pork.core.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品溯源码的唯一定义处。
 * <p>
 * 码由 sales-service 生成：{@code "QR-PORK-" + UUID 去掉横线后大写}（32 位十六进制）。
 * 「扫出来的是不是本系统的码」这件事，前后端必须用同一把尺子量 —— 不许在小程序、
 * 管理端、各服务里各写一份正则，否则码格式一变就会有人漏改。
 * <p>
 * 判据只用<b>前缀 + 字符集</b>，不锁死 32 位十六进制：历史演示数据里存在
 * {@code QR-PORK-DEMO-0001} 这类手工造的码，锁死位数会把它们误判成「不是本系统的码」。
 * 而前缀本身就是命名空间 —— 付款码（18 位纯数字条码、{@code https://qr.alipay.com/…}
 * 这类链接）不可能以 {@code QR-PORK-} 开头，靠前缀足够把异类码挡在查库之前。
 */
public final class TraceQrCode {

    public static final String PREFIX = "QR-PORK-";

    /** 宽松判据：前缀 + 6~64 位大写字母/数字/横线（够覆盖 32 位 hex 与 DEMO 码） */
    private static final Pattern CODE = Pattern.compile("QR-PORK-[A-Z0-9][A-Z0-9-]{5,63}");

    /**
     * 扫到别的码时给用户的下一步。
     * <p>
     * 不能说「查询失败」或「产品不存在」—— 消费者会把后者理解成「这肉是假的」。
     * 扫错码是操作问题，不是商品问题，两件事必须分开说。
     */
    public static final String NOT_TRACE_CODE =
            "这不是本系统的猪肉溯源码：请对准包装袋上的二维码重新扫描";

    private TraceQrCode() {
    }

    /**
     * 从扫码原文里取出溯源码，取不到返回 {@code null}。
     * <p>
     * 容忍码被包在链接或参数里（{@code https://x.cn/p?code=QR-PORK-…}），
     * 但不会「猜」——整段里找不到形如溯源码的片段就判定为不是本系统的码。
     */
    public static String extract(String raw) {
        if (raw == null) return null;
        String text = raw.trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) return null;
        Matcher matcher = CODE.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    public static boolean isTraceCode(String raw) {
        return extract(raw) != null;
    }

    /** 归一化：合法则返回标准形态（大写、已去空白），否则返回去空白的原文 */
    public static String normalize(String raw) {
        String code = extract(raw);
        if (code != null) return code;
        return raw == null ? "" : raw.trim();
    }
}
