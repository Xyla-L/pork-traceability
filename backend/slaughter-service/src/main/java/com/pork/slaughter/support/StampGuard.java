package com.pork.slaughter.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.mapper.CarcassStampMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 胴体盖章前置判断 —— 全项目唯一口径。
 *
 * <h3>为什么胴体批次必须以盖章为前提</h3>
 * 胴体批次是「这批白条已成型、可流通」的登记。而白条得以流通的凭证就是那枚章：
 * 盖章机在<b>宰后同步检疫合格之后</b>才执行盖章动作（判定见
 * {@code SlaughterDeviceIngestService}），所以「有没有章」本身就是
 * 「这具白条过没过同步检疫」的外部凭证。
 * <p>
 * 少了这道判断，未盖章的白条也能被归进批次往下走——追溯链上会出现一批
 * 「有批次、无章」的白条，而这批肉在纸面上是不该出厂的。
 *
 * <h3>判定粒度：判头不判批</h3>
 * 盖章是<b>逐具胴体</b>的行为（{@code carcass_stamp.pig_id} 唯一），不是按批下的结论，
 * 因此这道与瘦肉精的「按批抽检」不同，只认真数到每一头。
 *
 * <h3>共同口径</h3>
 * 「没盖章」是业务事实，不是「缺个字段补上就行」，调用方一律确定性拒绝，人工确认也绕不过去。
 * 被作废的章不参与判断（{@code @TableLogic} 自动过滤）——章被撤回，白条就退回未盖章状态。
 */
@Component
@RequiredArgsConstructor
public class StampGuard {

    /** 「未盖章」的统一措辞；批量场景下由 summarize 再冠以生猪编号 */
    private static final String MISSING = "尚未完成胴体盖章";
    /** 摘要里最多点名的生猪数量，避免回执过长 */
    private static final int MAX_LISTED = 5;
    /** status=2 表示已作废（与 void_flag 双写的旧口径，读时一并排除） */
    private static final Integer VOIDED_STATUS = 2;

    private final CarcassStampMapper stampMapper;

    /**
     * 单头猪是否已完成胴体盖章（胴体批次归集入账前调用）。
     *
     * @return null 表示已盖章、放行；否则返回可直接展示给业务人员的中文原因
     */
    public String unstampedReason(Long pigId, String earTagNo) {
        if (pigId == null) return null;
        if (hasStamp(pigId)) return null;
        return "该猪（耳标 " + orDash(earTagNo) + "）" + MISSING
                + "，白条未取得放行凭证，不得进入胴体批次"
                + "（盖章发生在宰后同步检疫之后，须先完成宰后检验并判合格）";
    }

    /**
     * 批量判断（供分割配送侧创建/变更胴体批次前核对）。
     *
     * @return 「未盖章的猪 id → 原因」，空 map 表示全部已盖章
     */
    public Map<Long, String> unstampedReasons(Collection<Long> pigIds) {
        Map<Long, String> unstamped = new LinkedHashMap<>();
        if (pigIds == null || pigIds.isEmpty()) return unstamped;
        List<Long> ids = pigIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return unstamped;

        // 一次取出所有已盖章的猪，再与入参做差集——比逐头查询少一轮往返，口径也更不容易写岔
        Set<Long> stamped = new LinkedHashSet<>();
        for (CarcassStamp stamp : stampMapper.selectList(Wrappers.<CarcassStamp>lambdaQuery()
                .in(CarcassStamp::getPigId, ids)
                .ne(CarcassStamp::getStatus, VOIDED_STATUS))) {
            if (stamp.getPigId() != null) stamped.add(stamp.getPigId());
        }
        for (Long pigId : ids) {
            if (stamped.contains(pigId)) continue;
            unstamped.put(pigId, "该猪" + MISSING + "，白条未取得放行凭证");
        }
        return unstamped;
    }

    /** 把批量判断结果拼成一句可直接展示给业务人员的话；无未盖章时返回 null */
    public String summarize(Map<Long, String> unstamped) {
        if (unstamped == null || unstamped.isEmpty()) return null;
        List<String> parts = unstamped.entrySet().stream()
                .limit(MAX_LISTED)
                .map(e -> "生猪#" + e.getKey() + "：" + e.getValue())
                .toList();
        String more = unstamped.size() > MAX_LISTED ? "（另有 " + (unstamped.size() - MAX_LISTED) + " 头同样受控）" : "";
        return String.join("；", parts) + more;
    }

    private boolean hasStamp(Long pigId) {
        Long n = stampMapper.selectCount(Wrappers.<CarcassStamp>lambdaQuery()
                .eq(CarcassStamp::getPigId, pigId)
                .ne(CarcassStamp::getStatus, VOIDED_STATUS));
        return n != null && n > 0;
    }

    private String orDash(String value) {
        return StringUtils.hasText(value) ? value : "未记录";
    }
}
