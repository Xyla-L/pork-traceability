package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.FarmDTO;
import com.pork.breeding.entity.Farm;
import com.pork.breeding.vo.FarmVO;

public interface FarmService extends IService<Farm> {
    Page<FarmVO> pageQuery(Long current, Long size, String farmName);
    void addFarm(FarmDTO dto);
    void updateFarm(FarmDTO dto);
    FarmVO getDetail(Long id);
    void removeFarm(Long id);
}