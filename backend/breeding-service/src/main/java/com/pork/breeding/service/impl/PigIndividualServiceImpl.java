package com.pork.breeding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pork.breeding.dto.PigIndividualDTO;
import com.pork.breeding.entity.PigIndividual;
import com.pork.breeding.mapper.PigIndividualMapper;
import com.pork.breeding.service.PigIndividualService;
import com.pork.breeding.vo.PigIndividualVO;
import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PigIndividualServiceImpl extends ServiceImpl<PigIndividualMapper, PigIndividual> implements PigIndividualService {

    @Override
    public Page<PigIndividualVO> pageQuery(Long current, Long size, String earTagNo, Integer status) {
        Page<PigIndividual> page = new Page<>(current, size);
        LambdaQueryWrapper<PigIndividual> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(earTagNo)) {
            wrapper.like(PigIndividual::getEarTagNo, earTagNo);
        }
        if (status != null) {
            wrapper.eq(PigIndividual::getStatus, status);
        }
        wrapper.orderByDesc(PigIndividual::getCreateTime);

        Page<PigIndividual> entityPage = this.page(page, wrapper);

        // Entity 转 VO
        Page<PigIndividualVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(entity -> {
            PigIndividualVO vo = new PigIndividualVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        }).toList());

        return voPage;
    }

    @Override
    public void addIndividual(PigIndividualDTO dto) {
        PigIndividual entity = new PigIndividual();
        BeanUtils.copyProperties(dto, entity);
        boolean saved = this.save(entity);
        if (!saved) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR);
        }
    }

    @Override
    public void updateIndividual(PigIndividualDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        PigIndividual entity = new PigIndividual();
        BeanUtils.copyProperties(dto, entity);
        boolean updated = this.updateById(entity);
        if (!updated) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
    }

    @Override
    public PigIndividualVO getDetail(Long id) {
        PigIndividual entity = this.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        PigIndividualVO vo = new PigIndividualVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    @Override
    public void removeIndividual(Long id) {
        boolean removed = this.removeById(id);
        if (!removed) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
    }
}