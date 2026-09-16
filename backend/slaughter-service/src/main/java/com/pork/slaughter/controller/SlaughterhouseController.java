package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pork.core.result.Result;
import com.pork.slaughter.entity.Slaughterhouse;
import com.pork.slaughter.mapper.SlaughterhouseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定点屠宰场查询：供出栏申报等下拉选择使用
 */
@RestController
@RequestMapping("/slaughter/slaughterhouses")
@Tag(name = "屠宰场管理", description = "提供屠宰场列表查询，供下拉选择")
@RequiredArgsConstructor
public class SlaughterhouseController {

    private final SlaughterhouseMapper slaughterhouseMapper;

    @GetMapping
    @Operation(summary = "查询所有启用的屠宰场")
    public Result<List<Slaughterhouse>> list() {
        // 仅返回启用状态(status=1)的屠宰场，按 id 升序保证展示稳定
        return Result.success(slaughterhouseMapper.selectList(
                Wrappers.<Slaughterhouse>lambdaQuery()
                        .eq(Slaughterhouse::getStatus, 1)
                        .orderByAsc(Slaughterhouse::getId)
        ));
    }
}
