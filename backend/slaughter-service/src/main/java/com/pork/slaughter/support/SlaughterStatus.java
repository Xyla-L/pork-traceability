package com.pork.slaughter.support;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;

import java.util.Map;

public final class SlaughterStatus {
    private static final Map<String, Integer> ENTRY = Map.of("待查验", 0, "合格", 1, "不合格", 2);
    private static final Map<String, Integer> INSPECTION = Map.of("待检验", 0, "合格", 1, "不合格", 2);
    private static final Map<String, Integer> TEST = Map.of("待检测", 0, "检测中", 1, "已完成", 2);
    private static final Map<String, Integer> STAMP = Map.of("待盖章", 0, "已盖章", 1, "已作废", 2);

    private SlaughterStatus() {
    }

    public static Integer entry(String value) { return parse(value, ENTRY, "入场查验状态"); }
    public static Integer inspection(String value) { return parse(value, INSPECTION, "检验状态"); }
    public static Integer test(String value) { return parse(value, TEST, "检测状态"); }
    public static Integer stamp(String value) { return parse(value, STAMP, "盖章状态"); }

    public static Integer inspectType(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value) {
            case "1", "宰前检验" -> 1;
            case "2", "宰后检验" -> 2;
            case "3", "同步检验" -> 3;
            default -> throw invalid("检验类型", value);
        };
    }

    public static Integer inspectionResult(String value) {
        if (value == null || value.isBlank() || "待检验".equals(value)) return null;
        return switch (value) {
            case "1", "合格" -> 1;
            case "0", "不合格" -> 0;
            default -> throw invalid("检验结果", value);
        };
    }

    public static Integer testResult(String value) {
        if (value == null || value.isBlank() || "待检测".equals(value)) return null;
        return switch (value) {
            case "1", "阴性", "合格" -> 1;
            case "0", "阳性", "不合格" -> 0;
            default -> throw invalid("检测结果", value);
        };
    }

    public static String entryLabel(Integer value) { return label(value, "待查验", "合格", "不合格"); }
    public static String inspectionLabel(Integer value) { return label(value, "待检验", "合格", "不合格"); }
    public static String testLabel(Integer value) { return label(value, "待检测", "检测中", "已完成"); }
    public static String stampLabel(Integer value) { return label(value, "待盖章", "已盖章", "已作废"); }
    public static String inspectTypeLabel(Integer value) {
        return switch (value == null ? 0 : value) { case 1 -> "宰前检验"; case 2 -> "宰后检验"; case 3 -> "同步检验"; default -> "未知"; };
    }
    public static String inspectionResultLabel(Integer value) { return value == null ? "待检验" : value == 1 ? "合格" : "不合格"; }
    public static String testResultLabel(Integer value) { return value == null ? "待检测" : value == 1 ? "阴性" : "阳性"; }

    private static Integer parse(String value, Map<String, Integer> values, String name) {
        if (value == null || value.isBlank()) return null;
        Integer result = values.get(value);
        if (result != null) return result;
        try {
            int numeric = Integer.parseInt(value);
            if (values.containsValue(numeric)) return numeric;
        } catch (NumberFormatException ignored) {
        }
        throw invalid(name, value);
    }

    private static String label(Integer value, String zero, String one, String two) {
        return switch (value == null ? 0 : value) { case 0 -> zero; case 1 -> one; case 2 -> two; default -> "未知"; };
    }

    private static BusinessException invalid(String name, String value) {
        return new BusinessException(ErrorCode.PARAM_ERROR, name + "不合法: " + value);
    }
}
