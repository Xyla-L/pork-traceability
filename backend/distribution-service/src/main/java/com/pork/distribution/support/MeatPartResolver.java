package com.pork.distribution.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.distribution.entity.MeatPart;
import com.pork.distribution.mapper.MeatPartMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 部位定型「唯一口径」：把设备/订单给的部位信息定到 {@code meat_part} 字典上。
 * <p>
 * 两处调用方：分割作业上报（设备只报名称时反查）与订单转入（商家选购的部位）。
 * <b>必须共用一份实现</b> —— 各写一份迟早出现「同一个别名在一处认得、在另一处认不得」，
 * 而部位是门店召回圈范围的关键字段（出问题按部位圈货），两套口径不一致等于圈错范围。
 * <p>
 * 反查不到一律返回 {@code null}，由调用方转人工 —— <b>宁缺勿猜</b>。
 */
@Component
public class MeatPartResolver {

    private final MeatPartMapper meatPartMapper;

    public MeatPartResolver(MeatPartMapper meatPartMapper) {
        this.meatPartMapper = meatPartMapper;
    }

    /**
     * 编码优先；只报名称时按字典的 {@code part_name} / {@code aliases}（逗号分隔）反查。
     *
     * @return 命中的字典项；反查不到返回 {@code null}
     */
    public MeatPart resolve(String partCode, String partName) {
        if (StringUtils.hasText(partCode)) {
            MeatPart byCode = meatPartMapper.selectOne(Wrappers.<MeatPart>lambdaQuery()
                    .eq(MeatPart::getPartCode, partCode.trim())
                    .eq(MeatPart::getEnabled, 1).last("LIMIT 1"));
            if (byCode != null) {
                return byCode;
            }
        }
        if (!StringUtils.hasText(partName)) {
            return null;
        }
        String name = partName.trim();
        return meatPartMapper.selectList(Wrappers.<MeatPart>lambdaQuery()
                        .eq(MeatPart::getEnabled, 1).orderByAsc(MeatPart::getSortNo)).stream()
                .filter(p -> name.equals(p.getPartName())
                        || (p.getAliases() != null && Arrays.stream(p.getAliases().split(","))
                                .map(String::trim).anyMatch(name::equals)))
                .findFirst().orElse(null);
    }
}
