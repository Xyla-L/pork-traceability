package com.pork.blockchain.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 上链前脱敏（去标识化）。
 * <p>
 * 链上一旦写入就删不掉，所以「谁办的」这类个人信息必须在写链那一刻处理掉：
 * 人名保留姓氏（张**），手机号、身份证只留头尾，其余业务标识一律不动。
 * <p>
 * 不动的东西是刻意的：耳标号、批次号、检疫证号、场名是追溯键，抹掉就查不动了，
 * 它们标识的是「物」不是「人」。真正的姓名仍完整保存在平台库内（业务表 + 存证快照之外），
 * 需要还原身份时按业务键回查，不走链上。
 * <p>
 * 脱敏只作用于**新写入**的存证；历史存证里的明文无法撤回（链上不可篡改），页面如实披露。
 */
public final class PhiMasker {

    /** 人名字段名（小写匹配）：出现这些键才做姓名脱敏 */
    private static final Set<String> PERSON_KEYS = Set.of(
            "operator", "inspector", "veterinary", "vet", "receiver", "driver",
            "farmer", "keeper", "breeder", "handler", "recorder", "applicant",
            "contactperson", "responsible", "signer", "checker");

    /** 这些值不是人名（设备/系统占位、空值），原样保留，避免把「未记录」涂成「未**」 */
    private static final Set<String> PLACEHOLDERS = Set.of("", "-", "未记录", "系统", "管理员", "无");

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern ID_CARD = Pattern.compile("^\\d{17}[\\dXx]$");

    private PhiMasker() {
    }

    /**
     * 返回脱敏副本（不修改入参）：调用方还要拿原报文本地留档时，不能被我改掉。
     */
    public static Map<String, Object> mask(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return payload;
        Map<String, Object> out = new LinkedHashMap<>(payload.size());
        payload.forEach((k, v) -> out.put(k, maskValue(k, v)));
        return out;
    }

    private static Object maskValue(String key, Object value) {
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> copy = new LinkedHashMap<>();
            nested.forEach((k, v) -> copy.put(String.valueOf(k), maskValue(String.valueOf(k), v)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            // 列表里的元素没有列名，按原样保留；只有明确的标量字段才脱敏
            list.forEach(item -> copy.add(item instanceof Map<?, ?> ? maskValue(key, item) : item));
            return copy;
        }
        if (!(value instanceof String text) || text.isBlank() || PLACEHOLDERS.contains(text)) return value;
        if (PHONE.matcher(text).matches()) return text.substring(0, 3) + "****" + text.substring(7);
        if (ID_CARD.matcher(text).matches()) return text.substring(0, 6) + "********" + text.substring(14);
        if (key != null && PERSON_KEYS.contains(key.toLowerCase())) return maskName(text);
        return value;
    }

    /** 姓名：保留首字（姓），其余打码；两字姓名 → 张*，三字 → 张** */
    private static String maskName(String name) {
        if (name.length() <= 1) return "*";
        int stars = Math.min(name.length() - 1, 3);
        return name.charAt(0) + "*".repeat(stars);
    }
}
