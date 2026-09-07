package com.pork.slaughter.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.dto.CarcassStampDTO;

public interface CarcassStampService extends IService<CarcassStamp> {

    /**
     * 分页查询盖章记录
     */
    IPage<CarcassStamp> pageQuery(Page<CarcassStamp> page, CarcassStamp query);

    /**
     * 新增盖章记录
     */
    boolean addStamp(CarcassStampDTO dto);

    /**
     * 更新盖章记录
     */
    boolean updateStamp(CarcassStampDTO dto);

    /**
     * 根据ID删除盖章记录
     */
    boolean deleteStamp(Long id);
}