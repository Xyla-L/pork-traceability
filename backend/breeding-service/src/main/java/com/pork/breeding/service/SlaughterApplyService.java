package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.entity.SlaughterApply;
import com.pork.breeding.vo.SlaughterApplyVO;

public interface SlaughterApplyService extends IService<SlaughterApply> {
    Page<SlaughterApplyVO> pageQuery(Long current, Long size, Integer approvalStatus);
    SlaughterApplyVO getDetail(Long id);
}