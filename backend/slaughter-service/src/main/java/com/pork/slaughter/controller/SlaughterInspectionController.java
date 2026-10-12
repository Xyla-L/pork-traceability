package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.service.SlaughterInspectionService;
import com.pork.slaughter.vo.SlaughterInspectionVO;
import com.pork.web.source.VoidRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/slaughter/inspections")
@RequiredArgsConstructor
public class SlaughterInspectionController {

    private final SlaughterInspectionService slaughterInspectionService;

    /* 人工新增入口已删除：屠宰检验为设备独占环节，数据只接受工位终端通道上报，
       设备报文异常走「设备接入 → 待人工处理」实名复核，不再提供人工补录接口。 */


    /**
     * 作废：已上链的检验记录不提供删除入口，只允许作废（实名 + 理由必填，作废动作单独上链存证）
     */
    @PostMapping("/{id}/void")
    public Result<Void> voidRecord(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        slaughterInspectionService.voidInspection(id, request.getReason());
        return Result.success();
    }

    @GetMapping
    public Result<PageResult<SlaughterInspectionVO>> pageQuery(SlaughterInspectionDTO dto) {
        Page<SlaughterInspectionVO> page = slaughterInspectionService.pageQuery(dto);
        return Result.success(PageResult.of(page));
    }
}
