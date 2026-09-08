package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.service.RactopamineTestService;
import com.pork.slaughter.vo.RactopamineTestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/slaughter/ractopamine")
@RequiredArgsConstructor
public class RactopamineTestController {

    private final RactopamineTestService ractopamineTestService;

    @PostMapping
    public Result<Void> add(@Valid @RequestBody RactopamineTestDTO dto) {
        ractopamineTestService.addTest(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RactopamineTestDTO dto) {
        ractopamineTestService.updateTest(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ractopamineTestService.deleteTest(id);
        return Result.success();
    }

    @GetMapping
    public Result<PageResult<RactopamineTestVO>> pageQuery(RactopamineTestDTO dto) {
        Page<RactopamineTestVO> page = ractopamineTestService.pageQuery(dto);
        return Result.success(PageResult.of(page));
    }
}
