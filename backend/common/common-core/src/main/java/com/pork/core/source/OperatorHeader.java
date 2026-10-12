package com.pork.core.source;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 操作人姓名在 HTTP 头里的编解码。
 * <p>
 * HTTP 头只能安全携带 ASCII：中文姓名（如「系统管理员」）在网关写出时会被按 ISO-8859-1
 * 编码，编码不了的字符直接变成 '?'，落到业务表里就是「?????」——「实名」等于没落，链上
 * 「谁批的」读出来是一串问号。所以网关写头前先做 URL 编码（结果是纯 ASCII），
 * 各服务读头后解回来。
 * <p>
 * 兼容性：解码失败（历史或手工构造的头里带裸 '%'）时原样返回，不因为一个名字把请求打挂。
 */
public final class OperatorHeader {

    private OperatorHeader() {
    }

    /** 写头前调用：把可能含中文的姓名转成纯 ASCII */
    public static String encode(String name) {
        if (name == null) return null;
        return URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    /** 读头后调用：把 URL 编码的姓名还原。不是编码格式时原样返回 */
    public static String decode(String value) {
        if (value == null) return null;
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
