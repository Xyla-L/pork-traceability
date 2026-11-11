package com.pork.breeding.support;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * 耳标区间：把「起号 ~ 止号」解析成可展开的成员序列。
 * <p>
 * 现实依据：自繁场的耳标是连续领用、连续佩戴的，所以一批猪的耳标天然是一个区间；
 * 外购拼车、跨号段领标、中途补标则会散开 —— 那种情况走明细清单，不走区间。
 * <p>
 * 规则：同前缀 + 纯数字尾号（如 ET-DEMO-0001 ~ ET-DEMO-0003）。
 * 例外：起号等于止号（单头批次，如种猪个体批）不要求尾号是数字 ——
 * 只有一头时不存在「展开」，任何格式的耳标号都可以直接圈进来。
 * <p>
 * 抽成公共类的原因：人工登记（PigBatchServiceImpl）与养殖系统同步
 * （FarmBatchIngestService）必须用<b>同一套</b>区间口径，否则同一份 ERP 数据走人工补录
 * 与走接口同步会得出不同成员数量 —— 这种不一致最难查。
 */
public final class PigTagRange {

    /** 单批次耳标区间上限：现实中一批商品猪几十到几百头，超上限基本是起止号填反或录错 */
    public static final int MAX_BATCH_SIZE = 2000;

    private PigTagRange() {
    }

    public record Range(String prefix, int suffixLen, long startNum, long endNum, String start, String end) {

        public boolean single() {
            return start.equals(end);
        }

        public long size() {
            return single() ? 1 : endNum - startNum + 1;
        }

        public String tagOf(long num) {
            return prefix + String.format("%0" + suffixLen + "d", num);
        }

        public List<String> members() {
            if (single()) return List.of(start);
            List<String> out = new ArrayList<>();
            for (long n = startNum; n <= endNum; n++) out.add(tagOf(n));
            return out;
        }
    }

    /** 解析并做上限校验；校验不通过抛 PARAM_ERROR（调用方据此回执「数据不成立」） */
    public static Range of(String start, String end) {
        Range range = parse(start, end);
        if (!range.single() && range.startNum > range.endNum) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "耳标止号不能小于起号");
        }
        if (range.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "单批次最多 " + MAX_BATCH_SIZE + " 头，当前区间 " + range.size() + " 头——请核对起止号是否录错");
        }
        return range;
    }

    private static Range parse(String start, String end) {
        // 单头批次（种猪登记、回归测试单头用例）：只有一头，不做数字尾号要求
        if (start.equals(end)) {
            return new Range(start, 0, 0, 0, start, end);
        }
        int sCut = cutDigitSuffix(start);
        int eCut = cutDigitSuffix(end);
        if (sCut <= 0 || eCut <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "耳标号必须以数字结尾（如 ET-DEMO-0001），才能按区间圈定批次成员");
        }
        String sPrefix = start.substring(0, sCut);
        String ePrefix = end.substring(0, eCut);
        if (!sPrefix.equals(ePrefix)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "耳标起止号前缀不一致，不在同一个编号序列里");
        }
        int sLen = start.length() - sCut;
        int eLen = end.length() - eCut;
        if (sLen != eLen) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "耳标起止号尾号位数不一致，请核对是否录错");
        }
        long sNum;
        long eNum;
        try {
            sNum = Long.parseLong(start.substring(sCut));
            eNum = Long.parseLong(end.substring(eCut));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "耳标尾号超出支持范围，请核对编号格式");
        }
        return new Range(sPrefix, sLen, sNum, eNum, start, end);
    }

    /** 找到尾部连续数字的起点下标；无数字尾号返回 0 */
    private static int cutDigitSuffix(String tag) {
        int i = tag.length();
        while (i > 0 && Character.isDigit(tag.charAt(i - 1))) i--;
        return i;
    }
}
