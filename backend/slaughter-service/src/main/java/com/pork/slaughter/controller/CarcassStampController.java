package com.pork.slaughter.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.service.CarcassStampService;
import com.pork.slaughter.vo.CarcassStampVO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/api/slaughter/carcass-stamp")
public class CarcassStampController {

    @Resource
    private CarcassStampService carcassStampService;

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public IPage<CarcassStamp> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long pigId,
            @RequestParam(required = false) String stampNo) {
        
        Page<CarcassStamp> page = new Page<>(pageNum, pageSize);
        CarcassStamp query = new CarcassStamp();
        query.setPigId(pigId);
        query.setStampNo(stampNo);
        return carcassStampService.pageQuery(page, query);
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public CarcassStampVO getById(@PathVariable Long id) {
        CarcassStamp stamp = carcassStampService.getById(id);
        CarcassStampVO vo = new CarcassStampVO();
        BeanUtils.copyProperties(stamp, vo);
        return vo;
    }

    /**
     * 新增
     */
    @PostMapping
    public boolean add(@RequestBody CarcassStampDTO dto) {
        return carcassStampService.addStamp(dto);
    }

    /**
     * 更新
     */
    @PutMapping
    public boolean update(@RequestBody CarcassStampDTO dto) {
        return carcassStampService.updateStamp(dto);
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return carcassStampService.deleteStamp(id);
    }
}