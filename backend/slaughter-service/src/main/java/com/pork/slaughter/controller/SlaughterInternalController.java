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
                    .eq(SlaughterInspection::getPigId, pigId).orderByAsc(SlaughterInspection::getInspectTime)));
            row.put("ractopamineTests", testMapper.selectList(Wrappers.<RactopamineTest>lambdaQuery()
                    .eq(RactopamineTest::getPigId, pigId).orderByAsc(RactopamineTest::getTestTime)));
            row.put("stamps", stampMapper.selectList(Wrappers.<CarcassStamp>lambdaQuery()
                    .eq(CarcassStamp::getPigId, pigId).orderByDesc(CarcassStamp::getStampTime)));
            result.add(row);
        }
        return Result.success(result);
    }
}
