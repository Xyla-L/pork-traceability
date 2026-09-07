package com.pork.breeding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pork.breeding.dto.PigIndividualDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.vo.PigIndividualVO;

public interface PigIndividualService extends IService<PigIndividual> {

    Page<PigIndividualVO> pageQuery(Long current, Long size, String earTagNo, Integer status);

    void addIndividual(PigIndividualDTO dto);

    void updateIndividual(PigIndividualDTO dto);

    PigIndividualVO getDetail(Long id);

    void removeIndividual(Long id);
}