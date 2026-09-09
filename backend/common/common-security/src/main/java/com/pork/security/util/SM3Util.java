package com.pork.security.util;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.digest.HMac;
import java.nio.charset.StandardCharsets;

/**
 * SM3 国密哈希工具类
 * 用于用户密码加密存储
 */
public class SM3Util {

    /**
     * SM3 哈希加密
     *
     * @param data 原始数据
     * @return 哈希值(小写十六进制)
     */
    public static String hash(String data) {
        return SmUtil.sm3().digestHex(data);
    }

    /**
     * SM3 哈希加密（加盐）
     * 使用 HMAC-SM3 方式实现加盐
     *
     * @param data 原始数据
     * @param salt 盐值
     * @return 哈希值(小写十六进制)
     */
    public static String hashWithSalt(String data, String salt) {
        HMac hMac = SmUtil.hmacSm3(salt.getBytes(StandardCharsets.UTF_8));
        return hMac.digestHex(data);
    }

    /**
     * 验证密码
     *
     * @param rawPassword  原始密码
     * @param hashedPassword 数据库中的哈希密码
     * @return 是否匹配
     */
    public static boolean verify(String rawPassword, String hashedPassword) {
        return hash(rawPassword).equalsIgnoreCase(hashedPassword);
    }

    /**
     * 验证密码（加盐）
     *
     * @param rawPassword  原始密码
     * @param hashedPassword 数据库中的哈希密码
     * @param salt 盐值
     * @return 是否匹配
     */
    public static boolean verifyWithSalt(String rawPassword, String hashedPassword, String salt) {
        return hashWithSalt(rawPassword, salt).equalsIgnoreCase(hashedPassword);
    }
}