package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.PageResult;
import com.pork.core.result.Result;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.service.CarcassStampService;
import com.pork.slaughter.vo.CarcassStampVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/slaughter/stamps")
@RequiredArgsConstructor
public class CarcassStampController {
    private final CarcassStampService service;

    @GetMapping
    public Result<PageResult<CarcassStamp>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "20") Integer pageSize,
                                                  @RequestParam(required = false) Long pigId,
                                                  @RequestParam(required = false) String stampNo) {
        CarcassStamp query = new CarcassStamp();
        query.setPigId(pigId);
        query.setStampNo(stampNo);
        IPage<CarcassStamp> page = service.pageQuery(new Page<>(pageNum, pageSize), query);
        return Result.success(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<CarcassStampVO> getById(@PathVariable Long id) {
        CarcassStamp stamp = service.getById(id);
        if (stamp == null) throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "检疫盖章记录不存在");
        CarcassStampVO vo = new CarcassStampVO();
        BeanUtils.copyProperties(stamp, vo);
        return Result.success(vo);
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody CarcassStampDTO dto) {
        service.addStamp(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CarcassStampDTO dto) {
        dto.setId(id);
        service.updateStamp(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteStamp(id);
        return Result.success();
    }
}
