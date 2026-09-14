package com.pork.slaughter.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.entity.CarcassStamp;
import com.pork.slaughter.dto.CarcassStampDTO;
import com.pork.slaughter.vo.CarcassStampVO;

public interface CarcassStampService extends IService<CarcassStamp> {

    /**
     * 分页查询盖章记录
     */
    IPage<CarcassStampVO> pageQuery(Page<CarcassStamp> page, CarcassStampDTO query,
                                    String status, String startDate, String endDate);

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
