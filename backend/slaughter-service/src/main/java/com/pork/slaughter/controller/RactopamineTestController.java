package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.service.RactopamineTestService;
import com.pork.slaughter.vo.RactopamineTestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/slaughter/ractopamine")
public class RactopamineTestController {

    @Autowired
    private RactopamineTestService ractopamineTestService;

    @PostMapping
    public Result<Void> add(@RequestBody RactopamineTestDTO dto) {
        ractopamineTestService.addTest(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody RactopamineTestDTO dto) {
        ractopamineTestService.updateTest(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ractopamineTestService.deleteTest(id);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<PageResult<RactopamineTestVO>> pageQuery(RactopamineTestDTO dto) {
        Page<RactopamineTestVO> page = ractopamineTestService.pageQuery(dto);
        return Result.success(PageResult.of(page));
    }
}