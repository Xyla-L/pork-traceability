package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.service.RactopamineTestService;
import com.pork.slaughter.vo.RactopamineTestVO;
import com.pork.web.source.VoidRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/slaughter/ractopamine")
@RequiredArgsConstructor
public class RactopamineTestController {

    private final RactopamineTestService ractopamineTestService;

    /* 人工新增入口已删除：瘦肉精检测为设备独占环节，数据只接受胶体金读数仪通道上报，
       设备报文异常走「设备接入 → 待人工处理」实名复核，不再提供人工补录接口。 */


    /** 作废：不再提供物理删除——检测记录已上链，删掉记录等于让追溯链断在这里 */
    @PostMapping("/{id}/void")
    public Result<Void> voidRecord(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        ractopamineTestService.voidTest(id, request.getReason());
        return Result.success();
    }

    @GetMapping
    public Result<PageResult<RactopamineTestVO>> pageQuery(RactopamineTestDTO dto) {
        Page<RactopamineTestVO> page = ractopamineTestService.pageQuery(dto);
        return Result.success(PageResult.of(page));
    }
}
