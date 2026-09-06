package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.PigCreateDTO;
import com.pork.breeding.dto.PigQueryDTO;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigDetailVO;
import com.pork.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/breeding/pigs")
public class PigIndividualController {

    @Autowired
    private PigIndividualService pigIndividualService;

    /**
     * 1. 创建生猪个体（绑定耳标）
     * 对应文档 5.2.2
     */
    @PostMapping
    public Result<Long> createPig(@RequestBody @Valid PigCreateDTO dto) {
        Long pigId = pigIndividualService.createPig(dto);
        return Result.success(pigId);
    }

    /**
     * 2. 分页查询生猪列表
     * 对应文档 5.2.3（通常列表页需要分页，且支持耳标号等条件筛选）
     */
    @GetMapping("/page")
    public Result<Page<PigDetailVO>> pagePigs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            PigQueryDTO queryDTO) {

        Page<PigDetailVO> result = pigIndividualService.pagePigs(page, size, queryDTO);
        return Result.success(result);
    }

    /**
     * 3. 根据ID查询生猪详情
     * 对应文档 5.2.4
     */
    @GetMapping("/{id}")
    public Result<PigDetailVO> getPigDetail(@PathVariable Long id) {
        PigDetailVO vo = pigIndividualService.getPigDetailById(id);
        return Result.success(vo);
    }

    /**
     * 4. 更新生猪信息（如转舍、状态变更等）
     */
    @PutMapping("/{id}")
    public Result<Void> updatePig(
            @PathVariable Long id,
            @RequestBody @Valid PigQueryDTO updateDTO) {

        pigIndividualService.updatePig(id, updateDTO);
        return Result.success();
    }

    /**
     * 5. 逻辑删除生猪个体
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePig(@PathVariable Long id) {
        pigIndividualService.deletePig(id);
        return Result.success();
    }
}