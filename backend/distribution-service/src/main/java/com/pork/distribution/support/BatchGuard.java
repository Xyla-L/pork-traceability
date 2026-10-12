package com.pork.distribution.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.distribution.client.SlaughterClient;
import com.pork.distribution.entity.CarcassBatch;
import com.pork.distribution.entity.CarcassBatchPig;
import com.pork.distribution.mapper.CarcassBatchMapper;
import com.pork.distribution.mapper.CarcassBatchPigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 胴体批次写入前的三道闸门 —— 人工入口与设备归集共用同一套判断，避免「设备拦了、手工放过去」。
 *
 * <h3>为什么胴体批次要判三件事</h3>
 * 胴体批次创建＝这批猪被做成白条，是「白条已成型、可流通」的登记，也是分割与冷链的起点。
 * 前面几个环节各自拦了设备通道，但这里是个独立落点，不在写入前再判一次，
 * 受控的猪就能被绕过前序环节直接塞进批次往下走。三道分别是：
 * <ol>
 *   <li><b>瘦肉精（阳性 / 未检）</b>——阳性须隔离处理并上报，没检过则须先补做批批检。
 *       判定粒度是「批」（{@code RactopamineGuard}）。</li>
 *   <li><b>胴体盖章</b>——白条得以流通的凭证就是那枚章，而盖章发生在宰后同步检疫之后，
 *       所以「有没有章」本身就是「过没过同步检疫」的外部凭证。判定粒度是「头」（{@code StampGuard}）。</li>
 *   <li><b>未被其他批次占用</b>——一头猪只能归属一个批次，否则同一具白条会在两个批次里各算一次，
 *       下游分割与冷链的批次数量全部对不上。</li>
 * </ol>
 *
 * <h3>顺序有意为之：瘦肉精 → 盖章 → 占用</h3>
 * 瘦肉精排在最先，是因为它最接近「屠宰前就该有结论」的那道前提；盖章其次（宰后）；
 * 占用放最后，是因为它只在写入阶段才成立，而且设备重复上报时要靠它把请求导回既有批次。
 * 这个顺序同时保证了既有回归里「阳性 / 未检」的拒绝原因不被新校验抢答。
 *
 * <h3>共同口径</h3>
 * 前两道是业务事实，不是「缺个字段补上就行」，因此调用方一律确定性拒绝，人工确认也绕不过去；
 * 核不上屠宰侧时抛 {@code REMOTE_CALL_ERROR}——<b>核不了就不放行</b>。
 */
@Component
@RequiredArgsConstructor
public class BatchGuard {

    private final CarcassBatchMapper carcassBatchMapper;
    private final CarcassBatchPigMapper carcassBatchPigMapper;
    private final SlaughterClient slaughterClient;

    /**
     * 人工建批 / 变更关联生猪前的完整校验（三道全查，命中即抛）。
     *
     * @param excludeBatchId 变更场景下排除自身批次，避免「本批次的猪占用了自己」
     */
    public void validateAll(List<Long> pigIds, Long excludeBatchId) {
        requireRactopamineClear(pigIds);
        requireStamped(pigIds);
        requireNotOccupied(pigIds, excludeBatchId);
    }

    /** ① 瘦肉精：阳性、与阳性样本同批、以及压根没检过的猪，一律不得进入胴体批次 */
    public void requireRactopamineClear(List<Long> pigIds) {
        String blocked = slaughterClient.blockedSummary(pigIds);
        if (StringUtils.hasText(blocked)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "以下生猪不得进入胴体批次——" + blocked
                            + "（瘦肉精阳性须隔离处理并按规定上报；没做过检测的须先补做批批检）");
        }
    }

    /** ② 胴体盖章：没有章的白条还没有取得放行凭证，不得进入胴体批次 */
    public void requireStamped(List<Long> pigIds) {
        String unstamped = slaughterClient.unstampedSummary(pigIds);
        if (StringUtils.hasText(unstamped)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "以下生猪不得进入胴体批次——" + unstamped
                            + "（胴体批次是「白条已成型、可流通」的登记，须先完成盖章："
                            + "盖章发生在宰后同步检疫之后）");
        }
    }

    /** ③ 一头猪只能归属一个批次 */
    public void requireNotOccupied(List<Long> pigIds, Long excludeBatchId) {
        if (pigIds == null || pigIds.isEmpty()) return;
        Map<Long, String> occupied = occupancy(pigIds, excludeBatchId);
        List<String> conflicts = pigIds.stream().distinct()
                .filter(p -> p != null && occupied.containsKey(p))
                .map(p -> "生猪ID" + p + "（已属批次" + occupied.get(p) + "）")
                .toList();
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "以下生猪已属于其他批次，一头猪只能归属一个批次：" + String.join("、", conflicts));
        }
    }

    /**
     * 该猪已归属的批次（设备重复上报时用它把请求导回既有批次）。
     * <p>
     * 走关联表的唯一键查找，一次索引命中；不要退回「遍历所有批次的 pig_ids 数组」那种写法——
     * 那种写法既慢（全表扫 + 逐行解析 JSON）又有竞态（两个请求可以同时通过检查）。
     *
     * @param excludeBatchId 需要排除的批次（变更场景传自身）
     * @return 命中的批次；未归属任何批次（或所属批次已作废）时返回 null
     */
    public CarcassBatch findOccupied(Long pigId, Long excludeBatchId) {
        if (pigId == null) return null;
        CarcassBatchPig link = carcassBatchPigMapper.selectOne(Wrappers.<CarcassBatchPig>lambdaQuery()
                .eq(CarcassBatchPig::getPigId, pigId).last("LIMIT 1"));
        if (link == null) return null;
        if (excludeBatchId != null && excludeBatchId.equals(link.getBatchId())) return null;
        // 批次可能已被作废：@TableLogic 会让查不到，此时该猪视为未被占用（可重新归批）
        return carcassBatchMapper.selectById(link.getBatchId());
    }

    /** 瘦肉精阻断摘要（null 表示全部放行）——同时供给「抛异常」与「设备侧软处理」两条路径 */
    public String ractopamineBlockedSummary(Collection<Long> pigIds) {
        return slaughterClient.blockedSummary(pigIds);
    }

    /** 未盖章摘要（null 表示全部已盖章）——同上 */
    public String unstampedSummary(Collection<Long> pigIds) {
        return slaughterClient.unstampedSummary(pigIds);
    }

    /**
     * 指定生猪的占用映射：pigId -> 该猪所属批次号。
     * 只查这批猪的关联行（走 uk_pig），不扫全表。
     */
    private Map<Long, String> occupancy(Collection<Long> pigIds, Long excludeBatchId) {
        if (pigIds == null || pigIds.isEmpty()) return Map.of();
        List<CarcassBatchPig> links = carcassBatchPigMapper.selectList(Wrappers.<CarcassBatchPig>lambdaQuery()
                .in(CarcassBatchPig::getPigId, pigIds.stream().filter(java.util.Objects::nonNull).distinct().toList()));
        if (links.isEmpty()) return Map.of();
        List<Long> batchIds = links.stream()
                .map(CarcassBatchPig::getBatchId)
                .filter(id -> excludeBatchId == null || !excludeBatchId.equals(id))
                .distinct().toList();
        if (batchIds.isEmpty()) return Map.of();
        Map<Long, String> noById = new HashMap<>();
        for (CarcassBatch batch : carcassBatchMapper.selectBatchIds(batchIds)) {
            noById.put(batch.getId(), batch.getBatchNo());
        }
        Map<Long, String> occupied = new HashMap<>();
        for (CarcassBatchPig link : links) {
            if (excludeBatchId != null && excludeBatchId.equals(link.getBatchId())) continue;
            String no = noById.get(link.getBatchId());
            if (no != null) occupied.putIfAbsent(link.getPigId(), no);
        }
        return occupied;
    }
}
