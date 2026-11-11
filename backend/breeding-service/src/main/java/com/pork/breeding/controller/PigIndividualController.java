package com.pork.breeding.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.breeding.dto.SlaughterApplyDTO;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.service.SlaughterApplyService;
import com.pork.breeding.support.FarmScope;
import com.pork.breeding.vo.PigIndividualVO;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.web.source.VoidRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pigs")
@Tag(name = "生猪个体管理", description = "提供生猪档案的增删改查及分页查询功能")
@RequiredArgsConstructor
public class PigIndividualController {

    private final PigIndividualService pigIndividualService;
    private final SlaughterApplyService slaughterApplyService;
    private final FarmScope farmScope;

    @GetMapping({"", "/page"})
    @Operation(summary = "分页查询")
    public Result<PageResult<PigIndividualVO>> pageQuery(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "耳标号") @RequestParam(required = false) String earTagNo,
            @Parameter(description = "养殖场ID") @RequestParam(required = false) Long farmId,
            @Parameter(description = "品种") @RequestParam(required = false) String breed,
            @Parameter(description = "状态：中文（在养/已出栏/已屠宰/异常）或数字 1-4") @RequestParam(required = false) String status,
            @Parameter(description = "出生日期起（YYYY-MM-DD）") @RequestParam(required = false) String birthDateStart,
            @Parameter(description = "出生日期止（YYYY-MM-DD）") @RequestParam(required = false) String birthDateEnd) {

        // 机构隔离：养殖角色只看自己场——请求里传的 farmId 被忽略（restrict 强制换成自己的场），
        // 防止「换个场号翻别家的猪」；其他角色原样放行
        Page<PigIndividualVO> page = pigIndividualService.pageQuery(current, size, earTagNo,
                farmScope.restrict(farmId), breed, status, birthDateStart, birthDateEnd);
        return Result.success(PageResult.of(page));
    }

    @GetMapping("/breeds")
    @Operation(summary = "品种字典", description = "供前端下拉使用：预置常见猪种与库内实际出现过的品种合并返回")
    public Result<List<String>> breeds() {
        return Result.success(pigIndividualService.breedOptions());
    }

    /* 设备建档走 /breeding/internal/ingest/tag；人工登记走 /pigs/batches（按批次，个体不再逐头建档）。 */


    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取详情")
    public Result<PigIndividualVO> getDetail(@PathVariable Long id) {
        farmScope.requirePigOfFarm(id, "该生猪档案");
        PigIndividualVO vo = pigIndividualService.getDetail(id);
        return Result.success(vo);
    }

    @PostMapping("/{id}/void")
    @Operation(summary = "作废生猪档案（原删除）：需填写作废理由，记录保留不物理删除")
    public Result<Void> voidIndividual(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        farmScope.requirePigOfFarm(id, "该生猪档案");
        pigIndividualService.voidIndividual(id, request.getReason());
        return Result.success();
    }

    @PostMapping("/{pigId}/apply")
    @Operation(summary = "创建出栏申报")
    public Result<Long> applySlaughter(@PathVariable Long pigId, @Valid @RequestBody SlaughterApplyDTO dto) {
        // 机构隔离：不能替别家的猪提出栏申报
        farmScope.requirePigOfFarm(pigId, "该出栏申报");
        // pigId 由路径变量注入，请求体无需携带
        dto.setPigId(pigId);
        return Result.success(slaughterApplyService.createApply(dto));
    }
}
