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
import com.pork.web.source.VoidRequest;
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
    public Result<PageResult<CarcassStampVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "20") Integer pageSize,
                                                   @RequestParam(required = false) Long pigId,
                                                   @RequestParam(required = false) String stampNo,
                                                   @RequestParam(required = false) String batchNo,
                                                   @RequestParam(required = false) String carcassNo,
                                                   @RequestParam(required = false) String stampType,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String startDate,
                                                   @RequestParam(required = false) String endDate) {
        CarcassStampDTO query = new CarcassStampDTO();
        query.setPigId(pigId);
        query.setStampNo(stampNo);
        query.setBatchNo(batchNo);
        query.setCarcassNo(carcassNo);
        query.setStampType(stampType);
        IPage<CarcassStampVO> page = service.pageQuery(new Page<>(pageNum, pageSize), query, status, startDate, endDate);
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

    /* 人工新增入口已删除：胴体盖章为设备独占环节，数据只接受自动盖章机通道上报，
       设备报文异常走「设备接入 → 待人工处理」实名复核，不再提供人工补录接口。 */


    /**
     * 作废：已上链的盖章记录不提供删除入口，只允许作废（实名 + 理由必填，作废动作单独上链存证）
     */
    @PostMapping("/{id}/void")
    public Result<Void> voidRecord(@PathVariable Long id, @Valid @RequestBody VoidRequest request) {
        service.voidStamp(id, request.getReason());
        return Result.success();
    }
}
