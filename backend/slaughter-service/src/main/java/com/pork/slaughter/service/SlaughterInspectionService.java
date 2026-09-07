package com.pork.slaughter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.dto.SlaughterInspectionDTO;
import com.pork.slaughter.entity.SlaughterInspection;
import com.pork.slaughter.vo.SlaughterInspectionVO;

public interface SlaughterInspectionService extends IService<SlaughterInspection> {

    /**
     * 新增屠宰检验记录
     */
    boolean addInspection(SlaughterInspectionDTO dto);

    /**
     * 修改屠宰检验记录
     */
    boolean updateInspection(Long id, SlaughterInspectionDTO dto);

    /**
     * 删除屠宰检验记录
     */
    boolean deleteInspection(Long id);

    /**
     * 分页查询屠宰检验记录
     */
    Page<SlaughterInspectionVO> pageQuery(SlaughterInspectionDTO dto);
}