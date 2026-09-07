package com.pork.slaughter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.dto.EntryInspectionDTO;
import com.pork.slaughter.entity.EntryInspection;
import com.pork.slaughter.vo.EntryInspectionVO;

public interface EntryInspectionService extends IService<EntryInspection> {

    /**
     * 新增入场查验记录
     */
    boolean addEntry(EntryInspectionDTO dto);

    /**
     * 修改入场查验记录
     */
    boolean updateEntry(Long id, EntryInspectionDTO dto);

    /**
     * 删除入场查验记录
     */
    boolean deleteEntry(Long id);

    /**
     * 分页查询入场查验列表
     */
    Page<EntryInspectionVO> pageQuery(EntryInspectionDTO dto, int pageNum, int pageSize);
}