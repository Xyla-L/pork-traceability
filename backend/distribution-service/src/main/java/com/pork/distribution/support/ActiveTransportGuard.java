package com.pork.distribution.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.distribution.entity.ColdChainTransport;
import com.pork.distribution.mapper.ColdChainTransportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 一辆车同一时间只跑一趟：车牌名下还有未完成的运单时，不再接受新的运单。
 * <p>
 * 为什么要有这一道：运单是「这趟车装了哪些货」的载体。同一车牌裂成两张未完成运单之后，
 * 发车（DEPART / ARRIVE）按车牌倒查就会撞上「多张等待发车 → 不敢猜是哪一趟」而转人工，
 * 一条现场作业变成两条待办；更糟的是两张单各记各的站点与温度，事后说不清车到底在哪一趟上。
 * <p>
 * 「未完成」= 待发车(1) / 运输中(2) / 待签收(3)。<b>已签收(4) 才算完成</b> ——
 * 车回来了才谈得上装下一趟，所以签收不只是「顺便记一下」，它是这辆车能重新排班的开关。
 * <b>拒收同样算完成</b>：拒收站点在状态推导里视同已终结，全部站点终结运单同样落 4 ——
 * 不存在「门店拒收了、车却被锁死排不了下一趟」的死结。
 * <p>
 * 与「并入」不冲突：同一趟车继续装货仍由 {@code appendToTransport} 并进那张**待发车**的运单，
 * 那是「一张单多装几批」，不是「多一张单」。本闸门只在**要新建一张运单**时说话。
 */
@Component
@RequiredArgsConstructor
public class ActiveTransportGuard {

    /** 未完成状态：待发车 / 运输中 / 待签收 */
    public static final List<Integer> ACTIVE_STATUS = List.of(1, 2, 3);

    private final ColdChainTransportMapper transportMapper;

    /** 该车牌名下未完成的运单（按运单 id 升序，报错时取第一张，说清是哪一趟） */
    public List<ColdChainTransport> activeOf(String vehicleNo, Long excludeId) {
        if (!StringUtils.hasText(vehicleNo)) return List.of();
        return transportMapper.selectList(Wrappers.<ColdChainTransport>lambdaQuery()
                .eq(ColdChainTransport::getVehicleNo, vehicleNo.trim())
                .in(ColdChainTransport::getStatus, ACTIVE_STATUS)
                .ne(excludeId != null, ColdChainTransport::getId, excludeId)
                .orderByAsc(ColdChainTransport::getId));
    }

    /**
     * 建单（或改挂车牌）前调用：该车名下还有未完成运单就拒绝。
     * <p>
     * 不替人决定「把旧单作废」—— 旧单可能有站点、温度、签收的实绩，
     * 谁该退、谁该留只有调度说得清，系统只能把话说清楚然后停下。
     *
     * @param excludeId 校验对象自身的运单 id（编辑运单时排除自己）
     * @param action    被拦下的动作名（「人工建单」「生成运单」「改挂车牌」），拼进提示让操作者知道卡在哪一步
     */
    public void assertFree(String vehicleNo, Long excludeId, String action) {
        List<ColdChainTransport> active = activeOf(vehicleNo, excludeId);
        if (active.isEmpty()) return;
        ColdChainTransport busy = active.get(0);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                "车牌 " + vehicleNo + " 名下还有未完成的运单 " + busy.getTransportNo()
                        + "（" + statusLabel(busy.getStatus()) + "），一辆车同一时间只能跑一趟，" + action + "被拒。"
                        + "请先把该运单送到门店签收（或门店拒收完结），或核对确实作废后删除该运单，再来装这一趟");
    }

    private String statusLabel(Integer status) {
        if (status == null) return "状态未知";
        return switch (status) {
            case 1 -> "待发车";
            case 2 -> "运输中";
            case 3 -> "待签收";
            case 4 -> "已签收";
            default -> "状态 " + status;
        };
    }
}
