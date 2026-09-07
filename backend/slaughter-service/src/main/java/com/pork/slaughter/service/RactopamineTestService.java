package com.pork.slaughter.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.slaughter.dto.RactopamineTestDTO;
import com.pork.slaughter.entity.RactopamineTest;
import com.pork.slaughter.vo.RactopamineTestVO;

public interface RactopamineTestService extends IService<RactopamineTest> {

    /**
     * 新增瘦肉精检测记录
     */
    boolean addTest(RactopamineTestDTO dto);

    /**
     * 修改瘦肉精检测记录
     */
    boolean updateTest(Long id, RactopamineTestDTO dto);

    /**
     * 删除瘦肉精检测记录
     */
    boolean deleteTest(Long id);

    /**
     * 分页查询瘦肉精检测记录
     */
    Page<RactopamineTestVO> pageQuery(RactopamineTestDTO dto);
}