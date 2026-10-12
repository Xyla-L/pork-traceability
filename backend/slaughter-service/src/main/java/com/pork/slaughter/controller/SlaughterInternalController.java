package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.Result;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.mapper.CarcassStampMapper;
import com.pork.slaughter.mapper.EntryInspectionMapper;
import com.pork.slaughter.mapper.RactopamineTestMapper;
import com.pork.slaughter.mapper.SlaughterInspectionMapper;
import com.pork.slaughter.support.RactopamineGuard;
import com.pork.slaughter.support.StampGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/slaughter/internal")
@RequiredArgsConstructor
public class SlaughterInternalController {
    private final EntryInspectionMapper entryMapper;
    private final SlaughterInspectionMapper inspectionMapper;
    private final RactopamineTestMapper testMapper;
    private final CarcassStampMapper stampMapper;
    private final RactopamineGuard ractopamineGuard;
    private final StampGuard stampGuard;

    /**
     * 瘦肉精阻断批量核对（供分割配送侧创建/变更胴体批次前调用）。
     * <p>
     * 返回 {@code {blocked: {pigId: 原因}, summary: 一句话}}；{@code blocked} 为空表示全部放行。
     * 阻断包含三种情形：自身阳性、与阳性样本同批次、<b>未做过检测</b>（未检不得屠宰）。
     * 判断口径在 {@link RactopamineGuard}，这里只负责搬运——下游不得再各自翻译一遍。
     */
    @GetMapping("/ractopamine-blocked")
    public Result<Map<String, Object>> ractopamineBlocked(@RequestParam List<Long> pigIds) {
        Map<Long, String> blocked = ractopamineGuard.blockReasons(pigIds);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("blocked", blocked);
        body.put("summary", ractopamineGuard.summarize(blocked));
        return Result.success(body);
    }

    /**
     * 胴体盖章批量核对（供分割配送侧创建/变更胴体批次前调用）。
     * <p>
     * 返回 {@code {unstamped: {pigId: 原因}, summary: 一句话}}；{@code unstamped} 为空表示全部已盖章。
     * 判断口径在 {@link StampGuard}，这里只负责搬运——下游不得再各自翻译一遍。
     */
    @GetMapping("/unstamped")
    public Result<Map<String, Object>> unstamped(@RequestParam List<Long> pigIds) {
        Map<Long, String> unstamped = stampGuard.unstampedReasons(pigIds);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unstamped", unstamped);
        body.put("summary", stampGuard.summarize(unstamped));
        return Result.success(body);
    }

    @GetMapping("/pigs")
    public Result<List<Map<String, Object>>> traceByPigs(@RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Result.success(List.of());
        List<Long> pigIds = ids.stream().distinct().toList();
        if (pigIds.size() > 200) throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多查询200头生猪");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long pigId : pigIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pigId", pigId);
            row.put("entries", entryMapper.selectList(Wrappers.<EntryInspection>lambdaQuery()
                    .eq(EntryInspection::getPigId, pigId).orderByDesc(EntryInspection::getArriveTime)));
            row.put("inspections", inspectionMapper.selectList(Wrappers.<SlaughterInspection>lambdaQuery()
                    .eq(SlaughterInspection::getPigId, pigId)
                    // 已作废记录不进入溯源链展示
                    .ne(SlaughterInspection::getStatus, 3)
                    .orderByAsc(SlaughterInspection::getInspectTime)));
            row.put("ractopamineTests", testMapper.selectList(Wrappers.<RactopamineTest>lambdaQuery()
                    .eq(RactopamineTest::getPigId, pigId).orderByAsc(RactopamineTest::getTestTime)));
            row.put("stamps", stampMapper.selectList(Wrappers.<CarcassStamp>lambdaQuery()
                    .eq(CarcassStamp::getPigId, pigId)
                    // 已作废记录不进入溯源链展示
                    .ne(CarcassStamp::getStatus, 2)
                    .orderByDesc(CarcassStamp::getStampTime)));
            // 以上查询均走 MyBatis-Plus（@TableLogic 挂在 void_flag 上），自动只取当前有效记录。
            // 被滤掉的作废行不静默消失，统一收进 voided 供追溯方标注说明。
            row.put("voided", voidedRecords(pigId));
            result.add(row);
        }
        return Result.success(result);
    }

    /**
     * 汇总该生猪各环节的已作废记录。
     * <p>
     * 「取当前有效记录」不等于「把作废当成没发生过」：结论类数据被作废重录，恰恰是最值得追溯方
     * 知道的事。这里只读不改，作废明细随链路一起返回，具体怎么展示交给上游决定。
     */
    private List<Map<String, Object>> voidedRecords(Long pigId) {
        List<Map<String, Object>> voided = new ArrayList<>();
        collect(voided, entryMapper.selectVoidedByPig(pigId), "ENTRY", "入场查验");
        collect(voided, inspectionMapper.selectVoidedByPig(pigId), "INSPECTION", "屠宰检验");
        collect(voided, testMapper.selectVoidedByPig(pigId), "RACTOPAMINE", "瘦肉精检测");
        collect(voided, stampMapper.selectVoidedByPig(pigId), "STAMP", "胴体盖章");
        return voided;
    }

    private void collect(List<Map<String, Object>> target, List<Map<String, Object>> rows,
                         String stage, String stageLabel) {
        if (rows == null || rows.isEmpty()) return;
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", stage);
            item.put("stageLabel", stageLabel);
            item.putAll(row);
            target.add(item);
        }
    }
}
